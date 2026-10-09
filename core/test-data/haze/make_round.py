#!/usr/bin/env python3
"""Writes the fog round (r217, was r211's haze round): core/test-data/haze/HiverHaze.atlas (HiverSeq.atlas's Hiver
regions and sequence segments, then the shaders round's mist on a page of its own) and engines/godot/tests/fog/
f01-f05.jplax and round.json: the shaders and sequence rounds' pages with a page fog (format 10), so every IMAGE, SHADER
and SEQUENCE layer is mixed toward the fog's colour by 1 - exp(-strength (1 / speed - 1 / front)), in every repeat mode,
in colours, through a format 9 page whose FOG layer's haze becomes its fog, and through a cross-fade into a page of
another fog while tinting. tools/r217-fog/strength.sh checks that leaving the fog out moves every still past the rounds'
2/255. Run from the repository root: python3 core/test-data/haze/make_round.py"""
import copy, json, os

HERE = 'core/test-data/haze'
ROUND = 'engines/godot/tests/fog'

sequence = open('core/test-data/sequence/HiverSeq.atlas').read().strip().split('\n')
sequence = ['../sequence/' + line if line.endswith('.png') and not line.startswith('..') else line for line in sequence]
mist = open('core/test-data/shaders/HiverFog.atlas').read().strip().split('\n\n')[-1].split('\n')
mist[0] = '../shaders/' + mist[0]
open(f'{HERE}/HiverHaze.atlas', 'w').write('\n'.join(sequence) + '\n\n' + '\n'.join(mist) + '\n')
os.makedirs(ROUND, exist_ok=True)


def load(path):
    return json.load(open(path))


def save(page, out):
    page['pageModel']['atlasName'] = 'HiverHaze.atlas'
    json.dump(page, open(f'{ROUND}/{out}.jplax', 'w'), indent=1)


def fog(page, strength, color=None):
    page['fogStrength'] = strength
    if color:
        page['fogColor'] = {'r': color[0], 'g': color[1], 'b': color[2], 'a': 1.0}
    return page


# The pages' speeds run 0.01 (back) to 0.041 (front): 1 / speed - 1 / front is 0 to 75.6.

def f01():
    # Repeat X, the mist's white: 0.03, the back layer 1 - exp(-0.03 x 75.6) = 0.90, the layer at 0.024 0.40.
    return fog(load('engines/godot/tests/shaders/s01.jplax'), 0.03)


def f02():
    # Repeat X and Y, padded, scrolling up, near-hidden in a night blue: q02 with s02's padded mist in front of its
    # SEQUENCE layer; 0.06, the back layer 0.99.
    p = load('engines/godot/tests/sequence/q02.jplax')
    p['pageModel']['pageList'].insert(6, copy.deepcopy(load('engines/godot/tests/shaders/s02.jplax')['pageModel']['pageList'][4]))
    return fog(p, 0.06, (0.25, 0.3, 0.45))


def f03():
    # Repeat Y, mirrored copies, a thin warm grey: 0.015.
    return fog(load('engines/godot/tests/shaders/s03.jplax'), 0.015, (0.75, 0.7, 0.65))


def f04():
    # No repeat, written as format 9 did: no page fog, the mist's shaderHaze 0.3, which becomes the page's fog.
    p = load('engines/godot/tests/shaders/s04.jplax')
    p['pageModel']['pageList'][6]['shaderHaze'] = 0.3
    return p


for name, make in [('f01', f01), ('f02', f02), ('f03', f03), ('f04', f04)]:
    save(make(), name)

ABOUT = {
    'f01': ('fog-repeat-X', 'c01 (repeat X): a page fog of 0.03 in the mist\'s white, the back layer 0.90, a WAVE and a FOG layer fogged by their speed too'),
    'f02': ('fog-repeat-XY-sequence-night', 'c02 (repeat X and Y, padded) with q02\'s SEQUENCE layer, scrolling up at 30 units/s: a night-blue fog of 0.06 near-hides the back layers'),
    'f03': ('fog-repeat-Y-warm', 'c03 (repeat Y, mirrored copies), scrolling up at 30 units/s: a thin warm-grey fog of 0.015'),
    'f04': ('fog-no-repeat-format-9', 'c05 (no repeat) as format 9 wrote it: the mist\'s shaderHaze 0.3 and no page fog, read as a fog of 0.3 in the mist\'s white'),
}
SPEED_Y = {'f02': 30, 'f03': 30}
scenes = []
for name, (title, about) in ABOUT.items():
    scene = {'id': name, 'page': f'{ROUND}/{name}.jplax', 'atlasDir': HERE, 'name': title, 'about': about}
    if name in SPEED_Y:
        scene['speedY'] = SPEED_Y[name]
    scenes.append(scene)
scenes.append({'id': 'f05', 'page': f'{ROUND}/f01.jplax', 'atlasDir': HERE, 'name': 'fog-cross-fade-tinted',
               'about': 'f01 cross-fading into f02 (another fog, another colour) while tinting orange: each page fogs its own layers by its own fog, faded and tinted, the fog\'s colours untinted',
               'transfer': {'at': 4, 'seconds': 4, 'page': f'{ROUND}/f02.jplax'}, 'tint': {'at': 4, 'seconds': 4, 'color': [1.0, 0.55, 0.3, 1.0]}})
json.dump({'round': 'fog',
           'about': 'Page fog (r217, format 10): each IMAGE, SHADER and SEQUENCE layer mixed toward the page\'s fog colour by 1 - exp(-strength (1 / speed - 1 / front)), front the page\'s fastest speed ratio X, in every repeat mode, in colours, from a format 9 FOG layer\'s haze, and through a cross-fade between two fogs while tinting. Written by core/test-data/haze/make_round.py. Read by shots/.../ParallaxShots, engines/godot/tests/shots.gd and engines/jme\'s JmeParallaxShots.',
           'scenes': scenes}, open(f'{ROUND}/round.json', 'w'), indent=1)
