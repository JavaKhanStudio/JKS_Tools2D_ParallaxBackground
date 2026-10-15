"""Give each frame of an animated layer its own alpha where it moves (r375).

    python3 tools/r375-animated-layer/rekey.py FRAMES_DIR --src l3_ai.png --cut l3_cut.png --mask OUT/mask.png

The layer's cut (layer.py) keyed the still strip: a frond that sways out of it is clipped, and the sky it leaves
shows inside it as a light halo. Inside --mask (white: may move) each frame is keyed again against the sky round
the moving part, its colour the median of the painted strip's (--src) pixels the cut made clear there, the alpha
rising from 0 to 1 between two distances fitted so that keying the painted strip gives back the cut's alpha. Outside it the cut's alpha stays, blended
across the mask's grey edge. A moving part with no clear pixel round it (a sea inside its band) keeps the cut's
alpha. Rewrites FRAMES_DIR/f*.png as RGBA and writes FRAMES_DIR/rekey.json (the fit).
"""
import argparse
import glob
import json
import os

import numpy as np
from PIL import Image


def key(rgb, sky, near, far):
    d = np.sqrt(((rgb - sky) ** 2).sum(-1))
    return np.clip((d - near) / max(far - near, 1e-3), 0, 1)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("frames")
    ap.add_argument("--src", required=True, help="the painted strip the cut was keyed from (its clear pixels are sky)")
    ap.add_argument("--cut", required=True)
    ap.add_argument("--mask", required=True)
    a = ap.parse_args()
    cut = np.asarray(Image.open(a.cut).convert("RGBA"), np.float32)
    alpha = cut[..., 3] / 255
    soft = np.asarray(Image.open(a.mask).convert("L"), np.float32) / 255
    inside = soft > 0.02
    files = sorted(glob.glob(os.path.join(a.frames, "f*.png")))
    painted = np.asarray(Image.open(a.src).convert("RGB"), np.float32)  # the cut's clear pixels hold spread colours
    clear = inside & (alpha < 0.04)
    if clear.sum() < 64:
        # what moves lies inside the layer's mass (a sea band): no outline moves, the cut's alpha holds every frame
        for f in files:
            rgb = np.asarray(Image.open(f).convert("RGB"), np.float32)
            Image.fromarray(np.dstack([rgb, alpha * 255 + 0.5]).astype(np.uint8), "RGBA").save(f)
        fit = {"sky": None, "kept": "the cut's alpha: nothing clear round the moving part"}
        with open(os.path.join(a.frames, "rekey.json"), "w") as fp:
            json.dump(fit, fp)
        print(json.dumps(fit))
        return
    sky = np.median(painted[clear], axis=0)
    best = None
    for near in range(0, 120, 4):
        for far in range(near + 8, 220, 8):
            err = float(np.abs(key(painted[inside], sky, near, far) - alpha[inside]).mean())
            if best is None or err < best[0]:
                best = (err, near, far)
    err, near, far = best
    for f in files:
        rgb = np.asarray(Image.open(f).convert("RGB"), np.float32)
        own = key(rgb, sky, near, far)
        out_a = soft * own + (1 - soft) * alpha
        Image.fromarray(np.dstack([rgb, out_a * 255 + 0.5]).astype(np.uint8), "RGBA").save(f)
    fit = {"sky": [round(float(c), 1) for c in sky], "near": near, "far": far, "error_on_still": round(err, 4)}
    with open(os.path.join(a.frames, "rekey.json"), "w") as fp:
        json.dump(fit, fp)
    print(json.dumps(fit))


if __name__ == "__main__":
    main()
