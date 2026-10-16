#!/usr/bin/env bash
# headless: numpy on PNGs, no window
# recut.sh [LAYERPY] OUT — r358: the painted examples' strips on disk (build/ai/examples/*-painted, ai-page.sh's) cut
# again by LAYERPY (default tools/r239-ai-strips/layer.py) into OUT/<name>_<strip>.png, each with its _mask.png and its
# kind's --grow from ai_page.py, no GPU. The examples' own _cut.png are left as they are (r261's sweep.sh reads them).
# A LAYERPY that knows --edges-of (r474) cuts a front variant (l<k>v<n>) on its front's cut in OUT, as ai_page.py does.
# Then: compose.py draws two OUTs side by side, cut_stats-style shares: python3 tools/r358-glow/share.py OUT/*.png
set -u
cd "$(dirname "$0")/../.." || exit 1
[ $# = 1 ] && set -- tools/r239-ai-strips/layer.py "$1"
L=$1 O=$2
mkdir -p "$O"
for e in build/ai/examples/*-painted; do
	[ -f "$e/recipe.json" ] || continue
	python3 - "$e/recipe.json" <<'PY' | while read -r name strip grow kind ckpt; do
import json, sys
sys.path.insert(0, "tools/r239-ai-strips")
from ai_page import GEN_CKPT, GROW
r = json.load(open(sys.argv[1]))
for lay in r["layers"]:
    for s in lay["strips"]:
        print(r["name"], s["strip"], GROW[lay["kind"]], lay["kind"], r.get("ckpt") or GEN_CKPT)
PY
		rules=()
		grep -q -- '"--kind"' "$L" && rules=(--kind "$kind" --ckpt "$ckpt")  # r400's RULES, as ai_page.py weighs them
		front=${strip%v[0-9]*}  # a recipe lists the front before its variants
		[ "$front" != "$strip" ] && grep -q -- '"--edges-of"' "$L" && rules+=(--edges-of "$O/${name}_$front.png")
		python3 "$L" "$e/strips/${strip}_ai.png" "$O/${name}_$strip.png" --mask "$e/strips/${strip}_mask.png" \
			--grow "$grow" --no-creatures "${rules[@]}" 2>/dev/null
	done
done
