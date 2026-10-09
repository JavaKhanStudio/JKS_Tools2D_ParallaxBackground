#!/usr/bin/env bash
# The FOG lab (r204, doubt d15): the shaders round's s01 page drawn four times by the real reader, compiled with GWT,
# in WebGL: its mist band through FOG with patches as tall as wide (A, what ships), half as tall (B), as tall as a
# slider says (C), and plain. Sliders: amplitude, wavelength, speed, C's height, the camera's scroll, the trees' WAVE ripple,
# the mist's layer, the page fog's strength and colour (r217), the parallax set, the mist's size and height. It opens on Simon's r208 settings.
#   tools/fog-lab.sh                 the lab: a Chrome window, served until it closes
#   tools/fog-lab.sh --shot F.png    one still, headless (6 s into the scroll)
#   tools/fog-lab.sh --clip F.mp4    a clip, headless: 12 s at 30 fps, stepped at game speed (window.fogLabAct)
#   tools/fog-lab.sh --sets DIR      one still per parallax set (r208: s01, round1's calm, PurpleFairy, OneNight), and the
#                                    last with the mist's size at 2.5x, headless: DIR/set0.png ...
#   tools/fog-lab.sh --slider K=V   before --shot/--clip/--sets: start control K (its data-key) at V, a choice at its option's index
#   tools/fog-lab.sh --copy F.png    checks "Copy settings", headless: moves C's slider and the mist to the front,
#                                    clicks, fails unless the JSON names every slider and the two moved; F is the page
# Builds with ./gradlew :core:browserTestWar first (--no-build skips it). Needs Chrome, python3 and, for --shot and
# --clip, Python Playwright and ffmpeg.
# on-screen: with no argument this is the fog lab, a window Simon asked for; --shot and --clip are headless Chrome.
set -u
cd "$(dirname "$0")/.." || exit 1
MODE=open OUT="" BUILD=1 SLIDERS=""
while [ $# -gt 0 ]; do
	case "$1" in
		--shot) MODE=shot; OUT="$(realpath -m "$2")"; shift ;;
		--clip) MODE=clip; OUT="$(realpath -m "$2")"; shift ;;
		--copy) MODE=copy; OUT="$(realpath -m "$2")"; shift ;;
		--sets) MODE=sets; OUT="$(realpath -m "$2")"; shift ;;
		--slider) SLIDERS="$SLIDERS $2"; shift ;;
		--no-build) BUILD=0 ;;
		*) echo "unknown argument: $1" >&2; exit 2 ;;
	esac
	shift
done
if [ $MODE = open ]; then . tools/agent-window-guard.sh; refuse_agent_window tools/fog-lab.sh; fi

CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
[ -n "$CHROME" ] || { echo "no Chrome found: set CHROME=" >&2; exit 2; }
WAR="$PWD/core/build/browserTest/war"
if [ $BUILD = 1 ]; then
	./gradlew -q :core:browserTestWar >/dev/null || exit 1
fi
[ -f "$WAR/fog-lab.html" ] || { echo "$WAR has no fog-lab.html: run without --no-build" >&2; exit 2; }

PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$WAR" >/dev/null 2>&1 &
SERVER=$!
PROFILE=$(mktemp -d)
trap 'kill $SERVER 2>/dev/null; rm -rf "$PROFILE"' EXIT
URL="http://127.0.0.1:$PORT/fog-lab.html"
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

# Headless: Playwright drives the system Chrome on SwiftShader, steps the lab through window.fogLabAct and screenshots
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
    page.on("console", lambda m: m.type == "error" and errors.append(m.text))
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.goto(url)
    try:
        page.wait_for_function("window.fogLabReady === true", timeout=60000)
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
        page.evaluate("window.fogLabAct(6)")
        shot(frames + "/still.png")
    elif mode == "sets":
        # Every set the slider picks, one still each, then the last one with the mist grown to cover the frame.
        names = []
        for i in range(int(page.eval_on_selector("[data-key=parallaxSet]", "e => e.options.length"))):
            page.evaluate("window.fogLabPick(%d)" % i)
            page.evaluate("window.fogLabAct(6)")
            names.append(page.text_content("#fog-readout"))
            shot(frames + "/set%d.png" % i)
        page.eval_on_selector("[data-key=mistSize]", "e => { e.value = '2.5'; }")
        page.evaluate("window.fogLabAct(1)")
        shot(frames + "/set%d.png" % (i + 1))
        print("\n".join(names))
    elif mode == "copy":
        page.context.grant_permissions(["clipboard-read", "clipboard-write"])
        page.eval_on_selector("[data-key=patchHeightC]", "e => { e.value = '0.6'; }")
        page.eval_on_selector("[data-key=mistLayer]", "e => { e.value = '7'; }")
        page.evaluate("window.fogLabAct(6)")
        page.click("#fog-copy")
        page.wait_for_function("document.getElementById('fog-copy-state').textContent !== ''")
        import json
        got = json.loads(page.input_value("#fog-copy-json"))
        keys = {"mistAmplitude", "mistWavelength", "mistSpeed", "patchHeightC", "cameraScroll", "treesWaveRipple", "mistLayer", "fogStrength",
                "fogColor", "parallaxSet", "mistSize", "mistRise"}
        moved = {"patchHeightC": {"started": 0.45, "now": 0.6}, "mistLayer": {"started": 5, "now": 7}}
        if set(got["settings"]) != keys or got["changed"] != moved:
            sys.exit("Copy settings gave the wrong JSON: %s" % json.dumps(got))
        print("Copy settings: %s; %s" % (page.text_content("#fog-copy-state"), json.dumps(got["settings"])))
        shot(frames + "/still.png")
    else:
        for i in range(360):
            page.evaluate("window.fogLabAct(%r)" % (0 if i == 0 else 1 / 30))
            shot(frames + "/f%04d.png" % i)
    if errors:
        print("page errors: %s" % errors, file=sys.stderr)
    browser.close()
PY
if [ $MODE = sets ]; then
	mkdir -p "$OUT" && cp "$FRAMES"/set*.png "$OUT"/ && echo "stills: $OUT/set*.png (the last: the mist at 2.5x)"
elif [ $MODE = shot ] || [ $MODE = copy ]; then
	cp "$FRAMES/still.png" "$OUT" && echo "still: $OUT"
else
	ffmpeg -loglevel error -y -framerate 30 -i "$FRAMES/f%04d.png" -vf "pad=ceil(iw/2)*2:ceil(ih/2)*2" \
		-c:v libx264 -pix_fmt yuv420p -crf 20 "$OUT" && echo "clip: $OUT"
fi
