#!/usr/bin/env python3
"""r381: an ai page laid out again by today's ai_page.Pipeline.bottoms, over the strips it was cut from, no re-cut.

    python3 tools/r381-notch-gap/relayout.py PAGE_DIR OUT_DIR

Copies PAGE_DIR (NAME.jplax, its atlas, round.json) to OUT_DIR and sets each layer's decal_Y_Ratio as write_page does,
from the atlas's own regions (l<k> or l<k>_<i>, a SEQUENCE's segments): an old page's cut, today's layout, so the
layout alone is what changes. Prints each layer's dy before and after. Then the parallax-ai repository's
tests/gap_scan.py (a round holding the two pages) counts the sky seen in the screen's bottom third.
"""
import glob
import json
import os
import re
import shutil
import sys
from pathlib import Path

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "r239-ai-strips"))
sys.path.insert(0, os.path.join(HERE, ".."))
import ai_page  # noqa: E402
import parallax_lab as lab  # noqa: E402

src, out = sys.argv[1:3]
shutil.rmtree(out, ignore_errors=True)
shutil.copytree(src, out)
jplax = glob.glob(os.path.join(out, "*.jplax"))[0]
atlas = glob.glob(os.path.join(out, "*.atlas"))[0]
sheet = Image.open(atlas[:-6] + ".png")
regions = {}
for name, x, y, w, h in re.findall(r"^(\S+)\n  rotate: \w+\n  xy: (\d+), (\d+)\n  size: (\d+), (\d+)",
                                   Path(atlas).read_text(), re.MULTILINE):
    m = re.match(r"l(\d+)(?:_\d+)?$", name)
    if m:
        x, y, w, h = map(int, (x, y, w, h))
        path = os.path.join(out, f"region_{name}.png")
        sheet.crop((x, y, x + w, y + h)).save(path)
        regions.setdefault(int(m[1]), []).append(path)
layers = [regions[k] for k in sorted(regions)]
pipe = ai_page.Pipeline.__new__(ai_page.Pipeline)
pipe.ks = [None] * len(layers)
bottoms = pipe.bottoms(layers)
page = json.loads(Path(jplax).read_text())
models = [m for m in lab.layers(page) if m.get("kind") != "LURKER"]
for k, m in enumerate(models):
    dy = round(100 * bottoms[k] / lab.SCREEN_H, 2)
    print(f"l{k}: dy {m['decal_Y_Ratio']} -> {dy}")
    m["decal_Y_Ratio"] = dy
Path(jplax).write_text(json.dumps(page, indent=1))
for p in glob.glob(os.path.join(out, "region_*.png")):
    os.remove(p)
