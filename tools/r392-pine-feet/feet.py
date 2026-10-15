#!/usr/bin/env python3
"""Do a depth page's farther pines skate on the field under their feet? (d18, r392)

    python3 tools/r392-pine-feet/feet.py build/ai/depth/river OUT.png [X0 Y0 X1 Y1]

Draws the page's layers (OUT/cut/l*.png at their recipe.json tops) as the camera moves, each slid by its own speed,
wrapped as the page loops, and crops the box (painting pixels; default river's small pines on the left) at the
moments the near layer has slid 0, 20, 40 and 60 px past the pines' layer. Beside them, the box in bands.png: which
band each pixel went to. A pine whose trunk sits in one band and the grass under its foot in a faster one is seen
leaving its ground. The near layer's top edge is drawn in red.
"""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

SCALE = 3
SLIDES = (0, 20, 40, 60)


def frame(cut, layers, slide):
    """The painting as the camera moves, the pines' layer (the one behind the nearest) held still: each layer slid
    left by its speed past the pines', in units where the nearest one has slid slide px. Also the nearest layer's
    alpha, where it lies."""
    w, h = Image.open(os.path.join(cut, "painting.png")).size
    out = Image.new("RGBA", (w, h), (0, 0, 0, 255))
    near = Image.new("L", (w, h), 0)
    held, front = layers[-2]["speed"], layers[-1]["speed"]
    for layer in layers:
        strip = Image.open(os.path.join(cut, layer["strip"] + ".png")).convert("RGBA")
        dx = round(slide * (layer["speed"] - held) / (front - held)) % w
        rolled = Image.new("RGBA", strip.size)
        rolled.paste(strip.crop((dx, 0, w, strip.height)), (0, 0))
        rolled.paste(strip.crop((0, 0, dx, strip.height)), (w - dx, 0))
        out.alpha_composite(rolled, (0, layer["top"]))
        if layer is layers[-1]:
            near.paste(rolled.getchannel("A"), (0, layer["top"]))
    return out.convert("RGB"), near


def outline(pane, alpha):
    """The nearest layer's top edge drawn in red over pane (both at the same size)."""
    edge = alpha.point(lambda a: 255 if a > 127 else 0).filter(ImageFilter.FIND_EDGES)
    pane.paste((255, 40, 40), mask=edge.point(lambda a: 255 if a else 0))
    return pane


def main():
    run, dest = sys.argv[1], sys.argv[2]
    box = tuple(int(v) for v in sys.argv[3:7]) if len(sys.argv) >= 7 else (20, 250, 220, 420)
    cut = os.path.join(run, "cut")
    with open(os.path.join(run, "recipe.json")) as f:
        layers = json.load(f)["layers"]
    bw, bh = (box[2] - box[0]) * SCALE, (box[3] - box[1]) * SCALE
    panes = [Image.open(os.path.join(cut, "bands.png")).convert("RGB").crop(box).resize((bw, bh), Image.NEAREST)]
    labels = ["bands: yellow = pines' layer, red = near layer"]
    for slide in SLIDES:
        f, near = frame(cut, layers, slide)
        panes.append(outline(f.crop(box).resize((bw, bh), Image.LANCZOS), near.crop(box).resize((bw, bh))))
        labels.append(f"near layer slid {slide} px past the pines")
    pad = 30
    sheet = Image.new("RGB", (bw * len(panes) + 10 * (len(panes) - 1), bh + pad), (255, 255, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (pane, label) in enumerate(zip(panes, labels)):
        x = i * (bw + 10)
        sheet.paste(pane, (x, pad))
        draw.text((x + 4, 8), label, fill=(0, 0, 0))
    sheet.save(dest)


if __name__ == "__main__":
    main()
