"""r311: the labs' stills of two runs, pixel by pixel. compare.py A B: every .png under A against B's, its mean and max
difference in /255 and the count of pixels that moved. Exits 1 if any still is missing or moved."""
import pathlib
import sys

import numpy as np
from PIL import Image

a, b = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
moved = 0
for left in sorted(a.rglob("*.png")):
    rel = left.relative_to(a)
    right = b / rel
    if not right.exists():
        print(f"MISSING {rel}")
        moved += 1
        continue
    x = np.asarray(Image.open(left).convert("RGB"), dtype=np.int16)
    y = np.asarray(Image.open(right).convert("RGB"), dtype=np.int16)
    if x.shape != y.shape:
        print(f"SIZE {rel} {x.shape} {y.shape}")
        moved += 1
        continue
    d = np.abs(x - y)
    pixels = int((d.max(axis=2) > 0).sum())
    print(f"{'same' if pixels == 0 else 'MOVED'} {rel}: mean {d.mean():.4f}/255, max {d.max()}, {pixels} pixels")
    moved += pixels > 0
sys.exit(1 if moved else 0)
