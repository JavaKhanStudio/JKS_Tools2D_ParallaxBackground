"""moved.py BEFORE AFTER — r454: per strip of two re-cuts (tools/r364-ghosts/recut.sh's OUTs), the px whose alpha rose
or fell by more than a quarter, and the box round them: a layer.py change that should only touch one kind of fault
shows where else it bit. Largest first.
    python3 tools/r454-wide-mass/moved.py build/r454/before build/r454/after"""
import os
import sys

import numpy as np
from PIL import Image


def main():
    before, after = sys.argv[1], sys.argv[2]
    rows = []
    for page in sorted(os.listdir(after)):
        if not os.path.isdir(os.path.join(after, page)):
            continue
        for f in sorted(os.listdir(os.path.join(after, page))):
            b = os.path.join(before, page, f)
            if not os.path.exists(b):
                continue
            a0 = np.asarray(Image.open(b).convert("RGBA"))[..., 3].astype(int)
            a1 = np.asarray(Image.open(os.path.join(after, page, f)).convert("RGBA"))[..., 3].astype(int)
            up, down = a1 - a0 > 64, a0 - a1 > 64
            ys, xs = np.nonzero(up | down)
            where = f"x {xs.min()}-{xs.max()} y {ys.min()}-{ys.max()}" if len(xs) else ""
            rows.append((int(up.sum() + down.sum()), page, f, int(up.sum()), int(down.sum()), where))
    for n, page, f, up, down, where in sorted(rows, reverse=True):
        print(f"{page:16} {f:10} +{up:6} -{down:6}  {where}")


if __name__ == "__main__":
    main()
