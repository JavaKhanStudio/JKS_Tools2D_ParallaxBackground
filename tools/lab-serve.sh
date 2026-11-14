# shellcheck shell=bash
# lab-serve.sh — sourced, never run: what every tools/*-lab.sh launcher shares (r528). Their Playwright half is
# tools/lab_driver.py.
#
#   . tools/lab-serve.sh
#   [ $MODE = open ] && refuse_agent_window tools/x-lab.sh   (tools/agent-window-guard.sh, sourced here: before the build)
#   lab_serve x-lab.html $BUILD [QUERY]   CHROME, the WAR built (./gradlew :core:browserTestWar unless BUILD is 0) and
#                                         served on a free port until the script exits; URL is the page (?QUERY if given)
#   [ $MODE = open ] && lab_open [WIDTH,HEIGHT]   the lab in a Chrome window (1580,800), served until it closes; exits
#   lab_frames                            FRAMES, a scratch directory removed on exit
#   lab_encode OUT [CRF]                  FRAMES/f%04d.png into the clip OUT at 30 fps (CRF 20)
# on-screen: lab_open is a lab's window, opened only in its open mode, which refuse_agent_window keeps off an agent session.
. tools/agent-window-guard.sh

lab_serve() {
	local page=$1 build=$2 query=${3:-}
	CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
	[ -n "$CHROME" ] || { echo "no Chrome found: set CHROME=" >&2; exit 2; }
	WAR="$PWD/core/build/browserTest/war"
	if [ "$build" = 1 ]; then
		./gradlew -q :core:browserTestWar >/dev/null || exit 1
	fi
	[ -f "$WAR/$page" ] || { echo "$WAR has no $page: run without --no-build" >&2; exit 2; }
	local port
	port=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
	python3 -m http.server "$port" --bind 127.0.0.1 --directory "$WAR" >/dev/null 2>&1 &
	LAB_SERVER=$!
	LAB_PROFILE=$(mktemp -d)
	FRAMES=""
	trap 'kill $LAB_SERVER 2>/dev/null; rm -rf "$LAB_PROFILE" ${FRAMES:+"$FRAMES"}' EXIT
	URL="http://127.0.0.1:$port/$page${query:+?$query}"
	for _ in $(seq 50); do python3 -c "import urllib.request; urllib.request.urlopen('$URL')" 2>/dev/null && break; sleep 0.1; done
}

lab_open() {
	local flags=(--user-data-dir="$LAB_PROFILE" --no-first-run --no-default-browser-check)
	# As tools/browser-test.sh --open: the board's server has no DISPLAY, point Chrome at the session's Wayland socket.
	if [ -z "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ] && [ -S "${XDG_RUNTIME_DIR:-/run/user/$(id -u)}/wayland-0" ]; then
		export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}" WAYLAND_DISPLAY=wayland-0
		flags+=(--ozone-platform=wayland)
	fi
	echo "serving $URL until the Chrome window closes"
	local start=$SECONDS
	"$CHROME" "${flags[@]}" --new-window --window-size="${1:-1580,800}" "$URL" 2>&1 | grep -E 'ERROR|FATAL' >&2
	[ $((SECONDS - start)) -ge 5 ] || { echo "Chrome closed at once: no window (see its errors above)" >&2; exit 1; }
	exit 0
}

lab_frames() {
	FRAMES=$(mktemp -d)
}

lab_encode() {
	ffmpeg -loglevel error -y -framerate 30 -i "$FRAMES/f%04d.png" -vf "pad=ceil(iw/2)*2:ceil(ih/2)*2" \
		-c:v libx264 -pix_fmt yuv420p -crf "${2:-20}" "$1" && echo "clip: $1"
}
