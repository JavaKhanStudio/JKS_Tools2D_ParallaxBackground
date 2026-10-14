"""r365: how much SD painted inside each strip's shape, and whether the shape held.

    python3 tools/r365-init/measure.py PAGE [OTHER]

PAGE is ai-page.sh's output (recipe.json, strips/). Per strip: its kind, the colour std (RGB, the mean of the three
channels' std) inside sil.py's mask shrunk by 8 px, of the init and of the painted strip; with OTHER (another page
painted from the same seeds) the painted std there, and the alpha IoU (alpha >= 0.5) of the two _cut.png.
"""
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "r239-ai-strips"))
import layer


def std_in(path, zone):
    img = np.asarray(Image.open(path).convert("RGB")).astype(np.float64)
    return float(img[zone].std(axis=0).mean())


def zone_of(strips, s):
    m = np.asarray(Image.open(os.path.join(strips, s + "_mask.png")).convert("L")) > 127
    return layer.spread(m, -8)


def opaque(strips, s):
    """The cut's alpha >= 0.5 at the painting's size: a pixel cut is one pixel per art cell, its cell measured per
    strip."""
    size = Image.open(os.path.join(strips, s + "_ai.png")).size
    cut = Image.open(os.path.join(strips, s + "_cut.png")).convert("RGBA").resize(size, Image.NEAREST)
    return np.asarray(cut)[..., 3] >= 128


def main():
    page, other = sys.argv[1], sys.argv[2] if len(sys.argv) > 2 else None
    with open(os.path.join(page, "recipe.json")) as f:
        recipe = json.load(f)
    st = os.path.join(page, "strips")
    print("strip\tkind\tinit std\tpainted std" + ("\tother init std\tother painted std\talpha IoU" if other else ""))
    for lay in recipe["layers"]:
        for s in (x["strip"] for x in lay["strips"]):
            zone = zone_of(st, s)
            row = f"{s}\t{lay['kind']}\t{std_in(os.path.join(st, s + '_init.png'), zone):.1f}"
            row += f"\t{std_in(os.path.join(st, s + '_ai.png'), zone):.1f}"
            if other:
                ot = os.path.join(other, "strips")
                if os.path.exists(os.path.join(ot, s + "_ai.png")):
                    oz = zone_of(ot, s)
                    a, b = opaque(st, s), opaque(ot, s)
                    row += f"\t{std_in(os.path.join(ot, s + '_init.png'), oz):.1f}"
                    row += f"\t{std_in(os.path.join(ot, s + '_ai.png'), oz):.1f}\t{(a & b).sum() / max((a | b).sum(), 1):.3f}"
            print(row)


if __name__ == "__main__":
    main()
