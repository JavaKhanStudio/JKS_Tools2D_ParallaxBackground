"""r505: where a planted bird proves the crowd rule's bounds or the half-keyed mass: tries a bird in each blob colour of a
fixture at places in its mass, against creature.find as it is and with one rule mutated, as mutate.sh does. Prints each
plant the real rule names and the mutant does not: a sweep case.

    python3 tools/r505-sweep-fixtures/search.py FIXTURES NAME... (crowd.py says which strips have the blobs)
"""
import multiprocessing
import os
import subprocess
import sys
import tempfile

import numpy as np
from mutant import components, load, read_alpha, spread
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))

MUTANTS = {"colour only": ("and 1 / 3 < t / tall < 3 and 1 / 3 < n / size < 3", ""),
           "without size": (" and 1 / 3 < n / size < 3", ""),
           "solid only": ("solid = spread(alpha >= 0.5, -4)", "solid = spread(alpha >= 0.95, -4)")}


REAL = load("real")
MUT = {n: load(n.replace(" ", "_"), *m) for n, m in MUTANTS.items()}


def blobs(rgb, solid, r):
    out = REAL.standing_out(rgb, solid, solid.astype(np.float64), r, 40, 3.0)
    out = REAL.standing_out(rgb, solid, (solid & ~spread(out, 3)).astype(np.float64), r, 40, 3.0)
    label, sizes = components(out)
    cols = []
    for i, size in enumerate(sizes):
        if size >= 20:
            cols.append(rgb[label == i + 1].mean(axis=0))
    return cols


def one(job):
    d, name, x, y, col = job
    with tempfile.TemporaryDirectory() as t:
        png = f"{t}/p.png"
        subprocess.run([sys.executable, os.path.join(HERE, "..", "r261-creature", "plant.py"), f"{d}/{name}_ai.png", png, "--at",
                        f"{x},{y}", "--kind", "bird", "--colour", col], check=True, capture_output=True)
        rgb = np.asarray(Image.open(png).convert("RGB")).astype(np.float64)
    cut = Image.open(f"{d}/{name}_cut.png").convert("RGBA")
    alpha = read_alpha(cut.getchannel("A").resize((rgb.shape[1], rgb.shape[0]), Image.NEAREST))
    k = (rgb.shape[1] / 1024) ** 2
    r = int(12 * k ** 0.5)

    def near(m):
        return [f for f in m.find(rgb, alpha, int(50 * k), int(3000 * k), r=r) if abs(f[0] - x) < 40 and abs(f[1] - y) < 40]

    if not near(REAL):
        return None
    return name, x, y, col, [n for n, m in MUT.items() if not near(m)]


def main():
    d = sys.argv[1]
    rng = np.random.default_rng(505)
    jobs = []
    for name in sys.argv[2:]:
        rgb = np.asarray(Image.open(f"{d}/{name}_ai.png").convert("RGB")).astype(np.float64)
        cut = Image.open(f"{d}/{name}_cut.png").convert("RGBA")
        alpha = read_alpha(cut.getchannel("A").resize((rgb.shape[1], rgb.shape[0]), Image.NEAREST))
        solid = spread(alpha >= 0.5, -4)
        r = int(12 * rgb.shape[1] / 1024)
        cols = blobs(rgb, solid, r)
        roomy = spread(solid, -16)
        half = spread(solid & (alpha < 0.95), -8)
        ys, xs = np.nonzero(roomy)
        hy, hx = np.nonzero(half)
        for c in cols[:: max(len(cols) // 8, 1)][:8]:
            hexc = "".join(f"{int(v):02x}" for v in c)
            for _ in range(3):
                i = rng.integers(len(xs))
                jobs.append((d, name, int(xs[i]), int(ys[i]), hexc))
        for _ in range(8 if len(hx) else 0):  # half-keyed: a third of the colour there
            i = rng.integers(len(hx))
            jobs.append((d, name, int(hx[i]), int(hy[i]), "".join(f"{int(v / 3):02x}" for v in rgb[hy[i], hx[i]])))
    print(len(jobs), "tries", flush=True)
    with multiprocessing.Pool(8) as pool:
        for res in pool.imap_unordered(one, jobs):
            if res and res[4]:
                print(*res[:4], res[4], flush=True)


if __name__ == "__main__":
    main()
