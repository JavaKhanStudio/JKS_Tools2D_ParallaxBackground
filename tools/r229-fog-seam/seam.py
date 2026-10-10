"""r229: how hard a still of the r229-fog-seam round cuts between two columns. For each pair of neighbouring columns,
the mean |difference| over the rows the mist covers; prints the worst step, where it is, and the median step. A seam
is a worst step many times the median (before the fix, w5); a seamless mist keeps it near (w25, and w5 after).

  python3 tools/r229-fog-seam/seam.py STILL.png [STILL.png ...]
"""
import sys

import numpy as np
from PIL import Image

for path in sys.argv[1:]:
    a = np.asarray(Image.open(path).convert('RGB'), dtype=np.float64)
    level = a.mean(axis=2)
    # The mist's rows: brighter than the near-black gradients somewhere along them.
    rows = level.max(axis=1) > 40
    band = level[rows]
    steps = np.abs(np.diff(band, axis=1)).mean(axis=0)
    worst = int(steps.argmax())
    print(f"{path}: worst step {steps[worst]:.2f}/255 at column {worst}|{worst + 1}, median {np.median(steps):.2f}, "
          f"ratio {steps[worst] / max(np.median(steps), 1e-6):.1f}, {rows.sum()} rows")
