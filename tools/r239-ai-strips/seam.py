"""Measure and show where a strip loops (r239).

    python3 tools/r239-ai-strips/seam.py strip.png [more.png ...] [--twice out.png] [--order a.png b.png ...]

seam ratio = mean |colour step| across the loop's join / median |colour step| between neighbour columns
inside the strip. About 1: the join is as smooth as the picture itself. Well above 2: a seam shows.
With alpha, only pixels opaque on both sides count, and the coverage step (alpha) is measured too,
beside its median between neighbour columns inside.
--twice writes the strip drawn twice side by side, with a 1 px tick under the join, to look at.
--order measures every join of a sequence of strips laid end to end (the last into the first).
"""
import sys

import numpy as np
from PIL import Image


def load(path):
    return np.asarray(Image.open(path).convert("RGBA")).astype(np.float32)


def step(a, b):
    """Mean colour step between two columns (H,4), over pixels opaque in both."""
    both = (a[:, 3] > 128) & (b[:, 3] > 128)
    if both.sum() == 0:
        return 0.0
    return float(np.abs(a[both, :3] - b[both, :3]).mean())


def inner(img):
    steps = [step(img[:, x], img[:, x + 1]) for x in range(img.shape[1] - 1)]
    return float(np.median(steps)) or 1e-6


def alpha_step(a, b):
    return float(np.abs(a[:, 3] - b[:, 3]).mean())


def inner_alpha(img):
    return float(np.median([alpha_step(img[:, x], img[:, x + 1]) for x in range(img.shape[1] - 1)]))


def join(left, right):
    """seam ratio and alpha step where `left` ends and `right` begins."""
    ref = (inner(left) + inner(right)) / 2
    return step(left[:, -1], right[:, 0]) / ref, alpha_step(left[:, -1], right[:, 0])


def alpha_ref(left, right):
    return (inner_alpha(left) + inner_alpha(right)) / 2


def twice(imgs, out):
    row = np.concatenate([i for i in imgs] * (2 if len(imgs) == 1 else 1), axis=1)
    h = row.shape[0]
    canvas = np.zeros((h + 6, row.shape[1], 4), np.float32)
    canvas[:h] = row
    canvas[h:, :, :] = (40, 40, 40, 255)
    x = 0
    for i in (imgs * 2 if len(imgs) == 1 else imgs)[:-1]:
        x += i.shape[1]
        canvas[h:, x - 1:x + 1] = (255, 60, 60, 255)  # tick under each join
    Image.fromarray(canvas.clip(0, 255).astype(np.uint8)).save(out)


def main(argv):
    out = None
    if "--twice" in argv:
        k = argv.index("--twice")
        out = argv[k + 1]
        del argv[k:k + 2]
    if "--order" in argv:
        paths = argv[argv.index("--order") + 1:]
        imgs = [load(p) for p in paths]
        for i, p in enumerate(paths):
            r, a = join(imgs[i], imgs[(i + 1) % len(imgs)])
            b = alpha_ref(imgs[i], imgs[(i + 1) % len(imgs)])
            print(f"{p} -> {paths[(i + 1) % len(paths)]}: seam ratio {r:.2f}  alpha step {a:.1f} (inside {b:.1f})")
        if out:
            twice(imgs, out)
        return
    for p in argv:
        img = load(p)
        r, a = join(img, img)
        print(f"{p}: seam ratio {r:.2f}  alpha step {a:.1f} (inside {inner_alpha(img):.1f})  ({img.shape[1]}x{img.shape[0]})")
        if out:
            twice([img], out)


if __name__ == "__main__":
    main(sys.argv[1:])
