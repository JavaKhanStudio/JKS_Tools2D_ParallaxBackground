"""join.py STRIPS OUT.png [LAYER] [BEHIND]: a strip's own loop join, seen (r475). The strip's cut tiled twice over the
layer behind it (tiled, bottom aligned) over the sky's colour, cropped round the join and magnified 4x, beside the
same crop of the alpha alone. Below them, for comparison, the strip's steepest inner column step, cropped the same."""
import sys

from PIL import Image

strips, out = sys.argv[1], sys.argv[2]
layer = sys.argv[3] if len(sys.argv) > 3 else 'l3'
behind = sys.argv[4] if len(sys.argv) > 4 else 'l2'
front = Image.open(f'{strips}/{layer}_cut.png').convert('RGBA')
back = Image.open(f'{strips}/{behind}_cut.png').convert('RGBA')
w, h = front.size
back = back.resize((w, round(back.height * w / back.width)))


def scene(x0):
    """The front tiled from x0 (two tiles), over the back, over the sky; the 128 columns round the middle."""
    canvas = Image.new('RGBA', (2 * w, max(h, back.height)), (210, 225, 240, 255))
    for k in range(2):
        canvas.alpha_composite(back, (k * w, canvas.height - back.height))
    tile = Image.new('RGBA', (2 * w, h))
    tile.paste(front.crop((x0, 0, w, h)), (0, 0))
    tile.paste(front, (w - x0, 0))
    tile.paste(front.crop((0, 0, x0, h)), (2 * w - x0, 0))
    canvas.alpha_composite(tile, (0, canvas.height - h))
    alpha = tile.getchannel('A').convert('RGBA')
    m = w
    box = (m - 64, canvas.height - h + 220, m + 64, canvas.height)
    abox = (m - 64, 220, m + 64, h)
    s = 4
    a = canvas.crop(box).resize(((box[2] - box[0]) * s, (box[3] - box[1]) * s), Image.NEAREST)
    b = alpha.crop(abox).resize(a.size, Image.NEAREST)
    row = Image.new('RGBA', (a.width * 2 + 8, a.height), (255, 0, 0, 255))
    row.paste(a, (0, 0))
    row.paste(b, (a.width + 8, 0))
    return row


px = front.load()
best, bx = -1, 0
for x in range(64, w - 64):
    d = sum(abs(px[x, y][3] - px[x + 1, y][3]) for y in range(220, h))
    if d > best:
        best, bx = d, x
top, low = scene(0), scene(bx + 1)
sheet = Image.new('RGBA', (top.width, top.height * 2 + 8), (255, 0, 0, 255))
sheet.paste(top, (0, 0))
sheet.paste(low, (0, top.height + 8))
sheet.save(out)
print(out, 'top: the loop join; bottom: the steepest inner alpha step, at column', bx)
