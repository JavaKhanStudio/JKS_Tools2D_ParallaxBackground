"""r398: every join a page tiles on X, ranked among the art's own neighbour-column steps.

    python3 tools/r398-seq-seams/rank.py PAGE.jplax...

One line a join: lint's step (_join_on_screen), the inner steps' P75 (lint's limit, r253), P90, P95, P99 and max,
the share of inner columns stepping as much as the join or more ('share'), and the steps between the two columns
just inside each side of the join ('beside': left's last pair, right's first pair). A cut steps past nearly every column."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import parallax_lab as lab  # noqa: E402
import parallax_regions as rt  # noqa: E402


def joins(path):
    page = lab.load(path)
    atlas_dir = lab.find_atlas_dir(path, page)
    if atlas_dir is None:
        print(f'{path}: no atlas found', file=sys.stderr)
        return
    regions = {(r['name'], r['pos']): r for r in rt.read_atlas(
        os.path.join(lab.ROOT, atlas_dir, page['pageModel']['atlasName']))}
    if not page.get('repeatOnX', True):
        return
    for i, l in enumerate(lab.layers(page)):
        if l.get('padX', 0) > 0 or not (l.get('kind') == 'SEQUENCE' or lab.has_image(l)):
            continue
        names = ([(s['regionName'], s.get('regionPosition', 0)) for s in l.get('sequenceSegments') or []
                  if s.get('weight', 1) > 0] if l.get('kind') == 'SEQUENCE' else [(l['regionName'], l['regionPosition'])])
        if not names or any(n not in regions for n in names):
            continue
        images = [(f'{n}#{k}', lab._image(page, i, l, regions[(n, k)], rt, {}, [])) for n, k in names]
        p = lab._place(page, i, l, images[0][1])
        for a, left in images:
            for b, right in images:
                steps = sorted(s for img in (left, right) for rows in [lab._rows_on_screen(img, p)] if rows
                               for s in lab._column_steps(rows))
                if not steps:
                    continue
                seam = lab._join_on_screen(left, right, p)
                q = [round(steps[min(len(steps) - 1, int(f * len(steps)))]) for f in (0.75, 0.9, 0.95, 0.99)]
                share = sum(1 for s in steps if s >= seam) / len(steps)
                near = [(lab._column_steps(rows) or [0])[0] for img, x in ((left, left.width - 2), (right, 0))
                        for rows in [lab._rows_on_screen(img.crop((x, 0, x + 2, img.height)), p)] if rows]
                yield i, f'{a}>{b}', seam, q, round(steps[-1]), share, [round(n) for n in near]


for path in sys.argv[1:]:
    for i, join, seam, q, top, share, near in joins(path):
        flag = ' (e)' if seam > max(lab.SEAM, q[0]) else ''
        print(f'{os.path.relpath(path, lab.ROOT)} l{i} {join}: {seam}  P75 {q[0]} P90 {q[1]} P95 {q[2]} P99 {q[3]}'
              f' max {top}  share {share:.3f}  beside {near}{flag}')
