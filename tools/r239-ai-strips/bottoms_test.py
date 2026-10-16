"""ai_page.Pipeline.bottoms (r446): a page laid out by it passes its own lint's (b), when the layer in front's fully
opaque band is a few rows deep and a notch's tip clears a few pixels just over it. No GPU.

    python3 tools/r239-ai-strips/bottoms_test.py

r2's beach-story (parallax-ai r4): the palms' band 6 rows of 576, their rows 6 and 7 clear 1 and 6 screen px; bottoms()
sank the dunes 0.8 of the band, and lint (b), reading 2 screen px each side of the edge, saw them. Take the lowering
loop out of bottoms() and the first test fails.
"""
import json
import os
import sys
import tempfile
import unittest

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, ".."))
import ai_page  # noqa: E402
import make_page  # noqa: E402
import parallax_lab as lab  # noqa: E402

W, H = 1536, 576


def strip(path, band, notch=()):
    """A strip opaque in its bottom `band` rows and its lower half, but for notch: (row up from the bottom, clear px)."""
    a = np.zeros((H, W), np.uint8)
    a[H // 2:] = 255
    for row, px in notch:
        a[H - 1 - row, :px] = 0
    a[H - band:] = 255
    rgba = np.dstack([np.full((H, W, 3), 90, np.uint8), a])
    Image.fromarray(rgba, "RGBA").save(path)
    return path


def lay_out(d, layers):
    """The page write_page writes from these strips (a list of paths per layer, back to front), and its lint."""
    pipe = ai_page.Pipeline.__new__(ai_page.Pipeline)
    pipe.ks = [None] * len(layers)
    bottoms = pipe.bottoms(layers)
    files = [(f"l{k}" if len(ps) == 1 else f"l{k}_{i}", p) for k, ps in enumerate(layers) for i, p in enumerate(ps)]
    make_page.pack("t", files, "Linear,Linear", out=d)
    page_layers = []
    for k, ps in enumerate(layers):
        dy = round(100 * bottoms[k] / lab.SCREEN_H, 2)
        if len(ps) == 1:
            page_layers.append(lab.layer(f"l{k}", speed=0.01 * 2 ** k, dy=dy))
        else:
            seq = lab.layer("", speed=0.01 * 2 ** k, dy=dy)
            seq.update(kind="SEQUENCE", name=f"l{k}", sequenceSeed=1, sequenceLength=2 * len(ps),
                       sequenceSegments=[{"regionName": f"l{k}_{i}", "regionPosition": 0, "weight": 1}
                                         for i in range(len(ps))])
            page_layers.append(seq)
    page = lab.page_of("t.atlas", page_layers, sky=("88aacc", "ccddee"), ground=("ccddee", "ccddee"), top_size=0.0,
                       bottom_size=1.0)
    with open(os.path.join(d, "t.jplax"), "w") as f:
        json.dump(page, f)
    return bottoms, [p for p in lab.lint(page, d) if p.startswith("(b)")]


class BottomsTest(unittest.TestCase):

    def test_a_thin_band_with_a_notch_just_over_it_hides_the_edge_behind(self):
        """r2 beach-story's palms: two segments, the thinner band 6 rows, rows 6 and 7 clear 1 and 7 px of 1536."""
        with tempfile.TemporaryDirectory() as d:
            back = [strip(os.path.join(d, f"l{k}.png"), 100) for k in range(2)]
            palms = [strip(os.path.join(d, "l2a.png"), 6, [(6, 1), (7, 7), (8, 27)]),
                     strip(os.path.join(d, "l2b.png"), 13, [(13, 10)])]
            bottoms, faults = lay_out(d, [[back[0]], [back[1]], palms])
            self.assertEqual([], faults)
            self.assertLess(bottoms[1], 0.8 * 6 / H * lab.SCREEN_W * H / W)

    def test_a_deep_band_keeps_its_fifth(self):
        """A band the lint's margin fits in: the edge stays 0.8 of it up, as before r446."""
        with tempfile.TemporaryDirectory() as d:
            ls = [[strip(os.path.join(d, f"l{k}.png"), 100, [(100, 40)])] for k in range(3)]
            bottoms, faults = lay_out(d, ls)
            self.assertEqual([], faults)
            band = 100 / H * lab.SCREEN_W * H / W
            self.assertAlmostEqual(bottoms[1], 0.8 * band)
            self.assertAlmostEqual(bottoms[0], 1.6 * band)


if __name__ == "__main__":
    unittest.main()
