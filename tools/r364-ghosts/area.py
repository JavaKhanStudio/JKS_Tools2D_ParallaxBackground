"""r364: what a layer.py change keys out, per strip: its opaque area (alpha >= 0.5) in two re-cuts (recut.sh's OUTs).

    python3 tools/r364-ghosts/area.py BEFORE AFTER PAGE...

Per strip: the opaque px inside sil.py's mask shrunk by 8 px (where layer.py made alpha 1 before r364), before and
after, and the opaque px lost there; then the largest hole the change opened in that solid zone (4-connected, px),
a ghost keyed out or a bite out of the mass: look at the strips with the largest.
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "r239-ai-strips"))
import layer


def main():
    before, after, pages = sys.argv[1], sys.argv[2], sys.argv[3:]
    print("strip\tsolid zone px\topaque before\topaque after\tlost\tlargest hole")
    for e in pages:
        cell = os.path.basename(e.rstrip("/"))
        if not os.path.isdir(os.path.join(after, cell)):
            continue
        for f in sorted(os.listdir(os.path.join(after, cell))):
            s = f[:-4]
            b = np.asarray(Image.open(os.path.join(before, cell, f)))[..., 3] >= 128
            a = np.asarray(Image.open(os.path.join(after, cell, f)))[..., 3] >= 128
            m = np.asarray(Image.open(os.path.join(e, "strips", s + "_mask.png")).convert("L").resize(a.shape[::-1]))
            zone = layer.spread(m > 127, -8)
            lost = zone & b & ~a
            _, sizes = layer.components(lost)
            print(f"{cell}/{s}\t{zone.sum()}\t{(zone & b).sum()}\t{(zone & a).sum()}\t{lost.sum()}\t{max(sizes, default=0)}")


if __name__ == "__main__":
    main()
