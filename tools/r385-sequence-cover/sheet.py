"""sheet.py FILM TRACK OUT — r366's film on frames the baked cover calls covered, beside the leak amplified (r385).

    python3 tools/r385-sequence-cover/sheet.py build/r366/film build/r385/track.txt build/r385/sheet.png

A row for each run of covered frames in TRACK (check.sh's): the frame just before it (the figure seen) and the run's
first, middle and last frames, cropped round the figure's place and enlarged, a01 above, a01 - a00 over r+g+b times 20
below (black: no figure pixel). The place, carried on from the frames before the run at its own speed, is boxed in red.
"""
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

CROP, CELL = 120, 320


def load(film, sid, f):
    return Image.open(os.path.join(film, 'frames', f'{sid}-f{f:04d}.png')).convert('RGB')


def diff(film, f):
    a, b = (np.asarray(load(film, s, f), dtype=int) for s in ('a01', 'a00'))
    return np.abs(a - b).sum(2)


def box(film, f):
    ys, xs = np.nonzero(diff(film, f) > 30)
    return None if len(xs) == 0 else (xs.min(), ys.min(), xs.max(), ys.max())


def main(film, track, out):
    states = {}
    with open(track) as lines:
        for line in lines:
            p = line.split()
            if len(p) >= 3 and p[0].isdigit():
                states[int(p[0])] = p[2]
    covered = sorted(f for f, s in states.items() if s == 'covered')
    runs, run = [], []
    for f in covered:
        if run and f != run[-1] + 1:
            runs.append(run)
            run = []
        run.append(f)
    runs.append(run)
    rows = []
    for run in [r for r in runs if len(r) > 3]:
        # The figure's right edge moves at one speed: measured over the seen frames before the run.
        seen = [f for f in range(run[0] - 12, run[0]) if states.get(f) == 'seen' and box(film, f)]
        b0, b1 = box(film, seen[0]), box(film, seen[-1])
        speed = (b1[2] - b0[2]) / (seen[-1] - seen[0])
        width, height = b1[2] - b1[0], b1[3] - b1[1]
        row = Image.new('RGB', (4 * CELL, 2 * CELL + 16), 'white')
        for i, f in enumerate((seen[-1], run[0], run[len(run) // 2], run[-1])):
            right = b1[2] + speed * (f - seen[-1])
            place = (right - max(width, height * 0.35), b1[1], right, b1[3])
            middle = ((place[0] + place[2]) / 2, (place[1] + place[3]) / 2)
            crop = (int(middle[0] - CROP / 2), int(middle[1] - CROP / 2), int(middle[0] + CROP / 2), int(middle[1] + CROP / 2))
            moved = tuple(v - crop[k % 2] for k, v in enumerate(place))
            frame = load(film, 'a01', f).crop(crop)
            leak = Image.fromarray(np.clip(diff(film, f) * 20, 0, 255).astype(np.uint8)).convert('RGB').crop(crop)
            for k, im in enumerate((frame, leak)):
                ImageDraw.Draw(im).rectangle(moved, outline=(255, 0, 0), width=1)
                row.paste(im.resize((CELL, CELL), Image.NEAREST), (i * CELL, 16 + k * CELL))
            ImageDraw.Draw(row).text((i * CELL + 4, 2), f'f{f} {states[f]}: {int(diff(film, f).astype(bool).sum())} px differ',
                                     fill=(0, 0, 0))
        rows.append(row)
    sheet = Image.new('RGB', (4 * CELL, (2 * CELL + 16) * len(rows)), 'white')
    for i, r in enumerate(rows):
        sheet.paste(r, (0, (2 * CELL + 16) * i))
    sheet.save(out)
    print(out, len(rows), 'runs')


if __name__ == '__main__':
    main(*sys.argv[1:4])
