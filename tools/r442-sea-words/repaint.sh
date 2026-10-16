#!/usr/bin/env bash
# headless: sil.py, gen.py (ComfyUI in-process, gpu_lock) and layer.py on PNGs, no window
# repaint.sh OUT SEED... — r442: tools/r438-dunes/repaint.sh with the dunes' negative since r442 (the page has a sea layer:
# 'sea, ocean, water' in it). r438's, with r437's negative, is the before. The beach's dunes strip painted on each
# SEED exactly as ai-page.sh paints it (its --dry-run's sil.py, gen.py and layer.py lines), each try kept in
# OUT/s<SEED>_*: ai-page.sh overwrites a refused strip with the next. Prints layer.py's exit and what it named.
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT=$1
shift
D=tools/r239-ai-strips
mkdir -p "$OUT"
for s in "$@"; do
	p="$OUT/s$s"
	python3 "$D"/sil.py dunes "$p" --w 1536 --h 576 --seed "$s" --colour e0c890 --quiet-join --texture 1.0 >"${p}_sil.log" 2>&1 || exit 1
	~/ComfyUI/venv/bin/python "$D"/gen.py img2img --ckpt DreamShaperXL_Turbo_v2_1.safetensors --w 1536 --h 576 \
		--steps 8 --cfg 2.0 --sampler dpmpp_sde --denoise 0.65 \
		--negative 'text, watermark, frame, border, people, buildings, foreground grass, birds, animals, creatures, sea, ocean, water, palm trees' \
		--prompt '2d game parallax background layer, tropical beach, warm sunny afternoon, middle distance sand dunes, painterly, plain pale sky' \
		--init "${p}_init.png" --tile x --seed "$s" --out "${p}_ai.png" >"${p}_gen.log" 2>&1 || exit 1
	python3 "$D"/layer.py "${p}_ai.png" "${p}_cut.png" --mask "${p}_mask.png" --grow 8 --kind dunes \
		--ckpt DreamShaperXL_Turbo_v2_1.safetensors --rules-json "${p}_rules.json" >"${p}_cut.log" 2>&1
	echo "$s: layer.py exit $? $(grep -o 'a creature.*' "${p}_cut.log" | paste -sd';')"
done
