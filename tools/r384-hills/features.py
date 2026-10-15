"""r384: candidate measures for the hills gate, printed per cut strip, to find one that tells inpf's round-tree
seeds (7021, 7023) from the flat, conifer and lone-tree ones.

    python3 tools/r384-hills/features.py build/r384/try/inpf/s*_cut.png
"""
import sys

import numpy as np
from PIL import Image, ImageFilter


def box(x, n):
    """Mean over an n x n box (edges clamped), numpy only: ai_page.py's python3 has no scipy."""
    p = n // 2
    x = np.pad(x, p, mode="edge")
    c = np.cumsum(np.cumsum(np.pad(x, ((1, 0), (1, 0))), 0), 1)
    return (c[n:, n:] - c[:-n, n:] - c[n:, :-n] + c[:-n, :-n]) / (n * n)


def erode(a, px):
    return np.asarray(Image.fromarray((a * 255).astype(np.uint8)).filter(ImageFilter.MinFilter(2 * px + 1))) > 127


def features(path):
    rgba = np.asarray(Image.open(path).convert("RGBA")).astype(float)
    a = rgba[..., 3] > 127
    lum = rgba[..., :3] @ [0.299, 0.587, 0.114]
    inside = erode(a, 8)
    top = np.argmax(a, axis=0).astype(float)
    rough = float(np.abs(np.roll(top, 1) - 2 * top + np.roll(top, -1)).mean())
    gaps = float(1 - a[-1].mean())  # columns open to the bottom
    hp = lum - box(lum, 9)
    tex = float(np.abs(hp[inside]).mean())
    local = np.sqrt(np.maximum(box(lum ** 2, 9) - box(lum, 9) ** 2, 0))
    busy = float((local[inside] > 6).mean())  # share of the inside with texture
    cols = np.array([(local[:, x][inside[:, x]] > 6).mean() if inside[:, x].any() else 0 for x in range(a.shape[1])])
    spread = float((cols > 0.2).mean())  # share of the columns with texture: one tree is not a forest
    # a band 40 px under the skyline: canopy lumps live there
    rows = np.arange(a.shape[0])[:, None]
    band = inside & (rows >= top[None, :] + 8) & (rows < top[None, :] + 48)
    bandbusy = float((local[band] > 6).mean()) if band.any() else 0
    # skyline peaks sharpness: mean |second diff| smoothed at 9 px (triangles keep it, round lumps less, flat 0)
    st = np.convolve(np.concatenate([top[-4:], top, top[:4]]), np.ones(9) / 9, "valid")
    sharp = float(np.abs(np.roll(st, 4) - 2 * st + np.roll(st, -4)).mean())
    sat = rgba[..., :3].max(-1) - rgba[..., :3].min(-1)
    return {"rough": rough, "gaps": gaps, "tex": tex, "busy": busy, "spread": spread, "band": bandbusy,
            "sharp": sharp, "lum": float(lum[inside].mean()), "sat": float(sat[inside].mean()),
            "lstd": float(lum[inside].std())}


if __name__ == "__main__":
    rows = [(p, features(p)) for p in sys.argv[1:]]
    keys = list(rows[0][1])
    print("strip".ljust(40) + "".join(k.rjust(8) for k in keys))
    for p, f in rows:
        print(p[-40:].ljust(40) + "".join(f"{f[k]:8.2f}" for k in keys))
