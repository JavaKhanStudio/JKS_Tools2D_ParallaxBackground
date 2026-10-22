#!/usr/bin/env bash
# ai-gallery.sh [--open | --shot F.png] [ROOT...] — the AI pages gallery (r239): every page tools/ai-page.sh or
# tools/depth-page.sh (r316) made under
# the ROOTs (default build/ai), its film, how each layer was painted and cut, and a form that writes the command for
# another. Built into build/ai-gallery/index.html by tools/r239-ai-strips/gallery.py.
#   tools/ai-gallery.sh                 build it, print its path
#   tools/ai-gallery.sh --open          build it and open it in a Chrome window, served until the window closes
#   tools/ai-gallery.sh --shot F.png    build it and screenshot the whole page, headless (Playwright)
# No page yet: tools/r239-ai-strips/examples.sh build/ai/examples makes r239's six (about 20 min of GPU).
# on-screen: --open is the gallery, a window Simon asked for; --shot is headless Chrome.
set -u
cd "$(dirname "$0")/.." || exit 1
MODE=build OUT=""
ROOTS=()
while [ $# -gt 0 ]; do
	case "$1" in
		--open) MODE=open ;;
		--shot) MODE=shot; OUT="$(realpath -m "$2")"; shift ;;
		-*) echo "unknown argument: $1" >&2; exit 2 ;;
		*) ROOTS+=("$1") ;;
	esac
	shift
done
[ ${#ROOTS[@]} -gt 0 ] || ROOTS=(build/ai)
if [ $MODE = open ]; then . tools/agent-window-guard.sh; refuse_agent_window tools/ai-gallery.sh; fi
GALLERY="$PWD/build/ai-gallery"
mkdir -p "$GALLERY" "${ROOTS[@]}"
python3 tools/r239-ai-strips/gallery.py "$GALLERY" "${ROOTS[@]}" || exit 1
[ $MODE = build ] && exit 0

CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
[ -n "$CHROME" ] || { echo "no Chrome found: set CHROME=" >&2; exit 2; }
PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$GALLERY" >/dev/null 2>&1 &
SERVER=$!
PROFILE=$(mktemp -d)
trap 'kill $SERVER 2>/dev/null; rm -rf "$PROFILE"' EXIT
URL="http://127.0.0.1:$PORT/index.html"
for _ in $(seq 50); do python3 -c "import urllib.request; urllib.request.urlopen('$URL')" 2>/dev/null && break; sleep 0.1; done

if [ $MODE = open ]; then
	FLAGS=(--user-data-dir="$PROFILE" --no-first-run --no-default-browser-check)
	# As tools/fog-lab.sh: the board's server has no DISPLAY, point Chrome at the session's Wayland socket.
	if [ -z "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ] && [ -S "${XDG_RUNTIME_DIR:-/run/user/$(id -u)}/wayland-0" ]; then
		export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}" WAYLAND_DISPLAY=wayland-0
		FLAGS+=(--ozone-platform=wayland)
	fi
	echo "serving $URL until the Chrome window closes"
	START=$SECONDS
	"$CHROME" "${FLAGS[@]}" --new-window --window-size=1400,900 "$URL" 2>&1 | grep -E 'ERROR|FATAL' >&2
	[ $((SECONDS - START)) -ge 5 ] || { echo "Chrome closed at once: no window (see its errors above)" >&2; exit 1; }
	exit 0
fi

# --shot: every "How it was made" opened, the films' posters in place, the whole page; fails on a broken image.
python3 - "$URL" "$CHROME" "$OUT" <<'PY' || exit 1
import sys
from playwright.sync_api import sync_playwright
sys.path.insert(0, "tools")
from browser import launch
url, chrome, out = sys.argv[1:]
with sync_playwright() as p:
    browser = launch(p, chrome)
    page = browser.new_page(viewport={"width": 1400, "height": 900})
    errors = []
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.goto(url)
    page.evaluate("document.querySelectorAll('details').forEach(d => d.open = true);"
                  "document.querySelectorAll('img').forEach(i => i.loading = 'eager')")
    page.wait_for_load_state("networkidle")
    page.wait_for_function("[...document.images].every(i => i.complete)")
    broken = page.evaluate("[...document.images].filter(i => !i.naturalWidth).map(i => i.src)")
    page.screenshot(path=out, full_page=True)
    print("command form:", page.text_content("#m-cmd"))
    browser.close()
    if broken or errors:
        sys.exit("broken images %s, page errors %s" % (broken, errors))
PY
echo "shot: $OUT"
