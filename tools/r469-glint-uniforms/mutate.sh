#!/usr/bin/env bash
# r469: proves the glint round's g04 sees libGDX leaving a glinting layer's u_glint on the next layer of its program.
# GdxLayerEffects sets no glint uniform for a layer that does not glint, unless its program's u_glint was last set to a
# glinting layer's; the mutant never resets it, so g04's front sea (glint 0) glints with the back one's numbers in the
# libGDX stills and not in Godot's. Runs Godot's frame check on engines/rounds/glint and fails unless it FAILs, on
# g04 alone. Restores the file on exit.
# headless: engines/godot/tools/frame-check.sh renders in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
FX=core/src/jks/tools2d/parallax/GdxLayerEffects.java
SAVED=$(mktemp)
cp "$FX" "$SAVED"
trap 'cp "$SAVED" "$FX"; rm -f "$SAVED"' EXIT
LOG=build/r469/mutant.log
mkdir -p build/r469
sed -i 's/			program.setUniformf(glint\[index\], 0, 0, 0, 0);/			\/\/ mutant: no reset/' "$FX"
grep -q 'mutant: no reset' "$FX" || { echo "mutation did not apply"; exit 2; }
if engines/godot/tools/frame-check.sh engines/rounds/glint >"$LOG" 2>&1; then
	echo "NOT CAUGHT: libGDX leaving the glint on passes the glint round"; exit 1
fi
grep -E '^g0[123]-.*off by >32: 0\.0[0-4]%' -c "$LOG" | grep -qx 9 || { echo "the mutant moved g01-g03 too:"; grep '^g0' "$LOG"; exit 1; }
echo "caught: libGDX leaving the glint on ($(grep '^g04-t6' "$LOG"))"
