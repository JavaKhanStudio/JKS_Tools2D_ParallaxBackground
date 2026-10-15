#!/usr/bin/env python3
"""r420's look: where layer l0's alpha changed between two replays (tools/r409-clouds/replay.py) or two depth-page
runs, each changed piece cut out of the painting, the before l0 and the after l0 over magenta, side by side and
enlarged.

    ~/ComfyUI/venv/bin/python tools/r420-sky-shard/compare.py BEFORE AFTER OUT.png [--layer l0] [--pad 24] [--zoom 4]

Prints each piece's box and how many pixels it lost or gained.
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage


def alpha(run, layer, h):
    part = np.asarray(Image.open(os.path.join(run, "cut", layer + ".png")).convert("RGBA"))
    out = np.zeros((h, part.shape[1], 4), np.uint8)
    out[h - part.shape[0]:] = part  # a layer is cut from its top row to the painting's bottom
    return out


def over(rgba):
    a = rgba[..., 3:].astype(np.float32) / 255
    return (rgba[..., :3] * a + np.array([255, 0, 255]) * (1 - a)).astype(np.uint8)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("before")
    ap.add_argument("after")
    ap.add_argument("out")
    ap.add_argument("--layer", default="l0")
    ap.add_argument("--pad", type=int, default=24)
    ap.add_argument("--zoom", type=int, default=4)
    a = ap.parse_args()
    painting = os.path.join(a.before, "painting.png")  # a replay's; a depth-page run's is in cut/
    if not os.path.exists(painting):
        painting = os.path.join(a.before, "cut", "painting.png")
    img = np.asarray(Image.open(painting).convert("RGB"))
    h, w = img.shape[:2]
    b, f = alpha(a.before, a.layer, h), alpha(a.after, a.layer, h)
    moved = (b[..., 3] > 0) != (f[..., 3] > 0)
    comp, _ = ndimage.label(ndimage.binary_dilation(moved, iterations=a.pad // 2))
    rows = []
    for i, sl in enumerate(ndimage.find_objects(comp)):
        y0, y1 = max(0, sl[0].start - a.pad), min(h, sl[0].stop + a.pad)
        x0, x1 = max(0, sl[1].start - a.pad), min(w, sl[1].stop + a.pad)
        m = moved[y0:y1, x0:x1] & (comp[y0:y1, x0:x1] == i + 1)
        lost = (m & (b[y0:y1, x0:x1, 3] > 0)).sum()
        print(f"x {x0}-{x1} y {y0}-{y1}: {lost} px lost, {m.sum() - lost} gained")
        tile = np.concatenate([img[y0:y1, x0:x1], over(b[y0:y1, x0:x1]), over(f[y0:y1, x0:x1])], axis=1)
        rows.append(np.kron(tile, np.ones((a.zoom, a.zoom, 1), np.uint8)))
    if not rows:
        print("nothing moved")
        return 0
    width = max(r.shape[1] for r in rows)
    sheet = np.concatenate([np.pad(r, ((0, 4), (0, width - r.shape[1]), (0, 0))) for r in rows])
    Image.fromarray(sheet).save(a.out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
