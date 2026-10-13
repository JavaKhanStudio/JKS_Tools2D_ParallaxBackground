"""r358: before and after of layer.py's cut on an ai-page.sh example, each layer over magenta and over the layers behind.

    python3 tools/r358-glow/compose.py EXAMPLE BEFORE AFTER OUT.png [--last l3v2]

EXAMPLE is ai-page.sh's output (recipe.json, strips/); BEFORE and AFTER hold its cuts as <name>_<strip>.png
(recut.sh's names). The page is stacked flat: each layer's bottom dy % of the strip's height up, over the recipe's sky,
no zoom or scroll, so a glow round a shape shows over the layer behind it as it does on screen. --last: the SEQUENCE
layer's strip to stack (default its first). Left column before, right after.
"""
import argparse
import json
import os

from PIL import Image


def magenta(path):
    im = Image.open(path).convert("RGBA")
    bg = Image.new("RGBA", im.size, (255, 0, 255, 255))
    bg.alpha_composite(im)
    return bg.convert("RGB")


def stack(recipe, cuts, name, last):
    w, h = Image.open(os.path.join(cuts, f"{name}_{recipe['layers'][0]['strips'][0]['strip']}.png")).size
    top, foot = (tuple(int(c[i:i + 2], 16) for i in (0, 2, 4)) for c in recipe["sky"])
    sky = Image.new("RGBA", (w, h))
    for y in range(h):
        f = y / (h - 1)
        sky.paste(tuple(round(t + (b - t) * f) for t, b in zip(top, foot)) + (255,), (0, y, w, y + 1))
    for lay in recipe["layers"]:
        strips = [s["strip"] for s in lay["strips"]]
        strip = last if last in strips else strips[0]
        im = Image.open(os.path.join(cuts, f"{name}_{strip}.png")).convert("RGBA")
        bottom = round(h * (1 - lay["dy"] / 100))
        sky.alpha_composite(im, (0, bottom - im.height))
    return sky.convert("RGB")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("example")
    ap.add_argument("before")
    ap.add_argument("after")
    ap.add_argument("out")
    ap.add_argument("--last", default="")
    a = ap.parse_args()
    with open(os.path.join(a.example, "recipe.json")) as f:
        recipe = json.load(f)
    name = recipe["name"]
    rows = [(stack(recipe, a.before, name, a.last), stack(recipe, a.after, name, a.last))]
    for lay in recipe["layers"]:
        for s in lay["strips"]:
            rows.append(tuple(magenta(os.path.join(d, f"{name}_{s['strip']}.png")) for d in (a.before, a.after)))
    w, h = rows[0][0].size
    sheet = Image.new("RGB", (2 * w + 8, len(rows) * (h + 8)), (40, 40, 40))
    for i, (b, f) in enumerate(rows):
        sheet.paste(b, (0, i * (h + 8)))
        sheet.paste(f, (w + 8, i * (h + 8)))
    sheet.save(a.out)


if __name__ == "__main__":
    main()
