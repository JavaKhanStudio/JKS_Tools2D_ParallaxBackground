"""r505: every painted strip on disk that a rule's mutant refuses and creature.py does not: the must-pass fixtures that
prove that rule. Looks through ridge and neck checks only: they are what plants cannot prove.

    python3 tools/r505-sweep-fixtures/corpus.py [ROOT...]   (default build, ../JKS_Tools2D_ParallaxAI/build)
"""
import glob
import multiprocessing
import os
import sys

import numpy as np
from mutant import load, read_alpha
from PIL import Image

MUTANTS = {"ridge on the mass's edge too": ("above = spread(mass, 2 * r) & ~spread(mass, -1)", "above = spread(mass, 2 * r) & ~solid"),
           "neck: the outline far from the mass": ("spread(mass, 4 * r) & (t", "spread(mass, 12 * r) & (t"),
           "neck: any head shape": (" or size < 0.6 * w * h:", ":")}


REAL = load("real")
MUT = {n: load(f"m{i}", *m) for i, (n, m) in enumerate(MUTANTS.items())}


def checks(m, rgb, alpha, sky, r, lo):
    return m.on_ridge(rgb, alpha, sky, lo, r, 80) + m.on_neck(rgb, alpha, sky, r)


def one(cut):
    src = cut[: -len("_cut.png")] + "_ai.png"
    if not os.path.exists(src):
        return None
    im = Image.open(src).convert("RGB")
    c = Image.open(cut).convert("RGBA")
    if c.width != im.width:  # pixel art
        return None
    rgb = np.asarray(im).astype(np.float64)
    alpha = read_alpha(c.getchannel("A"))
    if (alpha >= 0.5).sum() < 100:
        return None
    k = (im.width / 1024) ** 2
    r = int(12 * k ** 0.5)
    sky = np.median(rgb[:8].reshape(-1, 3), axis=0)
    if checks(REAL, rgb, alpha, sky, r, int(50 * k)):
        return None
    hits = [n for n, m in MUT.items() if checks(m, rgb, alpha, sky, r, int(50 * k))]
    if hits and not REAL.find(rgb, alpha, int(50 * k), int(3000 * k), r=r):
        return cut, im.width, os.path.getsize(src) // 1024, hits
    return None


def main():
    roots = sys.argv[1:] or ["build", "../JKS_Tools2D_ParallaxAI/build"]
    cuts = sorted(f for r in roots for f in glob.glob(f"{r}/**/*_cut.png", recursive=True))
    print(len(cuts), "cuts", flush=True)
    with multiprocessing.Pool(8) as pool:
        for res in pool.imap_unordered(one, cuts):
            if res:
                print(*res, flush=True)


if __name__ == "__main__":
    main()
