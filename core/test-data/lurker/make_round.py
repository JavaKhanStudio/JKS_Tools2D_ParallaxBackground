#!/usr/bin/env python3
"""Writes the reader round engines/rounds/lurker (r370, docs/lurker-layers.md phase 3): r363's hooded figure behind
Hiver's pines (this folder's lab.jplax, its cover baked by tools/lurker-cover.sh), each scene starting START seconds into
the lab's scroll (every layer's decal moved by what it scrolls in that time) and every layer SPEEDUP times as fast, so
that the stills 6 s apart fall where the reader's sightings say and the figure shows plainly when in sight. Then checks each scene's stills against tools/r370-lurker/Sightings.java, the reader run without GL on the shots'
clock. Run from the repository root: python3 core/test-data/lurker/make_round.py (needs Java and the gradle build)."""
import json
import subprocess
import sys

from make_lab import DROP

HERE = 'core/test-data/lurker'
OUT = 'engines/rounds/lurker'
# Where the scenes start in the lab page's scroll, and how much faster it goes: its first sighting from 13 s (the figure
# plain at 15 s), the cover at 23.03 to 23.67 s, the next sighting from 23.67 s (plain from 28 s): the stills fall at
# 15.35, 23.35 and 31.35 s of the lab's scroll.
START, SPEEDUP = 15.35, 4 / 3
# r414 lowered the lab's layers (make_lab.py's DROP) and painted its gradients so the lab fills its frame; the round
# keeps the page it was graded on (r370, r371): s03's height and white gradients. The cover is the same either way.
WHITE = {'r': 1.0, 'g': 1.0, 'b': 1.0, 'a': 1.0}
SCENES = [
    ('l01', 2, 'seen-covered-gone',
     ("seed 2: the figure seen between two pines (0 s), hidden whole behind a trunk (6 s), gone at the next gap (12 s): "
      "seen in a sighting, gone at the next"),
     ['in-sight there', 'hidden gone', 'in-sight gone']),
    ('l02', 1, 'gone-covered-there',
     ("seed 1: the gap empty (0 s), the figure switched on behind a trunk (6 s, unseen), standing in the next gap (12 s): "
      "a sighting it was absent from steps the seed's draws"),
     ['in-sight gone', 'hidden there', 'in-sight there']),
]


def main():
    with open(f'{HERE}/lab.jplax') as f:
        lab = json.load(f)
    scenes = []
    for sid, seed, name, about, stills in SCENES:
        page = json.loads(json.dumps(lab))
        page.update({k: dict(WHITE) for k in ('topHalf_top', 'topHalf_bottom', 'bottomHalf_top', 'bottomHalf_bottom')})
        for layer in page['pageModel']['pageList']:
            layer['decal_Y_Ratio'] = round(layer['decal_Y_Ratio'] + DROP, 6)
            # The shots scroll at 60 units a second; a decal is in percent of the 40-wide world.
            layer['decal_X_Ratio'] = round(layer['decal_X_Ratio'] - 60 * START * layer['parallaxScalingSpeedX'] * 100 / 40, 4)
            layer['parallaxScalingSpeedX'] = round(layer['parallaxScalingSpeedX'] * SPEEDUP, 6)
            if layer.get('kind') == 'LURKER':
                layer['lurkerSeed'] = seed
                # What a game reveals it by; jME's BREAK=lurker reveals it every step.
                layer['name'] = 'hooded'
        with open(f'{OUT}/{sid}.jplax', 'w') as f:
            json.dump(page, f, indent=1)
        scenes.append({'id': sid, 'page': f'{OUT}/{sid}.jplax', 'atlasDir': HERE, 'name': name, 'about': about})
        check(sid, stills)
    with open(f'{OUT}/round.json', 'w') as f:
        json.dump({
            'round': 'lurker',
            'about': "LURKER layers' sightings (format 15, r370; docs/lurker-layers.md): the figure appears or vanishes "
                     "only while nothing of it is on screen, gone at the sighting after one where it was seen, else "
                     "there by its seed's draws, 1 in lurkerOdds (3). r363's demo, Hiver's pines in front, the cover "
                     "baked by LurkerCover (r369). jME runs core's ParallaxPageReader; Godot's copy is plax_lurker.gd (r371). "
                     "Written by core/test-data/lurker/make_round.py.",
            # The figure is 0.07% of a still: no mean threshold sees it. A wrong switch moves that many pixels by more
            # than 32; the readers agree to 0.00% (tools/compare-parallax-frames.py).
            'pixelsOff': 0.02,
            'scenes': scenes}, f, indent=1)
        f.write('\n')


def check(sid, stills):
    cp = subprocess.run(['./gradlew', '-q', '-I', 'tools/r178-particle-stress/classpath.gradle', ':core:printRuntimeClasspath'],
                        check=True, capture_output=True, text=True).stdout.strip().splitlines()[-1]
    run = subprocess.run(['java', '-Djava.awt.headless=true', '-cp', cp, 'tools/r370-lurker/Sightings.java', f'{OUT}/{sid}.jplax', HERE, '12'],
                         check=True, capture_output=True, text=True).stdout
    got = [' '.join(line.split()[3:]) for line in run.splitlines() if line.startswith('still ')]
    if got != stills:
        sys.exit(f'{sid}: the stills are {got}, not {stills}:\n{run}')
    print(f'{sid}: {", ".join(f"{t} s {s}" for t, s in zip((0, 6, 12), got))}')


if __name__ == '__main__':
    main()
