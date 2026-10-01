#!/usr/bin/env bash
# godot-parallax-shots.sh [ROUND_DIR] — the Godot reader (engines/godot) against the libGDX runtime, frame by frame (r87).
#
#   tools/godot-parallax-shots.sh                          engines/godot/tests/round1, /conformance, /transfer, /pixelart, /effects, /particles, /shaders, /sequence
#   tools/godot-parallax-shots.sh engines/godot/tests/round1
#
# For each round: renders its scenes with libGDX (tools/parallax-lab-shots.sh, 0, 6 and 12 s into the lab's scroll)
# and with Godot (engines/godot/tests/shots.gd, the same scroll), off screen in cage's headless display on a nested
# Xwayland, then compares them pixel by pixel. The transfer round starts a cross-fade or a tint 4 s in, so t6 is
# mid-fade and t12 after it. Writes build/godot/<round>/ (a link to this run's own folder: tools/shots-out.sh): the libGDX stills in gdx/, Godot's in godot/,
# compare.png (libGDX | Godot | difference x4, per still) and report.txt. Fails when a still differs by more than
# THRESHOLD (mean absolute difference per channel, 0-255; default 2). Needs godot 4.x, cage, Xwayland, python3 with
# Pillow. ATELIER_NO_OFFSCREEN=1 draws both on $DISPLAY instead of cage: CI's "Godot frames against libGDX" job runs it
# so under xvfb-run on Mesa llvmpipe, where the worst still was 0.75 / 255 (r96), so the threshold holds there too.
# Its screen is 1920x1440: a "resize" scene asks for a window up to 1280 tall.
# The particles round (r179) is not compared by pixels, each engine drawing its own particle system:
# engines/godot/tests/particles.gd checks where Godot puts the particle nodes, saves its stills in godot/, and fails on
# a FAIL in godot.log. The sequence round (r183) first runs engines/godot/tests/sequence_cycle.gd: Godot's cycle
# generator against the JVM's picks.
set -u
cd "$(dirname "$0")/.." || exit 1
ROOT="$PWD"
. tools/shots-out.sh
THRESHOLD="${THRESHOLD:-2}"
ROUNDS=("$@")
[ ${#ROUNDS[@]} -eq 0 ] && ROUNDS=(engines/godot/tests/round1 engines/godot/tests/conformance engines/godot/tests/transfer engines/godot/tests/pixelart engines/godot/tests/effects engines/godot/tests/particles engines/godot/tests/shaders engines/godot/tests/sequence)
GODOT="${GODOT:-godot}"
status=0
for ROUND in "${ROUNDS[@]}"; do
	OUT=$(shots_out "$ROOT/build/godot" "$ROUND"); mkdir -p "$OUT/godot"
	PARTICLES=0; [ "$(basename "$ROUND")" = particles ] && PARTICLES=1
	if [ $PARTICLES = 0 ]; then
		tools/parallax-lab-shots.sh "$ROUND" "$OUT/gdx" >/dev/null || { echo "libGDX shots failed: $OUT/gdx/lab.log"; exit 1; }
	fi
	"$GODOT" --headless --path engines/godot --import >/dev/null 2>&1
	if [ "$(basename "$ROUND")" = sequence ]; then
		# Godot's cycle generator against the JVM's picks (picks.json), before the frames: a pick off shows here first.
		"$GODOT" --headless --path engines/godot --script res://tests/sequence_cycle.gd >"$OUT/sequence_cycle.log" 2>&1
		grep -h "^FAIL\|^sequence_cycle:" "$OUT/sequence_cycle.log"
		grep -q "^sequence_cycle: .*PASS" "$OUT/sequence_cycle.log" || { echo "FAIL: sequence_cycle, see $OUT/sequence_cycle.log"; status=1; }
	fi
	SCENE=res://tests/shots.tscn; [ $PARTICLES = 1 ] && SCENE=res://tests/particles.tscn
	RUN="$GODOT --path \"$ROOT/engines/godot\" --resolution 1280x720 $SCENE -- \"$ROOT\" \"$ROUND\" \"$OUT/godot\" >\"$OUT/godot.log\" 2>&1"
	if [ "${ATELIER_NO_OFFSCREEN:-0}" = 1 ]; then
		timeout 300 bash -c "$RUN"
	else
		WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 300 cage -- tools/nested-x.sh 1280x1280 bash -c "$RUN" >"$OUT/cage.log" 2>&1
	fi
	if [ $PARTICLES = 1 ]; then
		grep -h "^FAIL\|^particles: [PF]A" "$OUT/godot.log" | tee "$OUT/report.txt"
		grep -q "^particles: PASS" "$OUT/godot.log" || { echo "FAIL: particles round, see $OUT/godot.log"; status=1; }
		continue
	fi
	python3 tools/compare-parallax-frames.py "$ROUND" "$OUT" "$THRESHOLD" godot || status=1
done
exit $status
