#!/usr/bin/env bash
# r455: proves the shaders round (s12, s13) sees a wrong copy of MIRROR. Puts in, one at a time and in one engine's
# copy only: Godot drawing MIRROR through SWELL; Godot's sky white, not its page's top gradient; Godot's horizon at the
# image's top, not mirrorHorizon; Godot's mirror without the painting's relief; without the three fine ripples; Godot's
# bend in colour, not in grey (tint 0); Godot's glint under the shipped sun, not MIRROR's; jME's material never told
# MIRROR (drawn through WAVE); jME's sky left white. Runs that engine's frame check on engines/rounds/shaders for each
# (the glint round for the sun: the glint is too sparse for the mean), and fails unless every run FAILs. Restores the
# files on exit. ONLY=<regex> runs the mutants whose name matches it.
# headless: engines/godot/tools/frame-check.sh and engines/jme/tools/frame-check.sh render in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
GD=engines/godot/addons/jks_parallax/plax_effects.gd
BG=engines/godot/addons/jks_parallax/plax_background.gd
JME=engines/jme/src/jks/tools2d/parallax/jme/JmeLayerEffects.java
SAVED=$(mktemp -d)
cp "$GD" "$SAVED/gd"; cp "$BG" "$SAVED/bg"; cp "$JME" "$SAVED/jme"
restore() { cp "$SAVED/gd" "$GD"; cp "$SAVED/bg" "$BG"; cp "$SAVED/jme" "$JME"; }
trap 'restore; rm -rf "$SAVED"' EXIT
LOG=build/r455/mutant.log
mkdir -p build/r455
status=0
run() {
	if [ -n "${ONLY:-}" ] && ! [[ "$1" =~ $ONLY ]]; then restore; return; fi
	if "$2" "${3:-engines/rounds/shaders}" >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes $(basename "${3:-shaders}")"; status=1
	else
		echo "caught: $1 ($(grep -o 'FAIL.*' "$LOG" | tail -1))"
	fi
	restore
}
mutate() {
	sed -i "$2" "$1"
	grep -q "$3" "$1" || { echo "mutation did not apply: $2"; exit 2; }
}
mutate "$GD" 's/"MIRROR": _MIRROR,/"MIRROR": _SWELL,/' '"MIRROR": _SWELL,'
run "Godot drawing MIRROR through SWELL" engines/godot/tools/frame-check.sh
mutate "$BG" 's/l.sky = Vector3(from_page.top_half_bottom.r, from_page.top_half_bottom.g, from_page.top_half_bottom.b)/l.sky = Vector3(1.0, 1.0, 1.0)/' 'l.sky = Vector3(1.0, 1.0, 1.0)'
run "Godot's sky white, not the page's" engines/godot/tools/frame-check.sh
mutate "$GD" 's/Vector2((float(l.model.mirrorHorizon) - l.trim_bottom)/Vector2((1.0 - l.trim_bottom)/' 'Vector2((1.0 - l.trim_bottom)'
run "Godot's horizon at the image's top" engines/godot/tools/frame-check.sh
mutate "$GD" 's/	float facet = tilt + 0.5 \* mirror.y \* relief;/	float facet = tilt;/' '	float facet = tilt;'
run "Godot's mirror without the painting's relief" engines/godot/tools/frame-check.sh
mutate "$GD" 's/		+ effect.x \* (0.9 \* sin(TAU/		+ 0.0 * (0.9 * sin(TAU/' '+ 0.0 \* (0.9'
run "Godot's mirror without the fine ripples" engines/godot/tools/frame-check.sh
mutate "$GD" 's/const MIRROR_NUMBERS := Vector4(0.15, 3.0, 1.0, 1.0)/const MIRROR_NUMBERS := Vector4(0.15, 3.0, 1.0, 0.0)/' 'Vector4(0.15, 3.0, 1.0, 0.0)'
run "Godot's bend in colour (tint 0)" engines/godot/tools/frame-check.sh
mutate "$GD" 's/Vector2(MIRROR_SUN_X if mirrored else GLINT_SUN_X,/Vector2(GLINT_SUN_X,/' 'Vector2(GLINT_SUN_X,'
run "Godot's MIRROR glint under the shipped sun" engines/godot/tools/frame-check.sh engines/rounds/glint
mutate "$JME" 's/material.setBoolean("Mirror", kind == Enum_ShaderEffect.MIRROR);/material.setBoolean("Mirror", false);/' 'material.setBoolean("Mirror", false);'
run "jME never told MIRROR" engines/jme/tools/frame-check.sh
mutate "$JME" 's/s.material.setVector3("Sky", s.sky);/s.material.setVector3("Sky", new Vector3f(1, 1, 1));/' 'setVector3("Sky", new Vector3f(1, 1, 1))'
run "jME's sky left white" engines/jme/tools/frame-check.sh
exit $status
