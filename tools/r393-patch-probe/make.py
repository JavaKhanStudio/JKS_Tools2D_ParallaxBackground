"""r393: a probe round for the animated patch (format 16), drawn by the libGDX reference renderer.

    python3 tools/r393-patch-probe/make.py build/r393/round
    tools/parallax-lab-shots.sh build/r393/round build/r393/shots

Writes an atlas (a hill, 800x400, and four frames of a flag, 120x120, each numbered), a page whose two layers carry the
patch (the second flipped on X: its patch is mirrored with it) and a round.json. At 0.2083 frames a second the stills
at 0, 6 and 12 s show frames 0, 1 and 2, a quarter of a frame away from a change. Not a reader round (phase 2's
engines/rounds/animated is): a look at what phase 1 draws.
"""
import json
import os
import sys

from PIL import Image, ImageDraw

out = sys.argv[1]
os.makedirs(out, exist_ok=True)

W, H, F = 800, 400, 120
sheet = Image.new('RGBA', (1024, 512), (0, 0, 0, 0))
draw = ImageDraw.Draw(sheet)
# The hill, the layer's still: a dark green mound with a pole where the flag flies.
draw.polygon([(0, 400), (0, 300), (200, 220), (400, 200), (600, 230), (800, 300), (800, 400)], fill=(40, 90, 50, 255))
draw.rectangle([395, 80, 401, 200], fill=(60, 40, 30, 255))
frames = []
for i in range(4):
    x0 = i * (F + 2)
    y0 = H + 2  # the frames' row, under the hill on the sheet
    if x0 + F > 1024:
        raise SystemExit('sheet too narrow')
    frames.append((x0, y0))
for i, (x0, y0) in enumerate(frames):
    d = ImageDraw.Draw(sheet)
    # A flag waving: its tip up or down by frame, its number on it.
    tip = [10, 40, 70, 40][i]
    d.polygon([(x0 + 2, y0 + 10), (x0 + 110, y0 + tip), (x0 + 2, y0 + 70)], fill=[(230, 60, 40, 255), (240, 200, 40, 255),
                                                                              (60, 140, 230, 255), (240, 240, 240, 255)][i])
    d.text((x0 + 20, y0 + 30), str(i), fill=(0, 0, 0, 255))
sheet.save(os.path.join(out, 'probe.png'))

lines = ['probe.png', 'size: 1024, 512', 'format: RGBA8888', 'filter: Linear,Linear', 'repeat: none',
         'hill', '  rotate: false', '  xy: 0, 0', f'  size: {W}, {H}', f'  orig: {W}, {H}', '  offset: 0, 0', '  index: -1']
for i, (x0, y0) in enumerate(frames):
    lines += ['flag', '  rotate: false', f'  xy: {x0}, {y0}', f'  size: {F}, {F - 40}', f'  orig: {F}, {F - 40}',
              '  offset: 0, 0', f'  index: {i}']
with open(os.path.join(out, 'probe.atlas'), 'w') as atlas:
    atlas.write('\n'.join(lines) + '\n')


def layer(flip, decal_y, decal_x):
    # The flag's bottom-left at the pole's top in the hill's pixels from its bottom-left: x 401, y 400 - 80 - 80.
    return {'regionName': 'hill', 'regionPosition': 0, 'flipX': flip, 'sizeRatio': 0.5, 'decal_X_Ratio': decal_x,
            'decal_Y_Ratio': decal_y, 'parallaxScalingSpeedX': 0.02, 'padX': 2.0, 'kind': 'IMAGE',
            'animRegion': 'flag', 'animX': 401, 'animY': 240, 'animFps': 0.2083333}


page = {'repeatOnX': True, 'repeatOnY': False, 'useOriginalSize': True,
        'topHalf_top': {'r': 0.55, 'g': 0.75, 'b': 0.95, 'a': 1}, 'topHalf_bottom': {'r': 0.8, 'g': 0.9, 'b': 1, 'a': 1},
        'bottomHalf_top': {'r': 0.8, 'g': 0.9, 'b': 1, 'a': 1}, 'bottomHalf_bottom': {'r': 0.8, 'g': 0.9, 'b': 1, 'a': 1},
        'pageModel': {'atlasName': 'probe.atlas', 'pageList': [layer(False, 40, 0), layer(True, 0, 25)]}}
rel = os.path.relpath(out)
with open(os.path.join(out, 'p01.jplax'), 'w') as f:
    json.dump(page, f, indent=1)
scenes = {'round': 'r393-patch-probe', 'about': 'animated patch, phase 1: two hills with a flag patch, the front one '
           'flipped on X', 'scenes': [{'id': 'p01', 'page': f'{rel}/p01.jplax', 'atlasDir': rel, 'name': 'patch',
                                       'about': 'frames 0, 1, 2 at 0, 6, 12 s'}]}
with open(os.path.join(out, 'round.json'), 'w') as f:
    json.dump(scenes, f, indent=1)
