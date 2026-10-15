"""r398: a SEQUENCE join drawn big beside a stretch of the same strip's inside, over the sky, to see if it is a cut.

    python3 tools/r398-seq-seams/zoom.py LEFT.png RIGHT.png OUT.png [ROW0 ROW1]

Top: LEFT's last 48 columns then RIGHT's first 48 (the join at the red tick). Bottom: 96 columns from LEFT's middle.
Both x6, rows ROW0..ROW1 (default the strip's lower third and a bit)."""
import sys

from PIL import Image, ImageDraw

left, right = (Image.open(p).convert('RGBA') for p in sys.argv[1:3])
w, h = left.size
r0, r1 = (int(v) for v in sys.argv[4:6]) if len(sys.argv) > 5 else (int(h * 0.6), h)
sky = (212, 220, 227, 255)


def strip(parts):
    out = Image.new('RGBA', (sum(p.width for p in parts), r1 - r0), sky)
    x = 0
    for p in parts:
        out.alpha_composite(p.crop((0, r0, p.width, r1)), (x, 0))
        x += p.width
    return out


join = strip([left.crop((w - 48, 0, w, h)), right.crop((0, 0, 48, h))])
inner = strip([left.crop((w // 2 - 48, 0, w // 2 + 48, h))])
k = 6
sheet = Image.new('RGBA', (96 * k, 2 * (r1 - r0) * k + 8), (255, 255, 255, 255))
sheet.paste(join.resize((96 * k, (r1 - r0) * k), Image.NEAREST), (0, 0))
sheet.paste(inner.resize((96 * k, (r1 - r0) * k), Image.NEAREST), (0, (r1 - r0) * k + 8))
ImageDraw.Draw(sheet).line([(48 * k, 0), (48 * k, 12)], fill=(255, 0, 0, 255), width=3)
sheet.convert('RGB').save(sys.argv[3])
