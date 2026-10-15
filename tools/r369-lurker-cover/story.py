"""story.py OUT — r363's v01 from 14 to 30 s as the player sees it, with where the cover lets the figure change (r369).

    python3 tools/r369-lurker-cover/story.py build/r369/story.png

Needs build/r363/film (tools/r363-reveal/film.sh), build/r369/xray/film (xray.py's docstring) and
build/r369/v01-track.txt (check.sh). Simon read xray.py's sheet as broken: its crops were all from the hidden stretch,
so the figure never showed, and its x-ray row drew the figure over the layers. Here the frames are wide and as
filmed, the figure is never painted: a dotted line only marks where it stands. Above them a timeline: the figure's
pixels on screen (the film, moments.py's > 30 rule) and, in green, the frames LurkerCover calls covered.
"""
import os
import pathlib
import sys

import numpy as np
from PIL import Image, ImageDraw

FPS, START = 24, 12.0
V01, XRAY, TRACK = 'build/r363/film', 'build/r369/xray/film', 'build/r369/v01-track.txt'
T0, T1 = 14.0, 30.0
STILLS = (15.0, 18.5, 20.5, 21.25, 21.5, 22.5, 23.5, 25.0, 28.0)
GREEN, AMBER, MARK = (90, 210, 110), (240, 190, 70), (255, 60, 200)


def frame(film, sid, f):
    return np.asarray(Image.open(os.path.join(film, 'frames', f'{sid}-f{f:04d}.png')).convert('RGB'), dtype=int)


def shown(f):
    return np.abs(frame(V01, 'v01', f) - frame(V01, 'v00', f)).sum(2) > 30


def stands(f):
    """Where the figure stands: the x-ray (drawn over all) against the bare page; past the x-ray film, the film."""
    if os.path.exists(os.path.join(XRAY, 'frames', f'x-top-f{f:04d}.png')):
        return np.abs(frame(XRAY, 'x-top', f) - frame(V01, 'v00', f)).sum(2) > 30
    return shown(f)


def covered():
    out = set()
    for line in pathlib.Path(TRACK).read_text().splitlines():
        p = line.split()
        if len(p) >= 3 and p[0].isdigit() and p[2] == 'covered':
            out.add(int(p[0]))
    return out


def main(out):
    cov = covered()
    frames = range(round((T0 - START) * FPS), round((T1 - START) * FPS))
    counts = {f: int(shown(f).sum()) for f in frames}
    cw, chh, cols = 430, 242, 3
    rows = (len(STILLS) + cols - 1) // cols
    tl_h = 150
    im = Image.new('RGB', (cols * cw + 20, 40 + tl_h + rows * (chh + 34) + 10), (16, 16, 16))
    d = ImageDraw.Draw(im)
    d.text((10, 8), 'r363 v01, 14-30 s, as the player sees it. The figure is never painted over the page here: '
           'a dotted pink line only marks where it stands.', fill='white')
    d.text((10, 22), 'Timeline: grey bars = the figure\'s pixels on screen; green = frames LurkerCover calls covered '
           '(a swap there is never seen).', fill='white')
    # Timeline.
    x0, x1, y0, y1 = 40, im.width - 20, 50, 40 + tl_h - 30
    peak = max(counts.values()) or 1
    w = (x1 - x0) / len(frames)
    for i, f in enumerate(frames):
        x = x0 + i * w
        if f in cov:
            d.rectangle((x, y0, x + w, y1), fill=(30, 90, 40))
        h = (y1 - y0) * counts[f] / peak
        if counts[f]:
            d.rectangle((x, y1 - max(h, 1), x + w, y1), fill=(170, 170, 170))
    for t in range(int(T0), int(T1) + 1):
        x = x0 + (t - T0) * FPS * w
        d.line((x, y1, x, y1 + 4), fill='white')
        d.text((x - 6, y1 + 6), f'{t}s', fill='white')
    for k, t in enumerate(STILLS):
        x = x0 + (t - T0) * FPS * w
        d.line((x, y0 + 12, x, y1), fill=MARK, width=2)
        d.text((x - 3, y0), str(k + 1), fill=MARK)
    d.text((x0, y0 - 2), f'{peak} px', fill=(170, 170, 170))
    # Stills: a 320x180 window round the figure, as filmed.
    for k, t in enumerate(STILLS):
        f = round((t - START) * FPS)
        m = stands(f)
        ys, xs = np.nonzero(m)
        cx, cy = int(xs.mean()), int(ys.mean())
        bx = min(max(cx - 160, 0), 1280 - 320)
        by = min(max(cy - 110, 0), 720 - 180)
        tile = Image.fromarray(frame(V01, 'v01', f)[by:by + 180, bx:bx + 320].astype(np.uint8))
        tile = tile.resize((cw - 10, chh), Image.NEAREST)
        # The outline, dashed, drawn at the tile's scale so it stays thin and never hides the page.
        edge = m & ~(np.roll(m, 1, 0) & np.roll(m, -1, 0) & np.roll(m, 1, 1) & np.roll(m, -1, 1))
        ey, ex = np.nonzero(edge[by:by + 180, bx:bx + 320])
        sx, sy = (cw - 10) / 320, chh / 180
        td = ImageDraw.Draw(tile)
        for py, px in zip(ey, ex):
            if (py + px) // 2 % 2 == 0:
                td.rectangle((px * sx, py * sy, px * sx + 1, py * sy + 1), fill=MARK)
        r, c = divmod(k, cols)
        x, y = 10 + c * cw, 40 + tl_h + r * (chh + 34)
        im.paste(tile, (x, y + 24))
        n = counts.get(f, int(shown(f).sum()))
        if f in cov:
            label, colour = f'{k + 1}. t={t:.2f}s  hidden, and LurkerCover says covered: a swap here is safe', GREEN
        elif n == 0:
            label, colour = f'{k + 1}. t={t:.2f}s  hidden to the eye, but not covered (soft edge)', AMBER
        else:
            label, colour = f'{k + 1}. t={t:.2f}s  visible: {n} px on screen', 'white'
        d.text((x, y + 6), label, fill=colour)
    im.save(out)
    print(out)


if __name__ == '__main__':
    main(*sys.argv[1:])
