"""r443: try.py's painted dunes, one column a candidate, one row a seed, half size.

    python3 tools/r443-dunes-sea/sheet.py OUT SHEET.png NAME...
"""
import sys

from PIL import Image, ImageDraw

out, dest, names = sys.argv[1], sys.argv[2], sys.argv[3:]
W, H = 512, 192
seeds = range(11020, 11030)
img = Image.new("RGB", (len(names) * (W + 8), 18 + len(seeds) * (H + 4)), "white")
d = ImageDraw.Draw(img)
for i, n in enumerate(names):
    d.text((i * (W + 8) + 4, 3), n, fill="black")
    for j, s in enumerate(seeds):
        im = Image.open(f"{out}/{n}/s{s}_ai.png").convert("RGB").resize((W, H))
        img.paste(im, (i * (W + 8), 18 + j * (H + 4)))
        d.text((i * (W + 8) + 4, 20 + j * (H + 4)), str(s), fill="black")
img.save(dest)
