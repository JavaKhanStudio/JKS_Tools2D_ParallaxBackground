#!/usr/bin/env python3
"""r409: holes in a depth page's far range: the clear pixels of OUT/cut/l0.png under its own first opaque row, column
by column, where the filled sky behind showed through the hills (river's blue patches).

    python3 tools/r409-clouds/holes.py OUT...
"""
import sys

import numpy as np
from PIL import Image

for out in sys.argv[1:]:
    a = np.asarray(Image.open(f"{out}/cut/l0.png").convert("RGBA"))[..., 3] >= 128
    under = np.maximum.accumulate(a, axis=0)  # at or under the column's first opaque row
    holes = under & ~a
    print(f"{out}: {int(holes.sum())} px clear under the range's top, {int(holes.any(axis=0).sum())} columns")
