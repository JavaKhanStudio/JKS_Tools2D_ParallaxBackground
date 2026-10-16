"""Redraws the far peaks' skyline where a twig and a sliver stood (r467).

    python3 tools/r467-far-peaks/cut.py [--check] [--show OUT.png]

The far peaks strip (core/test-data/hills/forest.png, region l0) had, between two peaks at x 800-918, a free-standing
twig on a thin neck (x 804-860) and a one-or-two-pixel sliver (x 855-917, y 247-262), the sky between them and in a
pocket under the right peak's flank. Neither is a mountain. Here, in those columns only, the skyline is drawn again:
the valley floor the twig and the sliver stood on (row FLOOR, met from the left peak's foot), and the right peak's flank
carried down at its own slope until it meets that floor, the joint rounded. Above it: transparent (colour 0, as
core/test-data/hills/make_lab.py's clean). Under it: opaque, a new pixel's colour what diffuses in from the opaque
pixels around it (tools/r466-range-holes/fill.py's diffuse), and a lone speck over the sky cleared. l1 untouched.
Then tools/r466-range-holes/fill.py --region l0 fills l0's pockets beside its other peaks, as r466 did l1's.
--check: exits 1 when a column of l0 has a pixel under its ridge that is not opaque, or a shape standing free above it.
--show: the result on magenta, what changed in red.
"""
import importlib.util
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PNG = os.path.join(HERE, "..", "..", "core", "test-data", "hills", "forest.png")
H = 384  # l0: rows 0-383 of forest.png
X0, X1 = 800, 918  # the columns redrawn; the ridge is kept either side
FLOOR = 264.0  # the deepest row of the sky under the twig and the sliver: the valley they stood on
FLANK, FLANK_TO = 889, 904  # the right peak's flank, its slope measured over these columns
ROUGH = 1.2  # rows

spec = importlib.util.spec_from_file_location("fill", os.path.join(HERE, "..", "r466-range-holes", "fill.py"))
fill = importlib.util.module_from_spec(spec)
spec.loader.exec_module(fill)


def skyline(alpha):
    """The new ridge over X0..X1, a fractional row per column."""
    top = fill.ridge(alpha).astype(np.float64)
    xs = np.arange(X0, X1 + 1, dtype=np.float64)
    left = top[X0] + (FLOOR - top[X0]) * np.clip((xs - X0) / 26, 0, 1)  # down the left peak's foot to the floor
    slope = (top[FLANK] - top[FLANK_TO]) / (FLANK_TO - FLANK)
    flank = np.where(xs >= FLANK, top[X0:X1 + 1], top[FLANK] + (FLANK - xs) * slope)
    line = np.minimum(left, flank)  # rows: the smaller is the higher
    kernel = np.ones(7) / 7  # round the joints; the ends keep the ridge either side
    smooth = np.convolve(np.pad(line, 3, mode="edge"), kernel, "valid")
    redrawn = (xs > X0 + 3) & (xs < FLANK)  # a ruler-straight flank reads drawn: the painted ridges' own roughness
    return smooth + redrawn * ROUGH * (np.sin(xs * 0.9) * 0.6 + np.sin(xs * 0.37 + 1.3) * 0.4)


def cut(strip):
    alpha = strip[..., 3]
    line = skyline(alpha)
    rows = np.arange(H, dtype=np.float64)[:, None]
    cover = np.clip(rows + 1 - line[None, :], 0, 1)  # each pixel's share under the line: the edge's soft row
    colour = fill.diffuse(strip[..., :3], alpha == 255)
    out = strip.copy()
    part = out[:, X0:X1 + 1]
    old = alpha[:, X0:X1 + 1, None] / 255
    part[..., :3] = np.where(cover[..., None] > 0, part[..., :3] * old + colour[:, X0:X1 + 1] * (1 - old), 0)
    part[..., 3] = cover * 255
    return fill.bleed(out, colour)


def unspeck(strip):
    """A pixel a row thick, sky over and under it: the one speck left on l0's skyline (x 137)."""
    a = strip[..., 3]
    speck = (a > 0) & (np.roll(a, 1, 0) == 0) & (np.roll(a, -1, 0) == 0)
    speck[0] = speck[-1] = False
    strip[speck] = 0
    return strip


def problems(alpha):
    """The pixels of l0 under its ridge not opaque, and the opaque ones over a gap of sky in their column."""
    under = fill.holes(alpha)
    rows = np.arange(H)[:, None]
    opaque = alpha >= 128
    lowest_sky = H - 1 - np.argmax((alpha[::-1] == 0), axis=0)  # the lowest clear pixel of each column
    free = opaque & (rows < lowest_sky[None, :])
    return under, free


def main(argv):
    page = np.array(Image.open(PNG).convert("RGBA"))
    strip = page[:H].astype(np.float64)
    if "--check" in argv:
        under, free = problems(strip[..., 3])
        print(f"l0: {int(under.sum())} pixels not opaque under the ridge, {int(free.sum())} standing over the sky")
        return 1 if under.any() or free.any() else 0
    out = page.copy()
    out[:H] = np.rint(unspeck(cut(strip))).astype(np.uint8)
    if "--show" in argv:
        img = Image.new("RGBA", (page.shape[1], H), (255, 0, 255, 255))
        img.alpha_composite(Image.fromarray(out[:H]))
        img = np.array(img)
        img[(out[:H] != page[:H]).any(axis=2)] = (255, 0, 0, 255)
        Image.fromarray(img).save(argv[argv.index("--show") + 1])
        return 0
    Image.fromarray(out).save(PNG, optimize=True)
    print(f"l0: {int((out != page).any(axis=2).sum())} pixels changed in x {X0}-{X1}; {PNG}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
