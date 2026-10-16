#!/usr/bin/env bash
# strength.sh [ROUND_DIR] — how much the glint of a round's scenes changes their libGDX stills (r427).
#
# A reader round fails past a mean 2/255 apart, or, with a "pixelsOff" (percent), past that share of a still's pixels off
# by over 32: a reader that left the glint out would pass a scene whose glint moves its stills by less. This renders the
# round's glinting scenes twice with libGDX (tools/parallax-lab-shots.sh, off screen), as written and with every
# glintStrength dropped, and prints per still the mean difference and the pixels off by over 32. It fails when one still
# moves less than twice the round's pixelsOff (the glint round's gate), or, for a round without one, by a mean under MIN
# (default 2.5, past the rounds' 2). Scenes with no glinting layer are left out. Writes build/r427/strength/.
set -u
cd "$(dirname "$0")/../.." || exit 1
ROUND="${1:-engines/rounds/glint}" MIN="${MIN:-2.5}" OUT=build/r427/strength
rm -rf "$OUT"; mkdir -p "$OUT/glint-round" "$OUT/plain-round"
python3 - "$ROUND" "$OUT" <<'PY'
import json, os, sys
round_dir, out = sys.argv[1], sys.argv[2]
r = json.load(open(os.path.join(round_dir, 'round.json')))
glinting, plain = dict(r, scenes=[]), dict(r, scenes=[])
for scene in r['scenes']:
    page = json.load(open(scene['page']))
    # The front layer's glint is the one seen: g04 (r469) hides a glinting sea under one that does not glint.
    if not page['pageModel']['pageList'][-1].get('glintStrength', 0) > 0:
        continue
    glinting['scenes'].append(scene)
    for layer in page['pageModel']['pageList']:
        layer.pop('glintStrength', None)
    path = os.path.join(out, 'plain-round', os.path.basename(scene['page']))
    json.dump(page, open(path, 'w'))
    plain['scenes'].append(dict(scene, page=path))
json.dump(glinting, open(os.path.join(out, 'glint-round', 'round.json'), 'w'))
json.dump(plain, open(os.path.join(out, 'plain-round', 'round.json'), 'w'))
PY
tools/parallax-lab-shots.sh "$OUT/glint-round" "$OUT/glint" >/dev/null || exit 1
tools/parallax-lab-shots.sh "$OUT/plain-round" "$OUT/plain" >/dev/null || exit 1
python3 - "$OUT" "$MIN" "$ROUND" <<'PY'
import glob, json, os, sys
from PIL import Image, ImageChops, ImageStat
out, least, round_dir = sys.argv[1], float(sys.argv[2]), sys.argv[3]
pixels_off = json.load(open(os.path.join(round_dir, 'round.json'))).get('pixelsOff')
worst = None
for glint in sorted(glob.glob(os.path.join(out, 'glint', '*-t*.png'))):
    name = os.path.basename(glint)
    a = Image.open(glint).convert('RGB')
    b = Image.open(os.path.join(out, 'plain', name)).convert('RGB')
    diff = ImageChops.difference(a, b)
    mean = sum(ImageStat.Stat(diff).mean) / 3
    big = sum(diff.convert('L').histogram()[33:]) / (a.width * a.height) * 100
    print(f'{name}  the glint moves it by {mean:.2f} / 255, {big:.3f}% of its pixels by over 32')
    seen = big if pixels_off is not None else mean
    worst = seen if worst is None else min(worst, seen)
if worst is None:
    print('FAIL: no scene glints')
    sys.exit(1)
need, unit = (2 * pixels_off, '% off') if pixels_off is not None else (least, ' / 255')
print(('PASS' if worst >= need else 'FAIL') + f': weakest {worst:.3f}{unit} (at least {need})')
sys.exit(0 if worst >= need else 1)
PY
