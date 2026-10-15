"""layer.py's key on painted strips made up here (r358): no GPU, no files from a run.

    python3 tools/r239-ai-strips/layer_test.py

A strip is a sky that warms toward its foot, a dark mass over the bottom with towers, and what SD paints round it.
"""
import argparse
import os
import sys
import tempfile
import unittest

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import layer

W, H = 256, 128
TOP, FOOT, MASS = np.array([218, 227, 234]), np.array([179, 163, 149]), np.array([70, 55, 48])
SAND = np.array([243, 199, 136])


def sky():
    f = np.linspace(0, 1, H)[:, None, None]
    return np.broadcast_to(TOP + (FOOT - TOP) * f, (H, W, 3)).astype(np.float32).copy()


def mass():
    """The silhouette: a ground under row 96 and two towers."""
    m = np.zeros((H, W), bool)
    m[96:] = True
    m[40:, 60:80] = True
    m[50:, 170:190] = True
    return m


def cut(img, m, grow=16):
    """layer.key's alpha, as ai_page.py runs it on a painted strip (--mask, --grow)."""
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, "mask.png")
        Image.fromarray((m * 255).astype(np.uint8)).save(path)
        a = argparse.Namespace(near=18, far=0, mask=path, grow=grow, gap=0.6, keep_islands=False, edge=2, out="test")
        alpha, _ = layer.key(img, np.median(img[:8].reshape(-1, 3), axis=0), a)
    return alpha


def ring(m, inner, outer):
    """The pixels between inner and outer px outside m."""
    return layer.spread(m, outer) & ~layer.spread(m, inner)


class KeyTest(unittest.TestCase):
    def test_a_sky_that_warms_down_the_strip_keys_clear_round_the_shape(self):
        """r358: the sky read from the top rows only left the warm sky at the foot half opaque within --grow of the
        mask, a band round every building."""
        m = mass()
        img = sky()
        img[m] = MASS
        alpha = cut(img, m)
        band = ring(m, 3, 16)
        self.assertLess(float(alpha[band].max()), 0.05, "the sky round the shape keys clear")
        self.assertGreater(float(alpha[layer.spread(m, -8)].min()), 0.99, "the shape stays solid")

    def test_a_pale_haze_painted_round_the_shape_keys_clear(self):
        """SD paints a haze round the silhouette, paler than the shape and not the sky (R0 s05's 'shadows')."""
        m = mass()
        img = sky()
        haze = ring(m, 0, 10)
        img[haze] = img[haze] * 0.7 + MASS * 0.3
        img[m] = MASS
        alpha = cut(img, m)
        # 0.70 with the sky of the top rows only, 0.42 with the haze left as keyed (tools/r358-glow/mutate.sh)
        self.assertLess(float(alpha[ring(m, 3, 16)].max()), 0.1, "no half-opaque band past the edge")

    def test_a_thin_dark_frond_off_the_shape_stays(self):
        """A palm's frond: a dark line a px or two wide, past the mask, keyed solid along its middle (s06)."""
        m = mass()
        img = sky()
        img[m] = MASS
        frond = np.zeros((H, W), bool)
        frond[40:42, 80:100] = True  # off the left tower's side, within --grow
        img[frond] = MASS
        alpha = cut(img, m, grow=24)
        self.assertGreater(float(alpha[frond].min()), 0.9, "the frond is kept")

    def test_a_pale_foot_painted_past_the_mask_stays_solid(self):
        """r382: a dune's far sand, between two dunes, is paler than the mass, and SD paints it a few px past the mask.
        local_sky read that sand as sky, the pale foot keyed as sky against it, and ungap bit notches out of it."""
        m = np.zeros((H, W), bool)
        m[90:] = True
        img = sky()
        img[m] = SAND
        pale = np.zeros((H, W), bool)
        pale[70:104, 90:170] = True  # over the mask's top, and 20 px past it: well past --grow
        img[pale] = (TOP + 0.8 * (SAND - TOP)).round()  # dsxlt-beach l2's foot: 0.75..0.88 of the way to the sand
        alpha = cut(img, m, grow=8)
        self.assertGreater(float(alpha[layer.spread(m, -8)].min()), 0.95, "the pale foot stays solid")

    def test_a_crown_on_a_thin_trunk_with_a_pale_join_stays_whole(self):
        """r383: a palm's crown, in sil.py's mask but too thin to be solid, joins its trunk through a few pale px SD
        painted (the beach example's l3v1: grey 125 under a sky of 214). Its fronds run on past --grow, and local_sky
        read them as sky: the sky round the join came out grey 130, the join keyed clear, and the crown was cut as an
        island. r382's masslike keeps the fronds out of the sky."""
        m = np.zeros((H, W), bool)
        m[100:] = True
        m[44:, 126:130] = True  # the trunk, 4 px wide
        draw = Image.new("L", (W, H))
        for x, y in ((68, 60), (188, 60), (78, 30), (178, 30), (98, 22), (158, 22)):  # fronds out to 60 px each way
            ImageDraw.Draw(draw).line((128, 40, x, y), fill=255, width=3)
        fronds = np.asarray(draw) > 127
        m |= fronds & layer.spread(m, 16)  # sil.py's crown: the fronds near the trunk
        img = sky()
        img[m | fronds] = MASS
        join = np.zeros((H, W), bool)
        join[42:47, 125:131] = True
        img[join] = (sky()[join] + MASS) / 2  # the pale join
        alpha = cut(img, m, grow=24)
        crown = fronds & layer.spread(m, 20) & ~join
        self.assertGreater(float((alpha[crown] > 0.5).mean()), 0.95, "the crown is kept")
        self.assertGreater(float(alpha[join].min()), 0.5, "its join to the trunk too")

    def test_local_sky_is_the_clear_pixels_round_a_pixel(self):
        img = sky()
        clear = np.ones((H, W), bool)
        clear[:, 100:140] = False
        img[~clear] = MASS
        behind = layer.local_sky(img, TOP, clear, 24)
        self.assertLess(float(np.abs(behind[:, 120] - sky()[:, 120]).max()), 6, "the sky in the gap is its row's")


def forest():
    """(img, mask, misty, lit): two dark pines on a dark ground and, between them, a farther pine painted in mist inside
    the mask, from 0.5 of the way to the mass at its tip to 0.8 at its foot (dreamshaper_8's, r364); the right pine's
    left side lit, a px wide, as pale as the mist, against the sky (where it meets the mist it keys with it)."""
    rows, cols = np.mgrid[:H, :W]
    img = sky()
    pale = lambda t: sky() + t[..., None] * (MASS - sky())  # noqa: E731
    misty = (rows >= 44) & (rows < 96) & (np.abs(cols - 128) <= (rows - 44) * 0.45)
    t = 0.5 + 0.3 * (rows - 44) / 52
    img = np.where(misty[..., None], pale(t), img)
    dark = (rows >= 96) | (np.abs(cols - 90) <= (rows - 20) * 0.35) | (np.abs(cols - 170) <= (rows - 20) * 0.35)
    img[dark] = MASS
    lit = dark & ~layer.spread(dark, -1) & (cols < 170) & (cols > 140) & (rows > 30) & (rows < 80)
    img = np.where(lit[..., None], pale(np.full((H, W), 0.7)), img)
    return img.astype(np.float32), dark | misty, misty & ~dark, lit


def cut_ruled(img, m, kind="pines", ckpt="dreamshaper_8.safetensors", forced=None):
    """cut() with RULES weighed on kind and ckpt: (alpha, the records)."""
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, "mask.png")
        Image.fromarray((m * 255).astype(np.uint8)).save(path)
        a = argparse.Namespace(near=18, far=0, mask=path, grow=16, gap=0.6, keep_islands=False, edge=2, out="test",
                               kind=kind, ckpt=ckpt, forced=forced or {})
        records = []
        alpha, _ = layer.key(img, np.median(img[:8].reshape(-1, 3), axis=0), a, records)
    return alpha, records


class MistyPinesTest(unittest.TestCase):
    """r400: misty_pines, the first of layer.py's RULES: on dreamshaper_8's pines only."""

    def test_dreamshaper_8_s_misty_pine_keys_out_its_lit_edge_and_the_dark_pines_stay(self):
        img, m, misty, lit = forest()
        alpha, (rec,) = cut_ruled(img, m)
        self.assertTrue(rec["applied"], rec)
        self.assertTrue(all(rec["when"].values()))
        self.assertGreater(rec["moved"], 0)
        top = misty & (np.mgrid[:H, :W][0] < 80)  # t under 0.7: under half opaque
        self.assertLess(float(alpha[top].mean()), 0.5, "the misty pine keys out")
        self.assertGreater(float(alpha[lit].min()), 0.9, "the lit edge stays")
        self.assertGreater(float(alpha[layer.spread(m & ~misty, -8)].min()), 0.99, "the dark pines stay solid")

    def test_another_model_s_or_another_kind_s_pale_pine_stays(self):
        img, m, misty, _ = forest()
        for kind, ckpt, failed in (("pines", "openjourney-v4.ckpt", "painted by dreamshaper_8"),
                                   ("mountains", "dreamshaper_8.safetensors", "a pines layer")):
            alpha, (rec,) = cut_ruled(img, m, kind, ckpt)
            self.assertFalse(rec["applied"])
            self.assertEqual(list(rec["when"])[-1], failed)
            inside = misty & layer.spread(m, -8)
            self.assertGreater(float(alpha[inside].min()), 0.99, f"{kind} by {ckpt}: the pale pine stays")

    def test_forced_on_another_model_or_off_on_dreamshaper_8(self):
        img, m, misty, _ = forest()
        _, (rec,) = cut_ruled(img, m, ckpt="openjourney-v4.ckpt", forced={"misty_pines": True})
        self.assertTrue(rec["applied"])
        self.assertEqual(rec["forced"], "on")
        alpha, (rec,) = cut_ruled(img, m, forced={"misty_pines": False})
        self.assertFalse(rec["applied"])
        self.assertGreater(float(alpha[misty & layer.spread(m, -8)].min()), 0.99)

    def test_a_pines_strip_with_no_mist_is_left(self):
        img, m, misty, _ = forest()
        img[misty] = MASS
        _, (rec,) = cut_ruled(img, m)
        self.assertFalse(rec["applied"])
        self.assertFalse(rec["when"]["wide pale pines open to the sky"])


if __name__ == "__main__":
    unittest.main()
