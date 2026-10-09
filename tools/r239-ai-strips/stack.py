"""Stack layer PNGs back to front over a sky gradient, each drawn twice side by side (r239).

    python3 tools/r239-ai-strips/stack.py out.png back.png [mid.png ...] [--scale 4] [--shift 0.0,0.3,0.6] [--lift 0.4,0.2,0]

--shift moves each layer left by that fraction of its width, as a scroll would, so the joins of the
layers do not all fall on one column. A red tick under the picture marks each layer's join.
--lift raises each layer by that fraction of the picture's height, as a page's decal Y would; its
bottom row is drawn on down to the bottom edge.
--scale draws small (pixel art) layers back up with nearest neighbour.
"""
import sys

import numpy as np
from PIL import Image

args = sys.argv[1:]
scale, shifts = 1, None
if "--scale" in args:
    k = args.index("--scale"); scale = int(args[k + 1]); del args[k:k + 2]
if "--shift" in args:
    k = args.index("--shift"); shifts = [float(v) for v in args[k + 1].split(",")]; del args[k:k + 2]
lifts = None
if "--lift" in args:
    k = args.index("--lift"); lifts = [float(v) for v in args[k + 1].split(",")]; del args[k:k + 2]
out, paths = args[0], args[1:]
layers = [Image.open(p).convert("RGBA") for p in paths]
layers = [l.resize((l.width * scale, l.height * scale), Image.NEAREST) for l in layers]
w, h = layers[0].width * 2, max(l.height for l in layers)
top, bottom = np.array([150, 185, 225.]), np.array([225, 232, 240.])
t = np.linspace(0, 1, h)[:, None, None]
sky = (top * (1 - t) + bottom * t) * np.ones((h, w, 3))
canvas = Image.fromarray(sky.astype(np.uint8)).convert("RGBA")
ticks = []
for i, l in enumerate(layers):
    s = int((shifts[i] if shifts else 0) * l.width)
    arr = np.roll(np.asarray(l), -s, axis=1)
    up = int((lifts[i] if lifts else 0) * h)
    if up:
        arr = np.concatenate([arr[up:], np.repeat(arr[-1:], up, axis=0)])
    rolled = Image.fromarray(arr)
    strip = Image.new("RGBA", (w, h))
    strip.paste(rolled, (0, h - l.height)); strip.paste(rolled, (l.width, h - l.height))
    canvas = Image.alpha_composite(canvas, strip)
    ticks.append((l.width - s) % l.width or l.width)
full = Image.new("RGBA", (w, h + 6), (40, 40, 40, 255))
full.paste(canvas, (0, 0))
px = full.load()
for x in ticks:
    for y in range(h, h + 6):
        for dx in (-1, 0):
            px[x + dx, y] = (255, 60, 60, 255)
full.convert("RGB").save(out)
