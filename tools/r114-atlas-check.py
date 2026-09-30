#!/usr/bin/env python3
"""r114: what an exported atlas costs a game, and whether its regions still fit its pages.

python3 tools/r114-atlas-check.py a.atlas [b.atlas ...]

For each page: its size, the file's size, the video memory it takes (RGBA8888, +1/3 when mipmapped), whether its sides
are powers of two when mipmapped, and that every region lies inside it. Exits 1 on a region outside its page or a
mipmapped NPOT page.
"""
import os
import sys

from PIL import Image


def pages(path):
	found = []
	page = None
	region = None
	with open(path) as f:
		for line in f:
			line = line.rstrip("\n")
			if not line:
				page = None
				continue
			if page is None:
				page = {"file": line, "regions": []}
				found.append(page)
			elif not line.startswith(" ") and ":" in line and not page["regions"]:
				key, value = line.split(":", 1)
				page[key.strip()] = value.strip()
			elif not line.startswith(" "):
				region = {"name": line}
				page["regions"].append(region)
			else:
				key, value = line.strip().split(":", 1)
				region[key] = [int(v) for v in value.replace(" ", "").split(",")] if key in ("xy", "size") else value.strip()
	return found


def main(paths):
	failed = False
	for path in paths:
		folder = os.path.dirname(path)
		for page in pages(path):
			width, height = (int(v) for v in page["size"].replace(" ", "").split(","))
			mipmapped = page.get("filter", "").startswith("MipMap")
			image = Image.open(os.path.join(folder, page["file"]))
			vram = width * height * 4 * (4 / 3 if mipmapped else 1)
			print(f"{path}: {page['file']} {width}x{height} ({image.size[0]}x{image.size[1]} on disk), "
				f"{os.path.getsize(os.path.join(folder, page['file'])) / 1e6:.2f} MB file, {vram / 2**20:.1f} MiB video memory, "
				f"{len(page['regions'])} regions")
			if image.size != (width, height):
				print("  FAIL: the atlas says another size than the image")
				failed = True
			pot = lambda n: n & (n - 1) == 0
			if mipmapped and not (pot(width) and pot(height)):
				print("  FAIL: mipmapped and not a power of two")
				failed = True
			for region in page["regions"]:
				x, y = region["xy"]
				w, h = region["size"]
				if x < 0 or y < 0 or x + w > width or y + h > height:
					print(f"  FAIL: {region['name']} at {x},{y} {w}x{h} is outside the page")
					failed = True
	print("FAIL" if failed else "PASS")
	return 1 if failed else 0


if __name__ == "__main__":
	sys.exit(main(sys.argv[1:]))
