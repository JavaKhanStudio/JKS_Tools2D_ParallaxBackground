"""r218: finds thin dark vertical bars in stills: a column darker than the pixels 2 to its left AND right.
python3 tools/r218-seams/find_seams.py STILL.png... ; prints columns where many rows dip (a seam), with how many."""
import sys
from PIL import Image
import numpy as np

DIP, MIN_ROWS = 18, 25
for path in sys.argv[1:]:
    a = np.asarray(Image.open(path).convert('L'), dtype=np.int16)
    left, mid, right = a[:, :-4], a[:, 2:-2], a[:, 4:]
    dip = (mid < left - DIP) & (mid < right - DIP)
    rows = dip.sum(axis=0)
    hits = [(x + 2, int(n)) for x, n in enumerate(rows) if n >= MIN_ROWS]
    print(path.split('/')[-1], hits[:12], '...' if len(hits) > 12 else '')
