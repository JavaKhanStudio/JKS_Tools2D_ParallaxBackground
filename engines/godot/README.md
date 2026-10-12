# The Godot 4 reader

`addons/jks_parallax` draws a page saved by the parallax editor (`.jplax`, `.plaxpj`, and its libGDX `.atlas`) in
Godot 4, as the libGDX library in [`core/`](../../core) draws it. It is a GDScript port of core's
`ParallaxPageReader`, line for line: scrolling, tiling, cross-fade, tint, the page fog, `EMPTY`, `PARTICLES`,
`SHADER` (`plax_effects.gd`), `SEQUENCE`, `FLOOR`, tower and dolly layers. It does not read `.plax` (Kryo is Java's).
What each part must copy from core, and which round proves it, is in [AGENTS.md, "Runtime"](../../AGENTS.md#runtime-core).

## How a game gets it

From 2.5.0, each GitHub release carries `jks-parallax-godot-X.Y.Z.zip`, with `addons/jks_parallax/` at its root: unzip
it into the game's project. The Godot Asset Library listing (parallax:r104) is paused. How to load a page, its export
settings and what is not there yet: the root README, ["In Godot 4"](../../README.md#in-godot-4).

## This folder

    addons/jks_parallax/   the reader: what a game copies
    tests/                 the runners the frame check plays: shots.gd, particles.gd, sequence_cycle.gd; bench.gd
    demo.tscn, demo.gd     Hiver and Printemps cross-faded through the reader (SPACE, T, N)
    tools/                 this reader's checks, run from anywhere (each goes to the repository root first)

## Checks

- `tools/frame-check.sh [ROUND_DIR...]` — the frame check: draws each round in
  [`engines/rounds`](../rounds) with libGDX and with Godot, off screen in cage, and compares them still by still at
  2/255 (`build/godot/<round>/compare.png`, `report.txt`). Without arguments it plays every round. CI's `godot-frames`
  job runs it under `xvfb-run` (`ATELIER_NO_OFFSCREEN=1`). Needs `godot` 4.x (or `GODOT=`), cage, Xwayland, Pillow.
- `tools/bench.sh [OUT_JSON]` — Godot's latency on [`engines/rounds/bench`](../rounds/bench/round.json)
  (`tests/bench.gd`): each scene's load time, then 600 frames vsync off, timed by Godot's own clocks (act, and the
  viewport's measured render time on the CPU and GPU), into `build/bench/godot.json`. Off screen in cage; not in CI.
  [`tools/readers-health.sh`](../../tools/readers-health.sh) shows it beside libGDX's and jME's.
- `tools/addon-zip.sh [OUT_DIR]` — packs the release zip (`build/godot-addon/` by default); the Release workflow runs it.
- `tools/addon-zip-check.sh` — installs that zip into a blank Godot project, loads a page from `res://` and saves a frame
  (`build/godot-addon/check.png`): fails on an error or a blank frame.

## Demo

`tools/start-demo.sh godot` from the repository root (or `godot --path engines/godot`).
