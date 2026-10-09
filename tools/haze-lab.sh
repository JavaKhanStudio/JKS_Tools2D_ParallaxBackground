#!/usr/bin/env bash
# The haze lab (r215, doubt d17): the haze round's h01 with towers (an EMPTY layer, drawn by the lab's hook) and snow (a
# PARTICLES layer) behind its mist, drawn four times by the real reader, compiled with GWT, in WebGL: A as the depth haze
# ships (towers and snow not hazed, the haze's white untinted), B the towers and snow hazed too, C the haze's white
# times the page's tint, D both. Sliders: the mist's haze, the tint and its strength, the scroll, the towers' brightness.
#   tools/haze-lab.sh                 the lab: a Chrome window, served until it closes
#   tools/haze-lab.sh --shot F.png    one still, headless (6 s into the scroll)
#   tools/haze-lab.sh --same F.png    checks the lab's drawing, headless: with &same=1, B to D draw as A does, without
#                                     the snow (random in each panel), and must match A; F is the still
#   tools/haze-lab.sh --copy F.png    checks "Copy settings", headless: moves the haze and the tint, clicks, fails unless
#                                     the JSON names every slider and the two moved; F is the page
# Builds with ./gradlew :core:browserTestWar first (--no-build skips it). Needs Chrome, python3 and, headless, Python
# Playwright and Pillow.
# on-screen: with no argument this is the haze lab, a window Simon asked for (d17); the other modes are headless Chrome.
set -u
cd "$(dirname "$0")/.." || exit 1
MODE=open OUT="" BUILD=1
while [ $# -gt 0 ]; do
	case "$1" in
		--shot) MODE=shot; OUT="$(realpath -m "$2")"; shift ;;
		--same) MODE=same; OUT="$(realpath -m "$2")"; shift ;;
		--copy) MODE=copy; OUT="$(realpath -m "$2")"; shift ;;
		--no-build) BUILD=0 ;;
		*) echo "unknown argument: $1" >&2; exit 2 ;;
	esac
	shift
done

CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
[ -n "$CHROME" ] || { echo "no Chrome found: set CHROME=" >&2; exit 2; }
WAR="$PWD/core/build/browserTest/war"
if [ $BUILD = 1 ]; then
	./gradlew -q :core:browserTestWar >/dev/null || exit 1
fi
[ -f "$WAR/haze-lab.html" ] || { echo "$WAR has no haze-lab.html: run without --no-build" >&2; exit 2; }

PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$WAR" >/dev/null 2>&1 &
SERVER=$!
PROFILE=$(mktemp -d)
trap 'kill $SERVER 2>/dev/null; rm -rf "$PROFILE"' EXIT
URL="http://127.0.0.1:$PORT/haze-lab.html"
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
	"$CHROME" "${FLAGS[@]}" --new-window --window-size=1580,800 "$URL" 2>&1 | grep -E 'ERROR|FATAL' >&2
	[ $((SECONDS - START)) -ge 5 ] || { echo "Chrome closed at once: no window (see its errors above)" >&2; exit 1; }
	exit 0
fi

# Headless: Playwright drives the system Chrome on SwiftShader and steps the lab through window.hazeLabAct.
QUERY="?clip=1"
[ $MODE = same ] && QUERY="?clip=1&same=1"
python3 - "$URL$QUERY" "$CHROME" "$OUT" "$MODE" <<'PY' || exit 1
import sys, json
from playwright.sync_api import sync_playwright
url, chrome, out, mode = sys.argv[1:]
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=chrome, args=["--use-angle=swiftshader", "--enable-unsafe-swiftshader"])
    page = browser.new_page(viewport={"width": 1540, "height": 760})
    errors = []
    page.on("console", lambda m: m.type == "error" and not m.text.startswith("Failed to load resource") and errors.append(m.text))
    page.on("pageerror", lambda e: errors.append(str(e)))
    # Chrome asks every page for a favicon: the lab has none.
    page.on("response", lambda r: r.status >= 400 and not r.url.endswith("/favicon.ico") and errors.append("%d %s" % (r.status, r.url)))
    page.goto(url)
    try:
        page.wait_for_function("window.hazeLabReady === true", timeout=60000)
    except Exception:
        sys.exit("the lab never started: %s" % errors)
    def shot(path):
        # two animation frames: the step lands in the next render
        page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
        page.screenshot(path=path, full_page=True)
    if mode == "copy":
        page.context.grant_permissions(["clipboard-read", "clipboard-write"])
        page.eval_on_selector("input[data-key=depthHaze]", "e => { e.value = '0.4'; }")
        page.eval_on_selector("input[data-key=pageTint]", "e => { e.value = '2'; }")
        page.evaluate("window.hazeLabAct(6)")
        page.click("#haze-copy")
        page.wait_for_function("document.getElementById('haze-copy-state').textContent !== ''")
        got = json.loads(page.input_value("#haze-copy-json"))
        keys = {"depthHaze", "pageTint", "tintStrength", "cameraScroll", "towersLight"}
        moved = {"depthHaze": {"started": 0.25, "now": 0.4}, "pageTint": {"started": 1, "now": 2}}
        if set(got["settings"]) != keys or got["changed"] != moved or got["tint"] != "night blue":
            sys.exit("Copy settings gave the wrong JSON: %s" % json.dumps(got))
        print("Copy settings: %s; %s" % (page.text_content("#haze-copy-state"), json.dumps(got["settings"])))
    else:
        page.evaluate("window.hazeLabAct(6)")
    print(page.text_content("#haze-readout"))
    shot(out)
    if errors:
        sys.exit("page errors: %s" % errors)
    browser.close()
PY
echo "still: $OUT"
[ $MODE = same ] || exit 0
# The panels sit at x 0 and 764, y 0 and 254, 760 x 250 each (haze-lab.html), their titles in the top 30 px: below them,
# no pixel may differ by more than 2/255, or the lab's drawing is not the reader's.
python3 - "$OUT" <<'PY'
import sys
from PIL import Image, ImageChops
im = Image.open(sys.argv[1]).convert("RGB")
box = lambda x, y: im.crop((8 + x, 8 + y + 30, 8 + x + 760, 8 + y + 250))
a = box(0, 0)
worst = 0
for name, (x, y) in {"B": (764, 0), "C": (0, 254), "D": (764, 254)}.items():
    diff = ImageChops.difference(a, box(x, y)).convert("L")
    hist = diff.histogram()
    off = sum(hist[3:])
    mean = sum(i * n for i, n in enumerate(hist)) / (760 * 220)
    print("%s vs A: %d px differ by more than 2/255, mean %.3f/255" % (name, off, mean))
    worst = max(worst, off)
sys.exit("the lab's one-layer-at-a-time drawing is not the reader's" if worst > 0 else 0)
PY
