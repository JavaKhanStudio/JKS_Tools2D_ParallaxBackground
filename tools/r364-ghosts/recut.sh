#!/usr/bin/env bash
# headless: numpy on PNGs, no window
# recut.sh LAYERPY OUT PAGE... — r364: every strip of each painted ai-page.sh PAGE (its recipe.json, strips/) cut again
# by LAYERPY into OUT/<page's folder name>/<strip>.png, with its _mask.png and its kind's --grow from ai_page.py, eight
# at a time, no GPU. The pages' own _cut.png are left as they are. r358's recut.sh does the same for the examples only.
# A LAYERPY that knows --kind (r400's RULES) is given each strip's kind and checkpoint, as ai_page.py gives them; one
# that knows --edges-of (r474) cuts a front variant (l<k>v<n>) after the fronts, on its front's cut in OUT, as
# ai_page.py does.
#   tools/r364-ghosts/recut.sh tools/r239-ai-strips/layer.py build/r364/after ../JKS_Tools2D_ParallaxAI/build/rounds/r1/*/
set -u
cd "$(dirname "$0")/../.." || exit 1
L=${1:?LAYERPY} O=${2:?OUT}
shift 2
edges=
grep -q -- '"--edges-of"' "$L" && edges=1
jobs() { # PASS PAGE...: PASS 0 every strip but the front variants, 1 those (only when LAYERPY knows --edges-of)
	local pass=$1 e strip grow kind ckpt front rules
	shift
	for e in "$@"; do
		e=${e%/}
		[ -f "$e/recipe.json" ] || continue
		python3 - "$e/recipe.json" <<'PY' | while read -r strip grow kind ckpt; do
import json, sys
sys.path.insert(0, "tools/r239-ai-strips")
from ai_page import GROW, GEN_CKPT
r = json.load(open(sys.argv[1]))
if r.get("style") == "painted":
    for lay in r["layers"]:
        for s in lay["strips"]:
            print(s["strip"], GROW[lay["kind"]], lay["kind"], r.get("ckpt") or GEN_CKPT)
PY
			front=${strip%v[0-9]*}
			[ -n "$edges" ] && [ "$front" != "$strip" ] || front=
			[ "$pass" = "$([ -n "$front" ] && echo 1 || echo 0)" ] || continue
			mkdir -p "$O/${e##*/}"
			rules=
			grep -q -- '"--kind"' "$L" && rules="--kind=$kind --ckpt=$ckpt"
			[ -n "$front" ] && rules="$rules --edges-of=$O/${e##*/}/$front.png"
			printf '%s\0' "$e/strips/${strip}_ai.png" "$O/${e##*/}/$strip.png" "$e/strips/${strip}_mask.png" "$grow" "$rules"
		done
	done
}
for pass in 0 1; do
	# shellcheck disable=SC2016 # xargs's sh -c expands $0..$4, its own arguments
	jobs "$pass" "$@" | xargs -0 -r -n 5 -P 8 sh -c \
		'python3 "$0" "$1" "$2" --mask "$3" --grow "$4" --no-creatures $5 2>/dev/null' "$L"
done
