# The jMonkeyEngine 3 reader

`:jme` draws a page saved by the parallax editor in jMonkeyEngine 3. jME is a Java engine, so it runs core's own
`ParallaxPageReader` as is (`.plax`, `.jplax` and `.plaxpj` all load): `JmeBatch` implements libGDX's `Batch` with jME
meshes, and `PlaxBackground` is the `AppState` a game attaches. A `SHADER` layer's effects are `JmeLayerEffects`
(`resources/.../ParallaxEffect.j3md`), the same lines as core's `GdxLayerEffects`. No libGDX backend runs: a core call
that reaches one breaks here. What must stay in step with core, and which round proves it, is in
[AGENTS.md, "Runtime"](../../AGENTS.md#runtime-core).

## How a game gets it

`io.github.javakhanstudio:parallax-background-jme` on Maven Central, released with the library under the same version
from 2.5.0 on. The dependency, the setup and the gamma note: the root README,
["In jMonkeyEngine 3"](../../README.md#in-jmonkeyengine-3).

## This folder

    src/            the reader, published as parallax-background-jme: public API (PlaxBackground, PlaxRings)
    resources/      its materials and shaders: the effects', the rings' and their sky's
    tests/          the frame runner (JmeParallaxShots), the rings round (JmeRingsRound), the rings lab (JmeRingsLab), the
                    bench (JmeParallaxBench) and the demo (JmeParallaxDemo): not published
    tools/          this reader's checks, run from anywhere (each goes to the repository root first)

## Checks

- `tools/frame-check.sh [ROUND_DIR...]` — the frame check: draws rounds of [`engines/rounds`](../rounds) with libGDX
  and with jME, off screen in cage, and compares them still by still at 2/255 (`build/jme/<round>/compare.png`,
  `report.txt`). Without arguments it plays every round Godot plays, `engines/rounds/particles` by pixels too: both
  runners seed the particles' random (parallax-readers:r31). `BREAK=scroll` draws jME 10 % too fast: it must
  FAIL; `BREAK=particles`, `anchor` and `blend` must fail the particles round. `engines/rounds/rings` has no stills:
  `JmeRingsRound` (headless) loads each page as a game does and writes the numbers `PlaxRings` laid out, compared with
  core's by `tools/compare-rings.py` within 1e-4; `BREAK=rings` (each front 1 % out) must FAIL it.
- `tools/rings-lab.sh` — the rings lab (below): a window, or `--shot DIR` off screen in cage. `tools/rings-sheet.py`
  puts its stills beside three.js's lab's (`engines/threejs/tools/rings-lab.sh --shot`): three.js | jME | difference. CI's `jme-frames` job runs it under `xvfb-run` (`ATELIER_NO_OFFSCREEN=1`). Needs cage, Xwayland, Pillow.
- `tools/bench.sh [OUT_JSON]` — jME's latency on [`engines/rounds/bench`](../rounds/bench/round.json)
  (`JmeParallaxBench`): each scene's load time, then 600 frames vsync off, act and render with the GPU finished (no swap, as libGDX's),
  into `build/bench/jme.json`. Off screen in cage; not in CI (llvmpipe's numbers mean nothing).
  [`tools/readers-health.sh`](../../tools/readers-health.sh) shows it beside libGDX's.
- `tools/probe/` — a fresh jME game that only knows the published artifacts:
  `./gradlew :core:publishToMavenLocal :jme:publishToMavenLocal && ./gradlew -p engines/jme/tools/probe run`
  (`-Pparallax=X.Y.Z-SNAPSHOT` for another version).

## The page as the sky of a 3D world

`PlaxRings` (parallax-readers r50, [docs/page-as-3d-sky.md](../../docs/page-as-3d-sky.md)) is a jME `Node` that draws
a `PlaxBackground`'s page round the world: each layer that draws an image an open cylinder round the node, its radius
from its speed (core's `ParallaxRings`, called as is, no port), so a camera flying through the world sees the layers
slide by perspective alone; the page's gradients a sphere round the camera, the fog under the horizon.

    PlaxBackground bg = new PlaxBackground();
    stateManager.attach(bg);
    bg.setEnabled(false);                      // the rings draw the page; the flat background only moves
    bg.setPage(page, atlas);
    PlaxRings rings = new PlaxRings(assetManager, bg);
    rings.front = 330; rings.foot = 50;        // or far = 760, lift = -2, scale = 0.7, as the saga's sky
    rootNode.attachChild(rings);
    PlaxRings.sortIn(viewPort);                // the bands drawn first, far first
    // each frame: rings.update(tpf);          // acts the background (drift, fades, tint), then lays the rings out

Its fields are three.js's options: `front`, `far`, `foot`, `lift`, `scale`, `pageDegrees`, `fogStrength` (NaN: the
page's), `fogColor` (null: the page's), `melt`, `eyeMelt`, `horizon` and `tintSky`. The bands are in the Transparent
bucket, depth tested and writing no depth (the world drawn before them stays in front); they share a centre, so
`sortIn` gives that bucket a comparator that draws them before the rest, far first. The sky is in the Sky bucket. A
band's image is each tile core's own `ParallaxLayer.draw` lays out (regions inset by half a texel, trims, flips, a
SEQUENCE layer's cycle), recorded and curved round the ring; a still layer is on the sky round the camera. The scroll,
cross-fades (every transfert style: a fade, a stagger, a dissolve by its share, through a colour) and the tint are the
background's reader's. FLOOR, the dolly, towers, the planet and an animated patch are 2D ways to fake depth: a band
draws its layer's image plain, as three.js's do.

**The lab**: `tools/rings-lab.sh` (the board's `jme-rings-lab`), three.js's rings lab in jME, opening at Simon's look
on r48: a valley, a ridge at the page's foot, a camera flying round; panel A at the library's depths, B compressed into
front..far (a click on a panel zooms it). On the left the view pick, the scene, then every knob: up/down pick (or a
click), left/right or the wheel move (shift: ten steps), V view, N next scene, F a cross-fade into it, Space pause, R
reset, C Copy settings (JSON on the clipboard and in `build/jme/rings-lab/settings.json`). Its ground is lit as
three.js's lab lights it, so beside three.js's stills (`tools/rings-sheet.py`) the sky and bands differ by 0.05 / 255
and the whole panel by 2.3 (the ground's fog, mixed in sRGB here, in linear there).

## Demo

`./gradlew :jme:run` from the repository root.
