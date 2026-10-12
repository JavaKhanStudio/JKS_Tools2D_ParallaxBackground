#!/usr/bin/env python3
"""make_icon.py: the parallax-readers board mark (r335, redrawn for parallax-readers:r14), engines/icon.png.

Made from the project logo docs/parallaxIcon.png. Its six layers are found (see layers()) and each is painted in the colour
of one engine a reader draws in, back to front: ENGINES below, two layers each, the back one of a pair a shade darker. A
round badge at the bottom right holds an open book: the reader.
Run from anywhere: tools/r335-readers-logo/make_icon.py [--labels out.png] (--labels also writes the layer map, to check
the cut).
"""

import colorsys
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent.parent

# back to front; each engine takes two of the logo's six layers
ENGINES = [("libGDX", (231, 76, 60)), ("jME", (245, 166, 35)), ("Godot", (71, 140, 191))]
INK = (70, 40, 120)  # the badge's outline and book: the #readers violet

# The logo's layers, back to front: sky, cloud panel, mountains, mid strip, cloud strip, front strip. For each, the columns
# it spans and the colours it is painted in (the first is its flat base).
SPANS = [(0, 226), (34, 248), (60, 266), (84, 288), (108, 310), (134, 342)]
PAINTS = [
    [(95, 167, 249), (93, 166, 249)],
    [(141, 192, 250), (178, 212, 241), (157, 199, 243), (131, 179, 231), (183, 216, 242), (139, 191, 250)],
    [(106, 130, 163), (235, 240, 245), (160, 183, 208), (117, 139, 170), (200, 210, 225)],
    [(133, 171, 218), (148, 186, 225)],
    [(175, 217, 246), (153, 202, 242), (213, 241, 252)],
    [(210, 239, 252), (175, 217, 246), (200, 234, 249), (196, 233, 248)],
]


def layers(rgba):
    """The layer of every pixel, -1 where transparent. Down any column the layers only come nearer (the nearer panels
    are the lower ones), so each column is the non-decreasing labelling that best fits PAINTS; then each layer's top
    edge is smoothed across columns, so snow and cloud specks do not cut notches."""
    rgb, alpha = rgba[..., :3], rgba[..., 3]
    h, w = alpha.shape
    n = len(PAINTS)
    cost = np.stack([np.sqrt(((rgb[:, :, None, :] - np.array(p, float)) ** 2).sum(-1)).min(-1) for p in PAINTS])
    free = (np.abs(rgb - rgb.mean(-1, keepdims=True)).max(-1) < 12) | (alpha < 30)  # grey shadow fits any layer
    cost[:, free] = 0
    for k, (left, right) in enumerate(SPANS):
        cost[k][:, :left] += 1e6
        cost[k][:, right:] += 1e6
    lab = np.zeros((h, w), int)
    for x in range(w):
        c = cost[:, :, x]
        total = c[:, 0].copy()
        back = np.zeros((n, h), int)
        for y in range(1, h):
            prev = np.minimum.accumulate(total)
            arg = np.zeros(n, int)
            for k in range(1, n):
                arg[k] = arg[k - 1] if total[arg[k - 1]] <= total[k] else k
            back[:, y] = arg
            total = prev + c[:, y]
        k = int(total.argmin())
        for y in range(h - 1, -1, -1):
            lab[y, x] = k
            k = back[k, y]
    tops = np.array([[(lab[:, x] >= k).argmax() if (lab[:, x] >= k).any() else h for x in range(w)] for k in range(1, n)])
    pad = np.pad(tops, ((0, 0), (4, 4)), mode="edge")
    smooth = np.stack([np.median(pad[:, x:x + 9], axis=1) for x in range(w)], axis=1).astype(int)
    for x in range(w):
        lab[:, x] = 0
        for k in range(1, n):
            if tops[k - 1, x] < h:
                lab[smooth[k - 1, x]:, x] = k
    lab[alpha < 30] = -1
    return lab


def painted(rgba, lab):
    """Each layer turned to its engine's colour, keeping the logo's light and shade: a pixel keeps its saturation and
    value relative to its layer's base. Grey (shadow, edges) stays grey."""
    out = rgba.copy()
    for y, x in zip(*np.nonzero(lab >= 0)):
        k = lab[y, x]
        r, g, b = rgba[y, x, :3] / 255
        _, sp, vp = colorsys.rgb_to_hsv(r, g, b)
        if sp < 0.12:
            continue
        _, sb, vb = colorsys.rgb_to_hsv(*(np.array(PAINTS[k][0]) / 255))
        he, se, ve = colorsys.rgb_to_hsv(*(np.array(ENGINES[k // 2][1]) / 255))
        if k % 2 == 0:
            ve *= 0.85
        rgb = colorsys.hsv_to_rgb(he, se * min(1, sp / sb), min(1, ve * vp / vb))
        out[y, x, :3] = np.array(rgb) * 255
    return out


def badge(size):
    scale = 4  # drawn large, then shrunk: smooth edges
    big = size * scale
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((0, 0, big - 1, big - 1), fill=(255, 255, 255, 255), outline=(*INK, 255), width=big // 18)
    c, top, bottom, half, sag = big // 2, big * 30 // 100, big * 70 // 100, big * 30 // 100, big // 14
    for side in (-1, 1):  # an open book: two pages meeting at the spine, outer corners raised
        page = [(c, top + sag), (c + side * half, top), (c + side * half, bottom - sag), (c, bottom)]
        d.polygon(page, fill=(*INK, 255))
        for i in range(1, 4):  # the lines of text, in white
            t = i / 4.5
            y0 = top + sag + (bottom - top - sag) * t
            d.line([(c + side * half * 0.18, y0 - sag * 0.18), (c + side * half * 0.8, y0 - sag * 0.8)],
                   fill=(255, 255, 255, 255), width=big // 40)
    d.line([(c, top + sag), (c, bottom)], fill=(255, 255, 255, 255), width=big // 30)
    return img.resize((size, size), Image.LANCZOS)


def main():
    logo = Image.open(ROOT / "docs/parallaxIcon.png").convert("RGBA")
    rgba = np.array(logo).astype(float)
    lab = layers(rgba)
    out = Image.fromarray(painted(rgba, lab).round().clip(0, 255).astype("uint8"))
    size = out.width * 36 // 100  # small enough to leave the front (Godot) layers in sight
    out.alpha_composite(badge(size), (out.width - size - 2, out.height - size - 2))
    out.save(ROOT / "engines/icon.png", optimize=True)
    print(ROOT / "engines/icon.png", out.size)
    if "--labels" in sys.argv:
        hues = np.array([(255, 0, 0), (255, 160, 0), (0, 160, 0), (0, 0, 255), (160, 0, 200), (0, 200, 200)])
        img = np.full(lab.shape + (3,), 255)
        img[lab >= 0] = hues[lab[lab >= 0]]
        Image.fromarray(img.astype("uint8")).save(sys.argv[sys.argv.index("--labels") + 1])


if __name__ == "__main__":
    main()
