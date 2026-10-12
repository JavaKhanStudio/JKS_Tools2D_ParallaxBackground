"""Which img2img denoise a DiT wants in ai-page.sh (r357): paint the forest example's depths at several denoise
values over several seeds, as ai_page.py would, and count what its checks refuse.

    python3 tools/r357-dit-denoise/sweep.py OUT [--denoise 0.65,0.75,0.85] [--seeds 5] [--kinds pines,mountains]

Per strip: layer.py's exit (art on the top row, a creature), the pines' outline roughness (TREES), the seam ratio of
the painted strip, and how far its opaque colour moved from sil.py's (--colours is the theme's say in the palette).
Writes OUT/sweep.json and OUT/<kind>-<denoise>.png (the cuts over a grey sky, one under the other). ~75 s a strip.
"""
import argparse
import json
import os
import subprocess
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
STRIPS = os.path.join(os.path.dirname(HERE), "r239-ai-strips")
sys.path.insert(0, STRIPS)
sys.path.insert(0, os.path.dirname(HERE))
import ai_page  # noqa: E402
import seam  # noqa: E402

THEME = "misty pine forest in the mountains at dawn"
COLOUR = {"mountains": "7d8aa3", "hills": "3e5b4b", "pines": "1d3328"}  # the forest example's, by depth
DEPTH = {"mountains": 0, "hills": 2, "pines": 3}


def paint(out, kind, denoise, seed):
    p = os.path.join(out, f"{kind}-{denoise}-{seed}")
    depth = {0: "distant", 3: "foreground"}.get(DEPTH[kind], "middle distance")
    with open(p + ".log", "w") as log:
        rc = paint_cut(p, kind, depth, denoise, seed, log)
    a = seam.load(f"{p}_ai.png")
    row = {"kind": kind, "denoise": denoise, "seed": seed, "layer_rc": rc,
           "seam": round(seam.step(a[:, -1], a[:, 0]) / seam.inner(a), 2)}
    if os.path.exists(f"{p}_cut.png"):
        c = np.asarray(Image.open(f"{p}_cut.png").convert("RGBA")).astype(float)
        mean = c[c[..., 3] > 200][:, :3].mean(axis=0)
        want = np.array([int(COLOUR[kind][i:i + 2], 16) for i in (0, 2, 4)])
        row["colour"], row["colour_shift"] = ai_page.hexc(mean), round(float(np.abs(mean - want).mean()), 1)
        row["roughness"] = round(ai_page.roughness(f"{p}_cut.png"), 2)
    row["refused"] = rc != 0 or (kind == "pines" and row.get("roughness", 0) < ai_page.TREES)
    print(json.dumps(row), flush=True)
    return row


def paint_cut(p, kind, depth, denoise, seed, log):
    """sil.py, gen.py and layer.py, as ai_page.py runs them: layer.py's exit code."""
    subprocess.run(["python3", f"{STRIPS}/sil.py", kind, p, "--w", "1536", "--h", "576", "--seed", str(seed),
                    "--colour", COLOUR[kind], "--quiet-join"], stdout=log, stderr=subprocess.STDOUT, check=True)
    subprocess.run([ai_page.PY, f"{STRIPS}/gen.py", "img2img", "--ckpt", "z-image-turbo-Q5_K_M.gguf", "--w", "1536",
                    "--h", "576", "--denoise", str(denoise), "--prompt",
                    f"2d game parallax background layer, {THEME}, {depth} {ai_page.WORDS[kind]}, painterly, plain pale sky",
                    "--init", f"{p}_init.png", "--tile", "x", "--seed", str(seed), "--out", f"{p}_ai.png"],
                   stdout=log, stderr=subprocess.STDOUT, check=True)
    return subprocess.run(["python3", f"{STRIPS}/layer.py", f"{p}_ai.png", f"{p}_cut.png", "--mask", f"{p}_mask.png",
                           "--grow", str(ai_page.GROW[kind])], stdout=log, stderr=subprocess.STDOUT, check=False).returncode


def sheet(out, kind, denoise, seeds):
    rows = []
    for s in seeds:
        path = os.path.join(out, f"{kind}-{denoise}-{s}_cut.png")
        if os.path.exists(path):
            im = Image.open(path).convert("RGBA")
            bg = Image.new("RGBA", im.size, (200, 205, 215, 255))
            rows.append(Image.alpha_composite(bg, im).convert("RGB").resize((768, 288)))
    if rows:
        img = Image.new("RGB", (768, 288 * len(rows)))
        for i, r in enumerate(rows):
            img.paste(r, (0, 288 * i))
        img.save(os.path.join(out, f"{kind}-{denoise}.png"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--denoise", default="0.65,0.75,0.85")
    ap.add_argument("--seeds", type=int, default=5)
    ap.add_argument("--kinds", default="pines,mountains")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    rows = []
    for kind in a.kinds.split(","):
        seeds = [7000 + DEPTH[kind] * 10 + t for t in range(a.seeds)]  # ai_page.py's seeds for --seed 7
        for d in (float(x) for x in a.denoise.split(",")):
            rows += [paint(a.out, kind, d, s) for s in seeds]
            sheet(a.out, kind, d, seeds)
    with open(os.path.join(a.out, "sweep.json"), "w") as f:
        json.dump(rows, f, indent=1)
    for kind in a.kinds.split(","):
        for d in sorted({r["denoise"] for r in rows}):
            rs = [r for r in rows if r["kind"] == kind and r["denoise"] == d]
            print(f"{kind} {d}: refused {sum(r['refused'] for r in rs)}/{len(rs)}, roughness "
                  f"{[r.get('roughness') for r in rs]}, colour shift {[r.get('colour_shift') for r in rs]}, "
                  f"seam max {max(r['seam'] for r in rs)}")


if __name__ == "__main__":
    main()
