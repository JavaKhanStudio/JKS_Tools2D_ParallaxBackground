"""xray.py ROUND OUT — r363's v01 seen through the layers in front of its figure, round and sheet (r369).

    python3 tools/r369-lurker-cover/xray.py round build/r369/round build/r369/xray
    tools/ai-film.sh build/r369/xray build/r369/xray/film 14 24
    python3 tools/r369-lurker-cover/xray.py sheet build/r369/xray build/r369/xray-sheet.png build/r363/film \
        build/r369/v01-track.txt

`round` writes OUT/round.json from ROUND (make.py's, v01 with its cover baked): the figure drawn last, over every
layer (where it stands, its position unchanged: a layer's place depends on its own settings, not its order), and each
layer in front of it alone, with and without the figure (which one hides it). `sheet` draws, half a second apart over
the hidden stretch, v01 as filmed, the x-ray with the figure boxed, and each near layer alone over the figure, with
its figure pixels counted as moments.py does (more than 30 over r+g+b against the same page without it), and v01's
difference from v00 times 8: the figure a soft edge lets through under that threshold. Over each column, the cover's
verdict for that frame (check.sh's track).
"""
import copy
import json
import os
import pathlib
import shutil
import sys

import numpy as np
from PIL import Image, ImageDraw

FPS, START = 24, 12.0
TIMES = [19.5 + k / 2 for k in range(11)]


def write_round(src, out):
    os.makedirs(out, exist_ok=True)
    for f in ('reveal.atlas', 'reveal.png'):
        shutil.copy(os.path.join(src, f), out)
    page = json.loads(pathlib.Path(src, 'v01.jplax').read_text())
    layers = page['pageModel']['pageList']
    fig = next(i for i, layer in enumerate(layers) if layer.get('kind') == 'LURKER')
    near = list(range(fig + 1, len(layers)))
    pages = {'x-top': layers[:fig] + layers[fig + 1:] + [layers[fig]]}
    for n in near:
        others = [m for m in near if m != n]
        pages[f'x-only{n}'] = [layer for i, layer in enumerate(layers) if i not in others]
        pages[f'x-only{n}-nofig'] = [layer for i, layer in enumerate(layers) if i not in others and i != fig]
    scenes = []
    for sid, kept in pages.items():
        p = copy.deepcopy(page)
        p['pageModel']['pageList'] = kept
        path = os.path.join(out, sid + '.jplax')
        pathlib.Path(path).write_text(json.dumps(p, indent=1))
        scenes.append({'id': sid, 'page': path, 'atlasDir': out, 'name': sid, 'about': 'r369 x-ray', 'lint': []})
    r = {'note': 'r369: v01 seen through its near layers', 'seed': 363, 'scenes': scenes}
    pathlib.Path(out, 'round.json').write_text(json.dumps(r, indent=1))
    print(f'{out}/round.json: figure layer {fig}, near layers {near}')


def frame(film, sid, t):
    return Image.open(os.path.join(film, 'frames', f'{sid}-f{round((t - START) * FPS):04d}.png')).convert('RGB')


def diff(a, b):
    return (np.abs(np.asarray(a, dtype=int) - np.asarray(b, dtype=int)).sum(2) > 30)


def sheet(xray, out, v01film='build/r363/film', cover=None):
    film = os.path.join(xray, 'film')
    scenes = [s['id'] for s in json.loads(pathlib.Path(xray, 'round.json').read_text())['scenes']]
    alone = [s for s in scenes if s.startswith('x-only') and not s.endswith('-nofig')]
    track = {}
    if cover:
        for line in pathlib.Path(cover).read_text().splitlines():
            parts = line.split()
            if len(parts) >= 3 and parts[0].isdigit():
                track[int(parts[0])] = parts[2]
    rows = [('v01 as filmed', None), ('v01 minus the page\nwithout it, x8:\nwhat still shows', 'ghost'),
            ('x-ray: figure over all', None)] + [(s, s) for s in alone]
    cw, ch, top = 220, 165, 22
    im = Image.new('RGB', (110 + len(TIMES) * cw, top + len(rows) * (ch + top)), 'black')
    d = ImageDraw.Draw(im)
    for k, t in enumerate(TIMES):
        # The figure's box, from the x-ray against the page without it.
        top_fr, bare = frame(film, 'x-top', t), frame(v01film, 'v00', t)
        ys, xs = np.nonzero(diff(top_fr, bare)[:, :])
        cx, cy = (int(xs.mean()), int(ys.mean())) if len(xs) else (640, 300)
        box = (cx - 80, cy - 60, cx + 80, cy + 60)
        fb = (xs.min() - box[0], ys.min() - box[1], xs.max() - box[0], ys.max() - box[1]) if len(xs) else None
        x = 110 + k * cw
        state = track.get(round((t - START) * FPS), '')
        d.text((x + 4, 4), f't={t:.1f}s  {state}', fill=(120, 230, 120) if state == 'covered' else 'white')
        for r, (label, sid) in enumerate(rows):
            y = top + r * (ch + top)
            if r == 0:
                fr, px = frame(v01film, 'v01', t), int(diff(frame(v01film, 'v01', t), bare).sum())
            elif sid == 'ghost':
                a = np.abs(np.asarray(frame(v01film, 'v01', t), dtype=int) - np.asarray(bare, dtype=int))
                px = int(a.sum(2).max())
                fr = Image.fromarray(np.clip(a * 8, 0, 255).astype(np.uint8))
            elif r == 2:
                fr, px = top_fr, len(xs)
            else:
                fr = frame(film, sid, t)
                px = int(diff(fr, frame(film, sid + '-nofig', t)).sum())
            crop = fr.crop(box).resize((cw - 6, ch - 6 - 0), Image.NEAREST)
            im.paste(crop, (x, y + 16))
            if fb:
                s = (cw - 6) / 160
                d.rectangle((x + fb[0] * s, y + 16 + fb[1] * s, x + fb[2] * s, y + 16 + fb[3] * s),
                            outline=(230, 60, 60), width=1)
            colour = (120, 230, 120) if px == 0 else (240, 200, 80)
            text = {'ghost': f'largest diff {px}', None: f'figure: {px} px'}.get(sid, f'{px} figure px (>30)')
            d.text((x + 4, y + 2), text if r else f'{px} figure px (>30)', fill=colour)
            if k == 0:
                d.multiline_text((4, y + 40), label.replace(': ', ':\n').replace('x-only', 'layer ') +
                                 ('\nalone in front' if sid and sid.startswith('x-') else ''), fill='white')
    im.save(out)
    print(out)


if __name__ == '__main__':
    {'round': write_round, 'sheet': sheet}[sys.argv[1]](*sys.argv[2:])
