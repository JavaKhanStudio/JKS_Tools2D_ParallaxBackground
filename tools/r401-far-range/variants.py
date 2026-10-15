#!/usr/bin/env python3
"""r401: a page copied once per answer to Simon's question, for filming: how a pale far range would look kept whole (A),
half clear whole (B) or keyed out whole (C), beside today's key and the page's own cut.

    python3 tools/r401-far-range/variants.py PAGE STRIP CUTS OUT [--t 0.6]

PAGE is an ai-page.sh page folder (recipe.json, strips/, page/); CUTS today's layer.py cuts of its strips
(tools/r364-ghosts/recut.sh's OUT/<page>); STRIP the one holding the far range (l0, l3...). Writes OUT/<v>/page, a
copy of PAGE's page whose atlas holds: own = the page as it was cut; today = every strip from CUTS; A, B, C = today with
STRIP's far range (region.py's, at --t) at alpha 1, 0.5 or 0. OUT/region.png shows the range. A mockup: no key rule.
"""
import argparse
import glob
import json
import os
import re
import shutil
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import region as reg

RX = r"^(\S+)\n  rotate: \w+\n  xy: (\d+), (\d+)\n  size: (\d+), (\d+)"


def page_copy(page, out, strips):
    """page's page/ copied to out/page, its atlas regions replaced by strips (atlas name -> RGBA image)."""
    shutil.rmtree(out, ignore_errors=True)
    shutil.copytree(os.path.join(page, "page"), os.path.join(out, "page"))
    atlas = glob.glob(os.path.join(out, "page", "*.atlas"))[0]
    sheet = Image.open(atlas[:-6] + ".png").convert("RGBA")
    for name, x, y, w, h in re.findall(RX, Path(atlas).read_text(), re.MULTILINE):
        if name in strips:
            assert strips[name].size == (int(w), int(h)), (name, strips[name].size)
            sheet.paste(strips[name], (int(x), int(y)))
    sheet.save(atlas[:-6] + ".png")
    with open(os.path.join(out, "page", "round.json")) as f:
        r = json.load(f)
    for s in r["scenes"]:
        s["page"] = os.path.abspath(os.path.join(out, "page", os.path.basename(s["page"])))
        s["atlasDir"] = os.path.abspath(os.path.join(out, "page"))
    with open(os.path.join(out, "page", "round.json"), "w") as f:
        json.dump(r, f, indent=1)


def strip_of(name):
    """An atlas region's strip: l3_2 is strip l3v2, l3_0 strip l3."""
    m = re.match(r"(l\d)(?:_(\d))?$", name)
    return m[1] + ("v" + m[2] if m[2] and m[2] != "0" else "")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("page")
    ap.add_argument("strip")
    ap.add_argument("cuts")
    ap.add_argument("out")
    ap.add_argument("--t", type=float, default=0.6)
    a = ap.parse_args()
    page = a.page.rstrip("/")
    atlas = glob.glob(os.path.join(page, "page", "*.atlas"))[0]
    names = [n for n, *_ in re.findall(RX, Path(atlas).read_text(), re.MULTILINE)]
    today = {n: Image.open(os.path.join(a.cuts, strip_of(n) + ".png")).convert("RGBA") for n in names}
    page_copy(page, os.path.join(a.out, "own"), {})
    page_copy(page, os.path.join(a.out, "today"), today)
    cut = os.path.join(a.cuts, a.strip + ".png")
    img, r = reg.region(page, a.strip, cut, a.t)
    tint = img.copy()
    tint[r] = tint[r] * 0.4 + np.array([255, 0, 255]) * 0.6
    Image.fromarray(tint.astype(np.uint8)).save(os.path.join(a.out, "region.png"))
    base = np.asarray(Image.open(cut).convert("RGBA")).copy()
    name = next(n for n in names if strip_of(n) == a.strip)
    for v, alpha in (("A", 255), ("B", 128), ("C", 0)):
        im = base.copy()
        im[r, 3] = alpha
        if alpha:  # the painting's own colour where today's key had cleared it
            im[r, :3] = img[r].astype(np.uint8)
        page_copy(page, os.path.join(a.out, v), dict(today, **{name: Image.fromarray(im)}))
    print(f"{a.out}: own today A B C, far range {int(r.sum())} px")


if __name__ == "__main__":
    main()
