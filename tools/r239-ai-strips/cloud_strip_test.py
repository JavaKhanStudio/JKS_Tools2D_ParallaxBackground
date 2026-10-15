"""ai_page.py --clouds' strip (r409): sil.py's clouds keyed by layer.py as ai_page.py keys them, every heap kept. No GPU.

    python3 tools/r239-ai-strips/cloud_strip_test.py

Apart from layer_test.py, whose mutants (tools/r358-glow, tools/r400-ai-rules) copy layer.py's files alone: this one
imports ai_page.py and sil.py. tools/r409-clouds/mutate.sh takes ai_page.cut_args' --keep-islands out and proves it fails.
"""
import os
import sys
import tempfile
import unittest

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ai_page
import layer
import sil

W, H = 256, 128

class CloudStripTest(unittest.TestCase):
    """r409: a clouds strip (ai_page.py --clouds), sil.py's heaps keyed as ai_page.py keys them: every heap kept."""

    def test_sil_s_clouds_loop_float_clear_of_the_top_and_layer_py_keeps_every_heap(self):
        with tempfile.TemporaryDirectory() as d:
            prefix = os.path.join(d, "clouds")
            saved = sys.argv
            sys.argv = ["sil.py", "clouds", prefix, "--w", str(4 * W), "--h", str(3 * H // 2), "--seed", "3",
                        "--texture", "1"]
            try:
                sil.main()
            finally:
                sys.argv = saved
            m = np.asarray(Image.open(prefix + "_mask.png")) > 127
            img = np.asarray(Image.open(prefix + "_init.png").convert("RGB")).astype(np.float32)
            argv = ["layer.py", prefix + "_init.png", prefix + "_cut.png", "--mask", prefix + "_mask.png", "--grow",
                    str(ai_page.CLOUD_GROW), "--kind", "clouds", *ai_page.cut_args("clouds")]
            sys.argv = argv
            try:
                a = layer.parse_args()
            finally:
                sys.argv = saved
            alpha, _ = layer.key(img, np.median(img[:8].reshape(-1, 3), axis=0), a)
        h = m.shape[0]
        self.assertFalse(m[:h // 10].any())  # art on the top row is refused; a heap's top is 0.1 h clear
        self.assertFalse(m[-h // 4:].any())
        heaps, sizes = layer.components(m)
        count = len(sizes)
        self.assertGreaterEqual(count, 3)
        for i in range(1, count + 1):
            heap = heaps == i
            self.assertGreater((alpha[heap] > 0.5).mean(), 0.75, f"heap {i} cut")  # cut: 0
        self.assertTrue(a.no_creatures)



if __name__ == "__main__":
    unittest.main()
