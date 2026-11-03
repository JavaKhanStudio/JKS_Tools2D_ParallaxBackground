"""r500: r496's strips cut again by layer.py (--kind ruins, as ai_page.py), no GPU: how many it refuses, and what.

    python3 tools/r500-creature/refused.py BUILD/r496 OUT [LAYERPY]
"""
import glob
import multiprocessing
import os
import subprocess
import sys

HERE = os.path.dirname(os.path.abspath(__file__))


def one(job):
    p, out, py = job
    name = os.path.relpath(p, sys.argv[1]).replace("/strips", "")
    dest = os.path.join(out, name.replace("/", "-") + ".png")
    r = subprocess.run([sys.executable, py, p + "_ai.png", dest, "--mask", p + "_mask.png", "--grow", "16", "--kind", "ruins",
                        "--ckpt", "DreamShaperXL_Turbo_v2_1.safetensors"], capture_output=True, text=True, check=False)
    return name, r.returncode, [ln.split(": ", 1)[1] for ln in r.stderr.splitlines() if "a creature?" in ln]


def main():
    root, out = sys.argv[1:3]
    py = sys.argv[3] if len(sys.argv) > 3 else os.path.join(HERE, "..", "r239-ai-strips", "layer.py")
    os.makedirs(out, exist_ok=True)
    jobs = [(p[:-len("_ai.png")], out, py) for p in sorted(glob.glob(f"{root}/*/s*/strips/l*_ai.png"))]
    with multiprocessing.Pool(8) as pool:
        rows = pool.map(one, jobs)
    bad = [r for r in rows if r[1]]
    for name, code, what in bad:
        print(name, code, "; ".join(what))
    print(f"{len(bad)} of {len(rows)} refused")


if __name__ == "__main__":
    main()
