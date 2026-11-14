"""sheet.py PROBE OUT: r552's picture from tools/r540-webp-rounds/probe.sh's output PROBE: per scene, libGDX's still of
the PNG round beside jME's of its WebP twin (p01: city_1, 3252 colours; p02: City, a 20-colour palette page)."""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

PROBE, OUT = sys.argv[1], sys.argv[2]
ROWS = [('p01-t6.png', 'p01, city_1 (3252 colours)'), ('p02-t12.png', 'p02, City (20 colours: a palette WebP)')]
W = 640
FONT = '/usr/share/fonts/google-noto/NotoSans-Regular.ttf'
f = ImageFont.truetype(FONT, 22) if os.path.exists(FONT) else ImageFont.load_default(22)
tiles = []
for name, label in ROWS:
    a = Image.open(os.path.join(PROBE, 'gdx', name)).convert('RGB')
    j = Image.open(os.path.join(PROBE, 'jme', name)).convert('RGB')
    h = int(a.height * W / a.width)
    t = Image.new('RGB', (2 * W + 10, h + 40), 'white')
    d = ImageDraw.Draw(t)
    t.paste(a.resize((W, h)), (0, 40))
    t.paste(j.resize((W, h)), (W + 10, 40))
    d.text((8, 6), 'libGDX, PNG atlas: ' + label, fill='black', font=f)
    d.text((W + 18, 6), 'jME, its WebP twin', fill='black', font=f)
    tiles.append(t)
sheet = Image.new('RGB', (tiles[0].width, sum(t.height + 10 for t in tiles)), 'white')
y = 0
for t in tiles:
    sheet.paste(t, (0, y))
    y += t.height + 10
sheet.save(OUT)
