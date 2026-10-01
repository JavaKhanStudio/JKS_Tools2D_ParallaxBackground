#!/usr/bin/env python3
"""Writes the sequence round (r183, docs/sequence-layers.md phase 2): core/test-data/sequence/segments.png and
HiverSeq.atlas (Hiver's regions, then five segments on a page of their own), and engines/godot/tests/sequence/q01-q04.jplax,
each a conformance page (c01, c02, c03, c05) with SEQUENCE layers added. The segments are loud on purpose: each its own
colour and width, with marks that are not symmetric, so a wrong pick, a slot out of place or a flip missed shows as a
big difference, not a 1/255 one. Run from the repository root: python3 core/test-data/sequence/make_round.py (needs
Pillow)."""
import json
from PIL import Image, ImageDraw

HERE = 'core/test-data/sequence'
ROUND = 'engines/godot/tests/sequence'
H = 256
# name: (packed width, colour, (original width, offset x) when packed with its whitespace stripped)
SEGMENTS = {
    'stone': (512, (120, 120, 135), None),
    'bridge': (896, (150, 85, 30), None),
    'river': (320, (40, 110, 220), None),
    'tower': (192, (210, 40, 50), None),
    'bush': (400, (40, 170, 60), (600, 120)),
}


def segment(width, colour, letter_index):
    img = Image.new('RGBA', (width, H))
    d = ImageDraw.Draw(img)
    dark = tuple(c // 2 for c in colour) + (255,)
    # An opaque band at the bottom, a silhouette rising to the right above it: neither flip leaves it the same.
    d.rectangle([0, H * 0.6, width, H], fill=colour + (255,))
    d.polygon([(0, H * 0.6), (width * 0.3, H * 0.35), (width * 0.75, H * 0.15), (width, H * 0.1), (width, H * 0.6)],
              fill=dark)
    d.rectangle([width * 0.08, H * 0.25, width * 0.08 + 14, H], fill=(255, 255, 255, 255))
    d.rectangle([width * 0.85, H * 0.7, width * 0.95, H * 0.9], fill=(20, 20, 20, 255))
    for i in range(letter_index + 1):  # as many dots as the segment's place: stone 1 ... bush 5
        d.ellipse([20 + i * 26, H - 40, 40 + i * 26, H - 20], fill=(255, 240, 0, 255))
    return img


sheet_w = sum(w for w, _, _ in SEGMENTS.values()) + 2 * len(SEGMENTS)
sheet = Image.new('RGBA', (sheet_w, H))
atlas = ['', 'segments.png', f'size: {sheet_w}, {H}', 'format: RGBA8888', 'filter: Linear,Linear', 'repeat: none']
x = 0
for i, (name, (w, colour, stripped)) in enumerate(SEGMENTS.items()):
    sheet.paste(segment(w, colour, i), (x, 0))
    orig, offset = (stripped[0], stripped[1]) if stripped else (w, 0)
    atlas += [name, '  rotate: false', f'  xy: {x}, 0', f'  size: {w}, {H}', f'  orig: {orig}, {H}',
              f'  offset: {offset}, 0', '  index: -1']
    x += w + 2
sheet.save(f'{HERE}/segments.png')
hiver = open('core/test-data/samples/Hiver.atlas').read().strip().split('\n')
hiver[0] = '../samples/Hiver.png'
open(f'{HERE}/HiverSeq.atlas', 'w').write('\n'.join(hiver) + '\n' + '\n'.join(atlas) + '\n')


def sequence(name, segments, seed, length, size, decal_x, decal_y, ratio, **more):
    layer = {'regionName': '', 'regionPosition': 0, 'flipX': False, 'flipY': False,
             'parallaxScalingSpeedX': ratio, 'parallaxScalingSpeedY': ratio, 'speedXAtRest': 0.0,
             'sizeRatio': size, 'decal_X_Ratio': decal_x, 'decal_Y_Ratio': decal_y,
             'padX': 0.0, 'padXFactor': 0.0, 'padY': 0.0, 'padYFactor': 0.0, 'mirror': False, 'name': name,
             'kind': 'SEQUENCE', 'sequenceSeed': seed, 'sequenceLength': length,
             'sequenceSegments': [{'regionName': s, 'regionPosition': 0, 'weight': w} for s, w in segments]}
    layer.update(more)
    return layer


def page(source, out, change):
    p = json.load(open(f'engines/godot/tests/conformance/{source}.jplax'))
    p['pageModel']['atlasName'] = 'HiverSeq.atlas'
    change(p['pageModel']['pageList'])
    json.dump(p, open(f'{ROUND}/{out}.jplax', 'w'), indent=1)


def q01(layers):
    # Repeat X, useOriginalSize: a mid strip of three, padded, seeded negative, between the hills; a front ground of all
    # five (the bush packed stripped, kept at its original size), weighted, 16 slots.
    layers.insert(4, sequence('strip', [('tower', 1), ('river', 2), ('stone', 1)], -1640531527, 12, 0.2, 10, 30,
                              0.03, padX=1.5))
    layers.append(sequence('ground', [('stone', 5), ('bridge', 2), ('river', 3), ('tower', 1), ('bush', 2)], 42, 16,
                           0.35, 0, 0, 0.05))


def q02(layers):
    # Repeat X and Y, padded, scrolling up: a 4-slot cycle repeats in view, its tiles padded both ways; a weight of 0
    # (the bridge) never shows.
    layers.insert(5, sequence('tiles', [('river', 1), ('bridge', 0), ('tower', 2), ('stone', 1)], 7, 4, 0.22, 0, 10,
                              0.04, padX=2.0, padY=3.0))


def q03(layers):
    # Repeat Y, mirrored copies to the right: a flipped 3-slot cycle and its mirrored copy, each segment flipped in its
    # own slot.
    layers.insert(6, sequence('column', [('stone', 1), ('tower', 1), ('river', 1)], 99, 3, 0.2, 5, 0, 0.04,
                              flipX=True, mirror=True, padX=1.0))


def q04(layers):
    # No repeat: a cycle flipped upside down drawn once, from seed 0 (which starts from ZERO_SEED); a pad of -16 overlaps
    # the slots past the narrowest segment (the tower, 5.25 wide where the stone, the first, is 14), so the slots' edges
    # go back after a tower and the reader walks every slot rather than searching. Seed 20 puts towers where a search
    # would miss 5 slots at t6 and 1 at t12, in a cycle still 43 wide (tools/r183-sequence/overlap.py: one no wider
    # than 0 is not drawn at all).
    layers.insert(3, sequence('upside', [('stone', 2), ('bridge', 1), ('river', 1)], 0, 8, 0.3, -10, 55, 0.03,
                              flipY=True))
    layers.append(sequence('overlap', [('stone', 1), ('tower', 3), ('bridge', 2)], 20, 10, 0.35, 0, 0, 0.05,
                           padX=-16.0))


for source, out, change in [('c01', 'q01', q01), ('c02', 'q02', q02), ('c03', 'q03', q03), ('c05', 'q04', q04)]:
    page(source, out, change)
