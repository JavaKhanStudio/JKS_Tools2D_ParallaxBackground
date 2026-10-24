#!/usr/bin/env bash
# frame-check.sh [ROUND_DIR...] — the three.js reader (engines/threejs) against the libGDX runtime, frame by frame (r34).
#
#   engines/threejs/tools/frame-check.sh                        engines/rounds/round1, /conformance, /pixelart, /sequence,
#                                                               /shaders (s01-s11), /fog, /transfer, /effects,
#                                                               /particles (not q06, q07, q09, q10), /floor, /lurker,
#                                                               /animated (not a03, a04), /rings
#   engines/threejs/tools/frame-check.sh engines/rounds/round1
#
# For each round: renders its scenes with libGDX (tools/parallax-lab-shots.sh, 0, 6 and 12 s into the lab's scroll)
# and with three.js (engines/threejs/tests/shots.html, the same scroll) in headless Chrome (engines/threejs/tools/run-page.sh),
# then compares them pixel by pixel. Writes build/threejs/<round>/ (a link to this
# run's own folder: tools/shots-out.sh): the libGDX stills in gdx/, three.js's in threejs/, compare.png and report.txt.
# Fails when a still differs by more than THRESHOLD (mean absolute difference per channel, 0-255; default 2).
# The default rounds are those whose layer kinds the reader ports: IMAGE, EMPTY (its hooks), PARTICLES, SEQUENCE, SHADER, FLOOR,
# LURKER, the animated patch, the page fog and the transfer styles;
# a round with a kind it does not port fails in threejs.log. SKIP names a round's scenes it does not draw yet, and the
# task that will: they are not shot, and report.txt names them SKIPPED (tools/compare-parallax-frames.py). With engines/rounds/sequence it runs the cycle check too
# (engines/threejs/tests/sequence_cycle.html: the picks against picks.json, the slots drawn against a walk over all). Chrome draws on the GPU when tools/browser.py finds one, else on SwiftShader (CI), where
# conformance's worst still was 0.25 / 255 (0.17 on the GPU).
# engines/rounds/rings has no stills (r48): core's ParallaxRings numbers (./gradlew :rounds:rings) against PlaxRings'
# (engines/threejs/tests/rings.html), every number within 1e-4 (tools/compare-rings.py).
# BREAK=scroll draws three.js with a scroll 10 % too fast: it must FAIL (the threshold's negative control); BREAK=hooks
# draws no EMPTY layer: engines/rounds/effects must FAIL. engines/rounds/particles is compared by pixels, as jME's: the
# PARTICLES layers draw libGDX's effects (PlaxParticles), their random seeded from the scene's id as Round.seedRandom,
# and the round fails past its pixelsOff; BREAK=particles (no flakes), anchor (VIEW drawn as LAYER) and blend (every
# emitter blended normally) must FAIL it. BREAK=plant (no layer planted on a floor) must FAIL floor, BREAK=lurker
# (every figure always there) lurker, BREAK=anim (every patch at its first frame) animated (r42).
# Needs Chrome (CHROME=, or google-chrome, chromium, chromium-browser on the PATH), python3 with Pillow.
set -u
cd "$(dirname "$0")/../../.." || exit 1
ROOT="$PWD"
. tools/shots-out.sh
THRESHOLD="${THRESHOLD:-2}"
ROUNDS=("$@")
[ ${#ROUNDS[@]} -eq 0 ] && ROUNDS=(engines/rounds/round1 engines/rounds/conformance engines/rounds/pixelart engines/rounds/sequence
	engines/rounds/shaders engines/rounds/fog engines/rounds/transfer engines/rounds/effects engines/rounds/particles
	engines/rounds/floor engines/rounds/lurker engines/rounds/animated engines/rounds/rings)
# MIRROR, the glint and detail levels: r44. The dolly and planets: r43.
declare -A SKIP=([engines/rounds/shaders]="s12 s13 s14 s15 s16 s17 s18|r44" [engines/rounds/particles]="q06 q07 q09 q10|r43"
	[engines/rounds/animated]="a03 a04|r43")
status=0
for ROUND in "${ROUNDS[@]}"; do
	OUT=$(shots_out "$ROOT/build/threejs" "$ROUND"); mkdir -p "$OUT/threejs"
	if [ "$(basename "$ROUND")" = rings ]; then
		./gradlew -q :rounds:rings -Pround="$ROUND" -Pout="$OUT" >"$OUT/gdx.log" 2>&1 || { echo "FAIL: core's rings numbers, see $OUT/gdx.log"; status=1; continue; }
		engines/threejs/tools/run-page.sh "rings.html?round=$ROUND" "$OUT/threejs"
		mv "$OUT/threejs/threejs.log" "$OUT/threejs.log" 2>/dev/null
		grep -h "^FAIL" "$OUT/threejs.log" 2>/dev/null
		python3 tools/compare-rings.py "$ROUND" "$OUT" threejs || status=1
		continue
	fi
	tools/parallax-lab-shots.sh "$ROUND" "$OUT/gdx" >/dev/null || { echo "libGDX shots failed: $OUT/gdx/lab.log"; exit 1; }
	SKIPPED=${SKIP[${ROUND%/}]:-}
	SKIP_SCENES=${SKIPPED%|*} SKIP_WHY=${SKIPPED#*|}
	export SKIP_SCENES SKIP_WHY
	engines/threejs/tools/run-page.sh "shots.html?round=$ROUND&break=${BREAK:-}&skip=${SKIP_SCENES// /,}" "$OUT/threejs"
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
