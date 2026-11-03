#!/usr/bin/env python3
"""Do AI beach's palms read on their card? (r503) Measures a still of tools/depth-lab.sh --big --slider page=1 --slider
view=1, at the lab's default turn: of the front card's lower half, the share of pixels at least INK/255 darker than the
card's own median. The still from before r503 fails it (specks on a pane as dark as the ground), so the number is held
from both sides.
  python3 tools/r503-depth-cards/palms.py F.png        exit 0 when the palms read
"""
import sys

import numpy as np
from PIL import Image

# The front card's area where the palms stand, clear of the tags, in the 1280 x 720 still.
BOX = (345, 370, 395, 460)
INK = 40
ENOUGH = 0.05


def main():
    pixels = np.asarray(Image.open(sys.argv[1]).convert('L').crop(BOX)).astype(int)
    share = float((pixels <= np.median(pixels) - INK).mean())
    print(f'{sys.argv[1]}: {100 * share:.1f}% of the card at least {INK}/255 darker than its median (needs {100 * ENOUGH:.0f}%)')
    sys.exit(0 if share >= ENOUGH else 1)


if __name__ == '__main__':
    main()
