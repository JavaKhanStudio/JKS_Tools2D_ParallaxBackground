#!/usr/bin/env python3
"""The rings round's check (parallax-readers r47): a 3D reader's ParallaxRings numbers against core's.

    tools/compare-rings.py ROUND_DIR OUT READER

OUT/gdx/<id>.json is core's, written by ./gradlew :rounds:rings -Pround=ROUND_DIR -Pout=OUT; OUT/READER/<id>.json the
reader's, the same shape. Every band of every scene must name the same layer, repeat and order, and every number agree
to 1e-4 relative (1e-5 absolute near 0): a float port and a double one differ in the 7th digit, a wrong formula in the
2nd. A still ring's radius is null in both. Writes OUT/report.txt, read by tools/readers-health (one line per scene,
'FAIL <id>: ...' for a failing one, the verdict last), and exits 1 on a failure. A reader's check takes OUT as a frame
check does (. tools/shots-out.sh; OUT=$(shots_out build/<reader> engines/rounds/rings)), so its verdict lands in
build/<reader>/rings/report.txt.
"""
import json
import os
import sys

REL, ABS = 1e-4, 1e-5


def close(a, b):
    if a is None or b is None:
        return a is None and b is None
    return abs(a - b) <= max(ABS, REL * max(abs(a), abs(b)))


def compare(ref, got):
    """What differs between two scenes' numbers, [] when nothing."""
    bad = []
    for k, v in ref['gradient'].items():
        if not close(v, got.get('gradient', {}).get(k)):
            bad.append(f'gradient {k} {got.get("gradient", {}).get(k)} != {v}')
    rb, gb = ref['bands'], got.get('bands', [])
    if len(rb) != len(gb):
        return bad + [f'{len(gb)} bands != {len(rb)}']
    for i, (r, g) in enumerate(zip(rb, gb)):
        for k, v in r.items():
            ok = g.get(k) == v if k in ('layer', 'repeat', 'order') else close(v, g.get(k))
            if not ok:
                bad.append(f'band {i} (layer {r["layer"]}) {k} {g.get(k)} != {v}')
    return bad


def main(round_dir, out, reader):
    with open(os.path.join(round_dir, 'round.json')) as f:
        scenes = json.load(f)['scenes']
    lines, failed = [], 0
    for s in scenes:
        ref_path, got_path = (os.path.join(out, r, s['id'] + '.json') for r in ('gdx', reader))
        if not os.path.exists(ref_path):
            sys.exit(f'{ref_path}: missing, run ./gradlew :rounds:rings -Pround={round_dir} -Pout={out} first')
        if not os.path.exists(got_path):
            bad = ['MISSING']
        else:
            with open(ref_path) as f, open(got_path) as g:
                bad = compare(json.load(f), json.load(g))
        failed += bool(bad)
        lines.append(f'FAIL {s["id"]}: ' + '; '.join(bad[:5]) if bad else f'{s["id"]}  every number within {REL:g}   {s.get("name", "")}')
    lines.append(f'FAIL: {failed} of {len(scenes)} scenes differ' if failed else f'PASS: {len(scenes)} scenes, every number within {REL:g}')
    with open(os.path.join(out, 'report.txt'), 'w') as f:
        f.write('\n'.join(lines) + '\n')
    print('\n'.join(lines))
    return 1 if failed else 0


if __name__ == '__main__':
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    sys.exit(main(*sys.argv[1:]))
