#!/usr/bin/env python3
"""Two depth-page runs of one painting side by side (r360): before on the left, after on the right.

    python3 tools/r360-band-edges/before_after.py BEFORE AFTER OUT_PREFIX

OUT_PREFIX-layers.png: each layer of OUT/cut (l0 back .. front) over magenta, at the painting's full height, so a hole
or a stray piece shows; OUT_PREFIX-stills.png: the stills at 0, 6 and 12 s of OUT/shots.
"""
import os
import sys

from PIL import Image

MAGENTA = (255, 0, 255)


def layer(run, k, w, h):
    im = Image.open(os.path.join(run, "cut", f"l{k}.png")).convert("RGBA")
    full = Image.new("RGBA", (w, h), MAGENTA + (255,))
    full.alpha_composite(im, (0, h - im.height))
    return full.convert("RGB")


def pairs(rows, out, scale=2):
    w, h = rows[0][0].width // scale, rows[0][0].height // scale
    sheet = Image.new("RGB", (2 * w + 8, len(rows) * (h + 8)), "white")
    for i, (a, b) in enumerate(rows):
        sheet.paste(a.resize((w, h)), (0, i * (h + 8)))
        sheet.paste(b.resize((w, h)), (w + 8, i * (h + 8)))
    sheet.save(out)


def main():
    before, after, prefix = sys.argv[1:4]
    w, h = Image.open(os.path.join(after, "cut", "painting.png")).size
    n = sum(1 for f in os.listdir(os.path.join(after, "cut")) if f.endswith("_raw.png"))
    pairs([(layer(before, k, w, h), layer(after, k, w, h)) for k in range(n)], f"{prefix}-layers.png")
    pairs([tuple(Image.open(os.path.join(r, "shots", f"d01-t{t}.png")).convert("RGB") for r in (before, after))
           for t in (0, 6, 12)], f"{prefix}-stills.png")


if __name__ == "__main__":
    main()
