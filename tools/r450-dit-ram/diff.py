#!/usr/bin/env python3
"""r450: two strips' pixel difference: max and mean level (0-255) and how many pixels differ.  diff.py A B"""
import sys

import numpy as np
from PIL import Image

a, b = (np.asarray(Image.open(p).convert("RGB")).astype(int) for p in sys.argv[1:3])
d = np.abs(a - b)
print(f"max {d.max()}, mean {d.mean():.4f}, {(d.max(axis=2) > 0).sum()} px of {d.shape[0] * d.shape[1]} differ")
