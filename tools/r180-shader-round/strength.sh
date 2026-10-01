#!/usr/bin/env bash
# strength.sh [ROUND_DIR] — how much the SHADER layers of a round change its libGDX stills (r180).
#
# The reader rounds fail past a mean 2/255 apart: a reader that drew a SHADER layer as a plain image would pass a scene
# whose effects move its stills by less. This renders the round twice with libGDX (tools/parallax-lab-shots.sh, off
# screen), as written and with every SHADER layer turned into an IMAGE, and prints the mean difference per still; it
# fails when one is under MIN (default 2.5, past the rounds' 2). Writes build/r180/strength/.
set -u
cd "$(dirname "$0")/../.." || exit 1
ROUND="${1:-engines/godot/tests/shaders}" MIN="${MIN:-2.5}" OUT=build/r180/strength
rm -rf "$OUT"; mkdir -p "$OUT/plain-round"
python3 - "$ROUND" "$OUT/plain-round" <<'PY'
import json, os, sys
round_dir, out = sys.argv[1], sys.argv[2]
r = json.load(open(os.path.join(round_dir, 'round.json')))
for scene in r['scenes']:
    page = json.load(open(scene['page']))
    for layer in page['pageModel']['pageList']:
        if layer.get('kind') == 'SHADER':
            layer['kind'] = 'IMAGE'
    scene['page'] = os.path.join(out, os.path.basename(scene['page']))
    json.dump(page, open(scene['page'], 'w'))
json.dump(r, open(os.path.join(out, 'round.json'), 'w'))
PY
tools/parallax-lab-shots.sh "$ROUND" "$OUT/shaded" >/dev/null || exit 1
tools/parallax-lab-shots.sh "$OUT/plain-round" "$OUT/plain" >/dev/null || exit 1
python3 - "$OUT" "$MIN" <<'PY'
import glob, os, sys
from PIL import Image, ImageChops, ImageStat
out, least = sys.argv[1], float(sys.argv[2])
worst = None
for shaded in sorted(glob.glob(os.path.join(out, 'shaded', '*-t*.png'))):
    name = os.path.basename(shaded)
    a = Image.open(shaded).convert('RGB')
    b = Image.open(os.path.join(out, 'plain', name)).convert('RGB')
    mean = sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 3
    print(f'{name}  effects move it by {mean:.2f} / 255')
    worst = mean if worst is None else min(worst, mean)
print(('PASS' if worst >= least else 'FAIL') + f': weakest {worst:.2f} (at least {least})')
sys.exit(0 if worst >= least else 1)
PY
