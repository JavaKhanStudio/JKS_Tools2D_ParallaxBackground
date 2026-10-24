// PlaxRings' numbers for each scene of a rings round (parallax-readers r48): the page loaded by PlaxBackground, the
// scene's options over PlaxRings.options() (fogStrength left out, the page's own), PlaxRings.bands and gradient, POSTed
// as save/<id>.json in the shape engines/rounds/reader's RingsRound writes core's (a still ring's radius null), for
// tools/compare-rings.py. Logs "rings: PASS" (every scene written) or FAIL lines, and POSTs them to done.
// engines/threejs/tools/frame-check.sh opens
//   rings.html?round=engines/rounds/rings
// through engines/threejs/tools/run-page.sh whenever it checks that round.
import { PlaxBackground, PlaxRings, loadAtlas, loadPage } from '../src/plax.js';

const log = [];
const say = line => { log.push(line); console.log(line); };
const round = new URLSearchParams(location.search).get('round') ?? 'engines/rounds/rings';
const at = path => '/' + path.replace(/^\/+/, '');
const KEYS = ['front', 'far', 'foot', 'lift', 'scale', 'pageDegrees', 'fogStrength'];

async function scene(s) {
  const page = await loadPage(at(s.page));
  const bg = new PlaxBackground();
  bg.setPage(page, await loadAtlas(at(s.atlasDir) + '/' + page.atlasName));
  const given = Object.fromEntries(KEYS.filter(k => s.rings && k in s.rings).map(k => [k, s.rings[k]]));
  const options = PlaxRings.options({ fogStrength: page.fogStrength, ...given });
  const numbers = {
    options,
    gradient: PlaxRings.gradient(page.topHalfSize, page.bottomHalfSize, bg.worldWidth, options),
    bands: PlaxRings.bands(bg.layers, bg.worldWidth, options),
  };
  bg.dispose();
  const r = await fetch(`save/${s.id}.json`, { method: 'POST', body: JSON.stringify(numbers, null, 1) });
  if (!r.ok) throw new Error(`${s.id}: the save answered ${r.status}`);
  say(`${s.id}: ${numbers.bands.length} bands`);
}

async function run() {
  const scenes = (await (await fetch(at(round) + '/round.json')).json()).scenes;
  let failed = 0;
  for (const s of scenes) {
    try { await scene(s); } catch (e) { failed++; say(`FAIL ${s.id}: ${e.stack || e}`); }
  }
  say(`rings: ${scenes.length} scenes, ${failed ? 'FAIL' : 'PASS'}`);
}

run().catch(e => say('FAIL: ' + (e.stack || e)))
  .finally(() => fetch('done', { method: 'POST', body: log.join('\n') + '\n' }));
