#!/usr/bin/env bash
# r229: proves the shaders round (its s06) sees FOG's noise run on across the tiles. Puts back, one engine at a time,
# the noise inside one tile (local().x) in Godot's copy, then Godot's wrap not carried (wrapped_x kept 0), then the
# noise inside one tile in jME's ParallaxEffect.frag; runs that engine's frame check on engines/godot/tests/shaders
# for each, and fails unless every run FAILS. Restores the files on exit.
# headless: tools/godot-parallax-shots.sh and tools/jme-parallax-shots.sh render in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
FX=engines/godot/addons/jks_parallax/plax_effects.gd
BG=engines/godot/addons/jks_parallax/plax_background.gd
FRAG=engines/jme/resources/jks/tools2d/parallax/jme/ParallaxEffect.frag
SAVED=$(mktemp -d)
cp "$FX" "$BG" "$FRAG" "$SAVED"
restore() { cp "$SAVED/$(basename "$FX")" "$FX"; cp "$SAVED/$(basename "$BG")" "$BG"; cp "$SAVED/$(basename "$FRAG")" "$FRAG"; }
trap 'restore; rm -rf "$SAVED"' EXIT
LOG=build/r229/mutant.log
mkdir -p build/r229
status=0
run() {
	if "$2" engines/godot/tests/shaders >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes the shaders round"; status=1
	else
		echo "caught: $1 ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1); s06: $(grep '^s06' "$LOG" | grep -o 'mean diff [0-9.]*' | tr '\n' ' '))"
	fi
	restore
}
sed -i 's/vec2 p = (along(SCREEN_UV) + vec2/vec2 p = (vec2(local(UV).x, along(SCREEN_UV).y) + vec2/' "$FX"
grep -q "vec2(local(UV).x, along(SCREEN_UV).y)" "$FX" || { echo "mutation 1 did not apply"; exit 2; }
run "the noise inside one tile in Godot" tools/godot-parallax-shots.sh
sed -i 's/l.wrapped_x = fmod(l.wrapped_x + before - l.distance_x, period) if period > 0 else 0.0/l.wrapped_x = 0.0/' "$BG"
grep -q "^		l.wrapped_x = 0.0$" "$BG" || { echo "mutation 2 did not apply"; exit 2; }
run "Godot's wrap not carried" tools/godot-parallax-shots.sh
sed -i 's/vec2 p = (along() + vec2/vec2 p = (vec2(local().x, along().y) + vec2/' "$FRAG"
grep -q "vec2(local().x, along().y)" "$FRAG" || { echo "mutation 3 did not apply"; exit 2; }
run "the noise inside one tile in jME" tools/jme-parallax-shots.sh
exit $status
