"""r365: two paintings of one page, strip by strip, each strip tiled twice (the join in the middle shows a seam).

    python3 tools/r365-init/sheet.py BEFORE AFTER OUT.png [--cut] [--scale 0.5]

BEFORE and AFTER are ai-page.sh outputs painted from the same command (recipe.json, strips/). Per strip: BEFORE's
then AFTER's, the painted _ai.png (--cut: the _cut.png over magenta), drawn twice side by side at --scale.
"""
import argparse
import json
import os

from PIL import Image, ImageDraw


def twice(path, cut, scale):
    im = Image.open(path).convert("RGBA")
    if cut:
        bg = Image.new("RGBA", im.size, (255, 0, 255, 255))
        bg.alpha_composite(im)
        im = bg
    im = im.convert("RGB")
    out = Image.new("RGB", (2 * im.width, im.height))
    out.paste(im, (0, 0))
    out.paste(im, (im.width, 0))
    return out.resize((round(out.width * scale), round(out.height * scale)), Image.LANCZOS)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("before")
    ap.add_argument("after")
    ap.add_argument("out")
    ap.add_argument("--cut", action="store_true")
    ap.add_argument("--scale", type=float, default=0.5)
    a = ap.parse_args()
    with open(os.path.join(a.after, "recipe.json")) as f:
        recipe = json.load(f)
    suffix = "_cut.png" if a.cut else "_ai.png"
    rows = []
    for lay in recipe["layers"]:
        for s in (x["strip"] for x in lay["strips"]):
            for side, d in (("before", a.before), ("after", a.after)):
                p = os.path.join(d, "strips", s + suffix)
                if os.path.exists(p):
                    rows.append((f"{s} {lay['kind']} {side}", twice(p, a.cut, a.scale)))
    w = max(r.width for _, r in rows)
    sheet = Image.new("RGB", (w, sum(r.height + 22 for _, r in rows)), (30, 30, 30))
    g = ImageDraw.Draw(sheet)
    y = 0
    for label, r in rows:
        g.text((6, y + 4), label, fill=(235, 235, 235))
        sheet.paste(r, (0, y + 18))
        y += r.height + 22
    sheet.save(a.out)


if __name__ == "__main__":
    main()
