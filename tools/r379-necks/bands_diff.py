#!/usr/bin/env python3
"""What a depth_page.py change moved between two runs of one painting (r379): each pixel whose band differs, counted
by from->to band, and painted on the after run's painting (magenta: to a farther band, cyan: to a nearer one).

    python3 tools/r379-necks/bands_diff.py BEFORE AFTER OUT.png

Reads each run's cut/bands.png (depth_page.colour_bands' colours, 4 bands) and AFTER's cut/painting.png; no GPU.
"""
import sys

import numpy as np
from PIL import Image

PAL = np.array([(70, 90, 160), (90, 160, 120), (220, 180, 80), (220, 90, 70)])


def bands(run):
    b = np.asarray(Image.open(run + "/cut/bands.png").convert("RGB")).astype(int)
    return np.argmin(((b[..., None, :] - PAL[None, None]) ** 2).sum(-1), axis=2)


def main():
    before, after, out = sys.argv[1:4]
    a, b = bands(before), bands(after)
    img = np.asarray(Image.open(after + "/cut/painting.png").convert("RGB")).copy()
    ch = a != b
    print("changed", int(ch.sum()))
    for i in range(len(PAL)):
        for j in range(len(PAL)):
            n = int((ch & (a == i) & (b == j)).sum())
            if n:
                print(f"  {i}->{j}: {n}")
    img[ch & (b < a)] = (255, 0, 255)
    img[ch & (b > a)] = (0, 255, 255)
    Image.fromarray(img).save(out)


if __name__ == "__main__":
    main()
