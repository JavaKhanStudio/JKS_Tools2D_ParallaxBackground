"""sheet.py OUT.png ROW... — a before/after sheet for r46 (gradient channels truncated as libGDX packs them).

Each ROW is NAME=GDX.png,BEFORE.png,AFTER.png: libGDX | Godot before | Godot after | |before - libGDX| x32 |
|after - libGDX| x32, at a third of the size, each diff panel captioned with its mean / 255."""
import sys

import numpy as np
from PIL import Image, ImageDraw

rows = []
for arg in sys.argv[2:]:
	name, paths = arg.split("=", 1)
	g, b, a = (np.asarray(Image.open(p).convert("RGB")).astype(int) for p in paths.split(","))
	panels = [(g, "libGDX"), (b, "Godot before"), (a, "Godot after")]
	for x, label in ((b, "before"), (a, "after")):
		d = np.abs(x - g)
		panels.append((np.clip(d * 32, 0, 255), f"|{label} - libGDX| x32, mean {d.mean():.2f}"))
	tiles = []
	for img, label in panels:
		t = Image.fromarray(img.astype(np.uint8)).resize((img.shape[1] // 3, img.shape[0] // 3))
		ImageDraw.Draw(t).text((4, 4), f"{name} {label}", fill=(255, 0, 255))
		tiles.append(t)
	row = Image.new("RGB", (sum(t.width for t in tiles), tiles[0].height))
	x = 0
	for t in tiles:
		row.paste(t, (x, 0))
		x += t.width
	rows.append(row)
sheet = Image.new("RGB", (max(r.width for r in rows), sum(r.height for r in rows)))
y = 0
for r in rows:
	sheet.paste(r, (0, y))
	y += r.height
sheet.save(sys.argv[1])
