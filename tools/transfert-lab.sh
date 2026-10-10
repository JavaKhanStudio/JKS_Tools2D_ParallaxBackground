#!/usr/bin/env bash
# The transfert lab (r220): two of round1's pages, back and forth, the transfert (the cross-fade between pages) drawn
# four times by the real reader, compiled with GWT, in WebGL: A as it ships, B through a colour grade (the reader's tint,
# shipped API), C fog creep (the lab's own shader, from FOG's noise) and D the library's dissolve (r250). Sliders: the pages, the transfert's
# length, the hold between, the scroll, B's grade colour, C and D's depth stagger, patches and edge softness.
#   tools/transfert-lab.sh               the lab: a Chrome window, served until it closes
#   tools/transfert-lab.sh --sheet DIR   stills, headless: DIR/p000.png, p025, p050, p075 at those percents of the first
#                                        transfert, p100 just after it, and back050 halfway through the way back
#   tools/transfert-lab.sh --clip F.mp4  a clip, headless: one cycle there and back at 30 fps, stepped at game speed
#   tools/transfert-lab.sh --copy F.png  checks "Copy settings", headless: moves the pages and the stagger, clicks, fails
#                                        unless the JSON names every slider and the two moved; F is the page
#   tools/transfert-lab.sh --slider K=V  before a headless mode: start control K (its data-key) at V, a choice at its option's index
# Builds with ./gradlew :core:browserTestWar first (--no-build skips it). Needs Chrome, python3 and, headless, Python
# Playwright and ffmpeg (--clip).
# on-screen: with no argument this is the transfert lab, a window Simon asked for (r220); the other modes are headless Chrome.
set -u
cd "$(dirname "$0")/.." || exit 1
MODE=open OUT="" BUILD=1 SLIDERS=""
while [ $# -gt 0 ]; do
	case "$1" in
		--sheet) MODE=sheet; OUT="$(realpath -m "$2")"; shift ;;
		--clip) MODE=clip; OUT="$(realpath -m "$2")"; shift ;;
		--copy) MODE=copy; OUT="$(realpath -m "$2")"; shift ;;
		--slider) SLIDERS="$SLIDERS $2"; shift ;;
		--no-build) BUILD=0 ;;
		*) echo "unknown argument: $1" >&2; exit 2 ;;
	esac
	shift
done
if [ $MODE = open ]; then . tools/agent-window-guard.sh; refuse_agent_window tools/transfert-lab.sh; fi

CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
[ -n "$CHROME" ] || { echo "no Chrome found: set CHROME=" >&2; exit 2; }
WAR="$PWD/core/build/browserTest/war"
if [ $BUILD = 1 ]; then
	./gradlew -q :core:browserTestWar >/dev/null || exit 1
fi
[ -f "$WAR/transfert-lab.html" ] || { echo "$WAR has no transfert-lab.html: run without --no-build" >&2; exit 2; }

PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$WAR" >/dev/null 2>&1 &
SERVER=$!
PROFILE=$(mktemp -d)
trap 'kill $SERVER 2>/dev/null; rm -rf "$PROFILE"' EXIT
URL="http://127.0.0.1:$PORT/transfert-lab.html"
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
	"$CHROME" "${FLAGS[@]}" --new-window --window-size=1580,900 "$URL" 2>&1 | grep -E 'ERROR|FATAL' >&2
	[ $((SECONDS - START)) -ge 5 ] || { echo "Chrome closed at once: no window (see its errors above)" >&2; exit 1; }
	exit 0
fi

# Headless: Playwright drives the system Chrome on SwiftShader and steps the lab through window.transfertLabAct.
FRAMES=$(mktemp -d)
trap 'kill $SERVER 2>/dev/null; rm -rf "$PROFILE" "$FRAMES"' EXIT
python3 - "$URL?clip=1" "$CHROME" "$FRAMES" "$MODE" "$SLIDERS" <<'PY' || exit 1
import json, sys
from playwright.sync_api import sync_playwright
url, chrome, frames, mode, sliders = sys.argv[1:]
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=chrome, args=["--use-angle=swiftshader", "--enable-unsafe-swiftshader"])
    page = browser.new_page(viewport={"width": 1540, "height": 740})
    errors = []
    page.on("console", lambda m: m.type == "error" and "favicon" not in m.location.get("url", "") and errors.append(m.text))
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.goto(url)
    try:
        page.wait_for_function("window.transfertLabReady === true", timeout=60000)
    except Exception:
        sys.exit("the lab never started: %s" % errors)
    for kv in sliders.split():
        key, value = kv.split("=", 1)
        if page.query_selector("[data-key=%s]" % key) is None:
            sys.exit("no slider %s" % key)
        page.eval_on_selector("[data-key=%s]" % key, "(e, v) => { e.value = v; }", value)
    page.evaluate("window.transfertLabAct(0)")
    seconds = float(page.input_value("[data-key=seconds]"))
    hold = float(page.input_value("[data-key=hold]"))
    def shot(path):
        # two animation frames: the step lands in the next render
        page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
        page.screenshot(path=path, clip={"x": 0, "y": 0, "width": 1540, "height": 520})
    def act(s):
        page.evaluate("window.transfertLabAct(%r)" % s)
    if mode == "sheet":
        act(hold + 1 / 60)
        done = 0.0
        for percent in (0, 25, 50, 75):
            act(seconds * percent / 100 - done)
            done = seconds * percent / 100
            shot(frames + "/p%03d.png" % percent)
            print("p%03d: %s" % (percent, page.text_content("#transfert-readout")))
        act(seconds - done + 0.1)
        shot(frames + "/p100.png")
        print("p100: %s" % page.text_content("#transfert-readout"))
        act(hold + seconds / 2)
        shot(frames + "/back050.png")
        print("back050: %s" % page.text_content("#transfert-readout"))
    elif mode == "copy":
        page.context.grant_permissions(["clipboard-read", "clipboard-write"])
        page.eval_on_selector("[data-key=pagePair]", "e => { e.value = '2'; }")
        page.eval_on_selector("[data-key=depthStagger]", "e => { e.value = '1.2'; }")
        act(1)
        page.click("#transfert-copy")
        page.wait_for_function("document.getElementById('transfert-copy-state').textContent !== ''")
        got = json.loads(page.input_value("#transfert-copy-json"))
        keys = {"pagePair", "seconds", "hold", "cameraScroll", "gradeColour", "depthStagger", "patches", "softEdge"}
        moved = {"pagePair": {"started": 0, "now": 2}, "depthStagger": {"started": 0.5, "now": 1.2}}
        if set(got["settings"]) != keys or got["changed"] != moved or got["pages"] != "PurpleFairy \u2194 calm":
            sys.exit("Copy settings gave the wrong JSON: %s" % json.dumps(got))
        print("Copy settings: %s; %s" % (page.text_content("#transfert-copy-state"), json.dumps(got["settings"])))
        page.screenshot(path=frames + "/still.png", full_page=True)
    else:
        frames_n = int(round(2 * (hold + seconds) * 30))
        for i in range(frames_n):
            act(0 if i == 0 else 1 / 30)
            shot(frames + "/f%04d.png" % i)
    if errors:
        sys.exit("page errors: %s" % errors)
    browser.close()
PY
if [ $MODE = sheet ]; then
	mkdir -p "$OUT" && cp "$FRAMES"/*.png "$OUT"/ && echo "stills: $OUT/{p000,p025,p050,p075,p100,back050}.png"
elif [ $MODE = copy ]; then
	cp "$FRAMES/still.png" "$OUT" && echo "still: $OUT"
else
	ffmpeg -loglevel error -y -framerate 30 -i "$FRAMES/f%04d.png" -vf "pad=ceil(iw/2)*2:ceil(ih/2)*2" \
		-c:v libx264 -pix_fmt yuv420p -crf 20 "$OUT" && echo "clip: $OUT"
fi
