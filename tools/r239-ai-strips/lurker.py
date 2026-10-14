#!/usr/bin/env python3
"""A lurker's figure for ai-page.sh --lurker (r366): the init SD paints it over, and the check of its cut.

    python3 tools/r239-ai-strips/lurker.py init PREFIX --sky 9aa4b0 --colour 1f3a30 [--w 512 --h 768 --seed 1]
    python3 tools/r239-ai-strips/lurker.py crop PREFIX_cut.png OUT.png

init writes PREFIX_init.png and PREFIX_mask.png: one upright figure (a head over a body that widens to its foot) in
the colour of the layer it stands on, over the painted sky, textured so img2img paints detail into it (sil.py's
--texture). Not a strip: it does not loop, and the page draws it once every so many screens.
crop checks layer.py's cut (run with --no-creatures: this strip is the one meant to hold one) and writes OUT, the
figure's box: exactly one figure, whole. Exits 5 when it is not:
  - opaque pixels (alpha >= 128) outside the largest blob are more than 3% of them: two figures, or scenery;
  - the figure touches the image's left or right edge (cut off, or ground painted across it) or its top;
  - it is shorter than half the image, or wider than it is tall: ground or a landscape, not a standing figure.
"""
import argparse
import sys

import numpy as np
from layer import components
from PIL import Image
from sil import noise, soften

STRAY = 0.03  # opaque share allowed outside the figure's blob


def hex_rgb(h):
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)


def silhouette(w, h):
    """1 inside a standing figure 0.8 of h tall, its foot 4% over the bottom: a head a seventh of it on a neck,
    rounded shoulders, a body narrowing to the waist and widening to a hem. Generic: the prompt says what it is, the
    denoise lets SD reshape it."""
    y, x = np.mgrid[0:h, 0:w].astype(float)
    cx, top, foot = w / 2, h * 0.16, h * 0.96
    head_r = (foot - top) / 14
    head = ((x - cx) / (head_r * 0.8)) ** 2 + ((y - top - head_r) / head_r) ** 2 <= 1
    neck = (y > top + head_r) & (y < top + head_r * 2.4) & (np.abs(x - cx) <= head_r * 0.4)
    shoulder = top + head_r * 2.2
    t = np.clip((y - shoulder) / (foot - shoulder), 0, 1)  # 0 at the shoulders, 1 at the foot
    half = w * (0.12 - 0.05 * np.sin(np.pi * np.minimum(t / 0.8, 1)) ** 2 * (t < 0.8) + 0.02 * t)
    round_top = np.clip((y - shoulder) / (head_r * 0.8), 0, 1) ** 0.5  # the shoulders' curve
    body = (y >= shoulder) & (y <= foot) & (np.abs(x - cx) <= half * np.maximum(round_top, 0.35))
    return (head | neck | body).astype(float)


def init(a):
    rng = np.random.default_rng(a.seed)
    m = silhouette(a.w, a.h)
    sky, col = hex_rgb(a.sky), hex_rgb(a.colour)
    n = sum(amp * noise(rng, a.w, a.h, cells, 1.0) for cells, amp in ((5, 0.09), (13, 0.06), (34, 0.035)))
    soft = soften(m, max(2, a.h // 96))
    fill = col[None, None] * (1 + 0.6 * n + 0.3 * (np.roll(soft, 3, axis=1) - np.roll(soft, -3, axis=1)))[..., None]
    img = sky * (1 - m[..., None]) + fill * m[..., None]
    Image.fromarray(img.clip(0, 255).astype(np.uint8)).save(f"{a.prefix}_init.png")
    Image.fromarray((m * 255).astype(np.uint8)).save(f"{a.prefix}_mask.png")


def crop(a):
    im = Image.open(a.cut).convert("RGBA")
    alpha = np.asarray(im)[..., 3]
    h, w = alpha.shape
    opaque = alpha >= 128
    label, sizes = components(opaque)
    faults = []
    if not sizes:
        faults.append("nothing opaque")
    else:
        main = label == int(np.argmax(sizes)) + 1
        stray = 1 - max(sizes) / opaque.sum()
        if stray > STRAY:
            faults.append(f"{stray:.0%} of it outside the figure: more than one thing")
        ys, xs = np.nonzero(main)
        if xs.min() == 0 or xs.max() == w - 1 or ys.min() == 0:
            faults.append("the figure touches the image's edge")
        bh, bw = ys.max() - ys.min() + 1, xs.max() - xs.min() + 1
        if bh < h / 2 or bw > bh:
            faults.append(f"{bw}x{bh} px: not a standing figure")
    if faults:
        print(f"{a.cut}: refused: " + "; ".join(faults), file=sys.stderr)
        sys.exit(5)
    ys, xs = np.nonzero(alpha > 0)
    im.crop((max(0, xs.min() - 1), max(0, ys.min() - 1), min(w, xs.max() + 2), min(h, ys.max() + 2))).save(a.out)
    print(f"{a.out}: {bw}x{bh} px figure")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="step", required=True)
    i = sub.add_parser("init")
    i.add_argument("prefix")
    i.add_argument("--sky", required=True, help="the painted sky behind it, hex")
    i.add_argument("--colour", required=True, help="its fill, hex: the layer it stands on")
    i.add_argument("--w", type=int, default=512)
    i.add_argument("--h", type=int, default=768)
    i.add_argument("--seed", type=int, default=1)
    c = sub.add_parser("crop")
    c.add_argument("cut")
    c.add_argument("out")
    a = ap.parse_args()
    (init if a.step == "init" else crop)(a)


if __name__ == "__main__":
    main()
