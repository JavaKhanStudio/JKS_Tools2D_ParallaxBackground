#!/usr/bin/env bash
# agent-screen-check.sh — proves an agent session here cannot put a window on Simon's screen (r118).
#
# .claude/settings.json hands every Claude session in this checkout (the board's workers too) an empty DISPLAY and a
# WAYLAND_DISPLAY that names no socket. A window outside cage then fails to open instead of popping up, whatever
# launched it; cage (WLR_BACKENDS=headless) needs neither and renders as before. With that environment this script:
#   1. draws a round through cage (tools/parallax-lab-shots.sh): must pass;
#   2. draws it on "the screen" (ATELIER_NO_OFFSCREEN=1): must FAIL, GLFW finding no display;
#   3. runs every cage launch that strips the cage's Wayland (the jME frame check and demo check, the Godot frame check
#      when godot is installed) under a runtime dir whose wayland-0 is a sentinel socket, as Simon's gnome-shell is:
#      anything connecting to it would have opened on his screen (r234: `env -u WAYLAND_DISPLAY` inside cage sent
#      GLFW 3.4 to wayland-0, and 106 jME windows to gnome-shell). --only-sentinel runs this one alone.
# Exit 0 when all hold. Needs cage, Xwayland, python3.
set -u
ROOT=$(cd "$(dirname "$0")/.." && pwd) || exit 2
cd "$ROOT" || exit 2
eval "$(python3 -c '
import json, shlex
env = json.load(open(".claude/settings.json"))["env"]
for k in ("DISPLAY", "WAYLAND_DISPLAY"):
    print("export %s=%s" % (k, shlex.quote(env[k])))
')" || { echo "FAIL: .claude/settings.json sets no DISPLAY/WAYLAND_DISPLAY"; exit 1; }
[ -z "$DISPLAY" ] || { echo "FAIL: .claude/settings.json leaves DISPLAY=$DISPLAY"; exit 1; }
[ ! -e "${XDG_RUNTIME_DIR:-/run/user/$(id -u)}/$WAYLAND_DISPLAY" ] || { echo "FAIL: WAYLAND_DISPLAY=$WAYLAND_DISPLAY is a live socket"; exit 1; }
OUT=$(mktemp -d); trap 'rm -rf "$OUT"' EXIT
status=0
if [ "${1:-}" != --only-sentinel ]; then
if tools/parallax-lab-shots.sh engines/godot/tests/transfer "$OUT/cage" >/dev/null 2>&1; then
	echo "ok: cage renders without the screen"
else
	echo "FAIL: cage could not render with the screen stripped, see a rerun of tools/parallax-lab-shots.sh"; status=1
fi
if ATELIER_NO_OFFSCREEN=1 timeout 60 tools/parallax-lab-shots.sh engines/godot/tests/transfer "$OUT/screen" >/dev/null 2>&1; then
	echo "FAIL: a window opened outside cage"; status=1
elif grep -q "Failed to open display" "$OUT/screen/lab.log" 2>/dev/null; then
	echo "ok: a launch outside cage fails (GLFW: no display)"
else
	echo "FAIL: the launch outside cage failed for another reason, see $OUT/screen/lab.log"; status=1; trap - EXIT
fi
fi
RT="$OUT/run"; mkdir -m 700 "$RT"
python3 -c '
import fcntl, socket, sys
lock = open(sys.argv[1] + ".lock", "w"); fcntl.flock(lock, fcntl.LOCK_EX)  # held, as a live compositor does: cage takes wayland-1
s = socket.socket(socket.AF_UNIX); s.bind(sys.argv[1]); s.listen(8)
while True:
    c, _ = s.accept()
    open(sys.argv[2], "a").write("connected\n"); c.close()
' "$RT/wayland-0" "$OUT/sentinel.log" &
SENTINEL=$!
trap 'kill $SENTINEL 2>/dev/null; rm -rf "$OUT"' EXIT
sentinel() {
	local name=$1; shift
	rm -f "$OUT/sentinel.log"
	XDG_RUNTIME_DIR="$RT" "$@" >"$OUT/$name.log" 2>&1 || { echo "FAIL: $name failed, see $OUT/$name.log"; status=1; trap - EXIT; }
	if [ -s "$OUT/sentinel.log" ]; then
		echo "FAIL: $name connected to wayland-0, Simon's screen"; status=1
	else
		echo "ok: $name stays in cage"
	fi
}
sentinel jme-shots tools/jme-parallax-shots.sh engines/godot/tests/transfer
sentinel jme-demo tools/start-demo-check.sh jme
if command -v "${GODOT:-godot}" >/dev/null; then sentinel godot-shots tools/godot-parallax-shots.sh engines/godot/tests/transfer; fi
exit $status
