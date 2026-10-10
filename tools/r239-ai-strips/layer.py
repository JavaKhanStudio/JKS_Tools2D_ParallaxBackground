"""Cut a painted strip out of its flat sky into a parallax layer PNG with alpha (r239).

    python3 tools/r239-ai-strips/layer.py painted.png out.png [--pixel 4 --colours 12]

The sky colour is read from the strip's top rows; a pixel's alpha rises from 0 to 1 as its colour
moves away from it (between --near and --far, in 0..255 RGB distance; --far defaults to the
layer's own contrast). The strip loops already, and
this is per pixel, so the alpha loops too.
--mask (sil.py's) --grow G: the silhouette helps the key, for a layer whose colours come near the sky's (snow under
a pale sky keys half clear): alpha is 1 inside the mask, and the colour key's only within G px of it, so what SD paints
far from the shape (a horizon behind hills) goes too. Pines grow past their shape: give them a larger G (r246).
Alpha islands that do not touch the layer's main mass (a speck SD left in the sky) are cut, with the
faint edge around them; connectivity is taken around the loop. --keep-islands keeps them, for a layer
meant to be scattered (clouds).
--pixel N: pixel art. Box-downscale by N, quantise to --colours colours (one palette for the
layer, or --palette's), and cut alpha hard at 50%: every pixel is one palette colour, fully opaque or fully clear.
The PNG is the small one; the page draws it scaled N times with the Nearest filter.
"""
import argparse
import sys
from collections import deque

import numpy as np
from PIL import Image


def blur(a):
    """3x3 box, wrapping along X (the strip loops) and clamped along Y."""
    p = np.pad(a, [(1, 1), (0, 0)] + [(0, 0)] * (a.ndim - 2), mode="edge")
    p = np.concatenate([p[:, -1:], p, p[:, :1]], axis=1)
    out = sum(p[dy:dy + a.shape[0], dx:dx + a.shape[1]] for dy in range(3) for dx in range(3))
    return out / 9


def spread(m, n):
    """m grown by n px (n < 0: shrunk), wrapping along X."""
    m = m.copy()
    for _ in range(abs(n)):
        g = m | np.roll(m, 1, 1) | np.roll(m, -1, 1) if n > 0 else m & np.roll(m, 1, 1) & np.roll(m, -1, 1)
        if n > 0:
            g[1:] |= m[:-1]
            g[:-1] |= m[1:]
        else:
            g[1:] &= m[:-1]
            g[:-1] &= m[1:]
        m = g
    return m


def islands(mask):
    """Each 4-connected component of mask but the largest, as one mask; column 0 touches column w-1."""
    h, w = mask.shape
    label = np.zeros(mask.shape, np.int32)
    sizes = [0]
    for y0, x0 in zip(*np.nonzero(mask)):
        if label[y0, x0]:
            continue
        n = len(sizes)
        label[y0, x0] = n
        todo, size = deque([(y0, x0)]), 0
        while todo:
            y, x = todo.popleft()
            size += 1
            for ny, nx in ((y - 1, x), (y + 1, x), (y, (x - 1) % w), (y, (x + 1) % w)):
                if 0 <= ny < h and mask[ny, nx] and not label[ny, nx]:
                    label[ny, nx] = n
                    todo.append((ny, nx))
        sizes.append(size)
    if len(sizes) <= 2:
        return np.zeros(mask.shape, bool)
    main = int(np.argmax(sizes))
    return (label > 0) & (label != main)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("out")
    ap.add_argument("--near", type=float, default=18)
    ap.add_argument("--far", type=float, default=0, help="0: 0.85 x the layer's median distance from the sky")
    ap.add_argument("--mask", help="sil.py's _mask.png: opaque inside it, keyed only within --grow px of it")
    ap.add_argument("--grow", type=int, default=12)
    ap.add_argument("--keep-islands", action="store_true", help="keep alpha islands apart from the main mass")
    ap.add_argument("--pixel", type=int, default=0)
    ap.add_argument("--colours", type=int, default=12)
    ap.add_argument("--palette", help="--pixel: build the palette from this cut layer's opaque colours (one for all segments)")
    a = ap.parse_args()
    img = np.asarray(Image.open(a.src).convert("RGB")).astype(np.float32)
    sky = np.median(img[:8].reshape(-1, 3), axis=0)
    d = np.linalg.norm(img - sky, axis=2)
    # The ramp ends near the layer's own distance from the sky: a fixed one marks a pixel half
    # sky, half hill as solid, a light line along the silhouette.
    far = a.far if a.far else 0.85 * float(np.median(d[d > 48])) if (d > 48).any() else 48
    alpha = np.clip((d - a.near) / (far - a.near), 0, 1)
    if a.mask:
        m = np.asarray(Image.open(a.mask).convert("L").resize(alpha.shape[::-1])) > 127
        # SD moves an outline by a few px: opaque only 8 px inside the shape, or sky comes in with it.
        alpha = np.where(spread(m, -8), 1.0, np.where(spread(m, a.grow), alpha, 0.0))
    if not a.keep_islands:
        cut = islands(alpha > 0.5)
        if cut.any():
            # The island's faint edge goes with it: alpha within 3 px of it, outside the main mass.
            near = blur(blur(blur(cut.astype(np.float32)))) > 0
            alpha[near & ~((alpha > 0.5) & ~cut)] = 0
            print(f"{a.out}: {int(cut.sum())} px of alpha islands cut", file=sys.stderr)
    # Un-mix the sky out of the edge pixels, so a light fringe does not ring the layer.
    a3 = np.maximum(alpha, 1e-3)[..., None]
    rgb = np.clip((img - sky * (1 - a3)) / a3, 0, 255)
    # Edge pixels take their colour from the solid ones next to them. SD paints a rim lighter
    # than the sky along a silhouette, which no un-mix removes (a 1 px light outline on screen),
    # and a Linear filter blends the colour of clear pixels into the edge too.
    solid = (alpha >= 0.95).astype(np.float32)
    fill, wsum = rgb * solid[..., None], solid.copy()
    for _ in range(6):  # spread the solid colours outward, ~2^6 px
        fill, wsum = blur(fill), blur(wsum)
    # Past their reach (a pale ridge, alpha under 0.95 over more than ~6 px) the un-mixed colour stays:
    # fill / wsum there is 0 / 0, and was drawn black (r242's "bird").
    reached = (wsum > 1e-6)[..., None]
    rgb = np.where(solid[..., None] > 0, rgb, np.where(reached, fill / np.maximum(wsum, 1e-6)[..., None], rgb))
    rgba = np.dstack([rgb, alpha * 255]).astype(np.uint8)
    out = Image.fromarray(rgba, "RGBA")
    if a.pixel > 1:
        w, h = out.size
        small = out.resize((w // a.pixel, h // a.pixel), Image.BOX)
        arr = np.asarray(small).copy()
        opaque = arr[..., 3] >= 128
        rgb = Image.fromarray(arr[..., :3])
        if a.palette:
            # The segments of one SEQUENCE layer share a palette, or their joins change colour.
            ref = Image.open(a.palette).convert("RGBA")
            ref = np.asarray(ref.resize((ref.width // a.pixel, ref.height // a.pixel), Image.BOX))
            ref = Image.fromarray(ref[ref[..., 3] >= 128][None, :, :3])  # its opaque colours, one row
            ref = ref.quantize(a.colours, method=Image.Quantize.MEDIANCUT)
            pal = rgb.quantize(palette=ref, dither=Image.Dither.NONE)
        else:
            pal = rgb.quantize(a.colours, method=Image.Quantize.MEDIANCUT)
        arr[..., :3] = np.asarray(pal.convert("RGB"))
        arr[..., 3] = np.where(opaque, 255, 0)
        arr[~opaque, :3] = 0
        out = Image.fromarray(arr, "RGBA")
    out.save(a.out)
    cut = int((np.asarray(out)[0, :, 3] > 0).sum())
    if cut:
        # Art on the top row is cut flat on screen (the parallax_lab lint's fault a).
        print(f"{a.out}: art cut flat at the top edge over {cut} columns", file=sys.stderr)
        sys.exit(3)


if __name__ == "__main__":
    main()
