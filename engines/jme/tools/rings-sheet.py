#!/usr/bin/env python3
"""rings-sheet.py THREEJS_DIR JME_DIR SHEET.png [STILL...] — the jME rings lab beside three.js's (parallax-readers r50).

Both labs' --shot stills (engines/threejs/tools/rings-lab.sh --shot, engines/jme/tools/rings-lab.sh --shot) open at the
same knobs and fly the same path, so their panels (right of the 300 px controls) show the same rings: one row per still,
three.js | jME | difference (x4), and each still's mean difference per channel (0-255) over the panels and over their
sky (the top 40 %, where the bands are, below the panel tags), printed. Not a
gate: two engines' perspective rasterisation and lighting do not agree at 2/255 (docs/page-as-3d-sky.md); the ground is
lit by each engine's own lights. The bands are what to compare. STILL defaults to every PNG both folders hold.
"""
import os
import sys

from PIL import Image, ImageChops, ImageDraw

CONTROLS = 300


def mean_of(diff):
    """The mean difference per channel, 0-255."""
    h = diff.histogram()
    return sum(sum(i * c for i, c in enumerate(h[k * 256:(k + 1) * 256])) for k in range(3)) / (3 * diff.width * diff.height)


def main(a, b, sheet, names):
    names = names or sorted(n for n in os.listdir(a) if n.endswith('.png') and os.path.exists(os.path.join(b, n)))
    rows = []
    for n in names:
        x, y = (Image.open(os.path.join(d, n)).convert('RGB') for d in (a, b))
        h = min(x.height, y.height)
        x, y = (i.crop((CONTROLS, 0, i.width, h)) for i in (x, y))
        diff = ImageChops.difference(x, y)
        mean, sky = mean_of(diff), mean_of(diff.crop((0, 40, diff.width, diff.height * 4 // 10)))
        print(f'{n}: mean {mean:.2f} / 255, sky {sky:.2f} (the top 40 % but the tags)')
        rows.append((n, mean, x, y, diff.point(lambda v: min(255, v * 4))))
    w = rows[0][2].width // 2
    out = Image.new('RGB', (3 * w, sum(r[2].height // 2 + 20 for r in rows)), 'black')
    draw, top = ImageDraw.Draw(out), 0
    for n, mean, x, y, d in rows:
        draw.text((4, top + 3), f'{n}   three.js | jME | diff x4   mean {mean:.2f}/255', fill='white')
        for k, im in enumerate((x, y, d)):
            out.paste(im.resize((w, im.height // 2)), (k * w, top + 20))
        top += x.height // 2 + 20
    out.save(sheet)
    print(f'sheet: {sheet}')


if __name__ == '__main__':
    if len(sys.argv) < 4:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4:])
