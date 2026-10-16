#!/usr/bin/env python3
"""A numpy copy of MIRROR's mirror (GdxLayerEffects.MIRROR, r455) on the beach sea strip's water, to choose a round
scene's sky: how much three wrong copies move the water, in 1/255 on the mean, with no GPU. A facet bends the water by
the ratio of the two sky colours it crosses, so a sky whose colours clamp every bend to white, or that is all one hue,
hides a wrong sky, a bend in colour (tint 0) or missing ripples under the frame checks' 2/255. Approximate: the water
alone at the shots' zoom, along() taken as local(), no swell moving the image; the shaders round's s14 measured about
0.84 of what this says.

    python3 tools/r455-mirror/model.py           the scores of s12's and s14's skies
    python3 tools/r455-mirror/model.py --search  300 random skies, the best 8 by their weakest mutant
Run from the repository root (needs numpy, Pillow)."""
import sys

import numpy as np
from PIL import Image

GREY = np.array([0.299, 0.587, 0.114])
WATER_ROWS = 84  # the strip's water: its bottom 84 of 384 rows
STRIP_UNITS = 15.0  # the strip's height in world units, the world's width (40) wide


def _smooth(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def mirror_of(s, sky, zenith, shine=1.0):
    hard = 0.5 - 0.4 * shine
    m = sky + (zenith - sky) * _smooth(0.5 - hard, 0.5 + hard, s)[..., None]
    m = m + (1 - m) * (shine * (1 - _smooth(-0.3, 0.1, s)))[..., None]
    return m * (1 - 0.5 * shine * _smooth(0.9, 1.4, s))[..., None]


def render(water, sky, zenith, amp=0.3, wavelength=1.25, depth=1.5, tint=1.0, ripples=1.0, strength=0.15, t=0.4):
    rows, cols = water.shape[:2]
    px = rows / (WATER_ROWS / 384 * STRIP_UNITS)
    y = (WATER_ROWS - np.arange(rows) - 0.5)[:, None] / px * np.ones((1, cols))
    x = np.arange(cols)[None, :] / px * np.ones((rows, 1))
    L = wavelength
    p1 = 2 * np.pi * ((x + 0.6 * y) / L - t)
    p2 = 2 * np.pi * (0.625 * x / L - 0.9 * y / L + 2 * t)
    qx, qy = x / (8 * L), y / (10 * L)
    fine = (0.9 * np.sin(2 * np.pi * (13 * qx + 41 * qy - 3 * t)) + 0.7 * np.sin(2 * np.pi * (-7 * qx + 57 * qy - 4 * t))
            + 0.5 * np.sin(2 * np.pi * (21 * qx + 83 * qy - 5 * t + 0.5 * np.sin(2 * np.pi * (5 * qx - 9 * qy + t)))))
    tilt = 2 * np.pi / L * amp * (0.18 * np.cos(p1) - 0.18 * np.cos(p2)) + ripples * amp * fine
    color = water[..., :3]
    lum = color @ GREY
    d = max(1, round(0.05 * px))
    relief = np.zeros_like(lum)
    relief[d:-d] = (lum[:-2 * d] - lum[2 * d:]) / (2 * d / px)
    level = (WATER_ROWS / px - y) / depth
    mirrored = mirror_of(level + 1.2 * (tilt + 0.5 * 3 * relief), sky, zenith)
    still = mirror_of(level, sky, zenith)
    bend = mirrored / np.maximum(still, 0.05)
    grey = (mirrored @ GREY) / np.maximum(still @ GREY, 0.05)
    bend = bend + (grey[..., None] - bend) * tint
    return color + (np.minimum(color * bend, 1) - color) * strength


def score(water, sky, zenith):
    base = render(water, sky, zenith)
    return {'sky white': np.abs(render(water, np.ones(3), zenith) - base).mean() * 255,
            'tint 0': np.abs(render(water, sky, zenith, tint=0.0) - base).mean() * 255,
            'no ripples': np.abs(render(water, sky, zenith, ripples=0.0) - base).mean() * 255}


def main():
    strip = np.asarray(Image.open('core/test-data/anim/beach-sea.png').convert('RGBA')).astype(float) / 255
    water = strip[-WATER_ROWS:]
    if '--search' in sys.argv:
        rng = np.random.default_rng(1)
        found = []
        for _ in range(300):
            sky, zenith = (rng.choice([0.1, 0.2, 0.35, 0.5, 0.7, 0.9], 3) for _ in range(2))
            s = score(water, sky, zenith)
            found.append((min(s.values()), tuple(sky), tuple(zenith), s))
        skies = sorted(found, key=lambda f: -f[0])[:8]
    else:
        skies = [(None, (0.81, 0.89, 0.95), (0.31, 0.56, 0.82), None), (None, (0.5, 0.1, 0.9), (0.2, 0.9, 0.2), None)]
    for _, sky, zenith, s in skies:
        s = s or score(water, np.array(sky), np.array(zenith))
        print(f'sky {sky} zenith {zenith}: ' + ', '.join(f'{k} {v:.2f}' for k, v in s.items()))


if __name__ == '__main__':
    main()
