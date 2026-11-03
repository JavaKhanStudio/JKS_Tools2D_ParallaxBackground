"""r500: for every blob creature.find would refuse on every strip on disk, the numbers its false shapes differ by:
foot (px to the strip's bottom), same (share of the ring round it in its own colour), skyward (t on the sky-land line,
0 = the sky) and off (distance from that line). No rule of r500's is on: it asks find with them switched off.

    python3 tools/r500-creature/measure.py OUT.tsv ROOT...
"""
import glob
import multiprocessing
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "r239-ai-strips"))
import creature
from layer import spread

ORIG = creature.shading


def one(cut):
    ai = cut[:-len("_cut.png")] + "_ai.png"
    if not os.path.exists(ai):
        return []
    try:
        src = Image.open(ai).convert("RGB")
        c = Image.open(cut).convert("RGBA")
    except OSError:
        return []
    if c.size != src.size:  # pixel art: not this gate's numbers
        return []
    rgb = np.asarray(src).astype(np.float64)
    alpha = np.asarray(c.getchannel("A")).astype(np.float64) / 255
    sky = np.median(rgb[:8].reshape(-1, 3), axis=0)
    k = src.width / 1024
    seen = []

    def spy(rgb_, solid, blob, shade, hue):
        v = ORIG(rgb_, solid, blob, shade, hue)
        if not v:
            ys, xs = np.nonzero(blob)
            col = rgb_[blob].mean(0)
            ring = spread(blob, 6) & ~spread(blob, 2) & solid
            same = float((np.linalg.norm(rgb_[ring] - col, axis=1) < 40).mean()) if ring.any() else 0.0
            land = rgb_[ring].mean(0) if ring.any() else col
            seg = land - sky
            t = float((col - sky) @ seg / max(seg @ seg, 1))
            off = float(np.linalg.norm(col - (sky + np.clip(t, 0, 1) * seg)))
            seen.append((cut, int(xs.mean()), int(ys.mean()), int(blob.sum()), int(np.ptp(xs) + 1), int(np.ptp(ys) + 1),
                         rgb_.shape[0] - 1 - int(ys.max()), round(same, 3), round(t, 3), round(off, 1)))
        return v

    creature.shading = spy
    creature.find(rgb, alpha, int(50 * k * k), int(3000 * k * k), r=int(12 * k))
    creature.shading = ORIG
    return seen


def main():
    cuts = sorted(c for root in sys.argv[2:] for c in glob.glob(f"{root}/**/*_cut.png", recursive=True))
    with multiprocessing.Pool(8) as pool, open(sys.argv[1], "w") as f:
        f.write("cut\tx\ty\tsize\tw\th\tfoot\tsame\tskyward\toff\n")
        for rows in pool.imap_unordered(one, cuts, 8):
            f.writelines("\t".join(map(str, r)) + "\n" for r in rows)
    print(len(cuts), "strips")


if __name__ == "__main__":
    main()
