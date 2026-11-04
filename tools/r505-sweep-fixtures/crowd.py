"""r505: what a sweep fixture's mass stands out in: the blobs creature.find counts as a crowd, and how much of it is half
keyed. Picks the colours and places of the planted cases that prove the crowd rule's shape and size bounds and the
half-keyed mass.

    python3 tools/r505-sweep-fixtures/crowd.py FIXTURES NAME...
"""
import sys

import numpy as np
from mutant import components, load, read_alpha, spread
from PIL import Image

creature = load("creature")


def main():
    d = sys.argv[1]
    for name in sys.argv[2:]:
        rgb = np.asarray(Image.open(f"{d}/{name}_ai.png").convert("RGB")).astype(np.float64)
        cut = Image.open(f"{d}/{name}_cut.png").convert("RGBA")
        alpha = read_alpha(cut.getchannel("A").resize((rgb.shape[1], rgb.shape[0]), Image.NEAREST))
        solid = spread(alpha >= 0.5, -4)
        half = solid & (alpha < 0.95)
        k = (rgb.shape[1] / 1024) ** 2
        r = int(12 * k ** 0.5)
        out = creature.standing_out(rgb, solid, solid.astype(np.float64), r, 40, 3.0)
        out = creature.standing_out(rgb, solid, (solid & ~spread(out, 3)).astype(np.float64), r, 40, 3.0)
        label, sizes = components(out)
        print(f"{name}: solid {solid.mean():.2f} half-keyed {half.sum()} px, {len(sizes)} blobs")
        rows = []
        for i, size in enumerate(sizes):
            if size < 8:
                continue
            ys, xs = np.nonzero(label == i + 1)
            rows.append((int(size), int(xs.mean()), int(ys.mean()), round(float((np.ptp(ys) + 1) / (np.ptp(xs) + 1)), 2),
                         "".join(f"{int(c):02x}" for c in rgb[ys, xs].mean(axis=0))))
        for row in sorted(rows, reverse=True)[:12]:
            print("   size {} at {},{} tall {:.2f} colour {}".format(*row))


if __name__ == "__main__":
    main()
