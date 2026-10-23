# The three.js reader

`src/plax.js` draws a page saved by the parallax editor (`.jplax`, `.plaxpj`, and its libGDX `.atlas`) in three.js
(r170 or later), as the libGDX library in [`core/`](../../core) draws it. It is one ES module, ported from core through
the Godot reader (which copies core line for line): scrolling, tiling on X and Y with pads and mirrors, `flipX`/`flipY`,
`useOriginalSize`, `SEQUENCE` layers (their cycles picked as core's `SequenceCycle` picks them, `setSequenceSeed` for a
game's own seed), the two gradients, zoom, resizing, plain cross-fades (`transfertInto`) and tints (`tintTo`). Each
texture keeps its atlas's filter, so a pixel-art page stays sharp. It does not read `.plax` (Kryo is Java's).

It was born as Crypto en visuel's `lib/plax.js` (deux-generaux r195, r449), which drew Simon's calm-day page as the
saga's sky; parallax-readers r34 brought it to core's numbers and checks it here.

**Not ported yet**, each a task of its own, in the order the rounds need them: `SHADER` and the page fog (r39), the transfer styles (r40), `EMPTY` layer hooks and `PARTICLES` (r41), `FLOOR`, `LURKER` and the animated
patch (r42), dolly, towers and planets (r43), glint, `MIRROR` and detail levels (r44). A page with a layer of a kind it
does not draw fails to load, with the layer named; an `EMPTY` layer draws nothing.

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
`bg.tintTo([1, 0.6, 0.4, 1], 2)` tints it. `bg.scene` and `bg.camera` are there for a game that renders them itself
(call `bg.update()` first). Colours are libGDX's: no colour management on the background (its textures are
`NoColorSpace`, and `render()` draws with the output colour space set to `LinearSRGBColorSpace`, then puts the game's
back), so a page looks as it does in the editor.

## This folder

    src/plax.js            the reader: what a game copies
    tests/shots.html, .js  the frame runner: a round's stills, stepped as ParallaxShots steps them
    tests/sequence_cycle.html, .js  the cycle check: drawCycle against the JVM's picks, PlaxSequence.draw against a walk
    tests/bench.html, .js  the latency runner (engines/rounds/bench)
    tests/demo.html        Calm scrolling, T cross-fades into OneNight, SPACE stops the scroll
    tests/vendor/three/    three.js r170 (MIT), for the tests and the demo only
    tools/                 this reader's scripts, run from anywhere (each goes to the repository root first)

## Checks

- `tools/frame-check.sh [ROUND_DIR...]` — the frame check: draws each round with libGDX and with three.js in headless
  Chrome, and compares them still by still at 2/255 (`build/threejs/<round>/compare.png`, `report.txt`). Without
  arguments it plays the rounds it ports: `round1`, `conformance`, `pixelart`, `sequence` (worst still 0.26 / 255 on the
  GPU here, sequence's cross-fade). With `sequence` it also opens `tests/sequence_cycle.html`: every pick of
  `engines/rounds/sequence/picks.json` (the JVM's `SequenceCycle`) and the slots drawn against a walk over all of them. `BREAK=scroll` must fail it. CI's `threejs-frames` job runs it under `xvfb-run` (for the
  libGDX stills; Chrome is headless). Needs Chrome (or `CHROME=`), python3 with Pillow, and what
  `tools/parallax-lab-shots.sh` needs.
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
