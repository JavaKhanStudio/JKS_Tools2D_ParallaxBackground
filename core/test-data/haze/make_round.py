#!/usr/bin/env python3
"""Writes the haze round (r211): core/test-data/haze/HiverHaze.atlas (HiverSeq.atlas's Hiver regions and sequence
segments, then the shaders round's mist on a page of its own) and engines/godot/tests/haze/h01-h04.jplax and round.json:
the shaders and sequence rounds' pages with a depth haze on their FOG layers, so every IMAGE, WAVE and SEQUENCE layer
behind a mist is mixed toward its white, in every repeat mode, two mists stacked, and through a cross-fade.
tools/r211-haze/strength.sh checks that leaving the haze out moves every still past the rounds' 2/255.
Run from the repository root: python3 core/test-data/haze/make_round.py"""
import copy, json

HERE = 'core/test-data/haze'
ROUND = 'engines/godot/tests/haze'

sequence = open('core/test-data/sequence/HiverSeq.atlas').read().strip().split('\n')
sequence = ['../sequence/' + line if line.endswith('.png') and not line.startswith('..') else line for line in sequence]
mist = open('core/test-data/shaders/HiverFog.atlas').read().strip().split('\n\n')[-1].split('\n')
mist[0] = '../shaders/' + mist[0]
open(f'{HERE}/HiverHaze.atlas', 'w').write('\n'.join(sequence) + '\n\n' + '\n'.join(mist) + '\n')


def load(path):
    return json.load(open(path))


def save(page, out):
    page['pageModel']['atlasName'] = 'HiverHaze.atlas'
    json.dump(page, open(f'{ROUND}/{out}.jplax', 'w'), indent=1)


def h01():
    # Repeat X: Simon's lab start, 0.25, on the mist between the two front layers; a WAVE and four images behind it.
    p = load('engines/godot/tests/shaders/s01.jplax')
    p['pageModel']['pageList'][5]['shaderHaze'] = 0.25
    return p


def h02():
    # Repeat X and Y, padded, scrolling up: the sequence round's q02 with s02's padded mist in front of its SEQUENCE
    # layer, which is hazed through no effect, as the images are.
    p = load('engines/godot/tests/sequence/q02.jplax')
    fog = copy.deepcopy(load('engines/godot/tests/shaders/s02.jplax')['pageModel']['pageList'][4])
    fog['shaderHaze'] = 0.35
    p['pageModel']['pageList'].insert(6, fog)
    return p


def h03():
    # Repeat Y, mirrored copies: two mists, the flipped hill's FOG (0.15) behind the band's (0.2): their hazes stack.
    p = load('engines/godot/tests/shaders/s03.jplax')
    p['pageModel']['pageList'][4]['shaderHaze'] = 0.15
    p['pageModel']['pageList'][6]['shaderHaze'] = 0.2
    return p


def h04():
    # No repeat: a haze of 0.3 on the mist, behind it a FOG with none and WAVEs, one without a wavelength.
    p = load('engines/godot/tests/shaders/s04.jplax')
    p['pageModel']['pageList'][6]['shaderHaze'] = 0.3
    return p


for name, make in [('h01', h01), ('h02', h02), ('h03', h03), ('h04', h04)]:
    save(make(), name)

ABOUT = {
    'h01': ('haze-repeat-X', 'c01 (repeat X): the mist between the two front layers hazes the five behind it by 0.25 a step (Simon\'s r208 start), a WAVE among them'),
    'h02': ('haze-repeat-XY-sequence', 'c02 (repeat X and Y, padded) with q02\'s SEQUENCE layer, scrolling up at 30 units/s: a padded mist of haze 0.35 in front of the cycle hazes it and every image behind'),
    'h03': ('haze-repeat-Y-two-mists', 'c03 (repeat Y, mirrored copies), scrolling up at 30 units/s: a mist of 0.2 in front of a FOG hill of 0.15, the layers behind both mixed by the two'),
    'h04': ('haze-no-repeat', 'c05 (no repeat): a mist of 0.3 in front of FOG and WAVE layers, a WAVE with no wavelength hazed all the same'),
}
SPEED_Y = {'h02': 30, 'h03': 30}
scenes = []
for name, (title, about) in ABOUT.items():
    scene = {'id': name, 'page': f'{ROUND}/{name}.jplax', 'atlasDir': HERE, 'name': title, 'about': about}
    if name in SPEED_Y:
        scene['speedY'] = SPEED_Y[name]
    scenes.append(scene)
scenes.append({'id': 'h05', 'page': f'{ROUND}/h01.jplax', 'atlasDir': HERE, 'name': 'haze-cross-fade-tinted',
               'about': 'h01 cross-fading into itself (its layers cloned) while tinting orange: each page hazes its own layers, faded and tinted, the haze\'s white untinted',
               'transfer': {'at': 4, 'seconds': 4}, 'tint': {'at': 4, 'seconds': 4, 'color': [1.0, 0.55, 0.3, 1.0]}})
json.dump({'round': 'haze',
           'about': 'Depth haze (r211, format 9): FOG layers with a shaderHaze mix every layer of their page behind them toward the mist\'s white (0.93, 0.95, 0.97), 1 - (1 - haze)^n at n layers back, in every repeat mode, through a SEQUENCE layer, two mists stacked, and a cross-fade while tinting. Written by core/test-data/haze/make_round.py. Read by shots/.../ParallaxShots, engines/godot/tests/shots.gd and engines/jme\'s JmeParallaxShots.',
           'scenes': scenes}, open(f'{ROUND}/round.json', 'w'), indent=1)
