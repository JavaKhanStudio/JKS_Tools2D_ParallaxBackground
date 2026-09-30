#!/usr/bin/env python3
"""r126 (d14): reads what tools/r126-pixel-art.txt left in OUT and says PASS or FAIL for each claim.

The preview must turn sharp when Pixel art is ticked and come back exactly when unticked; the export must be written
Nearest,Nearest and reopen looking like the sharp preview; the saved project must keep the switch.
Usage: python3 tools/r126-pixel-art-check.py editor/build/r126/out
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageChops

out = Path(sys.argv[1])
# The preview in a 1280x720 window, less its top 8 rows, where the options' check boxes and the cursor sit.
VIEW = (370, 111, 1236, 590)


def frame(name):
    return Image.open(out / name).convert("RGB").crop(VIEW)


def changed(a, b):
    """Pixels differing by more than 8/255 on a channel."""
    diff = ImageChops.difference(frame(a), frame(b)).convert("L").point(lambda v: 255 if v > 8 else 0)
    return diff.histogram()[255]


failed = False


def claim(ok, what):
    global failed
    failed |= not ok
    print(("PASS " if ok else "FAIL ") + what)


claim(changed("1-soft.png", "2-sharp.png") > 10000, "ticking Pixel art changes the preview (%d px)" % changed("1-soft.png", "2-sharp.png"))
claim(changed("1-soft.png", "3-untick.png") == 0, "unticking gives the soft preview back exactly")
filters = [l.split(":", 1)[1].strip() for l in (out / "city.atlas").read_text().splitlines() if l.startswith("filter:")]
claim(filters and all(f == "Nearest,Nearest" for f in filters), "the export is written Nearest,Nearest: %s" % filters)
claim(changed("2-sharp.png", "5-export-reopened.png") == 0, "the reopened export draws as the sharp preview did")
claim(json.loads((out / "city.plaxpj").read_text()).get("pixelArt") is True, "the saved project keeps pixelArt")
claim(changed("2-sharp.png", "6-project-reopened.png") == 0, "the reopened project draws sharp")
sys.exit(1 if failed else 0)
