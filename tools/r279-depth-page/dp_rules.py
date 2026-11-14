"""depth_page.RULES (r378, tools/parallax_rules.py; r533: split out of depth_page.py): fixes for one case, weighed on
each layer's hole before its repaint."""
import re

import dp_common  # noqa: F401  (sys.path: parallax_rules)
import numpy as np
import parallax_rules
from dp_common import SKY, grow, looped
from dp_models import MODEL
from scipy import ndimage


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
