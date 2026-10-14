#!/usr/bin/env python3
"""The sky a near layer carries opaque (r361): per layer of depth-page runs, its opaque pixels whose colour is the
sky's (blue over red by 40 and over green by 15), and its opaque area, beside a before run's when one is given.

    ~/ComfyUI/venv/bin/python tools/r279-depth-page/see_through.py RUN [BEFORE_RUN]
    ~/ComfyUI/venv/bin/python tools/r279-depth-page/see_through.py RUN --crop l3 X0 Y0 X1 Y1 OUT.png [BEFORE_RUN]

A crown's gaps cleared show as the count falling in the near layers and their opaque area falling by about as much;
leaves eaten, as the area falling by much more. The count also takes a river's water, which is the front's own ground:
read it beside the crop. --crop writes the layer over magenta, cropped (painting coordinates) and enlarged 3 times,
the before run's above it when one is given.
"""
import sys

import numpy as np
from PIL import Image


def layer(run, name):
    """The layer as a painting-sized RGBA array: OUT/cut/<name>.png starts at its first row with art."""
    paint = Image.open(f"{run}/cut/painting.png")
    im = Image.open(f"{run}/cut/{name}.png").convert("RGBA")
    full = Image.new("RGBA", paint.size)
    full.paste(im, (0, paint.height - im.height))
    return full


def counts(run):
    out = []
    k = 0
    while True:
        try:
            a = np.asarray(layer(run, f"l{k}")).astype(int)
        except FileNotFoundError:
            return out
        opaque = a[..., 3] > 128
        sky = opaque & (a[..., 2] > a[..., 0] + 40) & (a[..., 2] > a[..., 1] + 15)
        out.append((int(sky.sum()), int(opaque.sum())))
        k += 1


def crop(runs, name, box, out):
    tiles = []
    for run in runs:
        bg = Image.new("RGBA", Image.open(f"{run}/cut/painting.png").size, (255, 0, 255, 255))
        bg.alpha_composite(layer(run, name))
        c = bg.crop(box).convert("RGB")
        tiles.append(c.resize((c.width * 3, c.height * 3), Image.NEAREST))
    sheet = Image.new("RGB", (tiles[0].width, sum(t.height for t in tiles) + 6 * (len(tiles) - 1)), "white")
    y = 0
    for t in tiles:
        sheet.paste(t, (0, y))
        y += t.height + 6
    sheet.save(out)


def main(argv):
    if "--crop" in argv:
        i = argv.index("--crop")
        run, name, box, out = argv[0], argv[i + 1], tuple(int(v) for v in argv[i + 2:i + 6]), argv[i + 6]
        crop(argv[1:i] + argv[i + 7:] + [run], name, box, out)
        return 0
    runs = argv[:2]
    rows = [counts(r) for r in runs]
    for k in range(len(rows[0])):
        now = rows[0][k]
        line = f"l{k}: sky {now[0]:6d}  opaque {now[1]:7d}"
        if len(rows) > 1 and k < len(rows[1]):
            was = rows[1][k]
            line += f"   before: sky {was[0]:6d}  opaque {was[1]:7d}  ({now[1] - was[1]:+d} px)"
        print(line)
    return 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    sys.exit(main(sys.argv[1:]))
