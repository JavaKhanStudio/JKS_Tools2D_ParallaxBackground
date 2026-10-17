#!/usr/bin/env bash
# strength.sh — how much the detail scenes (r464) move past what a reader that ignores the detail draws.
#
# Each scene of engines/rounds/shaders and engines/rounds/glint with a "detail", "detailDefault" or "layerDetail" is
# rendered with libGDX (tools/parallax-lab-shots.sh, off screen) three times: as written; as a reader ignoring the level
# draws it (no "detail": HIGH, as stored); as one ignoring the rules draws it (no "detailDefault", no "layerDetail").
# A scene is seen by a reader when it moves past its round's gate: the mean (at least MIN, default 2.5, past the
# rounds' 2) or, on the glint round, the pixels off by >32 (at least PIXELS percent, default 0.1, past its 0.05).
# Fails when either reader is not caught by a scene of the rounds, or when a scene is caught by neither.
# Writes build/r464/strength/.
set -u
cd "$(dirname "$0")/../.." || exit 1
MIN="${MIN:-2.5}" PIXELS="${PIXELS:-0.1}" OUT=build/r464/strength
rm -rf "$OUT"; mkdir -p "$OUT"
python3 - "$OUT" <<'PY' || exit 1
import json, os, sys
out = sys.argv[1]
RULES = ('detailDefault', 'layerDetail')
for round_dir in ['engines/rounds/shaders', 'engines/rounds/glint']:
    r = json.load(open(os.path.join(round_dir, 'round.json')))
    kept = [s for s in r['scenes'] if 'detail' in s or any(k in s for k in RULES)]
    name = os.path.basename(round_dir)
    for variant, drop in [('as-written', ()), ('no-level', ('detail',)), ('no-rules', RULES)]:
        os.makedirs(os.path.join(out, name, variant + '-round'))
        scenes = [{k: v for k, v in s.items() if k not in drop} for s in kept]
        json.dump(dict(r, scenes=scenes), open(os.path.join(out, name, variant + '-round', 'round.json'), 'w'))
PY
for round in shaders glint; do
	for variant in as-written no-level no-rules; do
		tools/parallax-lab-shots.sh "$OUT/$round/$variant-round" "$OUT/$round/$variant" >/dev/null || exit 1
	done
done
python3 - "$OUT" "$MIN" "$PIXELS" <<'PY'
import glob, os, sys
from PIL import Image, ImageChops, ImageStat
out, least, pixels = sys.argv[1], float(sys.argv[2]), float(sys.argv[3])
caught = {'no-level': 0, 'no-rules': 0}
fail = False
for round_name in ['shaders', 'glint']:
    scenes = {}
    for still in sorted(glob.glob(os.path.join(out, round_name, 'as-written', '*-t*.png'))):
        name = os.path.basename(still)
        a = Image.open(still).convert('RGB')
        for reader in caught:
            b = Image.open(os.path.join(out, round_name, reader, name)).convert('RGB')
            diff = ImageChops.difference(a, b)
            mean = sum(ImageStat.Stat(diff).mean) / 3
            # As tools/compare-parallax-frames.py counts them.
            off = sum(diff.convert('L').histogram()[33:]) / (a.width * a.height) * 100
            # The glint round fails a still on either gate, the shaders round on the mean.
            seen = mean >= least or (round_name == 'glint' and off >= pixels)
            print(f'{round_name}/{name}  against a reader with {reader}: mean {mean:.2f} / 255, {off:.3f}% off'
                  + ('  SEEN' if seen else ''))
            scenes.setdefault(name.split('-')[0], {}).setdefault(reader, []).append(seen)
    for scene, readers in scenes.items():
        seen_by = [reader for reader, stills in readers.items() if all(stills)]
        for reader in seen_by:
            caught[reader] += 1
        if not seen_by:
            print(f'FAIL: {round_name} {scene} is seen by no reader on every still')
            fail = True
for reader, n in caught.items():
    print(f'a reader with {reader}: caught by {n} scene(s)')
    if n == 0:
        fail = True
print('FAIL' if fail else 'PASS')
sys.exit(1 if fail else 0)
PY
