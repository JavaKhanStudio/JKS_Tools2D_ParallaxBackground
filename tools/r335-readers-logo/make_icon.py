#!/usr/bin/env python3
"""make_icon.py: the parallax-readers board mark (r335), engines/icon.png, made from the project logo docs/parallaxIcon.png.

The logo's hue is turned toward the #readers tag's violet, so the sub board tells apart from parallax's mark even at
favicon size. A round badge at the bottom right holds three dots, one per reader: libGDX (red), jME (blue) and Godot
(teal). Run from anywhere: tools/r335-readers-logo/make_icon.py
"""

from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent.parent
HUE_TURN = 45  # of 255: sky blue to violet


def turned(logo):
    alpha = logo.getchannel("A")
    h, s, v = logo.convert("RGB").convert("HSV").split()
    h = h.point(lambda x: (x + HUE_TURN) % 256)
    out = Image.merge("HSV", (h, s, v)).convert("RGBA")
    out.putalpha(alpha)
    return out


def badge(size):
    scale = 4  # drawn large, then shrunk: smooth edges
    big = size * scale
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((0, 0, big - 1, big - 1), fill=(255, 255, 255, 255), outline=(70, 40, 120, 255), width=big // 18)
    r = big // 9
    for i, colour in enumerate([(231, 76, 60), (52, 120, 220), (40, 170, 160)]):
        cx = big // 2 + (i - 1) * r * 5 // 2
        d.ellipse((cx - r, big // 2 - r, cx + r, big // 2 + r), fill=(*colour, 255))
    return img.resize((size, size), Image.LANCZOS)


def main():
    logo = Image.open(ROOT / "docs/parallaxIcon.png").convert("RGBA")
    out = turned(logo)
    size = out.width * 46 // 100
    out.alpha_composite(badge(size), (out.width - size - 2, out.height - size - 2))
    out.save(ROOT / "engines/icon.png", optimize=True)
    print(ROOT / "engines/icon.png", out.size)


if __name__ == "__main__":
    main()
