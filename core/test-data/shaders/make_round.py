#!/usr/bin/env python3
"""Writes the shaders round (r180, docs/effect-layers.md phase 3): core/test-data/shaders/mist.png and HiverFog.atlas
(Hiver's regions, then a white mist band on a page of its own), and engines/rounds/shaders/s01-s04.jplax, each a
conformance page (c01, c02, c03, c05) with a layer turned into a WAVE and a mist band drawn through FOG, s06.jplax,
the mist alone tiling on X (r229), s07.jplax, an even slab (slab.png, slab.atlas) through FOG tiling on Y (r262),
s08.jplax, vertical stripes (stripes.png, stripes.atlas) through WAVE tiling on Y (r273), and s09.jplax and s10.jplax, a
checker (checker.png, checker.atlas) through SWELL tiling on X, then flipped on X and not repeating (r431), and
s11.jplax, the checker through SWELL tiling on X and Y, small and scrolled up (r434).
Run from the repository root: python3 core/test-data/shaders/make_round.py (needs Pillow)."""
import json
import math
from pathlib import Path

from PIL import Image

HERE = 'core/test-data/shaders'
ROUND = 'engines/rounds/shaders'

# A white band, opaque in its middle rows and clear at its top and bottom: a mist FOG thins into patches.
W, H = 512, 128
mist = Image.new('RGBA', (W, H))
mist.putdata([(255, 255, 255, round(230 * math.sin(math.pi * (y + 0.5) / H) ** 2)) for y in range(H) for x in range(W)])
mist.save(f'{HERE}/mist.png')

hiver = Path('core/test-data/samples/Hiver.atlas').read_text().strip().split('\n')
hiver[0] = '../samples/Hiver.png'
Path(f'{HERE}/HiverFog.atlas').write_text('\n'.join(hiver) + f'''

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
    p = json.loads(Path(f'engines/rounds/conformance/{source}.jplax').read_text())
    p['pageModel']['atlasName'] = 'HiverFog.atlas'
    change(p['pageModel']['pageList'])
    with open(f'{ROUND}/{out}.jplax', 'w') as f:
        json.dump(p, f, indent=1)


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
with open(f'{ROUND}/s06.jplax', 'w') as f:
    json.dump({'topHalf_top': dark, 'topHalf_bottom': dark, 'topHalfSize': 0.5,
               'bottomHalf_top': dark, 'bottomHalf_bottom': dark, 'bottomHalfSize': 0.5,
               'repeatOnX': True, 'repeatOnY': False,
               'pageModel': {'atlasName': 'HiverFog.atlas', 'outside': False,
                             'pageList': [mist_layer(0.5, 0, 20, 0.03, 1.0, 5, 1.5)]},
               'useOriginalSize': True}, f, indent=1)

# s07 (r262): an even white slab (alpha 230 on every row: the mist fades to 0 at its top and bottom, which hides a cut on
# Y) through FOG, half the world wide and 5 tall, tiling on Y only, over near-black gradients; the round scrolls it up
# (speedY) so it wraps on Y. Its noise period up (10 wavelengths of 5) is ten times the image: before r262 it cut at
# every tile edge on Y. tools/r262-fog-seam-y's y5.
Image.new('RGBA', (W, H), (255, 255, 255, 230)).save(f'{HERE}/slab.png')
Path(f'{HERE}/slab.atlas').write_text(f'''
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
with open(f'{ROUND}/s07.jplax', 'w') as f:
    json.dump({'topHalf_top': dark, 'topHalf_bottom': dark, 'topHalfSize': 0.5,
               'bottomHalf_top': dark, 'bottomHalf_bottom': dark, 'bottomHalfSize': 0.5,
               'repeatOnX': False, 'repeatOnY': True,
               'pageModel': {'atlasName': 'slab.atlas', 'outside': False, 'pageList': [slab]},
               'useOriginalSize': True}, f, indent=1)

# s08 (r273): black and white vertical stripes 32 px apart (every row the same: seamless on Y) through WAVE, amplitude
# 0.3, half the world wide and 5 tall, tiling on Y only; the round scrolls it up (speedY) so it wraps on Y. Its
# wavelength, 1.5, does not divide the image's 5 units: before r273 the ripple (inside one tile, local().y) jumped
# sideways at every tile edge on Y. tools/r273-wave-seam-y's w15.
SW, SH = 512, 128
stripes = Image.new('RGBA', (SW, SH))
stripes.putdata([(255, 255, 255, 255) if (x // 32) % 2 == 0 else (0, 0, 0, 255) for y in range(SH) for x in range(SW)])
stripes.save(f'{HERE}/stripes.png')
Path(f'{HERE}/stripes.atlas').write_text(f'''
stripes.png
size: {SW}, {SH}
format: RGBA8888
filter: Linear,Linear
repeat: none
stripes
  rotate: false
  xy: 0, 0
  size: {SW}, {SH}
  orig: {SW}, {SH}
  offset: 0, 0
  index: -1
''')
wave = mist_layer(0.5, 25, 0, 0.03, 0.3, 1.5, 0.0, regionName='stripes', name='stripes')
wave['shaderEffect'] = 'WAVE'
with open(f'{ROUND}/s08.jplax', 'w') as f:
    json.dump({'topHalf_top': dark, 'topHalf_bottom': dark, 'topHalfSize': 0.5,
               'bottomHalf_top': dark, 'bottomHalf_bottom': dark, 'bottomHalfSize': 0.5,
               'repeatOnX': False, 'repeatOnY': True,
               'pageModel': {'atlasName': 'stripes.atlas', 'outside': False, 'pageList': [wave]},
               'useOriginalSize': True}, f, indent=1)

# s09, s10 (r431): a black and white checker, 32 px squares (seamless both ways), through SWELL at the animation lab's
# wavelength and speed (1.25, 0.3) and a crisp amplitude (0.3): the beach sea it was made for is too soft to gate a round
# (plain, 1.35 / 255 off its swell; the glint round's g03 draws it). s09 tiles on X, 18 units wide (no whole number of
# SWELL's 10-unit period: at 20, waves laid on each tile drew the same), and the round's scroll wraps it there: SWELL's
# waves are whole turns over 8 wavelengths across, so they run on across every tile edge and the wrap. s10 repeats on neither axis,
# the whole world wide and flipped on X: the waves run leftward from its right edge, its sides held, not wrapped (r434).
checker = Image.new('RGBA', (SW, SH))
checker.putdata([(255, 255, 255, 255) if (x // 32 + y // 32) % 2 == 0 else (0, 0, 0, 255) for y in range(SH) for x in range(SW)])
checker.save(f'{HERE}/checker.png')
Path(f'{HERE}/checker.atlas').write_text(f'''
checker.png
size: {SW}, {SH}
format: RGBA8888
filter: Linear,Linear
repeat: none
checker
  rotate: false
  xy: 0, 0
  size: {SW}, {SH}
  orig: {SW}, {SH}
  offset: 0, 0
  index: -1
''')
for out, repeat_x, flip_x, size in [('s09', True, False, 0.45), ('s10', False, True, 1.0)]:
    swell = mist_layer(size, 0, 40, 0.03, 0.3, 1.25, 0.3, regionName='checker', name='checker', flipX=flip_x)
    swell['shaderEffect'] = 'SWELL'
    with open(f'{ROUND}/{out}.jplax', 'w') as f:
        json.dump({'topHalf_top': dark, 'topHalf_bottom': dark, 'topHalfSize': 0.5,
                   'bottomHalf_top': dark, 'bottomHalf_bottom': dark, 'bottomHalfSize': 0.5,
                   'repeatOnX': repeat_x, 'repeatOnY': False,
                   'pageModel': {'atlasName': 'checker.atlas', 'outside': False, 'pageList': [swell]},
                   'useOriginalSize': True}, f, indent=1)

# s11 (r434): the checker through SWELL tiling on X and Y, a quarter of the world wide (10 by 2.5 units: nine tile edges
# up the view) and scrolled up (speedY), so it wraps on Y. SWELL wraps the image on the axes its page repeats on: before
# r434 it held the image's top and bottom rows, a flat band at every tile edge on Y. tools/r434-swell-wrap's mutate.sh.
swell = mist_layer(0.25, 0, 0, 0.03, 0.3, 1.25, 0.3, regionName='checker', name='checker')
swell['shaderEffect'] = 'SWELL'
with open(f'{ROUND}/s11.jplax', 'w') as f:
    json.dump({'topHalf_top': dark, 'topHalf_bottom': dark, 'topHalfSize': 0.5,
               'bottomHalf_top': dark, 'bottomHalf_bottom': dark, 'bottomHalfSize': 0.5,
               'repeatOnX': True, 'repeatOnY': True,
               'pageModel': {'atlasName': 'checker.atlas', 'outside': False, 'pageList': [swell]},
               'useOriginalSize': True}, f, indent=1)

# s12, s13 (r455): r407's beach sea (core/test-data/anim) alone through MIRROR at Simon's r419 numbers (amplitude 0.12,
# wavelength 1.25, speed 0.35, glint 0.5), its water's horizon at the strip's 84 of 384 rows and 3 world units deep,
# mirroring the beach page's sky (its top gradient: the pale horizon, the blue over the view). The round zooms in on the
# water as the glint round does. s12 repeats on X as the beach does; s13 is flipped on Y, not repeating: the horizon
# is in the image, not the view, so the mirror turns with it; its water, now the strip's top 84 rows, is moved down
# into the view (the strip is the world's width, 15 of 22.5 units tall: decal -49.4% puts the water's bottom where
# s12's is). tools/r455-mirror/strength.sh holds them against SWELL.
for out, flip_y, repeat_x, decal_y in [('s12', False, True, 2.7), ('s13', True, False, -49.4)]:
    beach = json.loads(Path('core/test-data/anim/beach.jplax').read_text())
    sea = next(layer for layer in beach['pageModel']['pageList'] if layer['regionName'] == 'l1')
    sea.update(kind='SHADER', shaderEffect='MIRROR', shaderAmplitude=0.12, shaderWavelength=1.25, shaderSpeed=0.35,
               glintStrength=0.5, mirrorHorizon=84 / 384, mirrorDepth=3.0, decal_Y_Ratio=decal_y, flipY=flip_y)
    beach['pageModel']['pageList'] = [sea]
    beach['repeatOnX'] = repeat_x
    with open(f'{ROUND}/{out}.jplax', 'w') as f:
        json.dump(beach, f, indent=1)

# s14 (r455): the same sea through MIRROR under a sky no page would paint (its top gradient violet at the horizon, green
# over the view), a stronger swell (0.3) and shallower water (1.5 units), no glint: the mirror's hue and its fine ripples
# show. On s12's pale blue sky a reader that left the horizon's sky white, bent the water in colour instead of grey, or
# left out the three fine ripples drew within the gate (tools/r455-mirror/mutate.sh). A facet bends the water by the
# ratio of the two colours, so at a sunset's orange over navy (5 to 1) every bend clamped to white whatever the sky;
# these two were picked by tools/r455-mirror/model.py, a numpy copy of the mirror on the strip, for the largest least
# of those three mutants (3.5 / 255 on the water there).
beach = json.loads(Path(f'{ROUND}/s12.jplax').read_text())
beach['topHalf_bottom'] = {'r': 0.5, 'g': 0.1, 'b': 0.9, 'a': 1.0}
beach['topHalf_top'] = {'r': 0.2, 'g': 0.9, 'b': 0.2, 'a': 1.0}
beach['pageModel']['pageList'][0].update(shaderAmplitude=0.3, mirrorDepth=1.5, glintStrength=0.0)
with open(f'{ROUND}/s14.jplax', 'w') as f:
    json.dump(beach, f, indent=1)
