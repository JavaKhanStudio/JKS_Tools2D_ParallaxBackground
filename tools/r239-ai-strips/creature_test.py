"""creature.find (r438): a dune's slip face, the sand's own colour a little darker, is no creature; a bird on the
dune still is, dark or pale grey. No GPU, no strip on disk: sweep.sh holds the painted ones.

    python3 tools/r239-ai-strips/creature_test.py
"""
import os
import sys
import unittest

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import creature

SAND = np.array([236.0, 192.0, 127.0])  # r437's DreamShaperXL Turbo dunes


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


if __name__ == "__main__":
    unittest.main()
