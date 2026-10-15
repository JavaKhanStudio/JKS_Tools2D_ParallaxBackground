#!/usr/bin/env python3
"""r380's harness: depth_page's bands of a painting without the GPU, to tune see_through on.

    ~/ComfyUI/venv/bin/python tools/r380-twig-gaps/bands.py cache PAINTING CACHE.npz
    ~/ComfyUI/venv/bin/python tools/r380-twig-gaps/bands.py bands CACHE.npz OUT_DIR [--depth-page PATH]

cache runs the three GPU steps (depth, SAM's masks, the prompted fields) once, under gpu_lock, and keeps them; bands
replays depth_page.depth_bands with those (--depth-page: another checkout's depth_page.py, a HEAD worktree's for the
before), and writes OUT_DIR/L.npy and OUT_DIR/cut/bands.png. Then counts.py compares two of them.
"""
import contextlib
import importlib.util
import os
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT = os.path.join(HERE, "..", "r279-depth-page", "depth_page.py")


def load(path):
    spec = importlib.util.spec_from_file_location("depth_page_" + str(abs(hash(path))), path)
    dp = importlib.util.module_from_spec(spec)
    sys.path.insert(0, os.path.dirname(path))
    spec.loader.exec_module(dp)
    return dp


def cache(painting, out):
    dp = load(DEFAULT)
    img = np.asarray(Image.open(painting).convert("RGB"))
    with dp.gpu_lock.held("r380 depth"):
        d = dp.depth_of(img)
    with dp.gpu_lock.held("r380 masks"):
        masks = dp.masks_of(img)
    # fields_of needs the bands: replay depth_bands up to it with the real depth and masks
    lo, hi = np.percentile(d, (1, 99.5))
    dn = np.clip((d - lo) / (hi - lo), 0, 1)
    h, w = img.shape[:2]
    L, _ = dp.bands(dn, 4, min_area=w * h // 150)
    L = dp.objects(L, masks, 4)
    with dp.gpu_lock.held("r380 fields"):
        fields = dp.fields_of(img, L, 4)
    np.savez_compressed(out, img=img, d=d, masks=np.array(masks, bool), fields=np.array(fields, bool))


def bands(cache_file, out, path):
    dp = load(path)
    c = np.load(cache_file)
    dp.depth_of = lambda img: c["d"]
    dp.masks_of = lambda img: list(c["masks"])
    dp.fields_of = lambda img, L, n: list(c["fields"])
    os.makedirs(os.path.join(out, "cut"), exist_ok=True)
    dp.log = lambda s: None
    dp.gpu_lock.held = lambda what: contextlib.nullcontext()  # nothing here runs on the GPU
    L = dp.depth_bands(c["img"], 4, os.path.join(out, "cut"))[1]  # (dn, L, centres, ground, masks) since r387
    np.save(os.path.join(out, "L.npy"), L)
    Image.fromarray(c["img"]).save(os.path.join(out, "cut", "painting.png"))


if __name__ == "__main__":
    if sys.argv[1] == "cache":
        cache(sys.argv[2], sys.argv[3])
    else:
        p = sys.argv[sys.argv.index("--depth-page") + 1] if "--depth-page" in sys.argv else DEFAULT
        bands(sys.argv[2], sys.argv[3], os.path.abspath(p))
