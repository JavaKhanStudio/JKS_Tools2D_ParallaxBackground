#!/usr/bin/env bash
# strength.sh — how much the wrap and the shade move the tower round's stills (r276).
#
# The reader rounds fail past a mean 2/255 apart: a reader that drew a tower flat, or unshaded, would pass a scene the
# wrap or the shade moves by less. Each scene has two twins: "flat", its tower's radius 0 (today's flat layer), and
# "unshaded", its tower's shade 0 (wrapped, no darkening). This renders the round and its twins with libGDX
# (tools/parallax-lab-shots.sh, off screen) and prints, per still, how far each is from its twin; it fails when one is
# under MIN (default 2.5, past the rounds' 2). Writes build/r276/strength/.
# headless: tools/parallax-lab-shots.sh renders in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
MIN="${MIN:-2.5}" OUT=build/r276/strength
rm -rf "$OUT"; mkdir -p "$OUT/flat" "$OUT/unshaded"
python3 - "$OUT" <<'PY' || exit 1
import json, os, sys
out = sys.argv[1]
def twin(path, kind):
    page = json.load(open(path))
    for layer in page['pageModel']['pageList']:
        if layer.get('towerRadius', 0) > 0:
            layer['towerRadius' if kind == 'flat' else 'towerShade'] = 0
    twin_path = os.path.join(out, kind, os.path.basename(path))
    json.dump(page, open(twin_path, 'w'), indent=1)
    return twin_path
for kind in ('flat', 'unshaded'):
    round_ = json.load(open('engines/godot/tests/tower/round.json'))
    for scene in round_['scenes']:
        scene['page'] = twin(scene['page'], kind)
        if 'transfer' in scene:
            scene['transfer']['page'] = twin(scene['transfer']['page'], kind)
    json.dump(round_, open(os.path.join(out, kind, 'round.json'), 'w'), indent=1)
PY
tools/parallax-lab-shots.sh engines/godot/tests/tower "$OUT" >/dev/null || exit 1
tools/parallax-lab-shots.sh "$OUT/flat" "$OUT/flat" >/dev/null || exit 1
tools/parallax-lab-shots.sh "$OUT/unshaded" "$OUT/unshaded" >/dev/null || exit 1
python3 - "$OUT" "$MIN" <<'PY'
import json, os, sys
from PIL import Image, ImageChops, ImageStat
out, least = sys.argv[1], float(sys.argv[2])
worst = None
for scene in json.load(open('engines/godot/tests/tower/round.json'))['scenes']:
    for t in (0, 6, 12):
        name = f"{scene['id']}-t{t}.png"
        a = Image.open(os.path.join(out, name)).convert('RGB')
        line = name
        for kind in ('flat', 'unshaded'):
            b = Image.open(os.path.join(out, kind, name)).convert('RGB')
            mean = sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 3
            line += f'  from its {kind} twin {mean:.2f} / 255'
            worst = mean if worst is None else min(worst, mean)
        print(line)
print(('PASS' if worst >= least else 'FAIL') + f': weakest {worst:.2f} (at least {least})')
sys.exit(0 if worst >= least else 1)
PY
