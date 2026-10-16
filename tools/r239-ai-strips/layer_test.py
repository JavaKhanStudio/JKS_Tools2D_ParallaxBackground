"""layer.py's key on painted strips made up here (r358): no GPU, no files from a run.

    python3 tools/r239-ai-strips/layer_test.py

A strip is a sky that warms toward its foot, a dark mass over the bottom with towers, and what SD paints round it.
"""
import argparse
import os
import subprocess
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

    def test_a_pale_far_range_round_the_shape_is_half_clear_whole(self):
        """r401: SD paints a pale far range between two near slopes, inside sil.py's mask and past it (R1 oj4-postapo
        l0). local_sky read its tops past --grow as sky, ungap and unglow keyed it in blobs, half solid, half sky.
        Simon's answer: half clear, one even alpha. A mesa standing in it stays, joined to the mass through it (ds8-postapo l0's was cut as an island)."""
        rows, cols = np.mgrid[:H, :W]
        near = (rows >= 100) | (rows - 30 >= np.abs(cols - 50) * 1.2) | (rows - 30 >= np.abs(cols - 210) * 1.2)
        rng = (rows - 46 >= np.abs(cols - 130) * 0.6) & ~near  # its peak 54 px over the mask: well past --grow
        mesa = (rows >= 60) & (rows < 88) & (np.abs(cols - 130) <= 9)  # standing in the range, off the ground
        m = near | (rng & (rows >= 80)) | mesa  # sil.py's shape takes the range's foot, and the mesa
        img = sky()
        img[rng] = TOP + 0.5 * (MASS - TOP)
        img[near | mesa] = MASS
        alpha = cut(img, m, grow=8)
        body = layer.spread(rng & ~mesa, -3)
        self.assertLess(float(alpha[body].std()), 0.05, "one even alpha over the range")
        self.assertLess(abs(float(alpha[body].mean()) - 0.5), 0.1, "half clear")
        self.assertLess(float(alpha[ring(rng | m, 3, 16) & (rows < 100)].max()), 0.1, "no glow in the sky beside it")
        self.assertGreater(float(alpha[layer.spread(mesa, -8)].min()), 0.99, "the mesa stays")
        self.assertGreater(float(alpha[layer.spread(near, -8)].min()), 0.99, "the near slopes stay solid")

    def test_a_sky_warming_down_to_a_low_ground_keys_clear(self):
        """r401: a sky warming half way to the mass's colour at its foot is as far from the top rows' sky as a pale far
        range, and as thick: it has no outline on the sky above it, and is no range."""
        m = np.zeros((H, W), bool)
        m[112:] = True
        f = np.linspace(0, 1, H)[:, None, None] ** 2
        img = np.broadcast_to(TOP + 0.5 * (MASS - TOP) * f, (H, W, 3)).astype(np.float32).copy()
        img[m] = MASS
        alpha = cut(img, m, grow=24)
        self.assertLess(float(alpha[ring(m, 3, 24)].max()), 0.1, "the warm sky keys clear")

    def test_a_pale_mass_inside_the_mask_has_no_line_along_its_core(self):
        """r454: a pale mountain (R6c zit-forest l0's, 0.65 of the way to the mass) painted a few px past the mask
        keys 0.7; unglow made it solid more than 2 px from the mask's 8 px core, and left it 0.7 within them: a line
        along the mask inside the peak, 20 px from its outline."""
        rows = np.mgrid[:H, :W][0]
        m = rows >= 52  # its 8 px core from row 60
        pale = (rows >= 40) & (rows < 80)  # its outline 12 px past the mask's
        img = sky()
        img[pale] = TOP + 0.65 * (MASS - TOP)
        img[rows >= 80] = MASS
        alpha = cut(img, m)
        self.assertGreater(float(alpha[(rows >= 42) & (rows < 80)].min()), 0.95, "the pale mass is solid to its core")
        self.assertLess(float(alpha[rows < 38].max()), 0.05, "the sky over it clear")

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


def cut_ruled(img, m, kind="pines", ckpt="dreamshaper_8.safetensors", forced=None, rule="misty_pines", grow=16):
    """cut() with RULES weighed on kind and ckpt: (alpha, rule's record)."""
    with tempfile.TemporaryDirectory() as d:
        path = os.path.join(d, "mask.png")
        Image.fromarray((m * 255).astype(np.uint8)).save(path)
        a = argparse.Namespace(near=18, far=0, mask=path, grow=grow, gap=0.6, keep_islands=False, edge=2, out="test",
                               kind=kind, ckpt=ckpt, forced=forced or {})
        records = []
        alpha, _ = layer.key(img, np.median(img[:8].reshape(-1, 3), axis=0), a, records)
    (rec,) = [r for r in records if r["rule"] == rule]
    return alpha, rec


class MistyPinesTest(unittest.TestCase):
    """r400: misty_pines, the first of layer.py's RULES: on dreamshaper_8's pines only."""

    def test_dreamshaper_8_s_misty_pine_keys_out_its_lit_edge_and_the_dark_pines_stay(self):
        img, m, misty, lit = forest()
        alpha, rec = cut_ruled(img, m)
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
            alpha, rec = cut_ruled(img, m, kind, ckpt)
            self.assertFalse(rec["applied"])
            self.assertEqual(list(rec["when"])[-1], failed)
            inside = misty & layer.spread(m, -8)
            self.assertGreater(float(alpha[inside].min()), 0.99, f"{kind} by {ckpt}: the pale pine stays")

    def test_forced_on_another_model_or_off_on_dreamshaper_8(self):
        img, m, misty, _ = forest()
        _, rec = cut_ruled(img, m, ckpt="openjourney-v4.ckpt", forced={"misty_pines": True})
        self.assertTrue(rec["applied"])
        self.assertEqual(rec["forced"], "on")
        alpha, rec = cut_ruled(img, m, forced={"misty_pines": False})
        self.assertFalse(rec["applied"])
        self.assertGreater(float(alpha[misty & layer.spread(m, -8)].min()), 0.99)

    def test_a_pines_strip_with_no_mist_is_left(self):
        img, m, misty, _ = forest()
        img[misty] = MASS
        _, rec = cut_ruled(img, m)
        self.assertFalse(rec["applied"])
        self.assertFalse(rec["when"]["wide pale pines open to the sky"])

    def test_the_mist_s_half_clear_top_goes_with_it(self):
        """r476: forest-inpaint's l3 (r471, --paint inpaint, seed 7), the 64 columns each side of its loop: the key
        left the mist's soft top in the V between two pines half clear, rows 331-332, and the rule, taking it for sky,
        cleared the mist under it alone: a pale line two rows thick across the gap, alpha 34-123 over clear rows."""
        # beside tools/, or under the current directory's when a mutant runs a copy of this file (r400's mutate.sh)
        here = next(d for d in (os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "tools")
                    if os.path.isdir(os.path.join(d, "r476-misty-rim")))
        img = np.asarray(Image.open(os.path.join(here, "r476-misty-rim/join-ai.png")).convert("RGB")).astype(np.float32)
        m = np.asarray(Image.open(os.path.join(here, "r476-misty-rim/join-mask.png")).convert("L")) > 127
        alpha, rec = cut_ruled(img, m, grow=40)
        self.assertTrue(rec["applied"], rec)
        gap = alpha[329:335, 54:73]
        self.assertLess(float(gap.max()), 0.15, f"the gap's rows 329-334 are clear:\n{np.round(gap * 255).astype(int)}")
        self.assertGreater(float(alpha[370:].min()), 0.99, "the pines' foot stays solid")


def valley():
    """(img, m, hill): a mask with a deep valley, and a pale hill Z-Image painted in it, its top 24 px over the
    mask's floor (R6c zit-forest l0), t 0.65 of the way to the mass: a crisp outline on the sky."""
    rows, cols = np.mgrid[:H, :W]
    m = (rows >= 100) | (rows - 30 >= np.abs(cols - 40) * 0.9) | (rows - 30 >= np.abs(cols - 216) * 0.9)
    hill = (rows >= 76) & ~m & (np.abs(cols - 128) < 60)
    img = sky()
    img[hill] = TOP + 0.65 * (MASS - TOP)
    img[m] = MASS
    return img.astype(np.float32), m, hill


class WideMassTest(unittest.TestCase):
    """r454: wide_mass, Z-Image's mass painted past --grow."""

    def test_z_image_s_hill_past_grow_is_kept_whole(self):
        img, m, hill = valley()
        alpha, rec = cut_ruled(img, m, "mountains", "z-image-turbo-Q5_K_M.gguf", rule="wide_mass", grow=8)
        self.assertTrue(rec["applied"], rec)
        self.assertGreater(rec["moved"], 0)
        self.assertGreater(float(alpha[layer.spread(hill, -2)].min()), 0.95, "the hill is kept, no floor at --grow")
        rows = np.mgrid[:H, :W][0]
        self.assertLess(float(alpha[(rows < 72) & ~layer.spread(m, 2)].max()), 0.05, "the sky over it clear")

    def test_another_model_s_hill_is_cut_at_grow(self):
        img, m, hill = valley()
        alpha, rec = cut_ruled(img, m, "mountains", "DreamShaperXL_Turbo_v2_1.safetensors", rule="wide_mass", grow=8)
        self.assertFalse(rec["applied"])
        self.assertEqual(list(rec["when"]), ["painted by Z-Image"])
        self.assertLess(float(alpha[hill & ~layer.spread(m, 8)].max()), 0.05)

    def test_a_z_image_strip_in_its_mask_is_left(self):
        img, m, hill = valley()
        img[hill] = sky()[hill]
        _, rec = cut_ruled(img, m, "mountains", "z-image-turbo-Q5_K_M.gguf", rule="wide_mass", grow=8)
        self.assertFalse(rec["applied"])
        self.assertFalse(rec["when"]["masslike px joined to the mask past --grow"])


def variant_pair():
    """A front strip and its variant, as gen.py variants paints them: the same pixels on their outer 32 columns, a
    ledge across the loop there, its top soft; in the front's middle a mass smaller than the ledge, in the variant's a
    tower larger and darker (the key's ramp ends at the strip's median distance from the sky: another soft top)."""
    ledge = np.zeros((H, W), bool)
    ledge[70:80, :32] = ledge[70:80, -32:] = True
    small, tower = np.zeros((H, W), bool), np.zeros((H, W), bool)
    small[100:110, 120:140] = True
    tower[40:, 100:160] = True
    pair = []
    soft = np.roll(ledge, -2, 0) & ~ledge
    for m, colour in ((ledge | small, MASS), (ledge | tower, MASS // 4)):
        img = sky()
        img[m] = MASS
        img[m & ~ledge] = colour
        img[soft] = (img[soft] + MASS) / 2
        pair.append((img, m))
    return pair


def layer_py(d, name, img, m, *extra):
    """layer.py's main on img and its mask m, as ai_page.py cuts a strip: the RGBA it wrote."""
    src, mask, out = (os.path.join(d, f"{name}{x}") for x in ("_ai.png", "_mask.png", "_cut.png"))
    Image.fromarray(img.round().astype(np.uint8)).save(src)
    Image.fromarray((m * 255).astype(np.uint8)).save(mask)
    subprocess.run([sys.executable, layer.__file__, src, out, "--mask", mask, "--grow", "16", "--no-creatures",
                    *extra], check=True, capture_output=True)
    return out, np.asarray(Image.open(out)).astype(int)


class EdgesOfTest(unittest.TestCase):
    """r474: a front variant's cut ends on its front's cut, or the SEQUENCE steps at every join (lint (e))."""

    def test_a_variant_ends_on_its_front_s_cut(self):
        (front, fm), (variant, vm) = variant_pair()
        with tempfile.TemporaryDirectory() as d:
            path, f = layer_py(d, "l3", front, fm)
            _, plain = layer_py(d, "l3v1", variant, vm)
            _, v = layer_py(d, "l3v1", variant, vm, "--edges-of", path)
        keep = layer.EDGE_KEEP
        self.assertGreater(int(np.abs(plain[:, :keep, 3] - f[:, :keep, 3]).max()), 200,
                           "the whole-strip key cuts the variant's ledge as an island: no fixture otherwise")
        np.testing.assert_array_equal(v[:, :keep], f[:, :keep], "starts on the front's cut")
        np.testing.assert_array_equal(v[:, -keep:], f[:, -keep:], "ends on it")
        self.assertGreater(int(v[72:78, np.r_[:32, W - 32:W], 3].min()), 200,
                           "the ledge the front keeps is kept whole, not a stub faded over the ramp")
        np.testing.assert_array_equal(v[:, 40:-40], plain[:, 40:-40], "its middle is its own cut")

    def test_the_lay_back_weighs_a_clear_pixel_s_colour_nothing(self):
        front = np.zeros((4, 64, 4), np.uint8)
        front[...] = (200, 40, 30, 255)
        out = np.zeros((4, 64, 4), np.uint8)
        out[...] = (0, 255, 0, 0)
        got = np.asarray(layer.edges_of(Image.fromarray(out, "RGBA"), Image.fromarray(front, "RGBA"))).astype(int)
        np.testing.assert_array_equal(got[:, :layer.EDGE_KEEP], front[:, :layer.EDGE_KEEP])
        ramp = got[0, layer.EDGE_KEEP:layer.EDGE_KEEP + layer.EDGE_RAMP]
        self.assertTrue((np.diff(ramp[:, 3]) < 0).all(), "it fades out over the ramp")
        self.assertLess(int(np.abs(ramp[:, :3] - front[0, 0, :3]).max()), 2, "in the front's colour")
        self.assertEqual(int(got[0, 32, 3]), 0, "the middle its own")


if __name__ == "__main__":
    unittest.main()
