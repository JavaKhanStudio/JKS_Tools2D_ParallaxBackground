#!/usr/bin/env bash
# r425 (Simon at 23:35: "Sun behind is still very bad and you did not show the sun behind the last layer"): the shadow
# lab before and after, as two sheets in OUT: behind-relief.png (A, the sun behind you, left and right, 6, 12 and 25
# degrees up) and sun-behind-peaks.png (B, the sun 14 degrees left sinking past the far peaks). BEFORE is a checkout
# of the lab before (a git worktree, its war built: ./gradlew :core:browserTestWar there).
#   tools/r425-shadow-relief/sheets.sh BEFORE OUT
# headless: tools/shadow-lab.sh --shot runs headless Chrome.
set -eu
cd "$(dirname "$0")/../.." || exit 1
BEFORE=$(realpath "$1") OUT=$(realpath -m "$2")
mkdir -p "$OUT"
./gradlew -q :core:browserTestWar >/dev/null
for s in -40 30; do
	for h in 6 12 25; do
		tools/shadow-lab.sh --no-build --behind --slider sun1Side=$s --slider sun1Height=$h --shot "$OUT/new-b$s-$h.png" >/dev/null
		(cd "$BEFORE" && tools/shadow-lab.sh --no-build --behind --slider sun1Side=$s --slider sun1Height=$h --shot "$OUT/old-b$s-$h.png" >/dev/null)
	done
done
for h in 14 11 8; do
	tools/shadow-lab.sh --no-build --slider sun1Side=-14 --slider sun1Height=$h --slider cameraScroll=0 --shot "$OUT/new-s$h.png" >/dev/null
	(cd "$BEFORE" && tools/shadow-lab.sh --no-build --slider sun1Side=-14 --slider sun1Height=$h --slider cameraScroll=0 --shot "$OUT/old-s$h.png" >/dev/null)
done
python3 - "$OUT" <<'PY'
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont

D = sys.argv[1]
path = subprocess.run(["fc-match", "-f", "%{file}", "Noto Sans:bold"], capture_output=True, text=True).stdout
font = ImageFont.truetype(path, 22) if path else ImageFont.load_default(22)


def panel(p, b):
    return Image.open(p).convert("RGB").crop((772, 50, 1528, 476) if b else (12, 50, 768, 476))


def sheet(rows, out, head):
    w, h = 756, 426
    img = Image.new("RGB", (2 * w + 12, 44 + len(rows) * (h + 40)), (30, 32, 38))
    d = ImageDraw.Draw(img)
    d.text((10, 8), head[0], font=font, fill=(235, 235, 235))
    d.text((w + 22, 8), head[1], font=font, fill=(235, 235, 235))
    for i, (label, left, right) in enumerate(rows):
        y = 44 + i * (h + 40)
        d.text((10, y + 4), label, font=font, fill=(200, 210, 255))
        img.paste(left, (0, y + 36))
        img.paste(right, (w + 12, y + 36))
    img.save(out)


rows = [("the sun behind you, %d degrees %s, %s up" % (abs(s), "left" if s < 0 else "right", h),
         panel("%s/old-b%d-%d.png" % (D, s, h), False), panel("%s/new-b%d-%d.png" % (D, s, h), False))
        for s in (-40, 30) for h in (6, 12, 25)]
sheet(rows, D + "/behind-relief.png", ("before: A, its shadows on the ground only", "now: A, with the relief"))
rows = [("the sun 14 degrees left, %d up (B, as painted)" % h,
         panel("%s/old-s%d.png" % (D, h), True), panel("%s/new-s%d.png" % (D, h), True)) for h in (14, 11, 8)]
sheet(rows, D + "/sun-behind-peaks.png", ("before: over the far peaks", "now: behind them"))
PY
echo "$OUT/behind-relief.png $OUT/sun-behind-peaks.png"
