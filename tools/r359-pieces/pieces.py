#!/usr/bin/env python3
"""The pieces of each depth-page layer apart from its mass (r359), as ParallaxAI's tools/review/cut_stats.py counts
them (alpha over 50%), but each once: cut_stats.py halves a count over the layer tiled twice, and a piece across
the join is then three halves.

    python3 tools/r359-pieces/pieces.py build/ai/depth/river [more runs...]

Prints, per run, each layer's pieces / their pixels / the largest one's pixels (l0, opaque, is skipped).
"""
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage


def pieces(path):
    """(count, pixels, largest) of the pieces apart from the layer's mass, on the layer laid three times side by side:
    each piece counted once, the copy whose left end is in the middle, whole even across the join."""
    a = np.asarray(Image.open(path).convert("RGBA"))[..., 3] > 127
    w = a.shape[1]
    lab, n = ndimage.label(np.tile(a, 3))
    if n < 2:
        return 0, 0, 0
    sizes = ndimage.sum(np.ones(lab.shape), lab, range(1, n + 1))
    left = np.array([s[1].start for s in ndimage.find_objects(lab)])
    apart = sizes[(sizes < sizes.max()) & (left >= w) & (left < 2 * w)]  # the mass: one piece or three of a size
    return len(apart), int(apart.sum()), int(apart.max()) if len(apart) else 0


def main():
    for run in sys.argv[1:]:
        cut = os.path.join(run, "cut")
        ks = sorted(int(f[1:-8]) for f in os.listdir(cut) if f.endswith("_raw.png"))
        cells = [f"l{k} {'/'.join(str(v) for v in pieces(os.path.join(cut, f'l{k}.png')))}" for k in ks if k]
        print(f"{run}: " + " | ".join(cells))


if __name__ == "__main__":
    main()
