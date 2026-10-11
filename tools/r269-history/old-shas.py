#!/usr/bin/env python3
"""old-shas.py: find (or --fix) commit SHAs from before r269's history rewrite quoted in tracked files (r289).

commit-map.txt beside this script is the map of the history Simon force-pushed (r285, the --wide run): old SHA, new SHA,
0000... for a commit dropped (it touched only editor/ or demo/). A 7-40 hex token that starts an old SHA, and no new
one, is an old quote. --fix replaces it by the new SHA cut to the same length; a dropped one is only reported.
Kept old on purpose (KEEP): rewrite.sh, which reads the old mirror, and .atelier/routing-eval.json, which copies board
cards' text as it was. The cards themselves no longer quote old SHAs: purge-board-shas.py blanked them (r334).

  tools/r269-history/old-shas.py [--fix] [--skip PATH ...]
"""

import argparse
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
KEEP = ("tools/r269-history/commit-map.txt", "tools/r269-history/rewrite.sh", ".atelier/routing-eval.json")
TOKEN = re.compile(r"\b[0-9a-f]{7,40}\b")


def load_map():
    pairs = {}
    for line in (HERE / "commit-map.txt").read_text().splitlines()[1:]:
        old, new = line.split()
        if old != new:
            pairs[old] = new
    return pairs


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fix", action="store_true")
    ap.add_argument("--skip", nargs="*", default=[])
    args = ap.parse_args()
    pairs = load_map()
    news = set(pairs.values())
    skip = {*KEEP, *args.skip}
    files = subprocess.run(["git", "ls-files"], cwd=ROOT, capture_output=True, text=True, check=True).stdout.split()
    left = 0
    for name in files:
        if name in skip:
            continue
        path = ROOT / name
        try:
            text = path.read_text()
        except (UnicodeDecodeError, OSError):
            continue

        def swap(m, name=name, text=text):
            nonlocal left
            tok = m.group(0)
            olds = [o for o in pairs if o.startswith(tok)]
            if len(olds) != 1 or any(n.startswith(tok) for n in news):
                return tok
            new = pairs[olds[0]]
            line = text.count("\n", 0, m.start()) + 1
            if set(new) == {"0"}:
                print(f"{name}:{line}: {tok} was dropped by the rewrite: no new SHA")
                left += 1
                return tok
            print(f"{name}:{line}: {tok} -> {new[: len(tok)]}")
            if not args.fix:
                left += 1
                return tok
            return new[: len(tok)]

        fixed = TOKEN.sub(swap, text)
        if args.fix and fixed != text:
            path.write_text(fixed)
    return 1 if left else 0


if __name__ == "__main__":
    sys.exit(main())
