#!/usr/bin/env python3
"""Writes the haze lab's page (r215, doubt d17; core/browser-test/.../HazeLab, tools/haze-lab.sh):
core/test-data/haze/HiverHazeLab.atlas (the shaders round's HiverFog.atlas, Hiver and the mist, then the particles
round's snowflake on a page of its own) and core/test-data/haze/lab.jplax: the haze round's h01 (s01 with a haze of
0.25 on its mist) with an EMPTY layer the lab's hook draws in, and p01's snow (a PARTICLES layer), both behind the mist,
which the shipped haze leaves as they are.
Run from the repository root: python3 core/test-data/haze/make_lab.py"""
import copy, json

HERE = 'core/test-data/haze'

fog = open('core/test-data/shaders/HiverFog.atlas').read().strip().split('\n')
fog = ['../shaders/' + line if line.endswith('.png') and not line.startswith('..') else line for line in fog]
snow = open('core/test-data/particles/HiverSnow.atlas').read().strip().split('\n\n')[-1].split('\n')
snow[0] = '../particles/' + snow[0]
open(f'{HERE}/HiverHazeLab.atlas', 'w').write('\n'.join(fog) + '\n\n' + '\n'.join(snow) + '\n')

page = json.load(open('engines/godot/tests/haze/h01.jplax'))
layers = page['pageModel']['pageList']
assert layers[5]['name'] == 'mist'
# What a game draws in an EMPTY layer: the lab's hook draws dark towers in each tile, three layers behind the mist.
hook = {'regionName': None, 'regionPosition': 0, 'flipX': False, 'flipY': False,
        'parallaxScalingSpeedX': 0.017, 'parallaxScalingSpeedY': 0.017, 'speedXAtRest': 0.0,
        'sizeRatio': 0.3, 'decal_X_Ratio': 5.0, 'decal_Y_Ratio': 62.0,
        'padX': 3.0, 'padXFactor': 0.0, 'padY': 0.0, 'padYFactor': 0.0, 'kind': 'EMPTY', 'name': 'towers', 'mirror': False}
particles = copy.deepcopy(json.load(open('core/test-data/particles/p01.jplax'))['pageModel']['pageList'][5])
assert particles['kind'] == 'PARTICLES'
layers.insert(5, particles)
layers.insert(3, hook)
page['pageModel']['atlasName'] = 'HiverHazeLab.atlas'
json.dump(page, open(f'{HERE}/lab.jplax', 'w'), indent=1)
