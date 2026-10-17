#!/usr/bin/env bash
# The panel zoom (r452), used as Simon uses it: in each lab of several panels, on a desktop (a click, the arrow keys,
# Escape) and on a 390px phone (a tap, the arrow buttons, the back button), every panel zoomed in turn. Stills of each
# stage, every panel shown and each zoomed, into OUT. Fails when the zoomed panel is not the one clicked (its picture
# closer to another panel's), the stage did not change, its title is not the clicked panel's, a step or the way back
# lands elsewhere, the stage comes back changed, or Copy settings moved.
#   [ZOOM_LABS="anim hills"] tools/r452-lab-zoom/zoom.sh OUT [--no-build]
# headless: Playwright drives the system Chrome headless on SwiftShader, no window.
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT="$(realpath -m "${1:?usage: zoom.sh OUT [--no-build]}")"
[ "${2:-}" = --no-build ] || ./gradlew -q :core:browserTestWar >/dev/null || exit 1
CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
# A copy: another session's build replaces the war under a page loading it, which then never gets ready (r486).
WAR=$(mktemp -d)
cp -r core/build/browserTest/war/. "$WAR"
mkdir -p "$OUT"
PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$WAR" >/dev/null 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null; rm -rf "$WAR"' EXIT
for _ in $(seq 50); do python3 -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:$PORT/index.html')" 2>/dev/null && break; sleep 0.1; done
# headless: Playwright drives the system Chrome headless on SwiftShader, no window.
python3 "$(dirname "$0")/zoom.py" "http://127.0.0.1:$PORT" "$CHROME" "$OUT"
