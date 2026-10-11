#!/usr/bin/env bash
# r303: proves a change to the fixture generators changes nothing they write. Runs each generator as it is at REF
# (default HEAD) and as it is in the working tree, each in its own scratch copy of the files they read and write
# (never over core/test-data: AGENTS.md), then diff -r the two copies. Exit 1 on any difference.
#   tools/r303-sim115/same.sh [REF]      (needs numpy and Pillow; from the repository root)
set -euo pipefail
ROOT=$(git rev-parse --show-toplevel)
REF=${1:-HEAD}
GENS=(core/test-data/sequence/make_round.py core/test-data/shaders/make_round.py core/test-data/haze/make_lab.py
      core/test-data/haze/make_round.py core/test-data/floor/make_lab.py core/test-data/floor/make_round.py
      tools/r262-fog-seam-y/make.py tools/r273-wave-seam-y/make.py)
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
for side in old new; do
  mkdir -p "$TMP/$side"
  git -C "$ROOT" archive "$REF" core/test-data engines/rounds tools/r262-fog-seam-y tools/r273-wave-seam-y \
    | tar -x -C "$TMP/$side"
done
touch "$TMP/stamp"
for g in "${GENS[@]}"; do
  git -C "$ROOT" show "$REF:$g" > "$TMP/old/$g"
  cp "$ROOT/$g" "$TMP/new/$g"
  for side in old new; do
    # haze/make_lab.py reads a page from git history: point git at this repository.
    (cd "$TMP/$side" && GIT_DIR="$ROOT/.git" python3 "$g" > /dev/null) || { echo "FAIL: $g ($side) did not run"; exit 1; }
  done
  echo "ran $g"
done
# What the new side wrote, the generators themselves left out: a run that wrote nothing proves nothing.
wrote=$(find "$TMP/new" -type f -newer "$TMP/stamp" ! -name 'make*.py' | wc -l)
[ "$wrote" -gt 0 ] || { echo "FAIL: the generators wrote nothing"; exit 1; }
if diff -r -x 'make_*.py' -x make.py "$TMP/old" "$TMP/new" > "$TMP/diff"; then
  echo "SAME: ${#GENS[@]} generators wrote $wrote files, identical at $REF and in the working tree"
else
  head -40 "$TMP/diff"; echo "DIFFERENT"; exit 1
fi
