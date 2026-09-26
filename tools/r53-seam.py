#!/usr/bin/env python3
"""r53: finds vertical seam lines in the parallax preview of an editor shot (x 370..1236, the top layer's rows).
The layer's tiles meet at x=456 in that flow. A seam is a column much darker or lighter than both its neighbours over most rows. Prints the worst columns.
  tools/r53-seam.py editor/build/r53/out/reopened.png [top bottom]"""
import sys
import numpy as np
from PIL import Image

shot = sys.argv[1]
top, bottom = (int(sys.argv[2]), int(sys.argv[3])) if len(sys.argv) > 3 else (110, 190)
g = np.asarray(Image.open(shot).convert('L').crop((370, top, 1236, bottom))).astype(float)
# How far each column sits from the mean of its two neighbours, averaged over the rows.
step = np.abs(g[:, 1:-1] - (g[:, :-2] + g[:, 2:]) / 2).mean(axis=0)
worst = sorted(((round(v, 1), x + 371) for x, v in enumerate(step)), reverse=True)[:3]
print(shot, 'worst columns (score, x):', worst, '| tile join x=456:', round(step[456 - 371], 1))
