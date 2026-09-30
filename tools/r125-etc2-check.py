#!/usr/bin/env python3
"""r125 (d13): an ETC2 atlas the editor wrote, decoded and compared with its PNG atlas.

python3 tools/r125-etc2-check.py path/name.atlas [...]

For each page of name.etc2.atlas: the .zktx is a gzipped KTX (libGDX's .zktx: a big-endian length, then the file),
GL_COMPRESSED_RGBA8_ETC2_EAC, with a mip chain when the atlas is mipmapped. Level 0 is decoded here from the ETC2 and
EAC specs (every colour mode: individual, differential, T, H, planar) and compared with the PNG page inside each region:
PSNR of the colour over pixels at least half opaque, PSNR of the alpha, and its largest error. Also checks that both atlases list
the same regions at the same places, and the video memory each page costs. Exits 1 when a check fails: regions
differ, a level is missing, colour PSNR under 30 dB, or alpha PSNR under 40 dB.
"""
import gzip
import math
import os
import struct
import sys

from PIL import Image

ETC_TABLES = [(2, 8), (5, 17), (9, 29), (13, 42), (18, 60), (24, 80), (33, 106), (47, 183)]
T_DISTANCES = [3, 6, 11, 16, 23, 32, 41, 64]
EAC_TABLES = [
	(-3, -6, -9, -15, 2, 5, 8, 14), (-3, -7, -10, -13, 2, 6, 9, 12), (-2, -5, -8, -13, 1, 4, 7, 12),
	(-2, -4, -6, -13, 1, 3, 5, 12), (-3, -6, -8, -12, 2, 5, 7, 11), (-3, -7, -9, -11, 2, 6, 8, 10),
	(-4, -7, -8, -11, 3, 6, 7, 10), (-3, -5, -8, -11, 2, 4, 7, 10), (-2, -6, -8, -10, 1, 5, 7, 9),
	(-2, -5, -8, -10, 1, 4, 7, 9), (-2, -4, -8, -10, 1, 3, 7, 9), (-2, -5, -7, -10, 1, 4, 6, 9),
	(-3, -4, -7, -10, 2, 3, 6, 9), (-1, -2, -3, -10, 0, 1, 2, 9), (-4, -6, -8, -9, 3, 5, 7, 8),
	(-3, -5, -7, -9, 2, 4, 6, 8)]


def clamp(v):
	return 0 if v < 0 else 255 if v > 255 else v


def ext4(v):
	return v << 4 | v


def ext5(v):
	return v << 3 | v >> 2


def signed3(v):
	return v - 8 if v >= 4 else v


def selectors(block):
	"""Pixel i (column i // 4, row i % 4): its 2-bit selector, MSB from bits 31..16 and LSB from 15..0."""
	return [(block >> (16 + i) & 1) << 1 | (block >> i & 1) for i in range(16)]


def decode_color(block):
	"""16 (r, g, b), pixel i = column i // 4, row i % 4."""
	diff = block >> 33 & 1
	flip = block >> 32 & 1
	if diff:
		r, dr = block >> 59 & 31, signed3(block >> 56 & 7)
		g, dg = block >> 51 & 31, signed3(block >> 48 & 7)
		b, db = block >> 43 & 31, signed3(block >> 40 & 7)
		if not 0 <= r + dr <= 31:
			return decode_t(block)
		if not 0 <= g + dg <= 31:
			return decode_h(block)
		if not 0 <= b + db <= 31:
			return decode_planar(block)
		bases = [(ext5(r), ext5(g), ext5(b)), (ext5(r + dr), ext5(g + dg), ext5(b + db))]
	else:
		bases = [(ext4(block >> 60 & 15), ext4(block >> 52 & 15), ext4(block >> 44 & 15)),
		         (ext4(block >> 56 & 15), ext4(block >> 48 & 15), ext4(block >> 40 & 15))]
	tables = [block >> 37 & 7, block >> 34 & 7]
	out = []
	for i, s in enumerate(selectors(block)):
		sub = (i // 4 >= 2) if flip == 0 else (i % 4 >= 2)
		a, b2 = ETC_TABLES[tables[sub]]
		m = (a, b2, -a, -b2)[s]
		out.append(tuple(clamp(c + m) for c in bases[sub]))
	return out


def decode_t(block):
	r1 = (block >> 59 & 3) << 2 | block >> 56 & 3
	c1 = (ext4(r1), ext4(block >> 52 & 15), ext4(block >> 48 & 15))
	c2 = (ext4(block >> 44 & 15), ext4(block >> 40 & 15), ext4(block >> 36 & 15))
	d = T_DISTANCES[(block >> 34 & 3) << 1 | block >> 32 & 1]
	paints = [c1, tuple(clamp(c + d) for c in c2), c2, tuple(clamp(c - d) for c in c2)]
	return [paints[s] for s in selectors(block)]


def decode_h(block):
	r1 = block >> 59 & 15
	g1 = (block >> 56 & 7) << 1 | block >> 52 & 1
	b1 = (block >> 51 & 1) << 3 | block >> 47 & 7
	r2, g2, b2 = block >> 43 & 15, block >> 39 & 15, block >> 35 & 15
	c1, c2 = (ext4(r1), ext4(g1), ext4(b1)), (ext4(r2), ext4(g2), ext4(b2))
	index = (block >> 34 & 1) << 2 | (block >> 32 & 1) << 1
	index |= 1 if (c1[0] << 16 | c1[1] << 8 | c1[2]) >= (c2[0] << 16 | c2[1] << 8 | c2[2]) else 0
	d = T_DISTANCES[index]
	paints = [tuple(clamp(c + d) for c in c1), tuple(clamp(c - d) for c in c1),
	          tuple(clamp(c + d) for c in c2), tuple(clamp(c - d) for c in c2)]
	return [paints[s] for s in selectors(block)]


def decode_planar(block):
	ro = block >> 57 & 63
	go = (block >> 56 & 1) << 6 | block >> 49 & 63
	bo = (block >> 48 & 1) << 5 | (block >> 43 & 3) << 3 | block >> 39 & 7
	rh = (block >> 34 & 31) << 1 | block >> 32 & 1
	gh, bh = block >> 25 & 127, block >> 19 & 63
	rv, gv, bv = block >> 13 & 63, block >> 6 & 127, block & 63
	e6 = lambda v: v << 2 | v >> 4
	e7 = lambda v: v << 1 | v >> 6
	o, h, v = (e6(ro), e7(go), e6(bo)), (e6(rh), e7(gh), e6(bh)), (e6(rv), e7(gv), e6(bv))
	out = []
	for i in range(16):
		x, y = i // 4, i % 4
		out.append(tuple(clamp((x * (h[c] - o[c]) + y * (v[c] - o[c]) + 4 * o[c] + 2) >> 2) for c in range(3)))
	return out


def decode_alpha(block):
	base, mult, table = block >> 56, block >> 52 & 15, block >> 48 & 15
	return [clamp(base + EAC_TABLES[table][block >> (45 - 3 * i) & 7] * mult) for i in range(16)]


def read_zktx(path):
	data = gzip.open(path).read()
	length = struct.unpack('>I', data[:4])[0]
	ktx = data[4:4 + length]
	assert ktx[:12] == b'\xabKTX 11\xbb\r\n\x1a\n', 'not a KTX'
	fields = struct.unpack('<13I', ktx[12:64])
	internal, width, height, levels, kv = fields[4], fields[6], fields[7], fields[11], fields[12]
	pos = 64 + kv
	images = []
	for level in range(levels):
		size = struct.unpack('<I', ktx[pos:pos + 4])[0]
		images.append(ktx[pos + 4:pos + 4 + size])
		pos += 4 + ((size + 3) & ~3)
	return internal, width, height, images


def decode_level(data, width, height):
	blocks_x = (width + 3) // 4
	pixels = Image.new('RGBA', (blocks_x * 4, ((height + 3) // 4) * 4))
	put = pixels.load()
	for n in range(len(data) // 16):
		alpha = int.from_bytes(data[n * 16:n * 16 + 8], 'big')
		color = int.from_bytes(data[n * 16 + 8:n * 16 + 16], 'big')
		bx, by = n % blocks_x, n // blocks_x
		for i, (rgb, a) in enumerate(zip(decode_color(color), decode_alpha(alpha))):
			put[bx * 4 + i // 4, by * 4 + i % 4] = rgb + (a,)
	return pixels.crop((0, 0, width, height))


def atlas_pages(path):
	pages, page = [], None
	for line in open(path):
		line = line.rstrip('\n')
		if not line:
			page = None
		elif page is None:
			page = {'file': line, 'header': {}, 'regions': []}
			pages.append(page)
		elif not line.startswith(' ') and ':' in line and not page['regions']:
			key, value = line.split(':', 1)
			page['header'][key.strip()] = value.strip()
		elif not line.startswith(' '):
			page['regions'].append({'name': line})
		else:
			key, value = line.strip().split(':', 1)
			page['regions'][-1][key] = value.strip()
	return pages


def check(atlas):
	failed = False
	etc2_atlas = atlas[:-len('.atlas')] + '.etc2.atlas'
	folder = os.path.dirname(atlas)
	pngs, etcs = atlas_pages(atlas), atlas_pages(etc2_atlas)
	strip = lambda pages: [(p['header'], [r for r in p['regions']]) for p in pages]
	if strip(pngs) != strip(etcs):
		print('FAIL %s: its pages or regions differ from %s' % (etc2_atlas, atlas))
		return True
	for png_page, etc_page in zip(pngs, etcs):
		internal, width, height, levels = read_zktx(os.path.join(folder, etc_page['file']))
		mipmapped = png_page['header']['filter'].startswith('MipMap')
		want_levels = int(math.log2(max(width, height))) + 1 if mipmapped else 1
		png = Image.open(os.path.join(folder, png_page['file'])).convert('RGBA')
		etc_bytes = sum(len(level) for level in levels)
		png_bytes = width * height * 4 * (4 / 3 if mipmapped else 1)
		print('%s: %dx%d, %d levels, %.1f MiB video memory (the PNG page: %.1f MiB)'
		      % (etc_page['file'], width, height, len(levels), etc_bytes / 2 ** 20, png_bytes / 2 ** 20))
		if internal != 0x9278 or (width, height) != png.size or len(levels) != want_levels:
			print('  FAIL: format 0x%x, %dx%d, %d levels; wanted 0x9278, %dx%d, %d'
			      % (internal, width, height, len(levels), png.size[0], png.size[1], want_levels))
			failed = True
			continue
		decoded = decode_level(levels[0], width, height)
		for region in png_page['regions']:
			x, y = (int(v) for v in region['xy'].split(','))
			w, h = (int(v) for v in region['size'].split(','))
			a = png.crop((x, y, x + w, y + h)).tobytes()
			b = decoded.crop((x, y, x + w, y + h)).tobytes()
			squared, count, worst_alpha, alpha_squared = 0, 0, 0, 0
			for i in range(0, len(a), 4):
				worst_alpha = max(worst_alpha, abs(a[i + 3] - b[i + 3]))
				alpha_squared += (a[i + 3] - b[i + 3]) ** 2
				if a[i + 3] >= 128:
					squared += (a[i] - b[i]) ** 2 + (a[i + 1] - b[i + 1]) ** 2 + (a[i + 2] - b[i + 2]) ** 2
					count += 3
			psnr = 99.0 if squared == 0 else 10 * math.log10(255 ** 2 * count / squared) if count else 99.0
			alpha_psnr = 99.0 if alpha_squared == 0 else 10 * math.log10(255 ** 2 * (len(a) // 4) / alpha_squared)
			verdict = 'ok' if psnr >= 30 and alpha_psnr >= 40 else 'FAIL'
			failed |= verdict == 'FAIL'
			print('  %-24s %4dx%-4d colour PSNR %5.1f dB, alpha PSNR %5.1f dB (off by %3d at most)  %s'
			      % (region['name'] + '#' + region.get('index', ''), w, h, psnr, alpha_psnr, worst_alpha, verdict))
	return failed


if __name__ == '__main__':
	failed = False
	for path in sys.argv[1:]:
		failed |= check(path)
	print('FAIL' if failed else 'PASS')
	sys.exit(1 if failed else 0)
