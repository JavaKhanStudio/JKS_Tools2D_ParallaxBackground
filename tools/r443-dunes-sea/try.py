"""r443: the beach's dunes strip (DreamShaperXL Turbo, layer 2) painted under each candidate prompt over seeds, as
tools/r442-sea-words/repaint.sh paints it: layer.py's exit and the sea px (b > r + 60, g > r + 20) per seed.

    python3 tools/r443-dunes-sea/try.py OUT [--only NAME,...] [--seeds 11020-11029]

A strip already painted (OUT/<name>/s<seed>_ai.png) is not painted again: delete it to repaint.
"""
import argparse
import os
import subprocess
import sys

import numpy as np
from PIL import Image

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
D = os.path.join(ROOT, "tools/r239-ai-strips")
PY = os.path.expanduser("~/ComfyUI/venv/bin/python")
sys.path.insert(0, D)
import ai_page  # noqa: E402

BEACH, KINDS = "tropical beach, palm trees, warm sunny afternoon", ["mountains", "sea", "dunes", "palms"]
NEG = "text, watermark, frame, border, people, buildings, foreground grass, birds, animals, creatures, palm trees"
SEA = ", sea, ocean, water"


def prompt(theme, words):
    return f"2d game parallax background layer, {theme}, middle distance {words}, painterly, plain pale sky"


# name: (prompt, negative)
CANDIDATES = {
    "r442": (prompt("tropical beach, warm sunny afternoon", "sand dunes"), NEG + SEA),
    "inland": (prompt("tropical beach, warm sunny afternoon", "desert sand dunes, endless sand"), NEG + SEA),
    "nobeach": (prompt("warm sunny afternoon", "sand dunes"), NEG + SEA),
    "both": (prompt("warm sunny afternoon", "desert sand dunes, endless sand"), NEG + SEA),
    "tropical": (prompt("tropical, warm sunny afternoon", "sand dunes"), NEG + SEA + ", beach"),
}
# What ai_page.py paints today: its theme_for on the beach page (r443: nobeach, "tropical beach" in the negative too).
_theme, _struck = ai_page.theme_for(BEACH, "dunes", KINDS)
CANDIDATES["today"] = (prompt(_theme, ai_page.WORDS["dunes"]),
                       ai_page.negative(ai_page.NEG, "dunes") + "".join(", " + w for w in _struck))


def sea_px(p):
    a = np.asarray(Image.open(p).convert("RGB")).astype(int)
    return int(((a[..., 2] > a[..., 0] + 60) & (a[..., 1] > a[..., 0] + 20)).sum())


def paint(out, name, s):
    p = os.path.join(out, name, f"s{s}")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    text, neg = CANDIDATES[name]
    if not os.path.exists(p + "_ai.png"):
        subprocess.run(["python3", f"{D}/sil.py", "dunes", p, "--w", "1536", "--h", "576", "--seed", str(s), "--colour",
                        "e0c890", "--quiet-join", "--texture", "1.0"], check=True, stdout=subprocess.DEVNULL)
        with open(p + "_gen.log", "w") as log:
            subprocess.run([PY, f"{D}/gen.py", "img2img", "--ckpt", "DreamShaperXL_Turbo_v2_1.safetensors", "--w", "1536",
                            "--h", "576", "--steps", "8", "--cfg", "2.0", "--sampler", "dpmpp_sde", "--denoise", "0.65",
                            "--negative", neg, "--prompt", text, "--init", p + "_init.png", "--tile", "x", "--seed",
                            str(s), "--out", p + "_ai.png"], check=True, stdout=log, stderr=subprocess.STDOUT)
    with open(p + "_cut.log", "w") as log:
        code = subprocess.run(["python3", f"{D}/layer.py", p + "_ai.png", p + "_cut.png", "--mask", p + "_mask.png",
                               "--grow", "8", "--kind", "dunes", "--ckpt", "DreamShaperXL_Turbo_v2_1.safetensors",
                               "--rules-json", p + "_rules.json"], stdout=log, stderr=subprocess.STDOUT,
                              check=False).returncode
    return code, sea_px(p + "_ai.png")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--only")
    ap.add_argument("--seeds", default="11020-11029")
    a = ap.parse_args()
    lo, hi = (int(x) for x in a.seeds.split("-"))
    print("candidate\trefused\tsea px\tper seed (exit/sea px)")
    for name in a.only.split(",") if a.only else CANDIDATES:
        cells = [paint(a.out, name, s) for s in range(lo, hi + 1)]
        print(f"{name}\t{sum(c != 0 for c, _ in cells)}\t{sum(x for _, x in cells)}\t"
              + " ".join(f"{c}/{x}" for c, x in cells), flush=True)


if __name__ == "__main__":
    main()
