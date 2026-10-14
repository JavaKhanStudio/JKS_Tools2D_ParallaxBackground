"""moments.py FILM — where an ai-page.sh lurker shows, and where the layer in front hides it, frame by frame (r366).

    python3 tools/r366-lurker/moments.py build/r366/film

Reads FILM/frames: film.sh's round filmed by tools/ai-film.sh (24 fps from 12 s on), a00 the page without its LURKER
layer, a01 with it. A figure pixel is one where a01's frame differs from a00's by more than 30 over r+g+b. Its place
on screen moves at one speed (it stands on its layer, at that layer's speed): fitted on the frames that show it whole,
it says where the figure is on a frame that shows none of it. Prints the count every half second and writes
FILM/a01-moments.png: whole in a gap, partly behind the front, wholly behind it while in the view (its place boxed),
each with its place enlarged; and FILM/a01-feet.png, its place at the start and the end of one crossing, enlarged
side by side with the ground under it: feet that stay on one spot of the ground do not skate.
Exits 1 when no frame shows it whole, or none hides it while in the view.
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

FPS, START = 24, 12.0


def frame(film, sid, n):
    return Image.open(os.path.join(film, 'frames', f'{sid}-f{n:04d}.png')).convert('RGB')


def shown(film, n):
    a = np.asarray(frame(film, 'a01', n), dtype=int)
    b = np.asarray(frame(film, 'a00', n), dtype=int)
    return np.abs(a - b).sum(2) > 30


def main(film):
    frames = sorted(int(f[5:9]) for f in os.listdir(os.path.join(film, 'frames')) if f.startswith('a01-f'))
    counts, boxes = {}, {}
    for n in frames:
        m = shown(film, n)
        counts[n] = int(m.sum())
        if m.any():
            ys, xs = np.nonzero(m)
            boxes[n] = (xs.min(), ys.min(), xs.max(), ys.max())
    for n in frames[::FPS // 2]:
        print(f't={START + n / FPS:5.1f}  {counts[n]:4d} px' + (f'  box {tuple(int(c) for c in boxes[n])}' if n in boxes else ''))
    whole = max(counts.values())
    full = [n for n in frames if counts[n] >= 0.95 * whole]
    if not whole:
        sys.exit('no frame shows the figure')
    # Its middle's x on the frames that show it whole: one speed, fitted where it moves on (not across a wrap).
    mid = {n: (boxes[n][0] + boxes[n][2]) / 2 for n in full}
    # The longest run of whole frames on one crossing: its middle never jumps back (the next copy comes in).
    runs, run = [], [full[0]]
    for n in full[1:]:
        if mid[n] <= mid[run[-1]] + 2:
            run.append(n)
        else:
            runs.append(run)
            run = [n]
    run = max([*runs, run], key=len)
    ref = run[0]
    v = np.polyfit(run, [mid[n] for n in run], 1)[0] if len(run) > 1 else 0.0
    w, _ = Image.open(os.path.join(film, 'frames', f'a01-f{ref:04d}.png')).size
    fig_w, fig_h = boxes[ref][2] - boxes[ref][0], boxes[ref][3] - boxes[ref][1]

    def place(n):
        x = (mid[ref] + v * (n - ref)) % w  # one copy a screen: --lurker-every 1, a 16:9 world 40 wide
        return x, (boxes[ref][1] + boxes[ref][3]) / 2

    in_view = [n for n in frames if fig_w < place(n)[0] < w - fig_w]
    hidden = [n for n in in_view if counts[n] == 0]
    part = [n for n in in_view if 0.1 * whole < counts[n] < 0.6 * whole]
    print(f'whole: {len(full)} frames ({whole} px), partly hidden: {len(part)}, wholly hidden in the view: '
          f'{len(hidden)}; it moves {v * FPS:.1f} px/s')
    if not hidden:
        sys.exit('no frame hides it while it is in the view')
    picks = [(full[len(full) // 2], 'whole, in a gap of the front pines'),
             (part[len(part) // 2] if part else full[0], 'partly behind the front'),
             (hidden[len(hidden) // 2], 'wholly behind the front, in the view')]
    sw, sh = 640, 360
    sheet = Image.new('RGB', (sw * len(picks), sh + 24), 'black')
    d = ImageDraw.Draw(sheet)
    zoom = 3 * fig_h
    for k, (n, what) in enumerate(picks):
        x, y = place(n)
        im = frame(film, 'a01', n)
        box = (int(x - zoom / 2), int(y - zoom / 2), int(x + zoom / 2), int(y + zoom / 2))
        crop = im.crop(box).resize((200, 200), Image.LANCZOS)
        big = im.resize((sw, sh))
        dd = ImageDraw.Draw(big)
        dd.rectangle([c * sw / w for c in box], outline=(220, 40, 40), width=2)
        ix = 4 if x > w / 2 else sw - 204
        big.paste(crop, (ix, sh - 204))
        dd.rectangle((ix, sh - 204, ix + 200, sh - 4), outline=(220, 40, 40), width=2)
        sheet.paste(big, (k * sw, 24))
        d.text((k * sw + 6, 6), f't={START + n / FPS:.1f}s  {what}  ({counts[n]} figure px)', fill='white')
    sheet.save(os.path.join(film, 'a01-moments.png'))
    # Feet: the first and last whole frame of one crossing, its foot's surroundings enlarged.
    inside = [n for n in run if 2 * fig_h < place(n)[0] < w - 2 * fig_h] or run
    first, last = inside[0], inside[-1]
    feet = Image.new('RGB', (2 * 300, 300 + 24), 'black')
    for k, n in enumerate((first, last)):
        x, _ = place(n)
        foot = boxes[n][3]
        box = (int(x - 2 * fig_h), int(foot - 2 * fig_h), int(x + 2 * fig_h), int(foot + fig_h))
        feet.paste(frame(film, 'a01', n).crop(box).resize((300, 225), Image.LANCZOS), (k * 300, 24))
        ImageDraw.Draw(feet).text((k * 300 + 6, 6), f't={START + n / FPS:.1f}s  x={x:.0f}', fill='white')
    feet.save(os.path.join(film, 'a01-feet.png'))


if __name__ == '__main__':
    main(sys.argv[1])
