#!/usr/bin/env bash
# The anim lab's beach backdrop (r478): paint the beach with DreamShaperXL Turbo + Storybook (parallax-ai R3's "story"),
# put r365's sea strip back in place of the one it paints, lay the page out again around it, and repack it into
# core/test-data/anim (make_lab.py). The sea's pixels come out as they ship.
#   tools/r478-beach/remake.sh R365_STRIPS OUT     R365_STRIPS: build/r365/after/beach-painted/strips
# headless: ai-page.sh renders its shots through tools/parallax-lab-shots.sh, in cage
set -euo pipefail
cd "$(dirname "$0")/../.."
SEA="$1" OUT="$2"
FLAGS=(--theme 'tropical beach, palm trees, warm sunny afternoon' --name beach --style painted
	--kinds 'mountains,sea,dunes,palms' --colours '8aa3b8,3fb0b0,e0c890,2a3a25' --sky '4f8fd0,cfe4f2' --seed 11
	--ckpt DreamShaperXL_Turbo_v2_1.safetensors --steps 8 --cfg 2 --sampler dpmpp_sde
	--lora StorybookRedmondV2_XL.safetensors --lora-strength 0.9)
[ -f "$OUT/strips/l3v3_cut.png" ] || tools/ai-page.sh "${FLAGS[@]}" "$OUT"
rm -f "$OUT"/strips/l1_*
for f in l1_ai.png l1_init.png l1_mask.png l1_seed.txt; do cp "$SEA/$f" "$OUT/strips/"; done
tools/ai-page.sh "${FLAGS[@]}" --keep "$OUT"
python3 core/test-data/anim/make_lab.py "$OUT/page/beach.atlas"
cp "$OUT/page/beach.jplax" core/test-data/anim/beach.jplax
git diff --quiet -- core/test-data/anim/beach-sea.png || { echo "the sea moved: beach-sea.png differs" >&2; exit 1; }
python3 tools/parallax_lab.py lint --atlas core/test-data/anim core/test-data/anim/beach.jplax
