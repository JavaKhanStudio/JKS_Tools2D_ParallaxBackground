#!/usr/bin/env bash
# The effects labs (r224, Simon: "one lab per feature", and one with them all): EffectsLab, compiled with GWT, in WebGL.
# Each draws its page twice by the real reader, the feature on the left, the same page without it on the right.
#   --lab fog    the page fog (r217) alone, on four parallax sets: strength, colour, scroll
#   --lab snow   a PARTICLES layer's snow alone: density, flake size, wind, its layer, its drift, its anchor
#   --lab all    every effect that ships, together on s01: page fog, FOG mist, WAVE trees, a game's towers, snow
# The readout gives the frame time, and each panel's draw calls and CPU time.
#   tools/effects-lab.sh --lab L                 the lab: a Chrome window, served until it closes
#   tools/effects-lab.sh --lab L --shot F.png    one still, headless (6 s into the scroll)
#   tools/effects-lab.sh --lab L --copy F.png    checks "Copy settings", headless: moves the lab's first slider and the
#                                                scroll, clicks, fails unless the JSON names every slider and the two moved
#   tools/effects-lab.sh ... --slider K=V        before --shot/--copy: start control K (its data-key) at V, a choice at its option's index
# Builds with ./gradlew :core:browserTestWar first (--no-build skips it). Needs Chrome, python3 and, headless, Python
# Playwright.
# on-screen: with no --shot/--copy this is the lab, a window Simon asked for (r224); the other modes are headless Chrome.
set -u
cd "$(dirname "$0")/.." || exit 1
MODE=open OUT="" BUILD=1 LAB=fog SLIDERS=""
while [ $# -gt 0 ]; do
	case "$1" in
		--lab) LAB="$2"; shift ;;
		--shot) MODE=shot; OUT="$(realpath -m "$2")"; shift ;;
		--copy) MODE=copy; OUT="$(realpath -m "$2")"; shift ;;
		--slider) SLIDERS="$SLIDERS $2"; shift ;;
		--no-build) BUILD=0 ;;
		*) echo "unknown argument: $1" >&2; exit 2 ;;
	esac
	shift
done
case "$LAB" in fog|snow|all) ;; *) echo "--lab is fog, snow or all, not: $LAB" >&2; exit 2 ;; esac
if [ $MODE = open ]; then . tools/agent-window-guard.sh; refuse_agent_window tools/effects-lab.sh; fi

CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
[ -n "$CHROME" ] || { echo "no Chrome found: set CHROME=" >&2; exit 2; }
WAR="$PWD/core/build/browserTest/war"
if [ $BUILD = 1 ]; then
	./gradlew -q :core:browserTestWar >/dev/null || exit 1
fi
[ -f "$WAR/effects-lab.html" ] || { echo "$WAR has no effects-lab.html: run without --no-build" >&2; exit 2; }

PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$WAR" >/dev/null 2>&1 &
SERVER=$!
PROFILE=$(mktemp -d)
trap 'kill $SERVER 2>/dev/null; rm -rf "$PROFILE"' EXIT
URL="http://127.0.0.1:$PORT/effects-lab.html?lab=$LAB"
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

# Headless: Playwright drives the system Chrome on SwiftShader and steps the lab through window.effectsLabAct.
python3 - "$URL&clip=1" "$CHROME" "$OUT" "$MODE" "$LAB" "$SLIDERS" <<'PY' || exit 1
import sys, json
from playwright.sync_api import sync_playwright
url, chrome, out, mode, lab, sliders = sys.argv[1:]
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=chrome, args=["--use-angle=swiftshader", "--enable-unsafe-swiftshader"])
    page = browser.new_page(viewport={"width": 1540, "height": 900})
    errors = []
    page.on("console", lambda m: m.type == "error" and not m.text.startswith("Failed to load resource") and errors.append(m.text))
    page.on("pageerror", lambda e: errors.append(str(e)))
    # Chrome asks every page for a favicon: the lab has none.
    page.on("response", lambda r: r.status >= 400 and not r.url.endswith("/favicon.ico") and errors.append("%d %s" % (r.status, r.url)))
    page.goto(url)
    try:
        page.wait_for_function("window.effectsLabReady === true", timeout=60000)
    except Exception:
        sys.exit("the lab never started: %s" % errors)
    for pair in sliders.split():
        key, value = pair.split("=", 1)
        if not page.query_selector("[data-key=%s]" % key):
            sys.exit("no slider %s in the %s lab" % (key, lab))
        page.eval_on_selector("[data-key=%s]" % key, "(e, v) => { e.value = v; }", value)
    keys = page.eval_on_selector_all("#effects-controls [data-key]", "es => es.map(e => e.dataset.key)")
    if mode == "copy":
        page.context.grant_permissions(["clipboard-read", "clipboard-write"])
        first = page.eval_on_selector("#effects-controls input[type=range][data-key]", "e => [e.dataset.key, Number(e.dataset.start), Number(e.max)]")
        page.eval_on_selector("[data-key=%s]" % first[0], "(e, v) => { e.value = v; }", str(first[2]))
        page.eval_on_selector("[data-key=cameraScroll]", "e => { e.value = '80'; }")
        page.evaluate("window.effectsLabAct(6)")
        page.click("#effects-copy")
        page.wait_for_function("document.getElementById('effects-copy-state').textContent !== ''")
        got = json.loads(page.input_value("#effects-copy-json"))
        moved = {first[0]: {"started": first[1], "now": first[2]}, "cameraScroll": {"started": 40, "now": 80}}
        if set(got["settings"]) != set(keys) or got["changed"] != moved or got["lab"] != "effects-lab " + lab:
            sys.exit("Copy settings gave the wrong JSON: %s" % json.dumps(got))
        print("Copy settings: %s; %s" % (page.text_content("#effects-copy-state"), json.dumps(got["settings"])))
    else:
        page.evaluate("window.effectsLabAct(6)")
    print("sliders: %s" % " ".join(keys))
    print(page.text_content("#effects-readout"))
    # two animation frames: the step lands in the next render
    page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
    page.screenshot(path=out, full_page=True)
    if errors:
        sys.exit("page errors: %s" % errors)
    browser.close()
PY
echo "still: $OUT"
