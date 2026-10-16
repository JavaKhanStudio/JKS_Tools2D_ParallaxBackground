#!/usr/bin/env bash
# r434: proves the shaders round (s11, the checker tiled on X and Y and scrolled up) sees a SWELL that does not wrap on
# the axes its page repeats on. Puts in, one at a time and in one engine's copy only: Godot's SWELL with its rule before
# r434 (wrapped across, held up and down, whatever the page repeats on); Godot's background never handing the page's
# repeat to the effect (the uniform's default, across only); jME's SWELL with the rule before r434; jME's material never
# given Wrap. Runs that engine's frame check on engines/rounds/shaders for each, and fails unless every run FAILs.
# Restores the files on exit.
# headless: engines/godot/tools/frame-check.sh and engines/jme/tools/frame-check.sh render in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
GD=engines/godot/addons/jks_parallax/plax_effects.gd
BG=engines/godot/addons/jks_parallax/plax_background.gd
JME=engines/jme/src/jks/tools2d/parallax/jme/JmeLayerEffects.java
FRAG=engines/jme/resources/jks/tools2d/parallax/jme/ParallaxEffect.frag
SAVED=$(mktemp -d)
cp "$GD" "$SAVED/gd"; cp "$BG" "$SAVED/bg"; cp "$JME" "$SAVED/jme"; cp "$FRAG" "$SAVED/frag"
restore() { cp "$SAVED/gd" "$GD"; cp "$SAVED/bg" "$BG"; cp "$SAVED/jme" "$JME"; cp "$SAVED/frag" "$FRAG"; }
trap 'restore; rm -rf "$SAVED"' EXIT
LOG=build/r434/mutant.log
mkdir -p build/r434
status=0
run() {
	if "$2" engines/rounds/shaders >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes the shaders round"; status=1
	else
		echo "caught: $1 ($(grep "^s11-t6" "$LOG" | head -1))"
	fi
	restore
}
mutate() {
	sed -i "$2" "$1"
	grep -q "$3" "$1" || { echo "mutation did not apply: $2"; exit 2; }
}
mutate "$GD" 's|vec2 f = mix(clamp(l / size, 0.0, 1.0), fract(l / size), wrap);|vec2 f = vec2(fract(l.x / size.x), clamp(l.y / size.y, 0.0, 1.0));|' 'vec2 f = vec2(fract(l.x / size.x)'
run "Godot's SWELL wrapping across only (before r434)" engines/godot/tools/frame-check.sh
mutate "$BG" 's|Vector2(1.0 if _repeat_x else 0.0, 1.0 if _repeat_y else 0.0))|Vector2(1.0, 0.0))|' 'Vector2(1.0, 0.0))'
run "Godot never handing the page's repeat to SWELL" engines/godot/tools/frame-check.sh
mutate "$FRAG" 's|vec2 f = mix(clamp(l / m_Size, 0.0, 1.0), fract(l / m_Size), m_Wrap);|vec2 f = vec2(fract(l.x / m_Size.x), clamp(l.y / m_Size.y, 0.0, 1.0));|' 'vec2 f = vec2(fract(l.x / m_Size.x)'
run "jME's SWELL wrapping across only (before r434)" engines/jme/tools/frame-check.sh
mutate "$JME" 's|s.material.setVector2("Wrap", s.wrap);|// r434 mutant: Wrap never given|' 'r434 mutant: Wrap never given'
run "jME's material never given Wrap" engines/jme/tools/frame-check.sh
exit $status
