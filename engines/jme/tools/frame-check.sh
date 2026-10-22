#!/usr/bin/env bash
# frame-check.sh [ROUND_DIR...] — the jMonkeyEngine reader (engines/jme) against the libGDX runtime, frame by frame (r112).
#
#   engines/jme/tools/frame-check.sh                          engines/rounds/round1, /conformance, /transfer, /pixelart, /effects, /particles, /shaders, /sequence, /fog, /floor, /dolly, /tower, /planet, /animated, /lurker and /glint
#   engines/jme/tools/frame-check.sh engines/rounds/round1
#
# The same rounds and stills as engines/godot/tools/frame-check.sh: libGDX's (tools/parallax-lab-shots.sh, 0, 6 and 12 s into
# the lab's scroll) and jME's (engines/jme/tests/.../JmeParallaxShots, the same scroll), off screen in cage's headless
# display on a nested Xwayland, compared by tools/compare-parallax-frames.py. Writes build/jme/<round>/ (a link to this run's own folder: tools/shots-out.sh): gdx/,
# jme/, compare.png and report.txt. Fails when a still differs by more than THRESHOLD (mean absolute difference per
# channel, 0-255; default 2). Needs cage, Xwayland, python3 with Pillow. ATELIER_NO_OFFSCREEN=1 draws both on
# $DISPLAY instead. BREAK=scroll draws jME with a scroll 10 % too fast: it must FAIL (the threshold's negative control).
# engines/rounds/particles is compared by pixels too: both runners seed the particles' random (Round.seedRandom), and the round
# fails past its pixelsOff. BREAK=particles (no flakes), anchor (VIEW drawn as LAYER) and blend (every emitter blended
# normally) must FAIL it (parallax-readers:r31).
# BREAK=lurker reveals the lurker round's figure every step, always there: engines/rounds/lurker must FAIL (r370).
set -u
cd "$(dirname "$0")/../../.." || exit 1
ROOT="$PWD"
. tools/shots-out.sh
THRESHOLD="${THRESHOLD:-2}"
ROUNDS=("$@")
[ ${#ROUNDS[@]} -eq 0 ] && ROUNDS=(engines/rounds/round1 engines/rounds/conformance engines/rounds/transfer engines/rounds/pixelart engines/rounds/effects engines/rounds/particles engines/rounds/shaders engines/rounds/sequence engines/rounds/fog engines/rounds/floor engines/rounds/dolly engines/rounds/tower engines/rounds/planet engines/rounds/animated engines/rounds/lurker engines/rounds/glint)
./gradlew -q :jme:shotsClasspath || exit 1
CP="$(cat engines/jme/build/shots.classpath)"
status=0
for ROUND in "${ROUNDS[@]}"; do
	OUT=$(shots_out "$ROOT/build/jme" "$ROUND"); mkdir -p "$OUT/jme"
	tools/parallax-lab-shots.sh "$ROUND" "$OUT/gdx" >/dev/null || { echo "libGDX shots failed: $OUT/gdx/lab.log"; exit 1; }
	# X11, cage's nested one: with no WAYLAND_DISPLAY, GLFW 3.4 tries the default wayland-0 first, Simon's desktop, not
	# cage (r234). XDG_SESSION_TYPE=x11 makes it pick X11 without trying. tools/agent-screen-check.sh holds it.
	RUN="env -u WAYLAND_DISPLAY XDG_SESSION_TYPE=x11 java -Dparallax.jme.break=${BREAK:-} -cp \"$CP\" jks.tools2d.parallax.jme.JmeParallaxShots \"$ROUND\" \"$OUT/jme\" >\"$OUT/jme.log\" 2>&1"
	if [ "${ATELIER_NO_OFFSCREEN:-0}" = 1 ]; then
		timeout 300 bash -c "$RUN"
	else
		WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 300 cage -- tools/nested-x.sh 1280x1280 bash -c "$RUN" >"$OUT/cage.log" 2>&1
	fi
	python3 tools/compare-parallax-frames.py "$ROUND" "$OUT" "$THRESHOLD" jme || status=1
done
exit $status
