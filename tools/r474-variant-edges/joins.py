"""joins.py OUT.png BEFORE AFTER [STRIP...] — r474: each SEQUENCE join of a page's front (l3>l3v1, l3v1>l3...), as the
reader draws it, from two re-lays of the same strips (tools/r400-ai-rules/relay.sh's OUT/<page>): per join a row, the
before then the after, 96 px each side of the join over a grey checker, 3x, a red tick at the join; only the rows
with art near any join, so a ground strip's thin edge is not lost in its sky.

    python3 tools/r474-variant-edges/joins.py build/r474/joins-beach.png build/r474/before/beach-inpaint \
        build/r474/after/beach-inpaint l3 l3v1 l3v2
"""
import sys

import numpy as np
from PIL import Image, ImageDraw

SIDE, ZOOM = 96, 3


def checker(w, h):
    y, x = np.mgrid[:h, :w]
    g = np.where((x // 8 + y // 8) % 2, 150, 110).astype(np.uint8)
    return Image.fromarray(np.dstack([g, g, g, np.full_like(g, 255)]), "RGBA")


def rows_of(pages, pairs):
    """(top, bottom): the rows with art within SIDE px of any join, padded by 8."""
    ys = []
    for page in pages:
        for s in {x for pair in pairs for x in pair}:
            a = np.asarray(Image.open(f"{page}/strips/{s}_cut.png").convert("RGBA"))[..., 3]
            ys += list(np.nonzero((np.r_["1", a[:, :SIDE], a[:, -SIDE:]] > 16).any(1))[0])
    return max(0, min(ys) - 8), max(ys) + 9


def join(page, left, right, rows):
    a, b = (Image.open(f"{page}/strips/{s}_cut.png").convert("RGBA") for s in (left, right))
    both = Image.new("RGBA", (2 * SIDE, a.height))
    both.paste(a.crop((a.width - SIDE, 0, a.width, a.height)), (0, 0))
    both.paste(b.crop((0, 0, SIDE, b.height)), (SIDE, 0))
    both = both.crop((0, rows[0], both.width, min(rows[1], both.height)))
    out = checker(*both.size)
    out.alpha_composite(both)
    return out.resize((both.width * ZOOM, both.height * ZOOM), Image.NEAREST)


def main():
    out, before, after, *strips = sys.argv[1:]
    strips = strips or ["l3", "l3v1"]
    pairs = [(a, b) for a in strips for b in strips if a != b][:6]
    rows = rows_of((before, after), pairs)
    tiles = [[join(p, a, b, rows) for p in (before, after)] for a, b in pairs]
    tw, th = tiles[0][0].size
    sheet = Image.new("RGB", (2 * tw + 30, len(tiles) * (th + 24) + 24), (30, 30, 30))
    d = ImageDraw.Draw(sheet)
    d.text((10, 4), "before (HEAD)", fill=(255, 255, 255))
    d.text((tw + 30, 4), "after (--edges-of)", fill=(255, 255, 255))
    for i, ((a, b), row) in enumerate(zip(pairs, tiles)):
        y = 24 + i * (th + 24)
        for j, t in enumerate(row):
            x = j * (tw + 30) + 10
            sheet.paste(t.convert("RGB"), (x, y + 14))
            d.line((x + tw // 2, y + 8, x + tw // 2, y + 14), fill=(255, 0, 0), width=2)
        d.text((10, y), f"{a} > {b}", fill=(255, 255, 255))
    sheet.save(out)


if __name__ == "__main__":
    main()
