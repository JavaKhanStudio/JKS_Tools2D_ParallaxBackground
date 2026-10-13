"""r360's second try, kept as evidence it fails: move a depth band's edge onto the painting's colour edges.

    ~/ComfyUI/venv/bin/python tools/r360-band-edges/geodesic.py build/ai/depth/river OUT_PREFIX \
        [--reach 40 --beta 3 --floor 6 --lam 40 --conf 0.4]

Reads OUT's cut/painting.png and cut/depth.png (no GPU), runs depth_page.py's bands(), then: within --reach px of a band
edge, a pixel whose depth is not clearly its band's (the two nearest centres within --conf of each other) takes the
band k minimising (path cost from k's sure pixels) + lam |disparity - centre k|, a step costing its length plus beta
per colour level over --floor. by_colour() and strays() run after both, as depth_page.py does. Writes
OUT_PREFIX_{before,after}.png (the bands over the painting) and prints the mean colour step along the band edges.

What it showed (r360): on these paintings the brush texture is everywhere, so the path cost does not single out an
object's outline. The valley's pines broke into yellow and green pieces, the river's forest edge became a blob through
the crowns; the mean colour step along the edges (after by_colour and strays) went 13.6 -> 13.6 (river), 18.9 -> 19.5 (valley).
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "r279-depth-page"))
import depth_page as dp

STEPS = [(0, 1), (0, -1), (1, 0), (-1, 0), (1, 1), (1, -1), (-1, 1), (-1, -1)]


def shift(a, dy, dx, fill):
    """out[y, x] = a[y - dy, x - dx], X wrapping, rows from past the edge = fill."""
    out = np.roll(a, dx, axis=1)
    if dy > 0:
        out = np.concatenate([np.full_like(out[:dy], fill), out[:-dy]], 0)
    elif dy < 0:
        out = np.concatenate([out[-dy:], np.full_like(out[:-dy], fill)], 0)
    return out


def geodesic(seed, costs, inside):
    """Least path cost from seed to each pixel of inside, by relaxing the 8 steps until nothing moves."""
    d = np.where(seed, 0.0, np.inf).astype(np.float32)
    while True:
        changed = False
        for (dy, dx), c in costs.items():
            nd = shift(d, dy, dx, np.inf) + c
            better = (nd < d - 1e-4) & inside
            if better.any():
                d = np.where(better, nd, d)
                changed = True
        if not changed:
            return d


def snap(L, img, dn, centres, a):
    rgb = ndimage.gaussian_filter(img.astype(np.float32), (1.5, 1.5, 0), mode=("nearest", "wrap", "nearest"))
    edge = (L != np.roll(L, 1, 1)) | (L != np.roll(L, -1, 1))
    edge[1:] |= L[1:] != L[:-1]
    edge[:-1] |= L[1:] != L[:-1]
    zone = dp.grow(edge, a.reach)
    costs = {s: np.hypot(*s) + a.beta * np.maximum(0, np.linalg.norm(rgb - shift(rgb, *s, 0), axis=-1) - a.floor)
             for s in STEPS}
    dist = np.abs(dn[None] - np.asarray(centres)[:, None, None])
    two = np.sort(dist, 0)
    sure = (two[1] - two[0]) / np.maximum(two[1] + two[0], 1e-6) > a.conf
    score = np.full((len(centres),) + L.shape, np.inf, np.float32)
    for k in range(len(centres)):
        seed = (L == k) & (~zone | sure)
        if not seed.any():
            seed = L == k
        if seed.any():
            score[k] = geodesic(seed, costs, zone | seed) + a.lam * dist[k]
    return np.where(zone & ~sure, np.argmin(score, 0), L)


def step_on_edges(L, img):
    """The mean colour step across the band edges, and the share of edge px on flat colour (a step under 8)."""
    rgb = ndimage.gaussian_filter(img.astype(np.float32), (1, 1, 0), mode=("nearest", "wrap", "nearest"))
    gx = np.linalg.norm(rgb - np.roll(rgb, 1, 1), axis=-1)
    gy = np.zeros_like(gx)
    gy[1:] = np.linalg.norm(rgb[1:] - rgb[:-1], axis=-1)
    ey = np.zeros(L.shape, bool)
    ey[1:] = L[1:] != L[:-1]
    g = np.concatenate([gx[L != np.roll(L, 1, 1)], gy[ey]])
    return float(g.mean()), float((g < 8).mean())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("page")
    ap.add_argument("out")
    ap.add_argument("--layers", type=int, default=4)
    ap.add_argument("--reach", type=int, default=40)
    ap.add_argument("--beta", type=float, default=3)
    ap.add_argument("--floor", type=float, default=6)
    ap.add_argument("--lam", type=float, default=40)
    ap.add_argument("--conf", type=float, default=0.4)
    a = ap.parse_args()
    img = np.asarray(Image.open(os.path.join(a.page, "cut", "painting.png")).convert("RGB"))
    dn = np.asarray(Image.open(os.path.join(a.page, "cut", "depth.png"))).astype(float) / 255
    h, w = dn.shape
    L, centres = dp.bands(dn, a.layers, w * h // 150)
    for name, lab in (("before", L), ("after", snap(L, img, dn, centres, a))):
        lab = dp.strays(dp.by_colour(lab, img, a.layers), a.layers, w * h // 150)
        mean, flat = step_on_edges(lab, img)
        print(f"{name}: mean step {mean:.1f} along the band edges, {flat:.0%} of them on flat colour")
        over = Image.blend(Image.fromarray(img), dp.colour_bands(lab, a.layers).convert("RGB"), 0.5)
        over.save(f"{a.out}_{name}.png")


if __name__ == "__main__":
    main()
