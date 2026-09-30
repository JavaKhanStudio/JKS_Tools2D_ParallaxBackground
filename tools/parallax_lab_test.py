#!/usr/bin/env python3
"""Checks tools/parallax_lab.py lint's layout rules (r129) on pages whose faults were seen full size. Python 3 + Pillow.

  python3 tools/parallax_lab_test.py

Round 1's Hiver (s03, Simon's page as saved) has its lower 45% empty, a fir cut flat and tree strips ending in the
air: (a), (b) and (c). The pages written from the art (r92) were built around those faults and pass them; the one
thing lint finds there is real: PurpleFairy-art tiles its pink trees ('3', seam 117), and the spider web's rays stop
on a vertical line every repeat (demo/build/lab/round2/s03-t0.png, x ~ 680).
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import parallax_lab as lab  # noqa: E402


def rules(problems):
    return {p[1] for p in problems if p.startswith('(')}


class LayoutLint(unittest.TestCase):
    def test_hiver_round1(self):
        problems = lab.lint(lab.load('demo/lab/round1/s03.jplax'), 'demo/assets')
        self.assertTrue({'a', 'b', 'c'} <= rules(problems), problems)
        self.assertIn('(c) the screen from 0% to 45% of its height (from the bottom) is covered by no layer and no '
                      'gradient', problems)

    def test_pages_from_the_art(self):
        pages = {
            'Hiver-art': (lab.hiver_from_art(), 'demo/assets'),
            'Calm-art': (lab.calm_from_art(), 'editor/Files/transfer'),
            'OneNight-art': (lab.night_from_art(), 'editor/Files/Demos'),
            'CalmTree3-art': (lab.calm_tree_from_art(), 'editor/Files/Aa new'),
            'PurpleFairy-art': (lab.fairy_from_art(), 'editor/Files/Demos'),
        }
        for name, (page, atlas_dir) in pages.items():
            with self.subTest(name):
                problems = lab.layout(page, atlas_dir)
                if name == 'PurpleFairy-art':
                    self.assertEqual(['(e) layer 4 (3#0) is tiled on X but its left and right edges differ (seam 117 '
                                      '> 20): a cut shows every repeat'], problems)
                else:
                    self.assertEqual([], problems)

    def test_cropped_top_is_not_a_crest(self):
        """calm.atlas's tallest peak and OneNight's wave crests touch their strip's top: a point, not a cut."""
        for page, atlas_dir in ((lab.calm_from_art(), 'editor/Files/transfer'),
                                (lab.night_from_art(), 'editor/Files/Demos')):
            self.assertNotIn('a', rules(lab.layout(page, atlas_dir)))


if __name__ == '__main__':
    unittest.main()
