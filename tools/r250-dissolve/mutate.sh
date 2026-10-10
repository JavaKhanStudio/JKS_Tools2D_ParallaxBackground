#!/usr/bin/env bash
# r250: proves the transfer round sees the dissolve (t09, t10), and a wrong copy of it. One engine's copy at a time:
# Godot fading its layers instead of masking them; Godot's noise read with SCREEN_UV's y down; libGDX's patches not
# drifting (the Godot check then holds Godot's drift against a libGDX without one); jME's mask keeping each side where
# the other should be; and, on t15's fogged page (r256), Godot's WAVE shader and jME's FOG shader each dropping the mask
# while PLAIN keeps it. Runs that engine's frame check on engines/godot/tests/transfer for each, and fails unless every
# run FAILs. Restores the files on exit.
# headless: tools/godot-parallax-shots.sh and tools/jme-parallax-shots.sh render in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
FILES=(engines/godot/addons/jks_parallax/plax_background.gd engines/godot/addons/jks_parallax/plax_effects.gd
	core/src/jks/tools2d/parallax/ParallaxPageReader.java engines/jme/resources/jks/tools2d/parallax/jme/ParallaxEffect.frag)
SAVED=$(mktemp -d)
for i in "${!FILES[@]}"; do cp "${FILES[$i]}" "$SAVED/$i"; done
restore() { for i in "${!FILES[@]}"; do cp "$SAVED/$i" "${FILES[$i]}"; done; }
trap 'restore; rm -rf "$SAVED"' EXIT
LOG=build/r250/mutant.log
mkdir -p build/r250
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
mutate 0 's/and _style.kind == "DISSOLVE"$/and _style.kind == "NONE"/' '_style.kind == "NONE"' \
	"Godot fading instead of masking" tools/godot-parallax-shots.sh
mutate 1 's/vec2(screen_uv.x, 1.0 - screen_uv.y)/screen_uv/' 'vec2 p = screen_uv * cells' \
	"Godot's noise upside down" tools/godot-parallax-shots.sh
mutate 2 's/TransfertStyle.drift(effectTime)/0/' 'transfertStyle.getSoftness(), 0,' \
	"libGDX's patches not drifting" tools/godot-parallax-shots.sh
mutate 3 's/return m_Dissolve.x < 1.5 ? 1.0 - m : m;/return m_Dissolve.x < 1.5 ? m : 1.0 - m;/' 'm_Dissolve.x < 1.5 ? m : 1.0 - m' \
	"jME's sides swapped" tools/jme-parallax-shots.sh
mutate 1 's/COLOR = hazed(tint \* texture(TEXTURE, vec2(u, UV.y)), SCREEN_UV);/COLOR = vec4(hazed(tint * texture(TEXTURE, vec2(u, UV.y)), SCREEN_UV).rgb, (tint * texture(TEXTURE, vec2(u, UV.y))).a);/' \
	'SCREEN_UV).rgb, (tint' "Godot's WAVE without the mask" tools/godot-parallax-shots.sh
mutate 3 's/^    gl_FragColor = hazed(color);$/    gl_FragColor = vec4(hazed(color).rgb, color.a);/' 'vec4(hazed(color).rgb, color.a)' \
	"jME's FOG without the mask" tools/jme-parallax-shots.sh
exit $status
