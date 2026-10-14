#!/usr/bin/env python3
"""The bands of a depth-page run, as depth cut them and with SAM's masks given whole (r360), from what the run left in
OUT/cut (painting.png, depth.png): no repaint, one SAM pass.

    ~/ComfyUI/venv/bin/python tools/r360-band-edges/sam_bands.py build/ai/depth/river build/r360/sam

Writes DIR/<name>_depth.png and DIR/<name>_sam.png (the painting under its bands, depth_page.py's colours), and
DIR/<name>_objects.png (the masks), and prints the share of pixels that changed band. depth.png is the run's depth
quantised to 8 bits: a band's edge may move a pixel from the run's own bands.png.
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "r279-depth-page"))
import depth_page as dp


def main():
    run, out = sys.argv[1], sys.argv[2]
    name = os.path.basename(os.path.normpath(run))
    os.makedirs(out, exist_ok=True)
    img = np.asarray(Image.open(os.path.join(run, "cut", "painting.png")).convert("RGB"))
    dn = np.asarray(Image.open(os.path.join(run, "cut", "depth.png"))).astype(np.float32) / 255
    h, w = dn.shape
    n = 4
    L, _ = dp.bands(dn, n, w * h // 150)
    masks = dp.masks_of(img)
    dp.outlines(img, masks).save(os.path.join(out, f"{name}_objects.png"))
    cuts = {}
    for tag, start in (("depth", L), ("sam", dp.objects(L, masks, n))):
        cuts[tag] = dp.strays(dp.by_colour(start, img, n), n, w * h // 150)
        b = np.asarray(dp.colour_bands(cuts[tag], n)).astype(np.float32)
        Image.fromarray((img * 0.5 + b * 0.5).astype(np.uint8)).save(os.path.join(out, f"{name}_{tag}.png"))
    print(f"{name}: {len(masks)} masks, {(cuts['depth'] != cuts['sam']).mean():.2%} of the pixels changed band")


if __name__ == "__main__":
    main()
