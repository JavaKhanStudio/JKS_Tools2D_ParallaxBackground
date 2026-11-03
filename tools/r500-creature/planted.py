"""r500: does layer.py still refuse a real creature? Plants birds, deer and figures on r496's own ruins strips, cuts each
with two layer.py and counts who names the plant (a blob within 40 px of it). No GPU. sweep.sh's own plants need
paintings since pruned from build/, so it no longer holds this.

    python3 tools/r500-creature/planted.py BUILD/r496 OUT BEFORE_LAYERPY AFTER_LAYERPY

    mass     in the wall the mask holds, bird, deer, a pale gull
    feet     a deer whose feet are on the first dark rubble under the wall, in a third of the wall's colour and in
             the rubble's own: the rule's price, one that should stay refused and one it says it misses
    stand    a person and a figure on the outline, plant.py's --stand
"""
import glob
import multiprocessing
import os
import re
import subprocess
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
PLANT = os.path.join(HERE, "..", "r261-creature", "plant.py")
SIZE = "1.5"  # 1536 px strips: plant.py's sizes are for 1024


def places(p, rng):
    """[(case, x, y, plant.py args)] on strip prefix p."""
    rgb = np.asarray(Image.open(p + "_ai.png").convert("RGB")).astype(float)
    mask = np.asarray(Image.open(p + "_mask.png").convert("L")) > 127
    h, w = mask.shape
    out = []
    # In the mass as painted, the mask is wider: 90 px under the column's top all far from the sky and for 40 px each
    # side, 45 px down, away from the foot.
    sky = np.median(rgb[:8].reshape(-1, 3), axis=0)
    painted = mask & (np.linalg.norm(rgb - sky, axis=2) > 48)
    tops = np.argmax(painted, axis=0)
    xs = [x for x in range(40, w - 40, 7) if painted[:, x].any() and tops[x] + 90 < h - 40
          and painted[tops[x]:tops[x] + 90, x - 40:x + 41].all()]
    for case, kind, colour in (("mass bird", "bird", None), ("mass deer", "deer", None), ("mass gull", "bird", "c8c8c8")):
        if xs:
            x = int(rng.choice(xs))
            out.append((case, x, int(tops[x]) + 45, ["--kind", kind, "--size", SIZE] + (["--colour", colour] if colour else [])))
    # Feet on the rubble: the first row under the mask's top much darker than the 30 rows over it.
    lum = rgb.mean(axis=2)
    feet = []
    for x in range(40, w - 40, 7):
        if not mask[:, x].any():
            continue
        col = lum[:, x]
        for y in range(tops[x] + 60, h - 12):
            if col[y] < 0.55 * col[y - 30:y - 6].mean():
                feet.append((x, y))
                break
    if feet:
        x, y = feet[int(rng.integers(len(feet)))]
        wall, rubble = rgb[y - 40, x], rgb[y + 6, x]
        for case, colour in (("feet deer dark", wall / 3), ("feet deer rubble's colour", rubble)):
            hex_ = "".join(f"{int(c):02x}" for c in colour)
            out.append((case, x, y - 22, ["--kind", "deer", "--size", SIZE, "--colour", hex_]))
    for kind in ("person", "figure"):
        x = int(rng.integers(40, w - 40))
        out.append((f"stand {kind}", x, None, ["--kind", kind, "--size", SIZE, "--colour", "2a2420"]))
    return out


def named(py, png, p, dest):
    """[(x, y)] of each blob layer.py names on png."""
    r = subprocess.run([sys.executable, py, png, dest, "--mask", p + "_mask.png", "--grow", "16", "--kind", "ruins", "--ckpt",
                        "DreamShaperXL_Turbo_v2_1.safetensors"], capture_output=True, text=True, check=False)
    return [(int(a), int(b)) for a, b in re.findall(r"a creature\? \d+ px blob at x=(\d+) y=(\d+)", r.stderr)]


def one(job):
    out, p, i, case, x, y, args, before, after = job
    png = os.path.join(out, f"plant-{i}.png")
    at = f"{x},{y if y is not None else 0}"
    subprocess.run([sys.executable, PLANT, p + "_ai.png", png, "--at", at, *args], check=True, capture_output=True)
    dest = os.path.join(out, f"cut-{i}.png")
    res, shade = [], None
    rgb = np.asarray(Image.open(png).convert("RGB")).astype(float)
    sky = np.median(rgb[:8].reshape(-1, 3), axis=0)
    for py in (before, after):
        near = [(a, b) for a, b in named(py, png, p, dest) if abs(a - x) < 40 and (y is None or abs(b - y) < 40)]
        res.append(bool(near))
        if near and shade is None:  # how far from the sky what it named is: under 90, a sky pocket, no plant
            shade = round(float(np.linalg.norm(rgb[near[0][1], near[0][0]] - sky)))
    for f in (png, dest):
        if os.path.exists(f):
            os.remove(f)
    return case, os.path.relpath(p), x, y, res[0], res[1], shade


def main():
    root, out, before, after = sys.argv[1:5]
    os.makedirs(out, exist_ok=True)
    rng = np.random.default_rng(500)
    jobs = []
    for p in sorted(glob.glob(f"{root}/plain/s*/strips/l[123]_ai.png")):
        p = p[:-len("_ai.png")]
        for case, x, y, args in places(p, rng):
            jobs.append((out, p, len(jobs), case, x, y, args, before, after))
    with multiprocessing.Pool(8) as pool:
        rows = pool.map(one, jobs)
    print("case                        planted  named before  named after")
    for case in sorted({r[0] for r in rows}):
        rs = [r for r in rows if r[0] == case]
        print(f"{case:27} {len(rs):7} {sum(r[4] for r in rs):13} {sum(r[5] for r in rs):12}")
    lost = [r for r in rows if r[4] and not r[5]]
    print(f"named before, not after: {len(lost)}")
    for r in lost:
        print("  ", *r[:4], f"named pixel {r[6]} from the sky")


if __name__ == "__main__":
    main()
