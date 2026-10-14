"""make.py OUT — the r363 reveal round: a figure in a layer of its own, behind Hiver's front pines (r363).

    python3 tools/r363-reveal/make.py build/r363/round
    tools/ai-film.sh build/r363/round build/r363/film 24 24

Hiver's page (engines/rounds/round1/s03.jplax) with one IMAGE layer added between its far hills (speed 0.024) and its
front pines (0.038, 0.041): a hooded figure, drawn here, alone in a strip Pad X wide (once every three screens of
its scroll), standing on the hills' snow line. Nothing but today's .jplax: the pines hide it and their gaps show it.
v01 draws it at the size its speed gives it (a person against the pines, scaled by its speed over theirs); v02 at the
size the accident in parallax-ai's R1 had, a giant. Writes OUT/reveal.png and .atlas (Hiver.png with the figure
under it), OUT/v01.jplax, v02.jplax and round.json.
"""
import copy
import json
import os
import sys

from PIL import Image, ImageDraw

HIVER = 'core/test-data/samples'
PAGE = 'engines/rounds/round1/s03.jplax'
# World units: the view is 40 wide and 22.5 high at 1280x720 (32 px a unit).
FIGURE_SPEED = 0.032
FOOT_Y = 59.0  # percent of the world height: the hills' snow line under the pines
ENTERS_AT = 13.0  # seconds into the 60 units/s scroll the figure's left edge crosses the view's right edge
PERIOD = 120.0  # world units from one figure to the next: three views


def figure(width=160, height=400, scale=4):
    """A hooded figure, dark as the pines' shadow side, two pale eyes; transparent around it."""
    w, h = width * scale, height * scale
    im = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    body = (26, 46, 48, 255)
    # Cloak: a bell from the shoulders to the ground, ragged hem.
    cloak = [(w * 0.28, h * 0.24), (w * 0.72, h * 0.24), (w * 0.98, h * 0.97)]
    for i in range(9, -1, -1):
        cloak.append((w * (0.02 + 0.96 * i / 9), h * (1.0 if i % 2 else 0.95)))
    d.polygon(cloak, fill=body)
    # Hood.
    d.ellipse((w * 0.22, h * 0.02, w * 0.78, h * 0.34), fill=body)
    # Eyes, a little apart, under the hood's brow.
    eye = (236, 240, 214, 255)
    for cx in (0.40, 0.60):
        d.ellipse((w * (cx - 0.05), h * 0.15, w * (cx + 0.05), h * 0.19), fill=eye)
    return im.resize((width, height), Image.LANCZOS)


def build_atlas(out):
    hiver = Image.open(os.path.join(HIVER, 'Hiver.png'))
    fig = figure()
    top = hiver.height + 2
    sheet = Image.new('RGBA', (hiver.width, top + fig.height + 1), (0, 0, 0, 0))
    sheet.paste(hiver, (0, 0))
    sheet.paste(fig, (1, top))
    sheet.save(os.path.join(out, 'reveal.png'))
    with open(os.path.join(HIVER, 'Hiver.atlas')) as f:
        atlas = f.read().replace('Hiver.png', 'reveal.png', 1)
    atlas = atlas.replace(f'size: {hiver.width}, {hiver.height}', f'size: {sheet.width}, {sheet.height}', 1)
    atlas = atlas.rstrip('\n') + f'\nfigure\n  rotate: false\n  xy: 1, {top}\n  size: {fig.width}, {fig.height}\n' \
        f'  orig: {fig.width}, {fig.height}\n  offset: 0, 0\n  index: -1\n'
    with open(os.path.join(out, 'reveal.atlas'), 'w') as f:
        f.write(atlas)
    return fig.size


def page_with_figure(size, height_units):
    """Hiver's page on the reveal atlas, with the figure height_units high (none when 0)."""
    with open(PAGE) as f:
        page = json.load(f)
    page['pageModel']['atlasName'] = 'reveal.atlas'
    layers = page['pageModel']['pageList']
    if not height_units:
        return page
    width = height_units * size[0] / size[1]
    pad = PERIOD - width
    # Its left edge at x = 40 when the scroll has moved it ENTERS_AT seconds at 60 units/s times its speed.
    start = 40 + 60 * FIGURE_SPEED * ENTERS_AT
    layer = copy.deepcopy(layers[4])
    layer.update({
        'kind': 'LURKER', 'regionName': 'figure', 'regionPosition': 0, 'flipX': False,
        'parallaxScalingSpeedX': FIGURE_SPEED, 'parallaxScalingSpeedY': FIGURE_SPEED * 0.8,
        'sizeRatio': width / 40, 'decal_X_Ratio': start / 40 * 100, 'decal_Y_Ratio': FOOT_Y,
        'padX': pad, 'padXFactor': 0.0,
    })
    # Behind the two front pine layers, in front of the far hills.
    layers.insert(5, layer)
    return page


def main(out):
    os.makedirs(out, exist_ok=True)
    size = build_atlas(out)
    # The front pines (speed 0.041) are about 400 px, 12.5 units, tall: a 20 m spruce. A 1.8 m figure at their depth
    # is 1.1 units; at its own speed, farther, 0.041 / 0.032 times smaller: 0.9. v01 draws it twice that (a tall
    # lurker, still a man beside the trees), v02 at the accident's size, as tall as the pines.
    scenes = []
    for sid, units, about in (
            ('v00', 0, 'Hiver without the figure: what v01 and v02 are measured against'),
            ('v01', 1.8, 'figure at its depth: hidden by the pines, seen through their gaps'),
            ('v02', 11.0, 'the same figure as large as the R1 accident: a giant')):
        with open(os.path.join(out, sid + '.jplax'), 'w') as f:
            json.dump(page_with_figure(size, units), f, indent=1)
        scenes.append({'id': sid, 'page': os.path.join(out, sid + '.jplax'), 'atlasDir': out,
                       'name': 'Hiver-' + sid, 'about': about, 'lint': []})
    with open(os.path.join(out, 'round.json'), 'w') as f:
        json.dump({'note': 'r363: a figure revealed by the pines in front of it', 'seed': 363, 'scenes': scenes}, f,
                  indent=1)


if __name__ == '__main__':
    main(sys.argv[1])
