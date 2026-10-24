#!/usr/bin/env bash
# r52: proves Godot packs a layer's tint as libGDX packs the batch colour, but through the page fog's shader, where the
# tint is a uniform and the haze is packed instead (PlaxEffects.packed_rgb, haze_of, page_fogged). Runs
# engines/godot/tools/frame-check.sh on this folder's round (transfer t05, fog f05) at THRESHOLD 0.32: it must PASS as
# is (worst 0.31), and FAIL with the tint fed as floats (t05-t6 0.54) and with the page-fog exception dropped (f05-t6
# 0.34). The margins are a few hundredths of a level: packing moves no pixel by more than 1, and the stills repeat to
# the bit on one machine, but another GPU may shift them; re-measure the clean run before reading a mutant there.
# Restores the file on exit.
# headless: engines/godot/tools/frame-check.sh renders in cage.
set -u
cd "$(dirname "$0")/../../../.." || exit 1
ROUND=engines/godot/tools/r52-tint-packing
FX=engines/godot/addons/jks_parallax/plax_effects.gd
SAVED=$(mktemp -d)
cp "$FX" "$SAVED"
trap 'cp "$SAVED/$(basename "$FX")" "$FX"; rm -rf "$SAVED"' EXIT
LOG=build/r52/mutant.log
mkdir -p build/r52
export THRESHOLD=0.32 PLAX_MUTANT=1
status=0
worst() { grep -o 'worst mean diff [0-9.]*' "$LOG"; }
if engines/godot/tools/frame-check.sh "$ROUND" >"$LOG" 2>&1; then
	echo "clean: passes ($(worst))"
else
	echo "CLEAN FAILS: $(worst), see $LOG"; status=1
fi
run() {
	if engines/godot/tools/frame-check.sh "$ROUND" >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes ($(worst))"; status=1
	else
		echo "caught: $1 ($(worst))"
	fi
	cp "$SAVED/$(basename "$FX")" "$FX"
}
sed -i 's/^\treturn Color(packed(c.r), packed(c.g), packed(c.b), c.a)$/\treturn c/' "$FX"
grep -A1 "^static func packed_rgb" "$FX" | grep -q "^	return c$" || { echo "mutation 1 did not apply"; exit 2; }
run "the tint as floats"
sed -i 's/^\tif bg.page == null or l.model.kind in \["EMPTY", "PARTICLES"\]/\treturn false\n&/' "$FX"
grep -B1 '^	if bg.page == null or l.model.kind in' "$FX" | grep -q "^	return false$" || { echo "mutation 2 did not apply"; exit 2; }
run "the page fog's exception dropped (tint packed, haze not)"
exit $status
