#!/usr/bin/env bash
# parallax-bench.sh [ROUND_DIR] [OUT_JSON] — libGDX's latency on the bench round (parallax-readers:r17), off Simon's screen.
#
#   tools/parallax-bench.sh                      engines/rounds/bench into build/bench/gdx.json
#
# Runs shots/'s ParallaxBench (load time and frame times of each scene, vsync off) from its installDist, in cage's
# headless display on a nested Xwayland as tools/parallax-lab-shots.sh does. ATELIER_NO_OFFSCREEN=1 runs it on $DISPLAY.
# The numbers are the GPU's that drew them: the file names its renderer, and keeps each scene's best of RUNS runs (3).
set -u
cd "$(dirname "$0")/.." || exit 1
ROUND="${1:-engines/rounds/bench}" OUT="$(realpath -m "${2:-build/bench/gdx.json}")"
mkdir -p "$(dirname "$OUT")"
./gradlew -q :shots:installDist >/dev/null || exit 1
CP="$PWD/shots/build/install/shots/lib/*"
LOG="${OUT%.json}.log"
RUN="java -cp \"$CP\" jks.tools2d.parallax.shots.ParallaxBench \"$ROUND\" \"@PART@\" >\"$LOG\" 2>&1"
# RUNS runs (3) in cage, each scene's best kept (tools/bench-runs.sh).
# shellcheck source=tools/bench-runs.sh
. tools/bench-runs.sh
bench_runs "$OUT" "$LOG" "$RUN"
