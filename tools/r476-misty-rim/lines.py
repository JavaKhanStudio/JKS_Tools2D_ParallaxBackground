"""lines.py CUT.png...: the pale lines a cut leaves in clear rows (r476): runs of 4 px or more, wrapping along X, of
alpha over 40 at most two rows thick, the rows over and under them under 15. One line a cut: how many, and the first
dozen as (row, first column, length). forest-inpaint's l3 had 4 (rows 331-332, either side of its loop) before r476."""
import sys

import numpy as np
from PIL import Image


def runs(path, n=4):
    a = np.asarray(Image.open(path).convert("RGBA"))[:, :, 3].astype(int)
    m = np.zeros(a.shape, bool)
    clear_over = (a[1:-3] < 15) | (a[:-4] < 15)
    clear_under = (a[3:-1] < 15) | (a[4:] < 15)
    m[2:-2] = (a[2:-2] > 40) & clear_over & clear_under & ((a[1:-3] < 15) | (a[3:-1] < 15))
    out, w = [], a.shape[1]
    for y in range(a.shape[0]):
        row, x = np.concatenate([m[y], m[y]]), 0
        while x < w:
            if not row[x]:
                x += 1
                continue
            e = x
            while e < x + w and row[e]:
                e += 1
            if e - x >= n:
                out.append((y, x, e - x))
            x = e
    return out


for p in sys.argv[1:]:
    r = runs(p)
    print(p, len(r), r[:12])
