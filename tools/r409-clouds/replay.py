#!/usr/bin/env python3
"""r409's harness: depth_page.main on a painting without the GPU, to look at the sky, clouds and range it cuts.

    ~/ComfyUI/venv/bin/python tools/r409-clouds/replay.py CACHE.npz OUT --theme "..." [--depth-page PATH] [-- ARGS]

CACHE.npz is tools/r380-twig-gaps/bands.py's (cache: the painting, its depth, SAM's masks, the prompted fields).
The repaints are not run: each layer's hole keeps fill()'s start (OUT/cut/l<k>_src.png), so what moves between two
runs is the cut, not a seed; --repaint runs them (gen.py, on the GPU), to tune a repaint on a cached cut (r426). --no-shots is passed; tools/parallax-lab-shots.sh OUT/page OUT/shots renders it.
--depth-page: another checkout's depth_page.py (a HEAD worktree's, for the before).
"""
import argparse
import contextlib
import os
import shutil
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "r380-twig-gaps"))
import bands  # noqa: E402


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("cache")
    ap.add_argument("out")
    ap.add_argument("--theme", required=True)
    ap.add_argument("--depth-page", default=bands.DEFAULT)
    ap.add_argument("--repaint", action="store_true", help="run gen.py's repaints instead of keeping their start")
    ap.add_argument("rest", nargs="*", help="more depth_page arguments, after --")
    a = ap.parse_args()
    dp = bands.load(os.path.abspath(a.depth_page))
    c = np.load(a.cache)
    dp.depth_of = lambda img: c["d"]
    dp.masks_of = lambda img: list(c["masks"])
    dp.fields_of = lambda img, L, n: list(c["fields"])
    dp.gpu_lock.held = lambda what: contextlib.nullcontext()

    def gen(args, logfile):  # the repaint's start stands for the repaint
        shutil.copy(args[args.index("--src") + 1], args[args.index("--out") + 1])
    if not a.repaint:
        dp.gen = gen
    os.makedirs(a.out, exist_ok=True)
    painting = os.path.join(a.out, "painting.png")
    Image.fromarray(c["img"]).save(painting)
    sys.argv = ["depth_page.py", painting, a.out, "--theme", a.theme, "--no-shots", *a.rest]
    return dp.main()


if __name__ == "__main__":
    sys.exit(main())
