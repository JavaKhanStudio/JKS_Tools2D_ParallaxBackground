"""3. bands, the things in them (r533: split out of depth_page.py): each SAM mask a band's edge cuts given whole to the
band at its foot (foot_of, r386), and the fields SAM outlined moved, agreeing, mended across the join and kept whole
(r374, r387)."""
import numpy as np
from dp_common import grow, looped
from PIL import Image
from scipy import ndimage

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


def outlines(img, masks):
    """The painting dimmed, each SAM mask in a colour of its own and outlined in white."""
    rng = np.random.default_rng(3)
    out = img.astype(np.float32) * 0.35
    for m in sorted(masks, key=lambda m: -m.sum()):
        out[m] = img[m] * 0.35 + rng.integers(40, 255, 3) * 0.65
        out[m & ~ndimage.binary_erosion(m)] = 255
    return Image.fromarray(out.astype(np.uint8))
