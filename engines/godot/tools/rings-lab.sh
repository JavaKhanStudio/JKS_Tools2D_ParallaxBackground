#!/usr/bin/env bash
# rings-lab.sh — the Godot rings lab (parallax-readers r49, engines/godot/tests/rings_lab.tscn): a page of the rings
# round as the sky of a small 3D world, by PlaxRings, panel A at the library's depths and panel B compressed into
# front..far, a camera flying round the valley, sliders for every option, Copy settings. three.js's twin:
# engines/threejs/tools/rings-lab.sh.
#
#   engines/godot/tools/rings-lab.sh                    the lab: a Godot window, until it closes
#   engines/godot/tools/rings-lab.sh K=V...             the same, started at values (scene=g01 view=both front=330 ...)
#                                                       (round=engines/threejs/tests/rings-seam: another round's scenes)
#   engines/godot/tools/rings-lab.sh --shot DIR [K=V]   in cage's headless display: DIR/<scene>-both-t<s>.png for each
#                                                       scene of scenes= (default g02,g01,g04) at 0, 8 and 20 s of the
#                                                       flight (and <scene>-join-t8.png, panel B turned to where a
#                                                       SHADER band closes its ring), then the last cross-fading into
#                                                       the next, half way and done; fails without "rings_lab: PASS" or on a SHADER or
#                                                       SCRIPT ERROR (DIR/godot.log)
#
# Needs godot 4.x (GODOT=), and for --shot cage and Xwayland.
# on-screen: with no --shot this is the rings lab, a window Simon asked for; --shot renders in cage.
set -u
cd "$(dirname "$0")/../../.." || exit 1
ROOT="$PWD"
GODOT="${GODOT:-godot}"
OUT=""
if [ "${1:-}" = --shot ]; then OUT="$(realpath -m "$2")"; shift 2; fi
"$GODOT" --headless --path engines/godot --import >/dev/null 2>&1
if [ -z "$OUT" ]; then
	. tools/agent-window-guard.sh; refuse_agent_window engines/godot/tools/rings-lab.sh
	exec "$GODOT" --path engines/godot --resolution 1600x820 res://tests/rings_lab.tscn -- "$ROOT" "$@"
fi
mkdir -p "$OUT"
ARGS=$(printf ' %q' "$@")
RUN="$GODOT --path \"$ROOT/engines/godot\" --resolution 1600x820 res://tests/rings_lab.tscn -- \"$ROOT\" --shot \"$OUT\"$ARGS >\"$OUT/godot.log\" 2>&1"
if [ "${ATELIER_NO_OFFSCREEN:-0}" = 1 ]; then
	timeout 300 bash -c "$RUN"
else
	WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 300 cage -- tools/nested-x.sh 1600x820 bash -c "$RUN" >"$OUT/cage.log" 2>&1
fi
grep -h "SCRIPT ERROR\|^ERROR" "$OUT/godot.log" | sort | uniq -c | head
grep -q "SHADER ERROR\|SCRIPT ERROR" "$OUT/godot.log" && { echo "FAIL: an error in the lab, see $OUT/godot.log"; exit 1; }
grep -q "^rings_lab: PASS" "$OUT/godot.log" || { echo "FAIL: the rings lab's stills, see $OUT/godot.log"; exit 1; }
ls "$OUT"/*.png
