#!/usr/bin/env python3
"""Every region of a libGDX atlas, one per row, at its original size, measured (r92). Python 3 + Pillow.

  tools/parallax_regions.py ATLAS OUT.png        a sheet of the regions, and their numbers printed
  tools/parallax_regions.py ATLAS --json         the numbers only, as JSON

Look at the art before choosing a layer's speed: region names lie about depth (calm.atlas's Trees_close is its
farthest strip). Each region is drawn on a checkerboard, so its transparent parts show, with what a page needs:
  pos     regionPosition, the 0-based order among the regions of that name in the .atlas file (not its index: field)
  aspect  original width / height: at sizeRatio s the layer is 40*s world units wide and 40*s/aspect high, of a
          16:9 screen 40 x 22.5
  art     the rows (in % of the region's height, 0 = bottom) holding any opaque pixel
  solid   the rows, from the bottom, opaque across the whole width: what can cover what is behind it
  bottom  how much of the bottom row is opaque: 100% = the strip can sit on the screen's bottom edge; less, and
          something behind it (a layer or the bottom gradient) shows through there
  seam    the mean difference (0-255) between the region's left and right edge columns: past ~20 a tiled layer
          shows a cut every repeat; tile it (repeatOnX) only when it is low, or make the layer wider than the scroll
  colour  the mean colour of the top and bottom art rows: what a gradient beside it should meet
"""
import json
import os
import sys

from PIL import Image, ImageDraw


def read_atlas(path):
    """[(page png, region dict)] of an atlas, old (xy/size/orig/offset) or new (bounds/offsets) libGDX format."""
    regions, page, current = [], None, None
    base = os.path.dirname(path)
    with open(path, encoding='utf-8') as f:
        lines = [l.rstrip('\n') for l in f]
    counts = {}
    i = 0
    while i < len(lines):
        line = lines[i]
        i += 1
        if not line.strip():
            page = None
            continue
        if page is None:
            page = os.path.join(base, line.strip())
            while i < len(lines) and ':' in lines[i]:
                i += 1
            continue
        if ':' not in line:
            name = line.strip()
            current = {'name': name, 'pos': counts.get(name, 0), 'page': page}
            counts[name] = current['pos'] + 1
            regions.append(current)
            continue
        key, value = (s.strip() for s in line.split(':', 1))
        nums = [int(v) for v in value.split(',')] if key in ('xy', 'size', 'orig', 'offset', 'bounds', 'offsets') else value
        if key == 'bounds':
            current['xy'], current['size'] = nums[:2], nums[2:]
        elif key == 'offsets':
            current['offset'], current['orig'] = nums[:2], nums[2:]
        else:
            current[key] = nums
    for r in regions:
        r.setdefault('orig', r['size'])
        r.setdefault('offset', [0, 0])
    return regions


def image_of(region, pages):
    """The region at its original size, transparent where the packer stripped it."""
    sheet = pages.setdefault(region['page'], Image.open(region['page']).convert('RGBA'))
    x, y = region['xy']
    w, h = region['size']
    if region.get('rotate') == 'true':
        crop = sheet.crop((x, y, x + h, y + w)).rotate(90, expand=True)
    else:
        crop = sheet.crop((x, y, x + w, y + h))
    ow, oh = region['orig']
    ox, oy = region['offset']
    full = Image.new('RGBA', (ow, oh), (0, 0, 0, 0))
    full.paste(crop, (ox, oh - oy - h))  # libGDX offsets count from the bottom
    return full


def step(a, b):
    """How far two RGBA pixels differ on screen (0-255): their colours premultiplied by their alpha, and their alphas.
    A pixel at alpha 0 keeps whatever colour the cut left (255,0,0 or 0,0,0): straight colours made a faint alpha-20
    haze ending against one a step of 255 (r280)."""
    return max(abs(a[3] - b[3]), *(abs(a[c] * a[3] - b[c] * b[3]) / 255 for c in range(3)))


def measure(img):
    w, h = img.size
    small = img.resize((min(w, 400), min(h, 200)), Image.NEAREST) if w > 400 or h > 200 else img
    sw, sh = small.size
    alpha = small.getchannel('A').load()
    px = small.load()
    rows = [sum(1 for x in range(sw) if alpha[x, y] > 16) / sw for y in range(sh)]  # y = 0 is the top

    def pct(y):
        return round(100 * (sh - y) / sh)

    art = [y for y in range(sh) if rows[y] > 0]
    solid = 0
    for y in range(sh - 1, -1, -1):
        if rows[y] < 0.99:
            break
        solid += 1

    def mean(y):
        cs = [px[x, y][:3] for x in range(sw) if alpha[x, y] > 16]
        return ''.join(f'{sum(c[i] for c in cs) // len(cs):02x}' for i in range(3)) if cs else '------'

    full = img.load()
    edge = []
    for y in range(0, h, max(1, h // 200)):
        a, b = full[0, y], full[w - 1, y]
        if a[3] > 16 or b[3] > 16:
            edge.append(step(a, b))
    seam = round(sum(edge) / len(edge)) if edge else 0
    return {'aspect': round(w / h, 2), 'seam': seam, 'art': [pct(art[-1] + 1), pct(art[0])] if art else None,
            'solid': round(100 * solid / sh), 'bottom': round(100 * rows[-1]),
            'colour': [mean(art[0]), mean(art[-1])] if art else None}


def checker(w, h, cell=16):
    board = Image.new('RGBA', (w, h), (200, 200, 200, 255))
    d = ImageDraw.Draw(board)
    for y in range(0, h, cell):
        for x in range((y // cell) % 2 * cell, w, cell * 2):
            d.rectangle((x, y, x + cell - 1, y + cell - 1), fill=(150, 150, 150, 255))
    return board


def main(argv):
    atlas = argv[1]
    regions = read_atlas(atlas)
    pages, rows = {}, []
    for r in regions:
        img = image_of(r, pages)
        m = measure(img)
        m.update(name=r['name'], pos=r['pos'], orig=r['orig'])
        rows.append((m, img))
    if len(argv) > 2 and argv[2] == '--json':
        print(json.dumps([m for m, _ in rows], indent=1))
        return
    width, label = 1200, 20
    heights = [max(40, round(width * img.height / img.width)) for _, img in rows]
    sheet = Image.new('RGB', (width, sum(h + label for h in heights)), 'black')
    d = ImageDraw.Draw(sheet)
    y = 0
    for (m, img), h in zip(rows, heights):
        text = (f"{m['name']} pos {m['pos']:d}  {m['orig'][0]:d}x{m['orig'][1]:d}  aspect {m['aspect']:.2f}"
                f"  art {m['art']}%  solid {m['solid']:d}%  bottom {m['bottom']:d}%  seam {m['seam']:d}"
                f"  top/bottom colour {m['colour']}")
        print(text)
        d.text((4, y + 4), text, fill='white')
        cell = checker(width, h)
        cell.alpha_composite(img.resize((width, h)))
        sheet.paste(cell.convert('RGB'), (0, y + label))
        y += h + label
    sheet.save(argv[2])


if __name__ == '__main__':
    main(sys.argv)
