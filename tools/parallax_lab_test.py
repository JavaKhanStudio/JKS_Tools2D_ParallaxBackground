#!/usr/bin/env python3
"""What parallax_lab.py lint says of imageless layers (r186 EMPTY, r193 PARTICLES). Run: python3 tools/parallax_lab_test.py"""
import copy
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import parallax_lab as lab  # noqa: E402

PARTICLES = 'core/test-data/particles'


class ImagelessLayers(unittest.TestCase):
    def setUp(self):
        self.page = lab.load(PARTICLES + '/p01.jplax')
        self.snow = lab.layers(self.page)[5]

    def test_load_keeps_the_particle_fields(self):
        self.assertEqual(('PARTICLES', 'snow-wide.p', 'VIEW'),
                         (self.snow['kind'], self.snow['particlesLibgdx'], self.snow['particlesAnchor']))

    def test_a_particle_layer_is_no_missing_region(self):
        problems = lab.lint(self.page, PARTICLES)
        self.assertFalse([p for p in problems if 'no region' in p or 'snow' in p], problems)
        self.assertEqual("PARTICLES 'snow'", lab.label(self.snow))

    def test_a_missing_effect_file_is_said(self):
        page = copy.deepcopy(self.page)
        lab.layers(page)[5]['particlesLibgdx'] = 'rain.p'
        self.assertIn(f"layer 5 (PARTICLES 'snow'): no {PARTICLES}/rain.p beside the atlas: it draws nothing",
                      lab.lint(page, PARTICLES))

    def test_no_effect_file_is_said(self):
        page = copy.deepcopy(self.page)
        del lab.layers(page)[5]['particlesLibgdx']
        self.assertIn("layer 5 (PARTICLES 'snow') names no libGDX effect (particlesLibgdx): it draws nothing",
                      lab.lint(page))

    def test_the_r186_empty_page_still_lints_clean(self):
        self.assertEqual([], lab.lint(lab.load('tools/r186-empty-page/page.jplax'), 'core/test-data/samples'))


if __name__ == '__main__':
    unittest.main()
