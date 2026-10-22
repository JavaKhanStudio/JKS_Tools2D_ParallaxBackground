#!/usr/bin/env bash
# r27: proves engines/rounds/pixelart sees jME's texture filters. In jME's JmeAtlas only: a Nearest atlas magnified
# Bilinear (blurred pixel art), then every atlas magnified Nearest (the Linear one drawn sharp, mid-fade and before).
# Runs engines/jme/tools/frame-check.sh on engines/rounds/pixelart for each, and fails unless both runs FAIL.
# Restores JmeAtlas.java on exit.
# headless: engines/jme/tools/frame-check.sh renders in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
F=engines/jme/src/jks/tools2d/parallax/jme/JmeAtlas.java
SAVED=$(mktemp)
cp "$F" "$SAVED"
trap 'cp "$SAVED" "$F"; rm -f "$SAVED"' EXIT
LOG=build/r27/mutant.log
mkdir -p build/r27
status=0
run() {
	if engines/jme/tools/frame-check.sh engines/rounds/pixelart >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes the pixelart round ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1))"; status=1
	else
		echo "caught: $1 ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1); over 2: $(awk '/\.png  mean diff/ && $4 > 2 {printf "%s ", $1}' "$LOG"))"
	fi
	cp "$SAVED" "$F"
}
sed -i 's/page.magFilter == TextureFilter.Nearest ? com.jme3.texture.Texture.MagFilter.Nearest/false ? com.jme3.texture.Texture.MagFilter.Nearest/' "$F"
grep -q "(false ? com.jme3.texture.Texture.MagFilter.Nearest" "$F" || { echo "mutation 1 did not apply"; exit 2; }
run "jME magnifies a Nearest atlas Bilinear"
sed -i 's/page.magFilter == TextureFilter.Nearest ? com.jme3.texture.Texture.MagFilter.Nearest/true ? com.jme3.texture.Texture.MagFilter.Nearest/' "$F"
grep -q "(true ? com.jme3.texture.Texture.MagFilter.Nearest" "$F" || { echo "mutation 2 did not apply"; exit 2; }
run "jME magnifies every atlas Nearest"
exit $status
