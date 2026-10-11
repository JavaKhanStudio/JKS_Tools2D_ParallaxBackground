#!/usr/bin/env bash
# r249: proves the transfer round sees the depth stagger (t07, t08). Leaves it out, one at a time and of one engine's
# copy only: Godot's _alpha_of fading every slot at once; libGDX's reader doing the same (the Godot check then holds
# Godot's stagger against a libGDX without one); jME's PlaxBackground dropping the style it is handed. Runs that
# engine's frame check on engines/rounds/transfer for each, and fails unless every run FAILs. Restores the files
# on exit.
# headless: engines/godot/tools/frame-check.sh and engines/jme/tools/frame-check.sh render in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
GD=engines/godot/addons/jks_parallax/plax_background.gd
GDX=core/src/jks/tools2d/parallax/ParallaxPageReader.java
JME=engines/jme/src/jks/tools2d/parallax/jme/PlaxBackground.java
SAVED=$(mktemp -d)
cp "$GD" "$SAVED/gd"; cp "$GDX" "$SAVED/gdx"; cp "$JME" "$SAVED/jme"
trap 'cp "$SAVED/gd" "$GD"; cp "$SAVED/gdx" "$GDX"; cp "$SAVED/jme" "$JME"; rm -rf "$SAVED"' EXIT
LOG=build/r249/mutant.log
mkdir -p build/r249
status=0
run() {
	if "$2" engines/rounds/transfer >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes the transfer round"; status=1
	else
		echo "caught: $1 ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1))"
	fi
	cp "$SAVED/gd" "$GD"; cp "$SAVED/gdx" "$GDX"; cp "$SAVED/jme" "$JME"
}
sed -i 's/if _style == null or _style.kind == "FADE":/if true:/' "$GD"
grep -q '^	if true:$' "$GD" || { echo "mutation 1 did not apply"; exit 2; }
run "Godot fading every slot at once" engines/godot/tools/frame-check.sh
sed -i 's/boolean staggered = transfertStyle.getKind() != TransfertStyle.Kind.FADE;/boolean staggered = false;/' "$GDX"
grep -q 'boolean staggered = false;' "$GDX" || { echo "mutation 2 did not apply"; exit 2; }
run "libGDX fading every slot at once" engines/godot/tools/frame-check.sh
sed -i 's/reader.addLayersTransfert(model, null, seconds, style);/reader.addLayersTransfert(model, null, seconds, TransfertStyle.FADE);/' "$JME"
grep -q 'seconds, TransfertStyle.FADE);$' "$JME" || { echo "mutation 3 did not apply"; exit 2; }
run "jME dropping the style" engines/jme/tools/frame-check.sh
exit $status
