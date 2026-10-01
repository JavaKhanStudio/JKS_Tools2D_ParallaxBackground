#!/usr/bin/env python3
"""r186: a page written by following the parallax-pages skill with an EMPTY layer, and the round that renders it.

hiver_from_art (lint-clean) with an EMPTY layer 'birds' between the mid hills (0.0267) and the front (0.08): a flock
the game draws at that depth. Scene h01 hooks it with an atlas region, h02 leaves it unhooked: the page alone shows
nothing there. Writes page.jplax and round.json next to this file; then
    python3 tools/parallax_lab.py lint tools/r186-empty-page/page.jplax     # must say ok
    tools/parallax-lab-shots.sh tools/r186-empty-page build/lab/r186
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
import parallax_lab as lab  # noqa: E402

page = lab.hiver_from_art()
# Speed between its neighbours (x1.7 then x1.8), 0.25 of the world each way (10 x 5.6 units), a tile every 16 units.
lab.layers(page).insert(3, lab.empty('birds', speed=0.045, size=0.25, dx=20.0, dy=55.0, pad_x=6.0))
with open(os.path.join(HERE, 'page.jplax'), 'w', encoding='utf-8') as f:
    json.dump(page, f, indent=1)
rel = os.path.relpath(os.path.join(HERE, 'page.jplax'), lab.ROOT)
scene = {'page': rel, 'atlasDir': 'core/test-data/samples'}
round_json = {'round': 'r186-empty-page', 'about': __doc__.splitlines()[0], 'scenes': [
    dict(scene, id='h01', name='hooked', about='the birds slot drawn by a hook (parallax4#2 stretched over each tile)',
         hooks={'birds': {'region': 'parallax4', 'position': 2}}),
    dict(scene, id='h02', name='unhooked', about='the same page, no hook: nothing at the slot, as a render of the page'
         ' alone shows it'),
]}
with open(os.path.join(HERE, 'round.json'), 'w', encoding='utf-8') as f:
    json.dump(round_json, f, indent=1)
