#!/usr/bin/env bash
# start-demo-check.sh [godot|jme] [mid] — a demo off screen in cage, SPACE and N pressed, one frame once both fades are
# over (Printemps, night tint). With mid: SPACE alone, the frame halfway through the 3 s transfert, in the demo's
# default style (the depth stagger, r254: the back layers already Printemps, the front ones still Hiver), saved as
# <engine>-mid.png.
#   godot  engines/godot/demo.gd, the board's "Start the Godot demo": build/start-demo/godot-keys.png, 3.5 s later.
#   jme    engines/jme's JmeParallaxDemo (r112), the .plax pages behind a cube: build/start-demo/jme-keys.png, 4 s later.
# Needs cage, Xwayland, and godot 4.x for godot.
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$(pwd)
ENGINE="${1:-godot}"
MODE="${2:-keys}"
[ "$MODE" = keys ] || [ "$MODE" = mid ] || { echo "usage: tools/start-demo-check.sh [godot|jme] [mid]" >&2; exit 2; }
OUT="$ROOT/build/start-demo"
SHOT="$ENGINE-$MODE"
mkdir -p "$OUT"; rm -f "$OUT/$SHOT.png"
case "$ENGINE" in
	godot)
		GODOT="${GODOT:-godot}"
		"$GODOT" --headless --path engines/godot --import >/dev/null 2>&1 || true
		RUN="$GODOT --path \"$ROOT/engines/godot\" --resolution 1280x720 res://tests/demo_keys.tscn -- \"$OUT/$SHOT.png\" $MODE >\"$OUT/$SHOT.log\" 2>&1" ;;
	jme)
		./gradlew -q :jme:shotsClasspath
		# X11, cage's nested one: with no WAYLAND_DISPLAY, GLFW 3.4 tries the default wayland-0 first, Simon's desktop, not
		# cage (r234). XDG_SESSION_TYPE=x11 makes it pick X11 without trying. tools/agent-screen-check.sh holds it.
		RUN="env -u WAYLAND_DISPLAY XDG_SESSION_TYPE=x11 java -Dparallax.jme.demoShot=\"$OUT/$SHOT.png\" -Dparallax.jme.demoShotMid=$([ "$MODE" = mid ] && echo true || echo false) -cp \"$(cat engines/jme/build/shots.classpath)\" jks.tools2d.parallax.jme.JmeParallaxDemo >\"$OUT/$SHOT.log\" 2>&1" ;;
	*)
		echo "usage: tools/start-demo-check.sh [godot|jme] [mid]" >&2
		exit 2 ;;
esac
WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 120 cage -- tools/nested-x.sh 1280x720 bash -c "$RUN" >/dev/null 2>&1
grep -E "SEVERE|Exception|ERROR" "$OUT/$SHOT.log" && { echo "FAIL: $OUT/$SHOT.log"; exit 1; }
[ -f "$OUT/$SHOT.png" ] || { echo "FAIL: no frame"; exit 1; }
echo "$OUT/$SHOT.png"
