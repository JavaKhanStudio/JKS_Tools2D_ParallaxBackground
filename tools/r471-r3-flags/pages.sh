#!/usr/bin/env bash
# pages.sh REF OUT — r471's proof: tools/ai-page.sh with each of R3's flags on R2's forest recipe (parallax-ai r2's
# forest-story: DreamShaperXL Turbo + StorybookRedmondV2_XL 0.9, seed 7), REF the IP-Adapter's reference, and r239's
# IP-Adapter again inside the mask (story-ipa-inpaint: at 0.6 img2img paints REF's clouds into the sky, which
# creature.py refuses, r471), and r239's three painted examples (forest, beach, post-apo, SD 1.5) with --paint inpaint --denoise 0.85. One page each under
# OUT, its log beside it; prints each run's exit, lint line and recipe.json's "tools". Stills by parallax-lab-shots.sh
# (cage).
# headless: ai-page.sh's stills go through tools/parallax-lab-shots.sh, in cage
set -u
cd "$(dirname "$0")/../.." || exit 1
REF=${1:?REF} OUT=${2:?OUT}
mkdir -p "$OUT"
F="misty pine forest in the mountains at dawn"
B="tropical beach, palm trees, warm sunny afternoon"
P="post-apocalyptic ruined city, dusty orange haze, desolate"
FOREST=(--theme "$F" --name forest --style painted --colours "7d8aa3,4f6f5c,1d3328" --seed 7)
STORY=(--ckpt DreamShaperXL_Turbo_v2_1.safetensors --steps 8 --cfg 2 --sampler dpmpp_sde
	--lora StorybookRedmondV2_XL.safetensors --lora-strength 0.9)
page() { # NAME ARGS...
	local name=$1; shift
	tools/ai-page.sh "$@" "$OUT/$name" > "$OUT/$name.log" 2>&1
	echo "$name: exit $?, $(grep -m1 -E 'lint ok|^  ! ' "$OUT/$name.log"), tools $(python3 -c \
		'import json,sys; print(json.load(open(sys.argv[1])).get("tools"))' "$OUT/$name/recipe.json" 2>&1 | tail -1)"
}
want() { [[ " ${ONLY:-story-inpaint story-canny story-ipa story-ipa-inpaint story-small forest-inpaint beach-inpaint postapo-inpaint} " == *" $1 "* ]]; }
want story-inpaint && page story-inpaint "${FOREST[@]}" "${STORY[@]}" --paint inpaint --denoise 0.85
want story-canny && page story-canny "${FOREST[@]}" "${STORY[@]}" --control canny
want story-ipa && page story-ipa "${FOREST[@]}" "${STORY[@]}" --ipadapter "$REF"
want story-ipa-inpaint && page story-ipa-inpaint "${FOREST[@]}" "${STORY[@]}" --paint inpaint --denoise 0.85 \
	--ipadapter "$REF"
want story-small && page story-small "${FOREST[@]}" "${STORY[@]}" --small 768x288
want forest-inpaint && page forest-inpaint "${FOREST[@]}" --paint inpaint --denoise 0.85
want beach-inpaint && page beach-inpaint --theme "$B" --name beach --style painted --kinds mountains,sea,dunes,palms \
	--colours 8aa3b8,3fb0b0,e0c890,2a3a25 --sky 4f8fd0,cfe4f2 --seed 11 --paint inpaint --denoise 0.85
want postapo-inpaint && page postapo-inpaint --theme "$P" --name postapo --style painted --kinds mountains,ruins,ruins,ruins \
	--colours a08a78,7a6458,4a3c38,231c1c --sky 8a6a58,e0b48a --seed 13 --paint inpaint --denoise 0.85
