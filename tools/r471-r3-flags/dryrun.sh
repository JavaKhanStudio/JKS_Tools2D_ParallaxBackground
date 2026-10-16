#!/usr/bin/env bash
# dryrun.sh OUT — r471: ai-page.sh --dry-run on the example recipes (r239's forest, beach, post-apo, painted and pixel;
# R2's forest-story on SDXL Turbo + Storybook; the forest with --clouds and a --lurker), one file each in OUT. Run on
# HEAD and on a change, then diff -r: a run without R3's flags must give the same gen.py lines.
# headless: --dry-run prints commands, opens nothing
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT="${1:?OUT}"
mkdir -p "$OUT"
F="misty pine forest in the mountains at dawn"
B="tropical beach, palm trees, warm sunny afternoon"
P="post-apocalyptic ruined city, dusty orange haze, desolate"
STORY=(--ckpt DreamShaperXL_Turbo_v2_1.safetensors --steps 8 --cfg 2 --sampler dpmpp_sde
	--lora StorybookRedmondV2_XL.safetensors --lora-strength 0.9)
run() { # NAME ARGS...
	local name=$1; shift
	tools/ai-page.sh --dry-run "$@" "build/ai-dry/$name" > "$OUT/$name.txt" 2>&1 || echo "exit $?" >> "$OUT/$name.txt"
}
for st in painted pixel; do
	run "forest-$st" --theme "$F" --name forest --style $st --colours 7d8aa3,4f6f5c,1d3328 --seed 7
	run "beach-$st" --theme "$B" --name beach --style $st --kinds mountains,sea,dunes,palms \
		--colours 8aa3b8,3fb0b0,e0c890,2a3a25 --sky 4f8fd0,cfe4f2 --seed 11
	run "postapo-$st" --theme "$P" --name postapo --style $st --kinds mountains,ruins,ruins,ruins \
		--colours a08a78,7a6458,4a3c38,231c1c --sky 8a6a58,e0b48a --seed 13
done
run forest-story --theme "$F" --name forest --colours 7d8aa3,4f6f5c,1d3328 --seed 7 "${STORY[@]}"
run forest-extras --theme "$F" --name forest --colours 7d8aa3,4f6f5c,1d3328 --seed 7 --clouds --lurker "a hooded figure"
# R3's flags (r471), one at a time and together, on R2's forest-story
run story-inpaint --theme "$F" --name forest --colours 7d8aa3,4f6f5c,1d3328 --seed 7 "${STORY[@]}" --paint inpaint --denoise 0.85
run story-canny --theme "$F" --name forest --colours 7d8aa3,4f6f5c,1d3328 --seed 7 "${STORY[@]}" --control canny
run story-ipa --theme "$F" --name forest --colours 7d8aa3,4f6f5c,1d3328 --seed 7 "${STORY[@]}" --ipadapter REF.png
run story-small --theme "$F" --name forest --colours 7d8aa3,4f6f5c,1d3328 --seed 7 "${STORY[@]}" --small 768x288
run story-all --theme "$F" --name forest --colours 7d8aa3,4f6f5c,1d3328 --seed 7 "${STORY[@]}" --paint inpaint \
	--denoise 0.85 --control canny --control-strength 0.4 --control-end 0.6 --ipadapter REF.png --ipa-weight 0.5 \
	--small 768x288 --clouds
