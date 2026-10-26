#!/usr/bin/env python3
"""ring-join.py JOIN.png PLAIN.png — how hard a SHADER band steps where its ring closes (parallax-readers r61).

JOIN.png is a rings lab's <scene>-join-t8.png (engines/jme/tools/rings-lab.sh --shot or three.js's): panel B (the right
half of what is right of the 300 px controls) turned to where the first SHADER layer's band closes its ring, the join
at the panel's centre. PLAIN.png is the same still with the band drawn plain (BREAK=ringeffect), which finds the rows
the effect draws on: those that differ by more than 4/255. Over those rows, each column's mean colour; the jump between
neighbouring columns, worst within 40 px of the centre (the join) against the worst elsewhere in the panel (the effect's
own grain). A join that steps shows a centre jump well over the rest.
"""
import sys

from PIL import Image

CONTROLS = 300


def main(join, plain):
    a, b = (Image.open(p).convert('L') for p in (join, plain))
    x0 = CONTROLS + (a.width - CONTROLS) // 2
    W, H = a.width - x0, a.height
    pa, pb = a.load(), b.load()
    rows = [y for y in range(H) if sum(abs(pa[x0 + x, y] - pb[x0 + x, y]) > 4 for x in range(W)) > W // 4]
    if not rows:
        sys.exit('no rows where the effect draws: is PLAIN the mutant of JOIN?')
    col = [sum(pa[x0 + x, y] for y in rows) / len(rows) for x in range(W)]
    jumps = [abs(col[x + 1] - col[x]) for x in range(W - 1)]
    mid = W // 2
    near = max(range(mid - 40, mid + 40), key=lambda x: jumps[x])
    rest = max(j for x, j in enumerate(jumps) if abs(x - mid) > 40)
    print(f'{join}: effect rows {rows[0]}..{rows[-1]} ({len(rows)}), worst column jump at the join {jumps[near]:.1f} '
          f'(x {near - mid:+d} from centre), worst elsewhere {rest:.1f}')


if __name__ == '__main__':
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
