#!/usr/bin/env python3
"""r273: writes the r273-wave-seam-y round's image and pages: stripes.png / stripes.atlas, black and white vertical
stripes 32 px apart (seamless on Y: every row is the same), and w15.jplax / w10.jplax, those stripes through WAVE
(amplitude 0.3), 20 of 40 units wide and 5 tall, tiling on Y only, over near-black gradients. w15: wavelength 1.5, which
does not divide the image's 5 units: a ripple taken inside one tile jumps at every tile edge on Y. w10: wavelength 1,
five to the image: no cut either way (the control).
Run from the repository root: python3 tools/r273-wave-seam-y/make.py (needs Pillow)."""
import json

from PIL import Image

HERE = 'tools/r273-wave-seam-y'
W, H = 512, 128
img = Image.new('RGBA', (W, H))
img.putdata([(255, 255, 255, 255) if (x // 32) % 2 == 0 else (0, 0, 0, 255) for y in range(H) for x in range(W)])
img.save(f'{HERE}/stripes.png')
open(f'{HERE}/stripes.atlas', 'w').write(f'''
stripes.png
size: {W}, {H}
format: RGBA8888
filter: Linear,Linear
repeat: none
stripes
  rotate: false
  xy: 0, 0
  size: {W}, {H}
  orig: {W}, {H}
  offset: 0, 0
  index: -1
''')
dark = {'r': 0.05, 'g': 0.07, 'b': 0.12, 'a': 1.0}
for name, wavelength in [('w15', 1.5), ('w10', 1.0)]:
    layer = {'regionName': 'stripes', 'regionPosition': 0, 'flipX': False, 'flipY': False,
             'parallaxScalingSpeedX': 0.03, 'parallaxScalingSpeedY': 0.03, 'speedXAtRest': 0.0,
             'sizeRatio': 0.5, 'decal_X_Ratio': 25, 'decal_Y_Ratio': 0,
             'padX': 0.0, 'padXFactor': 0.0, 'padY': 0.0, 'padYFactor': 0.0, 'mirror': False, 'name': 'stripes',
             'kind': 'SHADER', 'shaderEffect': 'WAVE', 'shaderAmplitude': 0.3, 'shaderWavelength': wavelength,
             'shaderSpeed': 0.0}
    json.dump({'topHalf_top': dark, 'topHalf_bottom': dark, 'topHalfSize': 0.5,
               'bottomHalf_top': dark, 'bottomHalf_bottom': dark, 'bottomHalfSize': 0.5,
               'repeatOnX': False, 'repeatOnY': True,
               'pageModel': {'atlasName': 'stripes.atlas', 'outside': False, 'pageList': [layer]},
               'useOriginalSize': True}, open(f'{HERE}/{name}.jplax', 'w'), indent=1)
