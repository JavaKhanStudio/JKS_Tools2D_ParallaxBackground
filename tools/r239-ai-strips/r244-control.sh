#!/usr/bin/env bash
# r244-control.sh [OUT] — does ControlNet lineart hold a silhouette at a higher denoise than img2img at 0.65?
#
#   tools/r239-ai-strips/r244-control.sh build/r244
#
# Paints hills and pines (run.sh's prompts and seeds) four ways: img2img 0.65 (run.sh's), and img2img 0.8 and 0.9
# with control_v11p_sd15_lineart fed the silhouette's outline, and 0.8 with that hint weak (0.5, first 60% of the
# steps: r257). Cuts each with layer.py, then prints, per strip,
# how well the cut holds the silhouette (IoU of alpha > 0.5 with sil.py's mask) and seam.py's numbers, and
# writes OUT/compare.png: each strip drawn twice (seam.py --twice), the four ways one under the other.
set -eu
cd "$(dirname "$0")/../.."
OUT="${1:-build/r244}"
DIR=tools/r239-ai-strips
PY="${COMFYUI_DIR:-$HOME/ComfyUI}/venv/bin/python"
mkdir -p "$OUT"
NEG="text, watermark, frame, border, people, buildings, foreground grass"
STYLE="2d game parallax background layer"
declare -A SEED=([hills]=12 [pines]=13)
declare -A PROMPT=([hills]="rolling green hills with small trees, painterly, soft light"
	[pines]="dark pine forest treeline silhouette, painterly")
for k in hills pines; do
	[ -e "$OUT/${k}_mask.png" ] || python3 $DIR/sil.py $k "$OUT/$k" --seed 3
	for way in i065 c080 c090 w080; do
		ctl=() d=0.65
		case $way in
		c080) ctl=(--control "$OUT/${k}_mask.png" --control-edge) d=0.8 ;;
		c090) ctl=(--control "$OUT/${k}_mask.png" --control-edge) d=0.9 ;;
		# r257: a weak hint that lets go after 60% of the steps, to hold the mass and leave SD the trees.
		w080) ctl=(--control "$OUT/${k}_mask.png" --control-edge --control-strength 0.5 --control-end 0.6) d=0.8 ;;
		esac
		# A strip already painted is kept: a rerun after an out-of-memory paints only what is missing.
		[ -e "$OUT/${k}_$way.png" ] || "$PY" $DIR/gen.py img2img --init "$OUT/${k}_init.png" --tile x --denoise $d --seed "${SEED[$k]}" "${ctl[@]}" \
			--negative "$NEG" --prompt "$STYLE, ${PROMPT[$k]}, plain pale sky" --out "$OUT/${k}_$way.png" \
			>"$OUT/${k}_$way.log" 2>&1
		python3 $DIR/layer.py "$OUT/${k}_$way.png" "$OUT/${k}_${way}_layer.png" || echo "$k $way: layer.py exit $?"
		python3 $DIR/seam.py "$OUT/${k}_${way}_layer.png" --twice "$OUT/${k}_${way}_twice.png"
	done
done
python3 - "$OUT" <<'EOF'
import sys
import numpy as np
from PIL import Image, ImageDraw
out = sys.argv[1]
rows = []
for k in ("hills", "pines"):
    m = np.asarray(Image.open(f"{out}/{k}_mask.png").convert("L")) > 127
    for way in ("i065", "c080", "c090", "w080"):
        a = np.asarray(Image.open(f"{out}/{k}_{way}_layer.png"))[..., 3] > 127
        iou = (a & m).sum() / (a | m).sum()
        print(f"{k} {way}: silhouette IoU {iou:.3f}  (art outside the mask {(a & ~m).sum()} px, mask left empty {(m & ~a).sum()} px)")
        # Over the sky the strip was painted on (black hides the dark layers), and a crop at full size
        # beside it: detail is what a higher denoise is for.
        lay = Image.open(f"{out}/{k}_{way}_layer.png").convert("RGBA")
        sky = Image.new("RGBA", lay.size, (210, 225, 240, 255))
        sky.alpha_composite(lay)
        whole = sky.convert("RGB").resize((lay.width // 2, lay.height // 2), Image.BOX)
        crop = sky.convert("RGB").crop((0, lay.height // 3, 512, lay.height // 3 + 192))
        t = Image.new("RGB", (whole.width + 4 + crop.width, max(whole.height, crop.height)), "white")
        t.paste(whole, (0, 0))
        t.paste(crop, (whole.width + 4, 0))
        ImageDraw.Draw(t).text((6, 4), f"{k} {way}  IoU {iou:.3f}", fill=(0, 0, 0))
        rows.append(t)
sheet = Image.new("RGB", (max(r.width for r in rows), sum(r.height + 4 for r in rows)), "white")
y = 0
for r in rows:
    sheet.paste(r, (0, y))
    y += r.height + 4
sheet.save(f"{out}/compare.png")
EOF
