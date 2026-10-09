#!/usr/bin/env bash
# r217: proves the fog round sees the page fog's colour and depth. Puts back, one at a time and in one engine's copy
# only: Godot's hazed() mixing toward the old mist white instead of the page's fog colour; Godot's fog_of without the
# front's depth taken off; jME's material always handed the mist white; libGDX's page fog shader ignoring the incoming
# page's fog colour during a cross-fade (f05). Runs that engine's frame check on
# engines/godot/tests/fog for each, and fails unless every run FAILs. Restores the files on exit.
# headless: tools/godot-parallax-shots.sh and tools/jme-parallax-shots.sh render in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
GD=engines/godot/addons/jks_parallax/plax_effects.gd
JME=engines/jme/src/jks/tools2d/parallax/jme/JmeLayerEffects.java
GDX=core/src/jks/tools2d/parallax/GdxLayerEffects.java
SAVED=$(mktemp -d)
cp "$GD" "$SAVED/gd"; cp "$JME" "$SAVED/jme"; cp "$GDX" "$SAVED/gdx"
trap 'cp "$SAVED/gd" "$GD"; cp "$SAVED/jme" "$JME"; cp "$SAVED/gdx" "$GDX"; rm -rf "$SAVED"' EXIT
LOG=build/r217/mutant.log
mkdir -p build/r217
status=0
run() {
	if "$2" engines/godot/tests/fog >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes the fog round"; status=1
	else
		echo "caught: $1 ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1))"
	fi
	cp "$SAVED/gd" "$GD"; cp "$SAVED/jme" "$JME"; cp "$SAVED/gdx" "$GDX"
}
sed -i 's/mix(color.rgb, fog_color, haze)/mix(color.rgb, vec3(0.93, 0.95, 0.97), haze)/' "$GD"
grep -q "mix(color.rgb, vec3(0.93" "$GD" || { echo "mutation 1 did not apply"; exit 2; }
run "Godot mixing toward the mist white" tools/godot-parallax-shots.sh
sed -i 's|var depth := 1.0 / speed - 1.0 / front|var depth := 1.0 / speed|' "$GD"
grep -q "var depth := 1.0 / speed$" "$GD" || { echo "mutation 2 did not apply"; exit 2; }
run "Godot's depth not taken from the front" tools/godot-parallax-shots.sh
sed -i 's|{return begin(batch, layer, phase, fog, fogColor.r, fogColor.g, fogColor.b);}|{return begin(batch, layer, phase, fog, GdxLayerEffects.HAZE_R, GdxLayerEffects.HAZE_G, GdxLayerEffects.HAZE_B);}|' "$JME"
grep -q "phase, fog, GdxLayerEffects.HAZE_R" "$JME" || { echo "mutation 3 did not apply"; exit 2; }
run "jME handed the mist white" tools/jme-parallax-shots.sh
sed -i 's/vec3 fog = v_color.g > 0.5 ? u_fogIncoming : u_fog;/vec3 fog = u_fog;/' "$GDX"
grep -q '"	vec3 fog = u_fog;' "$GDX" || { echo "mutation 4 did not apply"; exit 2; }
run "libGDX's page fog without the incoming page's colour" tools/godot-parallax-shots.sh
exit $status
