#!/usr/bin/env bash
# r305: proves a change to tools/r239-ai-strips/ai_page.py runs the same commands. Runs it --dry-run (every command
# printed, nothing run or written) as BEFORE has it and as the working tree has it, on painted, pixel, a --kinds list,
# no variants, --keep and given colours and sky, and diffs what they print. Exit 1 on any difference.
#   tools/r305-ai-page-split/same.sh [BEFORE.py]   (default: ai_page.py at HEAD, which must know --dry-run)
# headless: prints commands only, nothing is painted, no window.
set -u
cd "$(dirname "$0")/../.." || exit 1
DIR=tools/r239-ai-strips
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
# BEFORE runs as ai_page.py in a mirror of the checkout (every entry a link, the strips dir a real one), so argparse
# names it and wraps its usage as the working tree's, and its commands name the same paths once the mirror's root is
# put back (r543: a longer name wrapped the usage elsewhere, and the error cases always differed).
ROOT=$(pwd) MIR=$TMP/mirror
mkdir -p "$MIR/$DIR"
for e in "$ROOT"/* "$ROOT"/.[!.]*; do [ "$(basename "$e")" = tools ] || ln -s "$e" "$MIR/"; done
for e in "$ROOT"/tools/*; do [ "$(basename "$e")" = r239-ai-strips ] || ln -s "$e" "$MIR/tools/"; done
for e in "$ROOT/$DIR"/*; do ln -s "$e" "$MIR/$DIR/"; done
REF="$MIR/$DIR/ai_page.py"
rm "$REF"
if [ -n "${1:-}" ]; then cp "$1" "$REF"; else git show HEAD:$DIR/ai_page.py > "$REF"; fi
OUT=$TMP/out  # never made: --dry-run writes nothing
fail=0 n=0
while IFS='|' read -r name args; do
  [ -z "$name" ] && continue
  for side in before after; do
    script=$DIR/ai_page.py; [ $side = before ] && script=$REF
    # shellcheck disable=SC2086
    python3 "$script" "$OUT" --dry-run $args 2>&1 | sed "s#$MIR#$ROOT#g" > "$TMP/$name-$side.txt"
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
