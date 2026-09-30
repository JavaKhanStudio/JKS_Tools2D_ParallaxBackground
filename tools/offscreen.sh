#!/usr/bin/env bash
# offscreen.sh COMMAND... — runs a command that opens a window in cage's headless display, off Simon's screen (r118).
#
#   tools/offscreen.sh editor/build/install/bin/ParallaxEditor
#   tools/offscreen.sh java -cp "demo/build/install/demo/lib/*" jks.tools2d.parallax.demo.ParallaxLab demo/lab/round1
#
# The Gradle tasks that open one (:editor:run, :demo:run, :demo:lab, :demo:stress, :jme:run) do it by themselves for a
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
