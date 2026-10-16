#!/usr/bin/env bash
# strength.sh [ROUND_DIR] — how much MIRROR's mirror moves the shaders round's stills past SWELL's (r455).
#
# MIRROR is SWELL plus the mirror: tools/r180-shader-round/strength.sh holds its scenes against a plain image, which
# the swell alone passes, so a reader drawing MIRROR as SWELL would pass it too. This renders the round's scenes that
# have a MIRROR layer twice with libGDX (tools/parallax-lab-shots.sh, off screen), as written and with every MIRROR
# layer turned into SWELL (glint and all kept), and prints the mean difference per still; it fails when one is under
# MIN (default 2.5, past the rounds' 2), or when no scene has a MIRROR layer. Writes build/r455/strength/.
set -u
cd "$(dirname "$0")/../.." || exit 1
ROUND="${1:-engines/rounds/shaders}" MIN="${MIN:-2.5}" OUT=build/r455/strength
rm -rf "$OUT"; mkdir -p "$OUT/mirror-round" "$OUT/swell-round"
python3 - "$ROUND" "$OUT" <<'PY' || exit 1
import json, os, sys
round_dir, out = sys.argv[1], sys.argv[2]
r = json.load(open(os.path.join(round_dir, 'round.json')))
kept = []
for scene in r['scenes']:
    page = json.load(open(scene['page']))
    mirrors = [layer for layer in page['pageModel']['pageList'] if layer.get('shaderEffect') == 'MIRROR']
    if not mirrors:
        continue
    kept.append(scene)
    for layer in mirrors:
        layer['shaderEffect'] = 'SWELL'
    swell = dict(scene, page=os.path.join(out, 'swell-round', os.path.basename(scene['page'])))
    json.dump(page, open(swell['page'], 'w'))
    scene['swell'] = swell
if not kept:
    sys.exit('no scene of ' + round_dir + ' has a MIRROR layer')
for name, scenes in [('mirror-round', [{k: v for k, v in s.items() if k != 'swell'} for s in kept]),
                     ('swell-round', [s['swell'] for s in kept])]:
    json.dump(dict(r, scenes=scenes), open(os.path.join(out, name, 'round.json'), 'w'))
PY
tools/parallax-lab-shots.sh "$OUT/mirror-round" "$OUT/mirror" >/dev/null || exit 1
tools/parallax-lab-shots.sh "$OUT/swell-round" "$OUT/swell" >/dev/null || exit 1
python3 - "$OUT" "$MIN" <<'PY'
import glob, os, sys
from PIL import Image, ImageChops, ImageStat
out, least = sys.argv[1], float(sys.argv[2])
worst = None
for mirror in sorted(glob.glob(os.path.join(out, 'mirror', '*-t*.png'))):
    name = os.path.basename(mirror)
    a = Image.open(mirror).convert('RGB')
    b = Image.open(os.path.join(out, 'swell', name)).convert('RGB')
    mean = sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 3
    print(f'{name}  the mirror moves it by {mean:.2f} / 255')
    worst = mean if worst is None else min(worst, mean)
if worst is None:
    sys.exit('FAIL: no still rendered')
print(('PASS' if worst >= least else 'FAIL') + f': weakest {worst:.2f} (at least {least})')
sys.exit(0 if worst >= least else 1)
PY
