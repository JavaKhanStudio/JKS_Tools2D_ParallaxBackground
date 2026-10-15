"""depth_page's rules (r378), see_through's sky by hue (r380), foot_of's hidden feet (r386) and the moved fields kept
whole and seeding the layers behind with no track drawn there (r387, r426), on paintings made up here: no GPU, no
files from a run.

    ~/ComfyUI/venv/bin/python tools/r279-depth-page/rules_test.py

A sky over a land, a crown (the hole) hanging into the sky, a twig's tip left beside it in the back layer, a hedge's
dark line in the land touching the hole, a broad dark mountain touching it too, a dark cloud against the crown, the sky's own blue in a narrow gap
between two white clouds under the crown, and a thin dark stroke the painting has far out in the sky.
tools/r387-fields/mutate.sh takes each part of r387's fix out and proves a case here fails.
"""
import argparse
import os
import shutil
import sys
import tempfile
import types
import unittest

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import depth_page as dp
import parallax_rules

W, H = 256, 128
LAND = 80  # rows under it are land


def scene():
    img = np.zeros((H, W, 3), np.uint8)
    img[:LAND] = (150, 180, 220)
    img[LAND:] = (110, 150, 70)
    dn = np.zeros((H, W))
    dn[LAND:] = 0.2
    hole = np.zeros((H, W), bool)
    hole[10:40, 100:160] = True  # the crown
    hole[LAND + 20:, :] = True  # a nearer band's land
    twig = np.zeros((H, W), bool)
    for x in range(160, 180):  # a 2 px twig leaving the crown's side, rising
        y = 30 - (x - 160) // 2
        twig[y:y + 2, x] = True
    hedge = np.zeros((H, W), bool)
    hedge[LAND + 12:LAND + 14, 20:90] = True
    hedge[LAND + 12:LAND + 20, 88:90] = True  # down to the hole
    mountain = np.zeros((H, W), bool)
    for x in range(180, 256):
        mountain[LAND - min(x - 180, 255 - x) // 2:LAND + 20, x] = True
    cloud = np.zeros((H, W), bool)
    cloud[14:30, 80:100] = True  # broad, the sky round it
    white = np.zeros((H, W), bool)
    white[40:60, 100:126] = True
    white[40:60, 132:160] = True  # the gap between them, x 126-131, is sky
    gap = np.zeros((H, W), bool)
    gap[40:60, 126:132] = True
    stroke = np.zeros((H, W), bool)
    stroke[12:14, 20:40] = True  # 60 px from the crown
    img[twig] = (40, 40, 40)
    img[stroke] = (40, 40, 40)
    img[cloud] = (60, 60, 70)
    img[white] = (250, 250, 250)
    img[hedge] = (40, 60, 30)
    img[mountain] = (50, 60, 90)
    dn[mountain] = 0.1
    return img, dn, hole, twig, hedge, mountain, cloud, gap, stroke


def ctx(k=0, theme="green valley, pine forest", **kw):
    img, dn, hole, *_ = scene()
    c = {"k": k, "n": 4, "theme": theme, "depth_model": dp.MODEL, "img": img, "dn": dn,
         "alpha": np.ones((H, W), bool), "hole": hole.copy()}
    c.update(kw)
    return c


class SkyTwigsTest(unittest.TestCase):
    def test_a_twig_against_the_sky_is_found_a_hedge_a_mountain_a_cloud_a_gap_and_a_far_stroke_are_not(self):
        img, dn, hole, twig, hedge, mountain, cloud, gap, stroke = scene()
        found = dp.sky_twigs(img, dn, ~hole, hole)
        self.assertGreater((found & twig).sum(), 0.9 * twig.sum())
        self.assertFalse((found & hedge).any())
        self.assertFalse((found & mountain).any())
        self.assertFalse((found & cloud).any())
        self.assertFalse((found & gap).any())
        self.assertFalse((found & stroke).any())

    def test_it_applies_on_the_back_layer_of_a_tree_theme(self):
        c = ctx()
        before = c["hole"].sum()
        (rec,) = parallax_rules.weigh(dp.RULES, c)
        self.assertTrue(rec["applied"])
        self.assertEqual(c["hole"].sum() - before, rec["moved"])
        self.assertTrue(all(rec["when"].values()))

    def test_it_stops_at_the_first_condition_that_fails(self):
        for c, failed in ((ctx(k=1), "the back layer"), (ctx(theme="desert dunes"), "the theme names a tree"),
                          (ctx(depth_model="another depth model"), "depth by Depth Anything V2 Small")):
            before = c["hole"].copy()
            (rec,) = parallax_rules.weigh(dp.RULES, c)
            self.assertFalse(rec["applied"])
            self.assertEqual(list(rec["when"])[-1], failed)
            self.assertFalse(rec["when"][failed])
            self.assertTrue((c["hole"] == before).all())

    def test_a_painting_with_no_twig_leaves_the_hole(self):
        img, _, _, twig, *_ = scene()
        img[twig] = (150, 180, 220)
        c = ctx(img=img)
        (rec,) = parallax_rules.weigh(dp.RULES, c)
        self.assertFalse(rec["applied"])
        self.assertEqual(rec["when"]["thin dark pieces against the sky touch the hole"], False)

    def test_forced_on_or_off_whatever_the_conditions(self):
        c = ctx(theme="desert dunes")
        (rec,) = parallax_rules.weigh(dp.RULES, c, parallax_rules.parse_forced(["sky_twigs=on"], dp.RULES))
        self.assertTrue(rec["applied"])
        self.assertEqual(rec["forced"], "on")
        self.assertGreater(rec["moved"], 0)
        c = ctx()
        (rec,) = parallax_rules.weigh(dp.RULES, c, {"sky_twigs": False})
        self.assertFalse(rec["applied"])
        self.assertEqual(rec["forced"], "off")

    def test_a_forced_rule_must_exist(self):
        with self.assertRaises(ValueError):
            parallax_rules.parse_forced(["no_such=on"], dp.RULES)
        with self.assertRaises(ValueError):
            parallax_rules.parse_forced(["sky_twigs=maybe"], dp.RULES)


def crown_scene():
    """For see_through (r380): a sky (band 0) over hazed land (band 1), a field (band 2) and a crown (band 3) hanging
    into the sky. In the crown, the sky darkened by the leaves at a depth judged near (a gap), a pale grey underside
    beside a white cloud, and a blue flower as near as the leaves; a blue spruce beside it whose shade is the sky's
hue, a tuft of the sky's hue half as bright as it; in band 1, a far forest of the sky's hue."""
    n, h, w = 4, 128, 256
    centres = np.array([0.02, 0.3, 0.55, 0.8])
    L = np.zeros((h, w), int)
    img = np.zeros((h, w, 3), np.uint8)
    dn = np.zeros((h, w))
    img[:] = (85, 110, 150)
    img[70:100, 140:220] = (200, 205, 215)  # white clouds low in the sky
    L[100:], img[100:], dn[100:] = 1, (110, 135, 130), 0.3
    forest = np.zeros((h, w), bool)
    forest[100:108, 20:120] = True
    img[forest], dn[forest] = (65, 90, 130), 0.15
    L[118:], img[118:], dn[118:] = 2, (110, 150, 70), 0.55
    L[10:96, 80:200], img[10:96, 80:200], dn[10:96, 80:200] = 3, (70, 110, 50), 0.82
    gap = np.zeros((h, w), bool)
    gap[22:62, 100:140] = True  # wholly inside the crown, 12 px from the sky, wide: most of the crown round it
    img[gap], dn[gap] = (65, 85, 120), 0.7
    shadow = np.zeros((h, w), bool)
    shadow[84:96, 150:190] = True  # the crown's underside, on the white clouds
    img[shadow], dn[shadow] = (140, 150, 142), 0.7
    flower = np.zeros((h, w), bool)
    flower[30:36, 170:176] = True
    img[flower], dn[flower] = (60, 80, 115), 0.85
    L[10:96, 210:250], img[10:96, 210:250], dn[10:96, 210:250] = 3, (95, 120, 150), 0.82  # a blue spruce
    needles = np.zeros((h, w), bool)
    needles[40:60, 222:238] = True  # its shade, the sky's own hue
    img[needles], dn[needles] = (75, 100, 140), 0.7
    tuft = np.zeros((h, w), bool)
    tuft[64:76, 84:96] = True  # a tuft of the sky's hue, half as bright
    img[tuft], dn[tuft] = (40, 58, 90), 0.7
    return L, dn, centres, img, n, gap, shadow, flower, forest, needles, tuft


class SeeThroughTest(unittest.TestCase):
    def test_the_sky_in_a_crown_depth_judged_near_goes_back_a_shadow_a_flower_a_spruce_a_tuft_and_a_far_forest_stay(self):
        L, dn, centres, img, n, gap, shadow, flower, forest, needles, tuft = crown_scene()
        out = dp.see_through(L.copy(), dn, centres, img, n)
        self.assertGreater((out[gap] == 0).mean(), 0.8)
        self.assertTrue((out[shadow] == 3).all())
        self.assertTrue((out[flower] == 3).all())
        self.assertTrue((out[forest] == 1).all())
        self.assertTrue((out[needles] == 3).all())
        self.assertTrue((out[tuft] == 3).all())

    def test_a_wide_gap_is_seeded_to_its_middle_by_the_crown_s_core_hue(self):
        """The crown's mean over the window round the gap's middle is mostly the gap: it alone would seed only the
        gap's edges, and the rings, which cross no twig darker than the gap's own edge, leave its middle."""
        L, dn, centres, img, n, gap, *_ = crown_scene()
        rgb = img.astype(np.float32)
        own = np.stack([dp.filled(m, c > 0.02, 96) for m, c in dp.band_means(L, rgb, n, 41)])
        own = np.take_along_axis(own, L[None, ..., None].repeat(3, -1), 0)[0]
        seeds = dp.sky_hued(L, dn, centres, rgb, own, n, 41, 96)
        self.assertGreater(seeds[gap].mean(), 0.9)


def pine_scene(foot, trunk=True, right=132):
    """For foot_of (r386): sky (band 0) over a far field (band 1), a trunk (band 3) at x 100-109 when trunk, and a pine
    mask over x 112 to right, rows 60-99, band 1 but its foot rows (94-99), band 2 over the columns of foot: depth's ramp
    to the trunk beside it, or the pine standing on nearer ground."""
    L = np.zeros((H, W), int)
    L[40:] = 1
    if trunk:
        L[20:, 100:110] = 3
    pine = np.zeros((H, W), bool)
    pine[60:100, 112:right] = True
    L[94:100, foot] = 2
    return L, pine


class FootTest(unittest.TestCase):
    def test_a_foot_in_a_nearer_trunk_s_halo_is_hidden_the_pine_goes_to_the_band_most_of_it_is_in(self):
        L, pine = pine_scene(slice(112, 124), right=124)  # its foot all within 14 px of the trunk
        self.assertEqual(dp.foot_of(L, pine, 4), 1)

    def test_a_foot_on_nearer_ground_with_no_nearer_thing_beside_it_still_takes_the_pine(self):
        L, pine = pine_scene(slice(112, 132), trunk=False)
        self.assertEqual(dp.foot_of(L, pine, 4), 2)

    def test_a_foot_partly_in_the_halo_is_judged_on_the_part_seen(self):
        L, pine = pine_scene(slice(112, 126))  # 14 columns of band 2, 6 of band 1; the halo reaches x 123
        self.assertEqual(dp.foot_of(L, pine, 4), 1)


def field_scene():
    """For kept_fields (r387): far land (band 2) over a near field (band 3) from row 60, SAM's mask of it ragged at its
    top: a 4 px notch and a 40 px bay left out of it, and a 20 px dip. After with_fields (moved), a later pass handed
    back a tuft inside the field and a strip along the dip's floor, its top edge there."""
    moved = np.full((H, W), 2)
    moved[60:] = 3
    mask = moved == 3
    notch = np.zeros((H, W), bool)
    notch[60:64, 30:34] = True
    bay = np.zeros((H, W), bool)
    bay[60:66, 150:190] = True
    moved[notch | bay] = 2
    mask &= ~(notch | bay)
    L = moved.copy()
    tuft = np.zeros((H, W), bool)
    tuft[80:104, 60:90] = True  # wider than the closing shuts
    strip = np.zeros((H, W), bool)
    strip[66:68, 100:120] = True  # the field's top row there: nothing of it above
    mask[60:66, 100:120] = False
    moved[60:66, 100:120] = 2
    L[60:66, 100:120] = 2
    L[tuft | strip] = 2
    return L, moved, [mask], notch, bay, tuft


class KeptFieldsTest(unittest.TestCase):
    def test_a_tuft_inside_goes_back_to_the_field_a_strip_on_its_top_edge_stays_back(self):
        L, moved, fields, *_, tuft = field_scene()
        out = dp.kept_fields(L, moved, fields)
        self.assertTrue((out[tuft] == 3).all())
        self.assertTrue((out[66:68, 106:114] == 2).all())  # the closing rounds the dip's corners, not its floor

    def test_a_notch_in_the_field_s_edge_is_closed_a_bay_is_not(self):
        L, moved, fields, notch, bay, *_ = field_scene()
        out = dp.kept_fields(L, moved, fields)
        self.assertTrue((out[61:64, 30:34] == 3).all())  # all but its mouth, the field's own top row
        self.assertTrue((L[notch] == 2).all())
        self.assertGreater((out[bay] == 2).mean(), 0.9)

    def test_a_field_seeds_its_light_not_a_track_on_it(self):
        img = np.full((H, W, 3), 140, np.uint8)
        img[:, :, 1] = 180
        field = np.zeros((H, W), bool)
        field[60:] = True
        img[90:94, :] = (60, 50, 40)  # a track across it
        img[:60] = (20, 40, 20)  # a dark forest above it
        seed = dp.field_colours(img, field)
        self.assertLess(np.abs(seed[91, 100] - (140, 180, 140)).max(), 30)
        self.assertLess(np.abs(seed[61, 100] - (140, 180, 140)).max(), 20)

    def test_a_moved_field_starts_its_own_repaint_in_the_layer_behind(self):
        img = np.zeros((H, W, 3), np.uint8)
        img[:60] = (20, 40, 20)  # a dark forest, band 2
        img[60:] = (140, 180, 140)  # the field, moved whole to band 3
        img[90:94] = (60, 50, 40)  # a track across it
        L = np.full((H, W), 2)
        L[60:] = 3
        cut = tempfile.mkdtemp(prefix="r387-")
        saved = dp.gen
        dp.gen = lambda argv, logfile: shutil.copy(argv[argv.index("--src") + 1], argv[argv.index("--out") + 1])
        try:
            a = types.SimpleNamespace(layers=4, grow=6, theme="green hills", forced={}, denoise=0.8, seed=1, ckpt="x",
                                      keep=False)
            dp.cut_layer(2, img, np.zeros((H, W)), L, [0.0, 0.3, 0.6, 0.9], L == 3, cut, a)
            src = np.asarray(Image.open(os.path.join(cut, "l2_src.png")).convert("RGB")).astype(int)
        finally:
            dp.gen = saved
            shutil.rmtree(cut)
        self.assertLess(np.abs(src[80, 100] - (140, 180, 140)).max(), 30)  # the field's light, not the forest's dark
        self.assertLess(np.abs(src[92, 100] - (140, 180, 140)).max(), 40)  # and no second track

    def test_the_repaint_draws_no_track(self):
        img = np.zeros((H, W, 3), np.uint8)
        img[:60] = (20, 40, 20)
        img[60:] = (140, 180, 140)
        L = np.full((H, W), 2)
        L[60:] = 3
        cut = tempfile.mkdtemp(prefix="r426-")
        asked = []
        saved = dp.gen

        def gen(argv, logfile):
            asked.append(argv[argv.index("--negative") + 1])
            shutil.copy(argv[argv.index("--src") + 1], argv[argv.index("--out") + 1])
        dp.gen = gen
        try:
            a = types.SimpleNamespace(layers=4, grow=6, theme="green hills", forced={}, denoise=0.8, seed=1, ckpt="x",
                                      keep=False)
            dp.cut_layer(2, img, np.zeros((H, W)), L, [0.0, 0.3, 0.6, 0.9], L == 3, cut, a)
        finally:
            dp.gen = saved
            shutil.rmtree(cut)
        self.assertTrue(asked)
        self.assertIn("dirt track", asked[0])  # r426: SD carried the layer's track on across a field's hole


if __name__ == "__main__":
    argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter).parse_known_args()
    unittest.main()
