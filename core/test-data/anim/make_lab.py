"""The animation lab's beach atlas (r407): r365's AI page, repacked under the push check's 1 MB.

    python3 core/test-data/anim/make_lab.py build/r365/after/beach-painted/page/beach.png

Every 1024x384 strip keeps its size: the reader sizes a layer by its region's packed height, so a trimmed strip draws
another page (the sea went from 15 to 3.3 world units high). The sea, l1, the layer the lab animates, is its own page,
beach-sea.png; the others, the backdrop, share beach.png. Every pixel is as painted but the colour under a fully
transparent one more than a pixel from any other, set to 0: the painter's noise there cost 1.4 MB, and linear filtering
reads no farther than the next pixel (zeroed up to the edge, 3000 edge pixels drew up to 42/255 darker). beach.jplax is the page as generated, untouched.
"""
import os
import sys

from PIL import Image, ImageFilter

STRIPS = ["l0", "l1", "l2", "l3_0", "l3_1", "l3_2", "l3_3"]
W, H = 1024, 384


def region(name, y, h):
    return f"{name}\n  rotate: false\n  xy: 0, {y}\n  size: {W}, {h}\n  orig: {W}, {H}\n  offset: 0, 0\n  index: -1\n"


def page(png, size):
    return f"{png}\nsize: {size[0]}, {size[1]}\nformat: RGBA8888\nfilter: Linear,Linear\nrepeat: none\n"


def main():
    src = Image.open(sys.argv[1]).convert("RGBA")
    here = os.path.dirname(os.path.abspath(__file__))
    cut = {}
    for i, name in enumerate(STRIPS):
        strip = src.crop((0, i * (H + 1), W, i * (H + 1) + H))
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
