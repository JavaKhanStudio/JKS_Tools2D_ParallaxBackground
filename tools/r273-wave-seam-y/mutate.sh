#!/usr/bin/env bash
# r273: proves the shaders round sees WAVE's ripple run on up across the tiles (along().y, not local().y). Puts back,
# one engine at a time, the ripple inside one tile on Y (local().y) in Godot's plax_effects.gd, then in jME's
# ParallaxEffect.frag; runs that engine's frame check on engines/godot/tests/shaders for each, and fails unless every
# run FAILS (its scene s08, stripes through WAVE wrapping on Y). Restores the files on exit.
# headless: tools/godot-parallax-shots.sh and tools/jme-parallax-shots.sh render in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
FX=engines/godot/addons/jks_parallax/plax_effects.gd
FRAG=engines/jme/resources/jks/tools2d/parallax/jme/ParallaxEffect.frag
SAVED=$(mktemp -d)
cp "$FX" "$FRAG" "$SAVED"
restore() { cp "$SAVED/$(basename "$FX")" "$FX"; cp "$SAVED/$(basename "$FRAG")" "$FRAG"; }
trap 'restore; rm -rf "$SAVED"' EXIT
LOG=build/r273/mutant.log
mkdir -p build/r273
status=0
run() {
	if "$2" engines/godot/tests/shaders >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes the shaders round ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1))"; status=1
	else
		echo "caught: $1 ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1); s08: $(grep '^s08' "$LOG" | grep -o 'mean diff [0-9.]*' | tr '\n' ' '))"
	fi
	restore
}
sed -i 's/sin(TAU \* (along(SCREEN_UV).y - effect.z)/sin(TAU * (local(UV).y - effect.z)/' "$FX"
grep -q "sin(TAU \* (local(UV).y - effect.z)" "$FX" || { echo "mutation 1 did not apply"; exit 2; }
run "the ripple inside one tile on Y in Godot" tools/godot-parallax-shots.sh
sed -i 's/sin(TAU \* (along().y - m_Effect.z)/sin(TAU * (local().y - m_Effect.z)/' "$FRAG"
grep -q "sin(TAU \* (local().y - m_Effect.z)" "$FRAG" || { echo "mutation 2 did not apply"; exit 2; }
run "the ripple inside one tile on Y in jME" tools/jme-parallax-shots.sh
exit $status
