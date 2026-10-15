"""r384: second-round gate measures over inpf's 30 seeds, against labels by eye (round crowns across the width).

    git apply tools/r384-hills/gate.patch   # ai_page.box and canopy are the patch's
    python3 tools/r384-hills/features2.py build/r384/try/inpf
"""
import glob
import os
import re
import sys

import numpy as np
from PIL import Image, ImageFilter

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "r239-ai-strips"))
import ai_page

GOOD = {7021, 7023, 7034, 7042, 7047}
UNSURE = {7039}  # tall round-topped trees, cypress-like


def feats(p):
    rgba = np.asarray(Image.open(p).convert("RGBA")).astype(float)
    a = rgba[..., 3] > 127
    inside = np.asarray(Image.fromarray(a.astype(np.uint8) * 255).filter(ImageFilter.MinFilter(17))) > 127
    lum = rgba[..., :3] @ [0.299, 0.587, 0.114]
    sd = np.sqrt(np.maximum(ai_page.box(lum ** 2, 9) - ai_page.box(lum, 9) ** 2, 0))
    rel = sd / np.maximum(ai_page.box(lum, 9), 8)
    out = {}
    for name, busy in (("abs6", sd > 6), ("rel08", rel > 0.08), ("rel12", rel > 0.12)):
        cols = (busy & inside).sum(0) / np.maximum(inside.sum(0), 1)
        out[name] = (cols > 0.2).mean()
    top = np.argmax(a, axis=0).astype(float)
    def sm(x, n):
        return np.convolve(np.concatenate([x[-(n // 2):], x, x[:n // 2]]), np.ones(n) / n, "valid")
    fine = sm(top, 5) - sm(top, 49)
    # bumps: local minima of the skyline row (crown tops) of prominence > 3 px, per 100 px
    t = sm(top, 5)
    peaks = 0
    for x in range(len(t)):
        w = t[np.arange(x - 12, x + 13) % len(t)]
        if t[x] == w.min() and w.max() - t[x] > 3:
            peaks += 1
    out["bumps"] = peaks / len(t) * 100
    out["resid"] = np.abs(fine).mean()
    out["sharp"] = ai_page.canopy(p)[1]
    out["dark"] = lum[inside].mean()
    return out


rows = []
for p in sorted(glob.glob(os.path.join(sys.argv[1], "s*_cut.png"))):
    s = int(re.search(r"s(\d+)_cut", p)[1])
    rows.append((s, feats(p)))
keys = list(rows[0][1])
print("seed  lab " + "".join(k.rjust(7) for k in keys))
for s, f in sorted(rows, key=lambda r: -r[1]["rel08"]):
    lab = "GOOD" if s in GOOD else "?" if s in UNSURE else ""
    print(f"{s} {lab:5}" + "".join(f"{f[k]:7.2f}" for k in keys))
