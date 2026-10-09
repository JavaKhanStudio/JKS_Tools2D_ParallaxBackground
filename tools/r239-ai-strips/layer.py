"""Cut a painted strip out of its flat sky into a parallax layer PNG with alpha (r239).

    python3 tools/r239-ai-strips/layer.py painted.png out.png [--pixel 4 --colours 12]

The sky colour is read from the strip's top rows; a pixel's alpha rises from 0 to 1 as its colour
moves away from it (between --near and --far, in 0..255 RGB distance; --far defaults to the
layer's own contrast). The strip loops already, and
this is per pixel, so the alpha loops too.
--pixel N: pixel art. Box-downscale by N, quantise to --colours colours (one palette for the
layer, or --palette's), and cut alpha hard at 50%: every pixel is one palette colour, fully opaque or fully clear.
The PNG is the small one; the page draws it scaled N times with the Nearest filter.
"""
import argparse
import sys

import numpy as np
from PIL import Image


def blur(a):
    """3x3 box, wrapping along X (the strip loops) and clamped along Y."""
    p = np.pad(a, [(1, 1), (0, 0)] + [(0, 0)] * (a.ndim - 2), mode="edge")
    p = np.concatenate([p[:, -1:], p, p[:, :1]], axis=1)
    out = sum(p[dy:dy + a.shape[0], dx:dx + a.shape[1]] for dy in range(3) for dx in range(3))
    return out / 9


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("out")
    ap.add_argument("--near", type=float, default=18)
    ap.add_argument("--far", type=float, default=0, help="0: 0.85 x the layer's median distance from the sky")
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
    rgb = np.where(solid[..., None] > 0, rgb, fill / np.maximum(wsum, 1e-6)[..., None])
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
