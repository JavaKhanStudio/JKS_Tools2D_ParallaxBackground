"""loopcheck.py on clips made up here (r375): no GPU, no files from a run.

    python3 tools/r375-animated-layer/loopcheck_test.py

A clip is a band of waves over a still strip: rolled a whole period over the clip it loops, rolled 3/4 of one
it jumps at the wrap; a band that moves in its middle columns only keeps the strip's X seam.
"""
import os
import sys
import unittest

import numpy as np

sys.path.insert(0, os.path.dirname(__file__))
import loopcheck

W, H, N = 256, 96, 24


def clip(turns, band=(60, 90), x0=0, x1=W):
    x = np.arange(W)
    frames = np.zeros((N, H, W, 3), np.float32)
    frames[:] = 120
    for i in range(N):
        phase = 2 * np.pi * turns * i / N
        wave = 128 + 100 * np.sin(2 * np.pi * 4 * x / W + phase)
        still = 128 + 100 * np.sin(2 * np.pi * 4 * x / W)
        row = np.where((x >= x0) & (x < x1), wave, still)
        frames[i, band[0]:band[1]] = row[None, :, None]
    return frames


class LoopCheckTest(unittest.TestCase):
    def test_a_whole_period_loops(self):
        m = loopcheck.measure(clip(1.0))
        self.assertLess(m["time_seam"], 1.2)
        self.assertEqual(loopcheck.verdict(m), "PASS")

    def test_three_quarters_of_a_period_jumps(self):
        m = loopcheck.measure(clip(0.75))
        self.assertGreater(m["time_seam"], 2.0)
        self.assertIn("jumps at the wrap", loopcheck.verdict(m))

    def test_a_still_clip_fails(self):
        self.assertIn("barely moves", loopcheck.verdict(loopcheck.measure(clip(0.0))))

    def test_the_moving_box_is_the_band(self):
        m = loopcheck.measure(clip(1.0, x0=64, x1=192))
        self.assertEqual(m["moving_box"][1::2], [60, 90])
        self.assertLess(m["moving_share"], 0.2)


if __name__ == "__main__":
    unittest.main()
