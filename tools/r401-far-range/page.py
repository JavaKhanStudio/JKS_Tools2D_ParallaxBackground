#!/usr/bin/env python3
"""r401: the compare page for Simon's question, from variants.py's OUTs once each version is filmed (ai-film.sh into
<OUT>/<v>/film, 8 s at 30 fps).

    python3 tools/r401-far-range/page.py PAGE_OUT CASE_OUT...

Per case: the strip with its far range tinted (variants.py's region.png); the page scrolling in each version; and the
frame at 4 s cropped to where A and C differ (the far range, as drawn), at twice its size. Writes PAGE_OUT/spec.json and
tools/compare_page.py's page.
"""
import json
import os
import subprocess
import sys

import numpy as np
from PIL import Image

VERSIONS = [
    ("own cut", "own", "the page as it was cut when it was painted (R1's own, or the example's)"),
    ("today", "today", "today's layer.py: the far range keyed in patches, blobs cleared, parts kept"),
    ("A keep", "A", "A = the far range kept whole, opaque as painted: part of this layer, it scrolls at this layer's speed"),
    ("B half", "B", "B = the far range half clear, one even alpha: reads as haze, the layers behind show through"),
    ("C key out", "C", "C = the far range keyed out whole: sky, or the layer behind, shows in its place"),
]
FRAME = 120  # 4 s at 30 fps


def zoom(case, out, name):
    frames = {v: np.asarray(Image.open(f"{case}/{v}/film/frames/a01-f{FRAME:04d}.png").convert("RGB"), np.int16)
              for _, v, _ in VERSIONS}
    diff = np.abs(frames["A"] - frames["C"]).max(-1) > 10
    ys = np.nonzero(diff)[0]
    h, w = diff.shape
    if not len(ys):
        y0, y1, x0, x1 = 0, h, 0, w
    else:
        y0, y1 = max(int(ys.min()) - 30, 0), min(int(ys.max()) + 30, h)
        x0, x1 = 0, w
        if (y1 - y0) * 2 < 240:
            y1 = min(y0 + 120, h)
    paths = []
    for _, v, _ in VERSIONS:
        p = os.path.join(out, f"{name}-zoom-{v}.png")
        Image.fromarray(frames[v][y0:y1, x0:x1].astype(np.uint8)).resize(((x1 - x0) * 2, (y1 - y0) * 2),
                                                                          Image.LANCZOS).save(p)
        paths.append(p)
    return paths, (y0, y1)


def main():
    out, cases = sys.argv[1], sys.argv[2:]
    os.makedirs(out, exist_ok=True)
    sections = []
    for case in cases:
        case = case.rstrip("/")
        name = os.path.basename(case)
        sections.append({"title": f"{name}: the far range (tinted magenta on the painted strip)",
                         "about": "What A, B and C act on: the pale far range SD painted round this layer's shape, "
                                  "found by tools/r401-far-range/region.py. A mockup's region, not a key rule.",
                         "items": [{"label": "region", "src": f"{case}/region.png"}]})
        items = [{"label": lb, "about": ab, "src": f"{case}/{v}/film/a01.mp4"} for lb, v, ab in VERSIONS]
        sections.append({"title": f"{name}: the page scrolling (8 s, loops)",
                         "about": "The real reader's frames. Pick a version for each side of the line.",
                         "items": items, "left": 1, "right": 2})
        zooms, (y0, y1) = zoom(case, out, name)
        sections.append({"title": f"{name}: the frame at 4 s, rows {y0}-{y1}, twice their size",
                         "about": "The band where A and C differ: the far range, as the reader draws it.",
                         "items": [dict(it, src=z) for it, z in zip(items, zooms)], "left": 1, "right": 2})
    spec = {"name": "r401", "title": "r401: a pale far range painted round a layer's shape, kept, half clear, or keyed out?",
            "intro": "SD paints pale far ranges round a near layer's shape (between the mesas, behind the pines). "
                     "Today's key cuts them in patches. Which of A, B or C should it do instead? Each box shows two "
                     "versions of the same page: pick them under <b>Left</b> and <b>Right</b>, drag the yellow line, "
                     "or press <b>Flicker</b> (the clearest: the same place, one version then the other). A, B and C are mockups painted onto today's cut, region by region.",
            "sections": sections}
    with open(os.path.join(out, "spec.json"), "w") as f:
        json.dump(spec, f, indent=1)
    subprocess.run([sys.executable, os.path.join(os.path.dirname(__file__), "..", "compare_page.py"),
                    os.path.join(out, "spec.json"), os.path.join(out, "page")], check=True)


if __name__ == "__main__":
    main()
