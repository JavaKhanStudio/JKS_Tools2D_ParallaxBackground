#!/usr/bin/env bash
# start-demo-check.sh [godot|jme] — a demo off screen in cage, SPACE and N pressed, one frame once both fades are over
# (Printemps, night tint):
#   godot  engines/godot/demo.gd, the board's "Start the Godot demo": build/start-demo/godot-keys.png, 3.5 s later.
#   jme    engines/jme's JmeParallaxDemo (r112), the .plax pages behind a cube: build/start-demo/jme-keys.png, 4 s later.
# Needs cage, Xwayland, and godot 4.x for godot.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$(pwd)
ENGINE="${1:-godot}"
OUT="$ROOT/build/start-demo"
mkdir -p "$OUT"; rm -f "$OUT/$ENGINE-keys.png"
case "$ENGINE" in
	godot)
		GODOT="${GODOT:-godot}"
		"$GODOT" --headless --path engines/godot --import >/dev/null 2>&1 || true
		RUN="$GODOT --path \"$ROOT/engines/godot\" --resolution 1280x720 res://tests/demo_keys.tscn -- \"$OUT/godot-keys.png\" >\"$OUT/godot-keys.log\" 2>&1" ;;
	jme)
		./gradlew -q :jme:shotsClasspath
		RUN="env -u WAYLAND_DISPLAY java -Dparallax.jme.demoShot=\"$OUT/jme-keys.png\" -cp \"$(cat engines/jme/build/shots.classpath)\" jks.tools2d.parallax.jme.JmeParallaxDemo >\"$OUT/jme-keys.log\" 2>&1" ;;
	*)
		echo "usage: tools/start-demo-check.sh [godot|jme]" >&2
		exit 2 ;;
esac
WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 120 cage -- tools/nested-x.sh 1280x720 bash -c "$RUN" >/dev/null 2>&1
grep -E "SEVERE|Exception|ERROR" "$OUT/$ENGINE-keys.log" && { echo "FAIL: $OUT/$ENGINE-keys.log"; exit 1; }
[ -f "$OUT/$ENGINE-keys.png" ] || { echo "FAIL: no frame"; exit 1; }
echo "$OUT/$ENGINE-keys.png"
