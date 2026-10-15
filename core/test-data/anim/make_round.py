#!/usr/bin/env python3
"""Writes the glint round's pages (r408, r427): engines/rounds/glint/g01.jplax and g02.jplax, r407's beach page (this
folder's beach.jplax and beach.atlas) with its sea, l1, alone and glinting as the animation lab drew it: the painted crests
lit in pieces that fade in place, in a sun's column in the view's middle. g01's sea through WAVE, g02's through FOG: the
glint comes after either. The wavelength is the lab's swell's (1.25), so the glint's waves are its turns. The water (the
strip's bottom 84 of 384 rows) lies in the world's bottom fifth, and the round zooms in on it (zoom 0.2, about the
view's bottom-left corner): at the page's own size it is 80 of 720 rows, and the glint moves a still by 0.02 / 255.
Run from the repository root: python3 core/test-data/anim/make_round.py."""
import json
from pathlib import Path

for out, effect, amplitude in [('g01', 'WAVE', 0.06), ('g02', 'FOG', 0.15)]:
    beach = json.loads(Path('core/test-data/anim/beach.jplax').read_text())
    sea = next(layer for layer in beach['pageModel']['pageList'] if layer['regionName'] == 'l1')
    sea.update(kind='SHADER', shaderEffect=effect, shaderAmplitude=amplitude, shaderWavelength=1.25, shaderSpeed=0.3,
               glintStrength=0.5, decal_Y_Ratio=2.7)
    beach['pageModel']['pageList'] = [sea]
    with open(f'engines/rounds/glint/{out}.jplax', 'w') as f:
        json.dump(beach, f, indent=1)
