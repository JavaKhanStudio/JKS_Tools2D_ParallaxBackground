"""depth_page's helpers that see the painting as its tiled layer shows it, its two ends joined (r533: split out of
depth_page.py): looped, grow, fill, islands, the runs of columns, the bands' mean colours. Sets sys.path for the
tools/ and tools/r239-ai-strips modules the other dp_ modules import."""
import os
import sys

import numpy as np
from PIL import Image
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
AI = os.path.join(TOOLS, "r239-ai-strips")
for _p in (TOOLS, AI):  # depth_page.py puts them first; a dp_ module imported on its own finds them last
    if _p not in sys.path:
        sys.path.append(_p)


SKY = 0.01  # disparity, normalised: the depth's farthest 1 % is clipped to 0, the sky


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
