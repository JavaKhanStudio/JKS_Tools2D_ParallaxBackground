"""Every painted strip of an ai-page run, tiled twice, one under the other, its seam ratio written on it (r356).

    python3 tools/r356-dit/sheet.py OUT/strips sheet.png

Exits 1 when a strip's seam ratio is 2 or more. A cut (_cut.png) is not measured: a near-flat layer's median
step is a fraction of a level, and a join under one level reads as a ratio of 3 (r356's l2_cut: 0.62 / 0.2).
"""
import glob
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "r239-ai-strips"))
import seam


def main(strips, out):
    rows, worst = [], 0.0
    for path in sorted(glob.glob(os.path.join(strips, "l*_ai.png"))):
        name = os.path.basename(path)[:-len(".png")]
        a = seam.load(path)
        ratio = seam.step(a[:, -1], a[:, 0]) / seam.inner(a)
        worst = max(worst, ratio)
        print(f"{name}: seam ratio {ratio:.2f}")
        im = Image.open(path).convert("RGB")
        w, h = im.size
        twice = Image.new("RGB", (2 * w, h))
        twice.paste(im, (0, 0))
        twice.paste(im, (w, 0))
        twice = twice.resize((w, h // 2))
        d = ImageDraw.Draw(twice)
        d.line([(w // 2, h // 2 - 12), (w // 2, h // 2 - 1)], fill=(255, 0, 0), width=3)  # under the join
        d.rectangle([0, 0, 330, 26], fill=(0, 0, 0))
        d.text((6, 6), f"{name} tiled twice, seam ratio {ratio:.2f}", fill=(255, 255, 255))
        rows.append(twice)
    sheet = Image.new("RGB", (rows[0].width, sum(r.height for r in rows)))
    y = 0
    for r in rows:
        sheet.paste(r, (0, y))
        y += r.height
    sheet.save(out)
    print(f"{out}: {len(rows)} strips, worst seam ratio {worst:.2f}")
    return 1 if worst >= 2 else 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:3]))
