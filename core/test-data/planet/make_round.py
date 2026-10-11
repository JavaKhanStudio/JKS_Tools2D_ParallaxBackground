#!/usr/bin/env python3
"""Writes the planet round (r277, docs/depth-tricks.md §3): engines/rounds/planet/round.json and its pages, other
rounds' pages with a planet radius (WholePage_Model.planetRadius, format 13) so each layer is bent down toward the view's
sides by its depth, the far ones more, a still one not at all; p08 an EMPTY layer whose hook lowers its tiles by
planetDropAt (r330). No art of its own: the atlases are the floor lab's, the samples', the sequence round's and the
shaders round's. Also writes p09 and p10, the particles round's pages (core/test-data/particles) round a planet, which
engines/rounds/particles/round.json lists (q09, q10): particles are not compared in pixels.
Read by shots/.../ParallaxShots, engines/godot/tests/shots.gd and engines/jme's JmeParallaxShots;
tools/godot-parallax-shots.sh and tools/jme-parallax-shots.sh compare them. `make_round.py --flat OUT` (strength.sh)
writes the same pages with the planets dropped.
Run from the repository root: python3 core/test-data/planet/make_round.py [--flat OUT]"""
import json
import sys
from pathlib import Path

OUT = 'engines/rounds/planet'
FLOOR, SAMPLES, SEQUENCE, SHADERS = 'core/test-data/floor', 'core/test-data/samples', 'core/test-data/sequence', \
    'core/test-data/shaders'
PARTICLES = 'core/test-data/particles'


def load(path):
    return json.loads(Path(path).read_text())


def hiver(sky_still):
    """The floor lab's Hiver set, its EMPTY floor dropped; its sky band still (the sky: unbent) or scrolling."""
    p = load(f'{FLOOR}/lab.jplax')
    p['pageModel']['pageList'] = [l for l in p['pageModel']['pageList'] if l.get('kind') != 'EMPTY']
    if sky_still:
        p['pageModel']['pageList'][0]['parallaxScalingSpeedX'] = 0.0
    return p


def main(out, flat):
    def write(name, p, radius):
        if not flat:
            p['planetRadius'] = radius
        Path(out).mkdir(parents=True, exist_ok=True)
        with open(f'{out}/{name}.jplax', 'w') as f:
            json.dump(p, f, indent=1)
        return f'{OUT}/{name}.jplax'

    p01 = write('p01', hiver(True), 800)
    p02 = hiver(False)
    layers = p02['pageModel']['pageList']
    layers[1]['mirror'] = True
    layers[2]['flipX'] = True
    p02.update(fogStrength=0.02, fogColor={'r': 0.88, 'g': 0.9, 'b': 0.93, 'a': 1})
    p02 = write('p02', p02, 500)
    p03 = write('p03', load('engines/rounds/sequence/q01.jplax'), 600)
    p04 = write('p04', load('engines/rounds/shaders/s01.jplax'), 600)
    p05 = write('p05', load('engines/rounds/conformance/c02.jplax'), 700)
    p07 = write('p07', load('engines/rounds/conformance/c01.jplax'), 0)
    p08 = write('p08', load('engines/rounds/effects/e01.jplax'), 400)
    write('p09', load(f'{PARTICLES}/p02.jplax'), 400)
    write('p10', load(f'{PARTICLES}/p01.jplax'), 400)
    scenes = [
        {'id': 'p01', 'page': p01, 'atlasDir': FLOOR, 'name': 'planet-hiver-still-sky',
         'about': "the floor lab's Hiver set round a planet 800% of the world in radius (320 units): the trees bend "
                  "least, the far peaks most; the sky band, still, is the sky and stays flat"},
        {'id': 'p02', 'page': p02, 'atlasDir': FLOOR, 'name': 'planet-hiver-fogged-mirrored-flipped',
         'about': "the same set round a tighter planet (500%), every layer scrolling (the band, slowest, bends most), the "
                  "far peaks mirrored above themselves, the mountain flipped on X, under a page fog of 0.02"},
        {'id': 'p03', 'page': p03, 'atlasDir': SEQUENCE, 'name': 'planet-sequence',
         'about': "q01 (SEQUENCE strips between the hills) round a planet of 600%: each slot cut at the strips"},
        {'id': 'p04', 'page': p04, 'atlasDir': SHADERS, 'name': 'planet-shaders',
         'about': "s01 (a WAVE ripple, a FOG mist band, trimmed regions) round a planet of 600%: the effects drawn on "
                  "the bent strips"},
        {'id': 'p05', 'page': p05, 'atlasDir': SAMPLES, 'name': 'planet-repeat-xy',
         'about': "c02 (repeat X and Y, padded) round a planet of 700%, scrolling up at 30 units/s too: every row of "
                  "tiles bent alike", 'speedY': 30},
        {'id': 'p06', 'page': p01, 'atlasDir': FLOOR, 'name': 'planet-dolly-cross-fade',
         'about': "p01 dollied in from zoom 1 to 0.7 (each layer bent in its own view), cross-fading into p02 4 s in, "
                  "over 4 s: each page round its own planet", 'zoom': [1, 0.7], 'dolly': True,
         'transfer': {'at': 4, 'seconds': 4, 'page': p02}},
        {'id': 'p07', 'page': p07, 'atlasDir': SAMPLES, 'name': 'planet-from-flat',
         'about': "c01 (Hiver, flat: planetRadius 0) cross-fading into p01 4 s in, over 4 s: the flat page fades out "
                  "flat, the round one comes in bent", 'transfer': {'at': 4, 'seconds': 4, 'page': p01, 'atlasDir': FLOOR}},
        {'id': 'p08', 'page': p08, 'atlasDir': SAMPLES, 'name': 'planet-hook',
         'about': "e01 (Hiver with an EMPTY slot, 24 wide, drawn by a hook) round a planet of 400% (r330): the hook "
                  "lowers each tile as one block by planetDropAt at its middle, the images around it bent in strips",
         'hooks': {'slot': {'region': 'parallax4', 'position': 1, 'planet': True}}},
    ]
    with open(f'{out}/round.json', 'w') as f:
        json.dump({'round': 'planet',
                   'about': "A page round a planet (r277, docs/depth-tricks.md §3): each IMAGE, SHADER and SEQUENCE layer "
                            "cut in 256 vertical strips across its view, each lowered by d dx^2 / 2R, d its depth from "
                            "its speed against the front's: the far layers bend more, a still one not at all. Godot "
                            "draws its own strips (plax_planet.gd); jME runs core's PlanetBatch. Written by "
                            "core/test-data/planet/make_round.py. Read by shots/.../ParallaxShots, "
                            "engines/godot/tests/shots.gd and engines/jme's JmeParallaxShots.",
                   'scenes': scenes}, f, indent=1)
    print('wrote', out)


if __name__ == '__main__':
    if len(sys.argv) == 3 and sys.argv[1] == '--flat':
        main(sys.argv[2], True)
    else:
        main(OUT, False)
