"""A layer's outline (r533: split out of depth_page.py): where band k shows, its top edge going on behind what hides
it (topline, r322, r373), the layer's pixels (alpha_of), and the repaint's start behind a nearer thing."""
import numpy as np
from dp_common import mirrored, runs_of
from scipy import ndimage


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


def alpha_of(L, k):
    """Layer k's pixels: its band, and every nearer pixel under the band's top edge; over a run of columns where a
    nearer thing hides the band, every pixel under it (the sky there too: band k goes on behind, r373)."""
    rows = np.arange(L.shape[0])[:, None]
    has = unsliver(shown(L, k)[0])
    behind = ~has & (L > k).any(axis=0)  # a gap in the band with nothing nearer in it stays a gap
    return (L == k) | ((rows >= topline(L, k)[None]) & ((L > k) | behind[None]))


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
