#!/usr/bin/env python3
"""Did a still lose a layer? (r508) Each still of tools/r508-stills/repeat.sh against the median of its name in its arm: the clock is
stepped, so the same name is the same picture, and a drop is the one that is not.
  python3 tools/r508-stills/count.py OUT      writes OUT/<arm>/drops.txt, prints the count; exit 1 when the noaa arm has one.
A still is counted when more than OFF_SHARE of its pixels are over OFF/255 from the median (r507's smallest drop: 4.5% of a frame).
"""
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np
from PIL import Image

OFF, OFF_SHARE = 8, 0.001
MEDIAN_OF = 9


def load(path):
    return np.asarray(Image.open(path).convert("RGB")).astype(np.int16)


def count(arm):
    names = defaultdict(list)
    for path in sorted(arm.glob("round*/*.png")):
        names[path.name].append(path)
    if not names:
        sys.exit(f"{arm}: no stills")
    drops, total = [], 0
    worst_clean = 0.0
    for name, paths in sorted(names.items()):
        median = np.median(np.stack([load(p) for p in paths[:MEDIAN_OF]]), axis=0)
        for path in paths:
            total += 1
            off = (np.abs(load(path) - median).max(axis=2) > OFF).mean()
            if off > OFF_SHARE:
                drops.append(f"{path.relative_to(arm)} {100 * off:.2f}% of its pixels")
            else:
                worst_clean = max(worst_clean, off)
    (arm / "drops.txt").write_text("".join(d + "\n" for d in drops))
    failed = arm / "failed.txt"
    print(f"{arm.name}: {len(drops)} of {total} stills off their median, {len(names)} names; worst one under the line {100 * worst_clean:.3f}%; "
          f"runs failed: {len(failed.read_text().splitlines()) if failed.exists() else 0}")
    for d in drops:
        print("  " + d)
    return len(drops)


out = Path(sys.argv[1])
arms = {a: count(out / a) for a in ("aa", "noaa") if (out / a).exists()}
sys.exit(1 if arms.get("noaa") else 0)
