#!/usr/bin/env python3
"""best-of.py OUT_JSON RUN_JSON... — the bench runs of one reader folded into one file: each scene's run with the
lowest mean frame time, its load time the lowest too (parallax-readers:r19). The GPU of this machine is shared with
the board's other sessions (Stable Diffusion, frame checks): one run of the same scenes measured 0.08 ms a frame, the
next 2 ms. Another process only ever slows a run, so the best of a few is the reader's own cost. tools/parallax-bench.sh
and engines/<reader>/tools/bench.sh call it; the file says how many runs it kept the best of.
"""
import json
import sys

out, runs = sys.argv[1], []
for path in sys.argv[2:]:
    with open(path) as f:
        runs.append(json.load(f))
best = dict(runs[0], runs=len(runs))
best['scenes'] = []
for i, scene in enumerate(runs[0]['scenes']):
    same = [r['scenes'][i] for r in runs]
    pick = dict(min(same, key=lambda s: s['meanMs']))
    pick['loadMs'] = min(s['loadMs'] for s in same)
    best['scenes'].append(pick)
with open(out, 'w') as f:
    json.dump(best, f, indent=1)
    f.write('\n')
