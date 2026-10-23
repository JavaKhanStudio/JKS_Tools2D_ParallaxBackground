#!/usr/bin/env bash
# run-page.sh PAGE OUT_DIR — opens engines/threejs/tests/PAGE (a path with its query) in headless Chrome, the
# repository served by engines/threejs/tools/serve.py, and returns when the page POSTs done: what it saved is in OUT_DIR,
# what it logged in OUT_DIR/threejs.log. frame-check.sh and bench.sh run it; it goes to the repository root first.
# Chrome draws on the GPU when tools/browser.py finds one, else on SwiftShader (CI); PARALLAX_GL=soft asks for it.
# Needs Chrome (CHROME=, or google-chrome, chromium, chromium-browser on the PATH) and python3.
set -u
PAGE=$1 OUT=$(realpath -m "$2")
cd "$(dirname "$0")/../../.." || exit 1
CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
[ -n "$CHROME" ] || { echo "no Chrome found: set CHROME=" >&2; exit 2; }
mkdir -p "$OUT"
mapfile -t GL < <(python3 tools/browser.py flags "$CHROME")
PORT_FILE=$(mktemp) PROFILE=$(mktemp -d)
timeout "${PAGE_TIMEOUT:-600}" python3 engines/threejs/tools/serve.py "$PWD" "$OUT" "$PORT_FILE" &
SERVER=$!
for _ in $(seq 50); do [ -s "$PORT_FILE" ] && break; sleep 0.1; done
"$CHROME" --headless=new "${GL[@]}" --user-data-dir="$PROFILE" --no-first-run --no-default-browser-check \
	--window-size=1280,1280 --force-device-scale-factor=1 \
	"http://127.0.0.1:$(cat "$PORT_FILE")/engines/threejs/tests/$PAGE" >"$OUT/chrome.log" 2>&1 &
BROWSER=$!
wait $SERVER
status=$?
kill $BROWSER 2>/dev/null; wait $BROWSER 2>/dev/null
rm -rf "$PROFILE" "$PORT_FILE" 2>/dev/null
[ $status = 0 ] || echo "run-page: the page did not finish (exit $status), see $OUT/chrome.log" >&2
exit $status
