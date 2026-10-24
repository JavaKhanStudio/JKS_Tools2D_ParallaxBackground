// The three.js reader's latency on the bench round (parallax-readers:r20), as shots/.../ParallaxBench times libGDX: for
// each scene its load time (page and atlas read, until its first frame is drawn) and the time of each of FRAMES frames
// after WARMUP unmeasured ones, at 1/60 s steps: act, render, and gl.finish forced by a one-pixel readPixels, on
// performance.now (Chrome rounds it to 0.1 ms off a cross-origin-isolated page: the mean over 600 frames still
// reads). A scene's "transfer" starts at load and lasts its seconds. A scene with a layer kind the reader does not port
// yet is left out of the file and named in the log. engines/threejs/tools/bench.sh opens
//   bench.html?round=<round dir>
// which POSTs save/bench.json, in ParallaxBench's shape, and what it logged to done.
import * as THREE from 'three';
import { PlaxBackground, loadAtlas, loadPage, loadParticles } from '../src/plax.js';

const WARMUP = 60, FRAMES = 600, STEP = 1 / 60;
const log = [];
const say = line => { log.push(line); console.log(line); };
const round3 = v => Math.round(v * 1000) / 1000;

async function post(path, body) {
  const r = await fetch(path, { method: 'POST', body });
  if (!r.ok) throw new Error(`${path}: ${r.status}`);
}

async function bench() {
  const roundDir = new URLSearchParams(location.search).get('round');
  const round = await (await fetch('/' + roundDir + '/round.json')).json();
  const renderer = new THREE.WebGLRenderer({ antialias: false, alpha: false });
  renderer.setPixelRatio(1);
  renderer.setSize(1280, 720);
  document.body.appendChild(renderer.domElement);
  const gl = renderer.getContext(), info = gl.getExtension('WEBGL_debug_renderer_info');
  const rendererName = gl.getParameter(info ? info.UNMASKED_RENDERER_WEBGL : gl.RENDERER);
  say('renderer: ' + rendererName);
  const pixel = new Uint8Array(4);
  const finish = () => gl.readPixels(0, 0, 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, pixel);
  const scenes = [];
  for (const scene of round.scenes) {
    const bg = new PlaxBackground();
    bg.setSize(1280, 720);
    const start = performance.now();
    try {
      await bg.load('/' + scene.page, '/' + scene.atlasDir);
      const transfers = scene.transfer === undefined ? [] : Array.isArray(scene.transfer) ? scene.transfer : [scene.transfer];
      for (const t of transfers) {
        const page = await loadPage('/' + t.page);
        const atlas = await loadAtlas('/' + (t.atlasDir ?? scene.atlasDir) + '/' + page.atlasName);
        await loadParticles(page, atlas);
        bg.transfertInto(page, atlas, t.seconds);
      }
    } catch (e) {
      say(`skipped ${scene.id}: ${e.message}`);
      bg.dispose();
      continue;
    }
    bg.speedConstantX = 60;
    bg.speedConstantY = scene.speedY ?? 0;
    const layers = bg.layers.length + bg.transferLayers.length;
    bg.render(renderer);
    finish();
    const loadMs = performance.now() - start;
    const ms = [];
    for (let f = 0; f < WARMUP + FRAMES; f++) {
      const t0 = performance.now();
      bg.act(STEP);
      bg.render(renderer);
      finish();
      if (f >= WARMUP) ms.push(performance.now() - t0);
    }
    bg.dispose();
    const mean = ms.reduce((a, b) => a + b, 0) / ms.length;
    ms.sort((a, b) => a - b);
    scenes.push({ id: scene.id, layers, loadMs: Math.round(loadMs * 100) / 100, meanMs: round3(mean),
      p50Ms: round3(ms[FRAMES / 2]), p95Ms: round3(ms[Math.floor(FRAMES * 95 / 100)]), maxMs: round3(ms[FRAMES - 1]) });
    say('bench: ' + scene.id);
  }
  await post('save/bench.json', JSON.stringify({ reader: 'threejs', renderer: rendererName,
    measure: 'act, render, gl.finish by readPixels (no swap), performance.now', frames: FRAMES, scenes }, null, 1));
}

bench().then(() => say('bench: PASS'), e => say('FAIL: ' + (e.stack || e)))
  .finally(() => post('done', log.join('\n') + '\n'));
