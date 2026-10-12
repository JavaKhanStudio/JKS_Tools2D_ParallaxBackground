#!/usr/bin/env bash
# bench.sh [OUT_JSON] — Godot's latency on the bench round (parallax-readers:r20), off Simon's screen.
#
#   engines/godot/tools/bench.sh                 engines/rounds/bench into build/bench/godot.json
#
# Runs engines/godot/tests/bench.gd (load time and frame times of each scene, vsync off, Godot's own render timers) in
# cage's headless display on a nested Xwayland, as frame-check.sh runs its shots; ATELIER_NO_OFFSCREEN=1 runs it on
# $DISPLAY. Each scene's best of RUNS runs (3) is kept. tools/parallax-bench.sh is libGDX's; tools/readers-health.sh
# shows them side by side. Needs godot 4.x (GODOT=).
set -u
OUT="${1:+$(realpath -m "$1")}"  # a given OUT is the caller's path, the default the root's (r25)
cd "$(dirname "$0")/../../.." || exit 1
ROOT="$PWD"
OUT="${OUT:-$PWD/build/bench/godot.json}"
mkdir -p "$(dirname "$OUT")"
GODOT="${GODOT:-godot}"
LOG="${OUT%.json}.log"
"$GODOT" --headless --path engines/godot --import >/dev/null 2>&1
RUN="$GODOT --path \"$ROOT/engines/godot\" --resolution 1280x720 res://tests/bench.tscn -- \"$ROOT\" engines/rounds/bench \"@PART@\" >\"$LOG\" 2>&1"
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
