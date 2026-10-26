#!/usr/bin/env bash
# rings-lab.sh — the jME rings lab (parallax-readers r50, engines/jme/tests/.../JmeRingsLab): a page of the rings round
# as the sky of a small 3D world, by jME's PlaxRings, panel A at the library's depths and panel B compressed into
# front..far, a camera flying round the valley, knobs for every option, Copy settings. three.js's twin is
# engines/threejs/tools/rings-lab.sh; both open at Simon's look on r48.
#
#   engines/jme/tools/rings-lab.sh                    the lab: a window on the desktop, until it closes
#   engines/jme/tools/rings-lab.sh --shot DIR         off screen in cage: DIR/<scene>-both-t<s>.png for each scene of SCENES
#                                                     (default g02 g01 g04) at 0, 8 and 20 s of the flight, then the last
#                                                     scene cross-fading into the next, half way and done, and Copy settings
#                                                     checked (DIR/settings.json); fails on a jME error or warning in DIR/lab.log
#   engines/jme/tools/rings-lab.sh --shot DIR K=V...  the same with knobs started at values (front=500 far=1200 ...)
#
# Needs a JDK; --shot needs cage and Xwayland (tools/nested-x.sh).
# on-screen: with no argument this is the rings lab, a window Simon asked for; --shot runs in cage.
set -u
cd "$(dirname "$0")/../../.." || exit 1
MODE=open OUT=""
if [ "${1:-}" = --shot ]; then MODE=shot; OUT="$(realpath -m "$2")"; shift 2; fi
if [ "$MODE" = open ]; then . tools/agent-window-guard.sh; refuse_agent_window engines/jme/tools/rings-lab.sh; fi
./gradlew -q :jme:shotsClasspath || exit 1
CP="$(cat engines/jme/build/shots.classpath)"
MAIN=jks.tools2d.parallax.jme.JmeRingsLab

if [ "$MODE" = open ]; then
	# As tools/start-demo.sh: the board's server has no display variables, the window goes on the session's Xwayland.
	export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}" DISPLAY="${DISPLAY:-:0}"
	if [ -z "${XAUTHORITY:-}" ]; then
		for f in "$XDG_RUNTIME_DIR"/.mutter-Xwaylandauth.*; do [ -f "$f" ] && export XAUTHORITY="$f"; done
	fi
	exec env -u WAYLAND_DISPLAY XDG_SESSION_TYPE=x11 java -cp "$CP" "$MAIN" "$@"
fi

mkdir -p "$OUT"
# X11, cage's nested one, as engines/jme/tools/frame-check.sh runs its runner (r234).
RUN="env -u WAYLAND_DISPLAY XDG_SESSION_TYPE=x11 java -cp \"$CP\" $MAIN --shot \"$OUT\" --scenes \"${SCENES:-g02 g01 g04}\" $* >\"$OUT/lab.log\" 2>&1"
WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 300 cage -- tools/nested-x.sh 1600x760 bash -c "$RUN" >"$OUT/cage.log" 2>&1
grep -m1 -i "renderer:" "$OUT/lab.log"
# A lab draws clean: a shader that did not compile, a GL error or an exception fails it.
if grep -E "SEVERE|WARNING: (Bad|Unable|Shader|Uniform)|Exception|GL_INVALID|FAIL" "$OUT/lab.log"; then
	echo "FAIL: the lab warned or failed, see $OUT/lab.log"
	exit 1
fi
grep -q "PASS" "$OUT/lab.log" || { echo "FAIL: the lab did not finish, see $OUT/lab.log and $OUT/cage.log"; exit 1; }
tail -1 "$OUT/lab.log"
