#!/usr/bin/env bash
# The shadow lab (r425): r365's AI forest page drawn twice by the real reader compiled with GWT, in WebGL: A under the
# hour's light (docs/daylight.md: the measured day, the sun and the moon) with cast shadows, B as painted
# (core/browser-test/launcher/.../ShadowLab.java).
# Controls: the hour, the sun's noon height, the moon's age, the day's length (and the 24 hours button), the sun's side
# and height (set by the hour, or the sun dragged in the sky), the light (darkest night, dusk curve, full light from,
# warmth, night blue), the camera's scroll, the cast shadows (darkness, depth, softness, how much a shadow moves with
# the layer it falls on), the rays past the ridges, the sun behind you (its shadows laid on the ground in perspective,
# and the relief: the flanks turned to it lit).
#   tools/shadow-lab.sh                 the lab: a Chrome window, served until it closes
#   tools/shadow-lab.sh --shot F.png    one still, headless (6 s in)
#   tools/shadow-lab.sh --views DIR     headless stills: DIR/page.png, DIR/alone.png (without the pines), DIR/night.png (20:00, the full moon up)
#   tools/shadow-lab.sh --clip F.mp4    a clip, headless: 8 s at 30 fps, stepped at game speed (window.shadowLabAct)
#   tools/shadow-lab.sh --sunset F.mp4  as --clip, the sun sinking from its height to 4 degrees under the horizon
#   tools/shadow-lab.sh --day F.mp4     as --clip, the lab's "play 24 hours" button pressed, the day 8 s long
#                                       (DAY_SECONDS=S: S seconds, the clip as long)
#   tools/shadow-lab.sh --slider K=V    before --shot/--views/--clip: start control K (its data-key) at V, a choice at its option's index
#   tools/shadow-lab.sh --behind        before --shot/--views/--clip: the "sun behind you" button pressed
#   tools/shadow-lab.sh --copy F.png    checks "Copy settings" and the drag, headless: drags the sun in A's sky (fails if
#                                       the panel zooms instead), clicks, fails unless the JSON names every control and
#                                       those moved; then the 24 hours, the phone tap, the sun behind you in Copy, the
#                                       light (its brightness by the hour, not even; on A; blue at night, warm in the
#                                       golden hour), the moon's phase, the shadows by the direct share, the sun behind
#                                       you's shadows slanting away from it, the rays (none round a disc the peaks hide, a
#                                       glare round a clear one), the sun behind the far peaks and the relief lighting
#                                       the sun's side (tools/r425-shadow-light/mutate.sh); F is the page
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

# Headless: Playwright drives the system Chrome on the GPU (tools/browser.py), steps the lab through window.shadowLabAct and screenshots
# it, one frame per 1/30 s of game time, so the clip plays at game speed.
FRAMES=$(mktemp -d)
trap 'kill $SERVER 2>/dev/null; rm -rf "$PROFILE" "$FRAMES"' EXIT
python3 - "$URL?clip=1" "$CHROME" "$FRAMES" "$MODE" "$SLIDERS" <<'PY' || exit 1
import os
import sys
from playwright.sync_api import sync_playwright
sys.path.insert(0, "tools")
from browser import launch
url, chrome, frames, mode, sliders = sys.argv[1:]
with sync_playwright() as p:
    browser = launch(p, chrome)
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
        page.eval_on_selector("[data-key=hour]", "e => { e.value = '20'; }")
        page.evaluate("window.shadowLabAct(0.5)")
        shot(frames + "/night.png")
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
        page.evaluate("window.shadowLabAct(6)")
        page.click("#shadow-copy")
        page.wait_for_function("document.getElementById('shadow-copy-state').textContent !== ''")
        import json
        got = json.loads(page.input_value("#shadow-copy-json"))
        keys = {"view", "hour", "noonHeight", "moonAge", "dayLength", "sun1Side", "sun1Height", "nightDark", "curve", "fullFrom",
                "warmth", "nightBlue", "cameraScroll", "darkness", "depth", "softness", "withReceiver", "rays", "rayLength",
                "relief", "reliefWidth"}
        changed = got["changed"]
        if (set(got["settings"]) != keys or set(changed) != {"sun1Side", "sun1Height"}
                or changed["sun1Side"]["started"] != -35 or not changed["sun1Side"]["now"] > -35
                or changed["sun1Height"]["started"] != 22.5 or not changed["sun1Height"]["now"] < 22.5
                or "moon" not in got["readout"]):
            sys.exit("Copy settings gave the wrong JSON: %s" % json.dumps(got))
        print("Copy settings: %s; %s" % (page.text_content("#shadow-copy-state"), json.dumps(got["settings"])))
        # The day's button: 24 hours from 8 in a 40 s day, the sun back on its path. A quarter in (10 s, 14:00) it is
        # 20 degrees right, 39 up (45 sin 120); at the end, 8:00 again, the moon a day older, the button back to play.
        page.click("#shadow-day")
        page.evaluate("window.shadowLabAct(10)")
        def reading():
            return tuple(float(page.input_value("[data-key=%s]" % k)) for k in ("hour", "sun1Side", "sun1Height", "moonAge"))
        two = reading()
        page.evaluate("window.shadowLabAct(31)")
        end = reading()
        if (abs(two[0] - 14) > 0.1 or abs(two[1] - 20) > 1 or abs(two[2] - 39) > 0.5 or abs(end[0] - 8) > 0.1
                or abs(end[3] - 15.8) > 0.15 or "play" not in page.text_content("#shadow-day")):
            sys.exit("the day: at 10 s %s (want hour 14, side 20, height 39), at its end %s (want hour 8, moon 15.8), button %r"
                     % (two, end, page.text_content("#shadow-day")))
        print("the day: at 10 s hour, side, height, moon %s; at its end %s" % (two, end))
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
        def put(**values):
            for key, value in values.items():
                page.eval_on_selector("[data-key=%s]" % key, "(e, v) => { e.value = v; }", str(value))
            page.evaluate("window.shadowLabAct(0.5)")
        def readout_number(after):
            text = page.text_content("#shadow-readout")
            return float(text.split(after)[0].rsplit(" ", 1)[1])
        def at(height):
            page.eval_on_selector("[data-key=sun1Height]", "(e, v) => { e.value = v; }", str(height))
            page.evaluate("window.shadowLabAct(0.5)")
        # The check before left the sun behind you: back in the sky.
        page.click("#shadow-behind")
        # The light (Simon at 11:08: "higher when there is no sun, way lower when the sun is at the zenith, can climb back
        # up while it's going down. It should not do so in a linear fashion"). The camera still, the sun on its path.
        page.eval_on_selector("[data-key=cameraScroll]", "e => { e.value = '0'; }")
        def bright(hour, age=14.8):
            put(hour=hour, moonAge=age)
            return readout_number("% bright")
        # Sun heights 0, 5, 10, 20 (45 up at noon): 6:00, 6:25, 6:51, 7:47.
        rise = [bright(h) for h in (6, 6 + 25 / 60, 6 + 51 / 60, 7 + 47 / 60)]
        noon, dusk, night_full, night_new = bright(12), bright(18 + 32 / 60), bright(0), bright(0, 0)
        steps = [rise[i + 1] - rise[i] for i in range(3)]
        print("brightness %%: the sun 0, 5, 10, 20 up %s; noon %g; 6 under %g; midnight, full moon %g, new moon %g" % (rise, noon, dusk, night_full, night_new))
        if not (noon == 100 and noon >= rise[3] > rise[2] > rise[1] > rise[0] > dusk > night_full > night_new and night_new <= 15):
            sys.exit("the light: brightness out of order (noon 100, the sun rising, dusk, a full moon's night, a new moon's at most 15)")
        if not (steps[0] > 1.5 * steps[2]):
            sys.exit("the light: as even as the sun's height (steps %s): Simon asked for no linear fashion" % steps)
        # In pixels: A as bright as B at noon (its shadows under the view), far darker at a moonless midnight.
        def mean_ab():
            a, b = panels()
            return float(a.mean()), float(b.mean())
        put(hour=12, moonAge=14.8)
        at_noon = mean_ab()
        put(hour=0, moonAge=0)
        at_night = mean_ab()
        print("A against B, mean of 255: noon %.1f / %.1f, a moonless midnight %.1f / %.1f" % (at_noon + at_night))
        if not (abs(at_noon[0] - at_noon[1]) < 6 and at_night[0] < 0.4 * at_night[1]):
            sys.exit("the light is not on A: noon %.1f / %.1f, midnight %.1f / %.1f" % (at_noon + at_night))
        # Its colour: at a moonless midnight A's blue over red is above B's (night blue); with the sun behind you 3 degrees
        # up (the golden hour on the faces turned to it), below.
        def blue_over_red():
            a, b = panels()
            return float(a[..., 2].mean() / max(1, a[..., 0].mean())), float(b[..., 2].mean() / max(1, b[..., 0].mean()))
        night_blue = blue_over_red()
        # Night eyes (Purkinje): 'night blue' moves A at a moonless midnight, not at noon.
        def night_eyes(hour):
            put(hour=hour, moonAge=0, nightBlue=0.8)
            a8 = panels()[0]
            put(nightBlue=0)
            a0 = panels()[0]
            put(nightBlue=0.8)
            return float(np.abs(a8 - a0).mean())
        import numpy as np
        eyes_night, eyes_noon = night_eyes(0), night_eyes(12)
        print("night blue 0.8 against 0, A: %.2f/255 at a moonless midnight, %.2f at noon" % (eyes_night, eyes_noon))
        if not (eyes_night > 0.5 and eyes_noon < 0.05):
            sys.exit("night blue is not on A at night: %.2f at midnight, %.2f at noon (want over 0.5, under 0.05)" % (eyes_night, eyes_noon))
        put(hour=0, moonAge=0)
        page.click("#shadow-behind")
        put(hour=6 + 15 / 60, moonAge=14.8, darkness=0, relief=0, rays=0)
        golden = blue_over_red()
        page.click("#shadow-behind")
        print("blue / red, A against B: a moonless night %.2f / %.2f; the golden hour, the sun behind you %.2f / %.2f" % (night_blue + golden))
        if not (night_blue[0] > 1.15 * night_blue[1]):
            sys.exit("the light: the night is not blue")
        if not (golden[0] < 0.92 * golden[1]):
            sys.exit("the light: the golden hour is not warm")
        # The moon's phase: put where the sun is at 8 (35 left, 22.5 up) by an hour 24 age / 29.53 after 8, at night for
        # these ages. Full (20:00), B round it is bright on both halves; at its last quarter (1:58), lit on the left (the
        # sun, behind it on the path, is to its left), dark on the right.
        def moon_halves(age):
            import math
            put(hour=(8 + 24 * age / 29.53) % 24, moonAge=age)
            b = panels()[1]
            a = math.radians(-34.64)
            x = 20 + 20 * math.tan(a)
            y = 8.75 + 20 * math.tan(math.radians(22.5)) / math.cos(a)
            col, row = round(x / 40 * b.shape[1]), round((1 - y / 22.5) * (b.shape[0] + 40)) - 40
            r = round(0.5 / 40 * b.shape[1])
            return float(b[row - r:row + r, col - r:col].mean()), float(b[row - r:row + r, col:col + r].mean())
        full, last = moon_halves(14.77), moon_halves(22.15)
        print("the moon, B round it, left / right half: full %.1f / %.1f, last quarter %.1f / %.1f" % (full + last))
        if not (min(full) > 150 and last[0] > last[1] + 30):
            sys.exit("the moon's phase: full %s, last quarter %s (want both halves of the full over 150, the quarter's left over its right by 30: B's painted day sky shows through its dark part)" % (full, last))
        # Cast shadows by the direct share: darkness 1 against 0 moves A at 20 degrees up, all but nothing at half a degree
        # (the sky's light, all of it, then).
        def shadows_at(hour):
            put(hour=hour, moonAge=14.8, rays=0, darkness=1)
            a1 = panels()[0]
            put(darkness=0)
            a0 = panels()[0]
            return float(np.abs(a1 - a0).mean())
        import numpy as np
        high, low = shadows_at(7 + 47 / 60), shadows_at(6 + 2.5 / 60)
        print("cast shadows, darkness 1 against 0: %.2f/255 at 20 degrees, %.2f at half a degree" % (high, low))
        if not (high > 1 and low < 0.25 * high):
            sys.exit("the shadows by the direct share: %.2f at 20 degrees, %.2f at half a degree (want over 1, under a quarter)" % (high, low))
        # The checks below judge the shadows and the relief alone: white light, full from 2 degrees up.
        put(warmth=0, fullFrom=2, nightBlue=0, hour=8, rays=0.8)
        def slant(side):
            # The sun behind you, 10 degrees up: where A is darker than B, do the streaks climb to the right ("/", over 0)
            # or to the left? The structure tensor's cross term over the darkening, normalised: -1 to 1.
            import numpy as np
            page.eval_on_selector("[data-key=sun1Side]", "(e, v) => { e.value = v; }", str(side))
            at(10)
            a, b = panels()
            d = np.clip(b - a, 0, None).mean(axis=2)
            if os.environ.get("SHADOW_DEBUG"):
                from PIL import Image
                Image.fromarray(np.concatenate([a, b], axis=1).astype("uint8")).save(os.environ["SHADOW_DEBUG"] + "/slant%d.png" % side)
            gy, gx = np.gradient(d)
            return float((gx * gy).sum() / max(1e-9, np.sqrt((gx * gx).sum() * (gy * gy).sum()))), float(d.mean())
        page.click("#shadow-behind")
        for key, value in (("darkness", 1), ("relief", 0)):
            page.eval_on_selector("[data-key=%s]" % key, "(e, v) => { e.value = v; }", str(value))
        left, right = slant(-40), slant(40)
        print("the sun behind you, 10 up: the shadows' slant %+.2f with the sun on the left, %+.2f on the right (A darker by %.2f, %.2f)" % (left[0], right[0], left[1], right[1]))
        if not (left[0] > 0.1 and right[0] < -0.1):
            sys.exit("the sun behind you: its shadows do not run away from its side")
        # The rays: the sun in the sky, deep behind the peaks, against clear of them: A with rays against without.
        page.click("#shadow-behind")
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
        hidden, clear = rays_at(-6), rays_at(18)
        print("rays: round the disc, A brighter by %.2f/255 with the sun deep behind the peaks, %.2f clear of them" % (hidden, clear))
        if not (clear > 20 and hidden < 0.2 * clear):
            sys.exit("the rays: %.2f hidden, %.2f clear round the disc (want clear over 20, hidden under a fifth of it)" % (hidden, clear))
        # The sun behind every layer (Simon at 23:35: "you did not show the sun behind the last layer but the one before
        # that"): 8 degrees up, the disc is under every valley of the far peaks, so wherever it is across the view, B
        # round it is as with the sun out of the picture (60 up). Drawn over the far peaks, it shows where the range is
        # low, by tens of /255.
        def b_round(side, height):
            import math
            page.eval_on_selector("[data-key=sun1Side]", "(e, v) => { e.value = v; }", str(side))
            at(height)
            b = panels()[1]
            a = math.radians(side)
            x = 20 + 20 * math.tan(a)
            y = 8.75 + 20 * math.tan(math.radians(8)) / math.cos(a)
            col, row = round(x / 40 * b.shape[1]), round((1 - y / 22.5) * (b.shape[0] + 40)) - 40
            r = round(0.7 / 40 * b.shape[1])
            return b[row - r:row + r, col - r:col + r]
        import numpy as np
        shows = max((float(np.abs(b_round(side, 8) - b_round(side, 60)).mean()), side) for side in range(-30, 31, 3))
        print("the sun behind the far peaks, 8 up: B round it off the empty sky by at most %.2f/255 (at %d degrees)" % shows)
        if shows[0] > 2:
            sys.exit("the sun behind the far peaks: it shows over them by %.2f/255 at %d degrees (want under 2)" % shows)
        # The relief (Simon at 23:35: "Sun behind is still very bad"): the sun behind you, 10 degrees up, no cast
        # shadow (darkness 0), so A off B is the relief alone. It must light the flanks on the sun's side: where B
        # darkens to the right (a darker layer's left edge, gx under 0) A is lighter, and the other way round, with the
        # sun on the left; the opposite with it on the right.
        page.click("#shadow-behind")
        for key, value in (("darkness", 0), ("relief", 0.6), ("reliefWidth", 2.5)):
            page.eval_on_selector("[data-key=%s]" % key, "(e, v) => { e.value = v; }", str(value))
        def relief(side):
            page.eval_on_selector("[data-key=sun1Side]", "(e, v) => { e.value = v; }", str(side))
            at(10)
            a, b = panels()
            d = (a - b).mean(axis=2)
            lum = b.mean(axis=2)
            # B's brightness over 9 px, so a flank's band in A meets the edge it leans from.
            k = np.ones(9) / 9
            lum = np.apply_along_axis(lambda r: np.convolve(r, k, "same"), 1, np.apply_along_axis(lambda c: np.convolve(c, k, "same"), 0, lum))
            gx = np.gradient(lum, axis=1)
            return float(np.abs(d).mean()), float((d * gx).sum() / max(1e-9, (np.abs(d) * np.abs(gx)).sum()))
        left, right = relief(-40), relief(40)
        print("the relief, the sun behind you 10 up: A off B by %.2f/255, its lean %+.2f with the sun on the left; %.2f, %+.2f on the right" % (left[0], left[1], right[0], right[1]))
        if not (left[0] > 1 and right[0] > 1):
            sys.exit("the relief: A off B by %.2f and %.2f (want over 1)" % (left[0], right[0]))
        if not (left[1] < -0.1 and right[1] > 0.1):
            sys.exit("the relief: it lights the flanks away from the sun (lean %+.2f left, %+.2f right)" % (left[1], right[1]))
    else:
        start = float(page.input_value("[data-key=sun1Height]"))
        frames_count = 240
        if mode == "day":
            length = float(os.environ.get("DAY_SECONDS", "8"))
            frames_count = round(30 * length)
            page.eval_on_selector("[data-key=dayLength]", "(e, v) => { e.value = v; }", str(length))
            page.click("#shadow-day")
        for i in range(frames_count):
            if mode == "sunset":
                page.eval_on_selector("[data-key=sun1Height]", "(e, v) => { e.value = v; }", str(start - (start + 4) * i / (frames_count - 1)))
            page.evaluate("window.shadowLabAct(%r)" % (0 if i == 0 else 1 / 30))
            shot(frames + "/f%04d.png" % i)
    if errors:
        print("page errors: %s" % errors, file=sys.stderr)
    browser.close()
PY
if [ $MODE = views ]; then
	mkdir -p "$OUT" && cp "$FRAMES"/page.png "$FRAMES"/alone.png "$FRAMES"/night.png "$OUT"/ && echo "stills: $OUT/{page,alone,night}.png"
elif [ $MODE = shot ] || [ $MODE = copy ]; then
	cp "$FRAMES/still.png" "$OUT" && echo "still: $OUT"
else
	ffmpeg -loglevel error -y -framerate 30 -i "$FRAMES/f%04d.png" -vf "pad=ceil(iw/2)*2:ceil(ih/2)*2" \
		-c:v libx264 -pix_fmt yuv420p -crf 20 "$OUT" && echo "clip: $OUT"
fi
