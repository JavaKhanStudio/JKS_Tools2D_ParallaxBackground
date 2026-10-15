#!/usr/bin/env python3
"""The parallax page linter (r73), and the page writers the parallax-pages skill names. Plain Python 3.

  tools/parallax_lab.py lint [--atlas DIR] PAGE...  the numbers of a page (.jplax/.plaxpj) and the rules it breaks,
                                                its layout too when its atlas is found (next to it, in its round.json
                                                or in core/test-data/samples) or given; the layout needs Pillow

layer(), empty() and page_of() write a page by hand; flat(), rescale_span() and the other variants break one rule of a
page; the *_from_art() pages are the skill's worked examples. Paths are relative to the repository root.

The grading lab, its rounds (survey, study, round1 to round4) and the sample projects they read are in the editor
repository, JKS_Tools2D_ParallaxEditor, since r133: its copy of this file writes them (r321 took them out of this one).
"""
import collections
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
                'padYFactor', 'mirror', 'kind', 'name', 'particlesLibgdx', 'particlesGodot', 'particlesAnchor',
                'shaderEffect', 'shaderAmplitude', 'shaderWavelength', 'shaderSpeed', 'shaderHaze',
                'sequenceSegments', 'sequenceSeed', 'sequenceLength']
# What a layer field a .jplax leaves out reads as: Parallax_Model's field defaults (Godot's LAYER_DEFAULTS), for the
# fields read as l[...] here. The rest are read with get() and their own default.
LAYER_DEFAULTS = {'regionPosition': 0, 'flipX': False, 'flipY': False, 'parallaxScalingSpeedX': 0.0,
                  'parallaxScalingSpeedY': 0.0, 'speedXAtRest': 0.0, 'sizeRatio': 1.0, 'decal_X_Ratio': 0.0,
                  'decal_Y_Ratio': 0.0, 'padX': 0.0, 'padXFactor': 0.0, 'padY': 0.0, 'padYFactor': 0.0, 'mirror': False}
PAGE_FIELDS = ['topHalf_top', 'topHalf_bottom', 'topHalfSize', 'bottomHalf_top', 'bottomHalf_bottom',
               'bottomHalfSize', 'repeatOnX', 'repeatOnY', 'useOriginalSize', 'fogStrength', 'fogColor']

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
                        'pageList': [{**LAYER_DEFAULTS, **{k: l[k] for k in LAYER_FIELDS if k in l}} for l in layers]}
    return out


def layers(page):
    return page['pageModel']['pageList']


def color(hex_rgb, alpha=1.0):
    r, g, b = (int(hex_rgb[i:i + 2], 16) / 255 for i in (0, 2, 4))
    return {'r': round(r, 7), 'g': round(g, 7), 'b': round(b, 7), 'a': alpha}


def hex_of(c):
    return ''.join(f'{round(c[k] * 255):02x}' for k in 'rgb') if c else '------'


def layer(region, position=0, speed=0.02, size=1.0, dx=0.0, dy=0.0, speed_y=None, rest=0.0, flip_x=False,
          flip_y=False, pad_x=0.0, pad_y=0.0, mirror=False):
    """A layer written by hand; speed_y defaults to 0.6 x speed, the ratio of every designed sample."""
    return {'regionName': region, 'regionPosition': position, 'flipX': flip_x, 'flipY': flip_y,
            'parallaxScalingSpeedX': speed, 'parallaxScalingSpeedY': speed * 0.6 if speed_y is None else speed_y,
            'speedXAtRest': rest, 'sizeRatio': size, 'decal_X_Ratio': dx, 'decal_Y_Ratio': dy, 'padX': pad_x,
            'padXFactor': 0.0, 'padY': pad_y, 'padYFactor': 0.0, 'mirror': mirror}


def empty(name, speed=0.02, size=1.0, dx=0.0, dy=0.0, speed_y=None, rest=0.0, pad_x=0.0, pad_y=0.0):
    """An EMPTY layer (format 5): no image, world width x size by world height x size, drawn only by the hook the game
    registers under its name."""
    out = layer(None, 0, speed, size, dx, dy, speed_y, rest, pad_x=pad_x, pad_y=pad_y)
    out.update(kind='EMPTY', name=name)
    return out


def has_image(l):
    """An IMAGE layer (the default) or a SHADER one (format 7, an image drawn through an effect); EMPTY and PARTICLES
    layers (formats 5 and 6) have no region."""
    return l.get('kind', 'IMAGE') in ('IMAGE', 'SHADER')


def label(l):
    if has_image(l):
        return f"{l['regionName']}#{l['regionPosition']}"
    if l.get('kind') == 'SEQUENCE' and not l.get('name'):  # unnamed, it reads as its segments
        return 'SEQUENCE ' + '+'.join(s['regionName'] for s in l.get('sequenceSegments') or [])
    return f"{l['kind']} '{l.get('name')}'"


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

def lint(page, atlas_dir=None):
    """The rules a page breaks, as short sentences. The rules are the skill's (.claude/skills/parallax-pages). With the
    folder of the page's atlas, its layout too: see layout()."""
    problems = speed_faults(page)
    for i, l in enumerate(layers(page)):
        if l['sizeRatio'] < 0.5 and page.get('repeatOnX', True) and l['padX'] == 0:
            problems.append(f"layer {i} is {l['sizeRatio']:.2f} worlds wide and tiled: its repeat shows"
                            f" {1 / l['sizeRatio']:.0f} times a screen")
    problems += particle_faults(page, atlas_dir)
    problems += shader_faults(page)
    problems += sequence_faults(page, atlas_dir)
    if atlas_dir is not None:
        problems += layout(page, atlas_dir)
    return problems


def speed_faults(page):
    """The speeds' faults: too few or too many layers, a front layer slower than the one behind, a span too short or
    too deep, two layers at one speed."""
    problems = []
    s = speeds(page)
    n = len(s)
    if n < 3:
        problems.append(f'{n} layers: under 3 reads as flat')
    if n > 12:
        problems.append(f'{n} layers: past ~10 the depth steps blur and every layer costs fill rate')
    drops = [i for i in range(1, n) if s[i] < s[i - 1] * 0.98]
    if drops:
        drifting = [i for i in drops if layers(page)[i - 1]['speedXAtRest'] or layers(page)[i]['speedXAtRest']]
        drift = f' ({drifting} drift on their own: fine only if they do not overlap)' if drifting else ''
        problems.append(f'speeds go DOWN toward the front at layer(s) {drops}: layers are stored back to front,'
                        f' a front layer slower than the one behind it reads as behind it{drift}')
    if n >= 2 and s[0] > 0:
        span = max(s) / min(x for x in s if x > 0)
        if span < 2.5:
            problems.append(f'speed span {span:.1f}x (front/back): under ~3x the scene reads as one flat plane')
        if round(span) > 250:
            problems.append(f'speed span {span:.0f}x: past 250x, the deepest span graded, nothing is known; the back may'
                            ' look painted on (round 4: 250x won on Pure and tied on calm, but past ~40x deeper no longer'
                            ' wins every time)')
        steps = [s[i] / s[i - 1] for i in range(1, n) if s[i - 1] > 0 and s[i] > 0]
        # A LURKER figure stands on the layer behind it at its speed, or its feet skate (r366): one plane is meant.
        flat = [i + 1 for i, r in enumerate(steps) if r < 1.02 and layers(page)[i + 1].get('kind') != 'LURKER']
        if flat and not drops:
            problems.append(f'layers {flat} move at the speed of the one behind: they fuse into one plane')
    return problems


def particle_faults(page, atlas_dir):
    """A PARTICLES layer draws nothing in libGDX without a .p, and its .p is looked up beside the page's atlas."""
    problems = []
    for i, l in enumerate(layers(page)):
        if l.get('kind') != 'PARTICLES':
            continue
        effect = l.get('particlesLibgdx')
        if not effect:
            problems.append(f"layer {i} ({label(l)}) names no libGDX effect (particlesLibgdx): it draws nothing")
        elif atlas_dir is not None and not os.path.exists(os.path.join(ROOT, atlas_dir, effect)):
            problems.append(f"layer {i} ({label(l)}): no {os.path.relpath(os.path.join(ROOT, atlas_dir, effect), ROOT)}"
                            f" beside the atlas: it draws nothing")
    return problems


def shader_faults(page):
    """A SHADER layer with no wavelength draws its image without its effect; FOG thins by 0 to 1, no further."""
    problems = []
    for i, l in enumerate(layers(page)):
        if l.get('kind') != 'SHADER':
            continue
        effect = l.get('shaderEffect', 'WAVE')
        if not l.get('shaderWavelength', 0) > 0:
            problems.append(f"layer {i} ({label(l)}) has no shaderWavelength: it draws its image without its {effect}")
        amplitude = l.get('shaderAmplitude', 0)
        if effect == 'FOG' and not 0 <= amplitude <= 1:
            problems.append(f"layer {i} ({label(l)}): FOG thins by 0 to 1, its shaderAmplitude {amplitude:g} reads as"
                            f" {min(1, max(0, amplitude)):g}")
    return problems


def sequence_faults(page, atlas_dir):
    """A SEQUENCE layer (format 8) chains its segments' regions: core fails to load the page on a region its atlas does
    not hold, draws nothing with no segment, reads a sequenceLength under 1 as 1 and weighs every segment 1 when no
    weight is above 0. With the folder of the page's atlas, each segment's region is looked up in it."""
    problems, regions = [], None
    for i, l in enumerate(layers(page)):
        if l.get('kind') != 'SEQUENCE':
            continue
        segments = l.get('sequenceSegments') or []
        if not segments:
            problems.append(f"layer {i} ({label(l)}) has no sequenceSegments: it draws nothing")
            continue
        length = l.get('sequenceLength', 1)
        if length < 1:
            problems.append(f"layer {i} ({label(l)}): its sequenceLength {length} reads as 1, one segment repeated")
        if all(s.get('weight', 1) <= 0 for s in segments):
            problems.append(f"layer {i} ({label(l)}): no segment weighs above 0, so each weighs 1")
        if atlas_dir is not None:
            if regions is None:
                regions = _atlas_regions(page, atlas_dir)
            problems += _segment_faults(page, i, l, regions)
    return problems


def _segment_faults(page, i, l, regions):
    """Layer i's segments that name a region its atlas does not hold; regions: _atlas_regions'."""
    if isinstance(regions, str):
        return [f"layer {i} ({label(l)}): segments not checked: {regions}"]
    named = [(k, s['regionName'], s.get('regionPosition', 0)) for k, s in enumerate(l['sequenceSegments'])]
    return [f"layer {i} ({label(l)}): segment {k} names no region {name}#{pos} in {page['pageModel']['atlasName']}:"
            f" the page fails to load" for k, name, pos in named if (name, pos) not in regions]


def _atlas_regions(page, atlas_dir):
    """The (name, position) pairs the page's atlas holds, or why they could not be read."""
    try:
        import parallax_regions as regions_tool
    except ImportError:
        return 'tools/parallax_regions.py needs Pillow'
    atlas = os.path.join(ROOT, atlas_dir, page['pageModel']['atlasName'])
    if not os.path.exists(atlas):
        return f'no {os.path.relpath(atlas, ROOT)}'
    return {(r['name'], r['pos']) for r in regions_tool.read_atlas(atlas)}


SCREEN_W, SCREEN_H = 40.0, 22.5  # the lab's world at 1280x720, the camera's view before any scroll
SCREEN_PX = 1280  # that world's width in pixels
SOLID, SEAM = 0.99, 20
EDGE_PROBE = 1e-3  # world units inside a layer's top or bottom edge where (a) and (b) ask what covers it
COVERED = 0.9  # a layer edge behind nearer layers this opaque at its height does not show
NOTCH_MARGIN = 2 * SCREEN_H / 720  # 2 screen px each side of a bottom edge, its filtering, that one nearer layer hides
EDGE = SCREEN_H / 200  # an edge within half a percent of the screen's top or bottom is off it


def gradient_set(page, key_top, key_bottom):
    """A gradient the page chose: the editor starts both at white, and a transparent one draws nothing."""
    colours = [page.get(key_top), page.get(key_bottom)]
    if any(c is None or c.get('a', 1) <= 0 for c in colours):
        return False
    return any(hex_of(c) != 'ffffff' for c in colours)


def layout(page, atlas_dir):
    """What the page puts where, read from its atlas (tools/parallax_regions.py, needs Pillow), on the 40 x 22.5 world
    of a 16:9 screen before any scroll. The faults r92 only found by looking at full-size renders (r129):
      (a) a layer's art touches its top edge without filling it (cropped by the painter) and that edge is on screen,
          not behind a nearer layer's solid band: the art shows cut flat;
      (b) a layer's art reaches its bottom edge, that edge is on screen and no nearer layer's solid band covers it;
          nor, a screen pixel or more of it at a time, their clear pixels (alpha < 128) at its height: a notch's tip
          between two front trees, or gaps in several layers that scroll over each other (r399);
      (c) a band of the screen that no layer and no gradient covers (a white gradient is the editor's default: unset);
      (d) regions packed with their whitespace stripped and useOriginalSize off: they are stretched over the layer;
      (e) a layer tiled on X whose left and right edges differ (seam > 20) in the rows on screen: a cut every repeat.
          A SEQUENCE layer without padX is checked at every join its cycle can make: each segment's right edge against
          each segment's left edge (itself included), segments weighing above 0 only. A join is a cut only when it
          also steps more than the art does between most of its own neighbour columns (3 in 4, in the same rows):
          hard alpha pixel art steps 27-75 from one column to the next everywhere, and loops as smoothly (r253). Nor is
          a join under 2 * SEAM stepping no more than 1.5 times the art does one column in, on either side: an
          outline carried across it (a tip on the edge, r398).
      (f) a layer tiled from a mipmapped atlas (its `filter:` a MipMap one) drawn 1/m of its region's size on this
          1280 px screen, whose region edges are copied fewer than m texels into the atlas padding (duplicatePadding):
          the mip levels it samples average in what lies past the copy, a line at every join (r268,
          tools/r268-mip-seams: 17-91/255 below m texels, at most 8.5 at m, none from 2m). A narrower screen shrinks it
          more. The editor copies 25 texels (padding 50).
    A layer counts as covering a band only if it spans the screen's width: tiled on X without padding, or 1 world
    wide or more. An EMPTY layer covers nothing: what its hook draws is the game's. A SEQUENCE layer is as high as its
    first segment makes it (docs/sequence-layers.md) and covers a row only as far as its least opaque segment does
    there; its top is cut (a) if any segment's is."""
    try:
        import parallax_regions as regions_tool
    except ImportError:  # no Pillow
        return ['layout not checked: tools/parallax_regions.py needs Pillow']
    atlas = os.path.join(ROOT, atlas_dir, page['pageModel']['atlasName'])
    if not os.path.exists(atlas):
        return [f'layout not checked: no {os.path.relpath(atlas, ROOT)}']
    regions = {(r['name'], r['pos']): r for r in regions_tool.read_atlas(atlas)}
    placed, problems = _measure_layers(page, regions, regions_tool)
    return problems + _edge_faults(placed) + _hole_faults(page, placed)


# A layer as layout() reads it: rows is the opaque share of each of its rows, bottom to top; bottom and height are in
# world units; spans says it covers the screen's width; cut_top that its art touches its top edge cut flat; clear the
# share of each of its image's rows, bottom to top, that is clear (alpha < 128) (None: not measured).
Placed = collections.namedtuple('Placed', 'index layer rows bottom height spans cut_top clear', defaults=(None,))


def _measure_layers(page, regions, regions_tool):
    """Each layer placed on the screen, and faults (d) and (e), which need its image."""
    original = page.get('useOriginalSize', False)
    sheets, placed, problems, stripped = {}, [], [], []
    for i, l in enumerate(layers(page)):
        if l.get('kind') == 'SEQUENCE':
            problems += _measure_sequence(page, i, l, regions, regions_tool, sheets, placed, stripped)
            continue
        if not has_image(l):  # it covers nothing: its hook or its particles draw there, never a solid band
            continue
        if l.get('kind') == 'SHADER' and l.get('shaderEffect') == 'FOG' and l.get('shaderAmplitude', 0) > 0:
            continue  # its opacity drifts: it covers nothing for sure

        r = regions.get((l['regionName'], l['regionPosition']))
        if r is None:
            problems.append(f"layer {i}: no region {l['regionName']}#{l['regionPosition']}"
                            f" in {page['pageModel']['atlasName']}")
            continue
        img = _image(page, i, l, r, regions_tool, sheets, stripped)
        p = _place(page, i, l, img)
        placed.append(p)
        problems += _mip_faults(page, i, l, [r], regions_tool, sheets)
        if page.get('repeatOnX', True):
            seam = _seam_on_screen(img, p, regions_tool)
            limit = _seam_limit([img], p)
            if _is_cut(seam, limit, img, img, p):
                problems.append(f"(e) layer {i} ({l['regionName']}#{l['regionPosition']}) is tiled on X but its left"
                                f" and right edges differ (seam {seam} > {_said(limit)}): a cut shows every repeat")
    if stripped and not original:
        problems.append(f'(d) layers {stripped} use regions packed with their whitespace stripped, and useOriginalSize'
                        ' is off: each is stretched over its whole layer')
    return placed, problems


def _image(page, i, l, r, regions_tool, sheets, stripped):
    """Region r as layer i draws it: notes it in stripped if its whitespace was stripped."""
    img = regions_tool.image_of(r, sheets)
    (w, h), (ow, oh), (ox, oy) = r['size'], r['orig'], r['offset']
    if (w, h) != (ow, oh) or (ox, oy) != (0, 0):
        if i not in stripped:
            stripped.append(i)
        if not page.get('useOriginalSize', False):  # the packed image alone, stretched over the whole layer
            img = img.crop((ox, oh - oy - h, ox + w, oh - oy))
    if l.get('flipY'):
        img = img.transpose(regions_tool.Image.FLIP_TOP_BOTTOM)
    return img


def _measure_sequence(page, i, l, regions, regions_tool, sheets, placed, stripped):
    """A SEQUENCE layer placed as its segments together make it, and its joins (e). A missing region is
    sequence_faults' to say."""
    segments = [s for s in l.get('sequenceSegments') or []]
    weighed = [s for s in segments if s.get('weight', 1) > 0] or segments
    images = []
    for s in weighed:
        r = regions.get((s['regionName'], s.get('regionPosition', 0)))
        if r is None:
            return []
        images.append((s['regionName'], _image(page, i, l, r, regions_tool, sheets, stripped)))
    if not images:
        return []
    first = regions.get((segments[0]['regionName'], segments[0].get('regionPosition', 0)))
    if first is None:
        return []
    problems = _mip_faults(page, i, l, [regions[(s['regionName'], s.get('regionPosition', 0))] for s in weighed],
                           regions_tool, sheets)
    each = [_place(page, i, l, img) for _, img in images]
    height = _place(page, i, l, _image(page, i, l, first, regions_tool, sheets, stripped)).height
    rows = [min(_resampled(p.rows, k, len(each[0].rows)) for p in each) for k in range(len(each[0].rows))]
    cut = any(p.cut_top and p.rows[-1] < SOLID for p in each)
    clear = [max(_resampled(p.clear, k, len(each[0].clear)) for p in each) for k in range(len(each[0].clear))]
    p = each[0]._replace(rows=rows, height=height, cut_top=cut, clear=clear)
    placed.append(p)
    if not page.get('repeatOnX', True) or l['padX'] > 0:
        return problems
    limits = {a: _seam_limit([img], p) for a, img in images}
    joins = [f'{a}>{b} {seam}' for a, left in images for b, right in images
             for seam in [_join_on_screen(left, right, p)] if _is_cut(seam, max(limits[a], limits[b]), left, right, p)]
    if not joins:
        return problems
    limit = SEAM if all(v == SEAM for v in limits.values()) else f"{SEAM} and its segments' own step between columns"
    return problems + [(f"(e) layer {i} ({label(l)}) chains segments whose edges differ (seam > {limit}), a cut at each"
                       f" such join: {', '.join(joins)} (left>right seam)")]


def _resampled(rows, k, n):
    """Row k of n, read from rows (bottom to top) of another length."""
    return rows[min(len(rows) - 1, k * len(rows) // n)]


def _join_on_screen(left, right, p):
    """The mean step on screen (0-255, parallax_regions.step) between left's right column and right's left
    column, both at the layer's height, in the rows on screen, as parallax_regions.measure takes a region's seam."""
    low, high = max(0.0, -p.bottom / p.height), min(1.0, (SCREEN_H - p.bottom) / p.height)
    if high <= low:
        return 0
    from parallax_regions import step
    h = min(200, max(left.height, right.height))
    a = left.crop((left.width - 1, 0, left.width, left.height)).resize((1, h)).load()
    b = right.crop((0, 0, 1, right.height)).resize((1, h)).load()
    edge = []
    for y in range(round((1 - high) * h), round((1 - low) * h)):
        if a[0, y][3] > 16 or b[0, y][3] > 16:
            edge.append(step(a[0, y], b[0, y]))
    return round(sum(edge) / len(edge)) if edge else 0


def _mip_faults(page, i, l, rs, regions_tool, sheets):
    """(f): each region of a tiled layer, from a mipmapped atlas, whose edges are copied into the padding fewer texels
    than the layer shrinks it on screen."""
    if not any(r['filter'].startswith('MipMap') for r in rs):
        return []
    sides = (['left', 'right'] if page.get('repeatOnX', True) and l.get('padX', 0) == 0 else []) + \
            (['bottom', 'top'] if page.get('repeatOnY', False) and l.get('padY', 0) == 0 else [])
    if not sides:
        return []
    out = []
    for r in rs:
        # A rotated or stripped region is left alone: its packed edge is not the layer's edge.
        if r.get('rotate') == 'true' or r['size'] != r['orig']:
            continue
        shrink = r['size'][0] / (SCREEN_PX * l['sizeRatio'])
        if shrink <= 1:
            continue
        copied = min(_copied(regions_tool, sheets, r, side) for side in sides)
        if copied < shrink:
            out.append(f"(f) layer {i} ({r['name']}#{r['pos']}) is tiled from a mipmapped atlas ({r['filter']}) and"
                       f" drawn 1/{shrink:.1f} of its size, but its edges are copied only {copied} texel(s) into the"
                       f" padding (needs {math.ceil(shrink)}): a line at every join; pack with duplicatePadding and"
                       " a padding twice that, or a Linear filter")
    return out


def _copied(regions_tool, sheets, r, side, most=64):
    """How many texels past the region's edge on that side repeat its edge column or row, up to most. A texel repeats
    its edge texel when their premultiplied RGBA (the colour under alpha 0 is never seen) are within 32/255, or within
    the art's own step from that edge texel to the one inside it: the editor's PixmapPacker stretches the edge with
    Pixmap's BiLinear filter, so its copy blends in the next texels, and a few of its texels are off further (city_1.png:
    4 of 272 along a top edge, alpha 65 for 255): a column or row is copied when 95% of its texels are. The sheet's own edge counts as copied all the way: the mip levels have nothing past it."""
    sheet = sheets.setdefault(r['page'], regions_tool.Image.open(r['page']).convert('RGBA'))
    (x, y), (w, h) = r['xy'], r['size']
    if side in ('left', 'right'):
        edge = x if side == 'left' else x + w - 1
        step = -1 if side == 'left' else 1
        cut = lambda c: sheet.crop((c, y, c + 1, y + h))  # noqa: E731
        limit = sheet.width
    else:  # libGDX's y runs down the sheet: a region's top on screen is its first row
        edge = y + h - 1 if side == 'bottom' else y
        step = 1 if side == 'bottom' else -1
        cut = lambda c: sheet.crop((x, c, x + w, c + 1))  # noqa: E731
        limit = sheet.height

    def seen(c):
        raw = cut(c).tobytes()
        return [(raw[k] * raw[k + 3] / 255, raw[k + 1] * raw[k + 3] / 255, raw[k + 2] * raw[k + 3] / 255, raw[k + 3])
                for k in range(0, len(raw), 4)]

    def apart(p, q):
        return max(abs(p[n] - q[n]) for n in range(4))

    ref = seen(edge)
    inner = seen(edge - step) if 0 <= edge - step < limit else ref
    tolerance = [max(32, apart(p, q)) for p, q in zip(ref, inner)]
    for k in range(1, most + 1):
        c = edge + step * k
        if not 0 <= c < limit:
            return most
        off = sum(1 for p, q, t in zip(seen(c), ref, tolerance) if apart(p, q) > t)
        if off > 0.05 * len(ref):
            return k - 1
    return most


def _opaque(full, img, y):
    """The share of row y of img that is opaque, every other pixel; full is img's alpha channel, loaded."""
    return sum(1 for x in range(0, img.width, 2) if full[x, y] > 16) / len(range(0, img.width, 2))


def _place(page, i, l, img):
    small = img.resize((min(img.width, 400), min(img.height, 200)))
    alpha = small.getchannel('A').load()
    rows = [sum(1 for x in range(small.width) if alpha[x, y] > 16) / small.width
            for y in range(small.height - 1, -1, -1)]  # rows[0] is the bottom row
    full = img.getchannel('A').load()
    # Art cut by the painter goes on at the edge as wide as just inside it; a crest that touches it narrows fast.
    top, inside = _opaque(full, img, 0), _opaque(full, img, round(0.02 * (img.height - 1)))
    cut_top = top > 0.002 and top >= 0.7 * inside
    width = SCREEN_W * l['sizeRatio']
    bottom = l['decal_Y_Ratio'] * SCREEN_H / 100
    spans = page.get('repeatOnX', True) and l['padX'] <= 0 or width >= SCREEN_W
    return Placed(i, l, rows, bottom, width * img.height / img.width, spans, cut_top, _clear(img))


def _clear(img):
    """The share of each row of img, bottom to top, that is clear (alpha < 128: more of what is behind shows than of the
    pixel). A notch's tip between two trees leaves a few pixels in rows 99% opaque (r381); antialiased art's soft
    pixels (alpha 169-200 in Hiver's hills) are not clear, nor a mist's rows (180-192 in r6a s07's l1)."""
    alpha = img.getchannel('A')
    return [sum(alpha.crop((0, y, img.width, y + 1)).histogram()[:128]) / img.width
            for y in range(img.height - 1, -1, -1)]


def _rows_on_screen(img, p):
    """The rows of img, the layer p draws, that are on screen; None when none is."""
    low, high = max(0.0, -p.bottom / p.height), min(1.0, (SCREEN_H - p.bottom) / p.height)
    rows = img.crop((0, round((1 - high) * img.height), img.width, round((1 - low) * img.height)))
    return rows if high > low and rows.height else None


def _column_steps(img):
    """The mean step on screen (0-255, parallax_regions.step) between neighbour columns of img, as
    parallax_regions.measure takes the seam between its edges, at up to 256 places across it."""
    from parallax_regions import step
    w, h = img.size
    px = img.load()
    rows = range(0, h, max(1, h // 200))
    xs = range(w - 1) if w <= 257 else sorted({round(k * (w - 2) / 255) for k in range(256)})
    steps = []
    for x in xs:
        edge = [step(a, b) for y in rows for a, b in [(px[x, y], px[x + 1, y])]
                if a[3] > 16 or b[3] > 16]
        if edge:
            steps.append(sum(edge) / len(edge))
    return steps


def _seam_limit(images, p):
    """How far a join of these images may step before it is a cut: SEAM, or more where the art itself steps more
    between its neighbour columns: as much as 3 in 4 of them do, in the rows on screen (r253). Not their median: a
    pixel-art strip's own joins step past it 1 time in 2, and one of them rolled to the edge is no cut."""
    steps = sorted(s for img in images for rows in [_rows_on_screen(img, p)] if rows for s in _column_steps(rows))
    return max(SEAM, round(steps[int(0.75 * len(steps))])) if steps else SEAM


def _is_cut(seam, limit, left, right, p):
    """A join of left's right edge to right's left edge stepping past limit is a cut, unless it is a small one (under
    2 * SEAM) that steps no more than half again what the art steps one column in, on either side: an outline that
    climbs or falls there and carries on across the join (r398: a pine's tip on the forest's edge, 21 beside 17). A
    cut steps from columns that do not (q01's ground, 141-255 beside 0-2), and a big one is a cut whatever is beside
    it (g03's stem, ending at the edge: 56 beside 128)."""
    if seam <= limit:
        return False
    if seam >= 2 * SEAM:
        return True
    return seam > 1.5 * max(_step_beside(left, left.width - 2, p), _step_beside(right, 0, p))


def _step_beside(img, x, p):
    """The step between columns x and x + 1 of img, in the rows on screen, as _column_steps takes it."""
    rows = _rows_on_screen(img.crop((x, 0, x + 2, img.height)), p)
    return max(_column_steps(rows) or [0]) if rows else 0


def _said(limit):
    """The limit a seam was held to, as the fault says it."""
    return str(limit) if limit == SEAM else f"{limit}, the art's own step between columns"


def _seam_on_screen(img, p, regions_tool):
    """Only the rows on screen: a layer sunk below it hides a mismatch in its lower rows (r138, spring's hills)."""
    low, high = max(0.0, -p.bottom / p.height), min(1.0, (SCREEN_H - p.bottom) / p.height)
    rows_on_screen = img.crop((0, round((1 - high) * img.height), img.width, round((1 - low) * img.height)))
    return regions_tool.measure(rows_on_screen)['seam'] if high > low and rows_on_screen.height else 0


def _row_at(p, y):
    """The share of the layer's row at world height y that is opaque, None off the layer."""
    if not p.bottom <= y < p.bottom + p.height:
        return None
    return p.rows[min(len(p.rows) - 1, int((y - p.bottom) / p.height * len(p.rows)))]


def _covered_by_nearer(placed, k, y):
    """The nearer layers together hide that row: each one's opaque share, as if they overlapped at random."""
    clear = 1.0
    for p in placed[k + 1:]:
        if p.spans:
            clear *= 1 - (_row_at(p, y) or 0)
    return clear <= 1 - COVERED


def _edge_seen(placed, k):
    """How many screen pixels of layer k's bottom edge show, on average, through the nearer spanning layers: across
    the screen's width, the share each one leaves clear in its rows within NOTCH_MARGIN of the edge (its clearest row;
    a SEQUENCE's clearest segment), multiplied as if they overlapped at random, since they scroll at other speeds. A
    notch's tip shows the edge (r381: 4-7 px, R1 s03 and R6a s05); Hiver's hills' single clear pixels (0.4 px) do not
    (r399). 0 when a nearer layer was not measured."""
    edge, seen = placed[k].bottom, float(SCREEN_PX)
    for p in placed[k + 1:]:
        if not p.spans:
            continue
        if p.clear is None:
            return 0.0
        n = len(p.clear)
        low, high = ((edge - NOTCH_MARGIN - p.bottom) / p.height * n, (edge + NOTCH_MARGIN - p.bottom) / p.height * n)
        if low >= 0 and high <= n:
            seen *= max(p.clear[int(low):max(int(low) + 1, math.ceil(high))])
    return seen


def _on_screen(y):
    return EDGE < y < SCREEN_H - EDGE


def _pct(y):
    return round(100 * y / SCREEN_H)


def _edge_faults(placed):
    """Faults (a) and (b): a layer's top or bottom edge that shows."""
    problems = []
    for k, p in enumerate(placed):
        name = label(p.layer)
        top = p.bottom + p.height
        # At the edge itself, not half this layer's row inside it: a nearer pixel-art layer's rows are finer, and
        # the row half ours inside may be thinner or more solid than the one on the edge (r281 bottom, r282 top).
        if p.cut_top and p.rows[-1] < SOLID and _on_screen(top) and not _covered_by_nearer(placed, k, top - EDGE_PROBE):
            problems.append(f'(a) layer {p.index} ({name}): its art touches its top edge without filling it, and that'
                            f' edge is on screen at {_pct(top)}% of its height: the art shows cut flat')
        if p.rows[0] > 0 and _on_screen(p.bottom) and not _covered_by_nearer(placed, k, p.bottom + EDGE_PROBE):
            problems.append(f'(b) layer {p.index} ({name}): its art reaches its bottom edge, on screen at'
                            f' {_pct(p.bottom)}% of its height, and no nearer layer covers that edge')
        elif p.rows[0] > 0 and _on_screen(p.bottom) and (seen := _edge_seen(placed, k)) >= 1:
            problems.append(f'(b) layer {p.index} ({name}): its bottom edge, on screen at {_pct(p.bottom)}% of its'
                            f' height, shows through the clear pixels of the nearer layers at that height, about'
                            f' {seen:.0f} screen px of it at a time: what is behind shows under it there, as at a'
                            ' notch\'s tip between trees (r381)')
    return problems


def _hole_faults(page, placed):
    """Fault (c): the bands of the screen nothing covers."""
    sky_from = page.get('topHalfSize', 0.5) * SCREEN_H if gradient_set(page, 'topHalf_top', 'topHalf_bottom') else None
    ground_to = (1 - page.get('bottomHalfSize', 0.5)) * SCREEN_H \
        if gradient_set(page, 'bottomHalf_top', 'bottomHalf_bottom') else None
    empty, steps = [], 225
    for j in range(steps):
        y = (j + 0.5) * SCREEN_H / steps
        if sky_from is not None and y >= sky_from or ground_to is not None and y <= ground_to:
            continue
        if any(p.spans and (_row_at(p, y) or 0) > 0 for p in placed):
            continue
        if empty and empty[-1][1] == j:
            empty[-1][1] = j + 1
        else:
            empty.append([j, j + 1])
    problems = []
    for a, b in empty:
        if b - a >= 2:  # a band thinner than a hundredth of the screen is a row rounding, not a hole
            problems.append(f'(c) the screen from {round(100 * a / steps)}% to {round(100 * b / steps)}% of its height'
                            ' (from the bottom) is covered by no layer and no gradient')
    return problems


def describe(page):
    out = []
    sky = (f"{hex_of(page.get('topHalf_bottom'))}->{hex_of(page.get('topHalf_top'))}"
           f" ({page.get('topHalfSize', 0.5):.2f} uncovered)")
    ground = (f"{hex_of(page.get('bottomHalf_top'))}->{hex_of(page.get('bottomHalf_bottom'))}"
              f" ({page.get('bottomHalfSize', 0.5):.2f} uncovered)")
    out.append(f"  atlas {page['pageModel']['atlasName']}  repeat X={page.get('repeatOnX')} Y={page.get('repeatOnY')}"
               f"  sky {sky}  ground {ground}")
    prev = None
    for i, l in enumerate(layers(page)):
        sx = l['parallaxScalingSpeedX']
        step = '' if not prev else f'x{sx / prev:.2f}'
        prev = sx or prev
        region = label(l)
        y_over_x = l['parallaxScalingSpeedY'] / sx if sx else 0
        marks = (' flip' if l['flipX'] else '') + (' mirror' if l.get('mirror') and has_image(l) else '')
        out.append(f"  {i:2d} {region:<16} speed {sx:.4f} {step:<6} y/x {y_over_x:.2f} rest {l['speedXAtRest']:6.1f}"
                   f" size {l['sizeRatio']:5.2f} decal {l['decal_X_Ratio']:6.1f},{l['decal_Y_Ratio']:6.1f}{marks}")
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


def deepen_back(page, factor):
    """Keeps the front speed and multiplies the span (in log space) by factor: only the layers behind it move."""
    p = copy.deepcopy(page)
    s = speeds(p)
    front = max(s)
    set_speeds(p, [front * (v / front) ** factor for v in s])
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


# ---------------------------------------------------------------- pages written from the art (r92)
#
# Round 1's pages read as random values (Simon, r92): they were placed from rules, not from what each region shows.
# These are written after looking at every region (tools/parallax_regions.py) and measuring two things:
#  - what kind of region it is. A FULL-FRAME region (1920x1080, or a 1080-tall panorama) was painted for one screen
#    height, its art already at the height it belongs: it goes at its natural size (its height = the screen's) and
#    decal Y 0, and the regions of one atlas compose as the painter drew them. A STRIP (Hiver's 3645x580) is one band
#    of the scene: strips are stacked, farther ones higher, each bottom edge under the solid part of the one in front.
#  - how far each layer is, from the art. The same kind of thing (a tree, a hill) drawn at half the size is twice as
#    far, and moves half as fast: speed ratio between two layers = size ratio of what they show. The sky and the
#    mountains are so far that they barely move.

WORLD_W, WORLD_H = 40.0, 22.5


def natural_size(orig_w, orig_h):
    """The sizeRatio at which a region is exactly one 16:9 screen tall."""
    return round(orig_w / orig_h * WORLD_H / WORLD_W, 3)


def fairy_from_art(ratio=4 / 3, front=0.1):
    """PurpleFairy: seven full-frame 1920x1080 regions, back to front 7 (sky and far wave) 6 (far hills) 5 (bare
    trees, far) 4 (canopy trees) 3 (pink trees) 2 (grass and butterflies) 1 (front grass). All at size 1 and decal Y 0:
    together they are the picture the painter drew. Speeds a constant ratio: the tree rows shrink by about a third
    per row (5 -> 4 -> 3). PurpleFairy-loop.atlas is the 2019 sheet with region 3's edges reworked so it tiles: the
    web's rays thinned out at its left edge, the cut leaf closed at its right (parallax-ai r40; the original's seam
    is 74)."""
    order = ['7', '6', '5', '4', '3', '2', '1']
    stagger = [0, 37, 71, 13, 53, 89, 29]
    L = [layer(name, speed=front / ratio ** (len(order) - 1 - i), size=1.0, dx=stagger[i], dy=0)
         for i, name in enumerate(order)]
    return page_of('PurpleFairy-loop.atlas', L, sky=('4b1f52', '6d2975'), ground=('6d2975', '4b1f52'), original_size=True)


def hiver_from_art(front=0.08):
    """Hiver: four strips 580 px tall. p3 the sky (pink streaks at its top), p1 the far mountains (the ski lift),
    p2 the near snow hills (art floats between 14% and 76% of the strip), p0 snowy fir trees (trunks at the bottom).
    The snow under the hills is the bottom gradient, in the hills' own bottom colour, so the screen has no white hole.
    The tallest fir touches the top of the trees' strip (cropped by the painter), so wherever that strip's top edge is
    on screen the tree shows cut flat: the trees are the foreground, big enough (4.6) that the strip's top is above
    the screen."""
    snow, sky_low, sky_high = 'e0e8dd', 'aaa8be', '9d98b4'
    # The mountains' peaks touch the top of their strip (the painter cropped them): its top edge goes at the top of
    # the screen, so no peak shows cut flat. The hills' solid band (14-35% of their strip) covers the mountains' bottom
    # edge and the tree row's trunks; below the hills' art, the snow is the bottom gradient.
    L = [
        layer('parallax4', 3, speed=front / 16, size=2.6, dx=0, dy=26),
        layer('parallax4', 1, speed=front / 8, size=2.6, dx=23, dy=26),
        layer('parallax4', 2, speed=front / 3, size=1.8, dx=61, dy=11),
        layer('parallax4', 0, speed=front, size=4.6, dx=-12, dy=-3),
    ]
    page = page_of('Hiver.atlas', L, sky=(sky_high, sky_low), ground=(snow, snow), top_size=0.5, bottom_size=0.78,
                   original_size=True)
    return page


def calm_tree_from_art(front=0.1):
    """calmTree3 (a misty forest): 1080-tall regions, so all at their natural size and decal Y 0. Back to front:
    9em plan (the sky and a far pale ridge, 5 screens wide), 7em plan (pale trunks hanging from the top: the far
    forest), 6em plan (a green hill with small trees), Parallax 7 (pale trunks, nearer), 5em plan V2 (mist, drifting),
    Work2 (the forest floor with mushrooms, 8 screens wide), 4em plan M1 (the foreground trunks and grass). The trunks
    widen about x1.6 from 7em to Parallax 7 and x2.5 again to M1."""
    n = natural_size
    L = [
        layer('9em plan', speed=front / 20, size=n(9600, 1080), dx=0, dy=0),
        layer('7em plan', speed=front / 8, size=n(9600, 1080), dx=31, dy=0),
        layer('6em plan', speed=front / 6, size=n(9600, 1080), dx=57, dy=0),
        layer('Parallax 7', speed=front / 4, size=n(2012, 1080), dx=13, dy=0),
        layer('5em plan V2', speed=front / 3, size=n(2307, 1080), dx=0, dy=0, rest=-10),
        layer('Work2', speed=front / 2, size=n(15580, 1080), dx=43, dy=0),
        layer('4em plan M1', speed=front, size=n(2021, 1080), dx=7, dy=0),
    ]
    return page_of('calmTree3.atlas', L, sky=('b7ccd5', 'a7c4ce'), ground=('435853', '2f3f3b'), original_size=True)


def night_from_art(front=0.08):
    """OneNight: its grounds are 1920x1080 PIECES, not one frame cut in planes: each hill starts at the bottom of its
    canvas, so at size 1 and decal Y 0 they pile up with every tree the full screen tall. They are stacked instead,
    farther ones higher, and kept at least 0.8 wide (round 1's 0.5 showed each hill twice a screen). Clouds far behind
    the mountain, drifting; dark water in front, flowing against the scroll. Simon's sky colours."""
    L = [
        layer('clouds', 1, speed=front / 10, size=1.0, dx=40, dy=25, rest=40),
        layer('clouds', 0, speed=front / 8, size=1.0, dx=0, dy=0, rest=30),
        layer('rocks', 0, speed=front / 5, size=0.9, dx=17, dy=18),
        layer('ground', 0, speed=front / 3, size=0.8, dx=63, dy=20),
        layer('ground', 1, speed=front / 2, size=0.8, dx=29, dy=8),
        layer('ground', 2, speed=front / 1.4, size=1.0, dx=81, dy=-5),
        layer('waterDark', 0, speed=front, size=1.0, dx=0, dy=-12, rest=-15),
    ]
    # The mountain's glow is translucent: whatever gradient is behind it shows through, so one sky gradient covers
    # the whole screen (two would draw their seam across the mountain).
    return page_of('OneNight.atlas', L, sky=('12537c', 'a4c1ed'), top_size=0.0, bottom_size=1.0, original_size=True)


def calm_from_art(front=0.07):
    """calm: six strips 5000 px wide, stacked. Farthest to nearest by haze (Trees_close is the palest tree row, so
    the farthest). The three tree rows' crowns grow about x1.3 row to row."""
    L = [
        layer('Clouds', speed=front / 12, size=1.0, dx=15, dy=62, rest=40),
        layer('Mountains_big', speed=front / 8, size=1.15, dx=0, dy=42),
        layer('Mountains_small', speed=front / 5, size=1.2, dx=35, dy=33),
        layer('Trees_close', speed=front / 1.7, size=1.2, dx=10, dy=24),
        layer('Trees_far', speed=front / 1.3, size=1.3, dx=55, dy=10),
        layer('Trees_fartest', speed=front, size=1.5, dx=25, dy=0),
    ]
    return page_of('calm.atlas', L, sky=('00a6ff', 'f5f5f5'), ground=('05533f', '05533f'), original_size=True)


def printemps_from_art(front=0.1):
    """Printemps (r128, written from the skill, version 2): five strips 3645x580 (6.28:1), packed stripped. Back to
    front by haze: p4 a peach wash (solid), p3 grey hills (art 24-82%, seam 100), p2 the field with pink trees (solid to
    44%, seam 60), p1 telegraph poles (their wires touch the strip's top: the top goes above the screen), p0 flowers.
    p3 and p2 do not tile cleanly: they are made wide (2.6 worlds), so a seam passes rarely. The poles are the big
    near layer; the speed ratio x1.6 a layer from the field forward, the hills and wash far behind."""
    L = [
        layer('parallax1', 4, speed=front / 25, size=2.6, dx=0, dy=30),
        layer('parallax1', 3, speed=front / 10, size=2.6, dx=41, dy=4),
        layer('parallax1', 2, speed=front / 1.6 ** 2, size=2.6, dx=17, dy=0),
        layer('parallax1', 1, speed=front / 1.6, size=4.0, dx=63, dy=-5),
        layer('parallax1', 0, speed=front, size=2.0, dx=29, dy=-8),
    ]
    return page_of('Printemps.atlas', L, sky=('eec9b6', 'eec9b6'), ground=('bcc5b9', 'bcc5b9'), original_size=True)


def boss_from_art(front=0.1):
    """boss (r128, from the skill): calm's six strips redrawn as dark silhouettes against a fire-lit sky (the clouds'
    undersides glow orange), at their own sizes (its tree strips are taller than calm's). Back to front as calm: Clouds,
    Mountains_big, Mountains_small, Trees_close (the least dark), Trees_far, Trees_fartest (the biggest trees). Each
    bottom edge under the solid band of the strip in front, the clouds' too. Deep, as round 2 graded calm best at 41x: 25x here."""
    L = [
        # The clouds' underside is a flat orange rim across the whole strip: on screen it reads as a line drawn across
        # the sky, so it sinks behind the big mountains' solid band (to 12.3 world units up).
        layer('Clouds', speed=front / 25, size=1.2, dx=15, dy=52, rest=25),
        layer('Mountains_big', speed=front / 10, size=1.0, dx=0, dy=29),
        layer('Mountains_small', speed=front / 5, size=1.0, dx=35, dy=20),
        layer('Trees_close', speed=front / 2.2, size=1.2, dx=10, dy=12.5),
        layer('Trees_far', speed=front / 1.4, size=1.2, dx=55, dy=4.5),
        layer('Trees_fartest', speed=front, size=1.3, dx=25, dy=-4.5),
    ]
    # One gradient over the whole screen, dark above to the fire's orange at the horizon.
    return page_of('boss.atlas', L, sky=('120202', 'd8662a'), top_size=0.0, bottom_size=1.0, original_size=True)


def pure_from_art(ratio=1.6, front=0.1):
    """pure (r128, from the skill): a bright day. Strips, back to front by haze: Sky (a solid blue strip, 3.5:1),
    Clouds, Mountains_big (palest), Mountains_small, then three tree rows, palest first (Trees_close, Trees_far,
    Trees_fartest), the road and the front grass. Each bottom edge under the solid band of the strip in front. Left
    out: crasygrass and grass_back do not tile (seam 146, 167); feuille_fond and feuille_vert are walls of foliage
    taller than the forest, which they would hide; idkman is a second road. Speeds a constant ratio a layer from the
    front, the clouds drifting."""
    order = [  # region, size, decal Y %
        ('Sky', 1.2, 40), ('Clouds', 1.0, 62), ('Mountains_big', 1.0, 31), ('Mountains_small', 1.1, 22),
        ('Trees_close', 1.3, 24), ('Trees_far', 1.2, 11), ('Trees_fartest', 1.3, 2), ('road', 1.0, -7),
        ('grass', 1.0, -15),
    ]
    stagger = [0, 37, 71, 13, 53, 89, 29, 61, 7]
    n = len(order)
    L = [layer(r, speed=front / ratio ** (n - 1 - i), size=size, dx=stagger[i], dy=dy, rest=30 if r == 'Clouds' else 0)
         for i, (r, size, dy) in enumerate(order)]
    return page_of('pure.atlas', L, sky=('0165bb', '38d6ff'), ground=('032200', '032200'), original_size=True)


def find_atlas_dir(path, page):
    """The folder of a page's atlas: next to the page, else the one a round.json beside it names, else the one folder of
    core/test-data/samples that holds an atlas of that name. None when it is not found once."""
    name = page['pageModel']['atlasName']
    here = os.path.dirname(path)
    if os.path.exists(os.path.join(ROOT, here, name)):
        return here
    round_json = os.path.join(ROOT, here, 'round.json')
    if os.path.exists(round_json):
        with open(round_json, encoding='utf-8') as f:
            for scene in json.load(f)['scenes']:
                if os.path.normpath(scene['page']) == os.path.normpath(os.path.relpath(os.path.join(ROOT, path), ROOT)):
                    return scene['atlasDir']
    found = {os.path.relpath(os.path.dirname(a), ROOT)
             for a in glob.glob(os.path.join(ROOT, 'core/test-data/samples', '**', name), recursive=True)}
    return found.pop() if len(found) == 1 else None


def main(argv):
    if len(argv) < 2 or argv[1] in ('-h', '--help'):
        print(__doc__)
        return 0
    cmd = argv[1]
    if cmd == 'lint':
        bad = 0
        paths = argv[2:]
        atlas_dir = None
        if len(paths) > 1 and paths[0] == '--atlas':
            atlas_dir, paths = paths[1], paths[2:]
        for path in paths:
            page = load(path)
            print(path)
            print(describe(page))
            problems = lint(page, atlas_dir or find_atlas_dir(path, page))
            bad += bool(problems)
            print('\n'.join('  ! ' + p for p in problems) or '  ok')
        return 1 if bad else 0
    print('unknown command ' + cmd, file=sys.stderr)
    return 2


if __name__ == '__main__':
    sys.exit(main(sys.argv))
