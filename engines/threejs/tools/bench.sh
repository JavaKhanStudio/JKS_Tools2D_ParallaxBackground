#!/usr/bin/env bash
# bench.sh [OUT_JSON] — the three.js reader's latency on the bench round (parallax-readers:r20), in headless Chrome.
#
#   engines/threejs/tools/bench.sh                 engines/rounds/bench into build/bench/threejs.json
#
# Runs engines/threejs/tests/bench.js (load time and frame times of each scene it can draw) through
# engines/threejs/tools/run-page.sh, RUNS times (3), each scene's best kept by tools/readers-health/best-of.py: the GPU
# is shared with the board's other sessions. tools/parallax-bench.sh is libGDX's; tools/readers-health.sh shows them
# side by side. Needs Chrome and python3.
set -u
OUT="${1:+$(realpath -m "$1")}"  # a given OUT is the caller's path, the default the root's (r25)
cd "$(dirname "$0")/../../.." || exit 1
OUT="${OUT:-$PWD/build/bench/threejs.json}"
mkdir -p "$(dirname "$OUT")"
LOG="${OUT%.json}.log"
RUNDIR=$(mktemp -d)
PARTS=()
for i in $(seq "${RUNS:-3}"); do
	engines/threejs/tools/run-page.sh "bench.html?round=engines/rounds/bench" "$RUNDIR/$i"
	cp "$RUNDIR/$i/threejs.log" "$LOG" 2>/dev/null
	[ -s "$RUNDIR/$i/bench.json" ] || { echo "no bench:"; tail -20 "$LOG"; rm -rf "$RUNDIR"; exit 1; }
	PARTS+=("$RUNDIR/$i/bench.json")
done
python3 tools/readers-health/best-of.py "$OUT" "${PARTS[@]}" || { rm -rf "$RUNDIR"; exit 1; }
rm -rf "$RUNDIR"
grep -h "^skipped" "$LOG"
echo "bench: $OUT"
