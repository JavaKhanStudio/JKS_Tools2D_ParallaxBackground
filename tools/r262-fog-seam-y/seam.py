"""r262: how hard a still of the r262-fog-seam-y round cuts between two rows (tools/r229-fog-seam/seam.py, turned):
for each pair of neighbouring rows, the mean |difference| over the columns the slab covers; prints the worst step,
where it is, and the median. A seam is a worst step many times the median; a seamless mist keeps it near.

  python3 tools/r262-fog-seam-y/seam.py STILL.png [STILL.png ...]
"""
import sys

import numpy as np
from PIL import Image

for path in sys.argv[1:]:
    a = np.asarray(Image.open(path).convert('RGB'), dtype=np.float64)
    level = a.mean(axis=2)
    # The slab's columns: brighter than the near-black gradients somewhere along them, a margin off its sides.
    cols = np.flatnonzero(level.max(axis=0) > 40)[4:-4]
    band = level[:, cols]
    steps = np.abs(np.diff(band, axis=0)).mean(axis=1)
    worst = int(steps.argmax())
    print(f"{path}: worst step {steps[worst]:.2f}/255 at row {worst}|{worst + 1}, median {np.median(steps):.2f}, "
          f"ratio {steps[worst] / max(np.median(steps), 1e-6):.1f}, {len(cols)} columns")
