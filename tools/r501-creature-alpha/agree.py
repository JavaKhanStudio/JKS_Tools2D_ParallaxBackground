"""r501: does creature.py's CLI answer as layer.py's gate does, blob for blob?

    python3 tools/r501-creature-alpha/agree.py ROOT R239_DIR KEYED [JOBS]

Over every strip under ROOT: R239_DIR's creature.py through its command line (the cut's PNG), and its checks handed
layer.key's alpha, as layer.py's check does (KEYED/*.npz: alphas.py's, cached). Prints how many blobs each finds of the
strips' logs (meaningful with the creature.py that wrote them), and every blob only one of the two names. Exits 1 on any.
"""
import glob
import json
import multiprocessing
import os
import re
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT, D, KEYED = sys.argv[1:4]
sys.path.insert(0, D)
import creature  # noqa: E402

BLOB = r"a creature\? (\d+) px blob at x=(\d+) y=(\d+)"


def blobs(text):
    return {tuple(map(int, m)) for m in re.findall(BLOB, text)}


def one(p):
    cli = subprocess.run([sys.executable, f"{D}/creature.py", p + "_cut.png", "--src", p + "_ai.png"], check=False,
                         capture_output=True, text=True, env={**os.environ, "OMP_NUM_THREADS": "1"})  # 4: refused
    rgb = np.asarray(Image.open(p + "_ai.png").convert("RGB")).astype(np.float64)
    sky = np.median(rgb[:8].reshape(-1, 3), axis=0)
    alpha = np.load(f"{KEYED}/{os.path.relpath(p, ROOT).replace('/', '_')}.npz")["alpha"]
    k = rgb.shape[1] / 1024
    r = int(12 * k)
    found = creature.find(rgb, alpha, int(50 * k * k), int(3000 * k * k), r=r)
    found += creature.on_ridge(rgb, alpha, sky, int(50 * k * k), r)
    found += creature.on_neck(rgb, alpha, sky, r)
    return {"strip": os.path.relpath(p, ROOT), "cli": sorted(blobs(cli.stderr)),
            "gate": sorted((s, x, y) for x, y, s in found), "log": sorted(blobs(Path(p + "_cut.log").read_text()))}


if __name__ == "__main__":
    strips = sorted(s[:-len("_ai.png")] for s in glob.glob(f"{ROOT}/**/l*_ai.png", recursive=True))
    with multiprocessing.Pool(int(sys.argv[4]) if len(sys.argv) > 4 else 6) as pool:
        rows = pool.map(one, strips)
    log, cli, gate, apart = 0, 0, 0, 0
    for r in rows:
        a, b, c = (set(map(tuple, r[w])) for w in ("cli", "gate", "log"))
        log, cli, gate = log + len(c), cli + len(a & c), gate + len(b & c)
        for x in sorted(a ^ b):
            apart += 1
            print(r["strip"], "only the CLI" if x in a else "only the gate", x)
    print(f"{len(rows)} strips, logged blobs {log}: the CLI finds {cli} of them, the gate's alpha {gate}; "
          f"{apart} blob(s) only one of the two names; {sum(len(r['cli']) for r in rows)} from the CLI in all")
    print(json.dumps({"refused_by_cli": sum(bool(r["cli"]) for r in rows), "refused_by_gate": sum(bool(r["gate"]) for r in rows)}))
    sys.exit(1 if apart else 0)
