#!/usr/bin/env bash
# bench-runs.sh — sourced by the benches that run in cage (r537): tools/parallax-bench.sh (libGDX),
# engines/godot/tools/bench.sh and engines/jme/tools/bench.sh. Not run alone.
#
#   bench_runs OUT LOG RUN     RUN a bash command line with @PART@ where the run writes its JSON
#
# The GPU is shared with the board's other sessions: one run measured 0.08 ms a frame, the next 2 ms. bench_runs runs RUN
# RUNS times (3), each in cage's headless display on a nested Xwayland (ATELIER_NO_OFFSCREEN=1: on $DISPLAY), keeps each
# scene's best through tools/readers-health/best-of.py into OUT and removes the parts. A run that writes nothing ends the
# script with LOG's tail. Called from the repository root.
bench_runs() {
	local out=$1 log=$2 run=$3 i parts=()
	for i in $(seq "${RUNS:-3}"); do
		rm -f "$out.$i"
		if [ "${ATELIER_NO_OFFSCREEN:-0}" = 1 ]; then
			timeout 300 bash -c "${run//@PART@/$out.$i}"
		else
			WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 300 cage -- tools/nested-x.sh 1280x1280 bash -c "${run//@PART@/$out.$i}" >"${out%.json}.cage.log" 2>&1
		fi
		[ -s "$out.$i" ] || { echo "no bench:"; tail -20 "$log"; exit 1; }
		parts+=("$out.$i")
	done
	python3 tools/readers-health/best-of.py "$out" "${parts[@]}" || exit 1
	rm -f "${parts[@]}"
	echo "bench: $out"
}
