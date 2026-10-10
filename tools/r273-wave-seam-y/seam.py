"""r273: how hard a still of the r273-wave-seam-y round cuts between two rows: each row's sideways shift is the phase of
its stripes at their period on screen, over the columns the layer covers; for each pair of neighbouring rows,
the |difference| of that shift. A WAVE ripple moves it smoothly; a cut jumps it. Prints the
worst step, where it is, and the median.

  python3 tools/r273-wave-seam-y/seam.py STILL.png [STILL.png ...]
"""
import sys

import numpy as np
from PIL import Image

for path in sys.argv[1:]:
    level = np.asarray(Image.open(path).convert('L'), dtype=np.float64)
    # The stripes' columns: some pure white along them, a margin off the layer's sides.
    cols = np.flatnonzero(level.max(axis=0) > 200)[40:-40]
    band = level[:, cols]
    # The stripes' period on screen: the strongest of 20 to 200 px in tenths over the mean row.
    x, mean = np.arange(len(cols)), band.mean(axis=0) - band.mean()
    periods = np.arange(20, 200, 0.1)
    period = periods[np.abs(np.exp(-2j * np.pi * np.outer(1 / periods, x)) @ mean).argmax()]
    wave = np.exp(-2j * np.pi * np.arange(len(cols)) / period)
    shift = np.unwrap(np.angle((band - 128) @ wave)) * period / (2 * np.pi)
    steps = np.abs(np.diff(shift))
    worst = int(steps.argmax())
    print(f"{path}: worst step {steps[worst]:.2f} px at row {worst}|{worst + 1}, median {np.median(steps):.2f}, "
          f"ratio {steps[worst] / max(np.median(steps), 1e-6):.1f}, period {period:.1f} px")
