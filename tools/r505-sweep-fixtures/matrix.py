"""r505: which sweep case kills which of r261's mutants, from the logs mutate.sh keeps.

    KEEP=DIR tools/r261-creature/mutate.sh [FIXTURES]
    python3 tools/r505-sweep-fixtures/matrix.py DIR

Prints each mutant with the cases that answer wrongly under it (a mutant no case kills is what mutate.sh calls
SURVIVED), then a smallest-greedy set of cases that still kills them all: what is worth committing as a fixture.
"""
import glob
import itertools
import os
import re
import sys


def wrong_cases(path):
    """The cases of one sweep log that answered wrongly."""
    with open(path) as f:
        lines = f.read().splitlines()
    return {m[1] for a, b in itertools.pairwise(lines) if "  WRONG" in b and (m := re.match(r"(?:FLAG|PASS|MISSING) (\S+)", a))}


def main():
    kills = {os.path.basename(p)[:-4]: wrong_cases(p) for p in sorted(glob.glob(sys.argv[1] + "/*.log"))
             if not p.endswith("control.log")}
    for name, cases in kills.items():
        print(f"{name:45} {len(cases):3}  {' '.join(sorted(cases)[:8])}")
    left, picked = {n for n, c in kills.items() if c}, []
    while left:
        best = max({c for n in left for c in kills[n]}, key=lambda c: sum(c in kills[n] for n in left))
        picked.append(best)
        left = {n for n in left if best not in kills[n]}
    print("survived:", [n for n, c in kills.items() if not c])
    print("cover:", " ".join(sorted(picked)))


if __name__ == "__main__":
    main()
