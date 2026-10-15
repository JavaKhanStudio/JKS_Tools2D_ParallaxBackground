"""r384: one candidate's hills cut over the sky, for every seed try.py painted, two a row at half size, to see how
often a recipe paints what it was picked for (inpf: round-tree hills on 7021 and 7023 of 7020-7029).

    python3 tools/r384-hills/seeds_sheet.py build/r384/try/inpf OUT.png
"""
import glob
import os
import re
import sys

from PIL import Image, ImageDraw

src, out = sys.argv[1:3]
cuts = sorted(glob.glob(os.path.join(src, "s*_cut.png")))
first = Image.open(cuts[0])
w, h = first.width // 2, first.height // 2
rows = (len(cuts) + 1) // 2
sheet = Image.new("RGB", (2 * w + 10, rows * (h + 22)), "white")
d = ImageDraw.Draw(sheet)
for i, p in enumerate(cuts):
    c = Image.open(p).convert("RGBA")
    bg = Image.new("RGBA", c.size, (213, 222, 231, 255))
    bg.alpha_composite(c)
    x, y = (i % 2) * (w + 10), (i // 2) * (h + 22)
    d.text((x + 4, y + 4), "seed " + re.search(r"s(\d+)_cut", p)[1], fill="black")
    sheet.paste(bg.convert("RGB").resize((w, h)), (x, y + 18))
sheet.save(out)
print(out)
