"""r360: the same crop of each image, side by side at 2x.

    python3 tools/r360-band-edges/zoom.py OUT.png X0 Y0 X1 Y1 IMG...
"""
import sys

from PIL import Image

out, box, ims = sys.argv[1], tuple(int(v) for v in sys.argv[2:6]), sys.argv[6:]
cs = [Image.open(p).convert('RGB').crop(box) for p in ims]
cs = [c.resize((c.width * 2, c.height * 2), Image.NEAREST) for c in cs]
o = Image.new('RGB', (sum(c.width for c in cs) + 8 * len(cs), cs[0].height), (255, 255, 255))
x = 0
for c in cs:
    o.paste(c, (x, 0))
    x += c.width + 8
o.save(out)
