#!/usr/bin/env bash
# r305: proves a change to tools/r239-ai-strips/ai_page.py runs the same commands. Runs it --dry-run (every command
# printed, nothing run or written) as BEFORE has it and as the working tree has it, on painted, pixel, a --kinds list,
# no variants, --keep and given colours and sky, and diffs what they print. Exit 1 on any difference.
#   tools/r305-ai-page-split/same.sh [BEFORE.py]   (default: ai_page.py at HEAD, which must know --dry-run)
# headless: prints commands only, nothing is painted, no window.
set -u
cd "$(dirname "$0")/../.." || exit 1
DIR=tools/r239-ai-strips
# BEFORE runs from beside ai_page.py, so its commands name the same paths; a hidden name, removed on exit.
REF="$DIR/.r305-before.py"
if [ -n "${1:-}" ]; then cp "$1" "$REF"; else git show HEAD:$DIR/ai_page.py > "$REF"; fi
TMP=$(mktemp -d)
trap 'rm -rf "$TMP" "$REF"' EXIT
OUT=$TMP/out  # never made: --dry-run writes nothing
fail=0 n=0
while IFS='|' read -r name args; do
  [ -z "$name" ] && continue
  for side in before after; do
    script=$DIR/ai_page.py; [ $side = before ] && script=$REF
    # shellcheck disable=SC2086
    # argparse names the script in its usage: one name, its lines unindented, for both.
    python3 "$script" "$OUT" --dry-run $args 2>&1 | sed 's/\.r305-before\.py/ai_page.py/g; s/^ *//' > "$TMP/$name-$side.txt"
    echo "rc=${PIPESTATUS[0]}" >> "$TMP/$name-$side.txt"
  done
  n=$((n + 1))
  if diff "$TMP/$name-before.txt" "$TMP/$name-after.txt"; then
    echo "SAME $name: $(grep -c '' "$TMP/$name-after.txt") lines, $(tail -1 "$TMP/$name-after.txt")"
  else
    echo "DIFFERENT $name"; fail=1
  fi
done <<'CASES'
painted|--theme snowy-pine-valley --seed 3
pixel|--theme desert-ruins --style pixel --layers 5 --front ruins
kinds|--theme beach --kinds mountains,sea,dunes,palms --variants 2 --front-speed 0.1 --name beach
no-variants|--theme forest --layers 3 --variants 0 --tries 2
colours|--theme dusk --colours 8a93a8,5d7a8c,1c2e33 --sky 6d8fc4,dfe6ee --fog 0.3 --no-shots
keep|--theme snowy --keep --layers 2
bad-kind|--theme x --kinds mountains,castle
bad-layers|--theme x --layers 7
CASES
[ -d "$OUT" ] && { echo "FAIL: --dry-run wrote $OUT"; fail=1; }
[ $fail = 0 ] && echo "SAME: $n cases"
exit $fail
