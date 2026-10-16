#!/usr/bin/env bash
# The clouds lab (r451, out of the animation lab): r375's BILLOW as lab GLSL, drawn by the real reader compiled with
# GWT, in WebGL: round1's calm clouds, on a layer of their own, under BILLOW beside them still, in the page and alone.
# Sliders: zoom, the camera's scroll, the clouds' drift, and BILLOW's numbers; the view on the first panel's title.
#   tools/clouds-lab.sh                 the lab: a Chrome window, served until it closes
#   tools/clouds-lab.sh --shot F.png    one still, headless (6 s in)
#   tools/clouds-lab.sh --views DIR     one still per view, headless: DIR/view0.png ...
#   tools/clouds-lab.sh --clip F.mp4    a clip, headless: 8 s at 30 fps, stepped at game speed (window.cloudsLabAct)
#   tools/clouds-lab.sh --slider K=V    before --shot/--views/--clip: start control K (its data-key) at V, a choice at its option's index
#   tools/clouds-lab.sh --copy F.png    checks "Copy settings", headless: moves two controls, clicks, fails unless the JSON
#                                       names every control and the two moved; F is the page
# Builds with ./gradlew :core:browserTestWar first (--no-build skips it). Needs Chrome, python3 and, headless, Python
# Playwright and ffmpeg.
# on-screen: with no argument this is the clouds lab, a window Simon asked for; the rest is headless Chrome.
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
if [ $MODE = open ]; then . tools/agent-window-guard.sh; refuse_agent_window tools/clouds-lab.sh; fi

CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
[ -n "$CHROME" ] || { echo "no Chrome found: set CHROME=" >&2; exit 2; }
WAR="$PWD/core/build/browserTest/war"
if [ $BUILD = 1 ]; then
	./gradlew -q :core:browserTestWar >/dev/null || exit 1
fi
[ -f "$WAR/clouds-lab.html" ] || { echo "$WAR has no clouds-lab.html: run without --no-build" >&2; exit 2; }

PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$WAR" >/dev/null 2>&1 &
SERVER=$!
PROFILE=$(mktemp -d)
trap 'kill $SERVER 2>/dev/null; rm -rf "$PROFILE"' EXIT
URL="http://127.0.0.1:$PORT/clouds-lab.html"
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

# Headless: Playwright drives the system Chrome on SwiftShader, steps the lab through window.cloudsLabAct and screenshots
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
    # WebGL's own errors come as warnings, and the frame still draws: a uniform the lab set on a shipped shader of the
    # wrong size painted WAVE's sea white with nothing failing (r419). Any of them fails the run.
    webgl = []
    page.on("console", lambda m: m.type == "warning" and "WebGL" in m.text and webgl.append(m.text))
    page.on("pageerror", lambda e: errors.append(str(e)))
    # Chrome asks every page for a favicon: the lab has none.
    page.on("response", lambda r: r.status >= 400 and not r.url.endswith("/favicon.ico") and errors.append("%d %s" % (r.status, r.url)))
    page.goto(url)
    try:
        page.wait_for_function("window.cloudsLabReady === true", timeout=60000)
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
        page.evaluate("window.cloudsLabAct(6)")
        shot(frames + "/still.png")
    elif mode == "views":
        for i in range(int(page.eval_on_selector("[data-key=view]", "e => e.options.length"))):
            page.eval_on_selector("[data-key=view]", "(e, v) => { e.value = v; }", str(i))
            page.evaluate("window.cloudsLabAct(%r)" % (6 if i == 0 else 0.5))
            shot(frames + "/view%d.png" % i)
    elif mode == "copy":
        page.context.grant_permissions(["clipboard-read", "clipboard-write"])
        page.eval_on_selector("[data-key=billowAmplitude]", "e => { e.value = '0.5'; }")
        page.eval_on_selector("[data-key=view]", "e => { e.value = '2'; }")
        page.evaluate("window.cloudsLabAct(6)")
        page.click("#clouds-copy")
        page.wait_for_function("document.getElementById('clouds-copy-state').textContent !== ''")
        import json
        got = json.loads(page.input_value("#clouds-copy-json"))
        keys = {"view", "zoom", "cameraScroll", "cloudDrift", "billowAmplitude", "billowWavelength", "billowSpeed"}
        moved = {"billowAmplitude": {"started": 0.3, "now": 0.5}, "view": {"started": 0, "now": 2}}
        if set(got["settings"]) != keys or got["changed"] != moved:
            sys.exit("Copy settings gave the wrong JSON: %s" % json.dumps(got))
        print("Copy settings: %s; %s" % (page.text_content("#clouds-copy-state"), json.dumps(got["settings"])))
        shot(frames + "/still.png")
    else:
        for i in range(240):
            page.evaluate("window.cloudsLabAct(%r)" % (0 if i == 0 else 1 / 30))
            shot(frames + "/f%04d.png" % i)
    if errors:
        print("page errors: %s" % errors, file=sys.stderr)
    browser.close()
    if webgl:
        sys.exit("WebGL errors (%d), first: %s" % (len(webgl), webgl[0]))
PY
if [ $MODE = views ]; then
	mkdir -p "$OUT" && cp "$FRAMES"/view*.png "$OUT"/ && echo "stills: $OUT/view*.png"
elif [ $MODE = shot ] || [ $MODE = copy ]; then
	cp "$FRAMES/still.png" "$OUT" && echo "still: $OUT"
else
	ffmpeg -loglevel error -y -framerate 30 -i "$FRAMES/f%04d.png" -vf "pad=ceil(iw/2)*2:ceil(ih/2)*2" \
		-c:v libx264 -pix_fmt yuv420p -crf 20 "$OUT" && echo "clip: $OUT"
fi
