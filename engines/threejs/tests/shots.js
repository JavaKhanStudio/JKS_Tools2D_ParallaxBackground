// Stills of every scene of a lab round, as shots/.../ParallaxShots takes them with libGDX: the same page, the same 60
// units/s scroll stepped at 1/60 s, grabbed 0, 6 and 12 s in (engines/godot/tests/shots.gd does the same). A scene's
// "transfer" (one, or a list) and "tint" start a cross-fade or a tint at the same step as the lab, its "speedY" scrolls
// it up too, its "resize" resizes the screen mid-scroll, its "sequenceSeed" draws the SEQUENCE layers from that game
// seed its "hooks" draw its EMPTY layers (an atlas region stretched over each tile, as ParallaxShots; "planet": stood on
// the tile's foot round the page's planet, turned as it leans) and its "zoom": [1, 0.5] zooms the camera straight from
// the first at 0 s to the second at 12 s, dollied when its "dolly" is true. engines/threejs/tools/frame-check.sh serves the repository with
// engines/threejs/tools/serve.py and opens
//   shots.html?round=<round dir>
// which POSTs each still to save/<scene>-t<seconds>.png, and what it logged to done. &skip=s12,s13 leaves scenes out
// (frame-check.sh's SKIP: what this reader does not draw yet).
import * as THREE from 'three';
import {
  PlaxAnim, PlaxBackground, PlaxFloor, PlaxLurker, PlaxPanorama, PlaxParticles, PlaxPlanet, PlaxTransfertStyle, loadAtlas,
  loadPage, loadParticles,
} from '../src/plax.js';

const SHOT_TIMES = [0, 6, 12];
// BREAK=scroll (frame-check.sh's negative control): a scroll 10 % too fast, which the check must fail.
const BREAK = new URLSearchParams(location.search).get('break');
const SPEED = BREAK === 'scroll' ? 66 : 60;
// BREAK=plant (no layer planted on a floor), lurker (every figure always there) and anim (every patch at its first
// frame): the floor, lurker and animated rounds must FAIL each.
if (BREAK === 'plant') PlaxFloor.plant = () => {};
if (BREAK === 'lurker') PlaxLurker.isThere = () => true;
if (BREAK === 'anim') PlaxAnim.setTime = () => {};
// BREAK=dolly (the zoom without it) must FAIL dolly, planet (every page flat) planet, shade (no tower darker toward its
// sides) and panorama (nothing wrapped round a near tower) tower (r43).
if (BREAK === 'planet') PlaxPlanet.radiusOf = () => 0;
if (BREAK === 'shade') PlaxBackground.prototype._towerShade = () => null;
if (BREAK === 'panorama') PlaxPanorama.radiusOf = () => 0;
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
    // The particles' random seeded from the scene's id, as Round.seedRandom: the snow falls as in libGDX.
    PlaxParticles.random.setSeed(PlaxParticles.hashCode(scene.id));
    await bg.load(root + scene.page, root + scene.atlasDir);
    // BREAK=hooks: no EMPTY layer drawn, which the effects round must fail.
    setHooks(bg, BREAK === 'hooks' ? {} : scene.hooks ?? {});
    breakParticles(bg);
    if (scene.sequenceSeed !== undefined) bg.setSequenceSeed(scene.sequenceSeed);
    else bg.clearSequenceSeed();
    bg.tintTo([1, 1, 1, 1], 0);
    bg.speedConstantX = SPEED;
    bg.speedConstantY = scene.speedY ?? 0;
    // ParallaxShots: the dolly (r278) for the scene's zoom.
    bg.dolly = !!scene.dolly && BREAK !== 'dolly';
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

// A hook per EMPTY layer the scene names, drawing a region of the page's atlas; the scene before's are removed.
function setHooks(bg, hooks) {
  for (const name of [...bg._hooks.keys()]) bg.setLayerHook(name, null);
  for (const name in hooks) {
    const region = bg.atlas.findRegion(hooks[name].region, hooks[name].position ?? 0);
    // "planet": round a planet, stood on its tile's foot, placed and turned as the planet says (r347).
    const planet = !!hooks[name].planet;
    bg.setLayerHook(name, (batch, rect, color, l) => {
      if (!planet) {
        batch.drawRegion(region, rect.x, rect.y, rect.width, rect.height);
        return;
      }
      const foot = [rect.x + rect.width / 2, rect.y + rect.height];
      batch.drawRegion(region, -rect.width / 2, -rect.height, rect.width, rect.height, bg.planetPlaceAt(l, foot),
        bg.planetAngleAt(l, foot[0]));
    });
  }
}

// Into the scene's transfer page, or into the page on screen when it names none.
async function transfer(bg, root, scene, t) {
  const style = styleOf(t.style ?? {});
  if (!t.page) { bg.transfertInto(bg.page, bg.atlas, t.seconds, style); return; }
  const page = await loadPage(root + t.page);
  const atlasDir = root + (t.atlasDir ?? scene.atlasDir), atlas = await loadAtlas(atlasDir + '/' + page.atlasName);
  await loadParticles(page, atlas);
  bg.transfertInto(page, atlas, t.seconds, style);
  breakParticles(bg);
}

// BREAK=particles (no flakes drawn), anchor (a VIEW layer drawn as LAYER) and blend (every emitter blended normally):
// the particles round must FAIL each, as engines/jme's.
function breakParticles(bg) {
  for (const l of [...bg.layers, ...bg.transferLayers]) {
    if (!l.particles) continue;
    if (BREAK === 'particles') l.particles.draw = () => {};
    if (BREAK === 'anchor') l.model = { ...l.model, particlesAnchor: 'LAYER' };
    if (BREAK === 'blend') for (const e of l.particles.emitters) e.blend = () => 'normal';
  }
}

// A transfer's "style": {"kind": "DEPTH_STAGGER", "stagger": 1} or {"kind": "DISSOLVE", "patches": 5,
// "softness": 0.08, "stagger": 0.5} or {"kind": "THROUGH_COLOR", "color": "ffffff", "stagger": 0} or {"kind":
// "FOG_CREEP", "color": "eef2f7", "stagger": 1}; none, or another kind: a plain fade (null), as ParallaxShots.style.
function styleOf(style) {
  const hex = h => [0, 2, 4].map(i => parseInt((h ?? 'ffffff').slice(i, i + 2), 16) / 255);
  switch (style.kind) {
    case 'DEPTH_STAGGER': return PlaxTransfertStyle.depthStagger(style.stagger ?? 0);
    case 'DISSOLVE': return PlaxTransfertStyle.dissolve(style.patches ?? 5, style.softness ?? 0.08, style.stagger ?? 0);
    case 'THROUGH_COLOR': return PlaxTransfertStyle.throughColor(hex(style.color), style.stagger ?? 0);
    case 'FOG_CREEP': return PlaxTransfertStyle.fogCreep(hex(style.color), style.stagger ?? 0);
    default: return null;
  }
}

shots().then(() => say('shots: PASS'), e => say('FAIL: ' + (e.stack || e)))
  .finally(() => post('done', log.join('\n') + '\n'));
