"""Every painted strip of an ai-page run, tiled twice, one under the other, its seam ratio written on it (r356).

    python3 tools/r356-dit/sheet.py OUT/strips sheet.png

Exits 1 when a strip's seam ratio is 2 or more and its join's colour step is a level or more. On a near-flat strip
the median step is a fraction of a level, and a join under the one level a PNG can show reads as a ratio over 2
(r357's hills: 0.58 / 0.26 = 2.25, nothing to see). A cut (_cut.png) is not measured: its flat fill does that too.
"""
import glob
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "r239-ai-strips"))
import seam


def main(strips, out):
    rows, worst, bad = [], 0.0, 0
    for path in sorted(glob.glob(os.path.join(strips, "l*_ai.png"))):
        name = os.path.basename(path)[:-len(".png")]
        a = seam.load(path)
        join = seam.step(a[:, -1], a[:, 0])
        ratio = join / seam.inner(a)
        worst = max(worst, ratio)
        shows = ratio >= 2 and join >= 1
        bad += shows
        print(f"{name}: seam ratio {ratio:.2f}, join step {join:.2f} levels" + ("  SHOWS" if shows else ""))
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
    print(f"{out}: {len(rows)} strips, worst seam ratio {worst:.2f}, {bad} seams that show")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(*sys.argv[1:3]))
