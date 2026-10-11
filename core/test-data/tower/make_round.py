#!/usr/bin/env python3
"""Writes the tower round (r276, docs/depth-tricks.md §2): core/test-data/tower/bricks.png, a brick wall with windows,
seamless both ways (r266's mock tower, made taller); HiverTower.atlas, Hiver's regions then the bricks on a page of
their own; and engines/rounds/tower/round.json and its pages: the floor lab's Hiver set (its EMPTY floor dropped)
with a tower in front, its wall six bricks images round, turning a full turn in the 12 s the shots scroll.
Read by shots/.../ParallaxShots, engines/godot/tests/shots.gd and engines/jme's JmeParallaxShots;
tools/godot-parallax-shots.sh and tools/jme-parallax-shots.sh compare them.
Run from the repository root: python3 core/test-data/tower/make_round.py (needs numpy and Pillow)."""
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image

HERE = 'core/test-data/tower'
OUT = 'engines/rounds/tower'
W, H = 256, 1024
# The shots scroll 60 units of view a second for 12 s (shots.gd): a speed ratio of 2 pi R / 720 turns R a full turn.
SECONDS, SPEED = 12, 60


def bricks():
    """Staggered bricks 32 x 16 with a dark window every 128 x 128, every term repeating over the tile."""
    y, x = np.mgrid[0:H, 0:W]
    row = y // 16
    xs = (x + (row % 2) * 16) % 32
    mortar = (y % 16 < 2) | (xs < 2)
    # The brick straddling the tile's left and right edges is one brick: its index wraps with the tile (8 a row).
    tone = 0.85 + 0.15 * np.sin(row % 64 * 12.9898 + (x + (row % 2) * 16) // 32 % (W // 32) * 78.233) ** 2
    rgb = np.stack([0.62 * tone, 0.45 * tone, 0.36 * tone], axis=-1)
    rgb[mortar] = (0.35, 0.3, 0.28)
    window = ((x % 128) > 52) & ((x % 128) < 76) & ((y % 128) > 40) & ((y % 128) < 88)
    rgb[window] = (0.12, 0.12, 0.2)
    Image.fromarray((np.clip(rgb, 0, 1) * 255).astype(np.uint8)).convert('RGBA').save(f'{HERE}/bricks.png')


def atlas():
    """Hiver's page, as the floor lab's atlas names it, then the bricks."""
    hiver = Path('core/test-data/floor/HiverFloor.atlas').read_text().split('\n\n')[0]
    Path(f'{HERE}/HiverTower.atlas').write_text(hiver + f"""

bricks.png
size: {W}, {H}
format: RGBA8888
filter: Linear,Linear
repeat: none
bricks
  rotate: false
  xy: 0, 0
  size: {W}, {H}
  orig: {W}, {H}
  offset: 0, 0
  index: -1
""")


def tower(radius, around, shade, turns, **more):
    """A tower layer of the bricks, `radius` percent of the world, the image `around` times round, turning `turns` turns
    in 12 s; `more` sets other fields (another region, a flip, a pad)."""
    r = radius / 100 * 40
    layer = {'regionName': 'bricks', 'regionPosition': 0, 'flipX': False, 'flipY': False, 'speedXAtRest': 0.0,
             'sizeRatio': 2 * math.pi * r / around / 40, 'decal_X_Ratio': 0, 'decal_Y_Ratio': 0, 'padX': 0.0,
             'padXFactor': 0.0, 'padY': 0.0, 'padYFactor': 0.0, 'mirror': False, 'towerRadius': radius,
             'towerShade': shade}
    layer.update(more)
    speed = turns * 2 * math.pi * r / (SPEED * SECONDS)
    layer['parallaxScalingSpeedX'] = layer['parallaxScalingSpeedY'] = speed
    return layer


def page(front, **more):
    """The floor lab's Hiver set without its floor, `front` in front of it."""
    p = json.loads(Path('core/test-data/floor/lab.jplax').read_text())
    p['pageModel']['atlasName'] = 'HiverTower.atlas'
    p['pageModel']['pageList'] = [l for l in p['pageModel']['pageList'] if l.get('kind') != 'EMPTY'] + [front]
    p.update(more)
    return p


def write(name, p):
    with open(f'{OUT}/{name}.jplax', 'w') as f:
        json.dump(p, f, indent=1)
    return f'{OUT}/{name}.jplax'


bricks()
atlas()
Path(OUT).mkdir(parents=True, exist_ok=True)
w01 = write('w01', page(tower(20, 6, 0.65, 1)))
w02 = write('w02', page(tower(20, 6, 0.8, -0.5, flipX=True, padX=1.0), fogStrength=0.02,
                        fogColor={'r': 0.88, 'g': 0.9, 'b': 0.93, 'a': 1}))
# Hiver's far peaks, an image the atlas trimmed top and bottom: a round ridge, flipped upside down, tiled up.
peaks = tower(30, 1, 1.0, 0.25, regionName='parallax4', regionPosition=2, flipY=True, padY=1.0, decal_Y_Ratio=10)
peaks['sizeRatio'] = 1.4
w03 = write('w03', page(peaks, repeatOnY=True))
scenes = [
    {'id': 'w01', 'page': w01, 'atlasDir': HERE, 'name': 'tower-full-turn',
     'about': "Hiver's set behind a brick tower 20% of the world in radius (16 wide, in the view's middle), its wall six "
              "images round, shaded 0.65: a full turn in 12 s, half a turn at 6 s"},
    {'id': 'w02', 'page': w02, 'atlasDir': HERE, 'name': 'tower-fogged-flipped-padded',
     'about': "the tower flipped on X, a pad of 1 between its images (the set shows through the gaps), shaded 0.8, half a "
              "turn backward, under a page fog of 0.02: libGDX's page fog shader takes the darkness in the batch "
              "colour's blue"},
    {'id': 'w03', 'page': w03, 'atlasDir': HERE, 'name': 'tower-trimmed-trees-repeat-y',
     'about': "Hiver's far peaks (a region the atlas trimmed top and bottom) wrapped round a radius of 30% (24 wide), "
              "shaded 1 (black at its sides), flipped on Y, tiled up a page repeating on Y with a pad of 1, a quarter turn"},
    {'id': 'w04', 'page': w01, 'atlasDir': HERE, 'name': 'tower-dolly-cross-fade',
     'about': "w01 dollied in from zoom 1 to 0.7 (the tower at its own zoom, about the view's middle), cross-fading into "
              "w02 4 s in, over 4 s",
     'zoom': [1, 0.7], 'dolly': True, 'transfer': {'at': 4, 'seconds': 4, 'page': w02}},
]
with open(f'{OUT}/round.json', 'w') as f:
    json.dump({'round': 'tower',
               'about': "Tower layers (r276, docs/depth-tricks.md §2): an IMAGE layer wrapped round a cylinder in the view's "
                        "middle, its scroll turning it, darker toward its sides. Godot draws its own strips "
                        "(plax_tower.gd's draw); jME runs core's ParallaxLayer.drawTower. Written by "
                        "core/test-data/tower/make_round.py. Read by shots/.../ParallaxShots, engines/godot/tests/shots.gd "
                        "and engines/jme's JmeParallaxShots.",
               'scenes': scenes}, f, indent=1)
print('wrote', OUT)
