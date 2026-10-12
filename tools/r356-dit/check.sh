#!/usr/bin/env bash
# check.sh [OUT] — r356's proof: tools/ai-page.sh paints r239's forest example with Z-Image-Turbo (a DiT, looped by
# gen.py's unet wrapper), and every strip it painted loops: seam ratio under 2 (sheet.py, which also writes
# OUT/strips-twice.png). About 12 min on the RTX 5060; ~75 s a strip, the pines retried when no trees grow.
# headless: --no-shots, nothing but ComfyUI in-process; the page's stills are ai-page.sh's without it.
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT="${1:-build/r356/forest-z}"
tools/ai-page.sh --theme "misty pine forest in the mountains at dawn" --name forest --style painted \
	--colours 7d8aa3,4f6f5c,1d3328 --seed 7 --ckpt z-image-turbo-Q5_K_M.gguf --no-shots "$OUT" || exit 1
python3 tools/r356-dit/sheet.py "$OUT/strips" "$OUT/strips-twice.png"
