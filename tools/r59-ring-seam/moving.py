#!/usr/bin/env python3
"""moving.py DIR — r59: whether g03's FOG band moves on the Godot rings: DIR holds the lab's g03-both-t0.png and
g03-both-t8.png shot with the camera still (speed=0). Nothing on g03 drifts, so between the two only the effect's
clock runs: prints the mean grey difference over the mist's rows (275-350) of panel B ('mist') and over the rows
above and below it ('rest')."""
import sys

import numpy as np
from PIL import Image

a, b = (np.asarray(Image.open(f'{sys.argv[1]}/g03-both-t{t}.png').convert('L'), dtype=float)[:, 990:1600]
        for t in (0, 8))
d = np.abs(a - b)
print(f'mist {d[275:350].mean():.2f} rest {np.r_[d[100:250], d[375:500]].mean():.2f}')
