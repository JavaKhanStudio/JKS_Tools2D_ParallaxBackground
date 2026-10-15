#!/usr/bin/env python3
"""Writes the animated patch round (r394, docs/animated-layers.md phase 2): core/test-data/animated/animated.png and
.atlas, and engines/rounds/animated/round.json and its pages.

a01 is the round's teeth: r393's probe, a hill with a pole flying a flag whose four frames are four colours, at
0.2083 frames a second (frames 0, 1, 2 at 0, 6, 12 s), on an IMAGE layer mirrored above itself (the copy's flag upside
down) and on one flipped on X. a05 is a01 with its front hill a WAVE SHADER layer (r397): the effect is given the
flag's frame's region for the patch, else it clamps the flag's samples to the hill's edge. a02-a04 are r365's beach (its l0-l2, and its palms l3 as one IMAGE strip) at half size, the
palm crowns carrying r375's 24 re-keyed frames (build/r375/demo/palms, 785x150 of the 1024x384 strip, at (108, 104)
from its top-left) as an animated patch; a03 dollies it, a04 wraps it round a planet. The palms move little from one
frame to the next (0.5 to 0.8 / 255 over the patch's box), under the frame check's threshold: a wrong frame fails on
a01's flags.

THE CLOCK RUNS ACROSS SCENES: the shots never reset the reader's effect clock (ParallaxPageReader.effectTime, Godot's
_effect_time), so scene k's stills are at about 12k + 0, 6 and 12 s (still_times, the shots' float steps). The flags
come first, where 0, 6 and 12 s are what they say; the palms' rate is searched so that every one of their nine stills
lies as far from a frame change as can be (PALM_FPS). Reorder the scenes, or add one before them, and run this again.
Read by shots/.../ParallaxShots, engines/godot/tests/shots.gd and engines/jme's JmeParallaxShots;
engines/godot/tools/frame-check.sh and engines/jme/tools/frame-check.sh compare them; tools/r394-animated/mutate.sh
proves a wrong Godot copy fails.
Run from the repository root: python3 core/test-data/animated/make_round.py (needs Pillow, build/r365/after and
build/r375/demo: r365's and r375's runs). Its output is committed: a clone needs neither.
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = Path('core/test-data/animated')
OUT = Path('engines/rounds/animated')
BEACH = Path('build/r365/after/beach-painted')
PALMS = Path('build/r375/demo/palms')
# r375's moving box in the 1024x384 strip, from its top-left: x 108-894, y 104-254; halved.
BOX = (108, 104, 894, 254)
STRIP_W, STRIP_H = 512, 192
SHEET_W, SHEET_H = 1024, 1536
HILL_W, HILL_H, FLAG_W, FLAG_H = 400, 200, 160, 100
FLAG_FPS = 1.25 / 6
SHOTS, STEP = (0, 6, 12), 1 / 60


def still_times(scenes):
    """Each scene's stills on the reader's effect clock: ParallaxShots.takeShots' float32 loop, never reset."""
    step, effect, out = np.float32(STEP), 0.0, []
    for _ in range(scenes):
        t, times = np.float32(0), []
        for at in SHOTS:
            while t < np.float32(at):
                effect += float(step)
                t = np.float32(t + step)
            times.append(effect)
        out.append(times)
    return out


def palm_fps(times):
    """The rate, 0.9 to 1.6 frames a second, whose stills at `times` are farthest from a frame change."""
    def margin(fps):
        return min(min(t * fps % 1, 1 - t * fps % 1) for t in times)
    return max((round(0.9 + i / 10000, 4) for i in range(7001)), key=margin), margin


PALM_FPS, _margin = palm_fps([t for scene in still_times(4)[1:] for t in scene])


def wave_flag_fps(times):
    """a05's flag rate, 0.1 to 0.4 frames a second: three frames apart at its three stills, each as far from a frame
    change as can be."""
    def margin(fps):
        if len({int(t * fps) % 4 for t in times}) < 3:
            return -1
        return min(min(t * fps % 1, 1 - t * fps % 1) for t in times)
    return max((round(0.1 + i / 10000, 4) for i in range(3001)), key=margin)


WAVE_FLAG_FPS = wave_flag_fps(still_times(5)[4])


def half(im):
    return im.resize((im.width // 2, im.height // 2), Image.LANCZOS)


def sheet():
    """The page's four strips, the 24 palm frames, the hill and the four flags; each region's place, in file order."""
    im = Image.new('RGBA', (SHEET_W, SHEET_H), (0, 0, 0, 0))
    regions = []
    beach = Image.open(BEACH / 'page/beach.png').convert('RGBA')
    strips = [beach.crop((0, y, 1024, y + 384)) for y in (0, 385, 770)]
    strips.append(Image.open(BEACH / 'strips/l3_cut.png').convert('RGBA'))
    for i, strip in enumerate(strips):
        x, y = (i % 2) * (STRIP_W + 2), (i // 2) * (STRIP_H + 2)
        im.paste(half(strip), (x, y))
        regions.append((f'l{i}', -1, x, y, STRIP_W, STRIP_H))
    fw, fh = (BOX[2] - BOX[0]) // 2, (BOX[3] - BOX[1]) // 2
    top = 2 * (STRIP_H + 2)
    for i in range(24):
        frame = half(Image.open(PALMS / f'f{i:03d}.png').convert('RGBA').crop(BOX))
        x, y = (i % 2) * (fw + 2), top + (i // 2) * (fh + 2)
        im.paste(frame, (x, y))
        regions.append(('palms', i, x, y, fw, fh))
    top += 12 * (fh + 2)
    hill = Image.new('RGBA', (HILL_W, HILL_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(hill)
    d.polygon([(0, 200), (0, 150), (100, 110), (200, 100), (300, 115), (400, 150), (400, 200)], fill=(40, 90, 50, 255))
    d.rectangle([197, 0, 201, 100], fill=(60, 40, 30, 255))
    im.paste(hill, (0, top))
    regions.append(('hill', -1, 0, top, HILL_W, HILL_H))
    colours = [(230, 60, 40, 255), (240, 200, 40, 255), (60, 140, 230, 255), (240, 240, 240, 255)]
    for i, colour in enumerate(colours):
        flag = Image.new('RGBA', (FLAG_W, FLAG_H), (0, 0, 0, 0))
        tip = [5, 35, 65, 35][i]
        ImageDraw.Draw(flag).polygon([(0, 4), (FLAG_W - 1, tip), (0, 66)], fill=colour)
        x = HILL_W + 2 + (i % 2) * (FLAG_W + 2)
        y = top + (i // 2) * (FLAG_H + 2)
        im.paste(flag, (x, y))
        regions.append(('flag', i, x, y, FLAG_W, FLAG_H))
    assert top + HILL_H <= SHEET_H
    im.save(HERE / 'animated.png', optimize=True)
    lines = ['animated.png', f'size: {SHEET_W}, {SHEET_H}', 'format: RGBA8888', 'filter: Linear,Linear', 'repeat: none']
    for name, index, x, y, w, h in regions:
        lines += [name, '  rotate: false', f'  xy: {x}, {y}', f'  size: {w}, {h}', f'  orig: {w}, {h}',
                  '  offset: 0, 0', f'  index: {index}']
    (HERE / 'animated.atlas').write_text('\n'.join(lines) + '\n')
    return BOX[0] // 2, STRIP_H - BOX[3] // 2


def gradients(top, bottom):
    return {'topHalf_top': top, 'topHalf_bottom': bottom, 'topHalfSize': 0.0,
            'bottomHalf_top': bottom, 'bottomHalf_bottom': bottom, 'bottomHalfSize': 1.0}


def beach(anim_x, anim_y, **extra):
    base = json.loads((BEACH / 'page/beach.jplax').read_text())
    layers = base['pageModel']['pageList'][:3]
    l3 = dict(layers[2], regionName='l3', parallaxScalingSpeedX=0.08, parallaxScalingSpeedY=0.048, decal_Y_Ratio=0.0,
              decal_X_Ratio=0.0, animRegion='palms', animX=anim_x, animY=anim_y, animFps=PALM_FPS)
    page = {k: v for k, v in base.items() if k != 'pageModel'}
    page.update(extra)
    page['pageModel'] = {'atlasName': 'animated.atlas', 'pageList': layers + [l3]}
    return page


def flags(wave=False):
    sky, haze = {'r': 0.55, 'g': 0.75, 'b': 0.95, 'a': 1}, {'r': 0.8, 'g': 0.9, 'b': 1, 'a': 1}

    def hill(**kw):
        # The flag's bottom-left at the pole's top in the hill's pixels from its bottom-left: x 201, y 200 - 100.
        return dict({'regionName': 'hill', 'regionPosition': 0, 'sizeRatio': 0.4, 'parallaxScalingSpeedX': 0.02,
                     'padX': 1.0, 'kind': 'IMAGE', 'animRegion': 'flag', 'animX': 201, 'animY': 100,
                     'animFps': FLAG_FPS}, **kw)

    fps = {'animFps': WAVE_FLAG_FPS} if wave else {}
    back = hill(decal_Y_Ratio=30.0, mirror=True, **fps)
    front = hill(decal_X_Ratio=30.0, flipX=True, parallaxScalingSpeedX=0.04, **fps)
    if wave:
        # Rows shifted by up to 0.3 units, the flag's edges rippling with the hill's.
        front.update(kind='SHADER', shaderEffect='WAVE', shaderAmplitude=0.3, shaderWavelength=2.0, shaderSpeed=0.5)
    page = {'repeatOnX': True, 'repeatOnY': False, 'useOriginalSize': True}
    page.update(gradients(sky, haze))
    page['pageModel'] = {'atlasName': 'animated.atlas', 'pageList': [back, front]}
    return page


def main():
    HERE.mkdir(parents=True, exist_ok=True)
    OUT.mkdir(parents=True, exist_ok=True)
    anim_x, anim_y = sheet()
    pages = {'a01': flags(), 'a02': beach(anim_x, anim_y), 'a04': beach(anim_x, anim_y, planetRadius=400),
             'a05': flags(wave=True)}
    for sid, page in pages.items():
        (OUT / f'{sid}.jplax').write_text(json.dumps(page, indent=1) + '\n')
    about = f"r365 beach at half size, its palm crowns (l3) carrying r375's 24 frames as a patch at {PALM_FPS} fps"
    scenes = [
        {'id': 'a01', 'name': 'flags-mirrored-flipped',
         'about': 'r393\'s probe: a hill with a flag of four colours (frames 0, 1, 2 at 0, 6, 12 s), on an IMAGE layer '
                  'mirrored above itself and on one flipped on X: the patch mirrored in its tile. The round\'s teeth: the palms move too little to fail a wrong frame'},
        {'id': 'a02', 'name': 'beach-palms', 'about': about},
        {'id': 'a03', 'page': 'a02', 'name': 'beach-palms-dolly', 'zoom': [1, 0.7], 'dolly': True,
         'about': 'a02 dollied in from zoom 1 to 0.7: the patch at its layer\'s own zoom, through DollyBatch'},
        {'id': 'a04', 'name': 'beach-palms-planet',
         'about': 'a02 round a planet 400% of the world in radius: the patch bent with its tile, through PlanetBatch'},
        {'id': 'a05', 'name': 'flags-wave',
         'about': f'a01 with its front hill a WAVE SHADER layer, both flags at {WAVE_FLAG_FPS} fps (frames apart at its '
                  'stills, about 48, 54 and 60 s on the clock): the patch through the effect given its frame\'s '
                  'region (r397). Given the hill\'s, WAVE clamps the flag to the hill\'s edge'},
    ]
    for s in scenes:
        s['page'] = str(OUT / f"{s.get('page', s['id'])}.jplax")
        s['atlasDir'] = str(HERE)
    out = {'round': 'animated',
           'about': 'Animated patches (format 16, r393, r394; docs/animated-layers.md): a layer\'s frames drawn after '
                    'each tile at floor(time x fps) mod their count; a SHADER layer\'s after all its tiles (r397). Godot draws its own (plax_anim.gd); jME runs '
                    'core\'s ParallaxLayer.drawAnim. Written by core/test-data/animated/make_round.py.',
           'scenes': [{k: s[k] for k in ('id', 'page', 'atlasDir', 'name', 'about') if k in s} |
                      {k: v for k, v in s.items() if k in ('zoom', 'dolly')} for s in scenes]}
    (OUT / 'round.json').write_text(json.dumps(out, indent=1) + '\n')
    print(f'{OUT}/round.json: {len(scenes)} scenes; {HERE}/animated.png; palms at {PALM_FPS} fps, every still '
          f'{_margin(PALM_FPS):.3f} of a frame or more from a change; a05\'s flags at {WAVE_FLAG_FPS} fps, frames '
          f'{[int(t * WAVE_FLAG_FPS) % 4 for t in still_times(5)[4]]}')


if __name__ == '__main__':
    main()
