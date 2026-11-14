#!/usr/bin/env bash
# frame-check.sh [IMAGE] [OUT] — r523: libGDX draws a page from a WebP atlas page as it does from its PNG. Writes OUT
# (default build/r523): png/ and webp/ (make_pages.py: one page, one IMAGE layer of IMAGE, its atlas page a PNG or its
# lossless WebP twin), the frames of webp/tests WebpFrameCheck (both ways a page loads, two moments of a scroll),
# results.txt (pixels differing a frame pair, WEBP PASS/FAIL) and r523-png-vs-webp.png (PNG frame | WebP frame |
# difference x16). IMAGE defaults to parallax-ai's assets/fairy/region3-loop.png beside this checkout, else the test
# fixture webp/test-data/fairy-crop.png. Runs in cage's headless display (tools/nested-x.sh). Exit 1 on FAIL.
set -euo pipefail
cd "$(dirname "$0")/../.."
IMAGE=${1:-../JKS_Tools2D_ParallaxAI/assets/fairy/region3-loop.png}
[ -f "$IMAGE" ] || IMAGE=webp/test-data/fairy-crop.png
OUT=$(realpath -m "${2:-build/r523}")
rm -rf "$OUT/png" "$OUT/webp"
mkdir -p "$OUT/png" "$OUT/webp"

python3 tools/r523-webp/make_pages.py "$IMAGE" "$OUT"

./gradlew -q :webp:checkClasspath
CP=$(cat webp/build/check.classpath)
RUN="java -cp '$CP' jks.tools2d.parallax.webp.WebpFrameCheck '$OUT/png' '$OUT/webp' '$OUT' >'$OUT/check.log' 2>&1"
if [ "${ATELIER_NO_OFFSCREEN:-0}" = 1 ]; then
	bash -c "$RUN"
else
	WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 300 cage -- tools/nested-x.sh 1280x1280 bash -c "$RUN" >"$OUT/cage.log" 2>&1
fi
[ -f "$OUT/results.txt" ] || { echo "no results:"; tail -20 "$OUT/check.log"; exit 1; }
cat "$OUT/results.txt"

python3 - "$OUT" <<'PY'
import os, sys
from PIL import Image, ImageChops, ImageDraw
out = sys.argv[1]
rows = []
for name in ('path-t0', 'path-t4', 'manager-t0', 'manager-t4'):
    way, t = name.split('-')
    a = Image.open(os.path.join(out, f'{way}-png-{t}.png')).convert('RGB')
    b = Image.open(os.path.join(out, f'{way}-webp-{t}.png')).convert('RGB')
    d = ImageChops.difference(a, b).point(lambda v: min(255, v * 16))
    w, h = a.width // 2, a.height // 2
    row = Image.new('RGB', (w * 3, h + 24))
    for i, im in enumerate((a, b, d)):
        row.paste(im.resize((w, h)), (i * w, 24))
    ImageDraw.Draw(row).text((6, 6), f'{way} load, {t}: PNG atlas page | WebP atlas page | difference x16', fill='white')
    rows.append(row)
sheet = Image.new('RGB', (rows[0].width, sum(r.height for r in rows)))
for i, r in enumerate(rows):
    sheet.paste(r, (0, i * r.height))
sheet.save(os.path.join(out, 'r523-png-vs-webp.png'))
print('sheet: ' + os.path.join(out, 'r523-png-vs-webp.png'))
PY
grep -q '^WEBP PASS' "$OUT/results.txt"
