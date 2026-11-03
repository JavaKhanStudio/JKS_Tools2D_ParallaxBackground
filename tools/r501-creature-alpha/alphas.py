"""r501: how the alpha layer.py's gate saw differs from the one its cut's PNG holds, strip by strip.

    python3 tools/r501-creature-alpha/alphas.py BUILD/r496 OUT [JOBS]

For each strip: the key's alpha (layer.key, ai_page.py's ruins arguments), the PNG's, the largest gap between them, the
pixels each threshold creature.py reads (0.5) puts on another side, and which the checks named from each, against the
strip's log. OUT/keyed/*.npz keeps each key's alpha: a key takes half a minute.
"""
import contextlib
import glob
import json
import multiprocessing
import os
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "r239-ai-strips"))
import layer

ROOT, OUT = sys.argv[1:3]


def keyed(p, rgb, sky):
    path = f"{OUT}/keyed/{os.path.relpath(p, ROOT).replace('/', '_')}.npz"
    if os.path.exists(path):
        return np.load(path)["alpha"]
    argv = sys.argv
    sys.argv = ["layer.py", p + "_ai.png", "/dev/null", "--mask", p + "_mask.png", "--grow", "16", "--kind", "ruins",
                "--ckpt", "DreamShaperXL_Turbo_v2_1.safetensors"]
    try:
        a = layer.parse_args()
    finally:
        sys.argv = argv
    with open(os.devnull, "w") as quiet, contextlib.redirect_stderr(quiet):
        alpha = layer.key(rgb.astype(np.float32), sky, a)[0]
    np.savez_compressed(path, alpha=alpha)
    return alpha


def one(p):
    rgb = np.asarray(Image.open(p + "_ai.png").convert("RGB")).astype(np.float64)
    sky = np.median(rgb[:8].reshape(-1, 3), axis=0)
    alpha = keyed(p, rgb, sky)
    png = np.asarray(Image.open(p + "_cut.png").convert("RGBA").getchannel("A"))
    stored = (alpha * 255).astype(np.uint8)
    log = Path(p + "_cut.log").read_text()
    return {"strip": os.path.relpath(p, ROOT), "size": rgb.shape[:2],
            "stored_is_truncated_key": int((stored != png).sum()),
            "max_gap": float(np.abs(png / 255 - alpha).max()),
            "side_of_half": int(((png / 255 >= 0.5) != (alpha >= 0.5)).sum()),
            "at_half": int((alpha == 0.5).sum()),
            "logged": len(re.findall(r"a creature\?", log)),
            "far_range": bool(re.search(r"far range", log))}


if __name__ == "__main__":
    os.makedirs(f"{OUT}/keyed", exist_ok=True)
    strips = sorted(s[:-len("_ai.png")] for s in glob.glob(f"{ROOT}/**/l*_ai.png", recursive=True))
    with multiprocessing.Pool(int(sys.argv[3]) if len(sys.argv) > 3 else 6) as pool:
        rows = pool.map(one, strips)
    with open(f"{OUT}/alphas.json", "w") as f:
        json.dump(rows, f, indent=1)
    for r in rows:
        print(r["strip"], r["size"], "png!=trunc", r["stored_is_truncated_key"], f"gap {r['max_gap']:.4f}",
              "side-of-0.5", r["side_of_half"], "at-0.5", r["at_half"], "logged", r["logged"], "far", r["far_range"])
    print("strips", len(rows), "with a far range", sum(r["far_range"] for r in rows),
          "whose PNG is not the truncated key", sum(r["stored_is_truncated_key"] > 0 for r in rows))
