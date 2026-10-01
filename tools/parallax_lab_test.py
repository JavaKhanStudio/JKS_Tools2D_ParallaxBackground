#!/usr/bin/env python3
"""What parallax_lab.py lint says of imageless layers (r186 EMPTY, r193 PARTICLES) and SHADER layers (r180).
Run: python3 tools/parallax_lab_test.py"""
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


class ShaderLayers(unittest.TestCase):
    """engines/godot/tests/shaders/s04.jplax holds a WAVE with no wavelength (layer 5) and a FOG at 1.7 (layer 6)."""

    def setUp(self):
        self.page = lab.load('engines/godot/tests/shaders/s04.jplax')

    def test_load_keeps_the_shader_fields(self):
        fog = lab.layers(self.page)[6]
        self.assertEqual(('SHADER', 'FOG', 1.7, 6, 2.5), (fog['kind'], fog['shaderEffect'], fog['shaderAmplitude'],
                                                         fog['shaderWavelength'], fog['shaderSpeed']))

    def test_a_shader_layer_names_its_region(self):
        self.assertEqual('mist#0', lab.label(lab.layers(self.page)[6]))

    def test_its_faults_are_said(self):
        problems = lab.lint(self.page)
        self.assertIn("layer 5 (parallax4#0) has no shaderWavelength: it draws its image without its WAVE", problems)
        self.assertIn("layer 6 (mist#0): FOG thins by 0 to 1, its shaderAmplitude 1.7 reads as 1", problems)

    def test_a_good_shader_layer_says_nothing(self):
        problems = lab.lint(lab.load('engines/godot/tests/shaders/s01.jplax'))
        self.assertFalse([p for p in problems if 'shader' in p or 'FOG' in p], problems)


if __name__ == '__main__':
    unittest.main()
