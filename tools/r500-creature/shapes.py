"""r500: what creature.find sees of each blob r496 refused: the stand-out pixels in red over a 4x crop, and the numbers
a rule could use. Reads OUT/blobs.json (blobs.py's).

    python3 tools/r500-creature/shapes.py BUILD/r496 OUT
"""
import json
import os
import sys

import numpy as np
from blobs import alpha_of
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "r239-ai-strips"))
import creature
from layer import components, spread


def main():
    root, out = sys.argv[1:3]
    with open(f"{out}/blobs.json") as f:
        rows = [r for r in json.load(f) if r["check"] == "find"]
    tiles = []
    for r in rows:
        arm, seed, lay = r["strip"].split("/")
        p = f"{root}/{arm}/{seed}/strips/{lay}"
        rgb = np.asarray(Image.open(p + "_ai.png").convert("RGB")).astype(np.float64)
        sky = np.median(rgb[:8].reshape(-1, 3), axis=0)
        alpha = alpha_of(p, rgb, sky)
        rr = int(12 * rgb.shape[1] / 1024)
        solid = spread(alpha >= 0.5, -4)
        o = creature.standing_out(rgb, solid, solid.astype(np.float64), rr, 40, 3.0)
        o = creature.standing_out(rgb, solid, (solid & ~spread(o, 3)).astype(np.float64), rr, 40, 3.0)
        label, sizes = components(o)
        k = label[r["y"], r["x"]]
        if not k:  # the mean of a crescent lies off it: the nearest blob of that size
            k = 1 + int(np.argmin(np.abs(np.asarray(sizes) - r["size"])))
        ys, xs = np.nonzero(label == k)
        blob = label == k
        h = rgb.shape[0]
        ring = spread(blob, 6) & ~spread(blob, 2) & solid
        col, around = rgb[blob].mean(0), rgb[ring].mean(0) if ring.any() else np.zeros(3)
        fill = blob.sum() / ((np.ptp(ys) + 1) * (np.ptp(xs) + 1))
        print(f"{r['strip']} {r['size']}px box x{xs.min()}-{xs.max()} y{ys.min()}-{ys.max()} ({np.ptp(xs) + 1}x{np.ptp(ys) + 1}) "
              f"fill {fill:.2f} col {col.round().astype(int)} ring {around.round().astype(int)} sky {sky.round().astype(int)} "
              f"d(sky) {np.linalg.norm(col - sky):.0f} foot {h - 1 - ys.max()} clear-near {(~solid & spread(blob, 8)).sum()}")
        cx, cy = int(xs.mean()), int(ys.mean())
        win = 60
        y0 = max(0, min(h - 2 * win, cy - win))
        idx = range(cx - win, cx + win)
        crop = np.take(rgb[y0:y0 + 2 * win], idx, axis=1, mode="wrap").astype(np.uint8)
        mark = np.take(blob[y0:y0 + 2 * win], idx, axis=1, mode="wrap")
        shown = crop.copy()
        shown[mark] = (255, 0, 255)
        pair = np.concatenate([crop, shown], 1)
        tiles.append((f"{r['strip']} {r['size']}px", pair))
    cols, ww, hh = 3, 480, 240
    im = Image.new("RGB", (cols * (ww + 4), (len(tiles) + cols - 1) // cols * (hh + 16)), "white")
    d = ImageDraw.Draw(im)
    for i, (t, c) in enumerate(tiles):
        x, y = (i % cols) * (ww + 4), (i // cols) * (hh + 16)
        d.text((x + 2, y + 2), t, fill="black")
        im.paste(Image.fromarray(c).resize((c.shape[1] * 2, c.shape[0] * 2), Image.NEAREST), (x, y + 16))
    im.save(f"{out}/shapes.png")


if __name__ == "__main__":
    main()
