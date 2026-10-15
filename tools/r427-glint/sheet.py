#!/usr/bin/env python3
"""sheet.py OUT.png — the glint round's still in the three engines, one over the other, labelled (r427).

Reads build/godot/glint/gdx/, build/godot/glint/godot/ and build/jme/glint/jme/ (both frame checks on
engines/rounds/glint must have run) and writes a sheet of g01-t12 (the sea through WAVE, glinting) as libGDX, Godot and
jME draw it, labels in a 28 px font, readable on a phone. Needs Pillow and Open Sans."""
import sys

from PIL import Image, ImageDraw, ImageFont

out = sys.argv[1]
rows = [('libGDX (core)', 'build/godot/glint/gdx/g01-t12.png'), ('Godot', 'build/godot/glint/godot/g01-t12.png'),
        ('jME', 'build/jme/glint/jme/g01-t12.png')]
w, h, band = 960, 540, 44
font = ImageFont.truetype('/usr/share/fonts/open-sans/OpenSans-Semibold.ttf', 28)
sheet = Image.new('RGB', (w, (h + band) * len(rows)), 'white')
draw = ImageDraw.Draw(sheet)
for i, (label, path) in enumerate(rows):
    y = i * (h + band)
    draw.text((12, y + 7), f'{label}: g01 at 12 s, the beach sea through WAVE, glint 0.5', fill='black', font=font)
    sheet.paste(Image.open(path).convert('RGB').resize((w, h), Image.LANCZOS), (0, y + band))
sheet.save(out)
