"""What tells a dune's shading from a creature (r438): each blob creature.find() names, measured.

    python3 tools/r438-dunes/probe.py CUT SRC [CUT SRC ...]

Per blob: its size, how dark it is against the ring of mass around it (luminance ratio), how far its hue is (chroma,
0..255), and the share of its outline that is a sharp step (half its contrast or more within 2 px).
"""
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, "tools/r239-ai-strips")
import creature
from layer import components, spread


def blobs(rgb, alpha, lo, hi, r):
    found = creature.find(rgb, alpha, lo, hi, r=r)
    solid = spread(alpha >= 0.5, -4)
    out = creature.standing_out(rgb, solid, solid.astype(np.float64), r, 40, 3.0)
    out = creature.standing_out(rgb, solid, (solid & ~spread(out, 3)).astype(np.float64), r, 40, 3.0)
    label, _ = components(out)
    for x, y, size in found:
        ys, xs = np.nonzero(label)
        k = label[ys, xs][np.argmin((xs - x) ** 2 + (ys - y) ** 2)]
        yield x, y, size, label == k, solid


def measure(rgb, b, solid):
    ring = spread(b, 6) & ~spread(b, 2) & solid
    lum = rgb @ np.array([0.299, 0.587, 0.114])
    cb, cr = rgb[b].mean(0), rgb[ring].mean(0)
    dark = lum[b].mean() / max(lum[ring].mean(), 1)
    hue = np.linalg.norm(cb / max(cb.sum(), 1) - cr / max(cr.sum(), 1)) * 255
    edge = b & ~spread(b, -1)
    contrast = abs(lum[ring].mean() - lum[b].mean())
    out2 = spread(b, 2) & ~spread(b, 1)
    # for each edge pixel, the largest step to an outside pixel 2 px away
    steps = []
    for y, x in zip(*np.nonzero(edge)):
        win = (slice(max(y - 2, 0), y + 3), np.arange(x - 2, x + 3) % b.shape[1])
        o = out2[win[0]][:, win[1]]
        if o.any():
            steps.append(abs(lum[y, x] - lum[win[0]][:, win[1]][o].mean()))
    sharp = np.mean(np.array(steps) >= 0.5 * contrast) if steps else 0
    return dark, hue, contrast, sharp


for cut, src in zip(sys.argv[1::2], sys.argv[2::2]):
    s = Image.open(src).convert("RGB")
    alpha = np.asarray(Image.open(cut).convert("RGBA").getchannel("A").resize(s.size, Image.NEAREST)) / 255.0
    rgb = np.asarray(s).astype(np.float64)
    k = (s.width / 1024) ** 2
    r = int(12 * k ** 0.5)
    for x, y, size, b, solid in blobs(rgb, alpha, int(50 * k), int(3000 * k), r):
        dark, hue, contrast, sharp = measure(rgb, b, solid)
        print(f"{cut} x={x} y={y} size={size} dark={dark:.2f} hue={hue:.1f} contrast={contrast:.0f} sharp={sharp:.2f}")
