#!/usr/bin/env bash
# r427: proves the glint round sees a wrong copy of the glint. Puts in, one at a time and in one engine's copy only:
# Godot drawing no glint; Godot's sun column off the view's middle; Godot's glints frozen (no twinkle); Godot's crest
# found against the water above only; Godot's glinting layer keeping its effect's own period (WAVE's one wavelength,
# not FOG's eight), so its pieces are other turns; jME's material never handed the glint. Runs that engine's frame check
# on engines/rounds/glint for each, and fails unless every run FAILs. Restores the files on exit.
# headless: engines/godot/tools/frame-check.sh and engines/jme/tools/frame-check.sh render in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
GD=engines/godot/addons/jks_parallax/plax_effects.gd
JME=engines/jme/src/jks/tools2d/parallax/jme/JmeLayerEffects.java
SAVED=$(mktemp -d)
cp "$GD" "$SAVED/gd"; cp "$JME" "$SAVED/jme"
trap 'cp "$SAVED/gd" "$GD"; cp "$SAVED/jme" "$JME"; rm -rf "$SAVED"' EXIT
LOG=build/r427/mutant.log
mkdir -p build/r427
status=0
run() {
	if "$2" engines/rounds/glint >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes the glint round"; status=1
	else
		echo "caught: $1 ($(grep -o 'worst pixels off by >32 [0-9.]*%' "$LOG" | head -1))"
	fi
	cp "$SAVED/gd" "$GD"; cp "$SAVED/jme" "$JME"
}
mutate() {
	sed -i "$2" "$1"
	grep -q "$3" "$1" || { echo "mutation did not apply: $2"; exit 2; }
}
mutate "$GD" 's/	if (glint.x <= 0.0)/	if (true)/' '	if (true)'
run "Godot drawing no glint" engines/godot/tools/frame-check.sh
mutate "$GD" 's/(screen_uv.x - sun.x) \/ sun.y/(screen_uv.x - sun.x - 0.25) \/ sun.y/' 'sun.x - 0.25'
run "Godot's sun column off the middle" engines/godot/tools/frame-check.sh
mutate "$GD" 's/cos(TAU \* (glint.z + 1.5/cos(TAU * (0.0 + 1.5/' 'cos(TAU \* (0.0 + 1.5'
run "Godot's glints frozen" engines/godot/tools/frame-check.sh
mutate "$GD" 's/0.5 \* (dot(above.rgb, LUMA) + dot(below.rgb, LUMA))/dot(above.rgb, LUMA)/' 'LUMA) - dot(above.rgb, LUMA);'
run "Godot's crest against the water above only" engines/godot/tools/frame-check.sh
mutate "$GD" 's/return _least_multiple(own, FOG_PERIOD) if glints(model) else own/return own/' '^	return own$'
run "Godot's glint over the effect's own period" engines/godot/tools/frame-check.sh
mutate "$JME" 's/s.material.setVector4("Glint", s.glint);/s.glint.set(0, 0, 0, 0);/' 's.glint.set(0, 0, 0, 0);'
run "jME never handed the glint" engines/jme/tools/frame-check.sh
exit $status
