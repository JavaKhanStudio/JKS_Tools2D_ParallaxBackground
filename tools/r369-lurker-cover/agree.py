"""agree.py TRACK FILM — the baked lurkerCover against the r363 film, frame by frame (r369).

    python3 tools/r369-lurker-cover/agree.py build/r369/v01-track.txt build/r363/film

TRACK is tools/lurker-cover.sh's LURKER_TRACK output on make.py's v01 (one line a film frame: covered, seen or out);
FILM is tools/r363-reveal/film.sh's, whose frames count the figure's pixels as moments.py does (v01 against v00). Fails
when a frame the cover calls covered shows a figure pixel: the one rule's guarantee. Prints, half a second apart, the
pixels and the verdict, and how many of the frames showing none the cover finds.
"""
import os
import sys

import numpy as np
from PIL import Image

FPS, START = 24, 12.0


def shown(film, f):
    a = np.asarray(Image.open(os.path.join(film, 'frames', f'v01-f{f:04d}.png')).convert('RGB'), dtype=int)
    b = np.asarray(Image.open(os.path.join(film, 'frames', f'v00-f{f:04d}.png')).convert('RGB'), dtype=int)
    return int((np.abs(a - b).sum(2) > 30).sum())


def main(track, film):
    states = {}
    with open(track) as f:
        for line in f:
            parts = line.split()
            if len(parts) >= 3 and parts[0].isdigit():
                states[int(parts[0])] = parts[2]
    bad, hidden, found = [], 0, 0
    for f in sorted(states):
        if not os.path.exists(os.path.join(film, 'frames', f'v01-f{f:04d}.png')):
            break
        px, state = shown(film, f), states[f]
        if state == 'covered' and px:
            bad.append((f, px))
        if state != 'out' and not px:
            hidden += 1
            found += state == 'covered'
        if f % (FPS // 2) == 0 or state == 'covered':
            print(f'f{f:04d} t={START + f / FPS:6.2f}  {px:4d} px  {state}')
    print(f'in view and hidden in {hidden} frames, {found} of them inside a cover interval')
    if bad:
        print('FAIL: covered frames showing the figure: ' + ', '.join(f'f{f} {px} px' for f, px in bad))
        sys.exit(1)
    print('OK: no frame inside a cover interval shows a figure pixel')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
