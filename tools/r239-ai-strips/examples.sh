#!/usr/bin/env bash
# examples.sh OUT — r239's examples for Simon: a forest, a beach and a post-apocalyptic city, each painted (SD 1.5)
# and pixel (SDXL + PixelArt_XL), painted by tools/ai-page.sh into OUT/<theme>-<style> and filmed by tools/ai-film.sh
# into OUT/<theme>-<style>/film (8 s, 30 fps). About 20 min on the RTX 5060 with nothing else on it. Exits 1 when a
# page fails lint: three do, each looked at on its render (r280, r281: lint faults they showed). Its pages are r239's attachments, bit for bit.
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT="${1:?OUT}"
F="misty pine forest in the mountains at dawn"
B="tropical beach, palm trees, warm sunny afternoon"
P="post-apocalyptic ruined city, dusty orange haze, desolate"
rc=0
for st in painted pixel; do
	tools/ai-page.sh --theme "$F" --name forest --style $st --colours 7d8aa3,4f6f5c,1d3328 --seed 7 \
		"$OUT/forest-$st" || rc=1
	tools/ai-page.sh --theme "$B" --name beach --style $st --kinds mountains,sea,dunes,palms \
		--colours 8aa3b8,3fb0b0,e0c890,2a3a25 --sky 4f8fd0,cfe4f2 --seed 11 "$OUT/beach-$st" || rc=1
	tools/ai-page.sh --theme "$P" --name postapo --style $st --kinds mountains,ruins,ruins,ruins \
		--colours a08a78,7a6458,4a3c38,231c1c --sky 8a6a58,e0b48a --seed 13 "$OUT/postapo-$st" || rc=1
done
for d in "$OUT"/*-painted "$OUT"/*-pixel; do
	tools/ai-film.sh "$d/page" "$d/film" 8 30 || rc=1
done
exit $rc
