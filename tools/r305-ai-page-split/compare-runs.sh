#!/usr/bin/env bash
# r305: two real ai_page.py runs with the same arguments into BEFORE_OUT and AFTER_OUT (one before a change, one
# after): every image byte for byte, every text file (page, atlas, round.json, recipe.json, logs) with each run's
# own directory read as OUT, gen.py's progress bars left out. Exit 1 on any difference.
#   tools/r305-ai-page-split/compare-runs.sh BEFORE_OUT AFTER_OUT
# headless: compares files, opens nothing.
set -u
B=$(realpath "${1:?usage: compare-runs.sh BEFORE_OUT AFTER_OUT}") A=$(realpath "${2:?}")
fail=0 n=0
TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT
norm() { sed "s#$1#OUT#g; s#${1#/}#OUT#g; /it\/s\]/d" "$2"; }
diff <(cd "$B" && find . -type f | sort) <(cd "$A" && find . -type f | sort) || { echo "DIFFERENT file lists"; exit 1; }
while read -r f; do
  n=$((n + 1))
  case "$f" in
    *.png) cmp -s "$B/$f" "$A/$f" || { echo "DIFFERENT $f"; fail=1; } ;;
    # gen.py's logs carry the sampler's progress bar, timed: its lines are left out.
    *) diff <(norm "$B" "$B/$f") <(norm "$A" "$A/$f") > "$TMP" || { echo "DIFFERENT $f"; head -c 600 "$TMP"; fail=1; } ;;
  esac
done < <(cd "$B" && find . -type f | sort)
[ $fail = 0 ] && echo "SAME: $n files ($(cd "$B" && find . -name '*.png' | wc -l) images byte for byte)"
exit $fail
