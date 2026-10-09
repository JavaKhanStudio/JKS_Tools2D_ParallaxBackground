"""Periodic silhouettes for a parallax layer: the shape loops by construction (r239).

    python3 tools/r239-ai-strips/sil.py KIND OUT_PREFIX [--w 1024] [--h 384] [--seed 1]

KIND: mountains | hills | pines. Writes OUT_PREFIX_mask.png (white = the layer, antialiased) and
OUT_PREFIX_init.png (the shape in flat colours over a flat sky), the start image diffusion paints over.
Every curve is a sum of sines of whole frequencies over the width, and every tree sits at a
position taken modulo the width, so column w-1 runs on into column 0.
"""
import argparse

import numpy as np
from PIL import Image

SPEC = {
    # base height (fraction of h from the bottom), sines (freq, amp, sharpen), colour, sky
    "mountains": dict(base=0.55, waves=[(2, 0.10), (3, 0.08), (5, 0.05), (11, 0.012)],
                      ridge=True, colour=(120, 140, 175), sky=(210, 225, 240)),
    "hills": dict(base=0.38, waves=[(3, 0.07), (5, 0.04), (8, 0.02)],
                  ridge=False, colour=(70, 105, 95), sky=(210, 225, 240)),
    "pines": dict(base=0.16, waves=[(2, 0.03), (5, 0.02)], ridge=False, trees=70,
                  colour=(30, 50, 45), sky=(210, 225, 240)),
}


def ridge_line(w, waves, rng, ridge):
    x = np.arange(w) / w * 2 * np.pi
    y = np.zeros(w)
    for f, a in waves:
        s = np.sin(f * x + rng.uniform(0, 2 * np.pi))
        if ridge:
            s = 1 - 2 * np.abs(s) ** 0.8  # sharp peaks, round valleys
        y += a * s
    return y


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("kind", choices=SPEC)
    ap.add_argument("out")
    ap.add_argument("--w", type=int, default=1024)
    ap.add_argument("--h", type=int, default=384)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--edges-of", type=int, help="a variant: keep this seed's shape at both ends")
    ap.add_argument("--edge", type=int, default=96, help="kept px at each end, then as many to blend")
    a = ap.parse_args()
    spec, w, h = SPEC[a.kind], a.w, a.h
    ss = 4  # supersample for antialiased edges
    W, H = w * ss, h * ss
    top = profile(spec, np.random.default_rng(a.seed), W, H)
    if a.edges_of is not None:
        # The variant's middle, the kept seed's ends, blended over `edge` px: any variant of
        # that seed then follows any other in a SEQUENCE layer, the shape running on at the join.
        keep = profile(spec, np.random.default_rng(a.edges_of), W, H)
        x = np.arange(W)
        d = np.minimum(x, W - 1 - x) / ss
        r = np.clip((d - a.edge) / a.edge, 0, 1)
        top = keep * (1 - r) + top * r
    yy = H - np.arange(H)[:, None]  # height of each row from the bottom
    mask = (yy <= top[None, :]).astype(np.float32)
    mask = mask.reshape(h, ss, w, ss).mean(axis=(1, 3))
    Image.fromarray((mask * 255).astype(np.uint8)).save(f"{a.out}_mask.png")
    sky = np.array(spec["sky"], np.float32)
    shade = np.linspace(0.85, 1.1, h)[:, None, None]  # darker at the top of the layer, as haze lifts
    col = np.array(spec["colour"], np.float32) * shade
    init = sky * (1 - mask[..., None]) + col * mask[..., None]
    Image.fromarray(init.clip(0, 255).astype(np.uint8)).save(f"{a.out}_init.png")


def profile(spec, rng, W, H):
    """Height from the bottom of the layer's top edge, per supersampled column."""
    top = (spec["base"] + ridge_line(W, spec["waves"], rng, spec["ridge"])) * H
    if "trees" in spec:
        xs = np.arange(W)
        for _ in range(spec["trees"]):
            cx, tw, th = rng.uniform(0, W), rng.uniform(0.014, 0.026) * W, rng.uniform(0.10, 0.24) * H  # SD grows pines taller: leave room
            d = np.abs((xs - cx + W / 2) % W - W / 2)  # distance on the cylinder
            tree = np.where(d < tw, top + th * (1 - d / tw), 0)
            top = np.maximum(top, tree)
    return top


if __name__ == "__main__":
    main()
