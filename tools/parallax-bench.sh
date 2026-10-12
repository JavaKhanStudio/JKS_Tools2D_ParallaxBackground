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
# The GPU is shared with the board's other sessions: one run measured 0.08 ms a frame, the next 2 ms. RUNS runs (3),
# each scene's best kept by tools/readers-health/best-of.py.
PARTS=()
for i in $(seq "${RUNS:-3}"); do
	rm -f "$OUT.$i"
	if [ "${ATELIER_NO_OFFSCREEN:-0}" = 1 ]; then
		timeout 300 bash -c "${RUN//@PART@/$OUT.$i}"
	else
		WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 300 cage -- tools/nested-x.sh 1280x1280 bash -c "${RUN//@PART@/$OUT.$i}" >"${OUT%.json}.cage.log" 2>&1
	fi
	[ -s "$OUT.$i" ] || { echo "no bench:"; tail -20 "$LOG"; exit 1; }
	PARTS+=("$OUT.$i")
done
python3 tools/readers-health/best-of.py "$OUT" "${PARTS[@]}" || exit 1
rm -f "${PARTS[@]}"
echo "bench: $OUT"
