#!/usr/bin/env python3
"""From one painting to a page (r279): estimate its depth, cut it into depth bands, repaint what each band hid, and
write the atlas and a .jplax whose speeds follow the bands' depths.

    tools/depth-page.sh PAINTING.png OUT [--layers 4] [--theme "green valley, pines"] [--front-speed 0.08]

docs/depth-tricks.md §5. Passes:
  1. the loop: a page's layers tile along X, so the painting must end where it starts. A painting whose join shows
     (r239's seam ratio over 2) has a band round its join repainted (gen.py inpaint, tile x). Paint one that loops
     from the start with gen.py txt2img --tile x (docs/ai-parallax.md);
  2. depth: Depth Anything V2 Small (Hugging Face depth-anything/Depth-Anything-V2-Small-hf, Apache-2.0, ~100 MB in
     ~/.cache/huggingface), run on the painting padded with its own other end, so the depth loops too. Its output is
     relative disparity: larger is nearer;
  3. bands: --layers clusters of that disparity (k-means in one dimension), far to near, small islands merged into
     what surrounds them, then a colour pass that pulls into a nearer band the thin parts of it the model judged far
     (a branch), and a small patch standing above its band's top edge handed to the farther band round it. A layer
     holds its own band, and every nearer pixel under its band's top edge (a tree's trunk in front of the hills): the
     layer stays solid to its bottom, as a painted strip is. Over columns where a nearer thing hides the band, its top
     edge is the lower of its two sides;
  4. holes: those nearer pixels, grown by --grow px over the soft edge depth leaves, are filled with the layer's own
     colours and repainted by dreamshaper_8's inpainting model (gen.py inpaint, tile x). The camera scrolls past
     every one of them.
  Every step that looks across the painting (depth, islands, fill) sees it as the tiled layer shows it, its two
  ends joined: else a band's edge steps at the join, lint fault (e).
  5. the page: every layer the painting's full width at sizeRatio 1, bottom on the screen's bottom, cut 2 rows
     above its first row with art; speeds proportional to each band's median disparity, the front at --front-speed,
     none under a twentieth of it. The back layer is opaque (the painted sky); the gradients take its top and bottom
     colours.

Writes OUT/recipe.json (the command and each pass, for tools/ai-gallery.sh), OUT/cut (source.png, depth.png, bands.png, each layer before and after the repaint, sheet.png), OUT/page (NAME.atlas,
NAME.png, NAME.jplax, round.json) and, unless --no-shots, OUT/shots (tools/parallax-lab-shots.sh, cage: stills at 0,
6 and 12 s). Exits 1 when lint finds a fault. Runs in the ComfyUI venv (COMFYUI_DIR, default ~/ComfyUI), which holds
transformers, scipy and torch; needs --ckpt in its models/checkpoints (dreamshaper_8Inpainting.safetensors, Civitai
model version 131004, ~2 GB: a model that sees the mask and the painting round it, r315) and ~2 GB of free VRAM.
"""
import argparse
import json
import os
import re
import shlex
import subprocess
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
AI = os.path.join(TOOLS, "r239-ai-strips")
sys.path.insert(0, TOOLS)
sys.path.insert(0, AI)
import make_page  # noqa: E402
import parallax_lab as lab  # noqa: E402
import seam  # noqa: E402

ROOT = lab.ROOT
MODEL = "depth-anything/Depth-Anything-V2-Small-hf"
NEG = ("text, watermark, frame, border, people, birds, flying bird, animals, tree in the sky, close-up, "
       "foreground object, blurry")
WORDS = ["far distance", "middle distance", "near", "foreground"]  # the inpaint prompt's depth, back to front


def log(s):
    print(s, file=sys.stderr, flush=True)


def gen(args, logfile):
    """gen.py in a subprocess: it loads ComfyUI, whose models and argv parsing stay out of this process."""
    with open(logfile, "w") as f:
        code = subprocess.run([sys.executable, os.path.join(AI, "gen.py")] + args, stdout=f, stderr=subprocess.STDOUT,
                              cwd=ROOT).returncode
    if code:
        sys.exit(f"gen.py failed (free VRAM? nvidia-smi): {logfile}")


def depth_of(img):
    """Relative disparity per pixel (larger = nearer), estimated on the painting padded along X with its own other
    end: the network sees across the join, and the depth loops as the painting does."""
    import torch
    from transformers import pipeline
    h, w, _ = img.shape
    pad = w // 4
    wrap = np.concatenate([img[:, -pad:], img, img[:, :pad]], axis=1)
    est = pipeline("depth-estimation", model=MODEL, device=0 if torch.cuda.is_available() else -1)
    d = est(Image.fromarray(wrap))["predicted_depth"]
    d = torch.nn.functional.interpolate(d[None, None].float(), size=wrap.shape[:2], mode="bilinear")[0, 0]
    out = d.cpu().numpy()[:, pad:pad + w]
    # The two ends are w apart in what the network saw, and it judged them a little differently: on gently sloping
    # ground that moves a band's edge by rows across the join. Each row's last 96 columns are bent up and its first
    # 96 down, each by half its ends' difference (smoothed over 2 rows), so they meet. Bent across the whole width, a
    # row whose ends differ much (a tree at one end) draws a streak through the painting.
    step = ndimage.gaussian_filter1d(out[:, -1] - out[:, 0], 2)
    x = np.arange(w)
    edge = min(96, w // 4)
    left = np.clip(1 - x / edge, 0, 1)
    right = np.clip(1 - (w - 1 - x) / edge, 0, 1)
    out = out + step[:, None] * (left / 2)[None] - step[:, None] * (right / 2)[None]
    del est
    torch.cuda.empty_cache()  # dreamshaper needs the memory next
    return out


def bands(d, n, min_area):
    """Labels 0 (far) .. n-1 (near): k-means of the disparity in one dimension, then a 5 px majority filter and
    islands under min_area pixels merged into the label around them, both on the painting laid three times side by
    side: the middle copy's left and right edges then agree, and the layers loop."""
    v = d.ravel()
    centres = np.percentile(v, (np.arange(n) + 0.5) * 100 / n)
    for _ in range(50):
        lab_ = np.argmin(np.abs(v[:, None] - centres[None]), axis=1)
        new = np.array([v[lab_ == k].mean() if (lab_ == k).any() else centres[k] for k in range(n)])
        if np.allclose(new, centres):
            break
        centres = new
    order = np.argsort(centres)
    L = np.argsort(order)[np.argmin(np.abs(d[..., None] - centres[None, None]), axis=2)]
    w = L.shape[1]
    L = ndimage.median_filter(np.tile(L, 3), size=5, mode="nearest")  # three copies: an island across the join is whole
    for _ in range(3):
        changed = False
        for k in range(n):
            comp, count = ndimage.label(L == k)
            if not count:
                continue
            sizes = ndimage.sum(np.ones_like(L), comp, index=np.arange(1, count + 1))
            for i in np.nonzero(sizes < min_area)[0]:
                isl = comp == i + 1
                ring = ndimage.binary_dilation(isl, iterations=2) & ~isl
                if ring.any():
                    L[isl] = np.bincount(L[ring], minlength=n).argmax()
                    changed = True
        if not changed:
            break
    # A patch of a farther band shut in by nearer ones (4 in 5 of its rim), but the back one, is the sky between a
    # crown's leaves judged half near: kept, it lifts its band's top edge to the crown, and that layer carries a piece
    # of the tree. It joins what shuts it in. The back band's own patches are true gaps, seen through.
    for k in range(1, n - 1):
        comp, count = ndimage.label(L == k)
        for i in range(1, count + 1):
            isl = comp == i
            ring = ndimage.binary_dilation(isl, iterations=2) & ~isl
            if ring.any() and (L[ring] > k).mean() >= 0.8:
                L[isl] = np.bincount(L[ring], minlength=n).argmax()
    return L[:, w:2 * w], centres[order]


def by_colour(L, img, n, reach=16, window=41, rounds=8, margin=0.6, apart=40):
    """The small model misses a thin branch, and leaves it in the band behind: within reach px of a nearer band, a
    pixel moves to it when it matches that band's mean colour round it (window px, wrapping along X) better, by
    margin, than its own band's, and the two means are apart by more than apart (0-441). A few rounds let a branch
    fill in from the crown it hangs from. Only toward nearer bands, and only where the bands differ in colour: on
    gently sloping ground both sides of a cut are the same green, and texture alone would fray the cut.
    """
    rgb = img.astype(np.float32)
    mode = ("nearest", "wrap", "nearest")
    for _ in range(rounds):
        means = []
        for k in range(n):
            m = (L == k).astype(np.float32)
            cnt = ndimage.uniform_filter(m, window, mode=("nearest", "wrap"))
            sm = ndimage.uniform_filter(rgb * m[..., None], (window, window, 1), mode=mode)
            means.append((sm / np.maximum(cnt, 1e-6)[..., None], cnt))
        moved = 0
        for k in range(n):
            near_k = grow(L == k, reach) & (L != k) & (means[k][1] > 0.05)
            own = np.take_along_axis(np.stack([m for m, _ in means]), L[None, ..., None].repeat(3, -1), 0)[0]
            d_own = np.linalg.norm(rgb - own, axis=-1)
            d_k = np.linalg.norm(rgb - means[k][0], axis=-1)
            go = near_k & (L < k) & (d_k < margin * d_own) & (np.linalg.norm(own - means[k][0], axis=-1) > apart)
            L = np.where(go, k, L)
            moved += int(go.sum())
        if not moved:
            break
    return L


def topline(L, k):
    """Per column, the first row of band k. A run of columns without it (a nearer thing stands there, or right above
    its first row) takes the lower of the two rows either side: a line drawn between them, from tall pines on one side
    to low ground on the other, leaves a tall thin hole that the repaint fills with a spike of the nearer thing's
    shape, a wedge where the pines are short (r322)."""
    h, w = L.shape
    has = (L == k).any(axis=0)
    first = np.argmax(L == k, axis=0)
    if not has.any():
        return np.full(w, float(h))
    # A first row with a nearer band right above it is where the nearer thing ends, not where band k begins: kept, a
    # strip of grass under a trunk sets the run's lower side, and the layer's edge steps down beside the hill (r322).
    hidden = (first > 0) & (L[np.maximum(first - 1, 0), np.arange(w)] > k)
    if (has & ~hidden).any():
        has &= ~hidden
    first = first.astype(float)
    out = first.copy()
    x = 0
    start = int(np.argmax(has))  # walk from a column that has the band, so every run has both sides
    while x < w:
        c = (start + x) % w
        if has[c]:
            x += 1
            continue
        run = 0
        while not has[(c + run) % w]:
            run += 1
        low = max(first[(c - 1) % w], first[(c + run) % w])
        for i in range(run):
            out[(c + i) % w] = low
        x += run
    return out


def strays(L, n, area):
    """A patch of band k under area px, apart from its band's mass and standing above that band's top edge (taken
    without it), joins the farther band round it. Between the pines and the trunk in front of them, depth's soft edge
    leaves such a patch of the band in between: kept, it lifts that layer's top edge over the trunk's columns, and the
    trunk under it becomes a tall hole, repainted as a flat box standing above the hill (r322). Back to front, so a
    patch handed back can be handed back again; on the painting laid three times side by side, for the join."""
    w = L.shape[1]
    L = np.tile(L, 3)
    for k in range(n - 2, 0, -1):
        comp, count = ndimage.label(L == k)
        if count < 2:
            continue
        sizes = ndimage.sum(np.ones(L.shape), comp, index=np.arange(1, count + 1))
        small = np.isin(comp, 1 + np.nonzero(sizes < area)[0])
        top = topline(np.where(small, -1, L), k)
        for i in np.unique(comp[small]):
            isl = comp == i
            ys, xs = np.nonzero(isl)
            if not (ys < top[xs] - 8).any():
                continue
            ring = ndimage.binary_dilation(isl, iterations=2) & ~isl & (L < k)
            if ring.any():
                L[isl] = np.bincount(L[ring], minlength=n).argmax()
    return L[:, w:2 * w]


def looped(fn, *arrays):
    """fn run on each array laid three times side by side, its middle copy kept: whatever fn does across the join
    (labels, a pyramid, distances) it does as the tiled layer shows it."""
    w = arrays[0].shape[1]
    reps = [(1, 3) + (1,) * (a.ndim - 2) for a in arrays]
    return fn(*[np.tile(a, r) for a, r in zip(arrays, reps)])[:, w:2 * w]


def grow(mask, px):
    """Dilated by px, wrapping along X."""
    if px <= 0:
        return mask
    pad = np.concatenate([mask[:, -px:], mask, mask[:, :px]], axis=1)
    return ndimage.binary_dilation(pad, iterations=px)[:, px:-px]


def fill(img, known):
    """Unknown pixels filled with a start of the right colour for the repaint, where a grey start would be painted
    grey: next to the known ones, a blur of them (push-pull over a pyramid); deeper in, the median of the known pixels
    of the same rows, so a sky keeps its gradient and is not averaged with the clouds and hills round it."""
    rgb = img.astype(np.float32) * known[..., None]
    wgt = known.astype(np.float32)
    levels = [(rgb, wgt)]
    while min(levels[-1][1].shape) > 2:
        r, w_ = levels[-1]
        hh, ww = (r.shape[0] // 2) * 2, (r.shape[1] // 2) * 2
        r = r[:hh, :ww].reshape(hh // 2, 2, ww // 2, 2, 3).sum(axis=(1, 3))
        w_ = w_[:hh, :ww].reshape(hh // 2, 2, ww // 2, 2).sum(axis=(1, 3))
        levels.append((r, w_))
    up = None
    for r, w_ in reversed(levels):
        col = r / np.maximum(w_, 1e-6)[..., None]
        if up is not None:
            big = np.asarray(Image.fromarray(up.clip(0, 255).astype(np.uint8)).resize(
                (col.shape[1], col.shape[0]), Image.BILINEAR)).astype(np.float32)
            a = np.clip(w_, 0, 1)[..., None]
            col = col * a + big * (1 - a)
        up = col
    h = img.shape[0]
    rows = np.empty((h, 3), np.float32)
    for y in range(h):
        band = known[max(0, y - 8):y + 9]
        if band.mean() > 0.05:
            rows[y] = np.median(img[max(0, y - 8):y + 9][band], axis=0)
        else:
            rows[y] = np.nan
    good = ~np.isnan(rows[:, 0])
    if good.any():
        for c in range(3):
            rows[:, c] = np.interp(np.arange(h), np.nonzero(good)[0], rows[good, c])
        dist = ndimage.distance_transform_edt(~known)
        t = np.exp(-dist / 24)[..., None]
        up = up * t + rows[:, None, :] * (1 - t)
    out = img.astype(np.float32).copy()
    out[~known] = up[~known]
    return out


def islands(mask, area, beside):
    """The parts of mask under area pixels that touch beside."""
    comp, count = ndimage.label(mask)
    if count < 2:
        return np.zeros_like(mask)
    sizes = ndimage.sum(np.ones(mask.shape), comp, index=np.arange(1, count + 1))
    small = 1 + np.nonzero(sizes < area)[0]
    touching = np.unique(comp[ndimage.binary_dilation(beside) & mask])
    return np.isin(comp, np.intersect1d(small, touching))


def colour_bands(L, n):
    pal = np.array([(70, 90, 160), (90, 160, 120), (220, 180, 80), (220, 90, 70), (200, 80, 200), (240, 240, 240)])
    return Image.fromarray(pal[L % len(pal)].astype(np.uint8))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("painting")
    ap.add_argument("out")
    ap.add_argument("--layers", type=int, default=4, help="depth bands, 2-6 (lint wants 3 or more)")
    ap.add_argument("--theme", default="landscape painting", help="what the painting shows: the repaint's prompt")
    ap.add_argument("--front-speed", type=float, default=0.08)
    ap.add_argument("--grow", type=int, default=6, help="px a hole reaches past the nearer band: depth's soft edge")
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--denoise", type=float, default=0.8, help="the repaint's strength over the filled hole")
    ap.add_argument("--ckpt", default="dreamshaper_8Inpainting.safetensors", help="the repaint's checkpoint")
    ap.add_argument("--name", help="the atlas and page name; default the painting's")
    ap.add_argument("--keep", action="store_true", help="keep OUT/cut's repainted layers: only lay the page out again")
    ap.add_argument("--no-shots", action="store_true")
    a = ap.parse_args()
    if not 2 <= a.layers <= 6:
        ap.error("--layers: 2 to 6")
    out = os.path.abspath(a.out)
    cut, page_dir = os.path.join(out, "cut"), os.path.join(out, "page")
    os.makedirs(cut, exist_ok=True)
    os.makedirs(page_dir, exist_ok=True)
    name = a.name or re.sub(r"[^a-z0-9]+", "-", os.path.splitext(os.path.basename(a.painting))[0].lower()).strip("-")
    n = a.layers

    im = Image.open(a.painting).convert("RGB")
    w, h = (im.width // 8) * 8, (im.height // 8) * 8  # the VAE's cell
    im = im.crop((0, 0, w, h))
    im.save(os.path.join(cut, "painting.png"))
    im.save(os.path.join(cut, "source.png"))  # painting.png is the one with its join repainted, when it is
    img = np.asarray(im)
    recipe = {"tool": "depth-page", "command": shlex.join(["tools/depth-page.sh"] + [x for x in sys.argv[1:]
                                                                                    if x not in ("--keep", "--no-shots")]),
              "theme": a.theme, "name": name, "seed": a.seed, "size": [w, h], "denoise": a.denoise,
              "model": f"Depth Anything V2 Small + SD 1.5 {a.ckpt} (repaints)", "page": f"page/{name}.jplax",
              "negative": NEG, "fogStrength": 0, "layers": []}

    # 1. The loop.
    ratio, _ = seam.join(seam.load(os.path.join(cut, "painting.png")), seam.load(os.path.join(cut, "painting.png")))
    log(f"painting {w}x{h}: seam ratio {ratio:.2f}")
    recipe["loop"] = {"before": round(float(ratio), 2), "after": None}
    if ratio > 2:
        rolled = np.roll(img, w // 2, axis=1)  # the join in the middle, where the repaint has both sides
        x = np.arange(w)
        m = np.clip(1 - (np.abs(x - w // 2) - 48) / 16, 0, 1)
        mask = np.tile(m, (h, 1))
        known = mask < 0.5  # the ramp keeps the painting under it: filled, it would blend a blur into both ends
        Image.fromarray(fill(rolled, known).round().clip(0, 255).astype(np.uint8)).save(f"{cut}/loop_src.png")
        Image.fromarray((mask * 255).astype(np.uint8)).save(f"{cut}/loop_mask.png")
        gen(["inpaint", "--ckpt", a.ckpt, "--src", f"{cut}/loop_src.png", "--mask", f"{cut}/loop_mask.png", "--tile", "x", "--denoise",
             "0.95", "--seed", str(a.seed), "--w", str(w), "--h", str(h), "--negative", NEG,
             "--prompt", f"{a.theme}, painterly, seamless", "--out", f"{cut}/loop.png"], f"{cut}/loop.log")
        img = np.roll(np.asarray(Image.open(f"{cut}/loop.png").convert("RGB")), -(w // 2), axis=1)
        Image.fromarray(img).save(os.path.join(cut, "painting.png"))
        p = os.path.join(cut, "painting.png")
        recipe["loop"]["after"] = round(float(seam.join(seam.load(p), seam.load(p))[0]), 2)
        recipe["loop"]["prompt"] = f"{a.theme}, painterly, seamless"
        log(f"join repainted: seam ratio {recipe['loop']['after']:.2f}")

    # 2. Depth, 3. bands.
    d = depth_of(img)
    lo, hi = np.percentile(d, (1, 99.5))
    dn = np.clip((d - lo) / (hi - lo), 0, 1)
    Image.fromarray((dn * 255).astype(np.uint8)).save(os.path.join(cut, "depth.png"))
    L, centres = bands(dn, n, min_area=w * h // 150)
    L = by_colour(L, img, n)
    L = strays(L, n, w * h // 150)
    colour_bands(L, n).save(os.path.join(cut, "bands.png"))
    log("bands, far to near: " + ", ".join(f"{k}: disparity {centres[k]:.2f}, {(L == k).mean():.0%} of the pixels"
                                          for k in range(n)))

    # 4. Each layer and its hole.
    rows = np.arange(h)[:, None]
    layers = []
    for k in range(n):
        alpha = np.ones((h, w), bool) if k == 0 else (L == k) | ((rows >= topline(L, k)[None]) & (L > k))
        hole = alpha & grow(L > k, a.grow) if k < n - 1 else np.zeros_like(alpha)
        # The sky between a crown's branches is band k too, tinted by the leaves round it: left as a start for the
        # fill, the repaint grows a small tree there. Islands of the layer's own pixels inside the hole join it.
        if hole.any():
            hole |= looped(lambda m, o: islands(m, w * h // 150, o), alpha & ~hole, hole)
        cut_k = os.path.join(cut, f"l{k}")
        src = looped(fill, img, alpha & ~hole)
        Image.fromarray(np.dstack([img, alpha * 255]).astype(np.uint8)).save(f"{cut_k}_raw.png")
        done = f"{cut_k}_painted.png"
        if hole.any() and not (a.keep and os.path.exists(done)):
            Image.fromarray(src.round().clip(0, 255).astype(np.uint8)).save(f"{cut_k}_src.png")
            soft = ndimage.gaussian_filter(grow(hole, 2).astype(np.float32), 1.5, mode=("nearest", "wrap"))
            Image.fromarray((np.maximum(soft, hole) * 255).astype(np.uint8)).save(f"{cut_k}_mask.png")
            word = WORDS[min(3, round(k * 3 / max(1, n - 1)))]
            gen(["inpaint", "--ckpt", a.ckpt, "--src", f"{cut_k}_src.png", "--mask", f"{cut_k}_mask.png", "--tile", "x", "--denoise",
                 str(a.denoise), "--seed", str(a.seed * 100 + k), "--w", str(w), "--h", str(h), "--negative", NEG,
                 "--prompt", f"{a.theme}, {word} scenery, painterly, seamless", "--out", done], f"{cut_k}_gen.log")
            log(f"layer {k}: {hole.mean():.0%} of the painting repainted")
        elif not hole.any():
            Image.fromarray(img).save(done)
        rgb = np.asarray(Image.open(done).convert("RGB"))
        # A 1 px soft edge where the layer gives way to a farther band: the cut's own line would alias.
        soft_a = alpha.astype(np.float32)
        if k:
            soft_a = np.minimum(soft_a, ndimage.gaussian_filter(soft_a, 0.6, mode=("nearest", "wrap")) * 1.15)
        rgba = np.dstack([rgb, (np.clip(soft_a, 0, 1) * 255).round()]).astype(np.uint8)
        top = max(0, int(np.argmax(alpha.any(axis=1))) - 2)  # 2 clear rows: a pine's tip on the top row reads cut, (a)
        Image.fromarray(rgba[top:]).save(f"{cut_k}.png")
        med = float(np.median(dn[L == k])) if (L == k).any() else centres[k]
        layers.append({"path": f"{cut_k}.png", "disparity": med, "top": top, "hole": float(hole.mean())})
        word = WORDS[min(3, round(k * 3 / max(1, n - 1)))]
        recipe["layers"].append({"strip": f"l{k}", "disparity": round(med, 3), "top": top,
                                 "repainted": round(float(hole.mean()), 4), "seed": a.seed * 100 + k,
                                 "prompt": f"{a.theme}, {word} scenery, painterly, seamless" if hole.any() else None})

    # 5. The page.
    front = max(layers[-1]["disparity"], 1e-3)
    s, prev = [], 0.0
    for k, ly in enumerate(layers):
        v = max(a.front_speed * ly["disparity"] / front, a.front_speed / 20, prev * 1.25 if prev else 0)
        s.append(round(min(v, a.front_speed), 5))
        prev = v
    s[-1] = a.front_speed
    make_page.pack(name, [(f"l{k}", ly["path"]) for k, ly in enumerate(layers)], "Linear,Linear", out=page_dir)
    page_layers = [lab.layer(f"l{k}", speed=s[k], dy=0.0) for k in range(n)]
    sky = (seam_colour(img[:4]), seam_colour(img[-4:]))
    page = lab.page_of(f"{name}.atlas", page_layers, sky=(sky[0], sky[0]), ground=(sky[1], sky[1]), top_size=0.5,
                       bottom_size=0.5)
    with open(os.path.join(page_dir, f"{name}.jplax"), "w") as f:
        json.dump(page, f, indent=1)
    rel = os.path.relpath(page_dir, ROOT)
    about = (f"r279 depth-page: {os.path.basename(a.painting)} cut into {n} depth bands, speeds "
             + ", ".join(f"{v:g}" for v in s))
    scene = {"id": "d01", "page": f"{rel}/{name}.jplax", "atlasDir": rel, "name": name, "about": about}
    with open(os.path.join(page_dir, "round.json"), "w") as f:
        json.dump({"round": name, "about": about, "scenes": [scene]}, f, indent=1)
    sheet(cut, layers, n)

    problems = lab.lint(lab.load(scene["page"]), rel)
    for k, ly in enumerate(recipe["layers"]):
        ly["speed"] = s[k]
    recipe.update(lint=problems, sky=list(sky))
    with open(os.path.join(out, "recipe.json"), "w") as f:
        json.dump(recipe, f, indent=1)
    print(f"{rel}/{name}.jplax")
    for k, ly in enumerate(layers):
        print(f"  l{k}: disparity {ly['disparity']:.2f}, speed {s[k]:g}, from row {ly['top']}, "
              f"{ly['hole']:.0%} repainted")
    print("\n".join("  ! " + p for p in problems) or "  lint ok")
    if not a.no_shots:
        subprocess.run([os.path.join(TOOLS, "parallax-lab-shots.sh"), page_dir, os.path.join(out, "shots")],
                       cwd=ROOT, check=False)
    return 1 if problems else 0


def seam_colour(rows):
    return "".join(f"{int(v):02x}" for v in np.median(rows.reshape(-1, 3), axis=0))


def sheet(cut, layers, n):
    """The painting, its depth, its bands, then each layer raw (the hole see-through) and repainted, on a checker."""
    paint = Image.open(os.path.join(cut, "painting.png"))
    w, h = paint.size
    tw, th = w // 2, h // 2
    check = np.indices((th, tw)).sum(axis=0) // 8 % 2
    bg = Image.fromarray(np.where(check[..., None], 200, 150).repeat(3, axis=2).astype(np.uint8))
    tiles = [paint, Image.open(os.path.join(cut, "depth.png")).convert("RGB"),
             Image.open(os.path.join(cut, "bands.png"))]
    for k in range(n):
        for f in (f"l{k}_raw.png", f"l{k}.png"):
            im = Image.open(os.path.join(cut, f)).convert("RGBA")
            full = Image.new("RGBA", (w, h))
            full.paste(im, (0, h - im.height))
            t = bg.copy()
            t.paste(full.resize((tw, th)), (0, 0), full.resize((tw, th)))
            tiles.append(t)
    cols = 3
    out = Image.new("RGB", (cols * tw, -(-len(tiles) // cols) * th), "black")
    for i, t in enumerate(tiles):
        out.paste(t.convert("RGB").resize((tw, th)), ((i % cols) * tw, (i // cols) * th))
    out.save(os.path.join(cut, "sheet.png"))


if __name__ == "__main__":
    sys.exit(main())
