"""r216: what FOG does to fog-lab's panel A, before and after, as one image to look at.

Each band is panel A minus the plain panel (no FOG, no haze) of a tools/fog-lab.sh still, contrast-stretched, with a red
tick every wavelength (8.25 world units of 40 = 156.75 px of 760). Before the fix the same patch sits under every tick.
  python3 -I tools/r216-fog-loop/strip.py BEFORE.png AFTER.png OUT.png
"""
import sys
from PIL import Image, ImageChops, ImageDraw, ImageOps

before, after, out = sys.argv[1:]
A, PLAIN, TOP, BOTTOM = (8, 768), (772, 1532), 30, 175
TICK = 8.25 / 40 * 760


def band(path):
    im = Image.open(path).convert("L")
    a = im.crop((A[0], TOP, A[1], BOTTOM))
    p = im.crop((PLAIN[0], TOP, PLAIN[1], BOTTOM))
    d = ImageOps.autocontrast(ImageChops.difference(a, p), cutoff=1).convert("RGB")
    draw = ImageDraw.Draw(d)
    x = 0.0
    while x < d.width:
        draw.line([(round(x), 0), (round(x), 8)], fill=(255, 0, 0), width=2)
        x += TICK
    return d


b, f = band(before), band(after)
W, H = b.width, b.height
img = Image.new("RGB", (W, 2 * H + 60), (32, 32, 32))
draw = ImageDraw.Draw(img)
draw.text((6, 4), "before: FOG at 1, 2, 3 per wavelength (red tick = one wavelength)", fill=(255, 255, 255))
img.paste(b, (0, 22))
draw.text((6, H + 30), "after: 7, 17, 23 per 8 wavelengths", fill=(255, 255, 255))
img.paste(f, (0, H + 48))
img.save(out)
print(out)
