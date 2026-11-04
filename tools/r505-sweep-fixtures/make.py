"""r505: wrote tools/r261-creature/fixtures from the strips on disk that day (kept for the record: they are scratch, and
may be gone). Each painting whole and losslessly recompressed: creature.py's sizes follow the strip's width and its box
wraps along X, so a crop answers for another strip. A cut keeps its alpha alone, RGB black: creature.py with --src looks
at the painting through it and never reads the cut's colours, which cost as much as the painting.

    python3 tools/r505-sweep-fixtures/make.py [OUT]
"""
import os
import sys

import numpy as np
from PIL import Image

AI = "../JKS_Tools2D_ParallaxAI/build/rounds"
EX = "build/ai/examples"
SOURCES = {
    "forest_l1": f"{EX}/forest-painted/strips/l1",
    "forest_l3v3": f"{EX}/forest-painted/strips/l3v3",
    "beach_l2": f"{EX}/beach-painted/strips/l2",
    "beach_l3": f"{EX}/beach-painted/strips/l3",
    "beach_l3v1": f"{EX}/beach-painted/strips/l3v1",
    "postapo_l2": f"{EX}/postapo-painted/strips/l2",
    "postapo_l3v1": f"{EX}/postapo-painted/strips/l3v1",
    "pixel_forest_l1": f"{EX}/forest-pixel/strips/l1",
    "dunes": "build/r437/dunes/s11023",
    "ruins_a": "build/r496/plain/s17/strips/l2",
    "ruins_b": "build/r496/warp/s22/strips/l2",
    "oj4_beach_l2": f"{AI}/r1/oj4-beach/strips/l2",
    "nova_l2": f"{AI}/r1/nova-postapo/strips/l2",
    "nova_l3": f"{AI}/r1/nova-postapo/strips/l3",
    "nova_l3v3": f"{AI}/r1/nova-postapo/strips/l3v3",
    "bir_l1": f"{AI}/r6a/bir-postapo/strips/l1",
}


def main():
    out = sys.argv[1] if len(sys.argv) > 1 else "tools/r261-creature/fixtures"
    os.makedirs(out, exist_ok=True)
    for name, p in SOURCES.items():
        Image.open(p + "_ai.png").convert("RGB").save(f"{out}/{name}_ai.png", optimize=True)
        if name.startswith("pixel_"):  # one pixel per art cell: pixel.py cuts it again, from the mask
            Image.open(p + "_mask.png").convert("L").save(f"{out}/{name}_mask.png", optimize=True)
            continue
        cut = np.asarray(Image.open(p + "_cut.png").convert("RGBA")).copy()
        cut[..., :3] = 0
        Image.fromarray(cut, "RGBA").save(f"{out}/{name}_cut.png", optimize=True)
        if os.path.exists(p + "_mask.png"):
            Image.open(p + "_mask.png").convert("L").save(f"{out}/{name}_mask.png", optimize=True)


if __name__ == "__main__":
    main()
