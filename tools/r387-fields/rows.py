#!/usr/bin/env python3
"""r387's before/after rows: each example page's 6 and 12 s stills (tools/r279-depth-page/examples.sh), before and
after side by side, and how many pixels moved between the two.

    python3 tools/r387-fields/rows.py BEFORE AFTER OUT.png [--crop X0 Y0 X1 Y1]
"""
import argparse
import os

import numpy as np
from PIL import Image, ImageDraw


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("before")
    ap.add_argument("after")
    ap.add_argument("out")
    ap.add_argument("--crop", type=int, nargs=4)
    ap.add_argument("--pages", nargs="*", default=["valley", "river", "noloop"])
    a = ap.parse_args()
    rows = []
    for page in a.pages:
        for t in (6, 12):
            pair = [Image.open(os.path.join(d, page, "shots", f"d01-t{t}.png")).convert("RGB") for d in (a.before, a.after)]
            if a.crop:
                pair = [p.crop(a.crop) for p in pair]
            diff = np.abs(np.asarray(pair[0], int) - np.asarray(pair[1], int)).max(axis=2)
            w = 640
            pair = [p.resize((w, p.height * w // p.width)) for p in pair]
            row = Image.new("RGB", (2 * w + 10, pair[0].height + 24), (255, 255, 255))
            row.paste(pair[0], (0, 24))
            row.paste(pair[1], (w + 10, 24))
            ImageDraw.Draw(row).text((4, 6), f"{page} t{t}: before | after   ({(diff > 24).mean():.1%} of the pixels "
                                             f"moved by more than 24/255)", fill=(0, 0, 0))
            rows.append(row)
    out = Image.new("RGB", (rows[0].width, sum(r.height for r in rows)), (255, 255, 255))
    y = 0
    for r in rows:
        out.paste(r, (0, y))
        y += r.height
    out.save(a.out)


if __name__ == "__main__":
    main()
