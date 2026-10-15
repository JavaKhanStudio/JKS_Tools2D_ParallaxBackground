#!/usr/bin/env bash
# lab-check.sh OUT [SECONDS] [--no-build] — the lurker lab (r369, LurkerLab) holds the one rule in the browser.
#
#   tools/r369-lurker-cover/lab-check.sh build/r369/lab-check
#
# Two copies of effects-lab.html?lab=lurker step together at 1/60 s, the figure always drawn in one and never in the
# other (&figure=gone). Every step the lab calls covered, their canvases must be the same to the pixel: a switch there
# is never seen. Some step it calls uncovered must differ, or the check sees nothing. Then the lab's first mode, "the
# reader's sightings" (r370: ParallaxPageReader switches it), runs the same SECONDS (60 by default) and must make some
# switches and none while the figure is uncovered in the view, by the lab's own reckoning, while "every 2 s, anywhere"
# makes some there. Writes OUT/report.json and OUT/worst-covered.png, OUT/uncovered.png.
# LAB_WAR serves another war than core/build/browserTest/war (a copy with a mutant cover: lab-mutate.sh).
# headless: Playwright drives the system Chrome headless on SwiftShader, no window.
set -eu
cd "$(dirname "$0")/../.." || exit 1
OUT="$(realpath -m "${1:?usage: lab-check.sh OUT [SECONDS] [--no-build]}")"
shift
SECONDS_RUN=60 BUILD=1
for arg in "$@"; do
	if [ "$arg" = --no-build ]; then BUILD=0; else SECONDS_RUN="$arg"; fi
done
[ $BUILD = 0 ] || ./gradlew -q :core:browserTestWar >/dev/null
CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
WAR="${LAB_WAR:-$PWD/core/build/browserTest/war}"
mkdir -p "$OUT"
PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$WAR" >/dev/null 2>&1 &
SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT
for _ in $(seq 50); do python3 -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:$PORT/index.html')" 2>/dev/null && break; sleep 0.1; done
python3 "$(dirname "$0")/lab-check.py" "http://127.0.0.1:$PORT/effects-lab.html?lab=lurker&clip=1" "$CHROME" "$OUT" "$SECONDS_RUN"
