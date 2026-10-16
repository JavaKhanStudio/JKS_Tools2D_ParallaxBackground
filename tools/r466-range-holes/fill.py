"""Fills the holes under the range's ridge in the forest page's l1 (r466).

    python3 tools/r466-range-holes/fill.py [--region l0|l1] [--check] [--show OUT.png]

The range strip (core/test-data/hills/forest.png, region l1) was cut with pockets under its ridge: overhanging nubs
beside the peaks and stepped, see-through cut-outs between them, the sky (and the shadow lab's setting sun) showing
through. Here every pixel of a column below that column's ridge (its first pixel at alpha 128 or more, and the pixel
under it, the ridge's own soft edge) is made opaque. Filled straight down, the nubs either side of a pocket would leave a
slot with upright walls between them, so near a hole the ridge is first closed: a dip narrower than 2 DIP + 1 columns
is raised to the lower of its two sides, sagging into a saddle (SAG of its width at its middle). A filled pixel's colour is what it had over what diffuses in from the opaque
pixels around it, and so does a clear pixel's next to the new edge. The ridge line stays the strip's only edge. Rewrites forest.png in place, the other region untouched.
--region: l1 (the default) or l0, the far peaks, whose pockets r467 filled the same way after tools/r467-far-peaks/cut.py.
--check: exits 1 when a hole is left (nothing written). --show: the result on magenta, what changed in red.
"""
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PNG = os.path.join(HERE, "..", "..", "core", "test-data", "hills", "forest.png")
W, H = 1024, 384
REGIONS = {"l0": 0, "l1": 385}  # each region's first row in forest.atlas
DIP, NEAR, SAG = 12, 16, 0.2  # half the widest dip closed; how far from a hole; how deep a closed dip sags, per column


def ridge(alpha):
    return np.argmax(alpha >= 128, axis=0)


def holes(alpha):
    """The pixels under the ridge, a pixel below its soft edge, that are not opaque."""
    rows = np.arange(alpha.shape[0])[:, None]
    return (rows >= ridge(alpha)[None, :] + 2) & (alpha < 255)


def window(profile, k, pick):
    """pick (np.min or np.max) over each column's 2k + 1 neighbours; the strip loops."""
    return pick(np.stack([np.roll(profile, d) for d in range(-k, k + 1)]), axis=0)


def closed_ridge(alpha):
    """The ridge, its dips narrower than 2 DIP + 1 closed in the columns within NEAR of a hole."""
    top = ridge(alpha)
    near = window(holes(alpha).any(axis=0), NEAR, np.max)
    closed = window(window(top, DIP, np.min), DIP, np.max)  # a row index: min is the higher ridge
    raised = near & (closed < top)
    out = top.copy()
    for s, e in runs(raised):  # a flat shelf reads as a ruler line: let it sag into a saddle
        xs = np.arange(s, e + 1) % W
        t = (np.arange(s, e + 1) - s + 1) / (e - s + 2)
        out[xs] = np.minimum(top[xs], np.rint(closed[xs] + SAG * (e - s + 1) * 4 * t * (1 - t)).astype(top.dtype))
    return out


def runs(mask):
    """The [start, end] of each run of True columns, a run across the strip's loop as one (end past W)."""
    if mask.all():
        return [(0, W - 1)]
    shift = int(np.argmin(mask))  # start the walk on a False column
    found, start = [], None
    for i in range(W + 1):
        x = (shift + i) % W
        if mask[x] and start is None:
            start = shift + i
        elif not mask[x] and start is not None:
            found.append((start % W, start % W + (shift + i - start) - 1))
            start = None
    return found


def diffuse(rgb, known):
    """Every unknown pixel the mean of its known 4-neighbours, grown inward until none is left."""
    rgb, known = rgb.astype(np.float64), known.copy()
    while not known.all():
        acc, cnt = np.zeros_like(rgb), np.zeros(known.shape)
        for dy, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            k = np.roll(known, (dy, dx), (0, 1))
            acc += np.roll(rgb, (dy, dx), (0, 1)) * k[..., None]
            cnt += k
        grow = ~known & (cnt > 0)
        rgb[grow] = acc[grow] / cnt[grow][:, None]
        known |= grow
    return rgb


def fill(strip):
    alpha = strip[..., 3]
    top, ridge_top = closed_ridge(alpha), ridge(alpha)
    rows = np.arange(H)[:, None]
    # under the closed ridge; where it was raised, from the new ridge down, else under the old soft edge
    under = rows >= np.where(top < ridge_top, top, ridge_top + 2)[None, :]
    colour = diffuse(strip[..., :3], alpha == 255)
    a = (alpha / 255)[..., None]
    out = strip.copy()
    out[..., :3] = np.where(under[..., None], strip[..., :3] * a + colour * (1 - a), strip[..., :3])
    out[..., 3] = np.where(under, 255, alpha)
    return bleed(out, colour)


def bleed(strip, colour):
    """A clear pixel beside a seen one, its colour 0, given the colour around it: the reader draws with Linear, and the
    0 bleeds into the edge as a dark line (make_lab.py's clean keeps colour that close for the same reason)."""
    seen = strip[..., 3] > 0
    beside = np.zeros_like(seen)
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            beside |= np.roll(seen, (dy, dx), (0, 1))
    beside[0] = beside[-1] = False  # np.roll wraps the rows: the strip loops across, not up
    blank = beside & ~seen & (strip[..., :3] == 0).all(axis=2)
    strip[blank, :3] = colour[blank]
    return strip


def main(argv):
    name = argv[argv.index("--region") + 1] if "--region" in argv else "l1"
    y0 = REGIONS[name]
    page = np.array(Image.open(PNG).convert("RGBA"))
    strip = page[y0:y0 + H].astype(np.float64)
    hole = holes(strip[..., 3])
    if "--check" in argv:
        print(f"{name}: {int(hole.sum())} pixels not opaque under the ridge")
        return 1 if hole.any() else 0
    out = page.copy()
    out[y0:y0 + H] = np.rint(fill(strip)).astype(np.uint8)
    if "--show" in argv:
        img = Image.new("RGBA", (W, H), (255, 0, 255, 255))
        img.alpha_composite(Image.fromarray(out[y0:y0 + H]))
        img = np.array(img)
        img[(out[y0:y0 + H] != page[y0:y0 + H]).any(axis=2)] = (255, 0, 0, 255)
        Image.fromarray(img).save(argv[argv.index("--show") + 1])
        return 0
    Image.fromarray(out).save(PNG, optimize=True)
    print(f"{name}: {int(hole.sum())} holes filled, {int((out != page).any(axis=2).sum())} pixels changed; {PNG}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
