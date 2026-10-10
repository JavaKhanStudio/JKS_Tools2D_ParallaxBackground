"""A hole the island cut punched in haze, at given points of a cut strip (r264).

    python3 tools/r264-unspeck/holes.py cut.png kept.png X,Y [X,Y ...]

kept.png: layer.py's cut of the same strip with --keep-islands. In the 9x9 box around each point, a hole is a pixel
the cut left clear (alpha < 0.02) that kept.png has as haze (0.1 to 0.5): the speck's own pixels (over 0.5) and the
sky (clear in both) are not. For points in haze; a speck alone in clear sky has its faint edge cleared, as it should.
Exits 1 on a hole.
"""
import argparse
import sys

import numpy as np
from PIL import Image


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cut")
    ap.add_argument("kept")
    ap.add_argument("at", nargs="+")
    a = ap.parse_args()
    cut, kept = (np.asarray(Image.open(f).convert("RGBA"))[..., 3] / 255.0 for f in (a.cut, a.kept))
    bad = 0
    for p in a.at:
        x, y = map(int, p.split(","))
        box = np.s_[y - 4:y + 5, x - 4:x + 5]
        n = int(((cut[box] < 0.02) & (kept[box] >= 0.1) & (kept[box] <= 0.5)).sum())
        print(f"{a.cut} {x},{y}: {n} px cleared in haze")
        bad |= n > 0
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
