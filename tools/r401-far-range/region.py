#!/usr/bin/env python3
"""r401: the pale far range SD painted round a strip's shape, as one region, for mocking Simon's three answers.

    python3 tools/r401-far-range/region.py PAGE STRIP CUT OUT.png [--t 0.6] [--grow G]

A pixel is in the region when it lies within G px of the strip's mask (layer.py's --grow: past it, the key clears
everything), is painted (more than 14 from the sky, the strip's top rows), and lies under T of the way from the sky to
the mass's median colour (layer.py's sky_to_mass on CUT's alpha); pieces under 150 px are dropped. G defaults to the
strip's kind's in ai_page.GROW (recipe.json). Writes OUT.png, the painting with the region tinted, and OUT.npy.
A mockup's measure, not a key rule.
"""
import argparse
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "r239-ai-strips"))
import layer


def grow_of(page, strip):
    import ai_page
    with open(os.path.join(page, "recipe.json")) as f:
        r = json.load(f)
    kinds = [lay["kind"] for lay in r["layers"]]
    return ai_page.GROW[kinds[int(strip[1])]]


def region(page, strip, cut, t_max, grow=None):
    grow = grow_of(page, strip) if grow is None else grow
    img = np.asarray(Image.open(f"{page}/strips/{strip}_ai.png").convert("RGB"), np.float64)
    alpha = np.asarray(Image.open(cut).convert("RGBA"), np.float64)[..., 3] / 255
    mask = np.asarray(Image.open(f"{page}/strips/{strip}_mask.png").convert("L")) > 127
    sky = np.median(img[:8].reshape(-1, 3), axis=0)
    t = layer.sky_to_mass(img, sky, alpha)
    r = layer.spread(mask, grow) & (np.linalg.norm(img - sky, axis=-1) > 14) & (t < t_max)
    label, sizes = layer.components(r)
    return img, np.isin(label, [k + 1 for k, n in enumerate(sizes) if n >= 150])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("page")
    ap.add_argument("strip")
    ap.add_argument("cut")
    ap.add_argument("out")
    ap.add_argument("--t", type=float, default=0.6)
    ap.add_argument("--grow", type=int)
    a = ap.parse_args()
    img, r = region(a.page, a.strip, a.cut, a.t, a.grow)
    tint = img.copy()
    tint[r] = tint[r] * 0.5 + np.array([255, 0, 255]) * 0.5
    Image.fromarray(tint.astype(np.uint8)).save(a.out)
    np.save(a.out[:-4] + ".npy", r)
    print(f"{a.out}: {int(r.sum())} px")


if __name__ == "__main__":
    main()
