#!/usr/bin/env bash
# headless: ai_page.py --keep --no-shots re-cuts and lays out, no GPU, no window
# relay.sh AI_PAGE OUT PAGE... — r400: each PAGE's painted strips (an ai-page.sh OUT: its recipe.json, strips/) copied
# into OUT/<page's folder name> and laid out again by AI_PAGE (an ai_page.py, HEAD's in a worktree for the before)
# with --keep and the page's own command: no painting, the cut and the page only. Then parallax-lab-shots.sh on
# OUT/<name>/page gives the stills.
set -u
cd "$(dirname "$0")/../.." || exit 1
A=${1:?AI_PAGE} O=${2:?OUT}
shift 2
for e in "$@"; do
	e=${e%/} n=${e##*/}
	mkdir -p "$O/$n/strips"
	cp "$e"/strips/*_ai.png "$e"/strips/*_mask.png "$e"/strips/*_init.png "$e"/strips/*_seed.txt "$O/$n/strips/"
	# the page's own arguments, its OUT replaced
	mapfile -t args < <(python3 -c 'import json, shlex, sys; print("\n".join(shlex.split(json.load(open(sys.argv[1]))["command"])[1:-1]))' "$e/recipe.json")
	if python3 "$A" "${args[@]}" --keep --no-shots "$(realpath "$O/$n")" >"$O/$n.log" 2>&1; then
		echo "$n: laid out ($O/$n.log)"
	else
		echo "$n: exit $? ($O/$n.log)"
	fi
done
