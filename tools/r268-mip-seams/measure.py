#!/usr/bin/env python3
"""r268: the worst line at a tile join in each row of make.py's page, from its stills: per layer, the darkest column
of its band (mean over the band's rows) against the band's median column, in /255. The art is constant along x, so
the inside of a tile reads 0; a join that mixes in the transparent padding reads as a dip.

    python3 tools/r268-mip-seams/measure.py OUT [--max N]

--max N: exit 1 when a row with 25 texels duplicated dips more than N (that packing must stay clean), and when a row
with no edge duplicated dips LESS than N at mip level 3 or deeper (the round must still see the seam it measures).
"""
import glob
import os
import sys

import numpy as np
from PIL import Image



def bands(img):
    """(top, bottom) screen rows of each layer, top to bottom: rows brighter than the dark page."""
    lit = (img.max(axis=2) > 40).mean(axis=1) > 0.5
    out, start = [], None
    for y, v in enumerate(list(lit) + [False]):
        if v and start is None:
            start = y
        elif not v and start is not None:
            out.append((start, y))
            start = None
    return out


def rows_of(out):
    """The rows' names, top to bottom, from the atlas make.py wrote: bN x4, x8, x16 for each region."""
    names = [line.strip() for line in open(os.path.join(out, "mip.atlas")) if line.strip().startswith("b")]
    return [f"{n} x{s}" for n in names for s in (4, 8, 16)]


def dips(path, rows):
    img = np.asarray(Image.open(path).convert("RGB"), float)
    found = bands(img)
    if len(found) != len(rows):
        sys.exit(f"{path}: {len(found)} bands found, {len(rows)} expected")
    out = []
    for top, bottom in found:
        # Leave out the band's first and last rows: they are the layer's top and bottom edges, not its joins.
        cols = img[top + 1:bottom - 1].mean(axis=(0, 2)) if bottom - top > 2 else img[top:bottom].mean(axis=(0, 2))
        out.append(float(np.median(cols) - cols.min()))
    return out


def main(out, limit):
    rows = rows_of(out)
    worst = [0.0] * len(rows)
    for path in sorted(glob.glob(os.path.join(out, "shots", "*-t*.png"))):
        worst = [max(w, d) for w, d in zip(worst, dips(path, rows))]
    bad = []
    for name, d in zip(rows, worst):
        print(f"{name:10s} worst dip at a join {d:6.1f}/255")
        if limit is not None and name.startswith("b25 ") and d > limit:
            bad.append(f"{name} dips {d:.1f} > {limit}")
        if limit is not None and name in ("b0 x8", "b0 x16") and d < limit:
            bad.append(f"{name} dips only {d:.1f}: the round no longer sees the seam")
    for b in bad:
        print("FAIL", b)
    return 1 if bad else 0


if __name__ == "__main__":
    a = sys.argv[1:]
    lim = None
    if "--max" in a:
        i = a.index("--max")
        lim = float(a[i + 1])
        del a[i:i + 2]
    sys.exit(main(a[0], lim))
