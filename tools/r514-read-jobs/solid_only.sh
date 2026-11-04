#!/usr/bin/env bash
# headless: numpy on PNGs, no window
# solid_only.sh — r514: r512's log counts "solid only (alpha >= 0.95)": 1 wrong answer, without naming it. r505 says
# sweep.sh's half_bird. Runs that one mutant through the sweep and prints the wrong case, so the two can be compared.
set -u
cd "$(dirname "$0")/../.." || exit 1
M=$(mktemp -d)
trap 'rm -rf "$M"' EXIT
mkdir -p "$M/tools" && cp tools/parallax_rules.py "$M/tools/" && cp -r tools/r239-ai-strips "$M/tools/"
python3 - "$M/tools/r239-ai-strips/creature.py" <<'PY' || exit 2
import sys
p = sys.argv[1]
with open(p) as f:
    s = f.read()
old = "solid = spread(alpha >= 0.5, -4)"
assert old in s, old
with open(p, "w") as f:
    f.write(s.replace(old, "solid = spread(alpha >= 0.95, -4)"))
PY
R239_DIR="$M/tools/r239-ai-strips" tools/r261-creature/sweep.sh tools/r261-creature/fixtures >"$M/log" 2>&1
rc=$?
grep -B1 "  WRONG" "$M/log"
echo "solid only: sweep exit $rc, $(grep -c '  WRONG' "$M/log") wrong answer(s)"
