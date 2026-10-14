"""r364: a strip as SD painted it, then its BEFORE and AFTER cuts (recut.sh's OUTs) over magenta, stacked; --crop X0,Y0,X1,Y1.

    python3 tools/r364-ghosts/pair.py OUT.png BEFORE AFTER PAGE/STRIP... [--crop ...] [--behind]

--behind: over the layers behind instead of magenta, each cut drawn over its page's earlier strips (l0, l1, ...: the
first strip of each), all from the same OUT (no dy offset: the strips' bottoms line up, as ai_page.py paints them).
"""
import argparse
import os

from PIL import Image


def over(cut, under):
    bg = under.copy()
    bg.alpha_composite(cut)
    return bg.convert("RGB")


def behind(root, page, strip):
    """The page's earlier depths' first strips, composited back to front over its sky's top colour."""
    cell = os.path.basename(page.rstrip("/"))
    depth = int(strip[1])
    im = Image.open(os.path.join(root, cell, strip + ".png"))
    sky = Image.open(os.path.join(page, "strips", strip + "_ai.png")).convert("RGB").getpixel((0, 0))
    bg = Image.new("RGBA", im.size, sky + (255,))
    for k in range(depth):
        bg.alpha_composite(Image.open(os.path.join(root, cell, f"l{k}.png")).convert("RGBA"))
    return bg


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("before")
    ap.add_argument("after")
    ap.add_argument("strips", nargs="+")
    ap.add_argument("--crop")
    ap.add_argument("--behind", action="store_true")
    a = ap.parse_args()
    rows = []
    for ps in a.strips:
        page, strip = os.path.split(ps)
        cell = os.path.basename(page)
        rows.append(Image.open(os.path.join(page, "strips", strip + "_ai.png")).convert("RGB"))
        for root in (a.before, a.after):
            cut = Image.open(os.path.join(root, cell, strip + ".png")).convert("RGBA")
            under = behind(root, page, strip) if a.behind else Image.new("RGBA", cut.size, (255, 0, 255, 255))
            rows.append(over(cut, under))
    if a.crop:
        box = tuple(int(v) for v in a.crop.split(","))
        rows = [r.crop(box) for r in rows]
    w = max(r.width for r in rows)
    sheet = Image.new("RGB", (w, sum(r.height + 4 for r in rows)), (40, 40, 40))
    y = 0
    for r in rows:
        sheet.paste(r, (0, y))
        y += r.height + 4
    sheet.save(a.out)


if __name__ == "__main__":
    main()
