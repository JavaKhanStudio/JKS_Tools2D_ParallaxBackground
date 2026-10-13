"""moments.py FILM — how much of the r363 figure shows, frame by frame, and the stills at the moments it shows and hides.

    python3 tools/r363-reveal/moments.py build/r363/film

Reads FILM/frames (tools/ai-film.sh on make.py's round, 24 fps from 12 s on). A figure pixel is one where v01's frame
differs from v00's (the page without the figure) by more than 30 over r+g+b. Prints the count and box every half
second, and writes FILM/v01-moments.png (four stills, the figure boxed and enlarged) and FILM/v02-giant.png.
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

FPS, START = 24, 12.0
MOMENTS = ((15.0, 'half seen in a gap'), (18.5, 'a sliver past a trunk'), (22.0, 'hidden by the pines'),
           (28.0, 'whole, in the next gap'))


def frame(film, sid, t):
    return Image.open(os.path.join(film, 'frames', f'{sid}-f{round((t - START) * FPS):04d}.png')).convert('RGB')


def shown(film, sid, t):
    """The figure's pixels on screen at t: a mask."""
    a = np.asarray(frame(film, sid, t), dtype=int)
    b = np.asarray(frame(film, 'v00', t), dtype=int)
    return np.abs(a - b).sum(2) > 30


def main(film):
    track = []
    t = START
    while os.path.exists(os.path.join(film, 'frames', f'v01-f{round((t - START) * FPS):04d}.png')):
        m = shown(film, 'v01', t)
        if m.any():
            ys, xs = np.nonzero(m)
            print(f't={t:5.1f}  {m.sum():4d} px  x {xs.min():4d}..{xs.max():4d}  y {ys.min():3d}..{ys.max():3d}')
            track.append((t, xs.max()))
        else:
            print(f't={t:5.1f}     0 px')
        t += 0.5
    # The figure's right edge moves at a constant speed: where it is while the pines hide it.
    (t0, x0), (t1, x1) = track[0], track[-1]
    speed = (x0 - x1) / (t1 - t0)

    w, h = 640, 360
    sheet = Image.new('RGB', (2 * w, 2 * (h + 24)), 'black')
    d = ImageDraw.Draw(sheet)
    for k, (t, what) in enumerate(MOMENTS):
        im = frame(film, 'v01', t)
        right = int(x0 - speed * (t - t0))
        box = (right - 80, 200, right + 50, 330)
        crop = im.crop(box).resize((260, 260), Image.NEAREST)
        big = im.resize((w, h))
        dd = ImageDraw.Draw(big)
        dd.rectangle((box[0] / 2, box[1] / 2, box[2] / 2, box[3] / 2), outline=(220, 40, 40), width=2)
        ix = w - 264 if right < 700 else 4
        big.paste(crop, (ix, h - 264))
        dd.rectangle((ix, h - 264, ix + 260, h - 4), outline=(220, 40, 40), width=2)
        x, y = (k % 2) * w, (k // 2) * (h + 24)
        sheet.paste(big, (x, y + 24))
        d.text((x + 6, y + 6), f'v01 t={t:.1f}s  {what}  ({shown(film, "v01", t).sum()} figure px on screen)',
               fill='white')
    sheet.save(os.path.join(film, 'v01-moments.png'))
    giant = Image.new('RGB', (w, h + 24), 'black')
    giant.paste(frame(film, 'v02', 23.5).resize((w, h)), (0, 24))
    ImageDraw.Draw(giant).text((6, 6), 'v02 t=23.5s  the same layer as large as the R1 accident: a giant', fill='white')
    giant.save(os.path.join(film, 'v02-giant.png'))


if __name__ == '__main__':
    main(sys.argv[1])
