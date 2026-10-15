#!/usr/bin/env bash
# r431: proves the shaders round (s09, s10) sees a wrong copy of SWELL. Puts in, one at a time and in one engine's copy
# only: Godot drawing a SWELL layer through WAVE; Godot's SWELL over one wavelength across, not FOG's eight, so its
# phase and its start along the layer wrap at another place; Godot's second wave at the lab's first 1 / 1.7 turns a
# wavelength across (r419's jump), not 5 in 8; Godot's swell sideways only; Godot's waves laid on each tile (local()),
# not along the layer; jME's material never told SWELL (drawn through WAVE); jME's second wave at 1 / 1.7. Runs that
# engine's frame check on engines/rounds/shaders for each, and fails unless every run FAILs. Restores the files on exit.
# headless: engines/godot/tools/frame-check.sh and engines/jme/tools/frame-check.sh render in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
GD=engines/godot/addons/jks_parallax/plax_effects.gd
JME=engines/jme/src/jks/tools2d/parallax/jme/JmeLayerEffects.java
FRAG=engines/jme/resources/jks/tools2d/parallax/jme/ParallaxEffect.frag
SAVED=$(mktemp -d)
cp "$GD" "$SAVED/gd"; cp "$JME" "$SAVED/jme"; cp "$FRAG" "$SAVED/frag"
restore() { cp "$SAVED/gd" "$GD"; cp "$SAVED/jme" "$JME"; cp "$SAVED/frag" "$FRAG"; }
trap 'restore; rm -rf "$SAVED"' EXIT
LOG=build/r431/mutant.log
mkdir -p build/r431
status=0
run() {
	if "$2" engines/rounds/shaders >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes the shaders round"; status=1
	else
		echo "caught: $1 ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1))"
	fi
	restore
}
mutate() {
	sed -i "$2" "$1"
	grep -q "$3" "$1" || { echo "mutation did not apply: $2"; exit 2; }
}
mutate "$GD" 's/"SWELL": _SWELL, //' '{"FOG": _FOG, "PLAIN"'
run "Godot drawing SWELL through WAVE" engines/godot/tools/frame-check.sh
mutate "$GD" '0,/	var own := FOG_PERIOD if model.get("shaderEffect", "") in \["FOG", "SWELL"\] else 1/s//	var own := FOG_PERIOD if model.get("shaderEffect", "") == "FOG" else 1/' 'FOG_PERIOD if model.get("shaderEffect", "") == "FOG"'
run "Godot's SWELL over one wavelength across" engines/godot/tools/frame-check.sh
mutate "$GD" 's/float p2 = TAU \* (0.625 \* a.x/float p2 = TAU * (0.588 * a.x/' '0.588 \* a.x'
run "Godot's second wave at 1 / 1.7 turns a wavelength" engines/godot/tools/frame-check.sh
mutate "$GD" 's/vec2(0.6 \* sin(p1) + 0.4 \* sin(p2), 0.5 \* (0.6 \* cos(p1) - 0.4 \* cos(p2)))/vec2(0.6 * sin(p1) + 0.4 * sin(p2), 0.0)/' '0.4 \* sin(p2), 0.0)'
run "Godot's swell sideways only" engines/godot/tools/frame-check.sh
mutate "$GD" 's/	vec2 a = along(SCREEN_UV);/	vec2 a = local(UV);/' '	vec2 a = local(UV);'
run "Godot's waves laid on each tile" engines/godot/tools/frame-check.sh
mutate "$JME" 's/material.setBoolean("Swell", kind == Enum_ShaderEffect.SWELL);/material.setBoolean("Swell", false);/' 'material.setBoolean("Swell", false);'
run "jME never told SWELL" engines/jme/tools/frame-check.sh
mutate "$FRAG" 's/float p2 = TAU \* (0.625 \* a.x/float p2 = TAU * (0.588 * a.x/' '0.588 \* a.x'
run "jME's second wave at 1 / 1.7 turns a wavelength" engines/jme/tools/frame-check.sh
exit $status
