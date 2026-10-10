"""Periodic silhouettes for a parallax layer: the shape loops by construction (r239).

    python3 tools/r239-ai-strips/sil.py KIND OUT_PREFIX [--w 1024] [--h 384] [--seed 1] [--colour 1f3a30]

KIND: mountains | hills | pines | dunes | sea | ruins | palms (r239's examples: a beach, a post-apocalyptic city). Writes OUT_PREFIX_mask.png (white = the layer, antialiased) and
OUT_PREFIX_init.png (the shape in flat colours over a flat sky), the start image diffusion paints over.
img2img at 0.65 keeps much of that colour: --colour (hex) sets the layer's for a theme (r246).
--quiet-join turns the shape so the loop's join falls where its outline is flattest (a tree's flank there
steps past what a SEQUENCE's joins may show); a variant (--edges-of) turns by its kept seed's, so it still fits.
Every curve is a sum of sines of whole frequencies over the width, and every tree sits at a
position taken modulo the width, so column w-1 runs on into column 0. ruins are towers with broken tops on a rubble line, a height per column like the
rest; palms overhang (a crown wider than its trunk, sky under it), so they are drawn over the dunes as shapes, each one
again a width to the left and right so the ones across the join run on.
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
    "dunes": dict(base=0.30, waves=[(2, 0.05), (3, 0.04), (7, 0.012)], ridge=False, colour=(200, 175, 125),
                  sky=(210, 225, 240)),
    "ruins": dict(base=0.14, waves=[(3, 0.02), (9, 0.01)], ridge=False, towers=26, colour=(60, 55, 55),
                  sky=(210, 225, 240)),
    "sea": dict(base=0.22, waves=[(17, 0.004), (29, 0.003)], ridge=False, colour=(70, 170, 170), sky=(210, 225, 240)),
    "palms": dict(base=0.05, waves=[(2, 0.025), (5, 0.012)], ridge=False, palms=9, colour=(40, 50, 35),
                  sky=(210, 225, 240)),
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
    ap.add_argument("--quiet-join", action="store_true", help="turn the shape: the join where the outline is flattest")
    ap.add_argument("--colour", help="the layer's colour in the init image, hex RRGGBB; default the kind's")
    a = ap.parse_args()
    spec, w, h = SPEC[a.kind], a.w, a.h
    ss = 4  # supersample for antialiased edges
    W, H = w * ss, h * ss
    top = profile(spec, np.random.default_rng(a.seed), W, H)
    keep = profile(spec, np.random.default_rng(a.edges_of), W, H) if a.edges_of is not None else top
    if a.quiet_join:
        turn = quiet(keep, ss)
        top, keep = np.roll(top, -turn), np.roll(keep, -turn)
    if a.edges_of is not None:
        # The variant's middle, the kept seed's ends, blended over `edge` px: any variant of
        # that seed then follows any other in a SEQUENCE layer, the shape running on at the join.
        x = np.arange(W)
        d = np.minimum(x, W - 1 - x) / ss
        r = np.clip((d - a.edge) / a.edge, 0, 1)
        top = keep * (1 - r) + top * r
    yy = H - np.arange(H)[:, None]  # height of each row from the bottom
    mask = (yy <= top[None, :]).astype(np.float32)
    if "palms" in spec:
        turn = quiet(keep, ss) if a.quiet_join else 0  # top and keep were turned by it: the palms turn with them
        own = palms(spec, np.random.default_rng(a.seed), W, H, top)
        if a.edges_of is not None:
            # The kept seed's palms at both ends, this seed's in the middle: a palm is the end's or the middle's whole.
            kept = palms(spec, np.random.default_rng(a.edges_of), W, H, keep)
            end = lambda cx: min((cx - turn) % W, W - (cx - turn) % W) / ss  # noqa: E731
            own = [p for p in kept if end(p[0]) < 2 * a.edge] + [p for p in own if end(p[0]) >= 3 * a.edge]
        mask = np.maximum(mask, draw_palms(own, W, H, turn))
    mask = mask.reshape(h, ss, w, ss).mean(axis=(1, 3))
    Image.fromarray((mask * 255).astype(np.uint8)).save(f"{a.out}_mask.png")
    sky = np.array(spec["sky"], np.float32)
    shade = np.linspace(0.85, 1.1, h)[:, None, None]  # darker at the top of the layer, as haze lifts
    rgb = tuple(int(a.colour[i:i + 2], 16) for i in (0, 2, 4)) if a.colour else spec["colour"]
    col = np.array(rgb, np.float32) * shade
    init = sky * (1 - mask[..., None]) + col * mask[..., None]
    Image.fromarray(init.clip(0, 255).astype(np.uint8)).save(f"{a.out}_init.png")


def palms(spec, rng, W, H, top):
    """[(cx, lean, height, spread, fronds, phase)] for the kind's palms, cx in supersampled px before any turn."""
    out = []
    for _ in range(spec["palms"]):
        cx = rng.uniform(0, W)
        out.append((cx, rng.uniform(-0.35, 0.35), rng.uniform(0.32, 0.55) * H, rng.uniform(0.045, 0.07) * W,
                    int(rng.integers(6, 9)), rng.uniform(0, np.pi)))
    return out


def draw_palms(ps, W, H, turn):
    """The palms as a (H, W) 0..1 mask, each drawn at cx - turn and a width either side (the strip loops)."""
    from PIL import ImageDraw
    img = Image.new("L", (W, H), 0)
    g = ImageDraw.Draw(img)
    for cx, lean, height, spread, fronds, phase in ps:
        for shift in (-W, 0, W):
            x0 = (cx - turn) % W + shift
            # The trunk: a curve leaning one way, thinner toward the crown, from below the ground line.
            n = 24
            t = np.linspace(0, 1, n)
            xs = x0 + lean * height * t ** 1.6
            ys = H - 0.08 * H - height * t
            wd = 0.012 * W * (1 - 0.5 * t)
            left = list(zip(xs - wd / 2, ys))
            right = list(zip(xs + wd / 2, ys))[::-1]
            g.polygon(left + right + [(xs[0] - wd[0], H), (xs[0] + wd[0], H)], fill=255)
            # The crown: fronds that rise and droop, a thin leaf each.
            hx, hy = xs[-1], ys[-1]
            for k in range(fronds):
                ang = phase + 2 * np.pi * k / fronds
                dx = np.cos(ang)
                u = np.linspace(0, 1, 16)
                fx = hx + dx * spread * u
                fy = hy - (0.35 - abs(dx) * 0.1) * spread * np.sin(np.pi * u * 0.8) + 0.5 * spread * u ** 2
                th = 0.10 * spread * np.sin(np.pi * np.clip(u * 1.1, 0, 1)) + 1
                g.polygon(list(zip(fx, fy - th)) + list(zip(fx, fy + th))[::-1], fill=255)
            g.ellipse([hx - 0.04 * spread, hy - 0.04 * spread, hx + 0.04 * spread, hy + 0.04 * spread], fill=255)
    return np.asarray(img, np.float32) / 255


def quiet(top, ss):
    """The column where the outline is flattest over 16 px around it: the shape loops, so it can start there."""
    slope = np.abs(np.roll(top, -1) - top)
    k = 16 * ss
    window = sum(np.roll(slope, i) for i in range(-k // 2, k // 2))
    return int(np.argmin(window))


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
    if "towers" in spec:
        xs = np.arange(W)
        for _ in range(spec["towers"]):
            cx, tw = rng.uniform(0, W), rng.uniform(0.012, 0.035) * W
            th = rng.uniform(0.12, 0.5) * H
            d = (xs - cx + W / 2) % W - W / 2  # signed distance on the cylinder
            inside = np.abs(d) < tw
            # A broken top: a slant from one side to the other, and a notch or two bitten out of it.
            roof = th * (1 - rng.uniform(0, 0.45) * (d / tw + 1) / 2 * rng.choice([-1, 1]) - rng.uniform(0, 0.1))
            for _ in range(int(rng.integers(0, 3))):
                nc, nw = rng.uniform(-tw, tw), rng.uniform(0.15, 0.4) * tw
                roof = np.where(np.abs(d - nc) < nw, roof - rng.uniform(0.05, 0.2) * th, roof)
            top = np.where(inside, np.maximum(top, top.min() + roof), top)
    return top


if __name__ == "__main__":
    main()
