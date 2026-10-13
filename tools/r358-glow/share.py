"""r358: a cut layer's translucent share, as parallax-ai's tools/review/cut_stats.py counts it (no scipy).

    python3 tools/r358-glow/share.py OUT/*.png

The share of its visible pixels (alpha over 5 %) that are not solid (alpha under 95 %).
"""
import sys

import numpy as np
from PIL import Image

for p in sys.argv[1:]:
    a = np.asarray(Image.open(p).convert("RGBA"))[..., 3] / 255
    visible = a > 0.05
    print(f"{p}\t{((a > 0.05) & (a < 0.95)).sum() / max(visible.sum(), 1):.1%}")
