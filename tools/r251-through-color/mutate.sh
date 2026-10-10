#!/usr/bin/env bash
# r251: proves the transfer round sees the transfert through a colour (t11, t12), and a wrong copy of it. One engine's
# copy at a time: Godot not grading its layers (they fade out to the gradients instead); Godot's gradients fading
# straight; libGDX mixing the colour in before the fog rather than after it; jME's grade at half its amount. Runs that
# engine's frame check on engines/godot/tests/transfer for each, and fails unless every run FAILs. Restores the files
# on exit.
# headless: tools/godot-parallax-shots.sh and tools/jme-parallax-shots.sh render in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
FILES=(engines/godot/addons/jks_parallax/plax_background.gd engines/godot/addons/jks_parallax/plax_background.gd
	core/src/jks/tools2d/parallax/GdxLayerEffects.java engines/jme/resources/jks/tools2d/parallax/jme/ParallaxEffect.frag)
SAVED=$(mktemp -d)
for i in "${!FILES[@]}"; do cp "${FILES[$i]}" "$SAVED/$i"; done
restore() { for i in "${!FILES[@]}"; do cp "$SAVED/$i" "${FILES[$i]}"; done; }
trap 'restore; rm -rf "$SAVED"' EXIT
LOG=build/r251/mutant.log
mkdir -p build/r251
status=0
run() {
	if "$2" engines/godot/tests/transfer >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes the transfer round"; status=1
	else
		echo "caught: $1 ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1))"
	fi
	restore
}
# mutate FILE_INDEX SED CHECK_GREP NAME RUNNER
mutate() {
	sed -i "$2" "${FILES[$1]}"
	grep -qF -- "$3" "${FILES[$1]}" || { echo "mutation '$4' did not apply"; restore; exit 2; }
	run "$4" "$5"
}
mutate 0 's/and _style.kind == "THROUGH_COLOR"$/and _style.kind == "NONE"/' '_style.kind == "NONE"' \
	"Godot fading instead of grading" tools/godot-parallax-shots.sh
mutate 1 's/progress) if _gradient_style$/progress) if false/' 'progress) if false' \
	"Godot's gradients fading straight" tools/godot-parallax-shots.sh
mutate 2 's/mix(mix(color.rgb, u_fog, u_haze), u_grade.rgb, u_grade.a)/mix(mix(color.rgb, u_grade.rgb, u_grade.a), u_fog, u_haze)/' 'mix(mix(color.rgb, u_grade.rgb' \
	"libGDX grading before the fog" tools/godot-parallax-shots.sh
mutate 3 's/m_Grade.rgb, m_Grade.a)/m_Grade.rgb, 0.5 * m_Grade.a)/' '0.5 * m_Grade.a' \
	"jME's grade at half" tools/jme-parallax-shots.sh
exit $status
