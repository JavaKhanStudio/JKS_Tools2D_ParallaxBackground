"""r383: what a layer.py change cuts off a strip anywhere, not only in area.py's solid zone: a palm's crown sits at the
mask's edge, where area.py does not look.

    python3 tools/r383-crowns/lost.py BEFORE AFTER PAGE...

BEFORE and AFTER are recut.sh OUTs, or "own": each page's strips/<strip>_cut.png, the cut it shipped with.
Per strip, the opaque px (alpha >= 0.5) before and after, the px lost, and the largest 4-connected piece lost: a crown
cut off is one piece of hundreds of px, a keyed ghost or a pale edge many small ones. Sorted by that piece.
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "r239-ai-strips"))
import layer


def opaque(root, page, strip):
    cell = os.path.basename(page.rstrip("/"))
    p = os.path.join(page, "strips", strip + "_cut.png") if root == "own" else os.path.join(root, cell, strip + ".png")
    return np.asarray(Image.open(p).convert("RGBA"))[..., 3] >= 128 if os.path.exists(p) else None


def main():
    before, after, pages = sys.argv[1], sys.argv[2], sys.argv[3:]
    rows = []
    for e in pages:
        cell = os.path.basename(e.rstrip("/"))
        listed = after if after != "own" else before
        if not os.path.isdir(os.path.join(listed, cell)):
            continue
        for f in sorted(os.listdir(os.path.join(listed, cell))):
            s = f[:-4]
            b, a = opaque(before, e, s), opaque(after, e, s)
            if b is None or a is None or b.shape != a.shape:
                continue
            lost = b & ~a
            _, sizes = layer.components(lost)
            rows.append((max(sizes, default=0), f"{cell}/{s}", int(b.sum()), int(a.sum()), int(lost.sum())))
    print("strip\topaque before\topaque after\tlost\tlargest piece lost")
    for big, name, nb, na, nl in sorted(rows, reverse=True):
        print(f"{name}\t{nb}\t{na}\t{nl}\t{big}")


if __name__ == "__main__":
    main()
