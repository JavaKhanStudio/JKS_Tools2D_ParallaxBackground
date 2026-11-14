"""3. bands (r533: split out of depth_page.py): the depth clustered into bands, then the colour passes (by_colour,
see_through and its sky_hued) and the pieces handed back (strays, loose, necks)."""
import numpy as np
from dp_common import SKY, band_means, filled, grow
from dp_outline import alpha_of, topline
from PIL import Image
from scipy import ndimage


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


def colour_bands(L, n):
    pal = np.array([(70, 90, 160), (90, 160, 120), (220, 180, 80), (220, 90, 70), (200, 80, 200), (240, 240, 240)])
    return Image.fromarray(pal[L % len(pal)].astype(np.uint8))
