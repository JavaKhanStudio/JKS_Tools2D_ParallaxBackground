#!/usr/bin/env bash
# strength.sh — how much the fog creep moves the transfer round's stills (r252).
#
# The reader rounds fail past a mean 2/255 apart: a reader that drew the mist as a plain fade would pass a scene the
# mist moves by less. This renders the transfer round's t13, t14 and t17 (r259) with libGDX (tools/parallax-lab-shots.sh, off
# screen), and the same scenes with their style taken out (a plain fade of the same pages at the same time), and
# prints, per mid-transfert still, how far the mist is from the fade; it fails when one is under MIN (default 2.5,
# past the rounds' 2). Writes build/r252/strength/.
# headless: tools/parallax-lab-shots.sh renders in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
MIN="${MIN:-2.5}" OUT=build/r252/strength
rm -rf "$OUT"; mkdir -p "$OUT/round" "$OUT/plain" "$OUT/creep"
python3 - "$OUT/round" <<'PY' || exit 1
import json, sys
r = json.load(open('engines/godot/tests/transfer/round.json'))
r['scenes'] = [s for s in r['scenes'] if s['id'] in ('t13', 't14', 't17')]
for s in r['scenes']:
    del s['transfer']['style']
json.dump(r, open(sys.argv[1] + '/round.json', 'w'), indent=1)
PY
tools/parallax-lab-shots.sh engines/godot/tests/transfer "$OUT/creep" >/dev/null || exit 1
tools/parallax-lab-shots.sh "$OUT/round" "$OUT/plain" >/dev/null || exit 1
python3 - "$OUT" "$MIN" <<'PY'
import os, sys
from PIL import Image, ImageChops, ImageStat
out, least = sys.argv[1], float(sys.argv[2])
worst = None
for scene in ('t13', 't14', 't17'):
    a = Image.open(os.path.join(out, 'creep', scene + '-t6.png')).convert('RGB')
    b = Image.open(os.path.join(out, 'plain', scene + '-t6.png')).convert('RGB')
    mean = sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 3
    print(f'{scene}-t6.png  the mist moves it from the plain fade by {mean:.2f} / 255')
    worst = mean if worst is None else min(worst, mean)
print(('PASS' if worst >= least else 'FAIL') + f': weakest {worst:.2f} (at least {least})')
sys.exit(0 if worst >= least else 1)
PY
