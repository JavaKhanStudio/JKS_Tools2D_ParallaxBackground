#!/usr/bin/env bash
# The browser suite (core/browser-test, r68): the core tests that translate to JavaScript, run in Chrome.
#   tools/browser-test.sh               the gate: headless Chrome, prints the summary, exit 1 unless every case passed
#   tools/browser-test.sh --shot F.png  the gate, and a screenshot of the results page
#   tools/browser-test.sh --open        the lab: the results page in a Chrome window, served until the window closes
# Builds with ./gradlew :core:browserTestWar first (--no-build skips it). Needs Chrome (CHROME=, or google-chrome,
# chromium, chromium-browser on the PATH) and python3, which serves the page.
set -u
cd "$(dirname "$0")/.."
MODE=gate SHOT="" BUILD=1
while [ $# -gt 0 ]; do
	case "$1" in
		--open) MODE=open ;;
		--shot) SHOT="$(realpath -m "$2")"; shift ;;
		--no-build) BUILD=0 ;;
		*) echo "unknown argument: $1" >&2; exit 2 ;;
	esac
	shift
done

CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
[ -n "$CHROME" ] || { echo "no Chrome found: set CHROME=" >&2; exit 2; }
WAR="$PWD/core/build/browserTest/war"
if [ $BUILD = 1 ]; then
	# stdout is the asset preloader chatting; errors come on stderr
	./gradlew -q :core:browserTestWar >/dev/null || exit 1
fi
[ -f "$WAR/index.html" ] || { echo "$WAR is not built: run without --no-build" >&2; exit 2; }

PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$WAR" >/dev/null 2>&1 &
SERVER=$!
PROFILE=$(mktemp -d)
trap 'kill $SERVER 2>/dev/null; rm -rf "$PROFILE"' EXIT
URL="http://127.0.0.1:$PORT/index.html"
for _ in $(seq 50); do python3 -c "import urllib.request; urllib.request.urlopen('$URL')" 2>/dev/null && break; sleep 0.1; done

# SwiftShader is Chrome's software WebGL: the page is a libGDX app and needs a GL context, with or without a GPU.
FLAGS=(--user-data-dir="$PROFILE" --no-first-run --no-default-browser-check --enable-unsafe-swiftshader)
if [ $MODE = open ]; then
	echo "serving $URL until the Chrome window closes"
	"$CHROME" "${FLAGS[@]}" --new-window "$URL" >/dev/null 2>&1
	exit 0
fi

HEADLESS=(--headless=new --use-angle=swiftshader --virtual-time-budget=20000)
SUMMARY=$(timeout 120 "$CHROME" "${FLAGS[@]}" "${HEADLESS[@]}" --dump-dom "$URL" 2>/dev/null \
	| python3 -c '
import html, re, sys
page = sys.stdin.read()
summary = re.search(r"<pre id=\"summary\">(.*?)</pre>", page, re.S)
print(html.unescape(summary.group(1)) if summary else "browser-tests: no summary, the page did not run the suite")
for row in re.findall(r"<tr class=\"fail\".*?</tr>", page, re.S):
    print("  " + " | ".join(html.unescape(c) for c in re.findall(r"<td[^>]*>(.*?)</td>", row, re.S)[1:]))
')
echo "$SUMMARY"
if [ -n "$SHOT" ]; then
	timeout 120 "$CHROME" "${FLAGS[@]}" "${HEADLESS[@]}" --window-size=1100,1000 --screenshot="$SHOT" "$URL" >/dev/null 2>&1
	echo "screenshot: $SHOT"
fi
echo "$SUMMARY" | head -1 | grep -q ' 0 failed' && ! echo "$SUMMARY" | head -1 | grep -q ': 0 passed'
