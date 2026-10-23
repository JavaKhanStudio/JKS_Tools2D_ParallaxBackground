// Stills of every scene of a lab round, as shots/.../ParallaxShots takes them with libGDX: the same page, the same 60
// units/s scroll stepped at 1/60 s, grabbed 0, 6 and 12 s in (engines/godot/tests/shots.gd does the same). A scene's
// "transfer" (one, or a list) and "tint" start a cross-fade or a tint at the same step as the lab, its "speedY" scrolls
// it up too, its "resize" resizes the screen mid-scroll, its "sequenceSeed" draws the SEQUENCE layers from that game
// seed and its "zoom": [1, 0.5] zooms the camera straight from the first at 0 s to the second at 12 s. engines/threejs/tools/frame-check.sh serves the repository with
// engines/threejs/tools/serve.py and opens
//   shots.html?round=<round dir>
// which POSTs each still to save/<scene>-t<seconds>.png, and what it logged to done. &skip=s12,s13 leaves scenes out
// (frame-check.sh's SKIP: what this reader does not draw yet).
import * as THREE from 'three';
import { PlaxBackground, loadAtlas, loadPage } from '../src/plax.js';

const SHOT_TIMES = [0, 6, 12];
// BREAK=scroll (frame-check.sh's negative control): a scroll 10 % too fast, which the check must fail.
const SPEED = new URLSearchParams(location.search).get('break') === 'scroll' ? 66 : 60;
const f32 = Math.fround;
const log = [];
const say = line => { log.push(line); console.log(line); };

async function post(path, body) {
  const r = await fetch(path, { method: 'POST', body });
  if (!r.ok) throw new Error(`${path}: ${r.status}`);
}

async function shots() {
  const roundDir = new URLSearchParams(location.search).get('round');
  const root = '/';
  const round = await (await fetch(root + roundDir + '/round.json')).json();
  const renderer = new THREE.WebGLRenderer({ antialias: false, preserveDrawingBuffer: true, alpha: false });
  renderer.setPixelRatio(1);
  renderer.setClearColor(0x000000, 1);
  document.body.appendChild(renderer.domElement);
  const gl = renderer.getContext(), info = gl.getExtension('WEBGL_debug_renderer_info');
  say('renderer: ' + gl.getParameter(info ? info.UNMASKED_RENDERER_WEBGL : gl.RENDERER));
  const bg = new PlaxBackground();
  const resize = (w, h) => { renderer.setSize(w, h); bg.setSize(w, h); };
  const step = f32(1 / 60);
  const skip = (new URLSearchParams(location.search).get('skip') ?? '').split(',');
  for (const scene of round.scenes) {
    if (skip.includes(scene.id)) { say('skipped: ' + scene.id); continue; }
    resize(1280, 720);
    await bg.load(root + scene.page, root + scene.atlasDir);
    if (scene.sequenceSeed !== undefined) bg.setSequenceSeed(scene.sequenceSeed);
    else bg.clearSequenceSeed();
    bg.tintTo([1, 1, 1, 1], 0);
    bg.speedConstantX = SPEED;
    bg.speedConstantY = scene.speedY ?? 0;
    const zoom = scene.zoom ?? [1, 1];
    let resizeAt = scene.resize ?? null, tint = scene.tint ?? null;
    // One transfer, or a list of them started one after the other.
    const transfers = scene.transfer === undefined ? [] : Array.isArray(scene.transfer) ? [...scene.transfer] : [scene.transfer];
    // The libGDX loop adds a float step to a float time: count its steps the same way, in float32.
    let time = 0;
    for (const at of SHOT_TIMES) {
      while (time < at) {
        while (transfers.length && time >= transfers[0].at) await transfer(bg, root, scene, transfers.shift());
        if (resizeAt && time >= resizeAt.at) { resize(resizeAt.size[0], resizeAt.size[1]); resizeAt = null; }
        if (tint && time >= tint.at) { bg.tintTo(tint.color, tint.seconds); tint = null; }
        bg.act(step);
        time = f32(time + step);
      }
      // As ParallaxShots.zoomAt, in float32.
      bg.zoom = f32(zoom[0] + (zoom[1] - zoom[0]) * (at / 12));
      renderer.clear();
      bg.render(renderer);
      const png = await new Promise(ok => renderer.domElement.toBlob(ok, 'image/png'));
      await post(`save/${scene.id}-t${at}.png`, png);
    }
    say('shots: ' + scene.id);
  }
}

// Into the scene's transfer page, or into the page on screen when it names none.
async function transfer(bg, root, scene, t) {
  if (t.style) throw new Error(`transfer style ${t.style.kind}: not ported to three.js yet`);
  if (!t.page) { bg.transfertInto(bg.page, bg.atlas, t.seconds); return; }
  const page = await loadPage(root + t.page);
  const atlasDir = root + (t.atlasDir ?? scene.atlasDir);
  bg.transfertInto(page, await loadAtlas(atlasDir + '/' + page.atlasName), t.seconds);
}

shots().then(() => say('shots: PASS'), e => say('FAIL: ' + (e.stack || e)))
  .finally(() => post('done', log.join('\n') + '\n'));
