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
     what surrounds them; a thing a band's edge cuts (SAM 2.1 small's masks: Hugging Face
     facebook/sam2.1-hiera-small, Apache-2.0, ~180 MB) given whole to the band at its foot (one seen only in the depth's
     ramp beside a nearer thing is hidden: to the band most of it is in, r386), and so is a field a
     band's edge cut straight across its grass, which SAM prompted with two points across that cut outlines to its
     shadow's edge, hedge or bank (r374: a field down a slope was cut in slabs); then a colour pass that
     pulls into a nearer band the thin parts of it the model judged far (a branch), its mirror handing back to the
     farther band the sky and ridge seen between a crown's branches (r361), and a small patch standing above
     its band's top edge handed to the farther band round it; last, every other piece of a layer apart from its mass
     joined to it (a leaf beside its crown) or handed to the farther band round it (a speck floating over the layer).
     A layer holds its own band, and every nearer pixel under its band's top edge (a tree's trunk in front of the
     hills): the layer stays solid to its bottom, as a painted strip is. Over columns where a nearer thing hides the
     band, its outline on each side goes on behind it, mirrored and eased from one side's height to the other's
     (r373: a clump behind a trunk no longer ends in a straight edge);
  4. holes: those nearer pixels, grown by --grow px over the soft edge depth leaves, are filled with the layer's own
     colours and repainted by dreamshaper_8's inpainting model (gen.py inpaint, tile x). The camera scrolls past
     every one of them. Before it, RULES are weighed on each layer's hole (tools/parallax_rules.py, r378): a fix
     for one case, applied only where its conditions hold (sky_twigs: the twig tips Depth Anything V2 Small judged
     as far as the sky, kept in a tree page's back layer as birds, join its hole); --rule NAME=on|off forces one.
  Clouds (r409): SAM's masks at the sky's depth, brighter than the sky round them, are the clouds. When there are
  any, the back band comes apart: the sky (its pixels at the sky's depth, the clouds in it) and the far range, cut
  as a nearer band is; the sky is filled where the clouds and every nearer thing stood (fill(), no repaint) and the
  clouds go on a layer of their own between the two, faded where a peak hid them, drifting at rest speed. The page
  then has --layers + 2 layers: sky, clouds, l0 ... (the parallax-pages skill's rule 9).
  Every step that looks across the painting (depth, islands, fill) sees it as the tiled layer shows it, its two
  ends joined: else a band's edge steps at the join, lint fault (e).
  5. the page: every layer the painting's full width at sizeRatio 1, bottom on the screen's bottom, cut 2 rows
     above its first row with art; speeds proportional to each band's median disparity, the front at --front-speed,
     none under a twentieth of it. The back layer is opaque (the painted sky, or the filled sky behind the clouds);
     the gradients take the painting's top and bottom colours.

Writes OUT/recipe.json (the command and each pass, each layer's rules weighed, for tools/ai-gallery.sh), OUT/cut (source.png, depth.png, objects.png (SAM's masks), fields.png (the prompted fields), bands.png, each layer before and after the repaint, sheet.png), OUT/page (NAME.atlas,
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
import gpu_lock  # noqa: E402
import make_page  # noqa: E402
import parallax_lab as lab  # noqa: E402
import parallax_rules  # noqa: E402
import seam  # noqa: E402

ROOT = lab.ROOT
MODEL = "depth-anything/Depth-Anything-V2-Small-hf"
SAM = "facebook/sam2.1-hiera-small"
NEG = ("text, watermark, frame, border, people, birds, flying bird, animals, tree in the sky, close-up, "
       "foreground object, blurry")
WORDS = ["far distance", "middle distance", "near", "foreground"]  # the inpaint prompt's depth, back to front
# r426: what no repaint draws: SD carried a track the layer kept on across the hole, from r387's field-coloured start,
# a second track beside the painted one once the layers slid (valley, noloop).
TRACKS = ", path, road, dirt track, trail, tyre tracks"


def log(s):
    print(s, file=sys.stderr, flush=True)


def gen(args, logfile):
    """gen.py in a subprocess: it loads ComfyUI, whose models and argv parsing stay out of this process."""
    with open(logfile, "w") as f:
        code = subprocess.run([sys.executable, os.path.join(AI, "gen.py")] + args, stdout=f, stderr=subprocess.STDOUT,
                              cwd=ROOT, check=False).returncode
    if code:
        sys.exit(f"gen.py failed (free VRAM? nvidia-smi): {logfile}")


def depth_of(img):
    """Relative disparity per pixel (larger = nearer), estimated on the painting padded along X with its own other
    end: the network sees across the join, and the depth loops as the painting does."""
    import torch
    from transformers import pipeline
    _, w, _ = img.shape
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


def masks_of(img):
    """SAM 2.1 small's automatic masks, one per thing it outlines (a pine, a hill, a cloud), each whole across the join:
    run on the painting, then on it rolled by half its width, whose masks over the middle are the ones the join cut."""
    import torch
    from transformers import pipeline
    w = img.shape[1]
    sam = pipeline("mask-generation", model=SAM, device=0 if torch.cuda.is_available() else -1)

    def run(a):
        out = sam(Image.fromarray(a), points_per_batch=64, points_per_crop=64, pred_iou_thresh=0.7,
                  stability_score_thresh=0.8)
        return [np.asarray(m, bool) for m in out["masks"]]
    keep = [m for m in run(img) if not (m[:, 0].any() or m[:, -1].any())]
    keep += [np.roll(m, -(w // 2), axis=1) for m in run(np.roll(img, w // 2, axis=1))
             if m[:, w // 2 - 1].any() or m[:, w // 2].any()]
    sam.model.to("cpu")  # dreamshaper needs the memory next
    torch.cuda.empty_cache()
    return keep


def slab_cuts(L, img, k, reach=24, apart=25):
    """Per column, the row where band k + 1 starts under band k through one surface (the same colour 2-10 px above and
    below, nothing but those two bands within reach rows): a field down a slope that depth cut in two. -1 elsewhere: a
    cut along a hedge, a tree's foot, a crown against the sky."""
    h, w = L.shape
    rgb = img.astype(np.float32)
    out = np.full(w, -1)
    for x in range(w):
        xs = [(x + d) % w for d in range(-3, 4)]
        for y in np.nonzero((L[:-1, x] == k) & (L[1:, x] == k + 1))[0] + 1:
            if not reach <= y < h - reach or not np.isin(L[y - reach:y + reach, x], (k, k + 1)).all():
                continue
            above, below = rgb[y - 10:y - 2][:, xs].reshape(-1, 3), rgb[y + 2:y + 10][:, xs].reshape(-1, 3)
            if np.linalg.norm(above.mean(0) - below.mean(0)) < apart:
                out[x] = y
                break
    return out


def fields_of(img, L, n, every=96, least=24, off=16, sure=0.4, most=0.25, stray=0.05):
    """SAM 2.1 small prompted with two points across a slab cut (slab_cuts), off px above and below it, every every px
    along a run of least columns or more: the field the cut runs through, whole, to its painted edges (a hedge, a
    shadow's edge, the river bank), which the automatic masks never give (r374). Of its three masks, the best scored of
    those that hold both points, scored sure or more, over at most most of the painting, with under stray of it outside
    the two bands; only its piece holding both points (SAM adds a ragged patch of a lit field across a trunk, valley,
    which moved left a blocky patch of the near layer in the middle of the grass). A field that reaches one end of the
    painting and not the other is asked again with a third point just across the join, on the row it reaches the end
    at: SAM stops a field at the join for one prompt and not for another, and the two halves would go to two bands
    (river's lit field). One mask per outline."""
    import torch
    from transformers import Sam2Model, Sam2Processor
    w = L.shape[1]
    prompts = []
    for k in range(n - 1):
        cut = slab_cuts(L, img, k)
        for c, run in runs_of(cut < 0):
            if run < least:
                continue
            count = max(1, round(run / every))
            for i in range(count):
                x = (c + int((i + 0.5) * run / count)) % w
                prompts.append((k, [(x, int(cut[x] - off)), (x, int(cut[x] + off))]))
    if not prompts:
        return []
    dev = "cuda" if torch.cuda.is_available() else "cpu"
    proc = Sam2Processor.from_pretrained(SAM)
    model = Sam2Model.from_pretrained(SAM).to(dev)

    def ask(k, points, shift):
        """The best-scored fitting mask for points on the painting rolled by shift, or None."""
        inp = proc(images=Image.fromarray(np.roll(img, shift, axis=1)),
                   input_points=[[[[(x + shift) % w, y] for x, y in points]]], input_labels=[[[1] * len(points)]],
                   return_tensors="pt").to(dev)
        with torch.no_grad():
            got = model(**inp, multimask_output=True)
        masks = proc.post_process_masks(got.pred_masks.cpu(), inp["original_sizes"])[0][0].numpy() > 0
        best, top = None, sure
        for m, score in zip(masks, got.iou_scores[0, 0].cpu().numpy()):
            m = piece_at(np.roll(m, -shift, axis=1), points)
            if (score >= top and m.any() and 0.005 < m.mean() <= most
                    and (~np.isin(L[m], (k, k + 1))).mean() < stray):
                best, top = m, score
        return best

    out = []
    for k, points in prompts:
        m = ask(k, points, w // 2 - points[0][0])
        if m is not None and m[:, 0].any() != m[:, -1].any():
            end = 0 if m[:, 0].any() else w - 1
            across = (w - 9 if end == 0 else 8, int(np.median(np.nonzero(m[:, end])[0])))
            whole = ask(k, points + [across], w // 2)
            m = whole if whole is not None else m
        if m is not None and not any((m & o).sum() > 0.8 * (m | o).sum() for o in out):
            out.append(m)
    model.to("cpu")  # dreamshaper needs the memory next
    torch.cuda.empty_cache()
    return out


def piece_at(m, points):
    """The piece of mask m holding every (x, y) of points, its gaps under 2 px closed and its holes filled (SAM's
    prompted masks are hatched: moved as they come, the farther band shows through in rows of pinholes), on the
    painting laid three times side by side, each point in any copy and the piece's three copies laid back on one, so
    a piece across the join is whole; empty when they lie in two pieces."""
    w = m.shape[1]
    lab, _ = ndimage.label(ndimage.binary_closing(np.tile(m, 3), iterations=2))
    i = lab[points[0][1], w + points[0][0]]
    if not i or any(i not in lab[y, [x, w + x, 2 * w + x]] for x, y in points):
        return np.zeros_like(m)
    piece = ndimage.binary_fill_holes(lab == i)
    return piece[:, :w] | piece[:, w:2 * w] | piece[:, 2 * w:]


FIELD_MOST = 0.25  # a prompted field may be a quarter of the painting: the near field often is
HALO = 14  # px: how far depth's soft edge round a nearer thing reaches into what stands beside it (r386)
HIDDEN = 0.25  # a foot seen on less than this share, out of that halo, is hidden


def foot_of(L, m, n, most=0.03, least=0.05):
    """The band a mask joins whole, or -1: cut by a band's edge, over exactly two neighbouring bands (least or more of
    it in each), it joins the band at its foot (its lowest seventh): depth judges a pine's crown against the sky behind
    it, and its foot is where it stands. Masks over most of the painting or more (the sky, the river), or over three
    bands (a field down a slope), are ground, not things, and stay cut: fields_of finds the field a cut runs through,
    given here with a larger most."""
    h, w = L.shape
    area = m.sum()
    if not area or area > most * h * w:
        return -1
    on = np.nonzero(np.bincount(L[m], minlength=n) >= least * area)[0]
    if len(on) != 2 or on[1] - on[0] != 1:
        return -1
    ys = np.nonzero(m.any(axis=1))[0]
    foot = m & (np.arange(h)[:, None] >= ys[-1] - max(2, (ys[-1] - ys[0]) // 7))
    # Within HALO px of a band nearer than both, the depth ramps up to that nearer thing: a foot seen only there is
    # hidden, and the thing goes whole to the band most of it is in. Judged by its halo, the pine beside noloop's big
    # trunk went to the nearer band, and its tip lifted that layer's edge over the trunk: a slab, repainted (r386).
    seen = foot & ~grow(L > on[1], HALO)
    if seen.sum() < HIDDEN * foot.sum():
        return int(np.bincount(L[m], minlength=n).argmax())
    return int(np.bincount(L[seen], minlength=n).argmax())


def objects(L, masks, n, most=0.03, least=0.05):
    """Each mask foot_of moves, given whole to its band. Largest first, so a pine inside a clump has the last word; on
    the bands as depth cut them, so one mask's move does not decide another's (r360)."""
    out = L.copy()
    for m in sorted(masks, key=lambda m: -m.sum()):
        k = foot_of(L, m, n, most, least)
        if k >= 0:
            out[m] = k
    return out


def agreeing(L, fields, n, share=0.1):
    """The fields foot_of moves, largest first, but one overlapping a larger kept one (share or more of it) that goes
    to another band. A field over three bands (river's lit field, round the join and down past the pines' shadow) comes
    back as overlapping masks over two bands each, to two bands: each moved, the one moved last cut the other with a
    straight vertical edge, at the join on river (r374, lint (e)). The larger holds more of the surface."""
    kept = []
    for m in sorted(fields, key=lambda m: -m.sum()):
        k = foot_of(L, m, n, FIELD_MOST)
        if k >= 0 and not any(j != k and (m & o).sum() >= share * m.sum() for o, j in kept):
            kept.append((m, k))
    return [m for m, _ in kept]


def with_fields(L, fields, n, few=4):
    """L with the fields moved (objects), but those whose move leaves the two ends of the painting in different bands
    on a row: SAM splits a field that runs round the join at different places from different prompts, and moved, the
    two halves meet a few px apart or not at all (river's lit field in the GPU run of r374: lint (e), seam 76). A row
    whose moved end finds its band again within reach px of the other end is closed across (mend_join) first; a field
    still at more than few differing rows is dropped (SAM's masks are hatched at their edge: a row or two never close),
    one round at a time, as a dropped field can mend another's end."""
    fields = list(fields)
    while True:
        out = mend_join(objects(L, fields, n, most=FIELD_MOST), L)
        differ = out[:, 0] != out[:, -1]
        bad = [i for i, m in enumerate(fields) if (differ & (m[:, 0] | m[:, -1])).sum() > few]
        if not bad:
            return out
        fields = [m for i, m in enumerate(fields) if i not in bad]


def kept_fields(L, moved, fields, edge=6):
    """L, but each pixel of a field that the passes after with_fields handed to a farther band than the field's
    (moved), under a pixel of that field still in its band: see_through took a dark tuft of grass for sky between
    branches, necks a strip at the layer's top for a neck. Each was a notch in the nearer layer's field, through which
    the farther layer's repaint showed as it slid (river, r387). At the field's top edge, what they handed back stays
    back: the ridge seen between a far hill's trees. Then each field's part in its band closed by a disk of radius
    edge px: SAM's prompted mask is ragged where a field meets the trees behind it, and each notch it left in the
    field's edge showed those trees, in the farther layer, as a dark scallop once the layers slid."""
    if not fields:
        return L
    field = np.any(fields, axis=0)
    back = field & (moved > L)
    stayed = field & (moved == L)
    out = L.copy()
    for k in np.unique(moved[back]):
        under = np.maximum.accumulate(stayed & (L == k), axis=0)  # a pixel of the field in band k above, same column
        take = back & (moved == k) & under
        out[take] = k
    disk = np.hypot(*np.mgrid[-edge:edge + 1, -edge:edge + 1]) <= edge
    for k in np.unique(moved[field]):
        if k == 0:
            continue
        own = field & (moved == k) & (out == k)
        shut = looped(lambda m: ndimage.binary_closing(m, structure=disk), own)
        out[shut & (out < k)] = k
    return out


def mend_join(out, before, reach=32):
    """out with each row whose two ends differ, one of them moved from before, given the moved end's band from the
    other end inward up to where that band starts again, when it does within reach px; in place."""
    w = out.shape[1]
    for r in np.nonzero(out[:, 0] != out[:, -1])[0]:
        moved = (out[r, 0] != before[r, 0], out[r, -1] != before[r, -1])
        if moved[0] == moved[1]:
            continue
        band = out[r, 0] if moved[0] else out[r, -1]
        inward = np.arange(w - 1, w - 1 - reach, -1) if moved[0] else np.arange(reach)
        at = np.nonzero(out[r, inward] == band)[0]
        if len(at):
            out[r, inward[:at[0]]] = band
    return out


def bands(d, n, min_area):
    """Labels 0 (far) .. n-1 (near): k-means of the disparity in one dimension, then a 5 px majority filter and
    islands under min_area pixels merged into the label around them, both on the painting laid three times side by
    side: the middle copy's left and right edges then agree, and the layers loop."""
    centres = kmeans(d.ravel(), n)
    order = np.argsort(centres)
    L = np.argsort(order)[np.argmin(np.abs(d[..., None] - centres[None, None]), axis=2)]
    w = L.shape[1]
    L = ndimage.median_filter(np.tile(L, 3), size=5, mode="nearest")  # three copies: an island across the join is whole
    for _ in range(3):
        if not merge_small(L, n, min_area):
            break
    shut_in(L, n)
    return L[:, w:2 * w], centres[order]


def kmeans(v, n):
    """n centres of the values v, in one dimension."""
    centres = np.percentile(v, (np.arange(n) + 0.5) * 100 / n)
    for _ in range(50):
        lab_ = np.argmin(np.abs(v[:, None] - centres[None]), axis=1)
        new = np.array([v[lab_ == k].mean() if (lab_ == k).any() else centres[k] for k in range(n)])
        if np.allclose(new, centres):
            break
        centres = new
    return centres


def merge_small(L, n, min_area):
    """Each island of a label under min_area pixels takes the label around it, in place; whether any did."""
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
    return changed


def shut_in(L, n):
    """A patch of a farther band shut in by nearer ones (4 in 5 of its rim), but the back one, is the sky between a
    crown's leaves judged half near: kept, it lifts its band's top edge to the crown, and that layer carries a piece
    of the tree. It joins what shuts it in, in place. The back band's own patches are true gaps, seen through."""
    for k in range(1, n - 1):
        comp, count = ndimage.label(L == k)
        for i in range(1, count + 1):
            isl = comp == i
            ring = ndimage.binary_dilation(isl, iterations=2) & ~isl
            if ring.any() and (L[ring] > k).mean() >= 0.8:
                L[isl] = np.bincount(L[ring], minlength=n).argmax()


def by_colour(L, img, n, reach=16, window=41, rounds=8, margin=0.6, apart=40):
    """The small model misses a thin branch, and leaves it in the band behind: within reach px of a nearer band, a
    pixel moves to it when it matches that band's mean colour round it (window px, wrapping along X) better, by
    margin, than its own band's, and the two means are apart by more than apart (0-441). A few rounds let a branch
    fill in from the crown it hangs from. Only toward nearer bands, and only where the bands differ in colour: on
    gently sloping ground both sides of a cut are the same green, and texture alone would fray the cut.
    """
    rgb = img.astype(np.float32)
    for _ in range(rounds):
        means = band_means(L, rgb, n, window)
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


def band_means(L, rgb, n, window):
    """Per band, (its mean colour round each pixel over window px, wrapping along X; its share of the window)."""
    out = []
    for k in range(n):
        m = (L == k).astype(np.float32)
        cnt = ndimage.uniform_filter(m, window, mode=("nearest", "wrap"))
        sm = ndimage.uniform_filter(rgb * m[..., None], (window, window, 1), mode=("nearest", "wrap", "nearest"))
        out.append((sm / np.maximum(cnt, 1e-6)[..., None], cnt))
    return out


def filled(mean, has, far):
    """mean where has, else its value at the nearest place that has (wrapping along X), up to far px; past it NaN."""
    if not has.any():
        return np.full_like(mean, np.nan)
    if has.all():
        return mean
    w = has.shape[1]
    dist, (iy, ix) = ndimage.distance_transform_edt(~np.tile(has, 3), return_indices=True)
    dist, iy, ix = dist[:, w:2 * w], iy[:, w:2 * w], ix[:, w:2 * w] % w
    mean = mean[iy, ix]
    mean[dist > far] = np.nan
    return mean


def sky_hued(L, dn, centres, rgb, own, n, window, far, margin=0.5, apart=20, dim=0.65):
    """The sky seen through a crown that depth judged wholly near (r380): pixels of band 2 or nearer whose hue (their
    colour less its grey) is nearer the sky's round them (band 0 at the sky's depth, its mean over window px, up to
    far px from it) than their own band's, and than grey, by margin, and farther than their band's centre. The sky
    there is darkened by the leaves round it, so by colour it is nearer the crown's; its hue is still the sky's.
    Band k's hue is its core's, its pixels at or past its centre: its mean over all of them is tinted by the gaps
    themselves. Band 1 never: the haze tints the band just in front of the sky toward its hue (river's far forest).
    Than grey: a cool shadow under a clump beside a white cloud is as near the whitish sky there as the green. And
    at least dim as bright as the sky: the leaves darken it, a tuft painted in the pale sky's hue is darker still
    (valley: moved, it stayed in the back layer, unrepainted, and floated off its branch)."""
    (sky, seen), = band_means(np.where((L == 0) & (dn <= SKY), 0, 1), rgb, 1, window)
    sky = filled(sky, seen > 0.02, far)
    core = np.where(dn >= centres[L], L, -1)
    core = np.stack([filled(mean, cnt > 0.02, far) for mean, cnt in band_means(core, rgb, n, window)])
    core = np.take_along_axis(core, L[None, ..., None].repeat(3, -1), 0)[0]
    core = np.where(np.isnan(core), own, core)
    hue, hue_own, hue_sky = (v - v.mean(-1, keepdims=True) for v in (rgb, core, sky))
    h_own, h_sky = np.linalg.norm(hue - hue_own, axis=-1), np.linalg.norm(hue - hue_sky, axis=-1)
    grey = np.linalg.norm(hue_sky, axis=-1)
    return ((L > 1) & ~np.isnan(h_sky) & (np.linalg.norm(hue_own - hue_sky, axis=-1) > apart)
            & (rgb.mean(-1) >= dim * sky.mean(-1))
            & (h_sky < margin * np.minimum(h_own, grey)) & (dn < centres[L]))


def see_through(L, dn, centres, img, n, window=41, margin=0.8, rim=0.8, rounds=40, apart=40, third=1 / 3, far=96):
    """by_colour's mirror: the sky and the ridge seen between a crown's branches, which depth blurs to near and
    merge_small and shut_in hand to the crown, go back to the farther band, else the near layer carries them opaque
    and they slide over the scene behind (r361). A pixel of band k goes to the farther band j whose mean colour round
    it (window px; where j has none, its mean at the nearest place that has, up to far px) it matches better, by
    margin, than its own band's, the two means apart by more than apart (0-441), where depth itself put it in the
    first third of the way from j to k. Then, a ring a round, every pixel beside a moved one that matches j, or the
    moved pixels round it (the sky as the crown frames it), by rim goes too, so a gap clears to the leaves round it.
    The means are taken once, before any move: taken again, a moved leaf tints the sky's mean green and the next ring
    eats its clump. Grown only from the moved pixels, never from band j's own edge: on the ground that edge runs
    between lit and shaded fields, and growing from it frays the cut. A gap depth judged wholly near is seeded by its
    hue instead (sky_hued, r380), and grows the same way."""
    rgb = img.astype(np.float32)
    # once, on the bands as they came: the moved pixels' colours never tint the band they went to
    M = np.stack([filled(mean, cnt > 0.02, far) for mean, cnt in band_means(L, rgb, n, window)])
    own = np.take_along_axis(M, L[None, ..., None].repeat(3, -1), 0)[0]
    d_own = np.linalg.norm(rgb - own, axis=-1)
    D = np.linalg.norm(rgb[None] - M, axis=-1)
    D = np.where((np.arange(n)[:, None, None] < L[None]) & ~np.isnan(D), D, np.inf)
    j, dj = D.argmin(0), D.min(0)
    mj = np.take_along_axis(M, j[None, ..., None].repeat(3, -1), 0)[0]
    like = (L > 0) & np.isfinite(dj) & (np.linalg.norm(own - mj, axis=-1) > apart)
    moved = like & (dj < margin * d_own) & (dn < centres[j] + (centres[L] - centres[j]) * third)
    back = sky_hued(L, dn, centres, rgb, own, n, window, far) & ~moved
    j = np.where(back, 0, j)
    moved |= back
    seeds = moved.astype(np.float32)
    cnt = ndimage.uniform_filter(seeds, window, mode=("nearest", "wrap"))
    gap = ndimage.uniform_filter(rgb * seeds[..., None], (window, window, 1), mode=("nearest", "wrap", "nearest"))
    gap /= np.maximum(cnt, 1e-6)[..., None]
    d_gap = np.where(cnt * window * window > 0.5, np.linalg.norm(rgb - gap, axis=-1), np.inf)  # no moved pixel: none
    rim_ok = like & (np.minimum(dj, d_gap) < rim * d_own)
    for _ in range(rounds):
        more = rim_ok & ~moved & grow(moved, 1)
        if not more.any():
            break
        moved |= more
    return np.where(moved, j, L)


def shown(L, k):
    """(the columns where band k's first row is its own top, its first row per column). A first row with a nearer
    band right above it is where the nearer thing ends, not where band k begins: kept, a strip of grass under a trunk
    sets the run's lower side, and the layer's edge steps down beside the hill (r322)."""
    w = L.shape[1]
    has = (L == k).any(axis=0)
    first = np.argmax(L == k, axis=0)
    hidden = (first > 0) & (L[np.maximum(first - 1, 0), np.arange(w)] > k)
    if (has & ~hidden).any():
        has &= ~hidden
    return has, first


SLIVER = 8  # px: a run of columns showing band k narrower than this, between nearer things, is part of their run


def unsliver(has):
    """has without its runs narrower than SLIVER, wrapping along X: a column or two of the band seen between a pine
    and the trunk in front of it would split the trunk's run, and the outline mirrored into each half would step."""
    w = len(has)
    if has.all() or not has.any():
        return has
    out, x, start = has.copy(), 0, int(np.argmin(has))  # from a column without the band: each run seen whole
    while x < w:
        c = (start + x) % w
        if not has[c]:
            x += 1
            continue
        run = 0
        while has[(c + run) % w] and run < w:
            run += 1
        if run < SLIVER:
            out[[(c + i) % w for i in range(run)]] = False
        x += run
    return out if out.any() else has


def topline(L, k, behind=True):
    """Per column, the first row of band k. A run of columns without it (a nearer thing stands there, or right above
    its first row) takes the lower of the two rows either side, the run's floor: a line drawn between them, from tall
    pines on one side to low ground on the other, left a sloped wedge (r322). behind: over the run, the outline on
    each side goes on, mirrored into it, its height over the floor eased from one side's to the other's: a pine clump
    whose left part stood behind a trunk tapers down to the field on the trunk's other side, where the floor alone cut
    it with a straight vertical edge that showed once the trunk slid past (r373). alpha_of then takes every pixel
    under that outline in the run, so the repaint fills the clump's shape, not a spike of the trunk's."""
    h, w = L.shape
    has, first = shown(L, k)
    if not has.any():
        return np.full(w, float(h))
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
    if not behind:
        return out
    floor, out = out, out.copy()
    for c, run in runs_of(unsliver(has)):
        low = max(floor[(c - 1) % w], floor[(c + run) % w])  # the merged run's own sides, over any sliver in it
        for i, (left, right, eased) in enumerate(mirrored(c, run, w)):
            # A mirrored column in a run of its own reads that run's floor.
            out[(c + i) % w] = low - (1 - eased) * (low - min(floor[left], low)) - eased * (low - min(floor[right], low))
    return out


def runs_of(has):
    """(first column, length) of each run of columns without the band, wrapping along X."""
    w, out, x = len(has), [], 0
    if has.all() or not has.any():
        return out
    start = int(np.argmax(has))  # from a column that has the band, so every run has both sides
    while x < w:
        c = (start + x) % w
        if has[c]:
            x += 1
            continue
        run = 0
        while not has[(c + run) % w]:
            run += 1
        out.append((c, run))
        x += run
    return out


def mirrored(c, run, w):
    """For each column of a run: the column its left side mirrors into it, the right side's, and how far the right
    side weighs (eased from 0 to 1 across the run)."""
    for i in range(run):
        t = (i + 0.5) / run
        yield (c - 1 - i) % w, (c + 2 * run - 1 - i) % w, t * t * (3 - 2 * t)


def field_colours(img, field, sigma=8):
    """Each pixel of field, the mean of the field's colours round it (a Gaussian of sigma px, over the field only,
    wrapping along X): its light and shade, not a track or a bush on it."""
    f = field.astype(np.float32)

    def blur(v):
        return ndimage.gaussian_filter(v, sigma, mode=("nearest", "wrap"))
    wgt = np.maximum(blur(f), 1e-6)
    return np.dstack([blur(img[..., c].astype(np.float32) * f) / wgt for c in range(3)])


def behind_start(src, img, known, L, k):
    """The repaint's start over the runs where band k goes on behind a nearer thing: the painting's own pixels beside
    the run, mirrored into it as its outline is (topline), each side's eased, where those are layer k's; fill()'s
    blur elsewhere. From the blur alone, the inpaint left the clump behind the trunk a smooth olive mass (r373)."""
    w = L.shape[1]
    has = unsliver(shown(L, k)[0])
    out = src.copy()
    for c, run in runs_of(has):
        for i, (left, right, eased) in enumerate(mirrored(c, run, w)):
            x = (c + i) % w
            kl, kr = known[:, left], known[:, right]
            both = (1 - eased) * img[:, left] * kl[:, None] + eased * img[:, right] * kr[:, None]
            weight = (1 - eased) * kl + eased * kr
            take = (weight > 0.25) & ~known[:, x]
            out[take, x] = both[take] / weight[take, None]
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
        top = topline(np.where(small, -1, L), k, behind=False)
        for i in np.unique(comp[small]):
            isl = comp == i
            ys, xs = np.nonzero(isl)
            if not (ys < top[xs] - 8).any():
                continue
            ring = ndimage.binary_dilation(isl, iterations=2) & ~isl & (L < k)
            if ring.any():
                L[isl] = np.bincount(L[ring], minlength=n).argmax()
    return L[:, w:2 * w]


def alpha_of(L, k):
    """Layer k's pixels: its band, and every nearer pixel under the band's top edge; over a run of columns where a
    nearer thing hides the band, every pixel under it (the sky there too: band k goes on behind, r373)."""
    rows = np.arange(L.shape[0])[:, None]
    has = unsliver(shown(L, k)[0])
    behind = ~has & (L > k).any(axis=0)  # a gap in the band with nothing nearer in it stays a gap
    return (L == k) | ((rows >= topline(L, k)[None]) & ((L > k) | behind[None]))


def loose(L, n, near=3, rounds=2):
    """Each piece of a layer apart from its mass, front to back: within near px of the mass (a leaf beside its crown),
    the gap between them joins the layer; farther (a speck of crown or field the depth judged nearer, floating over
    the layer at its speed), its band's pixels go to the farther band round it, never to nothing, so no hole opens
    where it was. A piece handed back is a piece of the farther layer, seen again in its turn; a second round catches
    what the first moved under a top edge. On the painting laid three times side by side, for the join (r359)."""
    w = L.shape[1]
    for _ in range(rounds):
        moved = False
        for k in range(n - 1, 0, -1):
            T = np.tile(L, 3)
            lab, count = ndimage.label(alpha_of(T, k))
            if count < 2:
                continue
            sizes = ndimage.sum(np.ones(lab.shape), lab, index=np.arange(1, count + 1))
            mass = np.isin(lab, 1 + np.nonzero(sizes == sizes.max())[0])  # one piece round the join, or three alike
            dist = ndimage.distance_transform_edt(~mass)
            for i, sl in enumerate(ndimage.find_objects(lab)):
                if sizes[i] == sizes.max():
                    continue
                box = tuple(slice(max(0, q.start - near - 3), q.stop + near + 3) for q in sl)
                piece, t = lab[box] == i + 1, T[box]
                d = dist[box][piece].min()
                if d <= near:
                    j = int(np.ceil(d)) + 1
                    both = piece | mass[box]
                    gap = ndimage.binary_closing(both, iterations=j) & ~both & ndimage.binary_dilation(piece, iterations=j)
                    t[gap & (t < k)] = k
                else:
                    ring = ndimage.binary_dilation(piece, iterations=2) & ~piece & (t < k)
                    if not ring.any():
                        continue
                    t[piece & (t == k)] = np.bincount(t[ring], minlength=n).argmax()
                moved = True
            L = T[:, w:2 * w]
        if not moved:
            break
    return L


def necks(L, dn, n, r=4, least=50):
    """Each part of a layer hanging over its top edge on a neck under 2r px (a hedge line or field strip of the far
    hills the depth put in the band, joined to its treeline by a few pixels: loose() keeps it, and it floats at the
    layer's speed over the hills it belongs to), whose own pixels' depth is nearer the farther band's than its
    band's: those pixels go to the farther band round it, as loose() hands back a piece. The parts are what an
    opening by r px (4-connected) cuts off the mass, least px or more, at least as wide as tall (a pine tip, erased
    by the opening, is taller), with nothing of the mass above them (a crown's clump hangs under its crown). On the
    painting laid three times side by side, for the join (r379)."""
    w = L.shape[1]
    cross = ndimage.generate_binary_structure(2, 1)
    D = np.tile(dn, 3)
    for k in range(n - 1, 0, -1):
        T = np.tile(L, 3)
        med = [np.median(D[T == j]) if (T == j).any() else 0.0 for j in (k - 1, k)]
        a = alpha_of(T, k)
        lab, count = ndimage.label(ndimage.binary_opening(a, cross, iterations=r))
        if not count:
            continue
        sizes = ndimage.sum(np.ones(lab.shape), lab, index=np.arange(1, count + 1))
        mass = np.isin(lab, 1 + np.nonzero(sizes == sizes.max())[0])
        under = np.cumsum(mass, axis=0) > 0  # the mass at or above this row, in this column
        parts, count = ndimage.label(a & ~ndimage.binary_dilation(mass, cross, iterations=r))
        for i, sl in enumerate(ndimage.find_objects(parts)):
            box = tuple(slice(max(0, q.start - 3), q.stop + 3) for q in sl)
            part, t = parts[box] == i + 1, T[box]
            own = part & (t == k)
            if (part.sum() < least or sl[1].stop - sl[1].start < sl[0].stop - sl[0].start or under[box][part].any()
                    or not own.any() or np.median(D[box][own]) >= (med[0] + med[1]) / 2):
                continue
            ring = ndimage.binary_dilation(part, iterations=2) & ~part & (t < k)
            if ring.any():
                t[own] = np.bincount(t[ring], minlength=n).argmax()
        L = T[:, w:2 * w]
    return L


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


def outlines(img, masks):
    """The painting dimmed, each SAM mask in a colour of its own and outlined in white."""
    rng = np.random.default_rng(3)
    out = img.astype(np.float32) * 0.35
    for m in sorted(masks, key=lambda m: -m.sum()):
        out[m] = img[m] * 0.35 + rng.integers(40, 255, 3) * 0.65
        out[m & ~ndimage.binary_erosion(m)] = 255
    return Image.fromarray(out.astype(np.uint8))


def colour_bands(L, n):
    pal = np.array([(70, 90, 160), (90, 160, 120), (220, 180, 80), (220, 90, 70), (200, 80, 200), (240, 240, 240)])
    return Image.fromarray(pal[L % len(pal)].astype(np.uint8))


def parse_args():
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
    ap.add_argument("--rule", action="append", metavar="NAME=on|off",
                    help="force a rule on or off, whatever its conditions say: " + ", ".join(r.name for r in RULES))
    a = ap.parse_args()
    if not 2 <= a.layers <= 6:
        ap.error("--layers: 2 to 6")
    try:
        a.forced = parallax_rules.parse_forced(a.rule, RULES)
    except ValueError as e:
        ap.error(str(e))
    return a


def mend_loop(img, cut, a, recipe):
    """1. The loop: a painting whose join shows has a band round it repainted; the painting, its join mended."""
    h, w = img.shape[:2]
    ratio, _ = seam.join(seam.load(os.path.join(cut, "painting.png")), seam.load(os.path.join(cut, "painting.png")))
    log(f"painting {w}x{h}: seam ratio {ratio:.2f}")
    recipe["loop"] = {"before": round(float(ratio), 2), "after": None}
    if ratio <= 2:
        return img
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
    return img


def depth_bands(img, n, cut):
    """2. Depth, 3. bands: (dn, L, centres, ground, masks), the depth normalised to 0..1, each pixel's band, far 0 to
    near n-1, the fields fields_of found (r387: the layers behind one seed their hole with it) and SAM's masks (the
    clouds are read from them, r409)."""
    h, w = img.shape[:2]
    # r391: each GPU step under the lock gen.py and anim.py take, its memory freed before it ends; gen.py (the loop,
    # the repaints) takes it itself, outside these
    with gpu_lock.held("depth_page depth"):
        d = depth_of(img)
    lo, hi = np.percentile(d, (1, 99.5))
    dn = np.clip((d - lo) / (hi - lo), 0, 1)
    Image.fromarray((dn * 255).astype(np.uint8)).save(os.path.join(cut, "depth.png"))
    L, centres = bands(dn, n, min_area=w * h // 150)
    with gpu_lock.held("depth_page masks"):
        masks = masks_of(img)
    L = objects(L, masks, n)
    outlines(img, masks).save(os.path.join(cut, "objects.png"))
    with gpu_lock.held("depth_page fields"):
        fields = fields_of(img, L, n)
    fields = agreeing(L, fields, n)
    L = with_fields(L, fields, n)
    moved = L.copy()
    outlines(img, fields).save(os.path.join(cut, "fields.png"))
    L = by_colour(L, img, n)
    L = see_through(L, dn, centres, img, n)
    L = strays(L, n, w * h // 150)
    L = loose(L, n)
    L = loose(necks(L, dn, n), n)  # the stubs a neck leaves are loose pieces
    L = kept_fields(L, moved, fields)
    colour_bands(L, n).save(os.path.join(cut, "bands.png"))
    log("bands, far to near: " + ", ".join(f"{k}: disparity {centres[k]:.2f}, {(L == k).mean():.0%} of the pixels"
                                          for k in range(n)))
    return dn, L, centres, np.any(fields, axis=0) if fields else np.zeros((h, w), bool), masks


def cut_layer(k, img, dn, L, centres, ground, cut, a):
    """4. Layer k and its hole, repainted: (layer, its recipe entry). With the sky apart (r409: L is -1 there), band
    0 is cut as a nearer band is, its outline going on behind what hides it."""
    h, w = img.shape[:2]
    n = a.layers
    apart = (L < 0).any()  # the sky apart (r409): band 0 is the far range, cut along range_top
    back = k == 0 and not apart  # the opaque back layer, the painted sky
    if back:
        alpha = np.ones((h, w), bool)
    elif k == 0:
        alpha = (L == 0) | ((np.arange(h)[:, None] >= range_top(L)[None]) & (L > 0))
    else:
        alpha = alpha_of(L, k)
    # The hole: what nearer things hid, and what alpha_of took behind them that is no part of band k (r373).
    hole = alpha & (grow(L > k, a.grow) | (L < k)) if k < n - 1 else np.zeros_like(alpha)
    # The sky between a crown's branches is band k too, tinted by the leaves round it: left as a start for the
    # fill, the repaint grows a small tree there. Islands of the layer's own pixels inside the hole join it.
    if hole.any():
        hole |= looped(lambda m, o: islands(m, w * h // 150, o), alpha & ~hole, hole)
    ctx = {"k": k, "n": n, "back": back, "theme": a.theme, "depth_model": MODEL, "img": img, "dn": dn, "alpha": alpha,
           "hole": hole}
    rules = parallax_rules.weigh(RULES, ctx, a.forced)
    hole = ctx["hole"]
    for r in rules:
        if r["applied"]:
            log(f"layer {k}: rule {r['rule']}: {r['moved']} px")
    cut_k = os.path.join(cut, f"l{k}")
    src = looped(fill, img, alpha & ~hole)
    if hole.any() and not back:  # the back layer is opaque, and nothing goes on behind its sky
        src = behind_start(src, img.astype(np.float32), alpha & ~hole, L, k)
    # A field moved to a nearer band goes on behind itself: its own colours, blurred, start the repaint there. From the
    # fill alone, river's l2 under its moved fields was repainted as a dark forest, seen at l3's edge as it slid; from
    # its pixels, a track across the field was painted again behind it, a second track once the layers slid (r387).
    field = hole & ground & (L > k)
    if field.any():
        src[field] = field_colours(img, ground & (L > k))[field]
    Image.fromarray(np.dstack([img, alpha * 255]).astype(np.uint8)).save(f"{cut_k}_raw.png")
    done = f"{cut_k}_painted.png"
    word = WORDS[min(3, round(k * 3 / max(1, n - 1)))]
    if hole.any() and not (a.keep and os.path.exists(done)):
        Image.fromarray(src.round().clip(0, 255).astype(np.uint8)).save(f"{cut_k}_src.png")
        soft = ndimage.gaussian_filter(grow(hole, 2).astype(np.float32), 1.5, mode=("nearest", "wrap"))
        Image.fromarray((np.maximum(soft, hole) * 255).astype(np.uint8)).save(f"{cut_k}_mask.png")
        gen(["inpaint", "--ckpt", a.ckpt, "--src", f"{cut_k}_src.png", "--mask", f"{cut_k}_mask.png", "--tile", "x", "--denoise",
             str(a.denoise), "--seed", str(a.seed * 100 + k), "--w", str(w), "--h", str(h), "--negative", NEG + TRACKS,
             "--prompt", f"{a.theme}, {word} scenery, painterly, seamless", "--out", done], f"{cut_k}_gen.log")
        log(f"layer {k}: {hole.mean():.0%} of the painting repainted")
    elif not hole.any():
        Image.fromarray(img).save(done)
    rgb = np.asarray(Image.open(done).convert("RGB"))
    # A 1 px soft edge where the layer gives way to a farther band: the cut's own line would alias.
    soft_a = alpha.astype(np.float32)
    if not back:
        soft_a = np.minimum(soft_a, ndimage.gaussian_filter(soft_a, 0.6, mode=("nearest", "wrap")) * 1.15)
    rgba = np.dstack([rgb, (np.clip(soft_a, 0, 1) * 255).round()]).astype(np.uint8)
    top = max(0, int(np.argmax(alpha.any(axis=1))) - 2)  # 2 clear rows: a pine's tip on the top row reads cut, (a)
    Image.fromarray(rgba[top:]).save(f"{cut_k}.png")
    med = float(np.median(dn[L == k])) if (L == k).any() else centres[k]
    layer = {"name": f"l{k}", "path": f"{cut_k}.png", "disparity": med, "top": top, "hole": float(hole.mean()),
             "raw": f"{cut_k}_raw.png"}
    entry = {"strip": f"l{k}", "disparity": round(med, 3), "top": top,
             "repainted": round(float(hole.mean()), 4), "seed": a.seed * 100 + k,
             "prompt": f"{a.theme}, {word} scenery, painterly, seamless" if hole.any() else None, "rules": rules}
    return layer, entry


SKY = 0.01  # disparity, normalised: the depth's farthest 1 % is clipped to 0, the sky


def sky_twigs(img, dn, own, hole, dark=40, half=4.5, window=31, sky_share=0.5):
    """A layer's own pixels that are a twig's tip against the sky: a dark piece (darker by dark than the bright of
    what is round it, grown into its own soft edge a third as dark) touching the hole, nowhere wider than 2 x half px,
    and with at least sky_share of the ring 4 to 8 px round it (past the depth's soft halo) at the sky's depth, and
    its darkest tenth darker than that ring's. A ridge's or a hedge's dark line has land round it, a mountain or a
    dark cloud is broad, the blue between two white clouds no darker than the sky round them."""
    lum = img.astype(np.float32).mean(axis=2)
    bright = looped(lambda v: ndimage.percentile_filter(v, 75, size=window), np.where(own, lum, 0))
    seed = own & (lum < bright - dark)
    d = looped(lambda m, s_: ndimage.binary_propagation(s_, mask=m), own & (lum < bright - dark / 3), seed)
    comp = looped(lambda m: ndimage.label(m)[0], d)
    width = looped(ndimage.distance_transform_edt, d)  # each dark pixel's distance to the piece's edge
    out = np.zeros_like(d)
    for i in np.unique(comp[d & grow(hole, 2)]):
        piece = comp == i
        if width[piece].max() > half:
            continue
        ring = grow(piece, 8) & own & ~grow(piece, 4)
        if not ring.any() or (dn[ring] <= SKY).mean() < sky_share:
            continue
        if np.percentile(lum[piece], 10) > np.percentile(lum[ring], 10) - dark / 2:  # blue between white clouds
            continue
        out |= piece
    return grow(out, 1) & own  # its anti-aliased rim


def _twigs_found(c):
    c["twigs"] = sky_twigs(c["img"], c["dn"], c["alpha"] & ~c["hole"], c["hole"])
    return c["twigs"].any()


def _twigs_to_hole(c):
    c["hole"] = c["hole"] | c["twigs"]
    return c["twigs"].sum()


# r378: rules for one case, weighed on each layer's hole before its repaint (parallax_rules); recipe.json's layers
# say what each decided.
RULES = [
    parallax_rules.Rule(
        "sky_twigs", "a tree's twig tips the depth judged as far as the sky are repainted as sky, not kept as birds",
        when=[("the back layer", lambda c: c.get("back", c["k"] == 0)),
              ("depth by Depth Anything V2 Small", lambda c: c["depth_model"] == MODEL),
              ("the theme names a tree", lambda c: re.search(r"\b(tree|pine|oak|forest|wood|jungle|palm)",
                                                            c["theme"], re.IGNORECASE) is not None),
              ("thin dark pieces against the sky touch the hole", _twigs_found)],
        act=_twigs_to_hole),
]


CLOUD_LIFT = 6  # lum: a cloud's mask is this much brighter than the sky round it; the far ridge's is darker
CLOUD_REST = 30  # speedXAtRest of the cloud layer: the skill's far clouds drift at 25-90


def clouds_of(img, dn, L, masks, least=200, most=0.5, share=0.9, lift=CLOUD_LIFT):
    """r409: the clouds, apart from the sky they float in: each SAM mask lying in the sky (share of its pixels in band
    0 at the sky's depth), from least px to most of that sky (the sky's own mask is larger), brighter by lift than the
    sky in a ring 2-6 px round it. SAM outlines a cloud whole: a colour key left bars, and grey ghosts where the clouds
    were (r375). A haze too faint for SAM stays in the sky."""
    sky = (L == 0) & (dn <= SKY)
    lum = img.astype(np.float32).mean(axis=2)
    out = np.zeros(L.shape, bool)
    for m in masks:
        if not least <= m.sum() <= most * sky.sum() or sky[m].mean() < share:
            continue
        ring = grow(m, 6) & ~grow(m, 2) & sky
        if ring.sum() >= 20 and lum[m].mean() >= lum[ring].mean() + lift:
            out |= m
    return out & (L == 0)


def sky_apart(L, dn, clouds, area, img=None, reach=16, window=41, far=96, margin=0.3, neck=2, nearer=0.3, rim=8,
              pale=0.15, haze=1.5, seam=12):
    """L with the sky, -1, apart from band 0, the far range: band 0's pixels at the sky's depth and its clouds. Within
    reach px of a nearer thing, a range pixel whose colour is within margin of its distance to the range round it (the
    mean over window px) from the sky round it goes to the sky: depth's halo round a crown judged the sky beside it
    nearer than the sky, and the range layer carried it as a pale chunk on the peak's flank. Only that near the sky's
    colour: a far range is hazy, and at 1, still at 0.5, its flank beside a trunk went to the sky, the range cut in a
    wall there (valley). On the range's rim, within rim px of the sky and nothing nearer, a pixel within pale of
    that distance goes to the sky too (r420: sky painted beside a peak's flank, judged a shade farther than the sky
    is, rode on the range as a pale shard, noloop); its means are the ones above, the rim in the range's: at 0.2,
    still with the rim out of them, valley's hazy far hills went to the sky in notches. Then a piece of the range
    within haze x the sky's depth, touching the sky, under area / 10 px and meeting it with no edge (the colour across
    its border steps under seam, the median) goes to the sky: the end of a far ridge the sky took, a shade nearer, cut
    in a step where the range layer carried it (r423, noloop and valley); a far peak of the range against the sky,
    hazy as it is, is bigger, and a small one is edged. Then a piece of the range
    under area px goes to the sky unless the range is most of its rim (the sky between a crown's leaves: kept, the
    range layer carried it as specks floating in the sky); a piece of the sky under area px with the range most of its
    rim joins the range (a pale cliff). Then a thin piece of the range
    (gone when it is opened by neck px) rising more than neck px over its top edge goes to the sky when a nearer thing
    is on nearer of its rim (r416: a crown's gaps judged as far as the range; the range layer carried them, repainted,
    as lumps on the peak's flank that slid with it, valley); a peak's tip, the sky all round it, stays, and so does
    the range's edge beside a trunk. The top is range_top with the thin pieces read as nearer things: read as sky,
    a speck under a trunk unhid its column and the range's top fell in a V there. On the painting laid three times
    side by side, for the join."""
    w = L.shape[1]
    S = np.where((L == 0) & ((dn <= SKY) | clouds), -1, L)
    if img is not None:
        zone = (S == 0) & grow(S > 0, reach)
        T = np.where(S == -1, 0, np.where((S == 0) & ~zone, 1, 2))
        rgb = img.astype(np.float32)
        (sky_m, sky_c), (rng_m, rng_c) = band_means(T, rgb, 2, window)
        sky_m, rng_m = filled(sky_m, sky_c > 0, far), filled(rng_m, rng_c > 0, far)
        with np.errstate(invalid="ignore"):
            to_sky, to_rng = np.linalg.norm(rgb - sky_m, axis=2), np.linalg.norm(rgb - rng_m, axis=2)
        edge = (S == 0) & ~zone & grow(S == -1, rim)
        S[(zone & (to_sky < margin * to_rng)) | (edge & (to_sky < pale * to_rng))] = -1
        S[ridge_ends(np.tile(S, 3), np.tile(dn, 3), np.tile(rgb, (1, 3, 1)), area // 10, haze, seam)[:, w:2 * w]] = -1
    S = np.tile(S, 3)
    for k in (0, -1):
        comp, count = ndimage.label(S == k)
        sizes = ndimage.sum(np.ones(S.shape), comp, index=np.arange(1, count + 1))
        for i in np.nonzero(sizes < area)[0]:
            isl = comp == i + 1
            ring = ndimage.binary_dilation(isl, iterations=2) & ~isl
            ranged = (S[ring] == 0).mean() > 0.5
            if ranged != (k == 0):
                S[isl] = -1 - k
    thin = (S == 0) & ~ndimage.binary_opening(S == 0, iterations=neck)
    rise = range_top(np.where(thin, 1, S))[None] - np.arange(S.shape[0])[:, None]  # rows over the range's top
    comp, count = ndimage.label(thin & (rise > -neck))
    for i, sl in enumerate(ndimage.find_objects(comp)):
        sl = tuple(slice(max(0, d.start - 2), d.stop + 2) for d in sl)
        piece = comp[sl] == i + 1
        ring = ndimage.binary_dilation(piece, iterations=2) & ~piece
        if rise[sl][piece].max() > neck and (S[sl][ring] > 0).mean() >= nearer:
            S[sl][piece] = -1
    S = S[:, w:2 * w]
    # Under the range's top edge nothing is sky: a pale patch between far hills at the sky's depth, cut out, showed
    # the filled sky as a blue hole in the hills (river).
    S[(S == -1) & (np.arange(S.shape[0])[:, None] >= range_top(S)[None])] = 0
    return S


def ridge_ends(S, dn, rgb, small, haze, seam):
    """sky_apart's pieces of the range (0 in S) within haze x SKY of depth, touching the sky (-1), under small px,
    whose colour steps under seam across their border with the sky (the median over the border's pixel pairs)."""
    near = (S == 0) & (dn <= SKY * haze)
    comp, count = ndimage.label(near)
    if not count:
        return near
    labels, steps = [], []
    for a, b in (((slice(1, None),), (slice(None, -1),)), ((slice(None, -1),), (slice(1, None),)),
                 ((slice(None), slice(1, None)), (slice(None), slice(None, -1))),
                 ((slice(None), slice(None, -1)), (slice(None), slice(1, None)))):
        pair = near[a] & (S[b] == -1)
        labels.append(comp[a][pair])
        steps.append(np.linalg.norm(rgb[a][pair] - rgb[b][pair], axis=1))
    labels, steps = np.concatenate(labels), np.concatenate(steps)
    index = np.arange(1, count + 1)
    sizes = ndimage.sum(np.ones(S.shape), comp, index=index)
    touch = np.isin(index, labels)
    med = np.full(count, np.inf)
    if touch.any():
        med[touch] = ndimage.median(steps, labels, index=index[touch])
    return np.isin(comp, index[touch & (sizes < small) & (med < seam)])


def range_top(S):
    """Per column, the far range's first row (band 0 of S); over a run of columns where a nearer thing hides it, a
    straight line between the run's two sides. A range's flank runs on behind a tree: topline's floor and mirror
    (r322, r373: a clump of pines behind a trunk) cut it lower, and the sky showed in a wedge on the peak's flank."""
    h, w = S.shape
    has, first = shown(S, 0)
    if not has.any():
        return np.full(w, float(h))
    out = first.astype(float)
    for c, run in runs_of(has):
        left, right = out[(c - 1) % w], out[(c + run) % w]
        for i in range(run):
            out[(c + i) % w] = left + (right - left) * (i + 1) / (run + 1)
    return out


def sky_layer(img, dn, S, clouds, cut, a, reach=6, dark=25, speck=400, window=31):
    """r409: the sky behind the far range and its clouds, opaque: the clouds (grown by reach px, their soft rim) and
    every nearer pixel (grown by --grow) filled from the clear sky round them by fill(), which keeps a sky's gradient.
    No repaint: one paints a new cloud, a bird or a ridge into it. RULES are weighed on it as on the back layer
    (sky_twigs: a twig's tip at the sky's depth is filled too); then every speck under speck px darker by dark than
    the sky round it (the median of a window px wide, over the sky with its hole filled: beside a crown, a median
    over the crown too was as dark as the twigs, which stayed as birds, noloop) is filled as well: a twig's end the
    rule left, which fill() smeared into a dark blot, or a bird. A far ridge in the sky is larger and stays.
    (layer, its recipe entry)."""
    h, w = S.shape
    alpha = np.ones((h, w), bool)
    hole = grow(S >= 0, a.grow) | grow(clouds, reach)
    ctx = {"k": 0, "n": a.layers, "back": True, "theme": a.theme, "depth_model": MODEL, "img": img, "dn": dn,
           "alpha": alpha, "hole": hole}
    rules = parallax_rules.weigh(RULES, ctx, a.forced)
    hole = ctx["hole"]
    for r in rules:
        if r["applied"]:
            log(f"sky: rule {r['rule']}: {r['moved']} px")
    lum = img.astype(np.float32).mean(axis=2)
    clear = looped(fill, img, ~hole).mean(axis=2)  # the sky alone, its hole filled
    around = looped(lambda v: ndimage.median_filter(v, size=window), np.where(hole, clear, lum))
    blot = looped(lambda m: ndimage.label(m)[0], ~hole & (lum < around - dark))
    sizes = np.bincount(blot.ravel())
    specks = (blot > 0) & (sizes[blot] < speck)
    hole = hole | grow(specks, 2)
    rgb = looped(fill, img, ~hole).round().clip(0, 255).astype(np.uint8)
    Image.fromarray(img).save(os.path.join(cut, "sky_raw.png"))
    Image.fromarray(rgb).save(os.path.join(cut, "sky.png"))
    layer = {"name": "sky", "path": os.path.join(cut, "sky.png"), "disparity": 0.0, "top": 0, "hole": float(hole.mean()),
             "raw": os.path.join(cut, "sky_raw.png")}
    entry = {"strip": "sky", "disparity": 0.0, "top": 0, "repainted": 0, "filled": round(float(hole.mean()), 4),
             "specks": int(specks.sum()), "seed": None, "prompt": None, "rules": rules}
    return layer, entry


def cloud_layer(img, sky, S, clouds, cut, reach=6, fade=10):
    """r409: the clouds on a layer of their own, drawn over the sky layer (sky, its rgb) and behind the far range.
    Inside SAM's outline (shrunk 2 px) the painting, opaque; out to reach px past it, over the sky only, each pixel's
    share of cloud over the filled sky behind it: alpha the way from the sky's colour toward the clouds' contrast, the
    colour un-mixed from the sky, so the soft rim is the painting's again over that sky. Faded out over fade px toward
    every nearer thing that hid a part of a cloud (a peak, a crown): a cloud drifting off it shows no cut edge.
    (layer, its recipe entry, its rows on the painting: first, last + 1)."""
    h = S.shape[0]
    f, bg = img.astype(np.float32), sky.astype(np.float32)
    diff = np.linalg.norm(f - bg, axis=2)
    core = ~grow(~clouds, 2)
    contrast = max(20.0, 0.6 * float(np.median(diff[core]))) if core.any() else 40.0
    alpha = np.where(core, 1.0, np.clip(diff / contrast, 0, 1)) * (grow(clouds, reach) & (S < 0))
    near = looped(lambda m: ndimage.distance_transform_edt(~m), S >= 0)
    t = np.clip(near / fade, 0, 1)
    alpha *= t * t * (3 - 2 * t)
    alpha[alpha < 0.02] = 0
    rgb = np.where(alpha[..., None] > 0, bg + (f - bg) / np.maximum(alpha, 0.02)[..., None], 0)
    rgba = np.dstack([rgb.clip(0, 255), alpha * 255]).round().astype(np.uint8)
    rows = np.nonzero(alpha.any(axis=1))[0]
    top, bottom = max(0, int(rows[0]) - 2), min(h, int(rows[-1]) + 3)
    path = os.path.join(cut, "clouds.png")
    Image.fromarray(rgba[top:bottom]).save(path)
    Image.fromarray(np.dstack([img, clouds * 255]).astype(np.uint8)).save(os.path.join(cut, "clouds_raw.png"))
    layer = {"name": "clouds", "path": path, "disparity": 0.0, "top": top, "bottom": bottom, "hole": 0.0,
             "raw": os.path.join(cut, "clouds_raw.png"), "rest": CLOUD_REST}
    entry = {"strip": "clouds", "disparity": 0.0, "top": top, "bottom": bottom, "repainted": 0, "seed": None,
             "prompt": None, "clouds": round(float(clouds.mean()), 4), "faded": fade, "rest": CLOUD_REST, "rules": []}
    return layer, entry


def speeds_of(layers, front_speed):
    """Each layer's speed: proportional to its disparity, the front at front_speed, none under a twentieth of it,
    each at least 1.25 times the one behind."""
    front = max(layers[-1]["disparity"], 1e-3)
    s, prev = [], 0.0
    for ly in layers:
        v = max(front_speed * ly["disparity"] / front, front_speed / 20, prev * 1.25 if prev else 0)
        s.append(round(min(v, front_speed), 5))
        prev = v
    s[-1] = front_speed
    return s


def write_page(name, layers, s, img, page_dir, a):
    """5. The page: the atlas, the .jplax and its round.json; (scene, its folder from ROOT, the sky's colours)."""
    n = len(layers)
    h, w = img.shape[:2]
    make_page.pack(name, [(ly["name"], ly["path"]) for ly in layers], "Linear,Linear", out=page_dir)
    # Every layer's bottom on the screen's bottom, but the clouds' (r409): cut at their last row, raised by the rows
    # under it, and drifting at their rest speed.
    page_layers = [lab.layer(ly["name"], speed=s[k], rest=ly.get("rest", 0.0),
                             dy=round(100 * (h - ly.get("bottom", h)) * lab.SCREEN_W / w / lab.SCREEN_H, 3))
                   for k, ly in enumerate(layers)]
    sky = (seam_colour(img[:4]), seam_colour(img[-4:]))
    page = lab.page_of(f"{name}.atlas", page_layers, sky=(sky[0], sky[0]), ground=(sky[1], sky[1]), top_size=0.5,
                       bottom_size=0.5)
    with open(os.path.join(page_dir, f"{name}.jplax"), "w") as f:
        json.dump(page, f, indent=1)
    rel = os.path.relpath(page_dir, ROOT)
    about = (f"r279 depth-page: {os.path.basename(a.painting)} cut into {a.layers} depth bands"
             + (", the sky and its clouds apart (r409)" if n > a.layers else "") + ", speeds "
             + ", ".join(f"{v:g}" for v in s))
    scene = {"id": "d01", "page": f"{rel}/{name}.jplax", "atlasDir": rel, "name": name, "about": about}
    with open(os.path.join(page_dir, "round.json"), "w") as f:
        json.dump({"round": name, "about": about, "scenes": [scene]}, f, indent=1)
    return scene, rel, sky


def main():
    a = parse_args()
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
    recipe = {"tool": "depth-page", "command": shlex.join(["tools/depth-page.sh"] + [x for x in sys.argv[1:]
                                                                                    if x not in ("--keep", "--no-shots")]),
              "theme": a.theme, "name": name, "seed": a.seed, "size": [w, h], "denoise": a.denoise,
              "model": f"Depth Anything V2 Small + SAM 2.1 small + SD 1.5 {a.ckpt} (repaints)", "page": f"page/{name}.jplax",
              "negative": NEG, "fogStrength": 0, "layers": []}

    img = mend_loop(np.asarray(im), cut, a, recipe)
    dn, L, centres, ground, masks = depth_bands(img, n, cut)
    layers = []
    # r409: clouds always on a layer of their own, behind the far range and over a sky with none: BILLOW on a back
    # layer holding sky, clouds and range warped the mountains too, and painted clouds could not drift.
    clouds = clouds_of(img, dn, L, masks)
    if clouds.any():
        L = sky_apart(L, dn, clouds, w * h // 150, img)
        clouds &= L < 0
        sky, entry = sky_layer(img, dn, L, clouds, cut, a)
        layers.append(sky)
        recipe["layers"].append(entry)
        cloud, entry = cloud_layer(img, np.asarray(Image.open(sky["path"]).convert("RGB")), L, clouds, cut)
        layers.append(cloud)
        recipe["layers"].append(entry)
        log(f"clouds: {clouds.mean():.1%} of the painting, on a layer of their own over the filled sky")
    for k in range(n):
        layer, entry = cut_layer(k, img, dn, L, centres, ground, cut, a)
        layers.append(layer)
        recipe["layers"].append(entry)
    s = speeds_of(layers, a.front_speed)
    scene, rel, sky = write_page(name, layers, s, img, page_dir, a)
    sheet(cut, layers)

    problems = lab.lint(lab.load(scene["page"]), rel)
    for k, ly in enumerate(recipe["layers"]):
        ly["speed"] = s[k]
    recipe.update(lint=problems, sky=list(sky))
    with open(os.path.join(out, "recipe.json"), "w") as f:
        json.dump(recipe, f, indent=1)
    print(f"{rel}/{name}.jplax")
    for k, ly in enumerate(layers):
        print(f"  {ly['name']}: disparity {ly['disparity']:.2f}, speed {s[k]:g}, from row {ly['top']}, "
              f"{ly['hole']:.0%} repainted")
    print("\n".join("  ! " + p for p in problems) or "  lint ok")
    if not a.no_shots:
        subprocess.run([os.path.join(TOOLS, "parallax-lab-shots.sh"), page_dir, os.path.join(out, "shots")],
                       cwd=ROOT, check=False)
    return 1 if problems else 0


def seam_colour(rows):
    return "".join(f"{int(v):02x}" for v in np.median(rows.reshape(-1, 3), axis=0))


def sheet(cut, layers):
    """The painting, its depth, its bands, then each layer raw (the hole see-through) and repainted, on a checker."""
    paint = Image.open(os.path.join(cut, "painting.png"))
    w, h = paint.size
    tw, th = w // 2, h // 2
    check = np.indices((th, tw)).sum(axis=0) // 8 % 2
    bg = Image.fromarray(np.where(check[..., None], 200, 150).repeat(3, axis=2).astype(np.uint8))
    tiles = [paint, Image.open(os.path.join(cut, "depth.png")).convert("RGB"),
             Image.open(os.path.join(cut, "bands.png"))]
    for ly in layers:
        for f in (ly["raw"], ly["path"]):
            im = Image.open(f).convert("RGBA")
            full = Image.new("RGBA", (w, h))
            full.paste(im, (0, ly.get("bottom", h) - im.height if f == ly["path"] else h - im.height))
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
