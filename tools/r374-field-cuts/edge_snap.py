#!/usr/bin/env python3
"""r374's rejected candidate: snap each slab cut (a band's edge through one surface) to the strongest edge within R
rows, by a min-cost path through the painting's gradient, pinned to the old cut at its run's ends. On river's near
field there is no painted line within 40 rows; at R 80 the path draws straight ramps and dives to the river banks.

    ~/ComfyUI/venv/bin/python tools/r374-field-cuts/edge_snap.py build/ai/depth/river build/r374/river [R LAM STEP]

Reads OUT/cut/bands.png (decoded back to labels) and painting.png; writes PREFIX_before.png, PREFIX_after.png,
PREFIX_edges.png.
"""
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

pal = np.array([(70, 90, 160), (90, 160, 120), (220, 180, 80), (220, 90, 70), (200, 80, 200), (240, 240, 240)])
run, out = sys.argv[1], sys.argv[2]
R = int(sys.argv[3]) if len(sys.argv) > 3 else 40
lam = float(sys.argv[4]) if len(sys.argv) > 4 else 0.5
b = np.asarray(Image.open(run+'/cut/bands.png').convert('RGB')).astype(int)
L = np.argmin(((b[:, :, None] - pal[None, None]) ** 2).sum(-1), -1)
img=np.asarray(Image.open(run+'/cut/painting.png').convert('RGB')).astype(np.float32)
h,w=L.shape; n=4

def edges(img):
    s = ndimage.gaussian_filter(img, (1.5, 1.5, 0), mode=("nearest", "wrap", "nearest"))
    g = np.hypot(np.linalg.norm(ndimage.sobel(s, axis=0, mode="nearest"), axis=-1),
                 0.5*np.linalg.norm(ndimage.sobel(s, axis=1, mode="wrap"), axis=-1))
    g = ndimage.uniform_filter1d(g, 5, axis=1, mode="wrap")
    return (g / np.percentile(g, 99)).clip(0, 1)

def cut_rows(L, img, k, apart=25):
    """per column the row of a k-over-k+1 crossing through one surface, else -1"""
    b = np.full(w, -1)
    for x in range(w):
        ys = np.nonzero((L[:-1, x] == k) & (L[1:, x] == k + 1))[0] + 1
        for y in ys:
            if y < 10 or y > h - 10: continue
            xs = [(x + d) % w for d in range(-3, 4)]
            a = img[y-10:y-2][:, xs].reshape(-1, 3).mean(0); c = img[y+2:y+10][:, xs].reshape(-1, 3).mean(0)
            col = L[max(0, y-R):y+R, x]
            if np.linalg.norm(a - c) < apart and np.isin(col, (k, k+1)).all():
                b[x] = y; break
    return b

def runs(has):
    w = len(has); out = []
    if has.all(): return [(0, w)]
    start = int(np.argmin(has)); x = 0
    while x < w:
        c = (start + x) % w
        if not has[c]: x += 1; continue
        r = 0
        while has[(c + r) % w]: r += 1
        out.append((c, r)); x += r
    return out

def path(E, b, c, r, R, lam, step=1):
    xs = [(c + i) % w for i in range(r)]
    rows = np.arange(h)
    cost = np.full((r, h), np.inf)
    for i, x in enumerate(xs):
        ok = (np.abs(rows - b[x]) <= R)
        cost[i, ok] = (1 - E[ok, x]) + lam * np.abs(rows[ok] - b[x]) / R
    acc = cost.copy(); back = np.zeros((r, h), int)
    # pin start
    start = np.full(h, np.inf); start[b[xs[0]]] = 0
    acc[0] = start
    for i in range(1, r):
        prev = acc[i-1]
        best = prev.copy(); arg = rows.copy()
        for d in range(1, step+1):
            up = np.r_[prev[d:], [np.inf]*d]; dn = np.r_[[np.inf]*d, prev[:-d]]
            m = up < best; best[m] = up[m]; arg[m] = rows[m] + d
            m = dn < best; best[m] = dn[m]; arg[m] = rows[m] - d
        acc[i] = best + cost[i]; back[i] = arg
    y = b[xs[-1]] if np.isfinite(acc[-1, b[xs[-1]]]) else int(acc[-1].argmin())
    p = np.zeros(r, int)
    for i in range(r - 1, -1, -1):
        p[i] = y; y = back[i, y]
    return xs, p

STEP = int(sys.argv[5]) if len(sys.argv) > 5 else 1
E = edges(img)
out_L = L.copy()
for k in range(n - 1):
    b = cut_rows(L, img, k)
    has = b >= 0
    for c, r in runs(has):
        if r < 24: continue
        bb = b.copy()
        xs, p = path(E, bb, c, r, R, lam, STEP)
        moved = 0
        for x, y in zip(xs, p):
            y0 = b[x]
            if y < y0:
                seg = out_L[y:y0, x]; m = seg == k; seg[m] = k + 1; moved += m.sum()
            elif y > y0:
                seg = out_L[y0:y, x]; m = seg == k + 1; seg[m] = k; moved += m.sum()
        print(f"k={k}: run at {c} len {r}: moved {moved}, mean |dy| {np.abs(p - b[xs]).mean():.1f}, edge on path {E[p, xs].mean():.2f} vs {E[b[xs], xs].mean():.2f}")
img8 = img.astype(np.uint8)
def over(LL):
    return (img * 0.55 + pal[LL] * 0.45).astype(np.uint8)
Image.fromarray(over(L)).save(out + '_before.png')
Image.fromarray(over(out_L)).save(out + '_after.png')
Image.fromarray((E*255).astype(np.uint8)).save(out + '_edges.png')
np.save(out + '_L.npy', out_L)
