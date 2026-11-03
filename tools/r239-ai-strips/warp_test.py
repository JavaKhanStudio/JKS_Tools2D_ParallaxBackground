"""sil.py --warp (r496): the ruins' shape bent, its loop and its variants' ends kept, off by default. No GPU.

    python3 tools/r239-ai-strips/warp_test.py
"""
import os
import subprocess
import sys
import tempfile
import unittest

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import sil  # noqa: E402

SIZE = ["--w", "768", "--h", "288", "--quiet-join"]


def run_sil(d, name, kind, *args):
    out = os.path.join(d, name)
    subprocess.run([sys.executable, os.path.join(HERE, "sil.py"), kind, out, *SIZE, *args], check=True)
    return (np.asarray(Image.open(f"{out}_mask.png"), float) / 255,
            np.asarray(Image.open(f"{out}_init.png").convert("RGB"), float))


class Warp(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.d = self.tmp.name
        self.plain, self.plain_init = run_sil(self.d, "plain", "ruins", "--seed", "5")
        self.bent, self.bent_init = run_sil(self.d, "bent", "ruins", "--seed", "5", "--warp", "0.0243")

    def tearDown(self):
        self.tmp.cleanup()

    def test_off_by_default_and_at_zero(self):
        off, _ = run_sil(self.d, "off", "ruins", "--seed", "5", "--warp", "0")
        self.assertTrue((off == self.plain).all())
        self.assertGreater(np.abs(self.bent - self.plain).mean(), 0.005, "the warp moved nothing")

    def test_the_bend_runs_on_across_the_join(self):
        steps = np.abs(np.diff(self.bent, axis=1)).mean(axis=0)
        self.assertLess(np.abs(self.bent[:, 0] - self.bent[:, -1]).mean(), np.percentile(steps, 99.5),
                        "the mask steps at the loop's join")

    def test_a_tower_at_the_join_bends_across_it(self):
        mask = np.zeros((288, 768))
        mask[:, :3] = 1  # at column 0: only a field read modulo the width can bring it to the last columns
        self.assertGreater(sil.warp(mask, 0.0243, 5)[:, -3:].max(), 0.5)

    def test_the_init_is_the_mask_that_the_cut_reads(self):
        off = np.abs(self.bent_init - self.bent_init[0, 0]).sum(-1)
        painted = off > 0.5 * np.median(off[self.bent == 1])
        self.assertLess((painted ^ (self.bent > 0.5)).mean(), 0.002)

    def test_the_bend_never_reaches_the_top_or_opens_the_foot(self):
        self.assertEqual(self.bent[0].max(), 0)
        self.assertEqual(self.bent[-1].min(), 1)

    def test_a_variant_keeps_its_seeds_ends(self):
        v, _ = run_sil(self.d, "v", "ruins", "--seed", "9", "--edges-of", "5", "--warp", "0.0243")
        k, _ = run_sil(self.d, "k", "ruins", "--seed", "5", "--edges-of", "5", "--warp", "0.0243")
        ends = np.r_[0:48, 720:768]  # the kept px at this size's ends, clear of what the bend can bring in
        self.assertLess(np.abs(v[:, ends] - k[:, ends]).mean(), 0.002)

    def test_kinds_without_it_ignore_it(self):
        a, _ = run_sil(self.d, "a", "hills", "--seed", "5")
        b, _ = run_sil(self.d, "b", "hills", "--seed", "5", "--warp", "0.0243")
        self.assertTrue((a == b).all())


if __name__ == "__main__":
    unittest.main()
