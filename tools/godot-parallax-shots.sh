#!/usr/bin/env bash
# godot-parallax-shots.sh [ROUND_DIR] — the Godot reader (engines/godot) against the libGDX runtime, frame by frame (r87).
#
#   tools/godot-parallax-shots.sh                          engines/godot/tests/round1, /conformance, /transfer, /pixelart
#   tools/godot-parallax-shots.sh engines/godot/tests/round1
#
# For each round: renders its scenes with libGDX (tools/parallax-lab-shots.sh, 0, 6 and 12 s into the lab's scroll)
# and with Godot (engines/godot/tests/shots.gd, the same scroll), off screen in cage's headless display on a nested
# Xwayland, then compares them pixel by pixel. The transfer round starts a cross-fade or a tint 4 s in, so t6 is
# mid-fade and t12 after it. Writes build/godot/<round>/: the libGDX stills in gdx/, Godot's in godot/,
# compare.png (libGDX | Godot | difference x4, per still) and report.txt. Fails when a still differs by more than
# THRESHOLD (mean absolute difference per channel, 0-255; default 2). Needs godot 4.x, cage, Xwayland, python3 with
# Pillow. ATELIER_NO_OFFSCREEN=1 draws both on $DISPLAY instead of cage: CI's "Godot frames against libGDX" job runs it
# so under xvfb-run on Mesa llvmpipe, where the worst still was 0.75 / 255 (r96), so the threshold holds there too.
# Its screen is 1920x1440: a "resize" scene asks for a window up to 1280 tall.
set -u
cd "$(dirname "$0")/.." || exit 1
ROOT="$PWD"
THRESHOLD="${THRESHOLD:-2}"
ROUNDS=("$@")
[ ${#ROUNDS[@]} -eq 0 ] && ROUNDS=(engines/godot/tests/round1 engines/godot/tests/conformance engines/godot/tests/transfer engines/godot/tests/pixelart)
GODOT="${GODOT:-godot}"
status=0
for ROUND in "${ROUNDS[@]}"; do
	OUT="$ROOT/build/godot/$(basename "$ROUND")"
	rm -rf "$OUT"; mkdir -p "$OUT/godot"
	tools/parallax-lab-shots.sh "$ROUND" "$OUT/gdx" >/dev/null || { echo "libGDX shots failed: $OUT/gdx/lab.log"; exit 1; }
	"$GODOT" --headless --path engines/godot --import >/dev/null 2>&1
	RUN="$GODOT --path \"$ROOT/engines/godot\" --resolution 1280x720 res://tests/shots.tscn -- \"$ROOT\" \"$ROUND\" \"$OUT/godot\" >\"$OUT/godot.log\" 2>&1"
	if [ "${ATELIER_NO_OFFSCREEN:-0}" = 1 ]; then
		timeout 300 bash -c "$RUN"
	else
		inner="Xwayland :9 -geometry 1280x1280 & X=\$!; sleep 2; DISPLAY=:9 $RUN; kill \$X 2>/dev/null"
		WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 300 cage -- bash -c "$inner" >"$OUT/cage.log" 2>&1
	fi
	python3 tools/compare-parallax-frames.py "$ROUND" "$OUT" "$THRESHOLD" godot || status=1
done
exit $status
