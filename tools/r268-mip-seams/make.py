#!/usr/bin/env python3
"""r268: does a mipmapped atlas show a line at its tile joins at deep mip levels, now that readers inset a region by
half a texel (r218)? Writes OUT/mip.atlas (filter MipMapLinearLinear), OUT/mip.png, OUT/mip.jplax and OUT/round.json.

    python3 tools/r268-mip-seams/make.py OUT [--filter MipMapLinearLinear,Linear] [--borders 0,1,25] [--x 37]
    tools/parallax-lab-shots.sh OUT OUT/shots && python3 tools/r268-mip-seams/measure.py OUT

One art, 1024x128 of opaque horizontal colour bands (constant along x, so it loops perfectly: any line at a join is the
atlas, not the art), packed three times at an x that is no multiple of 2: with no edge duplicated into the padding
(pure.atlas's case), with 1 texel duplicated (a padding of 2 split between two regions), and with 25 (the editor's
PixmapPacker, padding 50). Each is tiled on X at three sizes, 1/4, 1/8 and 1/16 of the art's width on a 1280 px
screen: mip levels 2, 3 and 4. Rows, top to bottom: none x4, x8, x16, then 1 texel, then 25. --borders packs other
widths (region bN: N texels duplicated), --x places the regions at another column (37 by default).
"""
import json
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import parallax_lab as lab  # noqa: E402

W, H = 1024, 128
BANDS = [(240, 200, 60), (230, 90, 70), (90, 190, 120), (70, 140, 230), (240, 240, 240), (200, 110, 220), (250, 160, 60),
         (120, 220, 230)]
BORDERS = [0, 1, 25]
SCREEN_W = 1280
SHRINK = (4, 8, 16)  # screen px per art px: 1/4, 1/8, 1/16


def art():
    img = Image.new("RGBA", (W, H))
    band = H // len(BANDS)
    for i, c in enumerate(BANDS):
        img.paste(c + (255,), (0, i * band, W, (i + 1) * band))
    return img


def main(out, filt, borders, x0):
    os.makedirs(out, exist_ok=True)
    a = art()
    pad = 50
    sheet = Image.new("RGBA", (2048, 1024), (0, 0, 0, 0))
    lines = ["mip.png", f"size: {sheet.width}, {sheet.height}", "format: RGBA8888", f"filter: {filt}", "repeat: none"]
    y = 37  # odd, and x too: a region edge then falls inside a mip block at every level
    for b in borders:
        name, x = f"b{b}", x0
        if b:
            # The edge columns and rows copied b texels out, as the packers' duplicatePadding does.
            sheet.paste(a.crop((0, 0, 1, H)).resize((b, H)), (x - b, y))
            sheet.paste(a.crop((W - 1, 0, W, H)).resize((b, H)), (x + W, y))
            sheet.paste(a.crop((0, 0, W, 1)).resize((W, b)), (x, y - b))
            sheet.paste(a.crop((0, H - 1, W, H)).resize((W, b)), (x, y + H))
        sheet.paste(a, (x, y))
        lines += [name, "  rotate: false", f"  xy: {x}, {y}", f"  size: {W}, {H}", f"  orig: {W}, {H}",
                  "  offset: 0, 0", "  index: -1"]
        y += H + pad + 1
    sheet.save(os.path.join(out, "mip.png"), optimize=True)
    with open(os.path.join(out, "mip.atlas"), "w") as f:
        f.write("\n".join(lines) + "\n")
    layers, dy = [], 92.0
    for b in borders:
        for s in SHRINK:
            # A layer is the world's width x size wide: the art drawn W / s screen px wide.
            layers.append(lab.layer(f"b{b}", speed=0.05, speed_y=0.0, size=W / s / SCREEN_W, dy=dy))
            dy -= min(10.0, 90.0 / (len(borders) * len(SHRINK)))
    page = lab.page_of("mip.atlas", layers, sky=("101010", "101010"), ground=("101010", "101010"), top_size=0.5,
                       bottom_size=0.5)
    with open(os.path.join(out, "mip.jplax"), "w") as f:
        json.dump(page, f, indent=1)
    rel = os.path.relpath(out, lab.ROOT)
    with open(os.path.join(out, "round.json"), "w") as f:
        json.dump({"round": "r268-mip-seams", "about": __doc__.splitlines()[0], "scenes": [
            {"id": "m01", "page": f"{rel}/mip.jplax", "atlasDir": rel, "name": filt,
             "about": "rows top to bottom: x4 x8 x16 of each region bN, N texels of edge duplicated: "
                      + ", ".join(f"b{b}" for b in borders)}]},
                  f, indent=1)


if __name__ == "__main__":
    args = sys.argv[1:]
    opts = {"--filter": "MipMapLinearLinear,Linear", "--borders": "0,1,25", "--x": "37"}
    for k in opts:
        if k in args:
            i = args.index(k)
            opts[k] = args[i + 1]
            del args[i:i + 2]
    main(os.path.abspath(args[0]), opts["--filter"], [int(b) for b in opts["--borders"].split(",")], int(opts["--x"]))
