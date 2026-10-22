#!/usr/bin/env bash
# The panels' choices (r428), used as Simon uses them: in each lab, the mouse on a choice over the canvas, another
# option picked, the panel drawn again, and Copy settings carrying it. Stills of each lab's stage before and after into
# OUT. Fails when a chip cannot be clicked through the titles' grid, or a pick changes nothing on the canvas.
#   tools/r428-lab-chips/chips.sh OUT [--no-build]
# headless: Playwright drives the system Chrome headless on the GPU (tools/browser.py), no window.
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT="$(realpath -m "${1:?usage: chips.sh OUT [--no-build]}")"
[ "${2:-}" = --no-build ] || ./gradlew -q :core:browserTestWar >/dev/null || exit 1
CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
WAR="$PWD/core/build/browserTest/war"
mkdir -p "$OUT"
PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$WAR" >/dev/null 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT
for _ in $(seq 50); do python3 -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:$PORT/index.html')" 2>/dev/null && break; sleep 0.1; done
python3 "$(dirname "$0")/chips.py" "http://127.0.0.1:$PORT" "$CHROME" "$OUT"
