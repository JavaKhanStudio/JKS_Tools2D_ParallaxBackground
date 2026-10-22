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

    src/            the reader, published as parallax-background-jme: public API
    resources/      its effect material and shaders
    tests/          the frame runner (JmeParallaxShots), the bench (JmeParallaxBench) and the demo (JmeParallaxDemo): not published
    tools/          this reader's checks, run from anywhere (each goes to the repository root first)

## Checks

- `tools/frame-check.sh [ROUND_DIR...]` — the frame check: draws rounds of [`engines/rounds`](../rounds) with libGDX
  and with jME, off screen in cage, and compares them still by still at 2/255 (`build/jme/<round>/compare.png`,
  `report.txt`). Without arguments it plays every round jME draws: all of Godot's but `engines/rounds/particles`, jME
  drawing no `PARTICLES` layer yet (parallax-readers:r31). `BREAK=scroll` draws jME 10 % too fast: it must
  FAIL. CI's `jme-frames` job runs it under `xvfb-run` (`ATELIER_NO_OFFSCREEN=1`). Needs cage, Xwayland, Pillow.
- `tools/bench.sh [OUT_JSON]` — jME's latency on [`engines/rounds/bench`](../rounds/bench/round.json)
  (`JmeParallaxBench`): each scene's load time, then 600 frames vsync off, act and render with the GPU finished (no swap, as libGDX's),
  into `build/bench/jme.json`. Off screen in cage; not in CI (llvmpipe's numbers mean nothing).
  [`tools/readers-health.sh`](../../tools/readers-health.sh) shows it beside libGDX's.
- `tools/probe/` — a fresh jME game that only knows the published artifacts:
  `./gradlew :core:publishToMavenLocal :jme:publishToMavenLocal && ./gradlew -p engines/jme/tools/probe run`
  (`-Pparallax=X.Y.Z-SNAPSHOT` for another version).

## Demo

`./gradlew :jme:run` from the repository root.
