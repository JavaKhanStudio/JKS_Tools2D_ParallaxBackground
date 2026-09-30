#!/usr/bin/env bash
# jme-parallax-shots.sh [ROUND_DIR...] — the jMonkeyEngine reader (engines/jme) against the libGDX runtime, frame by frame (r112).
#
#   tools/jme-parallax-shots.sh                          engines/godot/tests/round1, /conformance and /transfer
#   tools/jme-parallax-shots.sh engines/godot/tests/round1
#
# The same rounds and stills as tools/godot-parallax-shots.sh: libGDX's (tools/parallax-lab-shots.sh, 0, 6 and 12 s into
# the lab's scroll) and jME's (engines/jme/tests/.../JmeParallaxShots, the same scroll), off screen in cage's headless
# display on a nested Xwayland, compared by tools/compare-parallax-frames.py. Writes build/jme/<round>/: gdx/,
# jme/, compare.png and report.txt. Fails when a still differs by more than THRESHOLD (mean absolute difference per
# channel, 0-255; default 2). Needs cage, Xwayland, python3 with Pillow. ATELIER_NO_OFFSCREEN=1 draws both on
# $DISPLAY instead. BREAK=scroll draws jME with a scroll 10 % too fast: it must FAIL (the threshold's negative control).
set -u
cd "$(dirname "$0")/.."
ROOT="$PWD"
THRESHOLD="${THRESHOLD:-2}"
ROUNDS=("$@")
[ ${#ROUNDS[@]} -eq 0 ] && ROUNDS=(engines/godot/tests/round1 engines/godot/tests/conformance engines/godot/tests/transfer)
./gradlew -q :jme:shotsClasspath || exit 1
CP="$(cat engines/jme/build/shots.classpath)"
status=0
for ROUND in "${ROUNDS[@]}"; do
	OUT="$ROOT/build/jme/$(basename "$ROUND")"
	rm -rf "$OUT"; mkdir -p "$OUT/jme"
	tools/parallax-lab-shots.sh "$ROUND" "$OUT/gdx" >/dev/null || { echo "libGDX shots failed: $OUT/gdx/lab.log"; exit 1; }
	RUN="env -u WAYLAND_DISPLAY java -Dparallax.jme.break=${BREAK:-} -cp \"$CP\" jks.tools2d.parallax.jme.JmeParallaxShots \"$ROUND\" \"$OUT/jme\" >\"$OUT/jme.log\" 2>&1"
	if [ "${ATELIER_NO_OFFSCREEN:-0}" = 1 ]; then
		timeout 300 bash -c "$RUN"
	else
		inner="Xwayland :9 -geometry 1280x1280 & X=\$!; sleep 2; DISPLAY=:9 $RUN; kill \$X 2>/dev/null"
		WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 300 cage -- bash -c "$inner" >"$OUT/cage.log" 2>&1
	fi
	python3 tools/compare-parallax-frames.py "$ROUND" "$OUT" "$THRESHOLD" jme || status=1
done
exit $status
