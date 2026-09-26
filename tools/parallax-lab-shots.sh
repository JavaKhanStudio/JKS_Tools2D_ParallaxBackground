#!/usr/bin/env bash
# parallax-lab-shots.sh ROUND_DIR OUT_DIR — stills of every scene of a lab round (r73), off Simon's screen.
#
#   tools/parallax-lab-shots.sh demo/lab/round1 demo/build/lab/round1
#
# Runs ParallaxLab --shots in cage's headless display on a nested Xwayland (needs cage and Xwayland), from the demo's
# installDist (built first). Writes <scene>-t0/-t6/-t12.png (1280x720, or the size a scene's "resize" gives; 0, 6 and
# 12 s into the same scroll) and, with python3 and Pillow, contact.png: one row per scene. ATELIER_NO_OFFSCREEN=1 runs
# it in a window instead.
set -u
cd "$(dirname "$0")/.."
ROUND="$1" OUT="$(realpath -m "$2")"
mkdir -p "$OUT"
./gradlew -q :demo:installDist >/dev/null || exit 1
CP="$PWD/demo/build/install/demo/lib/*"
RUN="java -cp \"$CP\" jks.tools2d.parallax.demo.ParallaxLab \"$ROUND\" --shots \"$OUT\" >\"$OUT/lab.log\" 2>&1"
if [ "${ATELIER_NO_OFFSCREEN:-0}" = 1 ]; then
	bash -c "$RUN"
else
	inner="Xwayland :9 -geometry 1280x1280 & X=\$!; sleep 2; DISPLAY=:9 $RUN; kill \$X 2>/dev/null"
	WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 300 cage -- bash -c "$inner" >"$OUT/cage.log" 2>&1
fi
ls "$OUT"/*-t0.png >/dev/null 2>&1 || { echo "no shots:"; tail -20 "$OUT/lab.log"; exit 1; }
python3 - "$ROUND" "$OUT" <<'PY' || echo "no contact sheet (python3 + Pillow)"
import json, os, sys
from PIL import Image, ImageDraw
round_dir, out = sys.argv[1], sys.argv[2]
scenes = json.load(open(os.path.join(round_dir, 'round.json')))['scenes']
w, h, label = 426, 240, 22
sheet = Image.new('RGB', (w * 3, (h + label) * len(scenes)), 'black')
draw = ImageDraw.Draw(sheet)
for row, s in enumerate(scenes):
    y = row * (h + label)
    draw.text((6, y + 5), s['id'] + '  ' + s['about'], fill='white')
    for col, t in enumerate((0, 6, 12)):
        shot = Image.open(os.path.join(out, '%s-t%d.png' % (s['id'], t))).convert('RGB').resize((w, h))
        sheet.paste(shot, (col * w, y + label))
sheet.save(os.path.join(out, 'contact.png'))
print('contact sheet: ' + os.path.join(out, 'contact.png'))
PY
