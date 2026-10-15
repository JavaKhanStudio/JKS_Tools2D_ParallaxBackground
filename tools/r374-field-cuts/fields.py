#!/usr/bin/env python3
"""The bands of a depth-page run cut without and with r374's prompted fields (depth_page.fields_of), from what the run
left in OUT/cut (painting.png, depth.png): no repaint, SAM's automatic masks and the prompted fields.

    ~/ComfyUI/venv/bin/python tools/r374-field-cuts/fields.py build/ai/depth/river build/r374/cut

Writes DIR/<name>_before.png and DIR/<name>_after.png (the painting under its bands, depth_page.py's colours, each slab cut
left in white; the labels in .npy beside them),
DIR/<name>_fields.png (the prompted masks), and prints, per slab cut left (a cut through one surface), how many columns
it still runs: 0 when every field's cut moved onto a painted line. depth.png is the run's depth quantised to 8 bits:
a band's edge may move a pixel from the run's own bands.png.
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "r279-depth-page"))
import depth_page as dp


def finish(L, dn, centres, img, n, fields=()):
    """depth_bands' passes after the masks, the moved fields kept whole at the end (r387)."""
    h, w = L.shape
    moved = L
    L = dp.by_colour(L, img, n)
    L = dp.see_through(L, dn, centres, img, n)
    L = dp.strays(L, n, w * h // 150)
    return dp.kept_fields(dp.loose(L, n), moved, list(fields))


def slabs(L, img, n):
    return [int((dp.slab_cuts(L, img, k) >= 0).sum()) for k in range(n - 1)]


def main():
    run, out = sys.argv[1], sys.argv[2]
    name = os.path.basename(os.path.normpath(run))
    os.makedirs(out, exist_ok=True)
    img = np.asarray(Image.open(os.path.join(run, "cut", "painting.png")).convert("RGB"))
    dn = np.asarray(Image.open(os.path.join(run, "cut", "depth.png"))).astype(np.float32) / 255
    h, w = dn.shape
    n = 4
    L, centres = dp.bands(dn, n, w * h // 150)
    L = dp.objects(L, dp.masks_of(img), n)
    fields = dp.agreeing(L, dp.fields_of(img, L, n), n)
    dp.outlines(img, fields).save(os.path.join(out, f"{name}_fields.png"))
    cuts = {"before": finish(L, dn, centres, img, n),
            "after": finish(dp.with_fields(L, fields, n), dn, centres, img, n, fields)}
    for tag, cut in cuts.items():
        b = np.asarray(dp.colour_bands(cut, n)).astype(np.float32)
        shown = (img * 0.55 + b * 0.45).astype(np.uint8)
        for k in range(n - 1):  # each slab cut left, in white
            ys = dp.slab_cuts(cut, img, k)
            xs = np.nonzero(ys >= 0)[0]
            for d in (-1, 0, 1):
                shown[ys[xs] + d, xs] = 255
        Image.fromarray(shown).save(os.path.join(out, f"{name}_{tag}.png"))
        np.save(os.path.join(out, f"{name}_{tag}.npy"), cut)
        print(f"{name} {tag}: slab-cut columns per band pair {slabs(cut, img, n)}")
    print(f"{name}: {len(fields)} fields, {(cuts['before'] != cuts['after']).mean():.2%} of the pixels changed band")


if __name__ == "__main__":
    main()
