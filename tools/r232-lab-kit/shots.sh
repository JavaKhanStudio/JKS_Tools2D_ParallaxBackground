#!/usr/bin/env bash
# The labs as Simon sees them (r232), headless: every HTML lab (fog, haze, transfert, the three effects labs, the
# browser tests' page) at the size of the window its tools/*-lab.sh opens (1580x710 inside Chrome's 1580x800), the
# viewport only, so what needs a scroll is cut off as it is for him; and on a 390x844 phone, the whole page.
#   tools/r232-lab-kit/shots.sh OUT [--no-build]   OUT/<lab>-desktop.png, OUT/<lab>-phone.png, and the fit checks
# Fails when a lab's last slider is below the desktop viewport, a control's text is under 14px, a value runs out of its
# slider's box, a PASS cell breaks over two lines, or a page is wider than
# the phone (the kit's layout rules: core/browser-test/webapp/lab.css).
# headless: Playwright drives the system Chrome headless on SwiftShader, no window.
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT="$(realpath -m "${1:?usage: shots.sh OUT [--no-build]}")"
[ "${2:-}" = --no-build ] || ./gradlew -q :core:browserTestWar >/dev/null || exit 1
CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
WAR="$PWD/core/build/browserTest/war"
mkdir -p "$OUT"
PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$WAR" >/dev/null 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT
for _ in $(seq 50); do python3 -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:$PORT/index.html')" 2>/dev/null && break; sleep 0.1; done
python3 "$(dirname "$0")/shots.py" "http://127.0.0.1:$PORT" "$CHROME" "$OUT"
