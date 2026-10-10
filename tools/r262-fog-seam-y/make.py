#!/usr/bin/env python3
"""r262: writes y05.jplax, the control of the r262-fog-seam-y round: the shaders round's s07 (core/test-data/shaders/
make_round.py: an even slab through FOG, 20 of 40 units wide and 5 tall, tiling on Y) at wavelength 0.5, whose noise
repeats up every 5 units, the image's height: no cut either way.
Run from the repository root, after make_round.py: python3 tools/r262-fog-seam-y/make.py"""
import json

page = json.load(open('engines/godot/tests/shaders/s07.jplax'))
page['pageModel']['pageList'][0]['shaderWavelength'] = 0.5
json.dump(page, open('tools/r262-fog-seam-y/y05.jplax', 'w'), indent=1)
