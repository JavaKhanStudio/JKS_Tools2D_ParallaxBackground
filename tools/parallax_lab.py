#!/usr/bin/env python3
"""The parallax lab's scene maker and page linter (r73). Plain Python 3, no dependency.

  tools/parallax_lab.py lint PAGE...            the numbers of a page (.jplax/.plaxpj) and the rules it breaks
  tools/parallax_lab.py survey OUT_DIR          every sample page as it is, as a round (to render or grade)
  tools/parallax_lab.py round1 [OUT_DIR]        round 1: Simon's pages, each rule broken once, and pages written from
                                                the rules alone, shuffled blind (default demo/lab/round1)

A round is a folder of .jplax pages and a round.json listing them in the order shown, with the folder of each page's
atlas and what the scene tests; ParallaxLab (./gradlew :demo:lab) shows it and saves the grades next to it.
Paths are relative to the repository root, the lab's working directory.
"""
import copy
import glob
import json
import math
import os
import random
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))

LAYER_FIELDS = ['regionName', 'regionPosition', 'flipX', 'flipY', 'parallaxScalingSpeedX', 'parallaxScalingSpeedY',
                'speedXAtRest', 'sizeRatio', 'decal_X_Ratio', 'decal_Y_Ratio', 'padX', 'padXFactor', 'padY',
                'padYFactor', 'mirror']
PAGE_FIELDS = ['topHalf_top', 'topHalf_bottom', 'topHalfSize', 'bottomHalf_top', 'bottomHalf_bottom',
               'bottomHalfSize', 'repeatOnX', 'repeatOnY', 'useOriginalSize']

SAMPLES = {
    'Hiver': ('demo/assets/hiver/Hiver.plaxpj', 'demo/assets'),
    'Printemps': ('demo/assets/printemps/Printemps.plaxpj', 'demo/assets'),
    'Calm': ('editor/Files/transfer/calm.plaxpj', 'editor/Files/transfer'),
    'CalmLag': ('editor/Files/transfer/calmLag.plaxpj', 'editor/Files/transfer'),
    'OneNight': ('editor/Files/Demos/OneNight.plaxpj', 'editor/Files/Demos'),
    'PurpleFairy': ('editor/Files/Demos/PurpleFairy.plaxpj', 'editor/Files/Demos'),
    'CalmTree': ('editor/Files/Aa new/calmTree.plaxpj', 'editor/Files/Aa new'),
    'CalmTree3': ('editor/Files/Aa new/calmTree3.plaxpj', 'editor/Files/Aa new'),
    'CalmTree4': ('editor/Files/Aa new/calmTree4.plaxpj', 'editor/Files/Aa new'),
    'CalmTree5': ('editor/Files/Aa new/calmTree5.plaxpj', 'editor/Files/Aa new'),
}


# ---------------------------------------------------------------- pages

def load(path):
    """A page as a .jplax holds it: of a project, only the layers an export keeps (the ones from the atlas)."""
    with open(os.path.join(ROOT, path), encoding='utf-8') as f:
        root = json.load(f)
    saving = root.get('saving')
    page = saving if saving is not None else root
    inside = saving.get('inside') if saving is not None else None
    out = {k: page[k] for k in PAGE_FIELDS if k in page}
    model = page['pageModel']
    layers = [l for i, l in enumerate(model['pageList']) if not inside or i >= len(inside) or inside[i]]
    out['pageModel'] = {'atlasName': model['atlasName'], 'outside': model.get('outside', False),
                        'pageList': [{k: l[k] for k in LAYER_FIELDS if k in l} for l in layers]}
    return out


def layers(page):
    return page['pageModel']['pageList']


def color(hex_rgb, alpha=1.0):
    r, g, b = (int(hex_rgb[i:i + 2], 16) / 255 for i in (0, 2, 4))
    return {'r': round(r, 7), 'g': round(g, 7), 'b': round(b, 7), 'a': alpha}


def hex_of(c):
    return '%02x%02x%02x' % tuple(round(c[k] * 255) for k in 'rgb') if c else '------'


def layer(region, position=0, speed=0.02, size=1.0, dx=0.0, dy=0.0, speed_y=None, rest=0.0, flip_x=False,
          flip_y=False, pad_x=0.0, pad_y=0.0, mirror=False):
    """A layer written by hand; speed_y defaults to 0.6 x speed, the ratio of every designed sample."""
    return {'regionName': region, 'regionPosition': position, 'flipX': flip_x, 'flipY': flip_y,
            'parallaxScalingSpeedX': speed, 'parallaxScalingSpeedY': speed * 0.6 if speed_y is None else speed_y,
            'speedXAtRest': rest, 'sizeRatio': size, 'decal_X_Ratio': dx, 'decal_Y_Ratio': dy, 'padX': pad_x,
            'padXFactor': 0.0, 'padY': pad_y, 'padYFactor': 0.0, 'mirror': mirror}


def page_of(atlas, layer_list, sky=None, ground=None, top_size=0.5, bottom_size=0.5, original_size=None):
    """sky/ground: (top hex, bottom hex) of the top and bottom gradient squares; None = white, as the editor starts."""
    sky = sky or ('ffffff', 'ffffff')
    ground = ground or ('ffffff', 'ffffff')
    page = {'topHalf_top': color(sky[0]), 'topHalf_bottom': color(sky[1]), 'topHalfSize': top_size,
            'bottomHalf_top': color(ground[0]), 'bottomHalf_bottom': color(ground[1]), 'bottomHalfSize': bottom_size,
            'repeatOnX': True, 'repeatOnY': False,
            'pageModel': {'atlasName': atlas, 'outside': False, 'pageList': layer_list}}
    if original_size is not None:
        page['useOriginalSize'] = original_size
    return page


def speeds(page):
    return [l['parallaxScalingSpeedX'] for l in layers(page)]


def set_speeds(page, values):
    """New X speeds, keeping each layer's Y/X ratio."""
    for l, v in zip(layers(page), values):
        ratio = l['parallaxScalingSpeedY'] / l['parallaxScalingSpeedX'] if l['parallaxScalingSpeedX'] else 0.6
        l['parallaxScalingSpeedX'] = v
        l['parallaxScalingSpeedY'] = v * ratio


# ---------------------------------------------------------------- lint

def lint(page):
    """The rules a page breaks, as short sentences. The rules are the skill's (.claude/skills/parallax-pages)."""
    problems = []
    s = speeds(page)
    n = len(s)
    if n < 3:
        problems.append('%d layers: under 3 reads as flat' % n)
    if n > 12:
        problems.append('%d layers: past ~10 the depth steps blur and every layer costs fill rate' % n)
    drops = [i for i in range(1, n) if s[i] < s[i - 1] * 0.98]
    if drops:
        drifting = [i for i in drops if layers(page)[i - 1]['speedXAtRest'] or layers(page)[i]['speedXAtRest']]
        problems.append('speeds go DOWN toward the front at layer(s) %s: layers are stored back to front, a front '
                        'layer slower than the one behind it reads as behind it%s' % (
                            drops, ' (%s drift on their own: fine only if they do not overlap)' % drifting
                            if drifting else ''))
    if n >= 2 and s[0] > 0:
        span = max(s) / min(x for x in s if x > 0)
        if span < 2.5:
            problems.append('speed span %.1fx (front/back): under ~3x the scene reads as one flat plane' % span)
        if span > 20:
            problems.append('speed span %.0fx: past ~15x the back looks painted on and the front whips' % span)
        steps = [s[i] / s[i - 1] for i in range(1, n) if s[i - 1] > 0 and s[i] > 0]
        flat = [i + 1 for i, r in enumerate(steps) if r < 1.02]
        if flat and not drops:
            problems.append('layers %s move at the speed of the one behind: they fuse into one plane' % flat)
    for i, l in enumerate(layers(page)):
        if l['sizeRatio'] < 0.5 and page.get('repeatOnX', True) and l['padX'] == 0:
            problems.append('layer %d is %.2f worlds wide and tiled: its repeat shows %.0f times a screen'
                            % (i, l['sizeRatio'], 1 / l['sizeRatio']))
    return problems


def describe(page):
    out = []
    out.append('  atlas %s  repeat X=%s Y=%s  sky %s->%s (%.2f uncovered)  ground %s->%s (%.2f uncovered)' % (
        page['pageModel']['atlasName'], page.get('repeatOnX'), page.get('repeatOnY'),
        hex_of(page.get('topHalf_bottom')), hex_of(page.get('topHalf_top')), page.get('topHalfSize', 0.5),
        hex_of(page.get('bottomHalf_top')), hex_of(page.get('bottomHalf_bottom')), page.get('bottomHalfSize', 0.5)))
    prev = None
    for i, l in enumerate(layers(page)):
        sx = l['parallaxScalingSpeedX']
        step = '' if not prev else 'x%.2f' % (sx / prev)
        prev = sx or prev
        out.append('  %2d %-16s speed %.4f %-6s y/x %.2f rest %6.1f size %5.2f decal %6.1f,%6.1f%s' % (
            i, '%s#%d' % (l['regionName'], l['regionPosition']), sx, step,
            l['parallaxScalingSpeedY'] / sx if sx else 0, l['speedXAtRest'], l['sizeRatio'], l['decal_X_Ratio'],
            l['decal_Y_Ratio'], (' flip' if l['flipX'] else '') + (' mirror' if l.get('mirror') else '')))
    return '\n'.join(out)


# ---------------------------------------------------------------- variants: one rule broken each

def flat(page):
    p = copy.deepcopy(page)
    s = sorted(speeds(p))
    set_speeds(p, [s[len(s) // 2]] * len(s))
    return p


def inverted(page):
    p = copy.deepcopy(page)
    set_speeds(p, list(reversed(speeds(p))))
    return p


def shuffled(page, seed):
    p = copy.deepcopy(page)
    s = speeds(p)
    rng = random.Random(seed)
    while True:
        t = s[:]
        rng.shuffle(t)
        if t != s:
            break
    set_speeds(p, t)
    return p


def rescale_span(page, factor):
    """Keeps the geometric middle and multiplies the span (in log space) by factor: >1 exaggerates, <1 compresses."""
    p = copy.deepcopy(page)
    s = speeds(p)
    logs = [math.log(v) for v in s]
    mid = (max(logs) + min(logs)) / 2
    set_speeds(p, [math.exp(mid + (x - mid) * factor) for x in logs])
    return p


def linear(page):
    """Same back and front speeds, evenly spaced instead of by a constant ratio."""
    p = copy.deepcopy(page)
    s = speeds(p)
    n = len(s)
    set_speeds(p, [s[0] + (s[-1] - s[0]) * i / (n - 1) for i in range(n)])
    return p


def geometric(page):
    """Same back and front speeds, each layer a constant ratio faster than the one behind."""
    p = copy.deepcopy(page)
    s = speeds(p)
    n = len(s)
    r = (s[-1] / s[0]) ** (1 / (n - 1))
    set_speeds(p, [s[0] * r ** i for i in range(n)])
    return p


def shrunk(page, factor):
    p = copy.deepcopy(page)
    for l in layers(p):
        l['sizeRatio'] *= factor
    return p


def white_sky(page):
    p = copy.deepcopy(page)
    for k in ('topHalf_top', 'topHalf_bottom', 'bottomHalf_top', 'bottomHalf_bottom'):
        p[k] = color('ffffff')
    return p


def recolored(page, sky, ground):
    p = copy.deepcopy(page)
    p['topHalf_top'], p['topHalf_bottom'] = color(sky[0]), color(sky[1])
    p['bottomHalf_top'], p['bottomHalf_bottom'] = color(ground[0]), color(ground[1])
    return p


def aligned(page):
    """Every layer starts at decal X 0: the tiles' joins and shapes line up at the start."""
    p = copy.deepcopy(page)
    for l in layers(p):
        l['decal_X_Ratio'] = 0.0
    return p


# ---------------------------------------------------------------- pages written from the rules alone

def calm_from_rules():
    """The calm atlas (6 strips 5000 px wide), laid out from the rules without looking at Simon's calm page: sky
    gradient behind, clouds drifting, mountains, then the three tree strips ordered by their haze (the palest is the
    farthest: Trees_close, whose name says the opposite), speeds x1.4 per layer, each strip covering the one behind's
    bottom edge."""
    r = 1.4
    base = 0.012
    L = [
        layer('Clouds', speed=base, size=1.1, dx=15, dy=68, rest=40),
        layer('Mountains_big', speed=base * r, size=1.3, dx=0, dy=36),
        layer('Mountains_small', speed=base * r ** 2, size=1.1, dx=35, dy=28),
        layer('Trees_close', speed=base * r ** 3, size=1.0, dx=10, dy=20),
        layer('Trees_far', speed=base * r ** 4, size=1.0, dx=55, dy=9),
        layer('Trees_fartest', speed=base * r ** 5, size=1.2, dx=25, dy=0),
    ]
    return page_of('calm.atlas', L, sky=('3a8fd9', 'dff1ff'), ground=('0b5a48', '07402f'))


def night_from_rules():
    """The OneNight atlas laid out from the rules: clouds drifting slowly, rocks, three grounds, water in front."""
    r = 1.35
    base = 0.018
    L = [
        layer('clouds', 0, speed=base, size=1.0, dx=20, dy=40, rest=25),
        layer('rocks', speed=base * r, size=1.0, dx=0, dy=24),
        layer('ground', 0, speed=base * r ** 2, size=1.0, dx=40, dy=16),
        layer('ground', 1, speed=base * r ** 3, size=1.0, dx=10, dy=8, flip_x=True),
        layer('ground', 2, speed=base * r ** 4, size=1.1, dx=65, dy=0),
        layer('waterDark', speed=base * r ** 5, size=1.0, dx=0, dy=-2, rest=-15),
    ]
    return page_of('OneNight.atlas', L, sky=('0b1d3a', '5a6fa8'), ground=('1a1420', '0a0810'), original_size=True)


# ---------------------------------------------------------------- rounds

def write_round(out_dir, scenes, seed, note):
    """scenes: (name, page, atlasDir, about). Shuffled with the seed and renamed s01.. so the order and the names
    say nothing; the name stays in round.json for the reveal."""
    out = os.path.join(ROOT, out_dir)
    os.makedirs(out, exist_ok=True)
    order = list(scenes)
    random.Random(seed).shuffle(order)
    listed = []
    for i, (name, page, atlas_dir, about) in enumerate(order, 1):
        sid = 's%02d' % i
        path = os.path.join(out_dir, sid + '.jplax')
        with open(os.path.join(ROOT, path), 'w', encoding='utf-8') as f:
            json.dump(page, f, indent=1)
            f.write('\n')
        listed.append({'id': sid, 'page': path, 'atlasDir': atlas_dir, 'name': name, 'about': name + ': ' + about,
                       'lint': lint(page)})
    with open(os.path.join(out, 'round.json'), 'w', encoding='utf-8') as f:
        json.dump({'note': note, 'seed': seed, 'scenes': listed}, f, indent=1)
        f.write('\n')
    print('%d scenes in %s' % (len(listed), out_dir))


def survey(out_dir):
    scenes = [(name, load(path), atlas_dir, 'as saved, ' + path) for name, (path, atlas_dir) in SAMPLES.items()]
    write_round(out_dir, scenes, 0, 'Every sample page as saved.')


def round1(out_dir):
    s = {name: load(path) for name, (path, _) in SAMPLES.items()}
    d = {name: atlas_dir for name, (_, atlas_dir) in SAMPLES.items()}
    scenes = [
        ('Hiver', s['Hiver'], d['Hiver'], 'original (control)'),
        ('Calm', s['Calm'], d['Calm'], 'original (control)'),
        ('OneNight', s['OneNight'], d['OneNight'], 'original (control)'),
        ('PurpleFairy', s['PurpleFairy'], d['PurpleFairy'], 'original (control)'),
        ('CalmLag', s['CalmLag'], d['CalmLag'], 'original: the editor\'s default layout, no art direction'),
        ('Hiver-flat', flat(s['Hiver']), d['Hiver'], 'every layer at the same speed: no depth from motion'),
        ('Calm-flat', flat(s['Calm']), d['Calm'], 'every layer at the same speed: no depth from motion'),
        ('PurpleFairy-inverted', inverted(s['PurpleFairy']), d['PurpleFairy'],
         'speeds reversed: the back moves fastest'),
        ('OneNight-shuffled', shuffled(s['OneNight'], 7), d['OneNight'], 'speeds shuffled: depth order broken'),
        ('Calm-compressed', rescale_span(s['Calm'], 0.35), d['Calm'],
         'speed span cut to a third (5.6x -> 1.8x): shallow'),
        ('PurpleFairy-exaggerated', rescale_span(s['PurpleFairy'], 2.2), d['PurpleFairy'],
         'speed span x2.2 in log (5.6x -> 45x): back frozen, front whips'),
        ('Hiver-linear', linear(s['Hiver']), d['Hiver'],
         'same back/front speeds, evenly spaced instead of a constant ratio'),
        ('OneNight-small', shrunk(s['OneNight'], 0.5), d['OneNight'], 'every layer half as wide: repeats show'),
        ('Calm-whitesky', white_sky(s['Calm']), d['Calm'], 'sky gradient removed (white)'),
        ('OneNight-wrongsky', recolored(s['OneNight'], ('ffb347', 'ffe7c2'), ('b0d88f', '6fae4a')), d['OneNight'],
         'gradients of a sunny day behind a night scene'),
        ('PurpleFairy-aligned', aligned(s['PurpleFairy']), d['PurpleFairy'],
         'every layer starts at decal X 0: joins line up at the start'),
        ('Calm-agent', calm_from_rules(), d['Calm'], 'written from the rules alone (not from Simon\'s page)'),
        ('OneNight-agent', night_from_rules(), d['OneNight'], 'written from the rules alone (not from Simon\'s page)'),
    ]
    write_round(out_dir, scenes, 73, 'Round 1 (r73): Simon\'s pages, one rule broken each, and two pages written '
                                     'from the rules. Grade 1-5 on how good the scene feels as a game background.')


def main(argv):
    if len(argv) < 2 or argv[1] in ('-h', '--help'):
        print(__doc__)
        return 0
    cmd = argv[1]
    if cmd == 'lint':
        bad = 0
        for path in argv[2:]:
            page = load(path)
            print(path)
            print(describe(page))
            problems = lint(page)
            bad += bool(problems)
            print('\n'.join('  ! ' + p for p in problems) or '  ok')
        return 1 if bad else 0
    if cmd == 'survey':
        survey(argv[2])
        return 0
    if cmd == 'round1':
        round1(argv[2] if len(argv) > 2 else 'demo/lab/round1')
        return 0
    print('unknown command ' + cmd, file=sys.stderr)
    return 2


if __name__ == '__main__':
    sys.exit(main(sys.argv))
