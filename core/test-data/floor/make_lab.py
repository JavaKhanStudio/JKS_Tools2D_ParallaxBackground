#!/usr/bin/env python3
"""Writes the floor lab's page (r275, Simon: "gimme a lab so I can test this ... I need to feel it";
core/browser-test/.../FloorLab, tools/effects-lab.sh --lab floor): core/test-data/floor/ground.png, a snowy ground with
stones seen from above, seamless both ways; painted.png, the same stones painted in perspective, seamless on X;
HiverFloor.atlas, Hiver's regions then the two grounds on pages of their own; and
lab.jplax, Hiver's four regions standing on a ground: the sky band and the far peaks, the mountain, then an EMPTY layer
named "floor" that the lab's hook draws the ground in (its box is the ground's: the bottom 40% of the world), then the
trees in front. Its second set (Simon: "let's try it again with another parallax"): ground-purple.png, painted-purple.png,
PurpleFloor.atlas and lab-purple.jplax, round1's PurpleFairy page standing on a violet ground, its pink trees and flowers
in front.
Run from the repository root: python3 core/test-data/floor/make_lab.py (needs numpy and Pillow)."""
import json
import math

import numpy as np
from PIL import Image

HERE = 'core/test-data/floor'

# The ground: snow with blue-grey stones (PurpleFairy's: violet moss with pale stones), one in each 64 px cell, jittered
# by its cell; every term repeats over the 512 px tile, so it is seamless on X and Y (r266's mock ground, made to tile).
N, CELL = 512, 64


def ground(base, stone_colour, out):
    z, x = np.mgrid[0:N, 0:N]
    rgb = np.ones((N, N, 3)) * base
    rgb *= (0.95 + 0.05 * np.sin(2 * math.pi * 5 * x / N) * np.sin(2 * math.pi * 3 * z / N))[..., None]
    cx, cz = x // CELL, z // CELL
    jx = np.sin(cx * 3.1 + cz * 7.7) * 14
    jz = np.cos(cx * 5.3 + cz * 2.9) * 14
    r2 = 140 + 60 * np.sin(cx * 1.7 + cz * 4.1)
    stone = ((x % CELL - CELL / 2 - jx) ** 2 + 2.0 * (z % CELL - CELL / 2 - jz) ** 2) < r2
    rgb[stone] = stone_colour
    Image.fromarray((np.clip(rgb, 0, 1) * 255).astype(np.uint8)).convert('RGBA').save(out)


# The same stones painted as seen (r275's line scroll art, as fighting games' floors are drawn): 4096 x 256, its rows
# the ground at the depths the lab's default rows put them (Hiver: bottom row's speed 0.06, the top's 0.016; depth as
# 1 / speed, a step up the bottom rows crossing the ground 0.6 times as fast as a step across), each row a whole number
# of stone tiles across so it stays seamless on X.
PW, PH, TILT, ACROSS = 4096, 256, 0.6, 8


def painted(ground_png, BOTTOM, TOP, out):
    stones = np.asarray(Image.open(ground_png).convert('RGB'), dtype=np.float64)
    image = np.zeros((PH, PW, 3))
    near = BOTTOM / (TILT * (BOTTOM - TOP))  # in box heights; a stone tile is ACROSS-th of the image across at the bottom
    box_per_tile = PW / (PH * ACROSS)  # a stone tile is as deep as it is wide
    for row in range(PH):
        t = (PH - 1 - row + 0.5) / PH
        speed = BOTTOM + (TOP - BOTTOM) * t
        repeats = max(1, round(ACROSS * BOTTOM / speed))
        v = ((BOTTOM / speed - 1) * near / box_per_tile) % 1
        u = (np.arange(PW) + 0.5) / PW * repeats % 1
        image[row] = stones[int(v * N) % N, (u * N).astype(int) % N]
    Image.fromarray(image.astype(np.uint8)).convert('RGBA').save(out)


def grounds_atlas(ground_png, painted_png):
    """The atlas pages of the two grounds, region names "ground" and "painted", as FloorLab finds them."""
    return f'''

{ground_png}
size: {N}, {N}
format: RGBA8888
filter: Linear,Linear
repeat: none
ground
  rotate: false
  xy: 0, 0
  size: {N}, {N}
  orig: {N}, {N}
  offset: 0, 0
  index: -1

{painted_png}
size: {PW}, {PH}
format: RGBA8888
filter: Linear,Linear
repeat: none
painted
  rotate: false
  xy: 0, 0
  size: {PW}, {PH}
  orig: {PW}, {PH}
  offset: 0, 0
  index: -1
'''


ground((0.86, 0.88, 0.92), (0.5, 0.56, 0.64), f'{HERE}/ground.png')
painted(f'{HERE}/ground.png', 0.06, 0.016, f'{HERE}/painted.png')

hiver = open('core/test-data/shaders/HiverFog.atlas').read().strip().split('\n\n')[0].split('\n')
assert hiver[0] == '../samples/Hiver.png'
open(f'{HERE}/HiverFloor.atlas', 'w').write('\n'.join(hiver) + grounds_atlas('ground.png', 'painted.png'))


def layer(position, size, decal_x, decal_y, speed, **more):
    out = {'regionName': 'parallax4', 'regionPosition': position, 'flipX': False, 'flipY': False,
           'parallaxScalingSpeedX': speed, 'parallaxScalingSpeedY': speed, 'speedXAtRest': 0.0,
           'sizeRatio': size, 'decal_X_Ratio': decal_x, 'decal_Y_Ratio': decal_y,
           'padX': 0.0, 'padXFactor': 0.0, 'padY': 0.0, 'padYFactor': 0.0, 'mirror': False}
    out.update(more)
    return out


# Hiver's regions by position: 0 the trees, 1 the mountain, 2 the far peaks, 3 the sky band.
layers = [layer(3, 1.4, 0, 52, 0.006),
          layer(2, 1.18, 10, 38, 0.01),
          layer(1, 1.18, 20, 36, 0.016),
          # The ground's box: the bottom 40% of the world (sizeRatio is of the world's height too). Its speed is the
          # flat panel's: B draws the ground at the mean of the lab's top and bottom row speeds.
          {'regionName': None, 'regionPosition': 0, 'flipX': False, 'flipY': False,
           'parallaxScalingSpeedX': 0.03, 'parallaxScalingSpeedY': 0.03, 'speedXAtRest': 0.0,
           'sizeRatio': 0.4, 'decal_X_Ratio': 0.0, 'decal_Y_Ratio': 0.0,
           'padX': 0.0, 'padXFactor': 0.0, 'padY': 0.0, 'padYFactor': 0.0, 'kind': 'EMPTY', 'name': 'floor',
           'mirror': False},
          layer(0, 1.3, -10, 8, 0.04)]
sky_top = {'r': 0.55, 'g': 0.64, 'b': 0.78, 'a': 1.0}
sky_low = {'r': 0.88, 'g': 0.9, 'b': 0.93, 'a': 1.0}
json.dump({'topHalf_top': sky_top, 'topHalf_bottom': sky_low, 'topHalfSize': 0.6,
           'bottomHalf_top': sky_low, 'bottomHalf_bottom': sky_low, 'bottomHalfSize': 0.4,
           'repeatOnX': True, 'repeatOnY': False,
           'pageModel': {'atlasName': 'HiverFloor.atlas', 'outside': False, 'pageList': layers},
           'useOriginalSize': True}, open(f'{HERE}/lab.jplax', 'w'), indent=1)

# The second set (r275, Simon: "let's try it again with another parallax"): round1's PurpleFairy (s05) standing on a
# violet ground. Its back trees (region 4) meet their grass 39% up the world, which is the ground's box; their speed is
# the ground's top row's. The pink trees (3) and the flowers (1) stand on the ground in front: the lab plants them at the
# speed of the row under their foot (their trimmed bottom, offsetY in the atlas, is their foot). The band of dark trees
# (5) and the stone strip (2), grounds of their own, are left out.
ground((0.4, 0.28, 0.46), (0.66, 0.54, 0.72), f'{HERE}/ground-purple.png')
painted(f'{HERE}/ground-purple.png', 0.13, 0.0224, f'{HERE}/painted-purple.png')
fairy = open('core/test-data/samples/Demos/PurpleFairy.atlas').read().strip().split('\n')
assert fairy[0] == 'PurpleFairy.png'
fairy = ['../samples/Demos/PurpleFairy.png'] + ['filter: Linear,Linear' if l.startswith('filter: ') else l for l in fairy[1:]]
open(f'{HERE}/PurpleFloor.atlas', 'w').write('\n'.join(fairy) + grounds_atlas('ground-purple.png', 'painted-purple.png'))


def fairy_layer(name, size, decal_x, decal_y, speed):
    out = layer(0, size, decal_x, decal_y, speed)
    out['regionName'] = name
    return out


purple = [fairy_layer('7', 1.0, 60, 21, 0.0063),
          fairy_layer('6', 1.0, 50, 4.5, 0.0119),
          fairy_layer('4', 1.11, 26.5, 2.5, 0.0224),
          {'regionName': None, 'regionPosition': 0, 'flipX': False, 'flipY': False,
           'parallaxScalingSpeedX': 0.076, 'parallaxScalingSpeedY': 0.076, 'speedXAtRest': 0.0,
           'sizeRatio': 0.39, 'decal_X_Ratio': 0.0, 'decal_Y_Ratio': 0.0,
           'padX': 0.0, 'padXFactor': 0.0, 'padY': 0.0, 'padYFactor': 0.0, 'kind': 'EMPTY', 'name': 'floor',
           'mirror': False},
          # Smaller than s05's close-up, so the ground shows round them: their foot (decal + the 95 trimmed rows) 3.9
          # up, about the middle of the ground; the flowers along the bottom edge, the nearest thing on it.
          fairy_layer('3', 0.8, 20, 10.3, 0.0794),
          fairy_layer('1', 0.8, 10, 0, 0.1496)]
dusk = {'r': 0.42, 'g': 0.3, 'b': 0.52, 'a': 1.0}
dusk_low = {'r': 0.62, 'g': 0.45, 'b': 0.66, 'a': 1.0}
json.dump({'topHalf_top': dusk, 'topHalf_bottom': dusk_low, 'topHalfSize': 0.6,
           'bottomHalf_top': dusk_low, 'bottomHalf_bottom': dusk_low, 'bottomHalfSize': 0.4,
           'repeatOnX': True, 'repeatOnY': False,
           'pageModel': {'atlasName': 'PurpleFloor.atlas', 'outside': False, 'pageList': purple},
           'useOriginalSize': True}, open(f'{HERE}/lab-purple.jplax', 'w'), indent=1)
