#!/usr/bin/env bash
# The shadow lab (r425): r365's AI forest page drawn three times by the real reader compiled with GWT, in WebGL: A with
# distance (each shadow's far end partly at the hills' speed, softer and lighter there, the hills casting on the far
# range), B the pines casting their shadow on the hills just behind them, and on nothing else, from one light or two,
# moving with the pines, C as painted (core/browser-test/launcher/.../ShadowLab.java).
# Controls: the view (the page, or without the pines), the camera's scroll, each sun's side and height (sliders, or
# dragged on the dial over C), the second sun on or off, how far up the screen the ground climbs into the picture, its
# softness and darkness; A's distance section: with the hills, soft far, fade far, hills cast, range rise.
#   tools/shadow-lab.sh                 the lab: a Chrome window, served until it closes
#   tools/shadow-lab.sh --shot F.png    one still, headless (6 s in)
#   tools/shadow-lab.sh --views DIR     headless stills: DIR/page.png, DIR/alone.png (without the pines), DIR/two.png (both suns)
#   tools/shadow-lab.sh --clip F.mp4    a clip, headless: 8 s at 30 fps, stepped at game speed (window.shadowLabAct)
#   tools/shadow-lab.sh --slider K=V    before --shot/--views/--clip: start control K (its data-key) at V, a choice at its option's index
#   tools/shadow-lab.sh --copy F.png    checks "Copy settings", headless: drags sun 1 on the dial, turns sun 2 on, clicks,
#                                       fails unless the JSON names every control and those moved; F is the page
# Builds with ./gradlew :core:browserTestWar first (--no-build skips it). Needs Chrome, python3 and, headless, Python
# Playwright and ffmpeg.
# on-screen: with no argument this is the shadow lab, a window Simon asked for; the rest is headless Chrome.
set -u
cd "$(dirname "$0")/.." || exit 1
MODE=open OUT="" BUILD=1 SLIDERS=""
while [ $# -gt 0 ]; do
	case "$1" in
		--shot) MODE=shot; OUT="$(realpath -m "$2")"; shift ;;
		--clip) MODE=clip; OUT="$(realpath -m "$2")"; shift ;;
		--copy) MODE=copy; OUT="$(realpath -m "$2")"; shift ;;
		--views) MODE=views; OUT="$(realpath -m "$2")"; shift ;;
		--slider) SLIDERS="$SLIDERS $2"; shift ;;
		--no-build) BUILD=0 ;;
		*) echo "unknown argument: $1" >&2; exit 2 ;;
	esac
	shift
done
if [ $MODE = open ]; then . tools/agent-window-guard.sh; refuse_agent_window tools/shadow-lab.sh; fi

CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
[ -n "$CHROME" ] || { echo "no Chrome found: set CHROME=" >&2; exit 2; }
WAR="$PWD/core/build/browserTest/war"
if [ $BUILD = 1 ]; then
	./gradlew -q :core:browserTestWar >/dev/null || exit 1
fi
[ -f "$WAR/shadow-lab.html" ] || { echo "$WAR has no shadow-lab.html: run without --no-build" >&2; exit 2; }

PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$WAR" >/dev/null 2>&1 &
SERVER=$!
PROFILE=$(mktemp -d)
trap 'kill $SERVER 2>/dev/null; rm -rf "$PROFILE"' EXIT
URL="http://127.0.0.1:$PORT/shadow-lab.html"
for _ in $(seq 50); do python3 -c "import urllib.request; urllib.request.urlopen('$URL')" 2>/dev/null && break; sleep 0.1; done

if [ $MODE = open ]; then
	FLAGS=(--user-data-dir="$PROFILE" --no-first-run --no-default-browser-check)
	# As tools/browser-test.sh --open: the board's server has no DISPLAY, point Chrome at the session's Wayland socket.
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

# Headless: Playwright drives the system Chrome on SwiftShader, steps the lab through window.shadowLabAct and screenshots
# it, one frame per 1/30 s of game time, so the clip plays at game speed.
FRAMES=$(mktemp -d)
trap 'kill $SERVER 2>/dev/null; rm -rf "$PROFILE" "$FRAMES"' EXIT
python3 - "$URL?clip=1" "$CHROME" "$FRAMES" "$MODE" "$SLIDERS" <<'PY' || exit 1
import sys
from playwright.sync_api import sync_playwright
url, chrome, frames, mode, sliders = sys.argv[1:]
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=chrome, args=["--use-angle=swiftshader", "--enable-unsafe-swiftshader"])
    page = browser.new_page(viewport={"width": 1540, "height": 700})
    errors = []
    # A failed load is reported below with its URL: the console's line names none (r320: it was the favicon).
    page.on("console", lambda m: m.type == "error" and not m.text.startswith("Failed to load resource") and errors.append(m.text))
    page.on("pageerror", lambda e: errors.append(str(e)))
    # Chrome asks every page for a favicon: the lab has none.
    page.on("response", lambda r: r.status >= 400 and not r.url.endswith("/favicon.ico") and errors.append("%d %s" % (r.status, r.url)))
    page.goto(url)
    try:
        page.wait_for_function("window.shadowLabReady === true", timeout=60000)
    except Exception:
        sys.exit("the lab never started: %s" % errors)
    for kv in sliders.split():
        key, value = kv.split("=", 1)
        if page.query_selector("[data-key=%s]" % key) is None:
            sys.exit("no slider %s" % key)
        page.eval_on_selector("[data-key=%s]" % key, "(e, v) => { e.value = v; }", value)
    def shot(path):
        # two animation frames: the step lands in the next render
        page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
        page.screenshot(path=path, full_page=True)
    if mode == "shot":
        page.evaluate("window.shadowLabAct(6)")
        shot(frames + "/still.png")
    elif mode == "views":
        page.evaluate("window.shadowLabAct(6)")
        shot(frames + "/page.png")
        page.eval_on_selector("[data-key=view]", "e => { e.value = '1'; }")
        page.evaluate("window.shadowLabAct(0.5)")
        shot(frames + "/alone.png")
        page.eval_on_selector("[data-key=view]", "e => { e.value = '0'; }")
        page.eval_on_selector("[data-key=sun2On]", "e => { e.value = '1'; }")
        page.evaluate("window.shadowLabAct(0.5)")
        shot(frames + "/two.png")
    elif mode == "copy":
        page.context.grant_permissions(["clipboard-read", "clipboard-write"])
        # Sun 1 dragged on the dial: half its radius right of the middle is on your right, 60 degrees up.
        box = page.locator("#shadow-dial").bounding_box()
        page.mouse.click(box["x"] + box["width"] * (66 + 28) / 132, box["y"] + box["height"] * 66 / 132)
        page.eval_on_selector("[data-key=sun2On]", "e => { e.value = '1'; }")
        page.evaluate("window.shadowLabAct(6)")
        page.click("#shadow-copy")
        page.wait_for_function("document.getElementById('shadow-copy-state').textContent !== ''")
        import json
        got = json.loads(page.input_value("#shadow-copy-json"))
        keys = {"view", "cameraScroll", "sun1Side", "sun1Height", "sun2On", "sun2Side", "sun2Height", "rise", "softness",
                "darkness", "withHills", "softFar", "fadeFar", "hillsCast", "rangeRise"}
        moved = {"sun1Side": {"started": -40, "now": 90}, "sun1Height": {"started": 20, "now": 60}, "sun2On": {"started": 0, "now": 1}}
        if set(got["settings"]) != keys or got["changed"] != moved or "sun 2:" not in got["readout"]:
            sys.exit("Copy settings gave the wrong JSON: %s" % json.dumps(got))
        print("Copy settings: %s; %s" % (page.text_content("#shadow-copy-state"), json.dumps(got["settings"])))
        shot(frames + "/still.png")
    else:
        for i in range(240):
            page.evaluate("window.shadowLabAct(%r)" % (0 if i == 0 else 1 / 30))
            shot(frames + "/f%04d.png" % i)
    if errors:
        print("page errors: %s" % errors, file=sys.stderr)
    browser.close()
PY
if [ $MODE = views ]; then
	mkdir -p "$OUT" && cp "$FRAMES"/page.png "$FRAMES"/alone.png "$FRAMES"/two.png "$OUT"/ && echo "stills: $OUT/{page,alone,two}.png"
elif [ $MODE = shot ] || [ $MODE = copy ]; then
	cp "$FRAMES/still.png" "$OUT" && echo "still: $OUT"
else
	ffmpeg -loglevel error -y -framerate 30 -i "$FRAMES/f%04d.png" -vf "pad=ceil(iw/2)*2:ceil(ih/2)*2" \
		-c:v libx264 -pix_fmt yuv420p -crf 20 "$OUT" && echo "clip: $OUT"
fi
