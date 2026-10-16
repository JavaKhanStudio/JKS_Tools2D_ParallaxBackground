#!/usr/bin/env bash
# smoke.sh SRC REF OUT — r471: gen.py's new paths once each on one strip of a painted page (SRC: its strips/ dir, l3 and
# l3v1 with their init, mask and _ai.png, e.g. parallax-ai's build/rounds/r2/forest-story/strips), Turbo + Storybook XL:
# img2img --small, inpaint --small (the sky outside the mask must stay the init's), variants --mask --small with canny
# and IP-Adapter fed REF. Then prints, per output, its size, its loop seam (seam.py) and, for the inpaint ones, the
# largest level an outside-the-mask pixel moved from the init.
# headless: gen.py runs ComfyUI's nodes in-process, no window
set -u
cd "$(dirname "$0")/../.." || exit 1
SRC=${1:?SRC} REF=${2:?REF} OUT=${3:?OUT}
mkdir -p "$OUT"
PY=${COMFYUI_DIR:-$HOME/ComfyUI}/venv/bin/python
G=(tools/r239-ai-strips/gen.py)
M=(--ckpt DreamShaperXL_Turbo_v2_1.safetensors --lora StorybookRedmondV2_XL.safetensors --lora-strength 0.9
	--w 1536 --h 576 --steps 8 --cfg 2 --sampler dpmpp_sde --tile x --seed 7030
	--prompt "2d game parallax background layer, misty pine forest in the mountains at dawn, foreground dark pine forest treeline silhouette, painterly, plain pale sky")
CTL=(--control "$SRC/l3_mask.png" --control-edge --control-model controlnet-canny-sdxl-xinsir.safetensors
	--control-strength 0.4 --control-end 0.6)
run() { # NAME ARGS...
	local name=$1; shift
	"$PY" "${G[@]}" "$@" "${M[@]}" --out "$OUT/$name.png" > "$OUT/$name.log" 2>&1 || { echo "$name: FAILED"; tail -5 "$OUT/$name.log"; }
}
run small img2img --init "$SRC/l3_init.png" --denoise 0.65 --small 768x288
run inpaint inpaint --src "$SRC/l3_init.png" --mask "$SRC/l3_mask.png" --denoise 0.85 "${CTL[@]}"
run inpaint-small inpaint --src "$SRC/l3_init.png" --mask "$SRC/l3_mask.png" --denoise 0.85 --small 768x288
run variant-all variants --src "$SRC/l3_ai.png" --init "$SRC/l3v1_init.png" --mask "$SRC/l3v1_mask.png" --n 1 --edge 96 \
	--denoise 0.85 --small 768x288 "${CTL[@]}" --ipadapter "$REF" --ipa-weight 0.5
python3 - "$SRC" "$OUT" <<'PY'
import sys
import numpy as np
from PIL import Image
src, out = sys.argv[1:]
init = np.asarray(Image.open(f"{src}/l3_init.png").convert("RGB")).astype(int)
mask = np.asarray(Image.open(f"{src}/l3_mask.png").convert("L"))
for name in ("small", "inpaint", "inpaint-small", "variant-all_1"):
    try:
        im = np.asarray(Image.open(f"{out}/{name}.png").convert("RGB")).astype(int)
    except FileNotFoundError:
        print(f"{name}: missing")
        continue
    seam = np.abs(im[:, 0] - im[:, -1]).mean()
    inside = np.abs(im[:, 1:] - im[:, :-1]).mean()
    line = f"{name}: {im.shape[1]}x{im.shape[0]}, seam {seam:.1f}/255 (neighbour columns {inside:.1f})"
    if name.startswith("inpaint"):
        line += f", outside the mask moved at most {np.abs(im - init)[mask == 0].max()} levels"
    print(line)
PY
