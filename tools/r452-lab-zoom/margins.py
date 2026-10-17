"""How far zoom.py's finer check is from failing (r486): on the pictures ZOOM_DUMP kept, each lab's right zooms and the
mutant's (panel i zoomed showing panel i ^ 1, as mutate.sh's LabKit draws it), as the ratio of the zoom's distance to
the other panel over its distance to its own, where the two panels differ. A right zoom passes above the threshold
(1 for a canvas shown 1:1, PHONE_CLEARLY for one its page reduces: a phone's), a wrong one fails below it; the
threshold goes between the two.
    ZOOM_DUMP=DIR tools/r452-lab-zoom/zoom.sh OUT && python3 tools/r452-lab-zoom/margins.py DIR"""
import glob
import json
import os
import sys

import numpy as np
from zoom_pics import (
    PHONE_CLEARLY,
    PHONE_REACH,
    inset,
    near,
    thumb,
    where_apart,
    zoom_box,
)


def ratio(shown, i, picture, reach):
    """The least, over the panels j that differ from panel i, of picture's distance to j over its distance to i."""
    least = None
    for j in range(len(shown)):
        mask = None if j == i else where_apart(shown[i], shown[j])
        if mask is None:
            continue
        own, other = (float(near(picture, s, reach)[mask].mean()) for s in (shown[i], shown[j]))
        r = other / max(own, 1e-3)
        least = r if least is None else min(least, r)
    return least


dump = sys.argv[1]
worst_right, worst_wrong = {}, {}
for meta in sorted(glob.glob(f"{dump}/*.json")):
    lab = os.path.basename(meta)[:-5]
    with open(meta) as f:
        kept = json.load(f)
    rects, (bw, bh) = kept["rects"], kept["home"]
    every = np.load(f"{dump}/{lab}-every.npy").astype(float)
    count = len(rects)
    fine = (rects[0][2], rects[0][3])
    reduced = every.shape[1] != bw
    reach, clearly = (PHONE_REACH, PHONE_CLEARLY) if reduced else (0, 1)
    shown = [thumb(every, inset((x / bw, (bh - y - ph) / bh, (x + pw) / bw, (bh - y) / bh), *fine), fine) for x, y, pw, ph in rects]
    seen = []
    for i in range(count):
        zoomed = np.load(f"{dump}/{lab}-z{i}.npy").astype(float)
        seen.append(thumb(zoomed, inset(zoom_box(zoomed.shape[1], zoomed.shape[0], *rects[i][2:]), *fine), fine))
    right = [r for r in (ratio(shown, i, seen[i], reach) for i in range(count)) if r is not None]
    wrong = [r for r in (ratio(shown, i, seen[i ^ 1], reach) for i in range(count) if i ^ 1 < count) if r is not None]
    verdict = []
    if right and min(right) < clearly:
        verdict.append("a right zoom FAILS")
    if not any(r < clearly for r in wrong):
        verdict.append("the mutant PASSES")
    print(f"{lab}: right {[round(r, 2) for r in right]}, mutant {[round(r, 2) for r in wrong]}, threshold {clearly}"
          f"{': ' + ', '.join(verdict) if verdict else ''}")
    kind = "reduced" if reduced else "1:1"
    if right:
        worst_right[kind] = min(worst_right.get(kind, 1e9), min(right))
    if wrong:
        worst_wrong[kind] = max(worst_wrong.get(kind, 0), min(wrong))
for kind, least in worst_right.items():
    print(f"{kind}: right zooms at least {least:.2f}, each lab's mutant at most {worst_wrong.get(kind, 0):.2f}")
