"""page_join.py PAGE LAYER OUT.png: a page's layer tiled on X, its own loop join seen (r475). The layer's image as lint
takes it (its first segment for a SEQUENCE), tiled twice over a grey, cropped to the 64 columns each side of the join
and its rows on screen, magnified 4x; lint's seam, its limit and the step beside each side printed."""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import parallax_lab as lab
import parallax_regions as rt

path, i, out = sys.argv[1], int(sys.argv[2]), sys.argv[3]
page = lab.load(path)
regions = {(r['name'], r['pos']): r for r in rt.read_atlas(
    os.path.join(lab.ROOT, lab.find_atlas_dir(path, page), page['pageModel']['atlasName']))}
l = lab.layers(page)[i]
name = (l['sequenceSegments'][0]['regionName'], l['sequenceSegments'][0].get('regionPosition', 0)) \
    if l.get('kind') == 'SEQUENCE' else (l['regionName'], l['regionPosition'])
img = lab._image(page, i, l, regions[name], rt, {}, [])
p = lab._place(page, i, l, img)
rows = lab._rows_on_screen(img, p) or img
seam = lab._join_on_screen(img, img, p)
print(f'{path} l{i} {name[0]}: seam {seam}, limit {lab._seam_limit([img], p)},'
      f' beside {lab._step_beside(img, img.width - 1 - lab.BESIDE, p):.0f} / {lab._step_beside(img, 0, p):.0f}'
      f' (one column: {lab._step_beside(img, img.width - 2, p) if lab.BESIDE == 1 else "-"}),'
      f' cut {lab._is_cut(seam, lab._seam_limit([img], p), img, img, p)}')
w, h = rows.size
n = min(64, w // 2)
twice = Image.new('RGBA', (2 * n, h), (128, 128, 128, 255))
pair = Image.new('RGBA', (2 * n, h))
pair.paste(rows.crop((w - n, 0, w, h)), (0, 0))
pair.paste(rows.crop((0, 0, n, h)), (n, 0))
twice.alpha_composite(pair)
s = max(1, min(4, 1200 // h))
twice.resize((2 * n * s, h * s), Image.NEAREST).save(out)
print(out, f'the join at x {n * s}')
