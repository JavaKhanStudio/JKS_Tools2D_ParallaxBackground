"""Find a creature SD painted inside a layer's mass: a blob far in colour from what is around it, and alone (r261).

    python3 tools/r239-ai-strips/creature.py cut.png [--src painted.png] [--min 50 --max 3000] [--show out.png]

run.sh's negative prompt names birds and animals, which lowers the odds and proves nothing: a bird that stands still in
a scrolling background is what this catches. Inside the layer's mass (alpha >= 0.5, 4 px in from its edge), each pixel
is compared with the mass's colour around it (a box mean, R px each way, taken again without what stood out the first
time) and with how much the mass varies there (that difference's mean, 2R px each way): a pixel stands out when it is
both --far from the mean and --times the variation. A blob of them --min to --max px big is a creature unless:
  - it touches the mass's edge: sky or far haze at the foot of a gap between trees, which the silhouette mask made
    opaque (a creature standing on the silhouette is missed too);
  - another blob has its colour (within 30), its shape (height/width within 3x) and its size (within 3x): texture
    repeats, snow on pines and trees on a slope stand out in crowds. A wide bird among upright trees of its colour is
    still alone; a creature the colour, shape and size of the layer's own flecks is missed.
Exits 4 and names each one (x, y, size). layer.py and pixel.py call find() on the painted strip and exit 4 too, so
ai-page.sh paints the strip again on the next seed. tools/r261-creature/sweep.sh holds it to every strip on disk and to
planted creatures, mutate.sh proves each rule above is needed.
--src: the painted strip the cut came from: what layer.py and pixel.py look at (pixel.py's cut is one pixel per art
cell, too small to see a bird in). The cut's alpha is scaled to it; sizes scale with its width (--min, --max and R are
for 1024 px), and in pixel art a creature is six art pixels at least, R three.
"""
import argparse
import sys

import numpy as np
from PIL import Image

from layer import components, spread


def box(a, r):
    """Sum over the (2r+1)^2 box, wrapping along X (the strip loops), clipped at the top and bottom."""
    h, w = a.shape[:2]
    p = np.concatenate([a[:, -r:], a, a[:, :r]], axis=1)
    c = np.pad(p, [(1, 0), (1, 0)] + [(0, 0)] * (a.ndim - 2)).cumsum(0).cumsum(1)
    y0, y1 = np.clip(np.arange(h) - r, 0, h), np.clip(np.arange(h) + r + 1, 0, h)
    x0 = np.arange(w)
    x1 = x0 + 2 * r + 1
    return c[y1][:, x1] - c[y0][:, x1] - c[y1][:, x0] + c[y0][:, x0]


def standing_out(rgb, solid, m, r, far, times):
    """Where rgb is --far from the box mean of the m pixels and --times the local variation of that difference."""
    mean = box(rgb * m[..., None], r) / np.maximum(box(m, r), 1)[..., None]
    d = np.linalg.norm(rgb - mean, axis=2) * solid
    vary = box(d * m, 2 * r) / np.maximum(box(m, 2 * r), 1)
    return solid & (d > far) & (d > times * vary)


def find(rgb, alpha, lo=50, hi=3000, r=12, far=40, times=3.0, like=30, crowd=0):
    """[(x, y, size)] of each creature-like blob in rgb (h, w, 3 floats) inside alpha's (h, w, 0..1) mass."""
    # Half keyed is mass too: a creature painted nearer the sky's colour than the land's keys part clear. Sky seen
    # through branches keys fully clear, and is no creature.
    solid = spread(alpha >= 0.5, -4)
    out = standing_out(rgb, solid, solid.astype(np.float64), r, far, times)
    # Again, the mean without what stood out: a blob as big as a fifth of the box pulls the mean toward itself.
    out = standing_out(rgb, solid, (solid & ~spread(out, 3)).astype(np.float64), r, far, times)
    label, sizes = components(out)
    # Sky or far haze at the foot of a gap between trees, which the silhouette mask made opaque, opens onto the
    # mass's edge; a creature inside the mass does not.
    edge = set(np.unique(label[spread(~solid, 2)]))
    blobs = []
    for k, size in enumerate(sizes):
        if size < 8:
            continue
        ys, xs = np.nonzero(label == k + 1)
        # Wrapping along X: a blob across the join spans the whole width; unroll it first.
        if xs.max() - xs.min() > rgb.shape[1] // 2:
            xs = np.where(xs < rgb.shape[1] // 2, xs + rgb.shape[1], xs)
        tall = (ys.max() - ys.min() + 1) / (xs.max() - xs.min() + 1)
        blobs.append((k + 1, size, rgb[ys, xs % rgb.shape[1]].mean(axis=0), tall, xs, ys))
    found = []
    for k, size, colour, tall, xs, ys in blobs:
        if k in edge or not lo <= size <= hi:
            continue
        # Texture repeats: snow on pines, trees on a slope stand out in crowds of one colour, shape and size. A creature
        # is alone: a wide bird among upright trees of its colour too.
        like_it = sum(np.linalg.norm(c - colour) < like and 1 / 3 < t / tall < 3 and 1 / 3 < n / size < 3
                      for _, n, c, t, *_ in blobs) - 1
        if like_it <= crowd:
            found.append((int(xs.mean()) % rgb.shape[1], int(ys.mean()), int(size)))
    return found


def show(src, alpha, found):
    """src with what find() does not look at dimmed, and a red box around each blob it found."""
    from PIL import ImageDraw
    rgb = np.asarray(src).astype(np.float64)
    seen = spread(alpha >= 0.5, -4)[..., None]
    im = Image.fromarray(np.where(seen, rgb, rgb * 0.35).astype(np.uint8))
    draw = ImageDraw.Draw(im)
    for x, y, size in found:
        e = max(8, int(size ** 0.5))
        draw.rectangle([x - e, y - e, x + e, y + e], outline=(255, 0, 0), width=2)
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cut", help="a cut layer (RGBA)")
    ap.add_argument("--src", help="the painted strip it came from: look there, through the cut's alpha")
    ap.add_argument("--min", type=int, default=50, help="px, in a 1024 px wide strip; never under six art pixels")
    ap.add_argument("--max", type=int, default=3000, help="px, in a 1024 px wide strip")
    ap.add_argument("--show", help="write the strip here, each blob in a red box, the mass outside the check dimmed")
    a = ap.parse_args()
    cut = Image.open(a.cut).convert("RGBA")
    src = Image.open(a.src).convert("RGB") if a.src else cut.convert("RGB")
    alpha = np.asarray(cut.getchannel("A").resize(src.size, Image.NEAREST)).astype(np.float64) / 255
    k = (src.width / 1024) ** 2  # --min and --max are for a 1024 px strip
    cell = src.width / cut.width  # pixel art: an art pixel is a cell this wide in the painted strip
    rgb = np.asarray(src).astype(np.float64)
    # The box: 12 px in a 1024 px strip, and three art pixels at least (pixel art's creature is wider than 12 px).
    r = max(int(12 * k ** 0.5), int(3 * cell)) if cell > 1.5 else int(12 * k ** 0.5)
    found = find(rgb, alpha, max(int(a.min * k), int(6 * cell ** 2)), int(a.max * k), r=r)
    for x, y, size in found:
        print(f"{a.src or a.cut}: a creature? {size} px blob at x={x} y={y}", file=sys.stderr)
    if a.show:
        show(src, alpha, found).save(a.show)
    sys.exit(4 if found else 0)


if __name__ == "__main__":
    main()
