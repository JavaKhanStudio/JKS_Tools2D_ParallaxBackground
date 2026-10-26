#!/usr/bin/env bash
# rings-lab.sh — the rings lab (parallax-readers r48, engines/threejs/tests/rings_lab.html): a page of the rings round
# as the sky of a small 3D world, by PlaxRings, panel A at the library's depths and panel B compressed into front..far,
# a camera flying round the valley, sliders for every option, Copy settings.
#
#   engines/threejs/tools/rings-lab.sh                    the lab: a Chrome window, served until it closes
#   engines/threejs/tools/rings-lab.sh --shot DIR         headless, on the GPU (tools/browser.py): DIR/<scene>-<view>-t<s>.png
#                                                         for each scene of SCENES (default g02 g01 g04), views both,
#                                                         at 0, 8 and 20 s of the flight; fails on a WebGL or console
#                                                         warning or error (DIR/console.log); then the last scene
#                                                         cross-fading into the next, half way and done
#   engines/threejs/tools/rings-lab.sh --shot DIR K=V...  the same with sliders started at values (front=500 far=1200 ...)
#                                                         (round=shaders SCENES="s08 s09": another round's pages)
#   engines/threejs/tools/rings-lab.sh --clip F.mp4 [K=V...]  headless: the first scene of SCENES side by side, 12 s of
#                                                         the flight at 30 fps, stepped through window.ringsLab.at
#                                                         (needs ffmpeg); the same console rule
#
# Needs Chrome (CHROME=, or google-chrome, chromium, chromium-browser on the PATH), python3 and, headless, Python
# Playwright.
# on-screen: with no argument this is the rings lab, a window Simon asked for; --shot is headless Chrome.
set -u
cd "$(dirname "$0")/../../.." || exit 1
MODE=open OUT=""
case "${1:-}" in --shot | --clip) MODE=${1#--}; OUT="$(realpath -m "$2")"; shift 2 ;; esac
if [ "$MODE" = open ]; then . tools/agent-window-guard.sh; refuse_agent_window engines/threejs/tools/rings-lab.sh; fi
CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
[ -n "$CHROME" ] || { echo "no Chrome found: set CHROME=" >&2; exit 2; }

PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$PWD" >/dev/null 2>&1 &
SERVER=$!
PROFILE=$(mktemp -d)
trap 'kill $SERVER 2>/dev/null; rm -rf "$PROFILE"' EXIT
URL="http://127.0.0.1:$PORT/engines/threejs/tests/rings_lab.html"
for _ in $(seq 50); do python3 -c "import urllib.request; urllib.request.urlopen('$URL')" 2>/dev/null && break; sleep 0.1; done

if [ "$MODE" != open ]; then
	python3 engines/threejs/tools/rings_lab_shots.py "$URL" "$CHROME" "$OUT" "$MODE" "${SCENES:-g02 g01 g04}" "$@"
	exit $?
fi
mapfile -t GL < <(python3 tools/browser.py flags "$CHROME")
FLAGS=(--user-data-dir="$PROFILE" --no-first-run --no-default-browser-check "${GL[@]}")
# As tools/depth-lab.sh: the board's server has no DISPLAY, point Chrome at the session's Wayland socket.
if [ -z "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ] && [ -S "${XDG_RUNTIME_DIR:-/run/user/$(id -u)}/wayland-0" ]; then
	export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}" WAYLAND_DISPLAY=wayland-0
	FLAGS+=(--ozone-platform=wayland)
fi
echo "serving $URL until the Chrome window closes"
START=$SECONDS
"$CHROME" "${FLAGS[@]}" --new-window --window-size=1600,820 "$URL" 2>&1 | grep -E 'ERROR|FATAL' >&2
[ $((SECONDS - START)) -ge 5 ] || { echo "Chrome closed at once: no window (see its errors above)" >&2; exit 1; }
exit 0
