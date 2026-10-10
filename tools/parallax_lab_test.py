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



class PixelArtSeams(unittest.TestCase):
    """(e) on hard-alpha pixel art (r253): pixel-xl's pines (r243) step 27-75 between neighbour columns everywhere and
    loop as smoothly; a fixed limit of 20 called their edge (58) a cut."""
    PATH = 'tools/r239-ai-strips/page/pixel-xl.jplax'

    def setUp(self):
        self.page = lab.load(self.PATH)
        self.atlas_dir = lab.find_atlas_dir(self.PATH, self.page)

    def test_a_pixel_art_strip_that_loops_has_no_seam(self):
        self.assertEqual([], [p for p in lab.layout(self.page, self.atlas_dir) if p.startswith('(e)')])

    def test_a_real_cut_in_pixel_art_is_still_one(self):
        """The pines with their right half's colours inverted: the edges differ far more than the columns inside."""
        import parallax_regions as regions_tool
        regions = {(r['name'], r['pos']): r for r in regions_tool.read_atlas(
            os.path.join(lab.ROOT, self.atlas_dir, self.page['pageModel']['atlasName']))}
        i, pines = 2, lab.layers(self.page)[2]
        img = regions_tool.image_of(regions[(pines['regionName'], pines['regionPosition'])], {})
        w, h = img.size
        right = img.crop((w // 2, 0, w, h))
        r, g, b, a = right.split()
        img.paste(regions_tool.Image.merge('RGBA', [c.point(lambda v: 255 - v) for c in (r, g, b)] + [a]), (w // 2, 0))
        p = lab._place(self.page, i, pines, img)
        seam, limit = lab._seam_on_screen(img, p, regions_tool), lab._seam_limit([img], p)
        self.assertGreater(limit, lab.SEAM, 'the art steps more than 20: the limit is its own')
        self.assertGreater(seam, limit, f'seam {seam}, limit {limit}')


class FaintAlphaSeams(unittest.TestCase):
    """(e) weighs a join as it shows on screen (r280): a pixel at alpha 0 keeps whatever colour the cut left (layer.py
    writes 255,0,0 or 0,0,0 there), so a faint alpha-20 haze ending against it is a step of about 20, not of 255."""
    W, H = 64, 200

    def strip(self, opaque, band):
        """Rows 0-149 (the top) as opaque, the bottom 50 as band; RGBA tuples."""
        from PIL import Image
        img = Image.new('RGBA', (self.W, self.H), opaque)
        img.paste(Image.new('RGBA', (self.W, 50), band), (0, 150))
        return img

    def setUp(self):
        self.p = lab.Placed(0, {}, [1.0], 0.0, lab.SCREEN_H, True, False)
        self.haze = self.strip((0, 0, 0, 0), (230, 235, 240, 20))
        self.cut = self.strip((0, 0, 0, 0), (255, 0, 0, 0))

    def test_a_faint_haze_ending_at_a_join_is_a_faint_step(self):
        seam = lab._join_on_screen(self.haze, self.cut, self.p)
        self.assertLess(seam, 25, f'seam {seam}')

    def test_a_hard_colour_cut_is_still_one(self):
        white, black = self.strip((255, 255, 255, 255), (255, 255, 255, 255)), self.strip((0, 0, 0, 255), (0, 0, 0, 255))
        self.assertGreater(lab._join_on_screen(white, black, self.p), lab.SEAM)

    def test_the_art_s_own_steps_are_weighed_the_same(self):
        """A layer whose columns alternate a faint haze and a cut alpha 0 red steps about 20 between them, not 255."""
        from PIL import Image
        img = Image.new('RGBA', (self.W, self.H), (255, 0, 0, 0))
        for x in range(0, self.W, 2):
            img.paste(Image.new('RGBA', (1, self.H), (230, 235, 240, 20)), (x, 0))
        self.assertTrue(all(s < 25 for s in lab._column_steps(img)), lab._column_steps(img)[:4])

    def test_a_single_layer_s_seam_is_weighed_the_same(self):
        """parallax_regions.measure's seam, between a region's two edges: haze on the left, alpha 0 red on the right."""
        import parallax_regions as regions_tool
        from PIL import Image
        img = Image.new('RGBA', (self.W, self.H), (230, 235, 240, 20))
        img.paste(Image.new('RGBA', (1, self.H), (255, 0, 0, 0)), (self.W - 1, 0))
        self.assertLess(regions_tool.measure(img)['seam'], 25)


class PixelArtCover(unittest.TestCase):
    """(b) on a pixel-art page (r281, r239's postapo-pixel): a 17-row layer whose art fills its bottom edge at 1.33,
    before a 35-row front from 0 whose bottom four rows (to 1.67) are solid and the fifth 0.89 opaque. The edge is
    covered; half the back layer's row above it (1.75) is in the front's thinner fifth row."""

    def placed(self, front_rows):
        behind = lab.Placed(2, {'regionName': 'l2', 'regionPosition': 0}, [1.0] * 17, 1.33, 14.17, True, False)
        front = lab.Placed(3, {'regionName': 'l3', 'regionPosition': 0}, front_rows, 0.0, 14.58, True, False)
        return [behind, front]

    def faults(self, front_rows):
        return [f for f in lab._edge_faults(self.placed(front_rows)) if f.startswith('(b) layer 2')]

    def test_a_solid_band_on_the_edge_covers_it(self):
        self.assertEqual([], self.faults([1.0] * 4 + [0.89] + [0.7] * 30))

    def test_a_band_ending_under_the_edge_does_not(self):
        """The same front with only three solid rows (to 1.25): the edge at 1.33 is in its 0.89 row."""
        self.assertEqual(1, len(self.faults([1.0] * 3 + [0.89] * 2 + [0.7] * 30)))


if __name__ == '__main__':
    unittest.main()
