#!/usr/bin/env python3
"""sheet.py GDX_DIR OUT.png — the lurker round's stills for the card (r370): engines/jme/tools/frame-check.sh's
build/jme/lurker/gdx stills of l01 (seen, covered, gone) and l02 (gone, covered, there), each with the figure's place
cropped and enlarged 4x. The figure's place is where l01 and l02 differ at 0 or 12 s: same page and scroll, only the
seed differs."""
import sys

from PIL import Image, ImageChops, ImageDraw

gdx, out = sys.argv[1], sys.argv[2]
SCENES = [('l01', ['seen', 'covered (switched off unseen)', 'gone at the next gap']),
          ('l02', ['gone (the gap empty)', 'covered (switched on unseen)', 'there at the next gap'])]


def still(sid, t):
    return Image.open(f'{gdx}/{sid}-t{t}.png').convert('RGB')


boxes = {}
for t in (0, 12):
    box = ImageChops.difference(still('l01', t), still('l02', t)).getbbox()
    if box is None:
        sys.exit(f'l01 and l02 do not differ at {t} s: the figure is drawn in neither or both')
    boxes[t] = box
# At 6 s the figure stands where it is between 0 and 12 s: the crop follows it there.
boxes[6] = tuple((a + b) // 2 for a, b in zip(boxes[0], boxes[12]))
W, H, PAD, LABEL = 480, 270, 30, 20
sheet = Image.new('RGB', (3 * (W + 160), 2 * (H + LABEL)), 'black')
draw = ImageDraw.Draw(sheet)
for row, (sid, labels) in enumerate(SCENES):
    for col, t in enumerate((0, 6, 12)):
        im = still(sid, t)
        x0, y0, x1, y1 = boxes[t]
        cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
        crop = im.crop((cx - 20, cy - 35, cx + 20, cy + 35)).resize((160, 280), Image.NEAREST).crop((0, 5, 160, 275))
        small = im.resize((W, H))
        d = ImageDraw.Draw(small)
        s = W / im.width
        d.rectangle(((cx - 20) * s, (cy - 35) * s, (cx + 20) * s, (cy + 35) * s), outline='magenta')
        x, y = col * (W + 160), row * (H + LABEL)
        sheet.paste(small, (x, y + LABEL))
        sheet.paste(crop, (x + W, y + LABEL))
        draw.text((x + 4, y + 4), f'{sid} t={t} s: {labels[col]}', fill='white')
sheet.save(out)
print(out)
