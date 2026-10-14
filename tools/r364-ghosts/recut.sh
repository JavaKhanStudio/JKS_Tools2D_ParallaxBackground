#!/usr/bin/env bash
# headless: numpy on PNGs, no window
# recut.sh LAYERPY OUT PAGE... — r364: every strip of each painted ai-page.sh PAGE (its recipe.json, strips/) cut again
# by LAYERPY into OUT/<page's folder name>/<strip>.png, with its _mask.png and its kind's --grow from ai_page.py, eight
# at a time, no GPU. The pages' own _cut.png are left as they are. r358's recut.sh does the same for the examples only.
#   tools/r364-ghosts/recut.sh tools/r239-ai-strips/layer.py build/r364/after ../JKS_Tools2D_ParallaxAI/build/rounds/r1/*/
set -u
cd "$(dirname "$0")/../.." || exit 1
L=${1:?LAYERPY} O=${2:?OUT}
shift 2
# shellcheck disable=SC2016 # xargs's sh -c expands $0..$4, its own arguments
for e in "$@"; do
	e=${e%/}
	[ -f "$e/recipe.json" ] || continue
	python3 - "$e/recipe.json" <<'PY' | while read -r strip grow; do
import json, sys
sys.path.insert(0, "tools/r239-ai-strips")
from ai_page import GROW
r = json.load(open(sys.argv[1]))
if r.get("style") == "painted":
    for lay in r["layers"]:
        for s in lay["strips"]:
            print(s["strip"], GROW[lay["kind"]])
PY
		mkdir -p "$O/${e##*/}"
		printf '%s\0' "$e/strips/${strip}_ai.png" "$O/${e##*/}/$strip.png" "$e/strips/${strip}_mask.png" "$grow"
	done
done | xargs -0 -n 4 -P 8 sh -c 'python3 "$0" "$1" "$2" --mask "$3" --grow "$4" --no-creatures 2>/dev/null' "$L"
