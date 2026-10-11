#!/usr/bin/env python3
"""r315's proof: depth-page's repaints before (r279: dreamshaper_8, SetLatentNoiseMask) and after (dreamshaper_8Inpainting,
InpaintModelConditioning without a noise mask), side by side.

    tools/r279-depth-page/examples.sh build/r315      # the same paintings as build/r279, repainted again
    python3 tools/r315-inpaint/compare.py build/r279 build/r315

Writes into the second directory: {noloop,valley,river}-cmp.png (each page's 6 and 12 s stills, old above new: the
noloop join's band), back-layers.png (each page's back layer, old left, new right: birds in a repainted sky) and l2.png
(layer 2 of valley and noloop on magenta: the patch under the trunk).
"""
import os
import sys

from PIL import Image, ImageDraw


def label(im, text):
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, 8 + 7 * len(text), 22), fill="black")
    d.text((5, 5), text, fill="white")
    return im


def grid(tiles, cols, out):
    w, h = max(t.width for t in tiles), max(t.height for t in tiles)
    o = Image.new("RGB", (w * cols, h * -(-len(tiles) // cols)), "black")
    for i, t in enumerate(tiles):
        o.paste(t, ((i % cols) * w, (i // cols) * h))
    o.save(out)
    print(out)


def main(old, new):
    runs = [(os.path.basename(old.rstrip("/")), old), (os.path.basename(new.rstrip("/")), new)]
    for page in ("noloop", "valley", "river"):
        tiles = []
        for name, d in runs:
            for t in (6, 12):
                im = Image.open(f"{d}/{page}/shots/d01-t{t}.png").convert("RGB")
                tiles.append(label(im.resize((im.width // 2, im.height // 2)), f"{name} {page} t={t}s"))
        grid(tiles, 2, f"{new}/{page}-cmp.png")
    tiles = []
    for page in ("valley", "river", "noloop"):
        for name, d in runs:
            im = Image.open(f"{d}/{page}/cut/l0.png").convert("RGB")
            tiles.append(label(im.resize((im.width * 5 // 8, im.height * 5 // 8)), f"{name} {page} back layer"))
    grid(tiles, 2, f"{new}/back-layers.png")
    tiles = []
    for page in ("valley", "noloop"):
        for name, d in runs:
            im = Image.open(f"{d}/{page}/cut/l2.png").convert("RGBA")
            bg = Image.new("RGBA", im.size, (255, 0, 255, 255))
            bg.alpha_composite(im)
            tiles.append(label(bg.convert("RGB").crop((300, 0, 900, im.height)), f"{name} {page} l2"))
    grid(tiles, 2, f"{new}/l2.png")


if __name__ == "__main__":
    main(*sys.argv[1:3])
