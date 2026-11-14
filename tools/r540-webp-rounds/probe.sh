#!/usr/bin/env bash
# probe.sh [ROUND] [OUT] — r540: which reader draws a round whose atlas pages are WebP, today. Writes OUT (default
# build/r540/probe): twin/ (twin.py: ROUND's pages, their atlases' pages as lossless WebP), gdx/ (libGDX's stills of
# the PNG round, the reference), then each reader's stills of the twin in godot/, jme/, threejs/, compared with gdx/ by
# tools/compare-parallax-frames.py as the frame checks do, and libGDX's own try at the twin (no ParallaxWebp installed:
# ParallaxShots does not). results.txt: one line per reader, READS or FAILS. ROUND defaults to engines/rounds/pixelart.
# Runs every window in cage's headless display (tools/nested-x.sh).
set -u
cd "$(dirname "$0")/../.." || exit 1
ROOT="$PWD"
ROUND=${1:-engines/rounds/pixelart}
OUT=$(realpath -m "${2:-build/r540/probe}")
TWIN=$(realpath -m --relative-to="$ROOT" "$OUT/twin")
rm -rf "$OUT"; mkdir -p "$OUT/godot" "$OUT/jme" "$OUT/threejs"
python3 tools/r540-webp-rounds/twin.py "$ROUND" "$TWIN" || exit 1
tools/parallax-lab-shots.sh "$ROUND" "$OUT/gdx" >/dev/null || { echo "libGDX shots of the PNG round failed"; exit 1; }
: >"$OUT/results.txt"
verdict() { # NAME LOG: compared, its result line kept
	if python3 tools/compare-parallax-frames.py "$TWIN" "$OUT" 2 "$1" >"$OUT/$1-compare.txt" 2>&1; then
		echo "$1 READS: $(grep -E '^(PASS|FAIL)' "$OUT/$1-compare.txt" | tail -1)" >>"$OUT/results.txt"
	else
		echo "$1 FAILS: $(grep -E '^(PASS|FAIL)|rror' "$OUT/$1-compare.txt" | tail -1) (see $2)" >>"$OUT/results.txt"
	fi
}
cage_run() { WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 300 cage -- tools/nested-x.sh 1280x1280 bash -c "$1" >>"$OUT/cage.log" 2>&1; }

# libGDX, as a game that did not install parallax-background-webp
if tools/parallax-lab-shots.sh "$TWIN" "$OUT/gdx-twin" >/dev/null 2>&1; then
	echo "libGDX READS (without ParallaxWebp)" >>"$OUT/results.txt"
else
	echo "libGDX FAILS without ParallaxWebp.install(): $(grep -m1 -iE 'exception|error' "$OUT/gdx-twin/lab.log")" >>"$OUT/results.txt"
fi

GODOT="${GODOT:-godot}"
"$GODOT" --headless --path engines/godot --import >/dev/null 2>&1
cage_run "$GODOT --path \"$ROOT/engines/godot\" --resolution 1280x720 res://tests/shots.tscn -- \"$ROOT\" \"$TWIN\" \"$OUT/godot\" >\"$OUT/godot.log\" 2>&1"
verdict godot "$OUT/godot.log"

./gradlew -q :jme:shotsClasspath || exit 1
CP="$(cat engines/jme/build/shots.classpath)"
cage_run "env -u WAYLAND_DISPLAY XDG_SESSION_TYPE=x11 java -cp \"$CP\" jks.tools2d.parallax.jme.JmeParallaxShots \"$TWIN\" \"$OUT/jme\" >\"$OUT/jme.log\" 2>&1"
verdict jme "$OUT/jme.log"

engines/threejs/tools/run-page.sh "shots.html?round=$TWIN&break=&skip=" "$OUT/threejs"
mv "$OUT/threejs/threejs.log" "$OUT/threejs.log" 2>/dev/null
verdict threejs "$OUT/threejs.log"
cat "$OUT/results.txt"
