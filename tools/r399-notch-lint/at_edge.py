"""r399: what the nearer spanning layers hold at each layer's bottom edge, the rows a (b) notch fault is about.

    python3 tools/r399-notch-lint/at_edge.py PAGE [LAYER]

For each layer whose art reaches its bottom edge on screen, and each nearer spanning layer over that edge: its clearest
row within NOTCH_MARGIN of the edge, as screen px across the screen's width (1280 x lint's Placed.clear, alpha < 128),
and, in its row at the edge's height plus NOTCH_MARGIN, the share of its columns at alpha 255, at alpha <= 16, and the
lowest alpha there."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import parallax_lab as lab
import parallax_regions as rt

path = sys.argv[1]
page = lab.load(path)
atlas_dir = lab.find_atlas_dir(path, page)
regions = {(r['name'], r['pos']): r for r in rt.read_atlas(os.path.join(lab.ROOT, atlas_dir,
                                                                        page['pageModel']['atlasName']))}
images = {}
for i, l in enumerate(lab.layers(page)):
    if lab.has_image(l) and (l['regionName'], l['regionPosition']) in regions:
        images[i] = lab._image(page, i, l, regions[(l['regionName'], l['regionPosition'])], rt, {}, [])
placed, _ = lab._measure_layers(page, regions, rt)
for k, p in enumerate(placed):
    if len(sys.argv) > 2 and p.index != int(sys.argv[2]) or not (p.rows[0] > 0 and lab._on_screen(p.bottom)):
        continue
    y = p.bottom + lab.NOTCH_MARGIN
    print(f'layer {p.index} ({lab.label(p.layer)}): bottom edge at {p.bottom:.2f}')
    for q in placed[k + 1:]:
        if not q.spans or not q.bottom <= y < q.bottom + q.height:
            continue
        n = len(q.clear)
        lo, hi = ((p.bottom - lab.NOTCH_MARGIN - q.bottom) / q.height * n, (p.bottom + lab.NOTCH_MARGIN - q.bottom) / q.height * n)
        band = q.clear[max(0, int(lo)):max(int(lo) + 1, int(hi) + 1)]
        line = f'  nearer {q.index} ({lab.label(q.layer)}): clear over {lab.SCREEN_PX * max(band):.1f} screen px at most over its rows {int(lo)}-{int(hi)}'
        img = images.get(q.index)
        if img is not None:
            row = min(img.height - 1, int((1 - (y - q.bottom) / q.height) * img.height))
            a = img.getchannel('A').crop((0, row, img.width, row + 1)).get_flattened_data()
            line += (f'; at the edge (row {row} of {img.height}) alpha 255 in {sum(v == 255 for v in a) / len(a):.3f},'
                     f' clear in {sum(v <= 16 for v in a) / len(a):.3f}, lowest {min(a)}')
        print(line)
