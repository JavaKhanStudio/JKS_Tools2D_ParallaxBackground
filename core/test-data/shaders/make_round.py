#!/usr/bin/env python3
"""Writes the shaders round (r180, docs/effect-layers.md phase 3): core/test-data/shaders/mist.png and HiverFog.atlas
(Hiver's regions, then a white mist band on a page of its own), and engines/godot/tests/shaders/s01-s04.jplax, each a
conformance page (c01, c02, c03, c05) with a layer turned into a WAVE and a mist band drawn through FOG, s06.jplax,
the mist alone tiling on X (r229), and s07.jplax, an even slab (slab.png, slab.atlas) through FOG tiling on Y (r262).
Run from the repository root: python3 core/test-data/shaders/make_round.py (needs Pillow)."""
import json, math
from PIL import Image

HERE = 'core/test-data/shaders'
ROUND = 'engines/godot/tests/shaders'

# A white band, opaque in its middle rows and clear at its top and bottom: a mist FOG thins into patches.
W, H = 512, 128
mist = Image.new('RGBA', (W, H))
mist.putdata([(255, 255, 255, round(230 * math.sin(math.pi * (y + 0.5) / H) ** 2)) for y in range(H) for x in range(W)])
mist.save(f'{HERE}/mist.png')

hiver = open('core/test-data/samples/Hiver.atlas').read().strip().split('\n')
hiver[0] = '../samples/Hiver.png'
open(f'{HERE}/HiverFog.atlas', 'w').write('\n'.join(hiver) + f'''

mist.png
size: {W}, {H}
format: RGBA8888
filter: Linear,Linear
repeat: none
mist
  rotate: false
  xy: 0, 0
  size: {W}, {H}
  orig: {W}, {H}
  offset: 0, 0
  index: -1
''')


def shader(layer, effect, amplitude, wavelength, speed):
    layer.update(kind='SHADER', shaderEffect=effect, shaderAmplitude=amplitude, shaderWavelength=wavelength,
                 shaderSpeed=speed)
    return layer


def mist_layer(size, decal_x, decal_y, ratio, amplitude, wavelength, speed, **more):
    layer = {'regionName': 'mist', 'regionPosition': 0, 'flipX': False, 'flipY': False,
             'parallaxScalingSpeedX': ratio, 'parallaxScalingSpeedY': ratio, 'speedXAtRest': 0.0,
             'sizeRatio': size, 'decal_X_Ratio': decal_x, 'decal_Y_Ratio': decal_y,
             'padX': 0.0, 'padXFactor': 0.0, 'padY': 0.0, 'padYFactor': 0.0, 'mirror': False, 'name': 'mist'}
    layer.update(more)
    return shader(layer, 'FOG', amplitude, wavelength, speed)


def page(source, out, change):
    p = json.load(open(f'engines/godot/tests/conformance/{source}.jplax'))
    p['pageModel']['atlasName'] = 'HiverFog.atlas'
    change(p['pageModel']['pageList'])
    json.dump(p, open(f'{ROUND}/{out}.jplax', 'w'), indent=1)


def s01(layers):
    # Repeat X, trimmed regions: the near hills and the front trees ripple, a mist band drifts between the two front
    # layers.
    shader(layers[3], 'WAVE', 0.25, 1.2, 0.8)
    shader(layers[6], 'WAVE', 0.35, 1.6, 1.1)
    layers.insert(5, mist_layer(1.4, 0, 45, 0.03, 1.0, 5, 1.5))


def s02(layers):
    # Repeat X and Y, padded, scrolling up: a ripple running down (negative speed), a padded mist tiled both ways.
    shader(layers[2], 'WAVE', 0.3, 0.8, -1.2)
    layers.insert(4, mist_layer(0.7, 10, 30, 0.035, 0.7, 3.5, -2, padX=3.0, padY=2.0))


def s03(layers):
    # Repeat Y, mirrored copies: a mirrored layer ripples (its copy too), a flipped mirrored one fogs.
    shader(layers[0], 'WAVE', 0.2, 0.6, 1.0)
    shader(layers[4], 'FOG', 0.6, 2.5, 0.9)
    layers.insert(6, mist_layer(0.5, 20, 0, 0.03, 0.9, 4, 1.2, mirror=True))


def s04(layers):
    # No repeat: a flipped layer ripples wide (its rows clamped to the region), another runs down, hills thin through FOG,
    # a mist's FOG past 1 thins at most fully, and a WAVE with no wavelength draws its image unchanged.
    shader(layers[3], 'WAVE', 0.6, 2.0, 1.5)
    shader(layers[5], 'WAVE', 0.5, 0.0, 1.0)
    shader(layers[6], 'WAVE', 0.4, 1.5, -1.0)
    shader(layers[4], 'FOG', 0.8, 3.0, 0.6)
    layers.insert(6, mist_layer(1.2, 5, 30, 0.025, 1.7, 6, 2.5))


for source, out, change in [('c01', 's01', s01), ('c02', 's02', s02), ('c03', 's03', s03), ('c05', 's04', s04)]:
    page(source, out, change)

# s06 (r229): the mist alone, half the world wide, its noise period (8 wavelengths of 5) twice the image, over near-black
# gradients: before r229 it cut at every tile edge; now it runs on across them. tools/r229-fog-seam's w5.
dark = {'r': 0.05, 'g': 0.07, 'b': 0.12, 'a': 1.0}
json.dump({'topHalf_top': dark, 'topHalf_bottom': dark, 'topHalfSize': 0.5,
           'bottomHalf_top': dark, 'bottomHalf_bottom': dark, 'bottomHalfSize': 0.5,
           'repeatOnX': True, 'repeatOnY': False,
           'pageModel': {'atlasName': 'HiverFog.atlas', 'outside': False,
                         'pageList': [mist_layer(0.5, 0, 20, 0.03, 1.0, 5, 1.5)]},
           'useOriginalSize': True}, open(f'{ROUND}/s06.jplax', 'w'), indent=1)

# s07 (r262): an even white slab (alpha 230 on every row: the mist fades to 0 at its top and bottom, which hides a cut on
# Y) through FOG, half the world wide and 5 tall, tiling on Y only, over near-black gradients; the round scrolls it up
# (speedY) so it wraps on Y. Its noise period up (10 wavelengths of 5) is ten times the image: before r262 it cut at
# every tile edge on Y. tools/r262-fog-seam-y's y5.
Image.new('RGBA', (W, H), (255, 255, 255, 230)).save(f'{HERE}/slab.png')
open(f'{HERE}/slab.atlas', 'w').write(f'''
slab.png
size: {W}, {H}
format: RGBA8888
filter: Linear,Linear
repeat: none
slab
  rotate: false
  xy: 0, 0
  size: {W}, {H}
  orig: {W}, {H}
  offset: 0, 0
  index: -1
''')
slab = mist_layer(0.5, 25, 0, 0.03, 1.0, 5, 1.5, regionName='slab', name='slab')
json.dump({'topHalf_top': dark, 'topHalf_bottom': dark, 'topHalfSize': 0.5,
           'bottomHalf_top': dark, 'bottomHalf_bottom': dark, 'bottomHalfSize': 0.5,
           'repeatOnX': False, 'repeatOnY': True,
           'pageModel': {'atlasName': 'slab.atlas', 'outside': False, 'pageList': [slab]},
           'useOriginalSize': True}, open(f'{ROUND}/s07.jplax', 'w'), indent=1)
