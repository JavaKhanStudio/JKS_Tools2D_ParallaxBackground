"""What each of layer.py's fixes does, per model and layer kind (r400): every painted strip of each PAGE cut by
layer.key with all its fixes (its RULES weighed on the strip's kind and checkpoint, as ai_page.py runs it), then with
one taken out; the opaque px each one moved (|alpha difference| summed: + what taking it out adds back, - clears).

    python3 tools/r400-ai-rules/probe.py ../JKS_Tools2D_ParallaxAI/build/rounds/r1/*/ build/ai/examples/*/

No GPU, no file written. A fix that moves nothing on one model or kind, or only bites there, is one to put behind a
rule (tools/parallax_rules.py). On R1 and the examples (r400): --gap, --edge and the islands cut act on every model;
misty_pines on dreamshaper_8's pines only.
"""
import argparse
import contextlib
import io
import json
import os
import sys
from collections import defaultdict
from concurrent.futures import ProcessPoolExecutor

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "r239-ai-strips"))
import layer
from ai_page import GEN_CKPT, GROW

OFF = {"gap": {"gap": 0}, "edge": {"edge": 0}, "islands": {"keep_islands": True},
       "misty_pines": {"forced": {"misty_pines": False}}, "wide_mass": {"forced": {"wide_mass": False}}}


def one(job):
    page, strip, kind, ckpt = job
    img = np.asarray(Image.open(f"{page}/strips/{strip}_ai.png").convert("RGB")).astype(np.float32)
    sky = np.median(img[:8].reshape(-1, 3), axis=0)
    base = {"near": 18, "far": 0, "mask": f"{page}/strips/{strip}_mask.png", "grow": GROW[kind], "gap": 0.6,
            "edge": 2, "keep_islands": False, "out": os.devnull, "kind": kind, "ckpt": ckpt, "forced": {}}
    out = {}
    with contextlib.redirect_stderr(io.StringIO()):
        full, _ = layer.key(img, sky, argparse.Namespace(**base))
        for name, change in OFF.items():
            alpha, _ = layer.key(img, sky, argparse.Namespace(**dict(base, **change)))
            out[name] = (float((alpha - full).clip(0).sum()), float((full - alpha).clip(0).sum()))
    return page, strip, kind, ckpt, out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("pages", nargs="+")
    a = ap.parse_args()
    jobs = []
    for page in a.pages:
        page = page.rstrip("/")
        if not os.path.exists(f"{page}/recipe.json"):
            continue
        with open(f"{page}/recipe.json") as f:
            r = json.load(f)
        if r.get("style") != "painted":
            continue
        for lay in r["layers"]:
            jobs += [(page, s["strip"], lay["kind"], r.get("ckpt") or GEN_CKPT) for s in lay["strips"]]
    total = defaultdict(lambda: defaultdict(lambda: np.zeros(2)))
    with ProcessPoolExecutor(8) as ex:
        for page, strip, kind, ckpt, out in ex.map(one, jobs):
            print(f"{os.path.basename(page):16} {strip:6} {kind:9} {ckpt[:22]:22} " + "  ".join(
                f"{n} +{v[0]:.0f} -{v[1]:.0f}" for n, v in out.items()))
            for n, v in out.items():
                total[n][(kind, ckpt)] += v
    print("\nper fix, kind, model: px taking it out adds back (+) / clears (-)")
    for n, by in total.items():
        for (kind, ckpt), v in sorted(by.items()):
            print(f"{n:11} {kind:9} {ckpt[:28]:28} +{v[0]:8.0f} -{v[1]:8.0f}")


if __name__ == "__main__":
    main()
