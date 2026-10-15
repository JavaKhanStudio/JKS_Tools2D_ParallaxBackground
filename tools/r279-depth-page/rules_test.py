"""depth_page's rules (r378) on a painting made up here: no GPU, no files from a run.

    ~/ComfyUI/venv/bin/python tools/r279-depth-page/rules_test.py

A sky over a land, a crown (the hole) hanging into the sky, a twig's tip left beside it in the back layer, a hedge's
dark line in the land touching the hole, a broad dark mountain touching it too, a dark cloud against the crown, the sky's own blue in a narrow gap
between two white clouds under the crown, and a thin dark stroke the painting has far out in the sky.
"""
import argparse
import os
import sys
import unittest

import numpy as np

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


if __name__ == "__main__":
    argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter).parse_known_args()
    unittest.main()
