#!/usr/bin/env bash
# r532: proves a refactor of layer.py (the cut) and creature.py (the figure check) changes nothing they write. Runs
# both as REF has them and as the working tree has them on every painted strip under STRIPS_ROOT (its */l<k>_ai.png
# with their _mask.png and _cut.png, e.g. build/r496 from tools/r496-warp/compare.py), and compares the cut, its rules
# record, what each prints, and creature.py's verdict. Exit 1 on any difference. No GPU.
#   tools/r532-ai-split/same-cuts.sh STRIPS_ROOT [REF]   (REF default HEAD)
# headless: image files only, no window.
set -u
root=${1:?STRIPS_ROOT}; ref=${2:-HEAD}
cd "$(dirname "$0")/../.." || exit 1
D=tools/r239-ai-strips
B=$(mktemp -d); T=$(mktemp -d)
trap 'rm -rf "$B" "$T"' EXIT
# REF's copies run from a copy of the directory and of tools/*.py (parallax_rules), so they import REF's siblings.
mapfile -t top < <(git ls-tree --name-only "$ref" tools/ | grep '\.py$')
git archive "$ref" "$D" "${top[@]}" | tar -x -C "$B"
n=0 bad=0
while IFS= read -r ai; do
  s=${ai%_ai.png}
  [ -f "${s}_mask.png" ] && [ -f "${s}_cut.png" ] || continue
  n=$((n + 1))
  for side in before after; do
    dir=$D; [ $side = before ] && dir=$B/$D
    mkdir -p "$T/$side"
    # one output path for both sides: layer.py names it in what it prints
    python3 "$dir/layer.py" "$ai" "$T/cut.png" --mask "${s}_mask.png" --grow 16 --kind ruins \
      --ckpt DreamShaperXL_Turbo.safetensors --rules-json "$T/rules.json" > "$T/$side/out" 2> "$T/$side/err"
    mv "$T/cut.png" "$T/rules.json" "$T/$side/"
    python3 "$dir/creature.py" "${s}_cut.png" --src "$ai" > "$T/$side/creature" 2>&1
    echo "rc=$?" >> "$T/$side/creature"
  done
  if diff -r "$T/before" "$T/after" > /dev/null; then :; else echo "DIFFERENT $s"; diff -r "$T/before" "$T/after" | head -5; bad=1; fi
done < <(find "$root" -name '*_ai.png' | sort)
echo "$n strips: $([ $bad = 0 ] && echo SAME || echo DIFFERENT)"
[ "$n" -gt 0 ] || { echo "no strips under $root"; exit 1; }
exit $bad
