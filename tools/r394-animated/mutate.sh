#!/usr/bin/env bash
# r394: proves the animated round (engines/rounds/animated) sees a wrong copy of the patch in Godot's plax_anim.gd, the
# one engine that ports it (jME runs core's ParallaxLayer.drawAnim as is). Puts in one mutant at a time, runs
# engines/godot/tools/frame-check.sh engines/rounds/animated for each, and fails unless every run FAILS: no patch; the
# frame one late; the patch at animX off by its width; a flipped tile's patch not mirrored on X; a mirrored copy's
# not mirrored on Y; the patch at the frame's size unscaled by the layer. Restores the file on exit. Writes
# build/r394/mutant.log.
# headless: engines/godot/tools/frame-check.sh renders in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
ANIM=engines/godot/addons/jks_parallax/plax_anim.gd
SAVED=$(mktemp -d)
cp "$ANIM" "$SAVED"
restore() { cp "$SAVED/plax_anim.gd" "$ANIM"; }
trap 'restore; rm -rf "$SAVED"' EXIT
LOG=build/r394/mutant.log
mkdir -p build/r394
status=0
mutant() {
	# $1 what it is, $2 the text as it is, $3 the text mutated
	FROM="$2" TO="$3" python3 - "$ANIM" <<'PY' || { echo "mutation did not apply: $1"; exit 2; }
import os, sys
path = sys.argv[1]
text = open(path).read()
if text.count(os.environ['FROM']) != 1:
    sys.exit(1)
open(path, 'w').write(text.replace(os.environ['FROM'], os.environ['TO']))
PY
	if engines/godot/tools/frame-check.sh engines/rounds/animated >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes the animated round ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1))"; status=1
	else
		echo "caught: $1 ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1); over 2: $(grep -E 'mean diff ([2-9]|[1-9][0-9])' "$LOG" | grep -oE '^[a-z][0-9]+-t[0-9]+' | tr '\n' ' '))"
	fi
	restore
}
mutant "no patch" \
	'	bg._draw_region(frame, left, bottom, patch_w, patch_h, fx, fy)' \
	'	pass'
mutant "the frame one late" \
	'	var f := fmod(floorf(time * float(l.model.animFps)), count)' \
	'	var f := fmod(floorf(time * float(l.model.animFps)) - 1, count)'
mutant "animX off by the patch's width" \
	'	var ax: float = l.model.animX * scale_x' \
	'	var ax: float = l.model.animX * scale_x + patch_w'
mutant "a flipped tile's patch not mirrored on X" \
	'	var left := x + width - (ax + patch_w) if fx else x + ax' \
	'	var left := x + ax'
mutant "a mirrored copy's patch not mirrored on Y" \
	'	var bottom := y + height - (ay + patch_h) if fy else y + ay' \
	'	var bottom := y + ay'
mutant "the frame's own size, not scaled by the layer" \
	'	var patch_w: float = frame.image_w * scale_x' \
	'	var patch_w: float = frame.image_w'
exit $status
