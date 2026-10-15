#!/usr/bin/env python3
"""r380: what bands.py's bands give each layer, before against after, with no repaint: per layer k, its opaque
pixels (depth_page.alpha_of) of the sky's colour (see_through.py's: blue over red by 40, over green by 15), and the
painting's leaf-green pixels (green over red by 10, over blue by 5) the before covered and the after no longer does.

    ~/ComfyUI/venv/bin/python tools/r380-twig-gaps/counts.py BEFORE_DIR AFTER_DIR [--crop K X0 Y0 X1 Y1 OUT.png] [--diff OUT.png]

The near layer's own pixels are the painting's (its hole is empty), so its count is see_through.py's on a full run.
--diff draws the painting over itself with the pixels whose band changed in magenta, and prints from which bands to
which. --crop draws layer K's opaque pixels over magenta, before above after, enlarged 3 times.
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "r279-depth-page"))
import depth_page as dp


def alphas(run):
    L = np.load(os.path.join(run, "L.npy"))
    return [dp.alpha_of(L, k) for k in range(1, int(L.max()) + 1)]


def main():
    before, after = sys.argv[1], sys.argv[2]
    img = np.asarray(Image.open(os.path.join(after, "cut", "painting.png")).convert("RGB")).astype(int)
    r, g, b = img[..., 0], img[..., 1], img[..., 2]
    sky = (b > r + 40) & (b > g + 15)
    green = (g > r + 10) & (g > b + 5)
    for k, (was, now) in enumerate(zip(alphas(before), alphas(after)), 1):
        lost = was & ~now & green
        print(f"l{k}: sky {int((was & sky).sum()):6d} -> {int((now & sky).sum()):6d}   opaque {int(was.sum()):6d} -> "
              f"{int(now.sum()):6d}   green lost {int(lost.sum()):5d} of {int((was & green).sum()):6d} "
              f"({lost.sum() / max(1, (was & green).sum()):.2%})")
    if "--diff" in sys.argv:
        a, b = (np.load(os.path.join(run, "L.npy")) for run in (before, after))
        ch = a != b
        print(f"   changed {int(ch.sum())} px, from bands {np.bincount(a[ch], minlength=4)} to {np.bincount(b[ch], minlength=4)}")
        Image.fromarray(np.vstack([img, np.where(ch[..., None], [255, 0, 255], img)]).astype(np.uint8)).save(
            sys.argv[sys.argv.index("--diff") + 1])
    if "--crop" in sys.argv:
        i = sys.argv.index("--crop")
        k, x0, y0, x1, y1 = (int(v) for v in sys.argv[i + 1:i + 6])
        tiles = []
        for a in (alphas(before)[k - 1], alphas(after)[k - 1]):
            t = np.where(a[..., None], img, [255, 0, 255])[y0:y1, x0:x1].astype(np.uint8)
            tiles.append(np.kron(t, np.ones((3, 3, 1), np.uint8)))
        gap = np.full((6, tiles[0].shape[1], 3), 255, np.uint8)
        Image.fromarray(np.vstack([tiles[0], gap, tiles[1]])).save(sys.argv[i + 6])


if __name__ == "__main__":
    main()
