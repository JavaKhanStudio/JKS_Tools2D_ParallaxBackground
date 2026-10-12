#!/usr/bin/env bash
# strength.sh — how much the planet moves the planet round's stills (r277, r347).
#
# The reader rounds fail past a mean 2/255 apart: a reader that drew a page flat would pass a scene the planet moves by
# less. Each scene has a "flat" twin, its pages' planetRadius 0. This renders the round and the twins with libGDX
# (tools/parallax-lab-shots.sh, off screen) and prints, per still, how far each is from its twin; it fails when one is
# under MIN (default 2.5, past the rounds' 2); p07-t0, the flat page before it fades into a round one, is
# skipped. Writes build/r277/strength/, and side.png: per scene at 6 s, the flat twin
# over the round's still.
# headless: tools/parallax-lab-shots.sh renders in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
MIN="${MIN:-2.5}" OUT=build/r277/strength
ROUND=engines/rounds/planet
rm -rf "$OUT"; mkdir -p "$OUT/flat"
python3 - "$OUT" "$ROUND" <<'PY' || exit 1
import json, os, sys
out, round_dir = sys.argv[1:]
def twin(path):
    page = json.load(open(path))
    page['planetRadius'] = 0
    twin_path = os.path.join(out, 'flat', os.path.basename(path))
    json.dump(page, open(twin_path, 'w'), indent=1)
    return twin_path
round_ = json.load(open(os.path.join(round_dir, 'round.json')))
for scene in round_['scenes']:
    scene['page'] = twin(scene['page'])
    if 'transfer' in scene:
        scene['transfer']['page'] = twin(scene['transfer']['page'])
json.dump(round_, open(os.path.join(out, 'flat', 'round.json'), 'w'), indent=1)
PY
tools/parallax-lab-shots.sh "$ROUND" "$OUT" >/dev/null || exit 1
tools/parallax-lab-shots.sh "$OUT/flat" "$OUT/flat" >/dev/null || exit 1
python3 - "$OUT" "$MIN" "$ROUND" <<'PY'
import json, os, sys
from PIL import Image, ImageChops, ImageStat
out, least, round_dir = sys.argv[1], float(sys.argv[2]), sys.argv[3]
worst = None
scenes = json.load(open(os.path.join(round_dir, 'round.json')))['scenes']
pairs = []
for scene in scenes:
    for t in (0, 6, 12):
        name = f"{scene['id']}-t{t}.png"
        if name == 'p07-t0.png':
            # The flat page before its cross-fade into p01: the same as its twin by design.
            continue
        a = Image.open(os.path.join(out, name)).convert('RGB')
        b = Image.open(os.path.join(out, 'flat', name)).convert('RGB')
        mean = sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 3
        worst = mean if worst is None else min(worst, mean)
        print(f'{name}  from its flat twin {mean:.2f} / 255')
        if t == 6:
            pairs.append((b, a))
w, h = pairs[0][0].size
side = Image.new('RGB', (w, h * 2 * len(pairs)))
for i, (b, a) in enumerate(pairs):
    side.paste(b, (0, 2 * i * h))
    side.paste(a, (0, (2 * i + 1) * h))
side.resize((w // 2, h * len(pairs))).save(os.path.join(out, 'side.png'))
print(('PASS' if worst >= least else 'FAIL') + f': weakest {worst:.2f} (at least {least})')
sys.exit(0 if worst >= least else 1)
PY
