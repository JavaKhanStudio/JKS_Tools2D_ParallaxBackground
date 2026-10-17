"""The animation lab's beach atlas (r407, r478): an AI page's atlas, repacked under the push check's 1 MB.

    python3 core/test-data/anim/make_lab.py build/r478/e-final/page/beach.atlas

Reads the generated atlas's regions wherever it packed them and brings each strip to 1024x384: the sea as painted at
that size by r365, the others (painted 1536x576 by an SDXL model, r478) scaled down, premultiplied, Lanczos. A layer's
world size comes from its strip's shape, 8:3 either way, so the page is the same page; the generated sheet was 6 MB.
Every 1024x384 strip keeps its full size: the reader sizes a layer by its region's packed height, so a trimmed strip
draws another page (the sea went from 15 to 3.3 world units high). The sea, l1, the layer the lab animates, is its own
page, beach-sea.png; the others, the backdrop, share beach.png. Every pixel is as painted but the colour under a fully
transparent one more than a pixel from any other, set to 0: the painter's noise there cost 1.4 MB, and linear
filtering reads no farther than the next pixel (zeroed up to the edge, 3000 edge pixels drew up to 42/255 darker).
beach.jplax is the page as generated, untouched.
"""
import os
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

STRIPS = ["l0", "l1", "l2", "l3_0", "l3_1", "l3_2", "l3_3"]
W, H = 1024, 384


def region(name, y, h):
    return f"{name}\n  rotate: false\n  xy: 0, {y}\n  size: {W}, {h}\n  orig: {W}, {H}\n  offset: 0, 0\n  index: -1\n"


def page(png, size):
    return f"{png}\nsize: {size[0]}, {size[1]}\nformat: RGBA8888\nfilter: Linear,Linear\nrepeat: none\n"


def regions_of(atlas):
    """Each region's (x, y, w, h) in the atlas's one page, and that page's file."""
    lines = Path(atlas).read_text().splitlines()
    png, found, name = lines[0].strip() or lines[1].strip(), {}, None
    for line in lines:
        if line and not line.startswith(" ") and ":" not in line and not line.endswith(".png"):
            name = line.strip()
        elif name and line.strip().startswith(("xy:", "size:")):
            key, value = line.strip().split(":")
            found.setdefault(name, {})[key] = [int(v) for v in value.split(",")]
    return os.path.join(os.path.dirname(atlas), png), {n: (*r["xy"], *r["size"]) for n, r in found.items()}


def scaled(strip):
    """The strip at W x H, its colour premultiplied while it is filtered, so a clear pixel's colour does not bleed."""
    if strip.size == (W, H):
        return strip
    a = np.asarray(strip, dtype=np.float32) / 255
    a[..., :3] *= a[..., 3:]
    bands = [Image.fromarray(a[..., i]).resize((W, H), Image.LANCZOS) for i in range(4)]
    b = np.clip(np.stack([np.asarray(x) for x in bands], axis=-1), 0, 1)
    b[..., :3] /= np.maximum(b[..., 3:], 1e-6)
    return Image.fromarray(np.round(np.clip(b, 0, 1) * 255).astype(np.uint8), "RGBA")


def main():
    png, where = regions_of(sys.argv[1])
    src = Image.open(png).convert("RGBA")
    here = os.path.dirname(os.path.abspath(__file__))
    cut = {}
    for name in STRIPS:
        x, y, w, h = where[name]
        strip = scaled(src.crop((x, y, x + w, y + h)))
        clear = strip.getchannel("A").filter(ImageFilter.MaxFilter(3)).point(lambda a: 255 if a == 0 else 0)
        strip.paste((0, 0, 0, 0), mask=clear)
        cut[name] = strip
    sea = cut["l1"]
    sea.save(os.path.join(here, "beach-sea.png"), optimize=True)
    rest = [n for n in STRIPS if n != "l1"]
    sheet = Image.new("RGBA", (W, sum(cut[n].height + 1 for n in rest) - 1), (0, 0, 0, 0))
    regions, y = [], 0
    for n in rest:
        sheet.paste(cut[n], (0, y))
        regions.append(region(n, y, cut[n].height))
        y += cut[n].height + 1
    sheet.save(os.path.join(here, "beach.png"), optimize=True)
    with open(os.path.join(here, "beach.atlas"), "w") as atlas:
        atlas.write(page("beach-sea.png", sea.size) + region("l1", 0, sea.height) + "\n"
                    + page("beach.png", sheet.size) + "".join(regions))


if __name__ == "__main__":
    main()
