"""r399: a still with a farther layer's bottom edge marked, to look for it through the nearer layers.

    python3 tools/r399-notch-lint/mark.py STILL.png EDGE_WORLD_Y OUT.png [ZOOM_ROWS]

Red ticks at both sides at the edge's height (world units up from the bottom of a 720 px screen, 32 px a unit). With
ZOOM_ROWS, OUT is instead a 3x crop of that many rows each side of the edge, full width, ticked the same."""
import sys

from PIL import Image, ImageDraw

img = Image.open(sys.argv[1]).convert('RGB')
y = round(img.height - float(sys.argv[2]) * img.height / 22.5)
d = ImageDraw.Draw(img)
for x0 in (0, img.width - 12):
    d.line([(x0, y), (x0 + 11, y)], fill=(255, 0, 0), width=1)
if len(sys.argv) > 4:
    rows = int(sys.argv[4])
    img = img.crop((0, max(0, y - rows), img.width, min(img.height, y + rows)))
    img = img.resize((img.width * 3, img.height * 3), Image.NEAREST)
img.save(sys.argv[3])
