"""r516: why creature.py refuses a strip. Runs its three checks apart and, for find()'s biggest blob, says what each
exemption answers and how near the crowd rule came: the siblings of its colour within the size bound, and their shape
ratio against the 3 of creature.find. Writes the strip rolled to the blob, plain over the blob in red.

    python3 tools/r516-far-pines/probe.py AI.png CUT.png OUT.png
"""
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "../r239-ai-strips"))
import creature
from layer import components, read_alpha, spread


def unrolled(xs, w):
    """A blob's columns, across the strip's join when it spans it."""
    return np.where(xs < w // 2, xs + w, xs) if xs.max() - xs.min() > w // 2 else xs


def main():
    ai, cut, out_png = sys.argv[1:4]
    src = Image.open(ai).convert("RGB")
    alpha = read_alpha(Image.open(cut).convert("RGBA").getchannel("A").resize(src.size, Image.NEAREST))
    rgb = np.asarray(src).astype(np.float64)
    w = src.width
    k = w / 1024
    r, lo, hi = int(12 * k), int(50 * k * k), int(3000 * k * k)
    sky = np.median(rgb[:8].reshape(-1, 3), axis=0)
    print("find", creature.find(rgb, alpha, lo, hi, r=r))
    print("on_ridge", creature.on_ridge(rgb, alpha, sky, lo, r))
    print("on_neck", creature.on_neck(rgb, alpha, sky, r))
    solid = spread(alpha >= 0.5, -4)
    first = creature.standing_out(rgb, solid, solid.astype(np.float64), r, 40, 3.0)
    out = creature.standing_out(rgb, solid, (solid & ~spread(first, 3)).astype(np.float64), r, 40, 3.0)
    label, sizes = components(out)
    found = creature.find(rgb, alpha, lo, hi, r=r)
    if not found:
        return
    size = max(f[2] for f in found)
    blob = label == list(sizes).index(size) + 1
    ys, xs = np.nonzero(blob)
    xu = unrolled(xs, w)
    tall = (np.ptp(ys) + 1) / (np.ptp(xu) + 1)
    colour = rgb[blob].mean(axis=0)
    print(f"blob {size} px, {np.ptp(xu) + 1} x {np.ptp(ys) + 1}, tall {tall:.3f}, colour {colour.round()}")
    print("shading", creature.shading(rgb, solid, blob, 0.7, 15), "sky_through", creature.sky_through(rgb, solid, blob, sky),
          "runs_on", creature.runs_on(rgb, solid, blob, 40, hi))
    for j, n in enumerate(sizes):
        if n < 8 or j + 1 == label[ys[0], xs[0]] or not 1 / 3 < n / size < 3:
            continue
        yy, xx = np.nonzero(label == j + 1)
        if np.linalg.norm(rgb[yy, xx].mean(axis=0) - colour) < 30:
            t = (np.ptp(yy) + 1) / (np.ptp(unrolled(xx, w)) + 1)
            print(f"sibling {n} px at x={int(xx.mean())} y={int(yy.mean())}: shape ratio {max(t / tall, tall / t):.2f} (bound 3)")
    shift = w // 2 - int(xu.mean())
    plain = np.roll(rgb, shift, 1).astype(np.uint8)
    red = plain.copy()
    red[np.roll(blob, shift, 1)] = (255, 0, 0)
    y0, y1 = max(ys.min() - 120, 0), min(ys.max() + 100, rgb.shape[0])
    x0, x1 = max(w // 2 - 350, 0), min(w // 2 + 350, w)
    both = np.vstack([plain[y0:y1, x0:x1], red[y0:y1, x0:x1]])
    Image.fromarray(both).resize((both.shape[1] * 2, both.shape[0] * 2), Image.NEAREST).save(out_png)


if __name__ == "__main__":
    main()
