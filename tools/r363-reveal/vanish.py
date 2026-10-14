"""vanish.py FILM — the r363 lurker seen once: it goes behind the pines and is not there in the next gap (r363).

    python3 tools/r363-reveal/vanish.py build/r363/film

What Simon asked after the first demo: the figure shows in a gap, a trunk covers it, and when the next gap comes, where
the player expects it, it is gone. Made from film.sh's frames, no reader change: v01's frames (the figure) until the
pines cover it whole, v00's (the page without it) after. The switch is allowed only on a frame where the two are the
same (the figure fully hidden), which this script asserts: that is the rule a reader would have to keep, a figure
vanishes only while it is covered, never in sight. Writes FILM/v03.mp4 and FILM/v03-vanish.png (stills when it shows,
when it is covered and the switch happens, and in the next gap; boxed and enlarged where it stands).
"""
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw

FPS, START = 24, 12.0
SWITCH = 22.0  # seconds: the pines cover the figure whole from 21 to 23 s (moments.py)


def path(film, sid, t):
    return os.path.join(film, 'frames', f'{sid}-f{round((t - START) * FPS):04d}.png')


def frame(film, sid, t):
    return Image.open(path(film, sid, t)).convert('RGB')


def figure_px(film, t):
    a = np.asarray(frame(film, 'v01', t), dtype=int)
    b = np.asarray(frame(film, 'v00', t), dtype=int)
    return int((np.abs(a - b).sum(2) > 30).sum())


def main(film):
    hidden = figure_px(film, SWITCH)
    assert hidden == 0, f'the figure shows {hidden} px at the switch: it would vanish in sight'
    out = os.path.join(film, 'v03-frames')
    os.makedirs(out, exist_ok=True)
    n = 0
    while os.path.exists(path(film, 'v00', START + n / FPS)):
        t = START + n / FPS
        src = path(film, 'v01' if t < SWITCH else 'v00', t)
        dst = os.path.join(out, f'f{n:04d}.png')
        if os.path.lexists(dst):
            os.remove(dst)
        os.symlink(os.path.abspath(src), dst)
        n += 1
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-framerate', str(FPS), '-i', os.path.join(out, 'f%04d.png'),
                    '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '18', os.path.join(film, 'v03.mp4')], check=True)

    def box(t):
        a = np.asarray(frame(film, 'v01', t), dtype=int)
        b = np.asarray(frame(film, 'v00', t), dtype=int)
        ys, xs = np.nonzero(np.abs(a - b).sum(2) > 30)
        return xs.max(), ys.max()

    # Where the figure stands, its right edge moving at a constant speed: read at 15 and 28 s, between them by line.
    (r0, y0), (r1, _) = box(15.0), box(28.0)

    def right_at(t):
        return r0 + (r1 - r0) * (t - 15.0) / 13.0

    w, h = 640, 360
    shots = ((15.0, 'v01', 'seen in a gap'), (SWITCH, 'v00', 'covered by the pines: it leaves here, unseen'),
             (28.0, 'v00', 'the next gap, where it was expected: nothing'))
    sheet = Image.new('RGB', (3 * w, h + 24), 'black')
    d = ImageDraw.Draw(sheet)
    for k, (t, sid, what) in enumerate(shots):
        im = frame(film, sid, t)
        right = int(right_at(t))
        crop = (right - 80, y0 - 110, right + 50, y0 + 20)
        big = im.resize((w, h))
        dd = ImageDraw.Draw(big)
        dd.rectangle(tuple(c / 2 for c in crop), outline=(220, 40, 40), width=2)
        ix = w - 204 if right < 700 else 4
        big.paste(im.crop(crop).resize((200, 200), Image.NEAREST), (ix, h - 204))
        dd.rectangle((ix, h - 204, ix + 200, h - 4), outline=(220, 40, 40), width=2)
        sheet.paste(big, (k * w, 24))
        d.text((k * w + 6, 6), f't={t:.1f}s  {what}', fill='white')
    sheet.save(os.path.join(film, 'v03-vanish.png'))
    print(f'switch at {SWITCH}s: {hidden} figure px on screen; {n} frames -> v03.mp4')


if __name__ == '__main__':
    main(sys.argv[1])
