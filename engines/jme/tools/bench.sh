#!/usr/bin/env bash
# bench.sh [OUT_JSON] — jME's latency on the bench round (parallax-readers:r19), off Simon's screen.
#
#   engines/jme/tools/bench.sh                   engines/rounds/bench into build/bench/jme.json
#
# Runs engines/jme/tests/.../JmeParallaxBench (load time and frame times of each scene, vsync off) in cage's headless
# display on a nested Xwayland, as engines/jme/tools/frame-check.sh runs its shots; ATELIER_NO_OFFSCREEN=1 runs it on
# $DISPLAY. Each scene's best of RUNS runs (3) is kept. tools/parallax-bench.sh is libGDX's; tools/readers-health.sh shows both.
set -u
OUT="${1:+$(realpath -m "$1")}"  # a given OUT is the caller's path, the default the root's (r25)
cd "$(dirname "$0")/../../.." || exit 1
OUT="${OUT:-$PWD/build/bench/jme.json}"
mkdir -p "$(dirname "$OUT")"
./gradlew -q :jme:shotsClasspath || exit 1
CP="$(cat engines/jme/build/shots.classpath)"
LOG="${OUT%.json}.log"
# X11, cage's nested one, as frame-check.sh says (r234): no WAYLAND_DISPLAY, XDG_SESSION_TYPE=x11.
RUN="env -u WAYLAND_DISPLAY XDG_SESSION_TYPE=x11 java -cp \"$CP\" jks.tools2d.parallax.jme.JmeParallaxBench engines/rounds/bench \"@PART@\" >\"$LOG\" 2>&1"
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
