"""Cut a painted strip out of its flat sky into a parallax layer PNG with alpha (r239).

    python3 tools/r239-ai-strips/layer.py painted.png out.png [--pixel 4 --colours 12]

The sky colour is read from the strip's top rows; a pixel's alpha rises from 0 to 1 as its colour
moves away from it (between --near and --far, in 0..255 RGB distance; --far defaults to the
layer's own contrast). The strip loops already, and
this is per pixel, so the alpha loops too.
--mask (sil.py's) --grow G: the silhouette helps the key, for a layer whose colours come near the sky's (snow under
a pale sky keys half clear): alpha is 1 inside the mask, and the colour key's only within G px of it, so what SD paints
far from the shape (a horizon behind hills) goes too. Pines grow past their shape: give them a larger G (r246).
Under --mask each pixel is keyed against the sky round it, the clear pixels past G px of the mask (r358): SD's sky
warms toward the foot and has clouds, and against the top rows' colour it stayed half opaque round the shape, a glow.
Clear pixels nearer the mass's colour than the sky's are the mass SD painted past the mask, not sky: they are not read (r382).
--gap T: the foot of a gap SD painted deeper than the shape is sky or far haze, which the mask made opaque (r263):
pixels less than T of the way from the sky to the mass's colour, open to the clear sky, key along that line (0: off).
--haze H: a far range SD painted pale round the shape (pale peaks between two near slopes, a haze plain at a mesa's
foot), open to the sky, keyed by ungap and unglow in blobs, is kept whole at alpha H instead, its edges ramped to the
mass and the sky (r401, Simon's answer: half clear, one even alpha) (0: off).
--edge E: alpha still half clear more than E px from a solid pixel is SD's haze or a ghost behind the shape, not a
soft edge: it goes clear or solid (0: off).
RULES (tools/parallax_rules.py, r400): a fix right for one model or one kind of layer only, weighed per strip on what
--kind and --ckpt say painted it; --rule NAME=on|off forces one, --rules-json PATH writes what each did. misty_pines:
dreamshaper_8 paints farther pines in mist at a pines layer's foot, pale ghosts inside the mask that scroll at the
front's speed (r364); on that model's pines they key along the sky-to-mass line. Another model's pale pines are
crisp, a second row it meant (openjourney, novaCartoon), or not there (SDXL): left as painted. Simon kept it on
after seeing it on and off in pines-lab (r421). wide_mass (r454): Z-Image keeps sil.py's shape less than the UNets do;
the mass it paints past the mask by more than --grow (pale hills in a valley of the mask, fronds, spires) is kept, not
cut flat at --grow's reach.
Alpha islands that do not touch the layer's main mass (a speck SD left in the sky) are cut, with the
faint edge around them; connectivity is taken around the loop. --keep-islands keeps them, for a layer
meant to be scattered (clouds).
A creature SD painted inside the mass or standing on it (a bird, an animal, a person: creature.py, r261, r349, r362) exits 4, after the PNG is written;
--no-creatures skips the check.
--edges-of CUT: a front variant (gen.py variants) starts and ends on exactly the front's painted columns, but this
cut weighs the whole strip (islands, the far range, RULES) and keyed them to another alpha: a step at every join of
the SEQUENCE, lint (e) (r474). The front's cut CUT is laid back on the outer EDGE_KEEP px, blended over EDGE_RAMP px
(gen.py's 8 and 16), so every segment of the layer ends on the front's own pixels.
Its alpha islands keep what the front's cut keeps solid on those columns: the same painting, cut whole.
--pixel N: pixel art. Box-downscale by N, quantise to --colours colours (one palette for the
layer, or --palette's), and cut alpha hard at 50%: every pixel is one palette colour, fully opaque or fully clear.
The PNG is the small one; the page draws it scaled N times with the Nearest filter.
"""
import argparse
import json
import os
import sys
from collections import deque

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import parallax_rules


def blur(a):
    """3x3 box, wrapping along X (the strip loops) and clamped along Y."""
    p = np.pad(a, [(1, 1), (0, 0)] + [(0, 0)] * (a.ndim - 2), mode="edge")
    p = np.concatenate([p[:, -1:], p, p[:, :1]], axis=1)
    out = sum(p[dy:dy + a.shape[0], dx:dx + a.shape[1]] for dy in range(3) for dx in range(3))
    return out / 9


def spread(m, n):
    """m grown by n px (n < 0: shrunk), wrapping along X."""
    m = m.copy()
    for _ in range(abs(n)):
        g = m | np.roll(m, 1, 1) | np.roll(m, -1, 1) if n > 0 else m & np.roll(m, 1, 1) & np.roll(m, -1, 1)
        if n > 0:
            g[1:] |= m[:-1]
            g[:-1] |= m[1:]
        else:
            g[1:] &= m[:-1]
            g[:-1] &= m[1:]
        m = g
    return m


def components(mask):
    """(label, sizes): each 4-connected component of mask numbered from 1, sizes[n - 1] its size; column 0 touches
    column w-1."""
    h, w = mask.shape
    label = np.zeros(mask.shape, np.int32)
    sizes = [0]
    for y0, x0 in zip(*np.nonzero(mask)):
        if label[y0, x0]:
            continue
        n = len(sizes)
        label[y0, x0] = n
        todo, size = deque([(y0, x0)]), 0
        while todo:
            y, x = todo.popleft()
            size += 1
            for ny, nx in ((y - 1, x), (y + 1, x), (y, (x - 1) % w), (y, (x + 1) % w)):
                if 0 <= ny < h and mask[ny, nx] and not label[ny, nx]:
                    label[ny, nx] = n
                    todo.append((ny, nx))
        sizes.append(size)
    return label, sizes[1:]


def box(a, r):
    """Sum over a (2r+1)-px square round each pixel, wrapping along X and clamped along Y."""
    p = np.pad(a, [(r + 1, r), (0, 0)] + [(0, 0)] * (a.ndim - 2), mode="edge")
    p = np.concatenate([p[:, -r - 1:], p, p[:, :r]], axis=1).cumsum(0).cumsum(1)
    h, w = a.shape[:2]
    n = 2 * r + 1
    return p[n:n + h, n:n + w] - p[:h, n:n + w] - p[n:n + h, :w] + p[:h, :w]


def local_sky(img, sky, clear, r):
    """The sky behind each pixel: the mean of the clear pixels within r px (wider where there are none), else the
    top rows' sky. SD paints a sky that changes down the strip and round the shape (a warmer haze at its foot,
    clouds): keyed against the top rows' colour, that haze stays half opaque and draws a glow over the layer
    behind (r358)."""
    out = np.broadcast_to(sky, img.shape).copy()
    known = clear.astype(np.float64)
    done = np.zeros(clear.shape, bool)
    for k in (1, 2, 4):
        # a box twice (a tent), or the edges of the box draw squares in the key
        q = max(r * k // 2, 1)
        wsum = box(box(known, q), q)
        here = ~done & (wsum > 0.5)
        out[here] = box(box(img * known[..., None], q), q)[here] / wsum[here][:, None]
        done |= here
    return out


def masslike(img, sky, m):
    """The pixels past half way from the top rows' sky to the mass's median colour (8 px inside m). SD paints the mass
    a few px past sil.py's mask (a dune's far sand, a ridge): read as sky by local_sky, they made the sky round the
    shape the mass's own colour, and its pale foot or crest keyed out with it, notches ungap opened to the sky (r382)."""
    inner = spread(m, -8)
    if not inner.any():
        return np.zeros(m.shape, bool)
    axis = np.median(img[inner], axis=0) - sky
    return (img - sky) @ axis / max(float(axis @ axis), 1.0) > 0.5


def islands(mask, anchor=None):
    """Each 4-connected component of mask but the largest, and but those touching anchor, as one mask; column 0
    touches column w-1."""
    label, sizes = components(mask)
    if len(sizes) <= 1:
        return np.zeros(mask.shape, bool)
    keep = [int(np.argmax(sizes)) + 1] + ([] if anchor is None else list(np.unique(label[anchor & mask])))
    return (label > 0) & ~np.isin(label, keep)


def sky_to_mass(img, sky, alpha):
    """Each pixel's place on the line from the sky (0, each pixel's own under --mask) to the mass's median colour (1)."""
    mass = alpha > 0.95
    axis = np.median(img[mass], axis=0) - sky
    return ((img - sky) * axis).sum(-1) / np.maximum((axis * axis).sum(-1), 1.0)


def ungap(img, sky, alpha, reach):
    """alpha with the feet of gaps keyed: a gap between trees SD painted deeper than the silhouette ends in sky or
    far haze, which the mask (or a key that ends at the layer's own distance) makes opaque, a pale spot in a dark
    treeline (r263). Each pixel's place t on the line from the sky (0) to the mass's median colour (1); the pixels
    under --gap (and not whiter than the sky, snow) that reach the clear sky through each other key t / --gap."""
    if not (alpha > 0.95).any():
        return alpha
    t = sky_to_mass(img, sky, alpha)
    label, _ = components((t > -0.15) & (t < reach))
    open_ = np.unique(label[alpha < 0.05])
    gap = np.isin(label, open_[open_ > 0])
    return np.where(gap, np.minimum(alpha, np.clip(t / reach, 0, 1)), alpha)


def unglow(alpha, edge):
    """alpha with its half-clear haze made clear or solid: past edge px of a solid pixel a pixel is not a silhouette's
    soft edge but a glow SD painted round it or a pale ghost behind it, which drawn half opaque over the layer behind
    shows as a shadow round the shape (r358). Its alpha goes through a steep ramp, 0 below 0.4, 1 above 0.6."""
    solid = alpha >= 0.95
    far = ~spread(solid, edge) & (alpha < 0.95)
    out = alpha.copy()
    out[far] = np.clip((alpha[far] - 0.4) / 0.2, 0, 1)
    # within edge px of the solid but more than 3 * edge px of anything under half: no soft edge, a pale mass keyed
    # 0.7 round sil.py's 8 px core, which the ramp made solid further out, a line along the mask inside it (r454)
    inside = ~far & (alpha < 0.95) & ~spread(out < 0.5, 3 * edge)
    out[inside] = np.clip((alpha[inside] - 0.4) / 0.2, 0, 1)
    return out


# far_range (r401): a piece of the pixels between FAR_LO and FAR_HI of the way from the top rows' sky to the mass,
# wider than 2 * FAR_OPEN px, its part under --gap within --grow of the mask wider than 2 * FAR_CORE px over FAR_MIN px
# or more (a 1024 px strip's), beside the clear sky, and outlined on it: the median step of t across its outline on the
# sky over FAR_STEP. A haze SD paints round the shape is thinner (r358); a sky warming down the strip has no outline.
# Its alpha is --haze from FAR_FULL to --gap and rises to 1 at FAR_HI: a threshold across a range SD shaded toward the
# mass's colour cut it in two (ds8-beach l0). It falls to 0 at t 0 over the pixels down to FAR_FAINT joined to it: a
# haze plain fading into the sky, cut at a threshold or a distance, drew a line (ds8-postapo l0).
FAR_LO, FAR_FULL, FAR_HI, FAR_OPEN, FAR_CORE, FAR_MIN, FAR_STEP, FAR_FAINT, FAR_REACH = 0.25, 0.4, 0.75, 3, 8, 150, 0.015, 0.05, 12


def row_sky(t, m):
    """t of each row's sky (column): its palest pixels off the mask (3rd percentile), carried down from the row above
    where the mask leaves under half the row (what is left there is the range)."""
    out, last = np.zeros((t.shape[0], 1)), 0.0
    for y in range(t.shape[0]):
        if (~m[y]).mean() >= 0.5:
            last = float(np.percentile(t[y][~m[y]], 3))
        out[y] = last
    return out


def far_range(img, top, alpha, m, grow, reach, haze):
    """(region, alpha, sky): the pale far range painted round the shape: SD's farther peaks (oj4-postapo l0), the haze
    plain at a mesa's foot (ds8-postapo l0), a farther pine in a V between two near ones (oj4-forest l3). Against each
    pixel's own sky (local_sky's) its t runs from 0.1 to 0.5, where that sky took the range's own clear tops: ungap and
    unglow keyed it in blobs, half solid, half sky. Against the top rows' sky it is one even t. Its faint edge is keyed
    against its row's sky (row_sky): against the top rows', a sky warming beside it kept a glow (layer_test); against
    local_sky's, a range at FAR_LO was half kept, half sky (ds8-beach l0). sky: the sky each pixel is un-mixed against."""
    none = np.zeros(m.shape, bool), np.zeros(m.shape), None
    inner = spread(m, -8)
    if not inner.any():
        return none
    mass = np.median(img[inner], axis=0)
    t = (img - top) @ (mass - top) / max(float((mass - top) @ (mass - top)), 1.0)
    band = (t > FAR_LO) & (t < FAR_HI)
    band &= spread(spread(band, -FAR_OPEN), FAR_OPEN)
    label, sizes = components(band)
    k = img.shape[1] / 1024
    zone, pale = spread(m, grow), t < reach
    step = np.max([np.abs(t - np.roll(t, s, ax)) for s, ax in ((1, 0), (-1, 0), (1, 1), (-1, 1))], axis=0)
    keep = []
    for n, size in enumerate(sizes, 1):
        if size < FAR_MIN * k * k:
            continue
        piece = label == n
        near = piece & zone & pale
        core = near & spread(spread(near, -FAR_CORE), FAR_CORE)
        rim = spread(piece, 1) & ~band & (t <= FAR_LO)  # its outline on the sky
        open_ = (alpha[rim] < 0.05).any()  # beside the clear sky
        if core.sum() >= FAR_MIN * k * k and open_ and np.median(step[rim]) > FAR_STEP:
            keep.append(n)
    if not keep:
        return none
    body = np.isin(label, keep)
    # round it, against its row's sky: its rim, which the opening took, and the mass's edge on it (keyed against a sky
    # the range made, they drew a light line round it over the layer behind, and holes at the mass's foot); and the
    # pixels down to FAR_FAINT joined to it (cut at a threshold or a distance, a haze plain fading into the sky drew
    # a line, ds8-postapo l0)
    t0 = row_sky(t, m)
    t = np.where(body, t, t - t0)
    far = body | spread(body, 2 * FAR_OPEN) & (t > 0)
    # within FAR_REACH px, fading out over it: further on it took the clouds over a city (ds8-postapo l2)
    reach_px, fade, ring = round(FAR_REACH * k), np.zeros(t.shape), far
    for _ in range(reach_px):
        fade += ring
        ring = spread(ring, 1)
    label, _ = components(((t > FAR_FAINT) & (fade > 0)) | far)
    on = np.unique(label[far])
    far = np.isin(label, on[on > 0])
    sky = np.where((far & ~body)[..., None], top + t0[..., None] * (mass - top), top)
    a = haze * np.clip(t / FAR_FULL, 0, 1) + (1 - haze) * np.clip((t - reach) / (FAR_HI - reach), 0, 1)
    a = np.where(body | (t > FAR_LO), a, a * fade / reach_px)
    return far, a, sky


# misty_pines (r400): pines nearer the sky than MISTY_HI of the way to the mass, open to the clear sky through each
# other, where they are wider than 2 * MISTY_CORE px, key along t from MISTY_LO (clear) to MISTY_HI; at least MISTY_MIN px of them
# in a 1024 px strip, or the strip has none. dreamshaper_8's foot pines run from t 0.5 at their tips to 0.85; a dark
# pine's lit edge reaches as pale but is 1-2 px wide.
MISTY_CKPTS = ("dreamshaper_8.safetensors",)
MISTY_LO, MISTY_HI, MISTY_CORE, MISTY_MIN = 0.6, 0.85, 2, 300


def misty(img, sky, alpha):
    """(region, t): the pale pines open to the sky inside the layer (misty_pines' measure) and sky_to_mass's t."""
    if not (alpha > 0.95).any():
        return np.zeros(alpha.shape, bool), np.zeros(alpha.shape)
    t = sky_to_mass(img, sky, alpha)
    clear = alpha < 0.5  # the key's half-clear sky round a pine is no pine of its own
    pale = (t > -0.15) & (t < MISTY_HI) & ~clear
    label, _ = components(pale | clear)
    open_ = np.unique(label[clear])
    region = np.isin(label, open_[open_ > 0]) & pale
    # opened: a lit edge that joins a misty pine is still a px or two wide, and stays
    return spread(spread(region, -MISTY_CORE), MISTY_CORE) & region, t


def measure_misty(c):
    c["misty"], c["t"] = misty(c["img"], c["sky"], c["alpha"])
    k = c["img"].shape[1] / 1024
    return c["misty"].sum() >= MISTY_MIN * k * k


def clear_misty(c):
    """misty_pines' act: c["alpha"] keyed along t over the region; returns the opaque px it took (alpha summed)."""
    m = c["misty"]
    before = c["alpha"]
    c["alpha"] = np.where(m, np.minimum(before, np.clip((c["t"] - MISTY_LO) / (MISTY_HI - MISTY_LO), 0, 1)), before)
    return round(float((before - c["alpha"]).sum()))


# wide_mass (r454): Z-Image (a DiT, gen.py's) keeps sil.py's shape less than the UNets do: it paints the layer's mass
# past the mask by more than --grow (pale hills in a valley of the mask, R6c zit-forest l0), and the key cut them flat
# at --grow's reach, a straight floor and steps along the skyline. The masslike pixels joined to the mask through each
# other, past --grow, at least WIDE_MIN px in a 1024 px strip, are kept solid, their outline keyed along t to WIDE_EDGE.
WIDE_CKPTS = ("z-image",)
WIDE_MIN, WIDE_EDGE = 150, 0.5


def wide(img, top, m, grow, ranged):
    """(past, whole): the mass painted past --grow, masslike pixels outside it joined to the mask through each other,
    not the far range's (wide_mass' measure); and the masslike pieces it is part of, inside --grow too, where the key
    against local_sky's sky (which the hill's colour reaches) left them half clear and unglow cleared them."""
    zone, like = spread(m, grow), masslike(img, top, m) & ~ranged
    label, _ = components(like | zone)
    on = np.unique(label[m])
    past = np.isin(label, on[on > 0]) & ~zone & like
    label, _ = components(like)
    on = np.unique(label[past])
    return past, np.isin(label, on[on > 0])


def measure_wide(c):
    c["wide"], c["wide_whole"] = wide(c["img"], c["top"], c["m"], c["grow"], c["ranged"])
    k = c["img"].shape[1] / 1024
    return c["wide"].sum() >= WIDE_MIN * k * k


def keep_wide(c):
    """wide_mass' act: the wide mass solid, its outline on the sky keyed along t; returns the opaque px it added."""
    w, before = c["wide_whole"], c["alpha"]
    rim = spread(w, 2) & ~w & ~c["ranged"]
    t = sky_to_mass(c["img"], c["sky"], before)
    c["alpha"] = np.where(w, 1.0, np.where(rim, np.maximum(before, np.clip(t / WIDE_EDGE, 0, 1)), before))
    return round(float((c["alpha"] - before).sum()))


RULES = [
    parallax_rules.Rule(
        "misty_pines", "pines dreamshaper_8 paints in mist at the forest's foot key out along the sky-to-mass line",
        when=[("a pines layer", lambda c: c["kind"] == "pines"),
              ("painted by dreamshaper_8", lambda c: os.path.basename(c["ckpt"] or "") in MISTY_CKPTS),
              ("wide pale pines open to the sky", measure_misty)],
        act=clear_misty),
    parallax_rules.Rule(
        "wide_mass", "the mass Z-Image paints past sil.py's mask by more than --grow stays, not cut flat at its reach",
        when=[("painted by Z-Image", lambda c: os.path.basename(c["ckpt"] or "").startswith(WIDE_CKPTS)),
              ("under --mask", lambda c: c["m"] is not None),
              ("masslike px joined to the mask past --grow", measure_wide)],
        act=keep_wide),
]


def unspeck(alpha, cut):
    """alpha with the islands of cut gone, and their faint edge: alpha within 3 px of them, outside the main mass,
    down to the alpha around that box (outside the mass, blurred in). In clear sky that is 0; in haze SD painted half
    clear it is the haze's own, where a 0 punched a square hole (r264)."""
    mass = (alpha > 0.5) & ~cut
    near = blur(blur(blur(cut.astype(np.float32)))) > 0
    known = (~near & ~mass).astype(np.float32)
    fill, wsum = alpha * known, known.copy()
    for _ in range(6):
        fill, wsum = blur(fill), blur(wsum)
    around = np.where(wsum > 1e-6, fill / np.maximum(wsum, 1e-6), 0.0)
    edge = near & ~mass
    out = alpha.copy()
    out[edge] = np.minimum(alpha[edge], around[edge])
    return out


def parse_args():
    ap = argparse.ArgumentParser()
    ap.add_argument("src")
    ap.add_argument("out")
    ap.add_argument("--near", type=float, default=18)
    ap.add_argument("--far", type=float, default=0, help="0: 0.85 x the layer's median distance from the sky")
    ap.add_argument("--mask", help="sil.py's _mask.png: opaque inside it, keyed only within --grow px of it")
    ap.add_argument("--grow", type=int, default=12)
    ap.add_argument("--gap", type=float, default=0.6, help="key pale pixels open to the sky up to this far toward the mass (0: off)")
    ap.add_argument("--haze", type=float, default=0.5, help="a pale far range round the shape: one alpha (0: off)")
    ap.add_argument("--edge", type=int, default=2, help="half-clear alpha past this many px of a solid pixel goes clear or solid (0: off)")
    ap.add_argument("--keep-islands", action="store_true", help="keep alpha islands apart from the main mass")
    ap.add_argument("--edges-of", metavar="CUT", help="a front variant: the front's cut, laid back on its outer columns")
    ap.add_argument("--pixel", type=int, default=0)
    ap.add_argument("--colours", type=int, default=12)
    ap.add_argument("--no-creatures", action="store_true", help="skip creature.py's check (exit 4)")
    ap.add_argument("--palette", help="--pixel: build the palette from this cut layer's opaque colours (one for all segments)")
    ap.add_argument("--kind", help="the layer's kind (ai_page.py's: pines, mountains...), for RULES")
    ap.add_argument("--ckpt", help="the checkpoint that painted it, for RULES")
    ap.add_argument("--rule", action="append", default=[], metavar="NAME=on|off", help="force a rule: "
                    + ", ".join(r.name for r in RULES))
    ap.add_argument("--rules-json", help="write each rule's record (applied, conditions held, px moved) here")
    a = ap.parse_args()
    try:
        a.forced = parallax_rules.parse_forced(a.rule, RULES)
    except ValueError as e:
        ap.error(str(e))
    return a


def key(img, sky, a, records=None, regions=None):
    """(alpha, sky): the layer's alpha, the colour key helped by --mask, its gaps keyed, RULES weighed and its islands
    cut; and the sky it was keyed against, each pixel's (local_sky's under --mask). Each rule's record is appended to
    records; regions["far"] is the far range (far_range's), for unmix."""
    m, top = None, sky
    if a.mask:
        m = np.asarray(Image.open(a.mask).convert("L").resize(img.shape[1::-1])) > 127
        sky = local_sky(img, sky, ~spread(m, a.grow) & ~spread(masslike(img, sky, m), 2), a.grow + 8)
    d = np.linalg.norm(img - sky, axis=2)
    # The ramp ends near the layer's own distance from the sky: a fixed one marks a pixel half
    # sky, half hill as solid, a light line along the silhouette.
    far = a.far if a.far else 0.85 * float(np.median(d[d > 48])) if (d > 48).any() else 48
    alpha = np.clip((d - a.near) / (far - a.near), 0, 1)
    if m is not None:
        # SD moves an outline by a few px: opaque only 8 px inside the shape, or sky comes in with it.
        alpha = np.where(spread(m, -8), 1.0, np.where(spread(m, a.grow), alpha, 0.0))
    ranged, haze = np.zeros(alpha.shape, bool), getattr(a, "haze", 0.5)
    if m is not None and a.gap and haze:
        ranged, range_alpha, range_sky = far_range(img, top, alpha, m, a.grow, a.gap, haze)
    if a.gap:
        alpha = ungap(img, sky, alpha, a.gap)
    if a.edge:
        alpha = unglow(alpha, a.edge)
    if ranged.any():
        alpha = np.where(ranged, range_alpha, alpha)
        sky = np.where(ranged[..., None], range_sky, sky)  # un-mixed against the sky over it, not its own clear tops
        print(f"{a.out}: far range: {int(ranged.sum())} px, alpha {haze} on the pale", file=sys.stderr)
    if regions is not None:
        regions["far"] = ranged
    ctx = {"kind": getattr(a, "kind", None), "ckpt": getattr(a, "ckpt", None), "img": img, "sky": sky, "alpha": alpha,
           "top": top, "m": m, "grow": a.grow, "ranged": ranged}
    weighed = parallax_rules.weigh(RULES, ctx, getattr(a, "forced", None))
    alpha = ctx["alpha"]
    for r in weighed:
        if r["applied"]:
            print(f"{a.out}: rule {r['rule']}: {r['moved']} px", file=sys.stderr)
    if records is not None:
        records += weighed
    if not a.keep_islands:
        # a far range at --haze joins what stands in it to the mass (ds8-postapo l0's mesa, in its haze plain)
        cut = islands((alpha > 0.5) | ranged, front_edges(a, alpha.shape))
        if cut.any():
            alpha = unspeck(alpha, cut)
            print(f"{a.out}: {int(cut.sum())} px of alpha islands cut", file=sys.stderr)
    return alpha, sky


# edges_of (r474): gen.py variants lays the source's own outer 8 px back over 16 px; the cut does the same with its cut.
EDGE_KEEP, EDGE_RAMP = 8, 16


def front_edges(a, shape):
    """--edges-of: the front cut's solid pixels on the columns laid back (EDGE_KEEP + EDGE_RAMP at each end), else None.
    What stands there in the front stands in the variant too, its painting the same: kept whole when the variant's
    middle makes it an island, or the lay-back fades a cut stub over the ramp (r474: beach-inpaint's trunk)."""
    if not getattr(a, "edges_of", None):
        return None
    f = np.asarray(Image.open(a.edges_of).convert("RGBA"))[..., 3] > 127
    if f.shape != shape:
        sys.exit(f"--edges-of: {f.shape[::-1]} is not the cut's size {shape[::-1]}")
    x = np.arange(shape[1])
    return f & (np.minimum(x, shape[1] - 1 - x) < EDGE_KEEP + EDGE_RAMP)[None, :]


def edges_of(out, front):
    """The RGBA out with front's (the front strip's cut) outer EDGE_KEEP columns at each end laid back, blended into
    out's own over EDGE_RAMP columns, premultiplied: a clear pixel's colour weighs nothing."""
    o, f = (np.asarray(x.convert("RGBA")).astype(np.float64) for x in (out, front))
    if o.shape != f.shape:
        sys.exit(f"--edges-of: {front.size} is not the cut's size {out.size}")
    x = np.arange(o.shape[1])
    r = np.clip((np.minimum(x, o.shape[1] - 1 - x) - EDGE_KEEP + 1) / (EDGE_RAMP + 1), 0, 1)[None, :, None]
    a = f[..., 3:] * (1 - r) + o[..., 3:] * r
    pm = f[..., :3] * f[..., 3:] * (1 - r) + o[..., :3] * o[..., 3:] * r
    rgb = np.where(a > 0, pm / np.maximum(a, 1e-6), f[..., :3] * (1 - r) + o[..., :3] * r)
    return Image.fromarray(np.dstack([rgb, a]).round().clip(0, 255).astype(np.uint8), "RGBA")


def unmix(img, sky, alpha, own=None):
    """The RGBA layer. The pixels of own keep their own colour, un-mixed: a far range half clear took the solid
    colours spread into it near the mass and its own farther off, in bands (r401)."""
    # Un-mix the sky out of the edge pixels, so a light fringe does not ring the layer.
    a3 = np.maximum(alpha, 1e-3)[..., None]
    rgb = np.clip((img - sky * (1 - a3)) / a3, 0, 255)
    # Edge pixels take their colour from the solid ones next to them. SD paints a rim lighter
    # than the sky along a silhouette, which no un-mix removes (a 1 px light outline on screen),
    # and a Linear filter blends the colour of clear pixels into the edge too.
    solid = (alpha >= 0.95).astype(np.float32)
    fill, wsum = rgb * solid[..., None], solid.copy()
    for _ in range(6):  # spread the solid colours outward, ~2^6 px
        fill, wsum = blur(fill), blur(wsum)
    # Past their reach (a pale ridge, alpha under 0.95 over more than ~6 px) the un-mixed colour stays:
    # fill / wsum there is 0 / 0, and was drawn black (r242's "bird").
    reached = (wsum > 1e-6)[..., None]
    if own is not None:
        reached &= ~own[..., None]
    rgb = np.where(solid[..., None] > 0, rgb, np.where(reached, fill / np.maximum(wsum, 1e-6)[..., None], rgb))
    rgba = np.dstack([rgb, alpha * 255]).astype(np.uint8)
    return Image.fromarray(rgba, "RGBA")


def pixelate(out, a):
    """--pixel: out box-downscaled, quantised to one palette and cut hard at half alpha."""
    w, h = out.size
    small = out.resize((w // a.pixel, h // a.pixel), Image.BOX)
    arr = np.asarray(small).copy()
    opaque = arr[..., 3] >= 128
    rgb = Image.fromarray(arr[..., :3])
    if a.palette:
        # The segments of one SEQUENCE layer share a palette, or their joins change colour.
        ref = Image.open(a.palette).convert("RGBA")
        ref = np.asarray(ref.resize((ref.width // a.pixel, ref.height // a.pixel), Image.BOX))
        ref = Image.fromarray(ref[ref[..., 3] >= 128][None, :, :3])  # its opaque colours, one row
        ref = ref.quantize(a.colours, method=Image.Quantize.MEDIANCUT)
        pal = rgb.quantize(palette=ref, dither=Image.Dither.NONE)
    else:
        pal = rgb.quantize(a.colours, method=Image.Quantize.MEDIANCUT)
    arr[..., :3] = np.asarray(pal.convert("RGB"))
    arr[..., 3] = np.where(opaque, 255, 0)
    arr[~opaque, :3] = 0
    return Image.fromarray(arr, "RGBA")


def check(out, img, sky, alpha, a):
    """Exits 3 on art at the top row, 4 on a creature in the mass or standing on it (unless --no-creatures)."""
    cut = int((np.asarray(out)[0, :, 3] > 0).sum())
    if cut:
        # Art on the top row is cut flat on screen (the parallax_lab lint's fault a).
        print(f"{a.out}: art cut flat at the top edge over {cut} columns", file=sys.stderr)
        sys.exit(3)
    if not a.no_creatures:
        import creature  # it imports this module
        rgb = img.astype(np.float64)
        k = img.shape[1] / 1024  # creature.py's sizes are for a 1024 px strip (r349: SDXL's are 1536)
        r = int(12 * k)
        found = creature.find(rgb, alpha, int(50 * k * k), int(3000 * k * k), r=r)
        found += creature.on_ridge(rgb, alpha, sky, int(50 * k * k), r)
        found += creature.on_neck(rgb, alpha, sky, r)
        for x, y, size in found:
            print(f"{a.src}: a creature? {size} px blob at x={x} y={y}", file=sys.stderr)
        if found:
            sys.exit(4)


def main():
    a = parse_args()
    img = np.asarray(Image.open(a.src).convert("RGB")).astype(np.float32)
    sky = np.median(img[:8].reshape(-1, 3), axis=0)
    records, regions = [], {}
    alpha, behind = key(img, sky, a, records, regions)
    if a.rules_json:
        with open(a.rules_json, "w") as f:
            json.dump(records, f, indent=1)
    out = unmix(img, behind, alpha, regions.get("far"))
    if a.edges_of:
        out = edges_of(out, Image.open(a.edges_of))
        alpha = np.asarray(out)[..., 3] / 255.0
    if a.pixel > 1:
        out = pixelate(out, a)
    out.save(a.out)
    check(out, img, sky, alpha, a)


if __name__ == "__main__":
    main()
