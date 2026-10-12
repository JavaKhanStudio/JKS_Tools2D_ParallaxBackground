#!/usr/bin/env python3
"""make_icon.py: the parallax-ai board mark (r345), ../JKS_Tools2D_ParallaxAI/icon.png.

Made as parallax-readers' mark (tools/r335-readers-logo) is: the project logo docs/parallaxIcon.png cut into its six
layers by that script's layers(), each painted along a dusk ramp, back to front (a generated sky rather than an engine's
colour), and a round badge at the bottom right holding sparkles: the generator.
Run from anywhere: tools/r345-ai-logo/make_icon.py [OUT] (default: the parallax-ai checkout beside this one).
"""

import colorsys
import importlib.util
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent.parent
_spec = importlib.util.spec_from_file_location("readers_logo", ROOT / "tools/r335-readers-logo/make_icon.py")
readers_logo = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(readers_logo)

# back to front, one per layer: deep violet sky to a warm front
RAMP = [(88, 52, 150), (130, 62, 170), (176, 70, 160), (214, 84, 132), (240, 120, 100), (250, 172, 92)]
INK = (88, 40, 130)  # the badge's outline and sparkles


def painted(rgba, lab):
    """Each layer turned to its ramp colour, keeping the logo's light and shade, as r335's painted() does."""
    out = rgba.copy()
    for y, x in zip(*np.nonzero(lab >= 0)):
        k = lab[y, x]
        r, g, b = rgba[y, x, :3] / 255
        _, sp, vp = colorsys.rgb_to_hsv(r, g, b)
        if sp < 0.12:
            continue
        _, sb, vb = colorsys.rgb_to_hsv(*(np.array(readers_logo.PAINTS[k][0]) / 255))
        he, se, ve = colorsys.rgb_to_hsv(*(np.array(RAMP[k]) / 255))
        rgb = colorsys.hsv_to_rgb(he, min(1, se * sp / sb), min(1, ve * vp / vb))
        out[y, x, :3] = np.array(rgb) * 255
    return out


def sparkle(d, cx, cy, r, fill):
    """A four-pointed star: its points r from the centre, its waist r/4."""
    w = r / 4
    d.polygon([(cx, cy - r), (cx + w, cy - w), (cx + r, cy), (cx + w, cy + w),
               (cx, cy + r), (cx - w, cy + w), (cx - r, cy), (cx - w, cy - w)], fill=fill)


def badge(size):
    scale = 4  # drawn large, then shrunk: smooth edges
    big = size * scale
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.ellipse((0, 0, big - 1, big - 1), fill=(255, 255, 255, 255), outline=(*INK, 255), width=big // 18)
    sparkle(d, big * 0.44, big * 0.56, big * 0.30, (*INK, 255))
    sparkle(d, big * 0.71, big * 0.30, big * 0.14, (*RAMP[3], 255))
    sparkle(d, big * 0.74, big * 0.70, big * 0.09, (*RAMP[5], 255))
    return img.resize((size, size), Image.LANCZOS)


def main():
    out_path = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT.parent / "JKS_Tools2D_ParallaxAI/icon.png"
    logo = Image.open(ROOT / "docs/parallaxIcon.png").convert("RGBA")
    rgba = np.array(logo).astype(float)
    lab = readers_logo.layers(rgba)
    out = Image.fromarray(painted(rgba, lab).round().clip(0, 255).astype("uint8"))
    size = out.width * 46 // 100
    out.alpha_composite(badge(size), (out.width - size - 2, out.height - size - 2))
    out.save(out_path, optimize=True)
    print(out_path, out.size)


if __name__ == "__main__":
    main()
