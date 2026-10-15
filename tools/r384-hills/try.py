"""r384: the forest's hills strip painted under each candidate, over seeds, measured against today's settings.

    python3 tools/r384-hills/try.py OUT [--seeds 7020,7021,7022] [--only NAME,...]

Each candidate paints OUT/<name>/s<seed>_{init,mask,ai,cut}.png as ai_page.py's paint() does a hills strip (sil.py
--texture 1, gen.py img2img, layer.py --grow 8), and prints per seed the colour std inside the mask shrunk by 8 px
(tools/r365-init/measure.py's) and the alpha IoU (alpha >= 0.5) of its cut against "today" of the same seed.
A strip already on disk is not painted again: delete it to repaint.
results.tsv: what it printed (o70n over 4 seeds, then all over 3). hills.patch: o70n in ai_page.py (WORDS["hills"],
the hills' negative, HILLS_DENOISE for SD 1.5 painted), not applied: at 0.70 SD paints pale misty ranges behind the
hills, which layer.py keeps with clipped tops (r384's question).
"""
import argparse
import os
import subprocess
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
STRIPS = os.path.join(HERE, "..", "r239-ai-strips")
sys.path.insert(0, STRIPS)
import ai_page  # noqa: E402
import layer  # noqa: E402

THEME = "misty pine forest in the mountains at dawn"
COLOUR = "3e5b4b"  # the forest example's l2
BEFORE = "rolling hills with small trees"  # ai_page.WORDS["hills"] before r384
WORDS = "rolling green hills dotted with small round trees, grassy folds and groves"
NEUTRAL = "rolling hills dotted with small round trees, folds and groves"  # no colour: --colours gives it
WOODED = "rolling wooded hills dotted with small round trees, folds and groves"
CANOPY = "rolling hills covered in small round trees, tree canopy texture, folds and shadows"


def prompt(words):
    return f"2d game parallax background layer, {THEME}, middle distance {words}, painterly, plain pale sky"


# name: (prompt, colour, denoise[, more negative])
CANDIDATES = {
    "today": (prompt(BEFORE), COLOUR, 0.65),
    "words": (prompt(WORDS), COLOUR, 0.65),
    "pale": (prompt(BEFORE), "6f8a6a", 0.65),
    "d75": (prompt(BEFORE), COLOUR, 0.75),
    "d85": (prompt(BEFORE), COLOUR, 0.85),
    "d70": (prompt(BEFORE), COLOUR, 0.70),
    "w70": (prompt(WORDS), COLOUR, 0.70),
    "w75": (prompt(WORDS), COLOUR, 0.75),
    "c70": (prompt(CANOPY), COLOUR, 0.70),
    "c70p": (prompt(CANOPY), "6f8a6a", 0.70),
    "n70": (prompt(NEUTRAL), COLOUR, 0.70),
    "o70": (prompt(WOODED), COLOUR, 0.70),
    "o70n": (  # what ai_page.py paints since r384
        prompt(WOODED), COLOUR, 0.70, ", pine trees, conifers, fir trees"),
}


def paint(out, name, seed):
    p = os.path.join(out, name, f"s{seed}")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    if os.path.exists(p + "_cut.png"):
        return p
    text, colour, denoise, *more = CANDIDATES[name]
    subprocess.run(["python3", f"{STRIPS}/sil.py", "hills", p, "--seed", str(seed), "--colour", colour,
                    "--quiet-join", "--texture", str(ai_page.TEXTURE)], check=True, stdout=subprocess.DEVNULL)
    with open(p + "_gen.log", "w") as log:
        subprocess.run([ai_page.PY, f"{STRIPS}/gen.py", "img2img", "--denoise", str(denoise), "--negative",
                        ai_page.NEG + "".join(more), "--prompt", text, "--init", p + "_init.png",
                        "--tile", "x", "--seed", str(seed), "--out", p + "_ai.png"], check=True, stdout=log,
                       stderr=subprocess.STDOUT)
    subprocess.run(["python3", f"{STRIPS}/layer.py", p + "_ai.png", p + "_cut.png", "--mask", p + "_mask.png",
                    "--grow", str(ai_page.GROW["hills"]), "--no-creatures"], check=True)
    return p


def measure(p, base):
    zone = layer.spread(np.asarray(Image.open(p + "_mask.png").convert("L")) > 127, -8)
    img = np.asarray(Image.open(p + "_ai.png").convert("RGB")).astype(np.float64)
    a = np.asarray(Image.open(p + "_cut.png"))[..., 3] >= 128
    b = np.asarray(Image.open(base + "_cut.png"))[..., 3] >= 128
    return float(img[zone].std(axis=0).mean()), float((a & b).sum() / max((a | b).sum(), 1))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--seeds", default="7020,7021,7022")
    ap.add_argument("--only", help="candidates, comma separated (today is always painted)")
    a = ap.parse_args()
    seeds = [int(s) for s in a.seeds.split(",")]
    names = ["today"] + [n for n in (a.only.split(",") if a.only else CANDIDATES) if n != "today"]
    print("candidate\t" + "\t".join(f"std {s}\tIoU {s}" for s in seeds))
    for name in names:
        cells = []
        for s in seeds:
            std, iou = measure(paint(a.out, name, s), paint(a.out, "today", s))
            cells += [f"{std:.1f}", f"{iou:.3f}"]
        print(name + "\t" + "\t".join(cells), flush=True)


if __name__ == "__main__":
    main()
