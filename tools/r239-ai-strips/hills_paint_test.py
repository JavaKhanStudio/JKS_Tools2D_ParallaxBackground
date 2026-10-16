"""How ai_page.py paints each depth (r384): the hills of the default SD 1.5, with no --denoise, inpainted inside
sil.py's shape at 1.0 by dreamshaper_8Inpainting (HILLS_INPAINT, Simon's pick in hills-lab); every other depth, model
and --denoise img2img as before. That inpaint grows round trees on about a sixth of the seeds: canopy() refuses
flat hills, conifers and a lone tree, and paint_depths paints the next seed, then keeps the most wooded. No GPU:
ai_page.py --dry-run's commands, synthetic cuts, and paint_depths with its commands faked.

    python3 tools/r239-ai-strips/hills_paint_test.py
"""
import os
import shlex
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import ai_page  # noqa: E402


def commands(*args, theme="misty pine forest"):
    """Each strip's gen.py command (its words split), back to front, of a dry run with args."""
    with tempfile.TemporaryDirectory() as d:
        out = subprocess.run([sys.executable, os.path.join(HERE, "ai_page.py"), "--theme", theme,
                              "--layers", "4", *args, "--dry-run", d], capture_output=True, text=True, check=True)
    return [shlex.split(line.split(" > ")[0]) for line in out.stdout.splitlines() if "/gen.py " in line]


def strip(cmd):
    return os.path.basename(cmd[cmd.index("--out") + 1])


def mode(cmd):
    return cmd[2]


class HillsPaintTest(unittest.TestCase):

    def test_default_hills_are_inpainted_inside_the_shape_at_full_strength(self):
        cmds = {strip(c): c for c in commands()}
        hills = cmds["l2_ai.png"]  # 4 layers: mountains, mountains, hills, pines
        self.assertEqual(mode(hills), "inpaint")
        self.assertEqual(hills[hills.index("--ckpt") + 1], ai_page.HILLS_INPAINT["ckpt"])
        self.assertEqual(float(hills[hills.index("--denoise") + 1]), 1.0)
        self.assertEqual(hills.count("--denoise"), 1)
        self.assertNotIn("--init", hills)
        self.assertIn("--src", hills)
        self.assertTrue(hills[hills.index("--src") + 1].endswith("l2_init.png"))
        self.assertTrue(hills[hills.index("--mask") + 1].endswith("l2_mask.png"))
        self.assertIn("small round trees", hills[hills.index("--prompt") + 1])
        self.assertIn("conifers", hills[hills.index("--negative") + 1])
        for k in ("l0_ai.png", "l1_ai.png", "l3_ai.png"):
            self.assertEqual(mode(cmds[k]), "img2img", k)
            self.assertNotIn("--ckpt", cmds[k])

    def test_inpainted_hills_keep_the_pine_words_and_lose_the_other_objects(self):
        # r437 strikes a page's other objects from a layer's theme; HILLS_INPAINT's trees grew from "pine forest"
        # (r384: none in ten seeds without it), so its hills keep that, and still lose the ruins.
        cmds = {strip(c): c for c in commands("--kinds", "mountains,ruins,hills,pines",
                                              theme="misty pine forest, ruins at dawn")}
        hills = cmds["l2_ai.png"]
        self.assertEqual(mode(hills), "inpaint")
        prompt, neg = hills[hills.index("--prompt") + 1], hills[hills.index("--negative") + 1]
        self.assertIn("misty pine forest", prompt)
        self.assertNotIn("ruins", prompt)
        self.assertIn("ruins", neg)
        self.assertIn("conifers", neg)
        mountains = cmds["l0_ai.png"]
        self.assertNotIn("pine forest", mountains[mountains.index("--prompt") + 1])

    def test_another_model_or_a_denoise_keeps_img2img(self):
        for args in (["--denoise", "0.7"], ["--ckpt", "openjourney-v4.ckpt"], ["--style", "pixel"]):
            for c in commands(*args):
                if strip(c).startswith("l"):
                    self.assertNotEqual(mode(c), "inpaint", (args, strip(c)))


W, H = 1024, 384
X = np.arange(W)


def hills(top, texture=None):
    """A cut: opaque under the skyline top (one row per column), its colour a flat green plus texture (H x W)."""
    rgba = np.zeros((H, W, 4), np.uint8)
    lum = np.full((H, W), 70.0) + (0 if texture is None else texture)
    rgba[..., 0], rgba[..., 1], rgba[..., 2] = np.clip(lum * 0.8, 0, 255), np.clip(lum, 0, 255), np.clip(lum * 0.7, 0, 255)
    rgba[..., 3] = np.where(np.arange(H)[:, None] >= np.asarray(top)[None, :], 255, 0)
    return Image.fromarray(rgba)


ROLLING = (220 + 25 * np.sin(2 * np.pi * 3 * X / W) + 10 * np.sin(2 * np.pi * 7 * X / W)).astype(int)
LEAVES = np.random.default_rng(1).normal(0, 25, (H, W))  # every column busy, as a forest's crowns
CONIFERS = (200 + 4 * np.abs((X % 64) - 32)).astype(int)  # a triangle every 64 px
TREELINE = 150 + np.random.default_rng(2).integers(0, 40, W)  # the front's pines: roughness over ai_page.TREES
LONE = LEAVES * ((X > 400) & (X < 520))[None, :]  # one big tree's worth of texture


class CanopyTest(unittest.TestCase):

    def measure(self, img):
        with tempfile.TemporaryDirectory() as d:
            img.save(os.path.join(d, "cut.png"))
            return ai_page.canopy(os.path.join(d, "cut.png"))

    def test_a_forest_passes(self):
        spread, sharp = self.measure(hills(ROLLING, LEAVES))
        self.assertGreater(spread, ai_page.CANOPY_SPREAD)
        self.assertLess(sharp, ai_page.CANOPY_SHARP)

    def test_flat_hills_lack_spread(self):
        self.assertLess(self.measure(hills(ROLLING))[0], ai_page.CANOPY_SPREAD)

    def test_a_lone_tree_lacks_spread(self):
        self.assertLess(self.measure(hills(ROLLING, LONE))[0], ai_page.CANOPY_SPREAD)

    def test_conifers_are_too_sharp(self):
        self.assertGreater(self.measure(hills(CONIFERS, LEAVES))[1], ai_page.CANOPY_SHARP)


class GateTest(unittest.TestCase):
    """paint_depths over a 3-layer page (mountains, hills, pines) with sil.py, gen.py and layer.py faked: layer.py's
    stand-in writes the cut the table gives for the seed."""

    def run_page(self, cut_of, *args):
        with tempfile.TemporaryDirectory() as d:
            painted = []

            def fake_run(cmd, log, timeout=None):
                open(log, "w").close()
                if cmd[1].endswith("layer.py"):
                    prefix = cmd[3][:-len("_cut.png")]
                    with open(prefix + "_seed.txt") as f:
                        seed = int(f.read())
                    painted.append((os.path.basename(prefix), seed))
                    (cut_of(os.path.basename(prefix), seed)).save(cmd[3])
                return 0

            with mock.patch.object(sys, "argv", ["ai_page.py", d, "--theme", "forest", "--kinds",
                                                 "mountains,hills,pines", *args]), \
                    mock.patch.object(ai_page, "run", fake_run), mock.patch.object(ai_page, "log", lambda *_: None):
                p = ai_page.Pipeline(ai_page.parse_args())
                try:
                    _, seeds = p.paint_depths()
                except SystemExit as e:  # a page given up on is a failed case, not a crash
                    self.fail(e)
            return seeds, painted

    @staticmethod
    def pines(prefix, seed):
        return hills(TREELINE) if prefix == "l2" else hills(ROLLING)

    def test_flat_hills_are_painted_again_until_a_forest(self):
        forest = 7 * 1000 + 10 + 3  # the fourth try of layer 1
        seeds, painted = self.run_page(lambda prefix, seed: hills(ROLLING, LEAVES) if seed == forest
                                       else self.pines(prefix, seed), "--seed", "7")
        self.assertEqual(seeds[1], forest)
        self.assertEqual([s for p, s in painted if p == "l1"], [7010, 7011, 7012, 7013])

    def test_no_forest_keeps_the_most_wooded(self):
        def cut(prefix, seed):
            if prefix != "l1":
                return self.pines(prefix, seed)
            if seed == 7015:
                return hills(ROLLING, LEAVES * ((X > 200) & (X < 400))[None, :])
            return hills(CONIFERS, LEAVES) if seed == 7016 else hills(ROLLING)
        seeds, painted = self.run_page(cut, "--seed", "7")
        l1 = [s for p, s in painted if p == "l1"]
        self.assertEqual(l1, list(range(7010, 7010 + ai_page.HILLS_TRIES)) + [7015])
        self.assertEqual(seeds[1], 7015)

    def test_the_gate_leaves_other_models_alone(self):
        _, painted = self.run_page(self.pines, "--seed", "7", "--denoise", "0.65")
        self.assertEqual([s for p, s in painted if p == "l1"], [7010])


if __name__ == "__main__":
    unittest.main()
