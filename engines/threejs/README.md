# The three.js reader

`src/plax.js` draws a page saved by the parallax editor (`.jplax`, `.plaxpj`, and its libGDX `.atlas`) in three.js
(r170 or later), as the libGDX library in [`core/`](../../core) draws it. It is one ES module, ported from core through
the Godot reader (which copies core line for line): scrolling, tiling on X and Y with pads and mirrors, `flipX`/`flipY`,
`useOriginalSize`, `SEQUENCE` layers (their cycles picked as core's `SequenceCycle` picks them, `setSequenceSeed` for a
game's own seed), `SHADER` layers (`WAVE`, `FOG`, `SWELL`: core's shaders, in GLSL), the page's depth fog, the two
gradients, zoom, resizing, cross-fades (`transfertInto`) with their styles (`PlaxTransfertStyle`: depth stagger,
dissolve, through a colour, fog creep) and tints (`tintTo`). Each texture keeps its atlas's
filter, so a pixel-art page stays sharp. It does not read `.plax` (Kryo is Java's).

It was born as Crypto en visuel's `lib/plax.js` (deux-generaux r195, r449), which drew Simon's calm-day page as the
saga's sky; parallax-readers r34 brought it to core's numbers and checks it here.

**Not ported yet**, each a task of its own, in the order the rounds need them: `EMPTY` layer hooks and `PARTICLES` (r41), `FLOOR`, `LURKER` and the animated patch (r42), dolly, towers and planets (r43),
glint, `MIRROR` and detail levels (r44). A page with a layer of a kind or an effect it does not draw fails to load,
with the layer named; an `EMPTY` layer draws nothing; a glint is not drawn.

## How a game gets it

Each GitHub release carries `jks-parallax-threejs-X.Y.Z.zip`, with `jks_parallax_threejs/plax.js` at its root (and its
README, LICENSE, NOTICE). It imports `three`: the game maps that name to its own copy, with an import map or its bundler.

```js
import * as THREE from 'three';
import { PlaxBackground } from './jks_parallax_threejs/plax.js';

const renderer = new THREE.WebGLRenderer();
const bg = new PlaxBackground();                       // worldWidth 40, as libGDX's heart
await bg.load('backgrounds/calm.jplax');               // its .atlas and images next to it; or load(page, atlasDir)
bg.speedConstantX = 60;                                // auto-scroll, in the page's units per second
bg.setSize(innerWidth, innerHeight);                   // and again on each resize: the world keeps its width
renderer.setSize(innerWidth, innerHeight);
renderer.autoClear = false;
renderer.setAnimationLoop(() => {
  bg.act(1 / 60);                                      // or the frame's delta; speedConsumableX for the player's push
  renderer.clear();
  bg.render(renderer);                                 // the background, in its own camera
  renderer.render(gameScene, gameCamera);              // the game over it
});
```

`bg.transfertInto(await loadPage(url), await loadAtlas(atlasUrl), 2)` cross-fades into another page,
`bg.transfertInto(page, atlas, 3, PlaxTransfertStyle.dissolve(5, 0.08, 0.5))` dissolves into it in patches (also
`depthStagger(s)`, `throughColor([r, g, b], s)`, `fogCreep([r, g, b], s)`), `bg.tintTo([1, 0.6, 0.4, 1], 2)` tints it. `bg.scene` and `bg.camera` are there for a game that renders them itself
(call `bg.update()` first). Colours are libGDX's: no colour management on the background (its textures are
`NoColorSpace`, and `render()` draws with the output colour space set to `LinearSRGBColorSpace`, then puts the game's
back), so a page looks as it does in the editor.

## The page as the sky of a 3D world: `PlaxRings`

A game drawn in 3D can stand the page round its world instead of behind its screen
([docs/page-as-3d-sky.md](../../docs/page-as-3d-sky.md)): each layer that draws an image is an open cylinder round the
world's centre, as far as its speed says, so a camera flying through the world sees the layers slide by perspective
alone. The page's gradients are a sphere round the camera, the fog's colour under the horizon.

```js
import { PlaxBackground, PlaxRings } from './jks_parallax_threejs/plax.js';

const bg = new PlaxBackground();
await bg.load('backgrounds/calm.jplax');
const rings = new PlaxRings(bg, { front: 330, foot: 50 });   // the front band at 330 units, the page's foot at 50
scene.add(rings.group);
renderer.setAnimationLoop(() => {
  rings.update(1 / 60);                                      // acts the background (drift, fades, tint), then lays out
  renderer.render(scene, camera);                            // the camera's far plane past the farthest band
});
```

The options are core's `ParallaxRings.Options` (`front`, `far`, `foot`, `lift`, `scale`, `pageDegrees`, `fogStrength`;
the page's own fog when left out) and the drawing's: `fogColor` (`[r, g, b]`, the page's when left out), `centre`
(`{x, z}`), `melt` (the share of each band's height, from its bottom, that melts into the fog's colour: 0.22),
`horizon` (how high the fog climbs the sky, in radians: 0.12) and `renderOrder` (the first of the orders the rings
take, -1000, so they draw before the game's transparent objects). By default the bands stand at the library's depths,
`front * frontSpeed / speed` (calm-day's back layer 9.1 times as far as its front); `far: 760` compresses them into
`front..far`, as Crypto en visuel's saga sky does. `rings.set({...})` changes options; a still layer is drawn on the sky.
The bands scroll as the flat background's layers do (`speedXAtRest`, the game's speeds), fade with `transfertInto`
(a dissolve's layers by their share, without the patches), are tinted by `tintTo` and graded by `throughColor` and
`fogCreep`. Not drawn on a ring yet: a `SHADER` layer's effect (its image is drawn plain).

`PlaxRings.bands(bg.layers, bg.worldWidth, PlaxRings.options({...}))` and `PlaxRings.gradient(...)` give the numbers
alone (radius, elevations, repeat, draw order, fog per band), core's to 1e-4 (the rings round below).

## This folder

    src/plax.js            the reader: what a game copies
    tests/shots.html, .js  the frame runner: a round's stills, stepped as ParallaxShots steps them
    tests/sequence_cycle.html, .js  the cycle check: drawCycle against the JVM's picks, PlaxSequence.draw against a walk
    tests/bench.html, .js  the latency runner (engines/rounds/bench)
    tests/rings.html, .js  the rings check: PlaxRings' numbers for each scene of engines/rounds/rings
    tests/rings_lab.html, .js  the rings lab (tools/rings-lab.sh)
    tests/demo.html        Calm scrolling, T cross-fades into OneNight, SPACE stops the scroll
    tests/vendor/three/    three.js r170 (MIT), for the tests and the demo only
    tools/                 this reader's scripts, run from anywhere (each goes to the repository root first)

## Checks

- `tools/frame-check.sh [ROUND_DIR...]` — the frame check: draws each round with libGDX and with three.js in headless
  Chrome, and compares them still by still at 2/255 (`build/threejs/<round>/compare.png`, `report.txt`). Without
  arguments it plays the rounds it ports: `round1`, `conformance`, `pixelart`, `sequence`, `shaders`, `fog`,
  `transfer` and `rings` (worst still 0.24 / 255 on the GPU here, transfer's t05 after its half-transparent tint). Its `SKIP` leaves out the scenes it does not draw yet, the
  task that will named in `report.txt`: `shaders` s12-s18 (MIRROR, the glint, detail levels: r44). With `sequence` it
  also opens `tests/sequence_cycle.html`: every pick of `engines/rounds/sequence/picks.json` (the JVM's
  `SequenceCycle`) and the slots drawn against a walk over all of them. `BREAK=scroll` must fail it. CI's `threejs-frames` job runs it under `xvfb-run` (for the
  libGDX stills; Chrome is headless). Needs Chrome (or `CHROME=`), python3 with Pillow, and what
  `tools/parallax-lab-shots.sh` needs.
- The `rings` round has no stills: `./gradlew :rounds:rings` writes core's `ParallaxRings` numbers for each scene,
  `tests/rings.html` PlaxRings' (`build/threejs/rings/threejs/<id>.json`), and `tools/compare-rings.py` wants every
  number within 1e-4 (a radius 0.1 % off at the library's depths fails the three scenes drawn so, r48).
- `tools/rings-lab.sh` — the rings lab: a page of the rings round as the sky of a small valley, panel A at the
  library's depths beside panel B compressed into `front..far`, a camera flying round, sliders for every option, a
  cross-fade into the next page, Copy settings. With no argument a Chrome window (the board's Labs button: `rings-lab`);
  `--shot DIR` stills and `--clip F.mp4` a 12 s flight, headless on the GPU, failing on any console warning.
- `tools/mutate-effects.sh [MUTANT...]` — breaks one piece of the effects at a time (WAVE's ripple, FOG's patches,
  SWELL's waves, `wrappedX`, `wrappedY`, the page fog's haze; the dissolve's drift, the gradients' grade, the slots'
  stagger) and runs the round that should see it: each must FAIL (r39, r40: each failed, its worst still 7.9 to
  78.9 / 255). Refuses to start on an uncommitted `plax.js`; restores it on
  exit.
- `tools/bench.sh [OUT_JSON]` — the latency on [`engines/rounds/bench`](../rounds/bench/round.json): each scene's load
  time, then 600 frames (act, render, a one-pixel `readPixels` to finish the GPU's work), best of 3 runs, into
  `build/bench/threejs.json`. A scene with a kind it does not port is skipped and named. Not in CI.
- `tools/zip.sh [OUT_DIR]` — packs the release zip (`build/threejs-zip/` by default); the Release workflow runs it.
- `tools/run-page.sh PAGE OUT_DIR` — what both runners go through: serves the repository (`tools/serve.py`, which saves
  what the page POSTs), opens `tests/PAGE` in headless Chrome on the GPU (`tools/browser.py`; SwiftShader without one),
  and returns when the page is done.

[`tools/readers-health.sh`](../../tools/readers-health.sh) shows its verdicts and latency beside the other readers'.

## Demo

From the repository root, `python3 -m http.server 8000`, then open
<http://localhost:8000/engines/threejs/tests/demo.html> (`?page=<path>&atlas=<folder>`, both from the root, draws
another page).
