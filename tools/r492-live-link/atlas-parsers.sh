#!/usr/bin/env bash
# atlas-parsers.sh: the atlas the live link makes for loose images (docs/live-link.md "A loose image"), read by libGDX's
# own parser and the Godot reader's: every region named, at its whole image, its original size its size. The three.js
# parser reads it in browser-check.py. Needs java, godot and gdx's jar in the Gradle cache (r492).
# headless: godot --headless and a plain java, no window.
set -u
cd "$(dirname "$0")/../.." || exit 1
WORK=$(mktemp -d)
STUB=
trap '[ -n "$STUB" ] && kill "$STUB" 2>/dev/null; rm -rf "$WORK"' EXIT
mkdir "$WORK/loose" "$WORK/empty" "$WORK/got"
python3 - "$WORK" <<'PY'
import json
import sys
from PIL import Image
work = sys.argv[1]
Image.new("RGB", (4, 3), "red").save(f"{work}/loose/hills.png")
Image.new("RGB", (8, 2), "blue").save(f"{work}/loose/sky-2.png")
json.dump({"pageModel": {"atlasName": "", "pageList": []}}, open(f"{work}/p.jplax", "w"))
PY
python3 tools/live-link-stub.py "$WORK/p.jplax" "$WORK/empty" --loose "$WORK/loose" --port 0 >"$WORK/url" &
STUB=$!
for _ in $(seq 50); do [ -s "$WORK/url" ] && break; python3 -c 'import time; time.sleep(0.1)'; done
URL=$(head -1 "$WORK/url")
for f in live.atlas hills.png sky-2.png; do curl -fsS "${URL}$f" -o "$WORK/got/$f" || exit 1; done
sed 's/^/  atlas: /' "$WORK/got/live.atlas"
GDX=$(find ~/.gradle/caches -name 'gdx-1*.jar' | grep -v sources | head -1)
status=0
expect_libgdx=$'hills hills.png 0,0 4x3 orig 4x3 index -1\nsky-2 sky-2.png 0,0 8x2 orig 8x2 index -1'
got=$(java -cp "$GDX" tools/r492-live-link/AtlasProbe.java "$WORK/got/live.atlas" 2>&1)
if [ "$got" = "$expect_libgdx" ]; then echo "PASS libGDX"; else echo "FAIL libGDX: $got"; status=1; fi
expect_godot=$'hills 0,0 4x3 orig 4x3\nsky-2 0,0 8x2 orig 8x2'
got=$(godot --headless --path engines/godot --script "$PWD/tools/r492-live-link/atlas_probe.gd" -- "$WORK/got/live.atlas" 2>&1 | grep -E 'orig|ERROR')
if [ "$got" = "$expect_godot" ]; then echo "PASS Godot"; else echo "FAIL Godot: $got"; status=1; fi
exit $status
