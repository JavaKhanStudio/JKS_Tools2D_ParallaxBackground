"""r498: occlusion on the river page, as takes Simon can pick from, drawn by the real reader and scrolled.
r493 judged one try at 0.5 on the whole painting and only reasoned the slide-off; this runs it. Nothing is wired in.

    ~/ComfyUI/venv/bin/python tools/r498-occlusion-takes/takes.py OUT [--weak 0.2] [--band 0.35]
    tools/parallax-lab-shots.sh OUT/round OUT/shots
    ~/ComfyUI/venv/bin/python tools/r498-occlusion-takes/takes.py OUT --sheets

OUT/round holds one scene per take, each a copy of build/ai/depth/river/page with its atlas image shaded:
n0 no occlusion   w5 r493's 0.5 on the whole painting (the rejected one: proof, not an option)
w2 a weaker strength, the same mask   b3 measured inside each band's own depth
Only a band's own pixels are shaded, so at rest w5 and w2 are r493's still; the repainted parts behind stay as painted.
"""
import argparse
import importlib
import json
import os
import shutil
import sys

import numpy as np
from PIL import Image, ImageDraw
from scipy import ndimage as ni

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
sys.path.insert(0, os.path.join(ROOT, "tools", "r493-material-ideas"))
tried = importlib.import_module("try")  # cavity and blur as r493 judged them
RIVER = os.path.join(ROOT, "build/ai/depth/river")
PAL = np.array([(70, 90, 160), (90, 160, 120), (220, 180, 80), (220, 90, 70)])  # depth_page.colour_bands
HALO = 14  # depth_page.HALO: how far depth's soft edge round a nearer thing reaches into what stands beside it
SCALES = (3, 9, 27)  # r493's, the whole-painting mask
OWN_SCALES = (3, 9)  # a band's: at 27 its own outline reads as one big hollow, a muddy ring on the lit field
FADE = 24  # px over which a band's mask comes in past HALO
NEAR_REACH = 60  # px: beside a NEARER band depth's ramp reaches this far on the river's meadow (mask at 14-60 px read 9-17%)
SMOOTH = 2.5  # px, the depth before a band's hollows are measured
NAME = "paint2"


def load(p, mode="RGB"):
    return np.asarray(Image.open(p).convert(mode), float)


def labels():
    b = load(f"{RIVER}/cut/bands.png")
    return np.argmin(((b[..., None, :] - PAL) ** 2).sum(-1), -1)


def regions():
    """{name: (x, y, w, h)} of the atlas's regions."""
    out, name, xy = {}, None, None
    with open(f"{RIVER}/page/{NAME}.atlas") as f:
        lines = f.read().splitlines()[5:]
    for line in lines:
        if not line.startswith(" "):
            name = line.strip()
        elif "xy:" in line:
            xy = [int(v) for v in line.split(":")[1].split(",")]
        elif "size:" in line:
            out[name] = (*xy, *[int(v) for v in line.split(":")[1].split(",")])
    return out


def nearer_distance(L, k):
    """Each pixel's distance to a band nearer than k, measured with the painting laid three times: the page loops."""
    w = L.shape[1]
    return ni.distance_transform_edt(np.tile(L <= k, (1, 3)))[:, w:2 * w]


def edge_distance(L, k):
    """Each pixel of band k's distance to any other band, the same way."""
    w = L.shape[1]
    return ni.distance_transform_edt(np.tile(L == k, (1, 3)))[:, w:2 * w]


def own_mask(d, L, k):
    """Occlusion inside band k: each scale's mean over band k's pixels only, so nothing nearer darkens it, and none
    within depth's soft edge of another band, whose ramp reads as a hollow: beside a nearer band it is the halo on the
    meadow, at a farther one a dark rim on the band's own top. Depth is 8 bits, smoothed or its terraces show as combing.
    The back band is none: its "hollows" are the sky over the mountains' ridge."""
    if k == 0:
        return np.zeros(d.shape)
    m = (L == k).astype(float)
    smooth = tried.blur(d * m, SMOOTH) / np.maximum(tried.blur(m, SMOOTH), 1e-3)
    c = np.zeros(d.shape)
    for s in OWN_SCALES:
        mean = tried.blur(smooth * m, s) / np.maximum(tried.blur(m, s), 1e-3)
        c += np.maximum(mean - smooth, 0) * m
    c *= np.clip((edge_distance(L, k) - HALO) / FADE, 0, 1)
    c *= np.clip((nearer_distance(L, k) - NEAR_REACH) / FADE, 0, 1)
    return np.clip(c / max(np.percentile(c[m > 0], 99), 1e-6), 0, 1)


def edge_vs_inside(c, L):
    """Mean shade of the pixels just beside a nearer band against the band's others: a halo is the first well over."""
    edge, inside = [], []
    for k in range(4):
        near, own = nearer_distance(L, k) <= NEAR_REACH, L == k
        edge.append(c[near & own])
        inside.append(c[~near & own])
    return float(np.concatenate(edge).mean()), float(np.concatenate(inside).mean())


def write_take(out, take, about, per_band, atlas, boxes, L):
    """A round scene whose atlas has each layer's own pixels shaded by per_band(k): (mask, strength)."""
    page = f"{out}/round/{take}"
    os.makedirs(page, exist_ok=True)
    for ext in ("atlas", "jplax"):
        shutil.copy(f"{RIVER}/page/{NAME}.{ext}", page)
    done = atlas.copy()
    for k in range(4):
        x, y, w, h = boxes[f"l{k}"]
        top = 576 - h
        c, s = per_band(k)
        own = (L[top:] == k)
        done[y:y + h, x:x + w, :3] *= 1 - s * (c[top:] * own)[..., None]
    Image.fromarray(done.round().astype(np.uint8), "RGBA").save(f"{page}/{NAME}.png")
    return {"id": take, "page": f"{page}/{NAME}.jplax", "atlasDir": page, "name": NAME, "about": about}


def make(a):
    d, L = load(f"{RIVER}/cut/depth.png", "L"), labels()
    atlas, boxes = load(f"{RIVER}/page/{NAME}.png", "RGBA"), regions()
    whole = tried.cavity(d, SCALES)
    own = {k: own_mask(d, L, k) for k in range(4)}
    each = np.zeros(d.shape)
    for k in range(4):
        each[L == k] = own[k][L == k]
    for title, c in (("whole painting's mask", whole), ("each band's own mask", each)):
        e, i = edge_vs_inside(c, L)
        print(f"{title}: mean {100 * c.mean():.1f}%, beside a nearer band {100 * e:.1f}%, elsewhere {100 * i:.1f}%")
    Image.fromarray((255 * (1 - each)).astype(np.uint8)).save(f"{a.out}/mask-own.png")
    Image.fromarray((255 * (1 - whole)).astype(np.uint8)).save(f"{a.out}/mask-whole.png")
    scenes = [write_take(a.out, "n0", "no occlusion", lambda k: (whole, 0.0), atlas, boxes, L),
              write_take(a.out, "w2", f"whole painting's mask, {a.weak}", lambda k: (whole, a.weak), atlas, boxes, L),
              write_take(a.out, "b3", f"each band's own depth, {a.band}", lambda k: (own[k], a.band), atlas, boxes, L),
              write_take(a.out, "w5", "whole painting's mask, 0.5 (r493's)", lambda k: (whole, 0.5), atlas, boxes, L)]
    with open(f"{a.out}/round/round.json", "w") as f:
        json.dump({"round": "r498", "about": "occlusion takes on the river page", "scenes": scenes}, f, indent=1)


def sheet(path, panels, cols, crop, scale=1):
    ims = [(t, Image.open(p).convert("RGB").crop(crop)) for t, p in panels]
    w, h = ims[0][1].width * scale, ims[0][1].height * scale
    rows = (len(ims) + cols - 1) // cols
    out = Image.new("RGB", (cols * w + 6 * (cols - 1), rows * (h + 22)), "white")
    d = ImageDraw.Draw(out)
    for i, (t, im) in enumerate(ims):
        x, y = (i % cols) * (w + 6), (i // cols) * (h + 22)
        d.text((x + 60, y + 5), t, fill="black")
        out.paste(im.resize((w, h), Image.LANCZOS), (x, y + 22))
    out.save(path)


def sheets(a):
    """The three takes across, at rest and 12 s into the scroll, on the meadows where a halo shows; w5 against them."""
    s = f"{a.out}/shots"
    crop = (400, 240, 1280, 600)
    takes = (("no occlusion", "n0"), ("a weaker strength", "w2"), ("inside each band's own depth", "b3"))
    for name, t in (("rest", "t0"), ("scrolled", "t12")):
        sheet(f"{a.out}/takes-{name}.png", [(title, f"{s}/{i}-{t}.png") for title, i in takes], 3, crop)
    sheet(f"{a.out}/r493-scrolled.png", [("r493's 0.5, at rest", f"{s}/w5-t0.png"), ("r493's 0.5, 12 s into the scroll", f"{s}/w5-t12.png")],
          2, (0, 0, 1280, 720))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--weak", type=float, default=0.2)
    ap.add_argument("--band", type=float, default=0.35)
    ap.add_argument("--sheets", action="store_true")
    a = ap.parse_args()
    os.makedirs(f"{a.out}/round", exist_ok=True)
    (sheets if a.sheets else make)(a)


if __name__ == "__main__":
    main()
