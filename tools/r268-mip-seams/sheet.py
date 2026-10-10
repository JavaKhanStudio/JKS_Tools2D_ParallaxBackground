#!/usr/bin/env python3
"""r268: OUT_PNG from make.py rounds' stills, side by side with each row named, then a 3x crop of their joins.

    python3 tools/r268-mip-seams/sheet.py OUT_PNG TITLE=ROUND_DIR...
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import measure  # noqa: E402


def panel(title, d):
    img = Image.open(os.path.join(d, "shots", "m01-t6.png")).convert("RGB")
    rows = measure.rows_of(d)
    found = measure.bands(__import__("numpy").asarray(img, float))
    dips = measure.dips(os.path.join(d, "shots", "m01-t6.png"), rows)
    g = ImageDraw.Draw(img)
    font = ImageFont.load_default(size=20)
    for name, (top, bottom), dip in zip(rows, found, dips):
        g.rectangle((980, bottom + 2, 1280, bottom + 26), fill=(0, 0, 0))
        g.text((988, bottom + 3), f"{name}  dip {dip:.0f}/255", font=font,
               fill=(255, 200, 90) if dip > 9 else (160, 230, 160))
    # The 3x crop of the joins, before the title goes over the top rows.
    crop = img.crop((150, 0, 470, img.height // 3)).resize((960, img.height), Image.NEAREST)
    g.rectangle((0, 0, 1280, 30), fill=(0, 0, 0))
    g.text((8, 4), title, font=font, fill=(255, 255, 255))
    ImageDraw.Draw(crop).rectangle((0, 0, 960, 30), fill=(0, 0, 0))
    ImageDraw.Draw(crop).text((8, 4), "x3, the top rows' joins", font=font, fill=(255, 255, 255))
    return img, crop


def main(out, specs):
    panels, crop = zip(*[panel(*s.rsplit("=", 1)) for s in specs])
    w, h = panels[0].size
    sheet = Image.new("RGB", (w * len(panels), h * 2), (0, 0, 0))
    for i, (p, c) in enumerate(zip(panels, crop)):
        sheet.paste(p, (i * w, 0))
        sheet.paste(c, (i * w, h))
    sheet.save(out)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2:])
