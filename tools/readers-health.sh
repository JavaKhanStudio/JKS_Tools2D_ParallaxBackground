#!/usr/bin/env bash
# readers-health.sh [--open | --shot F.png] [--no-ci] [--run [READER...]] — the readers' dashboard (parallax-readers:r17):
# each reader (libGDX, Godot, jME, Unity), the checks it has, when they last ran here and in CI and how, every round's
# verdict per reader, and each one's latency on the bench round (engines/rounds/bench). Built into
# build/readers-health/index.html by tools/readers-health/health.py, which reads what the checks left and runs nothing.
#   tools/readers-health.sh                  build it, print the summary; exits 1 when a reader is failing
#   tools/readers-health.sh --run            run every installed reader's checks and bench first (~10 min), then build it
#   tools/readers-health.sh --run godot jme  only those readers' (gdx, godot, jme)
#   tools/readers-health.sh --open           build it and open it in a Chrome window
#   tools/readers-health.sh --shot F.png     build it and screenshot the whole page, headless (Playwright)
# --run's logs: build/readers-health/run-<reader>.log. --no-ci skips the last CI run on develop (read through gh).
# on-screen: --open is the dashboard, a window Simon asked for; --shot is headless Chrome; --run's checks run in cage.
set -u
cd "$(dirname "$0")/.." || exit 1
MODE=build OUT="" RUN=0 CI=""
READERS=()
while [ $# -gt 0 ]; do
	case "$1" in
		--open) MODE=open ;;
		--shot) MODE=shot; OUT="$(realpath -m "$2")"; shift ;;
		--no-ci) CI=--no-ci ;;
		--run) RUN=1 ;;
		gdx|godot|jme) READERS+=("$1") ;;
		*) echo "unknown argument: $1" >&2; exit 2 ;;
	esac
	shift
done
if [ $MODE = open ]; then . tools/agent-window-guard.sh; refuse_agent_window tools/readers-health.sh; fi
DASH="$PWD/build/readers-health"
mkdir -p "$DASH"
if [ $RUN = 1 ]; then
	[ ${#READERS[@]} -gt 0 ] || READERS=(gdx godot jme)
	for R in "${READERS[@]}"; do
		LOG="$DASH/run-$R.log"
		case $R in
			gdx) CMD="./gradlew :core:test && tools/parallax-bench.sh" ;;
			godot) command -v "${GODOT:-godot}" >/dev/null || { echo "$R: godot not installed, skipped"; continue; }
				CMD="engines/godot/tools/frame-check.sh && engines/godot/tools/bench.sh" ;;
			jme) CMD="engines/jme/tools/frame-check.sh && engines/jme/tools/bench.sh" ;;
		esac
		echo "$R: $CMD  (log: $LOG)"
		bash -c "$CMD" >"$LOG" 2>&1 && echo "$R: PASS" || echo "$R: FAIL, see $LOG"
	done
fi
python3 tools/readers-health/health.py "$DASH" $CI
STATUS=$?
[ $MODE = build ] && exit $STATUS

CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
[ -n "$CHROME" ] || { echo "no Chrome found: set CHROME=" >&2; exit 2; }
URL="file://$DASH/index.html"
if [ $MODE = open ]; then
	PROFILE=$(mktemp -d)
	trap 'rm -rf "$PROFILE"' EXIT
	FLAGS=(--user-data-dir="$PROFILE" --no-first-run --no-default-browser-check)
	# As tools/ai-gallery.sh: the board's server has no DISPLAY, point Chrome at the session's Wayland socket.
	if [ -z "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ] && [ -S "${XDG_RUNTIME_DIR:-/run/user/$(id -u)}/wayland-0" ]; then
		export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}" WAYLAND_DISPLAY=wayland-0
		FLAGS+=(--ozone-platform=wayland)
	fi
	"$CHROME" "${FLAGS[@]}" --new-window --window-size=1400,900 "$URL" 2>&1 | grep -E 'ERROR|FATAL' >&2
	exit 0
fi
python3 - "$URL" "$CHROME" "$OUT" <<'PY' || exit 1
import sys
from playwright.sync_api import sync_playwright
url, chrome, out = sys.argv[1:]
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=chrome)
    page = browser.new_page(viewport={"width": 1400, "height": 900})
    page.goto(url)
    page.screenshot(path=out, full_page=True)
    browser.close()
PY
echo "shot: $OUT"
