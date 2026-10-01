#!/usr/bin/env bash
# offscreen.sh COMMAND... — runs a command that opens a window in cage's headless display, off Simon's screen (r118).
#
#   tools/offscreen.sh shots/build/install/shots/bin/shots engines/godot/tests/round1 build/lab/round1
#   tools/offscreen.sh java -cp "$(cat engines/jme/build/shots.classpath)" jks.tools2d.parallax.jme.JmeParallaxDemo
#
# The Gradle tasks that open one (:jme:run, :shots:run) do it by themselves for a
# board agent (gradle/offscreen.gradle); this is for everything else: an installDist build, a scratch program. The GPU
# stays (cage's display renders on the NVIDIA one here, a desktop window on the Intel one), the sound goes nowhere.
# ATELIER_NO_OFFSCREEN=1 runs the command on your display. Without cage it stops (exit 2) rather than open the window
# on the screen (r144).
set -uo pipefail
[ $# -eq 0 ] && { echo "usage: tools/offscreen.sh COMMAND..." >&2; exit 2; }
[ "${ATELIER_NO_OFFSCREEN:-0}" = 1 ] && exec "$@"
command -v cage >/dev/null 2>&1 || {
	echo "offscreen.sh: cage not found: $1 would open on your screen. Install cage, or set ATELIER_NO_OFFSCREEN=1." >&2
	exit 2
}
exec env WLR_BACKENDS=headless ALSOFT_DRIVERS=null cage -- "$@"
