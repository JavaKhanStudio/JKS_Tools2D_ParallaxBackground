"""r549: the labs page's tiles before and after a re-cut, side by side, one row a tile, its mean change in the label.
  python3 tools/r549-tiles/sheet.py BEFORE_DIR AFTER_DIR OUT.png
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

before, after, out = Path(sys.argv[1]), Path(sys.argv[2]), sys.argv[3]
names = sorted(p.name for p in after.glob("*.jpg"))
W, H, PAD = 240, 135, 20
COLS = 2
rows = (len(names) + COLS - 1) // COLS
sheet = Image.new("RGB", (COLS * (2 * W + 30), rows * (H + PAD)), "white")
draw = ImageDraw.Draw(sheet)
for i, name in enumerate(names):
    x, y = (i % COLS) * (2 * W + 30), (i // COLS) * (H + PAD)
    a = Image.open(after / name).convert("RGB")
    b = Image.open(before / name).convert("RGB") if (before / name).exists() else Image.new("RGB", a.size, "grey")
    mean = np.abs(np.asarray(a, np.int16) - np.asarray(b, np.int16)).mean()
    sheet.paste(b.resize((W, H)), (x, y + PAD))
    sheet.paste(a.resize((W, H)), (x + W + 4, y + PAD))
    draw.text((x + 2, y + 4), f"{name}: before | after, mean change {mean:.1f}/255", fill="black")
sheet.save(out)
print(out)
