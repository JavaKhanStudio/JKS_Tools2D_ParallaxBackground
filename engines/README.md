# Readers

A reader draws a page saved by the parallax editor in one engine. The libGDX library in [`core/`](../core) is the
reference; every other reader lives here, one folder each, and is checked frame by frame against it.

| Folder | Engine | A game gets it as | Its check |
|--------|--------|-------------------|-----------|
| [`godot/`](godot/README.md) | Godot 4, a GDScript port of core | `jks-parallax-godot-X.Y.Z.zip` on each GitHub release | `godot/tools/frame-check.sh`, CI job `godot-frames` |
| [`jme/`](jme/README.md) | jMonkeyEngine 3, runs core as is | `parallax-background-jme` on Maven Central | `jme/tools/frame-check.sh`, CI job `jme-frames` |
| [`rounds/`](rounds) | none: the rounds every reader is checked on, and `:rounds` (`reader/`), their `round.json` read for the Java runners | | |

**How they all are:** `tools/readers-health.sh` builds `build/readers-health/index.html` (parallax-readers:r17): each
reader's checks and how many stills they compare, when they last ran here and in CI and what they said, every round's
verdict per reader, and each reader's latency on [`rounds/bench`](rounds/bench/round.json) (few and many layers, plain
and with effects, a cross-fade: load time, frame time). It reads what the checks left and runs nothing;
`--run [gdx godot jme]` runs them first (about 10 minutes), `--shot F.png` screenshots it, `--open` opens it.
On the board it is the header's **Site** button: `--serve 8797` serves it, rebuilt on each load.

Unity is next (parallax:r103, paused); what it would take is in
[docs/other-engines.md](../docs/other-engines.md#unity-and-unreal-what-they-would-take).

## What a new reader must have

Copy the shape of `jme/` (a Java engine) or `godot/` (any other one). A reader is done when all of these hold:

1. **Its folder, `engines/<reader>/`,** with what a game copies (`jme/src/`, `godot/addons/jks_parallax/`) kept apart
   from what checks it (`tests/`, `tools/`). Nothing of the reader's lives in the shared `tools/`.
2. **What it reads and ports.** It reads `.jplax` and `.plaxpj` with their libGDX `.atlas`, and ports core's drawing:
   [docs/other-engines.md](../docs/other-engines.md), "What an engine has to read" and "What an engine has to do".
3. **Its frame check, `engines/<reader>/tools/frame-check.sh [ROUND_DIR...]`,** over the shared rounds in
   [`rounds/`](rounds), against the reference stills. It goes to the repository root first
   (`cd "$(dirname "$0")/../../.."`), takes its output folder from `tools/shots-out.sh`
   (`build/<reader>/<round>`), draws the reference stills with `tools/parallax-lab-shots.sh ROUND OUT/gdx`, has its
   own runner save `OUT/<reader>/<scene>-t0.png`, `-t6.png` and `-t12.png` (the scroll of `godot/tests/shots.gd` and
   `jme/tests/.../JmeParallaxShots.java`), and ends with `python3 tools/compare-parallax-frames.py ROUND OUT 2 <reader>`.
   Its window opens in cage through `tools/nested-x.sh`, or on `$DISPLAY` with `ATELIER_NO_OFFSCREEN=1`;
   `tools/offscreen-lint.sh` reads `engines/*/tools/*.sh` and refuses one that opens a window outside cage.
   A runner that is not Java parses `round.json` itself, as `godot/tests/shots.gd` does: a new round field goes in
   `rounds/reader` (`Round`) and in every runner that plays it. A negative control proves the threshold bites
   (`BREAK=scroll` makes jME's check FAIL).
4. **Its README, `engines/<reader>/README.md`:** what it is and what it ports, how a game gets it, its checks and demo.
   Add it to the table above, and an "In <engine>" section and code-map lines to the root [README](../README.md).
5. **Its own CI job, `<reader>-frames`,** in [`.github/workflows/ci.yml`](../.github/workflows/ci.yml), the shape of
   `jme-frames`: Xvfb, Mesa and Pillow, then `xvfb-run -a -s "-screen 0 1920x1440x24" engines/<reader>/tools/frame-check.sh`
   with `ATELIER_NO_OFFSCREEN=1` and `LIBGL_ALWAYS_SOFTWARE=1`, and `build/<reader>/*` uploaded on failure too.
   Also gated by `tools/library-alone-check.sh`, and named in `tools/ci-watch.sh`'s "Reproduce" list.
6. **Its release artifact,** versioned with `gradle.properties`' `version`: a step in
   [`.github/workflows/release.yml`](../.github/workflows/release.yml)'s "Package the downloads" (a script in
   `engines/<reader>/tools/`, as `godot/tools/addon-zip.sh`, or a Gradle publication, as `:jme`), and its line in
   [RELEASING.md](../RELEASING.md), "Releasing X.Y.Z".
7. **Its line on the dashboard:** an entry in `READERS` of
   [`tools/readers-health/health.py`](../tools/readers-health/health.py) (its check, its CI jobs, what makes a run
   stale), and a bench runner writing `build/bench/<reader>.json` in the shape of `shots/`'s `ParallaxBench` from the
   bench round, run by `tools/readers-health.sh --run`.
8. **Its duties in [AGENTS.md](../AGENTS.md):** a row in "Modules", and a bullet in "Runtime (`core`)" naming which
   core classes it copies line for line and which round proves each, as Godot's and jME's do; its CI job beside theirs.

Out of the folder's hands: a new GitHub repository. Simon picked one repository with a sub project per reader
(parallax-readers:r1); ask him before creating another.
