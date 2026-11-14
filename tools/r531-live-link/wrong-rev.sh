#!/usr/bin/env bash
# wrong-rev.sh OUT: the live-link browser check must fail against a stub whose X-Plax-Rev is not the event's rev (r531).
# A copy of tools/live-link-stub.py with the header one rev off, handed to tools/r492-live-link/browser-check.py through
# LIVE_LINK_STUB (tools/live_link_harness.py). Exits 1 if it passes. engines/threejs/tools/live-check.py is not run:
# connectLive never reads the header (docs/live-link.md: "A reader rarely needs it", the events carry the same number),
# so it passes such a stub, before r531 as after; the header is browser-check's to hold.
# headless: both checks drive Chrome through tools/browser.py, no window.
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT="$(realpath -m "${1:?usage: wrong-rev.sh OUT}")"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
sed -E 's/extra\["X-Plax-Rev"\] = str\(rev\)/extra["X-Plax-Rev"] = str(rev - 1)/' tools/live-link-stub.py >"$WORK/stub.py"
if cmp -s tools/live-link-stub.py "$WORK/stub.py"; then echo "BROKEN: the edit matched nothing"; exit 1; fi
check=tools/r492-live-link/browser-check.py
if LIVE_LINK_STUB="$WORK/stub.py" python3 "$check" "$OUT/browser-check" >"$OUT/browser-check.log" 2>&1; then
	echo "SURVIVED $check: a wrong rev header passed"; exit 1
fi
echo "caught   $check: $(grep -c '^FAIL' "$OUT/browser-check.log") FAIL, first: $(grep -m1 '^FAIL' "$OUT/browser-check.log" | cut -c1-120)"
