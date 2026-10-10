"""Cut an SDXL pixel-art strip to true pixel art: one PNG pixel per art pixel (r243).

    python3 tools/r239-ai-strips/pixel.py painted.png out.png [--colours 12] [--palette ref.png] [--cell N]

SDXL with PixelArt_XL paints art pixels as cells of about 16 screen pixels, soft at their edges.
The cell size and phase are read from the picture: colour steps between neighbour columns (rows)
pile up on the cell borders, so the step profile folded at the right period has one tall peak.
Each cell becomes one pixel, the median of its inner half (the VAE blurs the borders). The cell
count across is rounded so it divides the width exactly, and sampling wraps: the strip loops, so
the small one does too.
Then the sky is keyed out hard (colour distance from the top rows' colour, cut halfway to the
layer's own distance) and the opaque pixels are quantised to --colours colours, or to --palette's
(the cut layers of one page share it). Opaque cells apart from the layer's main mass are cleared,
as layer.py does (r242), and art on the top row exits 3.
"""
import argparse
import sys

import numpy as np
from PIL import Image

from layer import islands


def steps(img, axis):
    """Mean colour step between each line and the next one along axis (wrapping along X)."""
    d = np.abs(np.roll(img, -1, axis=axis) - img).sum(axis=2)
    prof = d.mean(axis=0 if axis == 1 else 1)
    if axis == 0:
        prof = prof[:-1]  # Y does not wrap
    return prof


def grid(prof, lo=4, hi=40):
    """(period, phase) whose folded profile peaks most; the smallest period within 85% of the
    best one, so twice the cell (half the borders, the same peak) is not taken for it."""
    scores = {}
    for p in range(lo, hi + 1):
        n = len(prof) // p * p
        fold = prof[:n].reshape(-1, p).mean(axis=0)
        scores[p] = (fold.max() / max(fold.mean(), 1e-6), int(fold.argmax()))
    best = max(s for s, _ in scores.values())
    p = min(p for p, (s, _) in scores.items() if s >= 0.85 * best)
    return p, scores[p][1], scores[p][0]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("out")
    ap.add_argument("--colours", type=int, default=12)
    ap.add_argument("--palette", help="quantise to this image's opaque colours (a cut layer, or a palette strip)")
    ap.add_argument("--cell", type=int, default=0, help="cell size in px; 0: found from the picture")
    ap.add_argument("--mask", help="sil.py's _mask.png: a cell is opaque only within one cell of the silhouette")
    a = ap.parse_args()
    img = np.asarray(Image.open(a.src).convert("RGB")).astype(np.float32)
    h, w = img.shape[:2]
    px, phx, sx = grid(steps(img, 1))
    py, phy, sy = grid(steps(img, 0))
    cell = a.cell or px
    print(f"{a.src}: cell x {px} (phase {phx}, peak {sx:.1f}), y {py} (phase {phy}, peak {sy:.1f}); using {cell}",
          file=sys.stderr)
    nx = max(1, round(w / cell))
    pitch = w / nx  # whole cells across the width: the small strip loops
    ny = int((h - phy - 1) // cell)
    small = np.zeros((ny, nx, 3), np.float32)
    inside = np.ones((ny, nx), bool)
    mask = np.asarray(Image.open(a.mask).convert("L").resize((w, h))) > 127 if a.mask else None
    q = max(1, cell // 4)
    for j in range(ny):
        y0 = phy + 1 + j * cell  # a border sits after column/row `phase`
        rows = np.arange(y0 + q, y0 + cell - q)
        for i in range(nx):
            x0 = phx + 1 + i * pitch
            cols = np.arange(int(round(x0 + q)), int(round(x0 + pitch - q))) % w
            small[j, i] = np.median(img[np.ix_(rows, cols)].reshape(-1, 3), axis=0)
            if mask is not None:
                inside[j, i] = mask[np.ix_(rows, cols)].mean() > 0.5
    if mask is not None:
        # One cell of slack all round (wrapping along X): SD moves an edge by a cell or so.
        grown = inside | np.roll(inside, 1, axis=1) | np.roll(inside, -1, axis=1)
        grown[1:] |= grown[:-1].copy()
        grown[:-1] |= grown[1:].copy()
        inside = grown
    sky = np.median(small[:2].reshape(-1, 3), axis=0)
    d = np.linalg.norm(small - sky, axis=2)
    far = float(np.median(d[d > 48])) if (d > 48).any() else 48
    opaque = (d > far / 2) & inside
    speck = islands(opaque)
    if speck.any():
        print(f"{a.out}: {int(speck.sum())} cells of alpha islands cut", file=sys.stderr)
    opaque &= ~speck
    rgb = Image.fromarray(small.round().clip(0, 255).astype(np.uint8))
    src = Image.fromarray(small[opaque].round().clip(0, 255).astype(np.uint8)[None])
    if a.palette:
        ref = np.asarray(Image.open(a.palette).convert("RGBA"))
        src = Image.fromarray(ref[ref[..., 3] >= 128][None, :, :3])
    pal = src.quantize(a.colours, method=Image.Quantize.MEDIANCUT)
    out = np.zeros((ny, nx, 4), np.uint8)
    out[..., :3] = np.asarray(rgb.quantize(palette=pal, dither=Image.Dither.NONE).convert("RGB"))
    out[..., 3] = np.where(opaque, 255, 0)
    out[~opaque, :3] = 0
    Image.fromarray(out, "RGBA").save(a.out)
    print(f"{a.out}: {nx}x{ny}, {len(np.unique(out[opaque][:, :3], axis=0))} colours", file=sys.stderr)
    cut = int(opaque[0].sum())
    if cut:
        print(f"{a.out}: art cut flat at the top edge over {cut} columns", file=sys.stderr)
        sys.exit(3)


if __name__ == "__main__":
    main()
