#!/usr/bin/env python3
"""Writes the floor round (r275): engines/rounds/floor/round.json and its pages, the floor lab's two sets
(lab.jplax, lab-purple.jplax, written by make_lab.py) with the lab's EMPTY "floor" layer turned into a FLOOR layer of
the same box (its sizeRatio of the world height: floorHeight), its rows from the lab's speeds. Every layer in front is
planted on it by the reader, whatever its page speed. Read by shots/.../ParallaxShots, engines/godot/tests/shots.gd and
engines/jme's JmeParallaxShots; tools/godot-parallax-shots.sh and tools/jme-parallax-shots.sh compare them.
Run from the repository root: python3 core/test-data/floor/make_round.py"""
import json
from pathlib import Path

HERE = 'core/test-data/floor'
OUT = 'engines/rounds/floor'


def page(lab, region, mapping, size, bottom, top, **more):
    """The lab's page with its floor a FLOOR layer drawing `region` `size` worlds wide."""
    p = json.loads(Path(f'{HERE}/{lab}').read_text())
    p.update({k: v for k, v in more.items() if k in ('repeatOnX', 'repeatOnY', 'fogStrength', 'fogColor')})
    for layer in p['pageModel']['pageList']:
        if layer.get('kind') == 'EMPTY' and layer.get('name') == 'floor':
            box = layer['sizeRatio'] * 100
            layer.update({'kind': 'FLOOR', 'name': None, 'regionName': region, 'regionPosition': 0, 'sizeRatio': size,
                          'parallaxScalingSpeedX': bottom, 'parallaxScalingSpeedY': bottom, 'floorMapping': mapping,
                          'floorTopSpeed': top, 'floorRows': more.get('rows', 96), 'floorHeight': box})
    return p


def write(name, p):
    with open(f'{OUT}/{name}.jplax', 'w') as f:
        json.dump(p, f, indent=1)
    return f'{OUT}/{name}.jplax'


# Hiver's ground: stones from above, a 16-unit tile (sizeRatio 0.4), in the lab's 40%-high box; the painted ground,
# 4096 x 256 painted in perspective, as wide as the box makes it (9 * 16 = 144: sizeRatio 3.6).
g01 = write('g01', page('lab.jplax', 'ground', 'MODE7', 0.4, 0.06, 0.016))
g02 = write('g02', page('lab.jplax', 'painted', 'LINE_SCROLL', 3.6, 0.06, 0.016))
g03 = write('g03', page('lab-purple.jplax', 'ground', 'MODE7', 0.4, 0.13, 0.0224, fogStrength=0.02,
                        fogColor={'r': 0.62, 'g': 0.45, 'b': 0.66, 'a': 1}))
g04 = write('g04', page('lab.jplax', 'ground', 'MODE7', 0.4, 0.06, 0.016, repeatOnX=False, rows=24))

scenes = [
    {'id': 'g01', 'page': g01, 'atlasDir': HERE, 'name': 'floor-mode7',
     'about': "Hiver's lab set on a MODE7 floor (stones from above, 16-unit tiles, a box 40% of the world high, 96 rows "
              "from 0.06 at the bottom to 0.016 at the top); the trees in front planted on it"},
    {'id': 'g02', 'page': g02, 'atlasDir': HERE, 'name': 'floor-line-scroll',
     'about': "the same set on a LINE_SCROLL floor: the stones painted in perspective, each row shifted at its speed"},
    {'id': 'g03', 'page': g03, 'atlasDir': HERE, 'name': 'floor-purple-fogged',
     'about': "PurpleFairy's lab set on a MODE7 violet floor (0.13 to 0.0224), its pink trees and flowers planted, under "
              "a page fog of 0.02: the floor is fogged at its bottom row's speed"},
    {'id': 'g04', 'page': g04, 'atlasDir': HERE, 'name': 'floor-no-repeat-cross-fade',
     'about': "g01 in 24 rows on a page repeating on neither axis (the floor still tiles across, the other layers are "
              "drawn once), cross-fading into g02 4 s in, over 4 s: both pages' layers planted on their own floor",
     'transfer': {'at': 4, 'seconds': 4, 'page': g02}},
]
with open(f'{OUT}/round.json', 'w') as f:
    json.dump({'round': 'floor',
               'about': "FLOOR layers (r275, docs/depth-tricks.md §1): the floor lab's grounds drawn row by row, each row at "
                        "its depth, in MODE7 and LINE_SCROLL, the layers in front planted on them. Godot draws its own rows "
                        "(plax_floor.gd's measure, draw) and plants its own (plant): a row at "
                        "another "
                        "speed, stretch or strip, or a tree at its page speed, shows. Written by "
                        "core/test-data/floor/make_round.py. Read by shots/.../ParallaxShots, engines/godot/tests/shots.gd "
                        "and engines/jme's JmeParallaxShots.",
               'scenes': scenes}, f, indent=1)
print('wrote', OUT)
