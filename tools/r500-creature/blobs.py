"""r500: which of creature.py's checks fired on r496's refused strips, and each blob's real box and colours.

    python3 tools/r500-creature/blobs.py BUILD/r496 OUT

The alpha is layer.py's own key(), with ai_page.py's arguments for ruins: the first printed line says how many of the
logged blobs that finds again. OUT/blobs.png: each blob's real extent in red beside its crop; OUT/blobs.json: the numbers.
"""
import argparse
import contextlib
import glob
import json
import os
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "r239-ai-strips"))
import creature
import layer


def alpha_of(p, rgb, sky):
    """layer.py's alpha for strip prefix p, as ai_page.py cuts a ruins strip."""
    argv = sys.argv
    sys.argv = ["layer.py", p + "_ai.png", "/dev/null", "--mask", p + "_mask.png", "--grow", "16", "--kind", "ruins",
                "--ckpt", "DreamShaperXL_Turbo_v2_1.safetensors"]
    try:
        a = layer.parse_args()
    finally:
        sys.argv = argv
    with open(os.devnull, "w") as quiet, contextlib.redirect_stderr(quiet):
        return layer.key(rgb.astype(np.float32), sky, a)[0]


def checks(rgb, alpha, sky):
    k = rgb.shape[1] / 1024
    r = int(12 * k)
    return r, {"find": creature.find(rgb, alpha, int(50 * k * k), int(3000 * k * k), r=r),
               "ridge": creature.on_ridge(rgb, alpha, sky, int(50 * k * k), r),
               "neck": creature.on_neck(rgb, alpha, sky, r)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("root")
    ap.add_argument("out")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    rows, tiles = [], []
    again = logged = 0
    for ai in sorted(glob.glob(f"{a.root}/**/l*_ai.png", recursive=True)):
        p = ai[:-len("_ai.png")]
        if not os.path.exists(p + "_cut.png"):
            continue
        name = os.path.relpath(p, a.root).replace("/strips", "")
        log = Path(p + "_cut.log").read_text() if os.path.exists(p + "_cut.log") else ""
        old = {tuple(map(int, m)) for m in re.findall(r"a creature\? (\d+) px blob at x=(\d+) y=(\d+)", log)}
        rgb = np.asarray(Image.open(ai).convert("RGB")).astype(np.float64)
        sky = np.median(rgb[:8].reshape(-1, 3), axis=0)
        alpha = alpha_of(p, rgb, sky)
        _, found = checks(rgb, alpha, sky)
        now = {(f[2], f[0], f[1]) for fs in found.values() for f in fs}
        logged += len(old)
        again += len(old & now)
        for which, fs in found.items():
            for x, y, size in fs:
                rows.append({"strip": name, "check": which, "x": x, "y": y, "size": size, "logged": (size, x, y) in old})
                c = np.take(np.asarray(rgb, np.uint8)[max(y - 50, 0):y + 50], range(x - 50, x + 50), axis=1, mode="wrap")
                tiles.append((f"{name} {which} {size}px y={y}", c, min(y, 50)))
        for size, x, y in sorted(old - now):
            rows.append({"strip": name, "check": "NOT AGAIN", "x": x, "y": y, "size": size, "logged": True})
    print(f"logged blobs {logged}, found again from the cut's alpha {again}")
    for r in rows:
        print(r["strip"], r["check"], f"x={r['x']} y={r['y']} {r['size']}px", "" if r["logged"] else "(not logged)")
    with open(f"{a.out}/blobs.json", "w") as f:
        json.dump(rows, f, indent=1)
    size, cols = 300, 5
    im = Image.new("RGB", (cols * (size + 4), (len(tiles) + cols - 1) // cols * (size + 18)), "white")
    d = ImageDraw.Draw(im)
    for i, (title, c, cy) in enumerate(tiles):
        px, py = (i % cols) * (size + 4), (i // cols) * (size + 18)
        d.text((px + 2, py + 2), title, fill="black")
        im.paste(Image.fromarray(c).resize((c.shape[1] * 3, c.shape[0] * 3), Image.NEAREST), (px, py + 18))
        d.rectangle([px + 135, py + 18 + cy * 3 - 15, px + 165, py + 18 + cy * 3 + 15], outline=(0, 255, 0))
    im.save(f"{a.out}/blobs.png")


if __name__ == "__main__":
    main()
