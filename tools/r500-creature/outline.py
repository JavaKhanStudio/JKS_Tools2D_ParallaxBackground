"""r500: on_neck's and on_ridge's blobs over every strip on disk, from each cut's alpha, as disk.sh asks them.

    python3 tools/r500-creature/outline.py ROOT...
"""
import glob
import multiprocessing
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "r239-ai-strips"))
import creature


def one(c):
    try:
        s = Image.open(c[:-len("_cut.png")] + "_ai.png").convert("RGB")
        k = Image.open(c).convert("RGBA")
    except OSError:
        return []
    if s.size != k.size:
        return []
    rgb = np.asarray(s).astype(np.float64)
    a = np.asarray(k.getchannel("A")).astype(np.float64) / 255
    sky = np.median(rgb[:8].reshape(-1, 3), axis=0)
    q = s.width / 1024
    r = int(12 * q)
    return ([("neck", c, s.width, f) for f in creature.on_neck(rgb, a, sky, r)]
            + [("ridge", c, s.width, f) for f in creature.on_ridge(rgb, a, sky, int(50 * q * q), r)])


def main():
    cuts = sorted(c for root in sys.argv[1:] for c in glob.glob(f"{root}/**/*_cut.png", recursive=True))
    with multiprocessing.Pool(8) as pool:
        for rows in pool.imap_unordered(one, cuts, 8):
            for w, c, width, f in rows:
                print(w, c, width, *f)


if __name__ == "__main__":
    main()
