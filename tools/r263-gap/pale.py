"""Pale opaque pixels near a point of a cut strip (r263): what a gap's foot left opaque looks like.

    python3 tools/r263-gap/pale.py painted.png cut.png X,Y [X,Y ...] [--r 12]

A pixel is pale when its painted colour is nearer the sky than the layer's mass along the line between them (layer.py's
ungap t < 0.5) and opaque when the cut's alpha is >= 0.95. Prints the count within R px of each point; exits 1 when
any point has one.
"""
import argparse
import sys

import numpy as np
from PIL import Image


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("cut")
    ap.add_argument("at", nargs="+")
    ap.add_argument("--r", type=int, default=12)
    a = ap.parse_args()
    img = np.asarray(Image.open(a.src).convert("RGB")).astype(np.float64)
    alpha = np.asarray(Image.open(a.cut).convert("RGBA"))[..., 3] / 255.0
    sky = np.median(img[:8].reshape(-1, 3), axis=0)
    axis = np.median(img[alpha > 0.95], axis=0) - sky
    t = (img - sky) @ axis / (axis @ axis)
    pale = (t < 0.5) & (alpha >= 0.95)
    bad = 0
    for p in a.at:
        x, y = map(int, p.split(","))
        n = int(pale[max(y - a.r, 0):y + a.r + 1, max(x - a.r, 0):x + a.r + 1].sum())
        print(f"{a.cut} {x},{y}: {n} pale opaque px")
        bad |= n > 0
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
