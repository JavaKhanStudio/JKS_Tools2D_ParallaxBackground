#!/usr/bin/env bash
# run.sh [OUT] — r239's AI parallax strips, start to finish: silhouettes, paint, variants, cut, page.
#
#   tools/r239-ai-strips/run.sh build/r239
#   tools/parallax-lab-shots.sh tools/r239-ai-strips/page build/lab/r239    # then look at the frames
#
# Needs a ComfyUI checkout with its venv (COMFYUI_DIR, default ~/ComfyUI) holding dreamshaper_8.safetensors
# (SD 1.5), and ~1.5 GB of free VRAM; nothing is downloaded. ~14 s a strip on an RTX 5060 laptop.
# Writes the strips to OUT, then the atlases, pages and round to tools/r239-ai-strips/page.
set -eu
cd "$(dirname "$0")/../.."
OUT="${1:-build/r239}"
DIR=tools/r239-ai-strips
PY="${COMFYUI_DIR:-$HOME/ComfyUI}/venv/bin/python"
mkdir -p "$OUT"
NEG="text, watermark, frame, border, people, buildings, foreground grass"
STYLE="2d game parallax background layer"
paint() { # KIND SEED PROMPT
	python3 $DIR/sil.py "$1" "$OUT/$1" --seed 3
	"$PY" $DIR/gen.py img2img --init "$OUT/$1_init.png" --tile x --denoise 0.65 --seed "$2" \
		--negative "$NEG" --prompt "$STYLE, $3, plain pale sky" --out "$OUT/$1_ai.png"
}
paint mountains 11 "distant rocky mountain range, blue grey haze, painterly, soft light"
paint hills 12 "rolling green hills with small trees, painterly, soft light"
paint pines 13 "dark pine forest treeline silhouette, painterly"

# Three more treelines that start and end on the seed-3 one's columns: a SEQUENCE layer can chain any of them.
cp "$OUT/pines_ai.png" "$OUT/pinesw0.png"
for v in 1 2 3; do
	python3 $DIR/sil.py pines "$OUT/pinesw$v" --seed $((30 + v)) --edges-of 3
	"$PY" $DIR/gen.py variants --src "$OUT/pines_ai.png" --init "$OUT/pinesw${v}_init.png" --tile x --n 1 \
		--edge 96 --denoise 0.65 --seed $((40 + v)) --negative "$NEG" \
		--prompt "$STYLE, dark pine forest treeline silhouette, painterly, plain pale sky" --out "$OUT/pinesw$v.png"
	mv "$OUT/pinesw${v}_1.png" "$OUT/pinesw$v.png"
done

for k in mountains hills; do
	python3 $DIR/layer.py "$OUT/${k}_ai.png" "$OUT/${k}_layer.png"
	python3 $DIR/layer.py "$OUT/${k}_ai.png" "$OUT/${k}_px.png" --pixel 4 --colours 10
done
for v in 0 1 2 3; do
	python3 $DIR/layer.py "$OUT/pinesw$v.png" "$OUT/pinesw${v}_layer.png"
done
for v in 0 1 2 3; do # one palette for the four, built from the first
	python3 $DIR/layer.py "$OUT/pinesw$v.png" "$OUT/pinesw${v}_px.png" --pixel 4 --colours 10 --palette "$OUT/pinesw0_layer.png"
done

python3 $DIR/seam.py "$OUT"/{mountains,hills}_layer.png "$OUT"/{mountains,hills}_px.png
python3 $DIR/seam.py --order "$OUT"/pinesw{0,2,1,3}_layer.png --twice "$OUT/pines-sequence.png"
python3 $DIR/seam.py --order "$OUT"/pinesw{0,2,1,3}_px.png
python3 $DIR/stack.py "$OUT/painted-layers.png" "$OUT"/{mountains,hills,pinesw0}_layer.png --shift 0,0.3,0.6 --lift 0.3,0.12,0
python3 $DIR/make_page.py "$OUT"
