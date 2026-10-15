#!/usr/bin/env python3
"""depth_page.sky_twigs (r378) on what a depth-page run left in OUT/cut, no GPU: each layer's own pixels it would hand
to the hole, on every layer (the rule itself runs on the back layer only).

    ~/ComfyUI/venv/bin/python tools/r378-rules/offline.py build/ai/depth/valley [MORE RUNS] --out DIR

Prints the px and pieces found per layer and writes DIR/<run>_l<k>.png: the painting dimmed, the hole purple, what
it found yellow. The hole is rebuilt from bands.png without the islands step, so it can be a little smaller than
the run's.
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "r279-depth-page"))
import depth_page as dp

PAL = np.array([(70, 90, 160), (90, 160, 120), (220, 180, 80), (220, 90, 70), (200, 80, 200), (240, 240, 240)])


def labels(cut):
    b = np.asarray(Image.open(os.path.join(cut, "bands.png")).convert("RGB")).astype(int)
    return ((b[:, :, None, :] - PAL[None, None]) ** 2).sum(-1).argmin(-1)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("runs", nargs="+")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    for run in a.runs:
        cut = os.path.join(run, "cut")
        img = np.asarray(Image.open(os.path.join(cut, "painting.png")).convert("RGB"))
        dn = np.asarray(Image.open(os.path.join(cut, "depth.png")).convert("L")) / 255.0
        L = labels(cut)
        n = int(L.max()) + 1
        for k in range(n - 1):
            alpha = np.ones(L.shape, bool) if k == 0 else dp.alpha_of(L, k)
            hole = alpha & (dp.grow(L > k, 6) | (L < k))
            found = dp.sky_twigs(img, dn, alpha & ~hole, hole)
            print(f"{run} l{k}: {int(found.sum())} px, {ndimage.label(found)[1]} pieces")
            out = img.astype(np.float32) * 0.4
            out[hole] = (80, 0, 80)
            out[found] = (255, 255, 0)
            Image.fromarray(out.astype(np.uint8)).save(os.path.join(a.out, f"{os.path.basename(run)}_l{k}.png"))


if __name__ == "__main__":
    main()
