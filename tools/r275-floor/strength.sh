#!/usr/bin/env bash
# strength.sh — how much the floor moves the floor round's stills (r275).
#
# The reader rounds fail past a mean 2/255 apart: a reader that drew a FLOOR flat would pass a scene the floor moves by
# less. Each scene's twin is the same page with its floor's top row at its bottom row's speed (floorTopSpeed =
# parallaxScalingSpeedX): every row then scrolls at one speed, unstretched, the image's rows as they are, and every
# layer in front is planted at that one speed: today's flat ground. This renders the round and its twins with libGDX
# (tools/parallax-lab-shots.sh, off screen) and prints, per still 6 and 12 s in, how far the floor is from its flat
# twin; it fails when one is under MIN (default 2.5, past the rounds' 2). Writes build/r275/strength/.
# headless: tools/parallax-lab-shots.sh renders in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
MIN="${MIN:-2.5}" OUT=build/r275/strength
rm -rf "$OUT"; mkdir -p "$OUT/flat"
python3 - "$OUT/flat" <<'PY' || exit 1
import json, os, sys
out = sys.argv[1]
round_ = json.load(open('engines/rounds/floor/round.json'))
def flat(path):
    page = json.load(open(path))
    for layer in page['pageModel']['pageList']:
        if layer.get('kind') == 'FLOOR':
            layer['floorTopSpeed'] = layer['parallaxScalingSpeedX']
    flat_path = os.path.join(out, os.path.basename(path))
    json.dump(page, open(flat_path, 'w'), indent=1)
    return flat_path
for scene in round_['scenes']:
    scene['page'] = flat(scene['page'])
    if 'transfer' in scene:
        scene['transfer']['page'] = flat(scene['transfer']['page'])
json.dump(round_, open(os.path.join(out, 'round.json'), 'w'), indent=1)
PY
tools/parallax-lab-shots.sh engines/rounds/floor "$OUT" >/dev/null || exit 1
tools/parallax-lab-shots.sh "$OUT/flat" "$OUT/flat" >/dev/null || exit 1
python3 - "$OUT" "$MIN" <<'PY'
import json, os, sys
from PIL import Image, ImageChops, ImageStat
out, least = sys.argv[1], float(sys.argv[2])
worst = None
for scene in json.load(open('engines/rounds/floor/round.json'))['scenes']:
    for t in (6, 12):
        name = f"{scene['id']}-t{t}.png"
        a = Image.open(os.path.join(out, name)).convert('RGB')
        b = Image.open(os.path.join(out, 'flat', name)).convert('RGB')
        mean = sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 3
        print(f'{name}  the floor moves it from its flat twin by {mean:.2f} / 255')
        worst = mean if worst is None else min(worst, mean)
print(('PASS' if worst >= least else 'FAIL') + f': weakest {worst:.2f} (at least {least})')
sys.exit(0 if worst >= least else 1)
PY
