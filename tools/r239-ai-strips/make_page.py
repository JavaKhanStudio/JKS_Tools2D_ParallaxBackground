#!/usr/bin/env python3
"""r239: the generated strips as a real page, and the round that renders it with core.

    python3 tools/r239-ai-strips/make_page.py LAYER_DIR [--xl]
    python3 tools/parallax_lab.py lint tools/r239-ai-strips/page/painted.jplax
    tools/parallax-lab-shots.sh tools/r239-ai-strips/page build/lab/r239

LAYER_DIR holds what run.sh writes: mountains_layer.png, hills_layer.png and pinesw0..3_layer.png
(painted), the same names with _px for pixel art. Packs each set into one atlas page by hand
(rows of strips, no whitespace stripping), writes painted.atlas/.png and pixel.atlas/.png, a page for
each (the four treelines as one SEQUENCE layer) and round.json, all in tools/r239-ai-strips/page.
--xl (r243): LAYER_DIR holds what run-xl.sh writes instead, {mountains,hills,pines}_xlpx.png, true pixel
art from SDXL; writes pixel-xl.atlas/.png and its page, and leaves the other two as they are. round.json
gets a scene for each page that is there.
"""
import json
import os
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "page")
sys.path.insert(0, os.path.dirname(HERE))
import parallax_lab as lab  # noqa: E402

SKY = ("8fb4e0", "e1e8f0")


def pack(name, files, filt):
    """One atlas page, the strips stacked in a column, 1 px apart."""
    imgs = [(region, Image.open(path).convert("RGBA")) for region, path in files]
    w = max(i.width for _, i in imgs)
    h = sum(i.height + 1 for _, i in imgs)
    sheet = Image.new("RGBA", (w, h))
    lines = [f"{name}.png", f"size: {w}, {h}", "format: RGBA8888", f"filter: {filt}", "repeat: none"]
    y = 0
    for region, img in imgs:
        sheet.paste(img, (0, y))
        lines += [region, "  rotate: false", f"  xy: 0, {y}", f"  size: {img.width}, {img.height}",
                  f"  orig: {img.width}, {img.height}", "  offset: 0, 0", "  index: -1"]
        y += img.height + 1
    sheet.save(os.path.join(OUT, f"{name}.png"), optimize=True)
    with open(os.path.join(OUT, f"{name}.atlas"), "w") as f:
        f.write("\n".join(lines) + "\n")


def page(atlas):
    pines = lab.layer("", speed=0.04, size=1.0, dy=0.0)
    pines.update(kind="SEQUENCE", name="pines", sequenceSeed=239, sequenceLength=8,
                 sequenceSegments=[{"regionName": f"pines{i}", "regionPosition": 0, "weight": 1} for i in range(4)])
    return lab.page_of(f"{atlas}.atlas", [
        lab.layer("mountains", speed=0.01, size=1.0, dy=14.0),
        lab.layer("hills", speed=0.02, size=1.0, dy=4.0),
        pines,
    ], sky=SKY, ground=("1e2b28", "1e2b28"), top_size=0.0, bottom_size=1.0)


def page_xl():
    return lab.page_of("pixel-xl.atlas", [
        lab.layer("mountains", speed=0.01, size=1.0, dy=14.0),
        lab.layer("hills", speed=0.02, size=1.0, dy=4.0),
        lab.layer("pines", speed=0.04, size=1.0, dy=0.0),
    ], sky=SKY, ground=("1e2b28", "1e2b28"), top_size=0.0, bottom_size=1.0)


def main(src, xl=False):
    os.makedirs(OUT, exist_ok=True)
    if xl:
        pack("pixel-xl", [(k, os.path.join(src, f"{k}_xlpx.png")) for k in ("mountains", "hills", "pines")],
             "Nearest,Nearest")
        with open(os.path.join(OUT, "pixel-xl.jplax"), "w") as f:
            json.dump(page_xl(), f, indent=1)
    else:
        for atlas, suffix, filt in (("painted", "layer", "Linear,Linear"), ("pixel", "px", "Nearest,Nearest")):
            files = [("mountains", f"mountains_{suffix}.png"), ("hills", f"hills_{suffix}.png")]
            files += [(f"pines{i}", f"pinesw{i}_{suffix}.png") for i in range(4)]
            pack(atlas, [(r, os.path.join(src, p)) for r, p in files], filt)
            with open(os.path.join(OUT, f"{atlas}.jplax"), "w") as f:
                json.dump(page(atlas), f, indent=1)
    rel = os.path.relpath(OUT, lab.ROOT)
    scenes = [
        {"id": "g01", "page": f"{rel}/painted.jplax", "atlasDir": rel, "name": "painted",
         "about": "SD1.5 strips painted over periodic silhouettes, circular padding; pines a SEQUENCE of 4 variants"},
        {"id": "g02", "page": f"{rel}/pixel.jplax", "atlasDir": rel, "name": "pixel",
         "about": "the same strips cut to pixel art (/4, 10 colours, hard alpha), drawn Nearest"},
        {"id": "g03", "page": f"{rel}/pixel-xl.jplax", "atlasDir": rel, "name": "pixel-xl",
         "about": "r243: SDXL + PixelArt_XL, one PNG pixel per art pixel (16 px cells), 12 colours a layer, drawn Nearest"},
    ]
    scenes = [s for s in scenes if os.path.exists(os.path.join(lab.ROOT, s["page"]))]
    with open(os.path.join(OUT, "round.json"), "w") as f:
        json.dump({"round": "r239-ai-strips", "about": __doc__.splitlines()[0], "scenes": scenes}, f, indent=1)


if __name__ == "__main__":
    main(sys.argv[1], "--xl" in sys.argv[2:])
