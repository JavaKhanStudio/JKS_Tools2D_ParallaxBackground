#!/usr/bin/env python3
"""Parts of a depth-page layer that hang from its mass on a thin neck (r359, for r379): each layer's alpha_of() opened
by R px (4-connected), the pieces it then cuts apart from the mass listed and boxed on the painting.

    ~/ComfyUI/venv/bin/python tools/r359-pieces/necks.py build/ai/depth/river 4 OUT.png

Reads the run's cut/bands.png (depth_page.colour_bands' colours, 4 bands) and cut/painting.png; no GPU.
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "r279-depth-page"))
import depth_page as dp

PAL = np.array([(70, 90, 160), (90, 160, 120), (220, 180, 80), (220, 90, 70)])


def main():
    run, r, out = sys.argv[1], int(sys.argv[2]), sys.argv[3]
    b = np.asarray(Image.open(os.path.join(run, "cut", "bands.png")).convert("RGB")).astype(int)
    L = np.argmin(((b[..., None, :] - PAL[None, None]) ** 2).sum(-1), axis=2)
    img = Image.open(os.path.join(run, "cut", "painting.png")).convert("RGB")
    draw = ImageDraw.Draw(img)
    w = L.shape[1]
    cross = ndimage.generate_binary_structure(2, 1)
    for k in range(1, len(PAL)):
        opened = ndimage.binary_opening(np.tile(dp.alpha_of(L, k), (1, 3)), cross, iterations=r)
        lab, count = ndimage.label(opened)
        sizes = ndimage.sum(np.ones(lab.shape), lab, range(1, count + 1))
        for i, sl in enumerate(ndimage.find_objects(lab)):
            if sizes[i] < sizes.max() and w <= sl[1].start < 2 * w:
                x0, x1 = sl[1].start - w, sl[1].stop - w
                print(f"l{k}: {int(sizes[i])} px at row {sl[0].start}, column {x0}")
                draw.rectangle([x0 - 2, sl[0].start - 2, x1 + 2, sl[0].stop + 2], outline=tuple(int(v) for v in PAL[k]),
                               width=2)
    img.save(out)


if __name__ == "__main__":
    main()
