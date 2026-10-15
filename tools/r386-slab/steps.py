#!/usr/bin/env python3
"""r386's probe: depth_page.depth_bands replayed pass by pass on a cache (tools/r380-twig-gaps/bands.py cache), and
after each pass, layer K's alpha and hole: which pass stands a hole over the layer's own top.

    ~/ComfyUI/venv/bin/python tools/r386-slab/steps.py CACHE.npz OUT_DIR [--k 2] [--box X0 Y0 X1 Y1]
        [--depth-page PATH]

Prints, per pass, the hole's pixels in the box and the highest hole row there; writes OUT_DIR/NN-pass.png: the
layer's own pixels grey, its hole red, over the painting dimmed.
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "r380-twig-gaps"))
import bands as harness  # noqa: E402


def main():
    p = argparse.ArgumentParser()
    p.add_argument("cache")
    p.add_argument("out")
    p.add_argument("--k", type=int, default=2)
    p.add_argument("--box", type=int, nargs=4, default=(390, 230, 510, 430))
    p.add_argument("--depth-page", default=harness.DEFAULT)
    a = p.parse_args()
    dp = harness.load(os.path.abspath(a.depth_page))
    c = np.load(a.cache)
    img, d, masks, fields = c["img"], c["d"], list(c["masks"]), list(c["fields"])
    h, w = img.shape[:2]
    n, k = 4, a.k
    x0, y0, x1, y1 = a.box
    os.makedirs(a.out, exist_ok=True)
    lo, hi = np.percentile(d, (1, 99.5))
    dn = np.clip((d - lo) / (hi - lo), 0, 1)
    L, centres = dp.bands(dn, n, min_area=w * h // 150)
    steps = [("bands", lambda L: L), ("objects", lambda L: dp.objects(L, masks, n)),
             ("fields", lambda L: dp.with_fields(L, dp.agreeing(L, fields, n), n)),
             ("by_colour", lambda L: dp.by_colour(L, img, n)),
             ("see_through", lambda L: dp.see_through(L, dn, centres, img, n)),
             ("strays", lambda L: dp.strays(L, n, w * h // 150)), ("loose", lambda L: dp.loose(L, n)),
             ("necks", lambda L: dp.loose(dp.necks(L, dn, n), n))]
    for i, (name, fn) in enumerate(steps):
        L = fn(L) if i else L
        alpha = dp.alpha_of(L, k)
        hole = alpha & (dp.grow(L > k, 3) | (L < k))
        box = hole[y0:y1, x0:x1]
        rows = np.nonzero(box.any(axis=1))[0]
        top = dp.topline(L, k)[x0:x1]
        print(f"{i:02d} {name:12s} hole in box {int(box.sum()):6d} px, highest row {y0 + rows[0] if len(rows) else '-'}"
              f", topline min {top.min():.0f}, band {k} px in box {int((L[y0:y1, x0:x1] == k).sum())}")
        o = img.astype(np.float32) * 0.35
        o[alpha & ~hole] = (150, 150, 150)
        o[(L == k) & ~hole] = (200, 200, 200)
        o[hole] = (220, 40, 40)
        o[y0, x0:x1] = o[y1 - 1, x0:x1] = (0, 255, 0)
        o[y0:y1, x0] = o[y0:y1, x1 - 1] = (0, 255, 0)
        Image.fromarray(o.astype(np.uint8)).save(os.path.join(a.out, f"{i:02d}-{name}.png"))
        np.save(os.path.join(a.out, f"{i:02d}-{name}.npy"), L)


if __name__ == "__main__":
    main()
