"""Plant a creature in a painted strip, where SD might paint one (r261): creature.py must name it.

    python3 tools/r261-creature/plant.py painted.png out.png --at X,Y [--kind bird|deer] [--size 1.0] [--colour 2a2420]

A bird in flight (body and two swept wings, ~30 x 12 px at --size 1) or a standing deer (body, neck, head, legs,
~34 x 30 px), drawn antialiased (4x supersampled) in --colour, by default a third of the colour under it: SD paints a
creature darker than the land it stands on. --at is its middle.
"""
import argparse

import numpy as np
from PIL import Image, ImageDraw

SHAPES = {  # polygons in px at --size 1, around (0, 0)
    "bird": [[(-15, -5), (-8, -2), (-2, 0), (0, -2), (2, 0), (8, -2), (15, -5), (10, 1), (3, 4), (0, 6), (-3, 4),
              (-10, 1)]],
    "deer": [[(-14, -6), (8, -6), (11, -12), (13, -16), (17, -15), (14, -10), (12, -2), (10, 4), (-14, 4)],
             [(-13, 3), (-10, 3), (-10, 15), (-12, 15)], [(-7, 3), (-4, 3), (-5, 15), (-7, 15)],
             [(4, 3), (7, 3), (7, 15), (5, 15)], [(8, 3), (11, 3), (11, 15), (9, 15)]],
}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("out")
    ap.add_argument("--at", required=True)
    ap.add_argument("--kind", choices=SHAPES, default="bird")
    ap.add_argument("--size", type=float, default=1.0)
    ap.add_argument("--colour", help="hex RGB; default a third of the colour under it")
    a = ap.parse_args()
    im = Image.open(a.src).convert("RGB")
    x, y = (int(v) for v in a.at.split(","))
    if a.colour:
        col = tuple(int(a.colour[i:i + 2], 16) for i in (0, 2, 4))
    else:
        col = tuple(int(c / 3) for c in np.asarray(im)[y - 3:y + 4, x - 3:x + 4].reshape(-1, 3).mean(0))
    ss, e = 4, int(40 * a.size)
    mask = Image.new("L", (2 * e * ss, 2 * e * ss))
    draw = ImageDraw.Draw(mask)
    for poly in SHAPES[a.kind]:
        draw.polygon([((px * a.size + e) * ss, (py * a.size + e) * ss) for px, py in poly], fill=255)
    mask = mask.resize((2 * e, 2 * e), Image.BOX)
    im.paste(Image.new("RGB", mask.size, col), (x - e, y - e), mask)
    im.save(a.out)


if __name__ == "__main__":
    main()
