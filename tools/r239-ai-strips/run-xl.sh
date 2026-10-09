#!/usr/bin/env bash
# run-xl.sh [OUT] — r243's true pixel-art layers: SDXL + PixelArt_XL over sil.py's silhouettes, cut one art
# pixel per cell by pixel.py, packed as page/pixel-xl.atlas by make_page.py --xl.
#
#   tools/r239-ai-strips/run-xl.sh build/r243
#   tools/parallax-lab-shots.sh tools/r239-ai-strips/page build/lab/r243
#
# Needs what run.sh needs, plus sdXL_v10VAEFix.safetensors and loras/PixelArt_XL.safetensors, and ~4 GB of
# free VRAM (SDXL offloads part of itself below that). Each paint's time and the GPU's memory in use before
# it are appended to OUT/times.txt: a time means nothing without what else held the GPU.
set -eu
cd "$(dirname "$0")/../.."
OUT="${1:-build/r243}"
DIR=tools/r239-ai-strips
PY="${COMFYUI_DIR:-$HOME/ComfyUI}/venv/bin/python"
mkdir -p "$OUT"
NEG="clouds, sun, moon, birds, gradient, text, watermark, frame, border, people, buildings, foreground"
STYLE="pixel art, 2d game parallax background layer"
paint() { # KIND SEED PROMPT
	python3 $DIR/sil.py "$1" "$OUT/$1" --w 1536 --h 576 --seed 3
	local gpu t0 t1
	gpu=$(nvidia-smi --query-gpu=memory.used --format=csv,noheader 2>/dev/null || echo "?")
	t0=$(date +%s.%N)
	"$PY" $DIR/gen.py img2img --ckpt sdXL_v10VAEFix.safetensors --lora PixelArt_XL.safetensors --lora-strength 1.0 \
		--init "$OUT/$1_init.png" --w 1536 --h 576 --tile x --denoise 0.7 --seed "$2" \
		--negative "$NEG" --prompt "$STYLE, $3, empty plain flat pale sky, solid background colour" \
		--out "$OUT/$1_xl.png" >"$OUT/$1_xl.log" 2>&1
	t1=$(date +%s.%N)
	printf '%s\t%.1f s\tGPU in use before: %s\n' "$1" "$(echo "$t1 - $t0" | bc)" "$gpu" | tee -a "$OUT/times.txt"
}
paint mountains 11 "distant rocky mountain range, blue grey"
paint hills 12 "rolling green hills with small trees"
paint pines 13 "dark pine forest treeline"

# A palette per layer: one shared by three layers of different hues starves each of them (16 shared
# colours left the mountains 6, and took the pines' trunks and highlights). The segments of one
# SEQUENCE layer do share one (pixel.py --palette), or their joins change colour.
for k in mountains hills pines; do
	python3 $DIR/pixel.py "$OUT/${k}_xl.png" "$OUT/${k}_xlpx.png" --mask "$OUT/${k}_mask.png" --colours 12
done
python3 $DIR/seam.py "$OUT"/{mountains,hills,pines}_xlpx.png
python3 $DIR/make_page.py "$OUT" --xl
