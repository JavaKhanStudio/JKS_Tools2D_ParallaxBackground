#!/usr/bin/env python3
"""The depth lab's own Hiver fills its 2D frame (r504), as tools/depth-lab.sh --big --slider page=4 --shot draws it.

Fails when the page file is not what core/test-data/depth/make_lab.py writes; when a row of the 2D frame is half pure white
or the frame's flat rows (one colour across, r502's white half) pass FLAT_MAX; when the mountains' cut top is under the
view's top by the page's numbers; or when the 3D still's sky card is the white of the old gradients.
  python3 tools/r504-hiver-frame/check.py 2D.png 3D.png
"""
import json
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
PAGE = ROOT / 'core/test-data/depth/hiver.jplax'
FLAT_MAX = 0.05
# The mountains' height over their sizeRatio, measured on r502's lowered still: 33.3% of the view at 1.18.
TALL_PER_SIZE = 33.3 / 1.18
# Where the sky card shows past the other cards in the 3D still at its opening view.
SKY_CARD = (1040, 240, 1170, 280)


def fail(why):
    sys.exit(f'FAIL: {why}')


def main():
    before = PAGE.read_bytes()
    subprocess.run([sys.executable, 'core/test-data/depth/make_lab.py'], cwd=ROOT, check=True)
    if PAGE.read_bytes() != before:
        fail(f'{PAGE} was not what make_lab.py writes')
    flat2d = np.asarray(Image.open(sys.argv[1]).convert('RGB')).astype(int)
    white = (flat2d.min(axis=2) >= 250).mean(axis=1)
    flat = (flat2d.max(axis=1) - flat2d.min(axis=1)).max(axis=1) <= 2
    print(f'2D: most white in a row {white.max():.3f}, flat rows {flat.mean():.3f}, colour spread {flat2d.std():.1f}')
    if white.max() >= 0.5:
        fail(f'row {int(white.argmax())} is {white.max():.0%} white')
    if flat.mean() > FLAT_MAX:
        fail(f'{flat.mean():.0%} of the rows are one flat colour')
    if flat2d.std() < 10:
        fail('the frame holds no picture')
    mountains = json.loads(before)['pageModel']['pageList'][1]
    top = mountains['decal_Y_Ratio'] + TALL_PER_SIZE * mountains['sizeRatio']
    print(f'mountains: their cut top at {top:.1f}% of the view')
    if top <= 100:
        fail('the mountains\' cut top shows in the frame')
    card = np.asarray(Image.open(sys.argv[2]).convert('RGB').crop(SKY_CARD)).reshape(-1, 3).astype(int)
    print(f'3D: the sky card {card.mean(axis=0).round()}')
    if card.min(axis=1).mean() >= 215:
        fail('the 3D sky card is white')
    print('OK')


if __name__ == '__main__':
    main()
