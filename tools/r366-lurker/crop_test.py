"""crop_test.py — lurker.py crop keeps one whole standing figure and refuses the rest (r366).

    python3 tools/r366-lurker/crop_test.py

Synthetic cuts, 512x768 as ai-page.sh paints them: one figure passes and is cropped to its box; two figures, one cut
off by the image's edge, one whose head reaches the top row, and a ground slab each exit 5.
"""
import os
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image

LURKER = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'r239-ai-strips', 'lurker.py')


def cut(boxes, w=512, h=768):
    a = np.zeros((h, w, 4), np.uint8)
    for x0, y0, x1, y1 in boxes:
        a[y0:y1, x0:x1] = (40, 50, 60, 255)
    return Image.fromarray(a, 'RGBA')


CASES = [
    ('one figure', [(200, 120, 300, 740)], 0),
    ('two figures', [(80, 120, 180, 740), (300, 120, 400, 740)], 5),
    ('cut off at the left', [(0, 120, 100, 740)], 5),
    ('head on the top row', [(200, 0, 300, 740)], 5),
    ('a ground slab', [(40, 600, 470, 740)], 5),
]


def main():
    fails = 0
    with tempfile.TemporaryDirectory() as d:
        for name, boxes, want in CASES:
            src, out = os.path.join(d, 'cut.png'), os.path.join(d, 'fig.png')
            cut(boxes).save(src)
            got = subprocess.run([sys.executable, LURKER, 'crop', src, out], capture_output=True, check=False).returncode
            ok = got == want and (want or Image.open(out).size == (102, 622))
            fails += not ok
            print(('ok  ' if ok else 'FAIL') + f' {name}: exit {got}, want {want}')
    sys.exit(1 if fails else 0)


if __name__ == '__main__':
    main()
