#!/usr/bin/env bash
# r252: proves the transfer round sees the fog creep (t13, t14), and a wrong copy of it. One engine's copy at a time:
# Godot swapping each slot at its own window's half instead of every slot at the middle; Godot's mist not eased
# (smoothstep left out); libGDX's gradients fading straight instead of through the mist; jME's gradients fading
# straight. Runs that engine's frame check on engines/godot/tests/transfer for each, and fails unless every run FAILs.
# Restores the files on exit.
# headless: tools/godot-parallax-shots.sh and tools/jme-parallax-shots.sh render in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
FILES=(engines/godot/addons/jks_parallax/plax_transfert_style.gd engines/godot/addons/jks_parallax/plax_transfert_style.gd
	core/src/jks/tools2d/parallax/TransfertStyle.java engines/jme/src/jks/tools2d/parallax/jme/JmeGradient.java)
SAVED=$(mktemp -d)
for i in "${!FILES[@]}"; do cp "${FILES[$i]}" "$SAVED/$i"; done
restore() { for i in "${!FILES[@]}"; do cp "$SAVED/$i" "${FILES[$i]}"; done; }
trap 'restore; rm -rf "$SAVED"' EXIT
LOG=build/r252/mutant.log
mkdir -p build/r252
status=0
run() {
	if "$2" engines/godot/tests/transfer >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes the transfer round ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1))"; status=1
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
mutate 0 's/return progress >= 0.5 if kind == "FOG_CREEP" else/return progress >= 0.5 if false else/' '0.5 if false else' \
	"Godot swapping each slot in its own window" tools/godot-parallax-shots.sh
mutate 1 's/	return r \* r \* (3 - 2 \* r)$/	return r/' '	return r' \
	"Godot's mist not eased" tools/godot-parallax-shots.sh
mutate 2 's/		if (!grades())$/		if (kind != Kind.THROUGH_COLOR)/' 'if (kind != Kind.THROUGH_COLOR)' \
	"libGDX's gradients straight in a fog creep" tools/godot-parallax-shots.sh
mutate 3 's/		style.gradient(/		TransfertStyle.FADE.gradient(/' 'TransfertStyle.FADE.gradient(' \
	"jME's gradients straight" tools/jme-parallax-shots.sh
exit $status
