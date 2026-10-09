"""r218: what a region's left/right edges hold, per atlas region: the edge column's alpha (mean over rows where the
column one texel in is opaque), the alpha 1 and 3 texels in, the alpha/RGB of the atlas column just outside it, and how
far the left edge is from the right (mean |RGBA| difference, the r139 'seam').
python3 tools/r218-seams/atlas_edges.py ATLAS..."""
import os
import sys
import numpy as np
from PIL import Image

def regions(path):
    lines = open(path).read().splitlines()
    page, out, name = None, [], None
    for i, line in enumerate(lines):
        if not line.strip():
            page = None
            continue
        if page is None and not line.startswith(' ') and ':' not in line:
            page = np.asarray(Image.open(os.path.join(os.path.dirname(path), line)).convert('RGBA'), dtype=np.int16)
            continue
        if not line.startswith(' ') and ':' not in line:
            name = line
        elif line.strip().startswith('xy:'):
            xy = [int(v) for v in line.split(':')[1].split(',')]
        elif line.strip().startswith('size:') and line.startswith(' '):
            size = [int(v) for v in line.split(':')[1].split(',')]
            out.append((name, page, xy, size))
    return out

for atlas in sys.argv[1:]:
    print(atlas)
    for name, page, (x, y), (w, h) in regions(atlas):
        r = page[y:y + h, x:x + w]
        rows = r[:, 1, 3] == 255
        rowsR = r[:, -2, 3] == 255
        def a(col, mask):
            return round(float(r[mask, col, 3].mean()), 1) if mask.any() else None
        outL = page[y:y + h, x - 1] if x > 0 else None
        outR = page[y:y + h, x + w] if x + w < page.shape[1] else None
        def out(o, mask):
            if o is None or not mask.any():
                return None
            return 'a%.0f rgb%.0f' % (o[mask, 3].mean(), o[mask, :3].mean())
        seam = float(np.abs(r[:, 0] - r[:, -1]).mean())
        print('  %-14s %5dx%-5d L edge a=%s in1=%s out:%s | R edge a=%s out:%s | seam %.0f' % (
            name, w, h, a(0, rows), a(1, rows), out(outL, rows), a(-1, rowsR), out(outR, rowsR), seam))
