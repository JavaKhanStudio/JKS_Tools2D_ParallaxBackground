#!/usr/bin/env bash
# The shadow lab (r425): r365's AI forest page drawn twice by the real reader compiled with GWT, in WebGL, under a sun
# in its sky: A with cast shadows (each layer casting toward you, on the layer just in front of it), B as painted
# (core/browser-test/launcher/.../ShadowLab.java).
# Controls: the view (the page, or without the pines), the camera's scroll, each sun's side and height (sliders, or the
# sun dragged in the sky), the second sun on or off, the depth between two layers, how much a shadow moves with the
# layer it falls on, its softness and darkness, the rays past the ridges (strength, length), the sun behind you (its
# shadows laid on the ground in perspective).
#   tools/shadow-lab.sh                 the lab: a Chrome window, served until it closes
#   tools/shadow-lab.sh --shot F.png    one still, headless (6 s in)
#   tools/shadow-lab.sh --views DIR     headless stills: DIR/page.png, DIR/alone.png (without the pines), DIR/two.png (both suns)
#   tools/shadow-lab.sh --clip F.mp4    a clip, headless: 8 s at 30 fps, stepped at game speed (window.shadowLabAct)
#   tools/shadow-lab.sh --sunset F.mp4  as --clip, sun 1 sinking from its height to 4 degrees under the horizon
#   tools/shadow-lab.sh --day F.mp4     as --clip, the lab's "play a whole day" button pressed, the day 8 s long
#   tools/shadow-lab.sh --slider K=V    before --shot/--views/--clip: start control K (its data-key) at V, a choice at its option's index
#   tools/shadow-lab.sh --behind        before --shot/--views/--clip: the "sun behind you" button pressed
#   tools/shadow-lab.sh --copy F.png    checks "Copy settings" and the drag, headless: drags sun 1 in A's sky (fails if the
#                                       panel zooms instead), turns sun 2 on, clicks, fails unless the JSON names every
#                                       control and those moved; then the day, the phone tap, the lift, the sun behind
#                                       you (its shadows slant away from its side) and the rays (none round a disc the
#                                       range hides, a glare round a clear one: tools/r425-shadow-light/mutate.sh); F is the page
# SHADOW_DEBUG=DIR: --copy saves the crops its slant and rays checks measure in DIR.
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
		--sunset) MODE=sunset; OUT="$(realpath -m "$2")"; shift ;;
		--day) MODE=day; OUT="$(realpath -m "$2")"; shift ;;
		--copy) MODE=copy; OUT="$(realpath -m "$2")"; shift ;;
		--views) MODE=views; OUT="$(realpath -m "$2")"; shift ;;
		--slider) SLIDERS="$SLIDERS $2"; shift ;;
		--behind) SLIDERS="$SLIDERS behind" ;;
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
import os
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
        if kv == "behind":
            page.click("#shadow-behind")
            continue
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
        # Sun 1 dragged in the sky, from where ShadowLab.drawSun puts it: it moves, and no panel zooms or unzooms.
        page.evaluate("window.shadowLabAct(1)")
        page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
        def drag(dx, dy):
            at = page.evaluate("window.shadowSunAt(1)")
            page.mouse.move(at["x"], at["y"])
            page.mouse.down()
            for i in range(1, 11):
                page.mouse.move(at["x"] + dx * i / 10, at["y"] + dy * i / 10)
            page.mouse.up()
            page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
        # Zoomed on B first: the drag moves the sun there and B stays zoomed; then back to all, the drag in A.
        page.evaluate("window.labZoom(1)")
        page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
        before = page.input_value("[data-key=sun1Side]")
        drag(80, 0)
        if page.evaluate("window.labFocus") != 1 or page.input_value("[data-key=sun1Side]") == before:
            sys.exit("dragging the sun in zoomed B: focus %s, side %s -> %s" % (page.evaluate("window.labFocus"), before,
                                                                                  page.input_value("[data-key=sun1Side]")))
        page.evaluate("window.labZoom(-1)")
        page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
        drag(200, 60)
        if page.evaluate("window.labFocus") != -1:
            sys.exit("dragging the sun zoomed panel %s" % page.evaluate("window.labFocus"))
        page.eval_on_selector("[data-key=sun2On]", "e => { e.value = '1'; }")
        page.evaluate("window.shadowLabAct(6)")
        page.click("#shadow-copy")
        page.wait_for_function("document.getElementById('shadow-copy-state').textContent !== ''")
        import json
        got = json.loads(page.input_value("#shadow-copy-json"))
        keys = {"view", "cameraScroll", "sun1Side", "sun1Height", "sun2On", "sun2Side", "sun2Height", "depth", "withReceiver",
                "softness", "darkness", "liftFrom", "liftHigh", "rays", "rayLength", "dayLength"}
        changed = got["changed"]
        if (set(got["settings"]) != keys or set(changed) != {"sun1Side", "sun1Height", "sun2On"}
                or changed["sun1Side"]["started"] != -40 or not changed["sun1Side"]["now"] > -40
                or changed["sun1Height"]["started"] != 22.5 or not changed["sun1Height"]["now"] < 22.5
                or changed["sun2On"] != {"started": 0, "now": 1} or "sun 2:" not in got["readout"]):
            sys.exit("Copy settings gave the wrong JSON: %s" % json.dumps(got))
        print("Copy settings: %s; %s" % (page.text_content("#shadow-copy-state"), json.dumps(got["settings"])))
        # The day's button: at half a 20 s day, sun 1 is over the view's middle at its highest; at its end, set at the right.
        page.click("#shadow-day")
        page.evaluate("window.shadowLabAct(10)")
        noon = (float(page.input_value("[data-key=sun1Side]")), float(page.input_value("[data-key=sun1Height]")))
        page.evaluate("window.shadowLabAct(11)")
        dusk = (float(page.input_value("[data-key=sun1Side]")), float(page.input_value("[data-key=sun1Height]")))
        if abs(noon[0]) > 1 or noon[1] < 29 or dusk != (40, -6) or "play" not in page.text_content("#shadow-day"):
            sys.exit("the day: noon %s (want 0, 30), dusk %s (want 40, -6), button %r" % (noon, dusk, page.text_content("#shadow-day")))
        print("the day: noon %s, dusk %s" % (noon, dusk))
        shot(frames + "/still.png")
        # On a phone: a tap on the sun zooms nothing (lab.js zooms on a tap the canvas cancelled), a tap beside it does.
        phone = browser.new_page(viewport={"width": 390, "height": 844}, is_mobile=True, has_touch=True)
        phone.goto(url)
        phone.wait_for_function("window.shadowLabReady === true", timeout=60000)
        phone.evaluate("window.shadowLabAct(1)")
        phone.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
        at = phone.evaluate("window.shadowSunAt(1)")
        phone.touchscreen.tap(at["x"], at["y"])
        phone.wait_for_timeout(300)
        on_sun = phone.evaluate("window.labFocus")
        phone.touchscreen.tap(at["x"], at["y"] + 60)
        phone.wait_for_timeout(300)
        beside = phone.evaluate("window.labFocus")
        if on_sun != -1 or beside != 0:
            sys.exit("a phone's tap: on the sun zoomed %s (want -1), beside it %s (want 0)" % (on_sun, beside))
        print("phone: a tap on the sun zooms nothing, beside it zooms A")
        # The sun behind you: Copy settings says so, and a drag where the sun was moves nothing.
        page.eval_on_selector("[data-key=sun1Height]", "e => { e.value = '20'; }")
        page.evaluate("window.shadowLabAct(0.5)")
        at = page.evaluate("window.shadowSunAt(1)")
        page.click("#shadow-behind")
        page.evaluate("window.shadowLabAct(0.5)")
        side = page.input_value("[data-key=sun1Side]")
        page.mouse.move(at["x"], at["y"]); page.mouse.down(); page.mouse.move(at["x"] - 80, at["y"] - 30); page.mouse.up()
        page.evaluate("window.labZoom(-1)")
        page.click("#shadow-copy")
        got = json.loads(page.input_value("#shadow-copy-json"))
        if got.get("sun") != "behind you" or page.input_value("[data-key=sun1Side]") != side or "behind you" not in got["readout"]:
            sys.exit("the sun behind you: sun %r, side %s -> %s, readout %r" % (got.get("sun"), side, page.input_value("[data-key=sun1Side]"), got["readout"]))
        print("sun behind you: Copy says so, the drag moves nothing")
        # A high sun lifts the shade: full under liftFrom (15), none from liftHigh (40) up, and A then draws as B.
        def dark_at(height):
            page.eval_on_selector("[data-key=sun1Height]", "(e, v) => { e.value = v; }", str(height))
            page.evaluate("window.shadowLabAct(0.5)")
            text = page.text_content("#shadow-readout")
            return int(text.split("% dark")[0].rsplit(" ", 1)[1])
        def a_off_b():
            # Panel A's mean difference from B (out of 255), under their titles, in the canvas's own pixels.
            import io
            import numpy as np
            from PIL import Image
            page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
            c = page.locator("#embed-browsertests canvas")
            img = np.asarray(Image.open(io.BytesIO(c.screenshot())).convert("RGB"), dtype=float)
            width, rects = page.evaluate("[document.querySelector('#embed-browsertests canvas').width, window.labPanelRects]")
            k = img.shape[1] / width
            def cut(r):
                x, y, w, h = (round(v * k) for v in r)
                top = img.shape[0] - y - h
                return img[top + 40:top + h, x:x + w]
            a, b = cut(rects[0]), cut(rects[1])
            n = min(a.shape[1], b.shape[1])
            return float(np.abs(a[:, :n] - b[:, :n]).mean())
        lifted = (dark_at(10), dark_at(27.5), dark_at(45))
        # Back in the sky, one sun at 20 degrees, at full darkness: A draws its shadows while the shade lifts from 30, and
        # as B once the sun is high from 20 (a higher sun's shadows land under the view: they would prove nothing). No
        # rays: they are A's alone, whatever the shade.
        page.click("#shadow-behind")
        for key, value in (("sun2On", 0), ("darkness", 1), ("liftFrom", 5), ("liftHigh", 30), ("rays", 0)):
            page.eval_on_selector("[data-key=%s]" % key, "(e, v) => { e.value = v; }", str(value))
        shaded = (dark_at(20), a_off_b())
        page.eval_on_selector("[data-key=liftHigh]", "e => { e.value = '20'; }")
        gone = (dark_at(20), a_off_b())
        if not (shaded[1] > 0.5 and gone[1] < 0.2):
            sys.exit("the high sun's lift: A off B by %.2f lifting to 30 degrees, %.2f high from 20 (want over 0.5, under 0.2)" % (shaded[1], gone[1]))
        print("A off B at 20 degrees: %.2f/255 (%d%% dark), %.2f once high (%d%%)" % (shaded[1], shaded[0], gone[1], gone[0]))
        if lifted != (100, 50, 0):
            sys.exit("the high sun's lift: %s%% dark at 10, 27.5 and 45 degrees (want 100, 50, 0)" % (lifted,))
        print("a high sun lifts the shade: %s%% dark at 10, 27.5, 45 degrees" % (lifted,))
        def panels():
            import io
            import numpy as np
            from PIL import Image
            page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
            c = page.locator("#embed-browsertests canvas")
            img = np.asarray(Image.open(io.BytesIO(c.screenshot())).convert("RGB"), dtype=float)
            width, rects = page.evaluate("[document.querySelector('#embed-browsertests canvas').width, window.labPanelRects]")
            k = img.shape[1] / width
            def cut(r):
                x, y, w, h = (round(v * k) for v in r)
                top = img.shape[0] - y - h
                return img[top + 40:top + h, x:x + w]
            a, b = cut(rects[0]), cut(rects[1])
            n = min(a.shape[1], b.shape[1])
            return a[:, :n], b[:, :n]
        def slant(side):
            # The sun behind you, 10 degrees up: where A is darker than B, do the streaks climb to the right ("/", over 0)
            # or to the left? The structure tensor's cross term over the darkening, normalised: -1 to 1.
            import numpy as np
            page.eval_on_selector("[data-key=sun1Side]", "(e, v) => { e.value = v; }", str(side))
            dark_at(10)
            a, b = panels()
            d = np.clip(b - a, 0, None).mean(axis=2)
            if os.environ.get("SHADOW_DEBUG"):
                from PIL import Image
                Image.fromarray(np.concatenate([a, b], axis=1).astype("uint8")).save(os.environ["SHADOW_DEBUG"] + "/slant%d.png" % side)
            gy, gx = np.gradient(d)
            return float((gx * gy).sum() / max(1e-9, np.sqrt((gx * gx).sum() * (gy * gy).sum()))), float(d.mean())
        # The lift's check left the sun in the sky: behind you again.
        page.click("#shadow-behind")
        for key, value in (("darkness", 1), ("liftFrom", 30), ("liftHigh", 40)):
            page.eval_on_selector("[data-key=%s]" % key, "(e, v) => { e.value = v; }", str(value))
        left, right = slant(-40), slant(40)
        print("the sun behind you, 10 up: the shadows' slant %+.2f with the sun on the left, %+.2f on the right (A darker by %.2f, %.2f)" % (left[0], right[0], left[1], right[1]))
        if not (left[0] > 0.1 and right[0] < -0.1):
            sys.exit("the sun behind you: its shadows do not run away from its side")
        # The rays: the sun in the sky, deep behind the range, against just clear of it: A with rays against without.
        page.click("#shadow-behind")
        def at(height):
            page.eval_on_selector("[data-key=sun1Height]", "(e, v) => { e.value = v; }", str(height))
            page.evaluate("window.shadowLabAct(0.5)")
        def rays_at(height):
            # A with rays against without, round the disc (2 world units about it, in A's 40 x 22.5 view): sun 1 at
            # 10 degrees left, where ShadowLab.drawSun puts it.
            import math
            import numpy as np
            page.eval_on_selector("[data-key=sun1Side]", "e => { e.value = '-10'; }")
            page.eval_on_selector("[data-key=rays]", "e => { e.value = '0.8'; }")
            at(height)
            lit = panels()[0]
            page.eval_on_selector("[data-key=rays]", "e => { e.value = '0'; }")
            at(height)
            plain = panels()[0]
            a = math.radians(-10)
            x = 20 + 20 * math.tan(a)
            y = 8.75 + 20 * math.tan(math.radians(height)) / math.cos(a)
            full = lit.shape[0] + 40
            col, row = round(x / 40 * lit.shape[1]), round((1 - y / 22.5) * full) - 40
            r = round(2 / 40 * lit.shape[1])
            if os.environ.get("SHADOW_DEBUG"):
                from PIL import Image
                d = np.abs(lit - plain) * 4
                d[max(0, row - r):row + r, col - r:col + r] += 60
                Image.fromarray(np.clip(np.concatenate([lit, d], axis=1), 0, 255).astype("uint8")).save(os.environ["SHADOW_DEBUG"] + "/rays%s.png" % height)
            return float(np.abs(lit - plain)[max(0, row - r):row + r, col - r:col + r].mean())
        # The camera still: the two shots are half a second apart.
        page.eval_on_selector("[data-key=cameraScroll]", "e => { e.value = '0'; }")
        hidden, clear = rays_at(-6), rays_at(12)
        print("rays: round the disc, A brighter by %.2f/255 with the sun deep behind the range, %.2f just clear of it" % (hidden, clear))
        if not (clear > 20 and hidden < 0.2 * clear):
            sys.exit("the rays: %.2f hidden, %.2f clear round the disc (want clear over 20, hidden under a fifth of it)" % (hidden, clear))
    else:
        start = float(page.input_value("[data-key=sun1Height]"))
        if mode == "day":
            page.eval_on_selector("[data-key=dayLength]", "e => { e.value = '8'; }")
            page.click("#shadow-day")
        for i in range(240):
            if mode == "sunset":
                page.eval_on_selector("[data-key=sun1Height]", "(e, v) => { e.value = v; }", str(start - (start + 4) * i / 239))
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
