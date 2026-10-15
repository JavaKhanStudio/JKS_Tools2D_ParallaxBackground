#!/usr/bin/env bash
# r397: proves the animated round's a05 (a flag patch on a WAVE SHADER layer, engines/rounds/animated) sees a SHADER
# layer's patch drawn through the layer's region instead of its frame's. Puts in one mutant at a time and fails unless
# every run FAILS: Godot's patch canvas not given the frame's region (plax_anim.gd's apply skipped; Godot's frame
# check); jME's beginPatch refusing, its patches drawn through the layer's material (jME's frame check); core's reader
# never calling beginPatch, libGDX as before r397 (Godot's frame check: libGDX's stills are then the wrong ones, kept in
# build/r397/before-gdx/). Restores the files on exit. Writes build/r397/mutant.log.
# headless: both frame checks render in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
BG=engines/godot/addons/jks_parallax/plax_background.gd
JME=engines/jme/src/jks/tools2d/parallax/jme/JmeLayerEffects.java
READER=core/src/jks/tools2d/parallax/ParallaxPageReader.java
SAVED=$(mktemp -d)
cp "$BG" "$JME" "$READER" "$SAVED"
restore() {
	cp "$SAVED/plax_background.gd" "$BG"
	cp "$SAVED/JmeLayerEffects.java" "$JME"
	cp "$SAVED/ParallaxPageReader.java" "$READER"
}
trap 'restore; rm -rf "$SAVED"' EXIT
LOG=build/r397/mutant.log
mkdir -p build/r397
status=0
mutant() {
	# $1 what it is, $2 the file, $3 the text as it is, $4 the text mutated, $5 the frame check
	FROM="$3" TO="$4" python3 - "$2" <<'PY' || { echo "mutation did not apply: $1"; exit 2; }
import os, sys
path = sys.argv[1]
with open(path) as f:
    text = f.read()
if text.count(os.environ['FROM']) != 1:
    sys.exit(1)
with open(path, 'w') as f:
    f.write(text.replace(os.environ['FROM'], os.environ['TO']))
PY
	if "$5" engines/rounds/animated >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes the animated round ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1))"; status=1
	else
		echo "caught: $1 ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1); over 2: $(grep -E 'mean diff ([2-9]|[1-9][0-9])' "$LOG" | grep -oE '^[a-z][0-9]+-t[0-9]+' | tr '\n' ' '))"
	fi
	restore
}
mutant "Godot: the patch canvas keeps the layer's region" "$BG" \
	'		PlaxAnim.apply(_target.material, l)' \
	'		pass' \
	engines/godot/tools/frame-check.sh
mutant "jME: beginPatch refuses, the patches drawn through the layer's material" "$JME" \
	'		if (!(batch instanceof JmeBatch) || s == null || layer.getAnimDrawnRegion() == null)' \
	'		if (true)' \
	engines/jme/tools/frame-check.sh
mutant "core: the reader never calls beginPatch (libGDX before r397)" "$READER" \
	'			effects.beginPatch(batch, layer);' \
	'' \
	engines/godot/tools/frame-check.sh
rm -rf build/r397/before-gdx
cp -r build/godot/animated/gdx build/r397/before-gdx
exit $status
