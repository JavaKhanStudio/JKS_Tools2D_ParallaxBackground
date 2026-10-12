#!/usr/bin/env python3
"""Writes the planet round (r277, r347, docs/depth-tricks.md §3): engines/rounds/planet/round.json and its pages, other
rounds' pages with a planet radius (WholePage_Model.planetRadius, format 13) so each is the side of a world turning round
one centre under the view, every layer wrapped round it alike, what stands up leaning out from it; p08 an EMPTY layer
whose hook stands its tiles on their foot by planetPlaceAt and planetAngleAt. No art of its own: the atlases are the floor lab's, the samples', the sequence round's and the
shaders round's. Also writes p09 and p10, the particles round's pages (core/test-data/particles) round a planet, which
engines/rounds/particles/round.json lists (q09, q10): particles are not compared in pixels.
Read by shots/.../ParallaxShots, engines/godot/tests/shots.gd and engines/jme's JmeParallaxShots;
engines/godot/tools/frame-check.sh and engines/jme/tools/frame-check.sh compare them. `make_round.py --flat OUT` (strength.sh)
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
    """The floor lab's Hiver set, its EMPTY floor dropped; its sky band still (wrapped, but not turning) or scrolling."""
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

    p01 = write('p01', hiver(True), 400)
    p02 = hiver(False)
    layers = p02['pageModel']['pageList']
    layers[1]['mirror'] = True
    layers[2]['flipX'] = True
    p02.update(fogStrength=0.02, fogColor={'r': 0.88, 'g': 0.9, 'b': 0.93, 'a': 1})
    p02 = write('p02', p02, 250)
    p03 = write('p03', load('engines/rounds/sequence/q01.jplax'), 300)
    p04 = write('p04', load('engines/rounds/shaders/s01.jplax'), 300)
    p05 = write('p05', load('engines/rounds/conformance/c02.jplax'), 350)
    p07 = write('p07', load('engines/rounds/conformance/c01.jplax'), 0)
    p08 = write('p08', load('engines/rounds/effects/e01.jplax'), 250)
    write('p09', load(f'{PARTICLES}/p02.jplax'), 400)
    write('p10', load(f'{PARTICLES}/p01.jplax'), 400)
    scenes = [
        {'id': 'p01', 'page': p01, 'atlasDir': FLOOR, 'name': 'planet-hiver-still-sky',
         'about': "the floor lab's Hiver set round a planet 400% of the world in radius (160 units): every layer "
                  "wrapped alike, each turning at its own speed; the sky band, still, wrapped but not turning"},
        {'id': 'p02', 'page': p02, 'atlasDir': FLOOR, 'name': 'planet-hiver-fogged-mirrored-flipped',
         'about': "the same set round a tighter planet (250%), every layer scrolling, the far peaks mirrored above "
                  "themselves, the mountain flipped on X, under a page fog of 0.02"},
        {'id': 'p03', 'page': p03, 'atlasDir': SEQUENCE, 'name': 'planet-sequence',
         'about': "q01 (SEQUENCE strips between the hills) round a planet of 300%: each slot cut at the strips"},
        {'id': 'p04', 'page': p04, 'atlasDir': SHADERS, 'name': 'planet-shaders',
         'about': "s01 (a WAVE ripple, a FOG mist band, trimmed regions) round a planet of 300%: the effects drawn on "
                  "the wrapped strips"},
        {'id': 'p05', 'page': p05, 'atlasDir': SAMPLES, 'name': 'planet-repeat-xy',
         'about': "c02 (repeat X and Y, padded) round a planet of 350%, scrolling up at 30 units/s too: every row of "
                  "tiles wrapped, the row just over the view's top shown at its corners", 'speedY': 30},
        {'id': 'p06', 'page': p01, 'atlasDir': FLOOR, 'name': 'planet-dolly-cross-fade',
         'about': "p01 dollied out from zoom 1 to 2 (each layer wrapped in its own view, round one circle on "
                  "screen), cross-fading into p02 4 s in, over 4 s: each page round its own planet", 'zoom': [1, 2],
         'dolly': True,
         'transfer': {'at': 4, 'seconds': 4, 'page': p02}},
        {'id': 'p07', 'page': p07, 'atlasDir': SAMPLES, 'name': 'planet-from-flat',
         'about': "c01 (Hiver, flat: planetRadius 0) cross-fading into p01 4 s in, over 4 s: the flat page fades out "
                  "flat, the round one comes in wrapped", 'transfer': {'at': 4, 'seconds': 4, 'page': p01, 'atlasDir': FLOOR}},
        {'id': 'p08', 'page': p08, 'atlasDir': SAMPLES, 'name': 'planet-hook',
         'about': "e01 (Hiver with an EMPTY slot, 24 wide, drawn by a hook) round a planet of 250% (r347): the hook "
                  "stands each tile on its foot, placed by planetPlaceAt and turned by planetAngleAt, the images around "
                  "it wrapped in strips",
         'hooks': {'slot': {'region': 'parallax4', 'position': 1, 'planet': True}}},
    ]
    with open(f'{out}/round.json', 'w') as f:
        json.dump({'round': 'planet',
                   'about': "A page round a planet (r277, r347, docs/depth-tricks.md §3): the side of a world turning "
                            "round one centre R under the middle of the view's bottom. Each IMAGE, SHADER and SEQUENCE "
                            "layer is cut in 256 vertical strips across its view, each drawn as the quad its corners go "
                            "to, x along the ground's arc and y along the radius: every layer alike, each turning at its "
                            "own speed, what stands up leaning out. Godot draws its own quads (plax_planet.gd); jME runs "
                            "core's PlanetBatch. Written by "
                            "core/test-data/planet/make_round.py. Read by shots/.../ParallaxShots, "
                            "engines/godot/tests/shots.gd and engines/jme's JmeParallaxShots.",
                   'scenes': scenes}, f, indent=1)
    print('wrote', out)


if __name__ == '__main__':
    if len(sys.argv) == 3 and sys.argv[1] == '--flat':
        main(sys.argv[2], True)
    else:
        main(OUT, False)
