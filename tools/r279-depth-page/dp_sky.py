"""The sky apart (r409; r533: split out of depth_page.py): the clouds SAM outlined, the sky cut from the far range,
and the sky and cloud layers."""
import os

import dp_common  # noqa: F401  (sys.path: parallax_rules)
import numpy as np
import parallax_rules
from dp_common import SKY, band_means, fill, filled, grow, looped, runs_of
from dp_models import MODEL
from dp_outline import shown
from dp_rules import RULES
from PIL import Image
from scipy import ndimage

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
    (layer, its recipe entry: depth_page logs its rules applied)."""
    h, w = S.shape
    alpha = np.ones((h, w), bool)
    hole = grow(S >= 0, a.grow) | grow(clouds, reach)
    ctx = {"k": 0, "n": a.layers, "back": True, "theme": a.theme, "depth_model": MODEL, "img": img, "dn": dn,
           "alpha": alpha, "hole": hole}
    rules = parallax_rules.weigh(RULES, ctx, a.forced)
    hole = ctx["hole"]
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
