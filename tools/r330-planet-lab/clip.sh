#!/usr/bin/env bash
# The planet lab moving (r330), headless: PlanetLab stepped through window.effectsLabAct, the canvas saved every
# STEP seconds for 8 s, as a GIF: the page round its planet, the snow and the game's towers among its layers.
#   tools/r330-planet-lab/clip.sh OUT.gif [K=V ...] [--no-build]   K a control's data-key (planetRadius, towersHook,
#                                                                  snowAnchor, cameraScroll), a choice by its index
# headless: Playwright drives the system Chrome headless on the GPU (tools/browser.py), no window.
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT="$(realpath -m "${1:?usage: clip.sh OUT.gif [K=V ...] [--no-build]}")"; shift
BUILD=1 SETTINGS=""
for a in "$@"; do [ "$a" = --no-build ] && BUILD=0 || SETTINGS="$SETTINGS $a"; done
[ $BUILD = 0 ] || ./gradlew -q :core:browserTestWar >/dev/null || exit 1
CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
WAR="$PWD/core/build/browserTest/war"
PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$WAR" >/dev/null 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT
URL="http://127.0.0.1:$PORT/effects-lab.html?lab=planet&clip=1"
for _ in $(seq 50); do python3 -c "import urllib.request; urllib.request.urlopen('$URL')" 2>/dev/null && break; sleep 0.1; done
python3 - "$URL" "$CHROME" "$OUT" "$SETTINGS" <<'PY'
import io
import sys

from PIL import Image
from playwright.sync_api import sync_playwright
sys.path.insert(0, "tools")
from browser import launch

url, chrome, out, settings = sys.argv[1:]
STEP, SECONDS = 0.25, 8
frames = []
with sync_playwright() as p:
    browser = launch(p, chrome)
    page = browser.new_page(viewport={"width": 1540, "height": 900})
    page.goto(url)
    page.wait_for_function("window.effectsLabReady === true", timeout=60000)
    for setting in settings.split():
        key, value = setting.split("=", 1)
        page.evaluate("([k, v]) => { const e = document.querySelector('[data-key=\"' + k + '\"]');"
                      " if (!e) throw new Error('no control ' + k); e.value = v; }", [key, value])
    canvas = page.query_selector("canvas")
    for i in range(int(SECONDS / STEP) + 1):
        if i:
            page.evaluate("window.effectsLabAct(%s)" % STEP)
        page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
        shot = Image.open(io.BytesIO(canvas.screenshot())).convert("RGB")
        frames.append(shot.resize((shot.width // 2, shot.height // 2)))
    browser.close()
frames[0].save(out, save_all=True, append_images=frames[1:], duration=int(STEP * 1000), loop=0)
print("%d frames, %s s apart: %s" % (len(frames), STEP, out))
PY
