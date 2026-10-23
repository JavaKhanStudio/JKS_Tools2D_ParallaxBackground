#!/usr/bin/env bash
# frame-check.sh [ROUND_DIR...] — the three.js reader (engines/threejs) against the libGDX runtime, frame by frame (r34).
#
#   engines/threejs/tools/frame-check.sh                        engines/rounds/round1, /conformance, /pixelart, /sequence
#   engines/threejs/tools/frame-check.sh engines/rounds/round1
#
# For each round: renders its scenes with libGDX (tools/parallax-lab-shots.sh, 0, 6 and 12 s into the lab's scroll)
# and with three.js (engines/threejs/tests/shots.html, the same scroll) in headless Chrome (engines/threejs/tools/run-page.sh),
# then compares them pixel by pixel. Writes build/threejs/<round>/ (a link to this
# run's own folder: tools/shots-out.sh): the libGDX stills in gdx/, three.js's in threejs/, compare.png and report.txt.
# Fails when a still differs by more than THRESHOLD (mean absolute difference per channel, 0-255; default 2).
# The default rounds are those whose layer kinds the reader ports: IMAGE (and EMPTY), SEQUENCE; a round with a kind it
# does not port fails in threejs.log. With engines/rounds/sequence it runs the cycle check too
# (engines/threejs/tests/sequence_cycle.html: the picks against picks.json, the slots drawn against a walk over all). Chrome draws on the GPU when tools/browser.py finds one, else on SwiftShader (CI), where
# conformance's worst still was 0.25 / 255 (0.17 on the GPU).
# BREAK=scroll draws three.js with a scroll 10 % too fast: it must FAIL (the threshold's negative control).
# Needs Chrome (CHROME=, or google-chrome, chromium, chromium-browser on the PATH), python3 with Pillow.
set -u
cd "$(dirname "$0")/../../.." || exit 1
ROOT="$PWD"
. tools/shots-out.sh
THRESHOLD="${THRESHOLD:-2}"
ROUNDS=("$@")
[ ${#ROUNDS[@]} -eq 0 ] && ROUNDS=(engines/rounds/round1 engines/rounds/conformance engines/rounds/pixelart engines/rounds/sequence)
status=0
for ROUND in "${ROUNDS[@]}"; do
	OUT=$(shots_out "$ROOT/build/threejs" "$ROUND"); mkdir -p "$OUT/threejs"
	tools/parallax-lab-shots.sh "$ROUND" "$OUT/gdx" >/dev/null || { echo "libGDX shots failed: $OUT/gdx/lab.log"; exit 1; }
	engines/threejs/tools/run-page.sh "shots.html?round=$ROUND&break=${BREAK:-}" "$OUT/threejs"
	mv "$OUT/threejs/threejs.log" "$OUT/threejs.log" 2>/dev/null
	grep -h "^FAIL\|^renderer:" "$OUT/threejs.log" 2>/dev/null
	grep -q "^shots: PASS" "$OUT/threejs.log" 2>/dev/null || { echo "FAIL: three.js shots, see $OUT/threejs.log"; status=1; }
	python3 tools/compare-parallax-frames.py "$ROUND" "$OUT" "$THRESHOLD" threejs || status=1
	if [ "${ROUND%/}" = engines/rounds/sequence ]; then
		engines/threejs/tools/run-page.sh sequence_cycle.html "$OUT/cycle"
		grep -h "^FAIL\|^sequence_cycle:" "$OUT/cycle/threejs.log" 2>/dev/null
		grep -q "^sequence_cycle: .*PASS" "$OUT/cycle/threejs.log" 2>/dev/null || { echo "FAIL: the cycle check, see $OUT/cycle/threejs.log"; status=1; }
	fi
done
exit $status
