#!/usr/bin/env bash
# The labs page's tiles (r418): a still of each HTML lab's canvas, 480x270 JPEG, into core/browser-test/webapp/tiles,
# the icons core/browser-test/webapp/index.html shows. Run it again when a lab's picture changes, or for a new lab
# (add it to tiles.py and its tile to index.html).
#   tools/r418-lab-tiles/tiles.sh [--no-build]
# headless: Playwright drives the system Chrome headless on SwiftShader, no window.
set -u
cd "$(dirname "$0")/../.." || exit 1
[ "${1:-}" = --no-build ] || ./gradlew -q :core:browserTestWar >/dev/null || exit 1
CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
WAR="$PWD/core/build/browserTest/war"
PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$WAR" >/dev/null 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT
for _ in $(seq 50); do python3 -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:$PORT/index.html')" 2>/dev/null && break; sleep 0.1; done
python3 "$(dirname "$0")/tiles.py" "http://127.0.0.1:$PORT" "$CHROME" "$PWD/core/browser-test/webapp/tiles"
