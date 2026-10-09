#!/usr/bin/env python3
"""What parallax_lab.py lint says of imageless layers (r186 EMPTY, r193 PARTICLES), SHADER layers (r180) and SEQUENCE
layers (r201).
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


class SequenceLayers(unittest.TestCase):
    """engines/godot/tests/sequence/q01.jplax: layer 4 chains tower, river and stone from HiverSeq.atlas."""
    PATH = 'engines/godot/tests/sequence/q01.jplax'

    def setUp(self):
        self.page = lab.load(self.PATH)
        self.atlas_dir = lab.find_atlas_dir(self.PATH, self.page)
        self.strip = lab.layers(self.page)[4]

    def faults(self, page, atlas_dir=None):
        """sequence_faults' lines, not layout()'s (which start with their letter: SequenceLayout's)."""
        return [p for p in lab.lint(page, atlas_dir) if 'SEQUENCE' in p and not p.startswith('(')]

    def test_load_keeps_the_sequence_fields(self):
        self.assertEqual(('SEQUENCE', 12, 3), (self.strip['kind'], self.strip['sequenceLength'],
                                               len(self.strip['sequenceSegments'])))

    def test_a_good_sequence_says_nothing(self):
        self.assertEqual([], self.faults(self.page, self.atlas_dir))

    def test_a_segment_missing_from_the_atlas_is_said(self):
        page = copy.deepcopy(self.page)
        lab.layers(page)[4]['sequenceSegments'][1]['regionName'] = 'lake'
        lab.layers(page)[4]['sequenceSegments'][2]['regionPosition'] = 3
        self.assertEqual(["layer 4 (SEQUENCE 'strip'): segment 1 names no region lake#0 in HiverSeq.atlas: the page"
                          " fails to load",
                          "layer 4 (SEQUENCE 'strip'): segment 2 names no region stone#3 in HiverSeq.atlas: the page"
                          " fails to load"], self.faults(page, self.atlas_dir))

    def test_no_segments_is_said(self):
        page = copy.deepcopy(self.page)
        lab.layers(page)[4]['sequenceSegments'] = []
        self.assertEqual(["layer 4 (SEQUENCE 'strip') has no sequenceSegments: it draws nothing"], self.faults(page))

    def test_a_length_under_1_is_said(self):
        page = copy.deepcopy(self.page)
        lab.layers(page)[4]['sequenceLength'] = 0
        self.assertEqual(["layer 4 (SEQUENCE 'strip'): its sequenceLength 0 reads as 1, one segment repeated"],
                         self.faults(page))

    def test_weights_all_0_or_less_are_said(self):
        page = copy.deepcopy(self.page)
        for weight, segment in zip((0, -2, 0), lab.layers(page)[4]['sequenceSegments']):
            segment['weight'] = weight
        self.assertEqual(["layer 4 (SEQUENCE 'strip'): no segment weighs above 0, so each weighs 1"],
                         self.faults(page))
        lab.layers(page)[4]['sequenceSegments'][0]['weight'] = 1
        self.assertEqual([], self.faults(page), 'one weight above 0 is enough')

    def test_an_unnamed_sequence_reads_as_its_segments(self):
        strip = dict(self.strip, name=None)
        self.assertEqual('SEQUENCE tower+river+stone', lab.label(strip))



class SequenceLayout(unittest.TestCase):
    """layout() measures a SEQUENCE layer from its segments (r245): it covers what every segment covers, and each join
    its cycle can make is checked for a seam."""
    PAINTED = 'tools/r239-ai-strips/page/painted.jplax'
    Q01 = 'engines/godot/tests/sequence/q01.jplax'

    def layout(self, path, change=None):
        page = lab.load(path)
        if change:
            change(lab.layers(page))
        return lab.layout(page, lab.find_atlas_dir(path, page))

    def test_a_sequence_in_front_covers_the_edge_behind_it(self):
        self.assertEqual([], self.layout(self.PAINTED), 'the pines cover the hills\' bottom edge (r239, render 06)')
        self.assertEqual(['(b) layer 1 (hills#0): its art reaches its bottom edge, on screen at 4% of its height, and'
                          ' no nearer layer covers that edge'], self.layout(self.PAINTED, lambda ls: ls.pop()),
                         'without the pines the edge shows: the check is not blind')

    def test_two_segments_whose_edges_differ_are_named(self):
        def stone_and_bridge(ls):
            ls[8]['sequenceSegments'] = [s for s in ls[8]['sequenceSegments'] if s['regionName'] in ('stone', 'bridge')]
        faults = [p for p in self.layout(self.Q01, stone_and_bridge) if p.startswith('(e) layer 8')]
        self.assertEqual(1, len(faults), faults)
        for join in ('stone>bridge', 'bridge>stone', 'stone>stone', 'bridge>bridge'):
            self.assertIn(join, faults[0])

    def test_padding_between_segments_makes_no_join(self):
        def pad(ls):
            ls[8]['padX'] = 1.0
        self.assertEqual([], [p for p in self.layout(self.Q01, pad) if p.startswith('(e) layer 8')])

    def test_a_weight_0_segment_makes_no_join(self):
        def only_stone(ls):
            for s in ls[8]['sequenceSegments']:
                s['weight'] = 1 if s['regionName'] == 'stone' else 0
        faults = [p for p in self.layout(self.Q01, only_stone) if p.startswith('(e) layer 8')]
        self.assertEqual(1, len(faults), faults)
        self.assertIn(': stone>stone ', faults[0])
        self.assertNotIn('bridge', faults[0])


if __name__ == '__main__':
    unittest.main()
