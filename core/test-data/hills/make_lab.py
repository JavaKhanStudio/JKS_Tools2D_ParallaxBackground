"""The hills lab's page and strips (r384): r365's forest page, and its hills strip painted by each candidate recipe.

    python3 core/test-data/hills/make_lab.py build/r365/after/forest-painted build/r384/try

forest.jplax is the page as generated (build/r365/after/forest-painted/page), untouched. forest.atlas holds its backdrop,
forest.png (l0, l1) and forest-pines.png (l3_0-l3_3), and one page per candidate, hills-<name>.png: its strip for each seed in
SEEDS, region h_<name>_<seed>, cut by tools/r384-hills/try.py (sil.py, gen.py, layer.py as ai_page.py paints a hills
strip). HillsLab (core/browser-test) puts a candidate's region in place of the page's l2. Every strip keeps its 1024x384
(the reader sizes a layer by its region's packed height), every pixel as painted but the colour under a fully
transparent one more than a pixel from any other, set to 0, as core/test-data/anim/make_lab.py does, so each page is
under the push check's 1 MB. candidates.json: each candidate's name, recipe and measures (colour std inside the mask
shrunk by 8 px, alpha IoU against today's of the same seed), in the order the lab lists them. Never repaint them: a
lab judged on these pixels is judged on these pixels. Two exceptions: forest.png's l1 (the range) had see-through
pockets under its ridge, filled by tools/r466-range-holes/fill.py (r466), and its l0 (the far peaks) a free-standing twig
and a sliver between two peaks, redrawn by tools/r467-far-peaks/cut.py, and pockets like l1's (r467). After this script:
cut.py, then fill.py --region l0, then fill.py.
"""
import json
import os
import sys

from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "..", "tools", "r384-hills"))
import importlib  # noqa: E402

TRY = importlib.import_module("try")

W, H = 1024, 384
# The backdrop on two pages: on one it came to 1.004 MB.
BACKDROP = {"forest.png": ["l0", "l1"], "forest-pines.png": ["l3_0", "l3_1", "l3_2", "l3_3"]}
SEEDS = [7020, 7021, 7022]
# The lab's list: what ships, r384's patch Simon found worse, then the second round's (try.py's ctl*, inp*).
SHOWN = {  # short: a select clips a long name (r248); the about says the rest
    "today": "today (ships)",
    "o70n": "r384 patch",
    "inp": "inpaint 1.0",
    "inp85": "inpaint 0.85",
    "inpf": "inpaint 1.0, round",
    "inpf85": "inpaint 0.85, round",
    "ctlh": "outline-guided",
}


def clean(strip):
    clear = strip.getchannel("A").filter(ImageFilter.MaxFilter(3)).point(lambda a: 255 if a == 0 else 0)
    strip = strip.copy()
    strip.paste((0, 0, 0, 0), mask=clear)
    return strip


def region(name, y):
    return f"{name}\n  rotate: false\n  xy: 0, {y}\n  size: {W}, {H}\n  orig: {W}, {H}\n  offset: 0, 0\n  index: -1\n"


def sheet(png, strips):
    """strips: [(region, image)] stacked a pixel apart into HERE/png; its atlas page."""
    img = Image.new("RGBA", (W, len(strips) * (H + 1) - 1), (0, 0, 0, 0))
    text = f"{png}\nsize: {img.width}, {img.height}\nformat: RGBA8888\nfilter: Linear,Linear\nrepeat: none\n"
    for i, (name, strip) in enumerate(strips):
        img.paste(clean(strip), (0, i * (H + 1)))
        text += region(name, i * (H + 1))
    img.save(os.path.join(HERE, png), optimize=True)
    return text


def main():
    page, tried = sys.argv[1], sys.argv[2]
    src = Image.open(os.path.join(page, "page", "forest.png")).convert("RGBA")
    with open(os.path.join(page, "page", "forest.atlas")) as f:
        regions = f.read().split("\n")
    at = {regions[i]: int(regions[i + 2].split(":")[1].split(",")[1]) for i in range(len(regions) - 2)
          if regions[i + 1].strip() == "rotate: false"}
    atlas = [sheet(png, [(n, src.crop((0, at[n], W, at[n] + H))) for n in names]) for png, names in BACKDROP.items()]
    listed = []
    for name, shown in SHOWN.items():
        text, colour, denoise, more, how = (TRY.CANDIDATES[name] + ("", "img2img"))[:5]
        strips, measures = [], []
        for seed in SEEDS:
            p = os.path.join(tried, name, f"s{seed}")
            strips.append((f"h_{name}_{seed}", Image.open(p + "_cut.png").convert("RGBA")))
            std, iou = TRY.measure(p, os.path.join(tried, "today", f"s{seed}"))
            measures.append({"seed": seed, "std": round(std, 1), "iou": round(iou, 3)})
        atlas.append(sheet(f"hills-{name}.png", strips))
        listed.append({"name": name, "shown": shown, "how": how, "denoise": denoise, "prompt": text,
                       "negative more": more, "colour": colour, "measures": measures})
    with open(os.path.join(HERE, "forest.atlas"), "w") as f:
        f.write("\n".join(atlas))
    with open(os.path.join(HERE, "candidates.json"), "w") as f:
        json.dump({"seeds": SEEDS, "candidates": listed}, f, indent=1)
    page_json = os.path.join(page, "page", "forest.jplax")
    with open(page_json) as f, open(os.path.join(HERE, "forest.jplax"), "w") as out:
        out.write(f.read())


if __name__ == "__main__":
    main()
