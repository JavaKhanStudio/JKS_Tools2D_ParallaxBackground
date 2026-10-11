#!/usr/bin/env bash
# The tower lab turning (r328), headless: TowerLab stepped through window.effectsLabAct, the canvas saved every
# STEP seconds for one turn (12.6 s at the lab's start values), as a GIF.
#   tools/r328-tower-lab/clip.sh OUT.gif [--no-build]
# headless: Playwright drives the system Chrome headless on SwiftShader, no window.
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT="$(realpath -m "${1:?usage: clip.sh OUT.gif [--no-build]}")"
[ "${2:-}" = --no-build ] || ./gradlew -q :core:browserTestWar >/dev/null || exit 1
CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
WAR="$PWD/core/build/browserTest/war"
PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$WAR" >/dev/null 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT
URL="http://127.0.0.1:$PORT/effects-lab.html?lab=tower&clip=1"
for _ in $(seq 50); do python3 -c "import urllib.request; urllib.request.urlopen('$URL')" 2>/dev/null && break; sleep 0.1; done
python3 - "$URL" "$CHROME" "$OUT" <<'PY'
import io
import sys

from PIL import Image
from playwright.sync_api import sync_playwright

url, chrome, out = sys.argv[1:]
STEP, SECONDS = 0.25, 12.75
frames = []
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=chrome, args=["--use-angle=swiftshader", "--enable-unsafe-swiftshader"])
    page = browser.new_page(viewport={"width": 1540, "height": 900})
    page.goto(url)
    page.wait_for_function("window.effectsLabReady === true", timeout=60000)
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
