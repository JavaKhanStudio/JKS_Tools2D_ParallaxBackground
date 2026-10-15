"""r398: what a SEQUENCE layer's joins step by, against what its art steps between its own columns.

    python3 tools/r398-seq-seams/probe.py PAGE.jplax [LAYER]

For each segment: its own neighbour-column steps (P50, P75, P90) in the rows on screen, as lint's _seam_limit takes
them. For each join left>right: lint's figure (_join_on_screen, columns resized to 200 rows), the same on the raw
rows, and how much of it is alpha (the alpha step alone) against colour (premultiplied rgb alone)."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import parallax_lab as lab  # noqa: E402
import parallax_regions as rt  # noqa: E402


def raw_join(left, right, p):
    """The join on every raw row on screen: (mean step, mean alpha step, mean colour step, rows counted)."""
    low, high = max(0.0, -p.bottom / p.height), min(1.0, (lab.SCREEN_H - p.bottom) / p.height)
    a, b = left.load(), right.load()
    h = min(left.height, right.height)
    xs = left.width - 1
    out = []
    for y in range(round((1 - high) * h), round((1 - low) * h)):
        u, v = a[xs, y], b[0, y]
        if u[3] > 16 or v[3] > 16:
            out.append((rt.step(u, v), abs(u[3] - v[3]), max(abs(u[c] * u[3] - v[c] * v[3]) / 255 for c in range(3))))
    n = len(out) or 1
    return tuple(round(sum(o[k] for o in out) / n, 1) for k in range(3)) + (len(out),)


def main():
    path = sys.argv[1]
    page = lab.load(path)
    regions = {(r['name'], r['pos']): r for r in rt.read_atlas(
        os.path.join(os.path.dirname(path), page['pageModel']['atlasName']))}
    for i, l in enumerate(lab.layers(page)):
        if l.get('kind') != 'SEQUENCE' or (len(sys.argv) > 2 and i != int(sys.argv[2])):
            continue
        sheets, stripped = {}, []
        images = [(s['regionName'], lab._image(page, i, l, regions[(s['regionName'], s.get('regionPosition', 0))],
                                               rt, sheets, stripped)) for s in l['sequenceSegments']]
        p = lab._place(page, i, l, images[0][1])
        print(f'layer {i}: on screen rows {p.bottom:.2f}..{p.bottom + p.height:.2f} of {lab.SCREEN_H}')
        for name, img in images:
            steps = sorted(lab._column_steps(lab._rows_on_screen(img, p)))
            q = [round(steps[int(f * len(steps))], 1) for f in (0.5, 0.75, 0.9)]
            print(f'  {name} {img.size}: inner steps P50 {q[0]} P75 {q[1]} P90 {q[2]}, limit {lab._seam_limit([img], p)}')
        for a, left in images:
            for b, right in images:
                s, al, co, n = raw_join(left, right, p)
                print(f'  {a}>{b}: lint {lab._join_on_screen(left, right, p)}  raw {s} (alpha {al}, colour {co},'
                      f' {n} rows)')


if __name__ == '__main__':
    main()
