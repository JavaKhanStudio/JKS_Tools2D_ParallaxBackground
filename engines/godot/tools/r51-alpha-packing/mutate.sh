#!/usr/bin/env bash
# r51: proves Godot's layer opacity is packed as libGDX packs the batch colour (Color.toFloatBits: 8 bits, truncated,
# the lowest dropped, 255 / 254 in the vertex shader). Runs engines/godot/tools/frame-check.sh on this folder's round
# (Hiver into OneNight 1/16 and 1/4 in, at t6) at THRESHOLD 0.55, the worst other transfer still: it must PASS as is,
# and FAIL with the opacity fed as a float (PlaxEffects.packed_alpha returning it as is). Restores the file on exit.
# headless: engines/godot/tools/frame-check.sh renders in cage.
set -u
cd "$(dirname "$0")/../../../.." || exit 1
ROUND=engines/godot/tools/r51-alpha-packing
BG=engines/godot/addons/jks_parallax/plax_effects.gd
SAVED=$(mktemp -d)
cp "$BG" "$SAVED"
trap 'cp "$SAVED/$(basename "$BG")" "$BG"; rm -rf "$SAVED"' EXIT
LOG=build/r51/mutant.log
mkdir -p build/r51
export THRESHOLD=0.55
status=0
if PLAX_MUTANT=1 engines/godot/tools/frame-check.sh "$ROUND" >"$LOG" 2>&1; then
	echo "clean: passes ($(grep -o 'worst mean diff [0-9.]*' "$LOG"))"
else
	echo "CLEAN FAILS: $(grep -o 'worst mean diff [0-9.]*' "$LOG"), see $LOG"; status=1
fi
sed -i 's/^\treturn float(int(255 \* clampf(a, 0, 1)) \& 0xfe) \/ 254$/\treturn a/' "$BG"
grep -q "^	return a$" "$BG" || { echo "mutation 1 did not apply"; exit 2; }
if PLAX_MUTANT=1 engines/godot/tools/frame-check.sh "$ROUND" >"$LOG" 2>&1; then
	echo "NOT CAUGHT: the opacity as a float passes ($(grep -o 'worst mean diff [0-9.]*' "$LOG"))"; status=1
else
	echo "caught: the opacity as a float ($(grep -o 'worst mean diff [0-9.]*' "$LOG"))"
fi
exit $status
