#!/usr/bin/env bash
# offscreen.sh COMMAND... — runs a command that opens a window in cage's headless display, off Simon's screen (r118).
#
#   tools/offscreen.sh editor/build/install/bin/ParallaxEditor
#   tools/offscreen.sh java -cp "demo/build/install/demo/lib/*" jks.tools2d.parallax.demo.ParallaxLab demo/lab/round1
#
# The Gradle tasks that open one (:editor:run, :demo:run, :demo:lab, :demo:stress, :jme:run) do it by themselves for a
# board agent (gradle/offscreen.gradle); this is for everything else: an installDist build, a scratch program. The GPU
# stays (cage's display renders on the NVIDIA one here, a desktop window on the Intel one), the sound goes nowhere.
# ATELIER_NO_OFFSCREEN=1, or no cage, runs the command on your display.
set -uo pipefail
[ $# -eq 0 ] && { echo "usage: tools/offscreen.sh COMMAND..." >&2; exit 2; }
if [ "${ATELIER_NO_OFFSCREEN:-0}" != 1 ] && command -v cage >/dev/null 2>&1; then
	exec env WLR_BACKENDS=headless ALSOFT_DRIVERS=null cage -- "$@"
fi
[ "${ATELIER_NO_OFFSCREEN:-0}" != 1 ] && echo "offscreen.sh: cage not found, running on your display instead" >&2
exec "$@"
