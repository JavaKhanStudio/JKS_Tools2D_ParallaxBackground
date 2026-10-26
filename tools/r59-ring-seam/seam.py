#!/usr/bin/env python3
"""seam.py PNG — r59: how hard the Godot rings lab's join shot (<scene>-join-t8.png, 1600x820, controls 380 wide) steps
where panel B's SHADER band closes its ring. lookAtJoin turns panel B's centre (x 1295) to the join. For each column of
panel B, the mean absolute difference from the column left of it over the band's rows (ROWS, the mist of
engines/threejs/tests/rings-seam at the lab's defaults); prints the worst within 15 px of the centre ('join': the
join lands at 1289) and the 99th percentile of the panel ('body': the mist's own sharpest columns)."""
import sys

import numpy as np
from PIL import Image

ROWS = slice(335, 395)
LEFT, CENTRE, RIGHT = 1000, 1295, 1595
img = np.asarray(Image.open(sys.argv[1]).convert('RGB'), dtype=float)[ROWS]
jumps = np.abs(np.diff(img, axis=1)).mean(axis=(0, 2))  # jumps[x - 1]: column x against x - 1
join = jumps[CENTRE - 16:CENTRE + 14].max()
body = np.percentile(jumps[LEFT:RIGHT], 99)
print(f'join {join:.2f} body {body:.2f}')
