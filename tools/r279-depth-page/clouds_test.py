"""depth_page's clouds on a layer of their own (r409), on a painting made up here: no GPU, no files from a run.

    ~/ComfyUI/venv/bin/python tools/r279-depth-page/clouds_test.py

A sky at the sky's depth over a far range (band 0 both) and a land (band 1); a crown (band 2) with sky between its
leaves that depth's halo judged nearer than the sky; a white cloud with a soft rim whose foot the range's peak hides; a pale far
ridge in the sky, darker than it; a bird, a dark speck in the sky, and a twig's end in an opening of the crown; a trunk hiding the peak's right flank, the sky beside it
in depth's halo; a mist in the range that depth judged as far as the sky. SAM's masks: the cloud, the sky round it, the ridge.
tools/r409-clouds/mutate.sh takes each fix out and proves a case here fails.
"""
import argparse
import os
import shutil
import sys
import tempfile
import types
import unittest

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import depth_page as dp

W, H = 256, 128
SKY, CLOUD, RANGE, RIDGE = (150, 180, 220), (240, 240, 245), (60, 80, 110), (120, 140, 170)


def scene():
    img = np.zeros((H, W, 3), np.uint8)
    img[:] = SKY
    dn = np.zeros((H, W))
    L = np.zeros((H, W), int)
    rows, cols = np.mgrid[:H, :W]
    peak = 30 + np.abs(cols - 170) * 0.6  # the range's top: row 30 at x 170, falling to row 90 either side
    rng = rows >= np.minimum(peak, 90)
    land = rows >= 100
    cloud = (((rows - 30) / 12.0) ** 2 + ((cols - 170) / 28.0) ** 2 <= 1) & ~rng  # its foot behind the peak
    ridge = (rows >= 64) & (rows < 72) & (cols < 60) & ~rng
    crown = (rows >= 4) & (rows < 44) & (cols >= 20) & (cols < 80)
    gap = (rows >= 20) & (rows < 26) & (cols >= 46) & (cols < 52)  # sky between the leaves
    bird = (rows >= 12) & (rows < 15) & (cols >= 120) & (cols < 123)
    opening = (rows >= 12) & (rows < 30) & (cols >= 25) & (cols < 42)  # the sky through the crown
    bird |= (rows >= 20) & (rows < 23) & (cols >= 32) & (cols < 35)  # a twig's end in it, 8 px from the leaves
    trunk = (rows >= 40) & (cols >= 196) & (cols < 214)  # hides the peak's right flank
    halo = (rows >= 44) & (rows < 58) & (cols >= 214) & (cols < 220) & ~rng  # sky beside the trunk, judged nearer
    pale = (rows >= 94) & (rows < 100) & (cols >= 60) & (cols < 160)  # mist on the land, at the sky's depth
    img[rng], dn[rng] = RANGE, 0.1
    img[cloud] = CLOUD
    rim = dp.grow(cloud, 3) & ~cloud & ~rng  # its soft edge, half way to the sky, out of SAM's outline
    img[rim] = (np.array(CLOUD) + SKY) // 2
    img[ridge] = RIDGE
    img[bird] = (40, 40, 40)
    img[pale], dn[pale] = (190, 205, 225), 0.0
    img[land], dn[land], L[land] = (90, 140, 60), 0.4, 1
    img[trunk], dn[trunk], L[trunk] = (70, 50, 40), 0.8, 2
    leaves = crown & ~gap & ~opening
    img[leaves], dn[leaves], L[leaves] = (40, 90, 40), 0.8, 2
    img[gap], dn[gap] = (95, 135, 130), 0.05  # tinted by the leaves round it (r361), in depth's halo
    dn[halo] = 0.05
    sky_mask = (L == 0) & ~rng & ~cloud & ~ridge
    return {"img": img, "dn": dn, "L": L, "rng": rng, "cloud": cloud, "ridge": ridge, "gap": gap, "bird": bird,
            "crown": crown, "trunk": trunk, "halo": halo, "pale": pale, "peak": peak, "masks": [cloud, sky_mask, ridge]}


def args(**kw):
    a = types.SimpleNamespace(layers=3, grow=6, theme="green valley, pine forest", forced={}, denoise=0.8, seed=1,
                              ckpt="x", keep=False)
    a.__dict__.update(kw)
    return a


class CloudsTest(unittest.TestCase):
    def setUp(self):
        self.s = scene()
        self.cut = tempfile.mkdtemp(prefix="r409-")

    def tearDown(self):
        shutil.rmtree(self.cut)

    def split(self):
        s = self.s
        clouds = dp.clouds_of(s["img"], s["dn"], s["L"], s["masks"])
        S = dp.sky_apart(s["L"], s["dn"], clouds, W * H // 150, s["img"])
        return clouds & (S < 0), S

    def test_the_cloud_is_found_the_sky_s_own_mask_and_a_darker_far_ridge_are_not(self):
        clouds, _ = self.split()
        self.assertTrue((clouds == self.s["cloud"]).all())
        self.assertFalse((clouds & self.s["ridge"]).any())

    def test_the_sky_comes_apart_from_the_range_the_sky_between_the_leaves_with_it(self):
        _, S = self.split()
        s = self.s
        self.assertTrue((S[s["rng"] & (s["L"] == 0)] == 0).all())
        self.assertTrue((S[s["cloud"]] == -1).all())
        self.assertTrue((S[s["gap"]] == -1).all())  # left in the range, a speck floating in the sky at its speed
        self.assertTrue((S[s["L"] > 0] == s["L"][s["L"] > 0]).all())
        self.assertTrue((S[s["pale"]] == 0).all())  # cut out of the range, a blue hole in the hills (river)
        self.assertTrue((S[s["halo"]] == -1).all())  # in the range, a pale chunk on the peak's flank (valley)

    def test_the_sky_layer_has_no_cloud_and_no_bird_and_keeps_the_ridge(self):
        clouds, S = self.split()
        layer, entry = dp.sky_layer(self.s["img"], self.s["dn"], S, clouds, self.cut, args())
        sky = np.asarray(Image.open(layer["path"]).convert("RGB")).astype(int)
        self.assertEqual(sky.shape, (H, W, 3))
        self.assertLess(np.abs(sky[self.s["cloud"]] - SKY).max(), 12)
        self.assertLess(np.abs(sky[self.s["bird"]] - SKY).max(), 12)  # no dark blot where it was
        self.assertTrue((sky[self.s["ridge"]] == RIDGE).all())
        self.assertGreater(entry["specks"], 0)

    def test_the_cloud_layer_holds_the_cloud_faded_where_the_peak_hid_its_foot(self):
        clouds, S = self.split()
        sky_l, _ = dp.sky_layer(self.s["img"], self.s["dn"], S, clouds, self.cut, args())
        sky = np.asarray(Image.open(sky_l["path"]).convert("RGB"))
        layer, _ = dp.cloud_layer(self.s["img"], sky, S, clouds, self.cut)
        part = np.asarray(Image.open(layer["path"]).convert("RGBA")).astype(int)
        rgba = np.zeros((H, W, 4), int)
        rgba[layer["top"]:layer["bottom"]] = part
        a = rgba[..., 3]
        self.assertEqual(a[20, 170], 255)  # the cloud's top middle, far from the peak
        self.assertEqual(a[~dp.grow(clouds, 6)].max(), 0)
        foot = clouds & dp.grow(self.s["rng"], 2)
        self.assertTrue(foot.any())
        self.assertLess(a[foot].max(), 128)  # a cloud drifting off the peak shows no cut edge
        over = (rgba[..., :3] * a[..., None] + sky * (255 - a[..., None])) / 255
        whole = dp.grow(clouds, 3) & ~dp.grow(S >= 0, 12)  # its rim too, away from what hid it
        self.assertLess(np.abs(over[whole] - self.s["img"][whole]).max(), 3)  # over the sky, the painting again
        self.assertEqual(layer["rest"], dp.CLOUD_REST)

    def test_with_the_sky_apart_the_range_is_cut_as_a_nearer_band_and_is_not_the_back_layer(self):
        _, S = self.split()
        saved = dp.gen
        dp.gen = lambda argv, logfile: shutil.copy(argv[argv.index("--src") + 1], argv[argv.index("--out") + 1])
        try:
            layer, entry = dp.cut_layer(0, self.s["img"], self.s["dn"], S, [0.0, 0.4, 0.8], self.cut, args())
        finally:
            dp.gen = saved
        part = np.asarray(Image.open(layer["path"]).convert("RGBA"))[..., 3]
        a = np.zeros((H, W), np.uint8)
        a[H - part.shape[0]:] = part
        self.assertEqual(a[self.s["cloud"]].max(), 0)
        self.assertEqual(a[5, 140], 0)
        self.assertTrue((a[self.s["rng"] & ~dp.grow(~self.s["rng"], 2)] == 255).all())
        (rec,) = entry["rules"]
        self.assertEqual(rec["when"]["the back layer"], False)
        # Behind the trunk the flank runs on, straight from one side to the other: no wedge of sky on it.
        for x in range(196, 214):
            flank = int(np.ceil(self.s["peak"][0, x])) + 1
            self.assertTrue((a[flank:, x] > 0).all(), f"column {x}: the flank cut under row {flank}")


if __name__ == "__main__":
    argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter).parse_known_args()
    unittest.main()
