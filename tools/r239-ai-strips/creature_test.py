"""creature.find (r438): a dune's slip face, the sand's own colour a little darker, is no creature; a bird on the
dune still is, dark or pale grey. r500: the rim of a painted edge, a mast off the strip's foot, a slit of sky and a
tower's top neither; a bird or a figure among those still is. No GPU, no strip on disk: sweep.sh holds the painted ones.

    python3 tools/r239-ai-strips/creature_test.py
"""
import io
import os
import sys
import tempfile
import unittest
from unittest import mock

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import creature
import layer

SAND = np.array([236.0, 192.0, 127.0])  # r437's DreamShaperXL Turbo dunes
SKY, ORANGE, RUBBLE = np.array([205.0, 224.0, 218.0]), np.array([170.0, 105.0, 65.0]), np.array([40.0, 25.0, 15.0])


def dune():
    """(rgb, alpha) 256 x 512: sky over a sand mass from y=64, lit from the left, and a slip face 0.8 as bright as the
    sand under its crest: a crescent 10 px thick, soft on its inner side (11024's at x=994 y=368, 162 px there)."""
    h, w = 256, 512
    y, x = np.mgrid[:h, :w].astype(np.float64)
    rgb = np.broadcast_to(SAND * (1 - 0.1 * y / h)[..., None], (h, w, 3)).copy()
    d = np.hypot(x - 256, (y - 100) * 1.6)
    face = np.clip((d - 14) / 3, 0, 1) * np.clip((24 - d) / 1.5, 0, 1) * (x > 240)
    rgb *= (1 - 0.2 * face)[..., None]
    alpha = (y >= 64).astype(np.float64)
    return rgb, alpha


def ruins():
    """(rgb, alpha) 256 x 512, as DreamShaperXL Turbo paints r496's: a pale sky, an orange wall the mask holds opaque
    from y=32, and on it a slab of dark rubble at y 230-250 with a block 25 x 19 rising from it at x=220."""
    h, w = 256, 512
    rgb = np.broadcast_to(ORANGE, (h, w, 3)).copy()
    rgb[:32] = SKY
    rgb[230:250] = RUBBLE
    rgb[211:230, 220:245] = RUBBLE
    alpha = np.ones((h, w))
    alpha[:32] = 0
    return rgb, alpha


def necked(head):
    """(rgb, alpha): a mass from y=400 with a head head px wide on a 3 px neck standing on it, sky round it."""
    h, w = 576, 768
    y, x = np.mgrid[:h, :w]
    m = y >= 400
    m |= (abs(x - 384) <= 1) & (y >= 400 - head // 2)
    m |= np.hypot(x - 384, y - (400 - head)) <= head / 2
    rgb = np.where(m[..., None], RUBBLE, SKY)
    return rgb, m.astype(np.float64)


def figure(rgb, colour):
    """A figure 17 px wide and 56 tall, no neck, its legs on ruins()'s slab at x=120."""
    y, x = np.mgrid[:rgb.shape[0], :rgb.shape[1]]
    rgb[(np.hypot(x - 120, y - 180) <= 6) | ((abs(x - 120) <= 8) & (186 <= y) & (y < 216))] = colour
    rgb[216:230, 112:118] = colour
    rgb[216:230, 122:128] = colour


def bird(rgb, cx, cy, colour):
    """A bird 30 x 12 px, plant.py's shape cut to its body and wings, in colour."""
    y, x = np.mgrid[:rgb.shape[0], :rgb.shape[1]]
    wing = (abs(x - cx) <= 15) & (abs(y - cy + abs(x - cx) / 4) <= 2)
    body = np.hypot(x - cx, (y - cy) * 1.5) <= 5
    rgb[wing | body] = colour


class CreatureTest(unittest.TestCase):
    def assert_found(self, found, x, y):
        self.assertEqual(len(found), 1, found)
        self.assertLessEqual(abs(found[0][0] - x) + abs(found[0][1] - y), 4, found)

    def test_the_slip_face_of_a_dune_is_no_creature(self):
        rgb, alpha = dune()
        self.assertEqual(creature.find(rgb, alpha), [])

    def test_without_the_shading_rule_the_slip_face_is_one(self):
        rgb, alpha = dune()
        self.assertNotEqual(creature.find(rgb, alpha, shade=1, hue=0), [], "the dune no longer shows the fault")

    def test_a_dark_bird_on_the_dune_is_found(self):
        rgb, alpha = dune()
        bird(rgb, 120, 180, SAND / 3)
        self.assert_found(creature.find(rgb, alpha), 120, 180)

    def test_a_pale_grey_gull_as_bright_as_the_sand_is_found(self):
        rgb, alpha = dune()
        bird(rgb, 400, 200, np.array([200.0, 200.0, 200.0]))
        self.assert_found(creature.find(rgb, alpha), 400, 200)

    def test_the_rim_of_rubble_against_a_painted_wall_is_no_creature(self):
        rgb, alpha = ruins()
        self.assertEqual(creature.find(rgb, alpha), [])

    def test_without_the_runs_on_rule_the_rim_is_one(self):
        rgb, alpha = ruins()
        with mock.patch.object(creature, "runs_on", return_value=False):
            self.assertNotEqual(creature.find(rgb, alpha), [], "the ruins no longer show the fault")

    def test_a_mast_off_the_strip_foot_is_no_creature(self):
        rgb, alpha = ruins()
        rgb[32:] = ORANGE
        rgb[220:, 100:106] = RUBBLE
        self.assertEqual(creature.find(rgb, alpha), [])

    def test_a_mast_inside_the_mass_still_is_one(self):
        rgb, alpha = ruins()
        rgb[32:] = ORANGE
        rgb[180:216, 100:106] = RUBBLE
        self.assert_found(creature.find(rgb, alpha), 102, 197)

    def test_a_slit_of_sky_through_a_tower_is_no_creature(self):
        rgb, alpha = ruins()
        rgb[32:] = RUBBLE
        rgb[90:126, 300:306] = SKY * 0.9
        self.assertEqual(creature.find(rgb, alpha), [])

    def test_a_wide_pale_bird_in_a_tower_is_found(self):
        rgb, alpha = ruins()
        rgb[32:] = RUBBLE
        bird(rgb, 300, 110, SKY * 0.9)
        self.assert_found(creature.find(rgb, alpha), 300, 110)

    def test_a_bird_on_the_wall_is_found(self):
        rgb, alpha = ruins()
        bird(rgb, 120, 100, RUBBLE)
        self.assert_found(creature.find(rgb, alpha), 120, 100)

    def test_a_figure_in_another_colour_on_the_rubble_is_found(self):
        """Its feet on the rubble are no way on: nothing round it is its colour."""
        rgb, alpha = ruins()
        figure(rgb, np.array([140.0, 40.0, 40.0]))
        self.assertTrue(any(abs(f[0] - 120) < 12 for f in creature.find(rgb, alpha)), creature.find(rgb, alpha))

    def test_a_neckless_figure_in_the_rubbles_colour_is_missed(self):
        """The rule's price: to find it is a block on rubble. Should this start passing, the rim's test will not."""
        rgb, alpha = ruins()
        figure(rgb, RUBBLE)
        self.assertFalse(any(abs(f[0] - 120) < 12 for f in creature.find(rgb, alpha)))

    def test_a_tall_streak_nearer_the_tower_than_the_sky_is_found(self):
        """On the sky-land line but not at its sky end: not sky through the mass."""
        rgb, alpha = ruins()
        rgb[32:] = RUBBLE
        rgb[90:126, 300:306] = SKY + 0.6 * (RUBBLE - SKY)
        self.assert_found(creature.find(rgb, alpha), 302, 107)

    def test_a_dark_dog_on_thin_legs_on_the_rubble_is_found(self):
        """Its colour runs on down its legs into the slab, but nearly all of the ring round it is the wall."""
        rgb, alpha = ruins()
        rgb[190:202, 110:132] = RUBBLE
        rgb[202:230, 112:114] = RUBBLE
        rgb[202:230, 128:130] = RUBBLE
        self.assertTrue(any(abs(f[0] - 120) < 12 for f in creature.find(rgb, alpha)), creature.find(rgb, alpha))

    def test_a_creature_as_wide_as_the_box_is_found(self):
        """It stands out only round its rim: its own core must not count as running on."""
        rgb, alpha = ruins()
        rgb[32:] = ORANGE
        rgb[150:180, 220:245] = RUBBLE
        self.assert_found(creature.find(rgb, alpha), 232, 164)

    def test_a_head_as_wide_as_a_tower_is_no_figure(self):
        rgb, alpha = necked(90)
        self.assertEqual(creature.on_neck(rgb, alpha, SKY, 12), [])

    def test_a_giants_head_is_a_figure(self):
        rgb, alpha = necked(30)
        self.assertEqual(len(creature.on_neck(rgb, alpha, SKY, 12)), 1)


class CutAlpha(unittest.TestCase):
    """r501: a cut's PNG answers as the alpha layer.py's gate saw, at the one line creature.py reads it."""

    def test_every_key_stays_on_its_side_of_a_half_through_the_png(self):
        keyed = np.concatenate([np.linspace(0, 1, 2001), [0.5, 0.499, 0.4999, 0.5001, 127.5 / 255, 127 / 255]])
        keyed = keyed.reshape(1, -1).astype(np.float32)
        with tempfile.TemporaryDirectory() as d:
            layer.unmix(np.zeros(keyed.shape + (3,), np.float32), np.zeros(3, np.float32), keyed).save(d + "/cut.png")
            stored = np.asarray(Image.open(d + "/cut.png").getchannel("A"))
        read = layer.read_alpha(stored)
        self.assertEqual(((read >= 0.5) != (keyed >= 0.5)).sum(), 0)
        self.assertEqual(read[keyed == 0].max(), 0)
        self.assertEqual(read[keyed == 1].min(), 1)

    def test_a_bird_in_a_far_range_is_found_through_the_command_line(self):
        rgb, alpha = dune()
        rgb[150:156, 300:314] = 20  # a far range stores 0.5: read as 0.498 it is no mass, and the bird nobody's
        alpha = alpha * 0.5
        self.assertTrue(creature.find(rgb, alpha, 12, 750, r=6), "what the gate sees in a 512 px strip")
        with tempfile.TemporaryDirectory() as d:
            layer.unmix(rgb.astype(np.float32), SKY.astype(np.float32), alpha.astype(np.float32)).save(d + "/cut.png")
            Image.fromarray(rgb.astype(np.uint8)).save(d + "/ai.png")
            with mock.patch.object(sys, "argv", ["creature.py", d + "/cut.png", "--src", d + "/ai.png"]), \
                    mock.patch.object(sys, "stderr", io.StringIO()), self.assertRaises(SystemExit) as out:
                creature.main()
        self.assertEqual(out.exception.code, 4)


if __name__ == "__main__":
    unittest.main()
