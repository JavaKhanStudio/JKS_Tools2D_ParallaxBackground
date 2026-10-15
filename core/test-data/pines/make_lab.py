"""The pines lab's page and strips (r421): R1's ds8-forest laid out on HEAD with misty_pines as it ships (on) and
forced off, no painting:

    python3 tools/r239-ai-strips/ai_page.py <ds8-forest's command> --keep --no-shots ON         (strips copied in)
    python3 tools/r239-ai-strips/ai_page.py <ds8-forest's command> --rule misty_pines=off --keep --no-shots OFF
    python3 core/test-data/pines/make_lab.py ON OFF

The two pages' .jplax are the same and so are every strip but the front's variants misty_pines keys (l3_1, l3_2 on
ds8-forest): forest.jplax is ON's, its atlas renamed pines.atlas (the browser build's assets are one folder: the
hills lab's forest.atlas is there). pines.atlas: pines-far.png (l0-l2), pines-front.png (the front's strips the rule
leaves alone), pines-on.png and pines-off.png (the ones it changes, as each lays them; off's regions named
<region>_off), and pines-marks.png: per front strip, red where on and off differ in alpha (m<i>, blank where they do not), which PinesLab lays
over the front as a SEQUENCE of the same seed, so the marks ride with the strip they belong to. Every strip keeps its
1024x384, the colour under a fully transparent pixel set to 0 (core/test-data/hills/make_lab.py's clean), so each
page stays under the push check's 1 MB. diff.json: the changed regions and their pixels.
"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
W, H = 1024, 384


def clean(strip):
    clear = strip.getchannel("A").filter(ImageFilter.MaxFilter(3)).point(lambda a: 255 if a == 0 else 0)
    strip = strip.copy()
    strip.paste((0, 0, 0, 0), mask=clear)
    return strip


def region(name, y):
    return f"{name}\n  rotate: false\n  xy: 0, {y}\n  size: {W}, {H}\n  orig: {W}, {H}\n  offset: 0, 0\n  index: -1\n"


def sheet(png, strips):
    img = Image.new("RGBA", (W, len(strips) * (H + 1) - 1), (0, 0, 0, 0))
    text = f"{png}\nsize: {img.width}, {img.height}\nformat: RGBA8888\nfilter: Linear,Linear\nrepeat: none\n"
    for i, (name, strip) in enumerate(strips):
        img.paste(clean(strip), (0, i * (H + 1)))
        text += region(name, i * (H + 1))
    img.save(os.path.join(HERE, png), optimize=True)
    return text


def strips(page):
    src = Image.open(os.path.join(page, "page", "forest.png")).convert("RGBA")
    with open(os.path.join(page, "page", "forest.atlas")) as f:
        lines = f.read().split("\n")
    at = {lines[i]: int(lines[i + 2].split(":")[1].split(",")[1]) for i in range(len(lines) - 2)
          if lines[i + 1].strip() == "rotate: false"}
    return {n: src.crop((0, y, W, y + H)) for n, y in at.items()}


def main():
    on_dir, off_dir = sys.argv[1:3]
    with open(os.path.join(on_dir, "page", "forest.jplax")) as a, open(os.path.join(off_dir, "page", "forest.jplax")) as b:
        page = a.read()
        if page != b.read():
            sys.exit("the two pages' .jplax differ: misty_pines moved more than pixels")
    on, off = strips(on_dir), strips(off_dir)
    front = sorted(n for n in on if n.startswith("l3_"))
    changed = [n for n in front if not np.array_equal(np.asarray(on[n]), np.asarray(off[n]))]
    if [n for n in on if n not in front and not np.array_equal(np.asarray(on[n]), np.asarray(off[n]))]:
        sys.exit("misty_pines changed a layer behind the front")
    diff, marks = {}, []
    for i, n in enumerate(front):
        a = np.asarray(on[n])[..., 3] > 127
        b = np.asarray(off[n])[..., 3] > 127
        mark = np.zeros((H, W, 4), np.uint8)
        mark[a != b] = (255, 30, 30, 255)
        marks.append((f"m{i}", Image.fromarray(mark)))
        if n in changed:
            diff[n] = int((a != b).sum())
    atlas = [sheet("pines-far.png", [(n, on[n]) for n in ("l0", "l1", "l2")]),
             sheet("pines-front.png", [(n, on[n]) for n in front if n not in changed]),
             sheet("pines-on.png", [(n, on[n]) for n in changed]),
             sheet("pines-off.png", [(n + "_off", off[n]) for n in changed]),
             sheet("pines-marks.png", marks)]
    with open(os.path.join(HERE, "pines.atlas"), "w") as f:
        f.write("\n".join(atlas))
    with open(os.path.join(HERE, "forest.jplax"), "w") as f:
        f.write(page.replace('"atlasName": "forest.atlas"', '"atlasName": "pines.atlas"'))
    with open(os.path.join(HERE, "diff.json"), "w") as f:
        json.dump({"changed": diff}, f, indent=1)
    print(diff)


if __name__ == "__main__":
    main()
