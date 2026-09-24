#!/usr/bin/env python3
"""Writes a many-layer editor project from a sample one, to profile the editor (r42).

    tools/stress-project.py LAYERS [SOURCE.plaxpj] [OUT_DIR]

Copies SOURCE's atlas and pages next to OUT_DIR/Stress<LAYERS>.plaxpj, whose page holds LAYERS layers drawn from
SOURCE's layers with shuffled sizes, speeds and decals (seeded: the same LAYERS gives the same file).
Defaults: demo/assets/hiver/Hiver.plaxpj, editor/build/stress. Open it with `open build/stress/Stress300.plaxpj`
through tools/driver-probe.sh.
"""
import copy, json, os, random, re, shutil, sys

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
count = int(sys.argv[1])
source = sys.argv[2] if len(sys.argv) > 2 else os.path.join(root, 'demo/assets/hiver/Hiver.plaxpj')
out = sys.argv[3] if len(sys.argv) > 3 else os.path.join(root, 'editor/build/stress')
os.makedirs(out, exist_ok=True)

project = json.load(open(source))
page = project['saving']['pageModel']
atlas = page['atlasName']
src_dir = os.path.dirname(source)
atlas_path = next(p for p in (os.path.join(src_dir, atlas), os.path.join(src_dir, '..', atlas)) if os.path.exists(p))
shutil.copy(atlas_path, out)
for image in re.findall(r'^(\S+\.png)$', open(atlas_path).read(), re.M):
    shutil.copy(os.path.join(os.path.dirname(atlas_path), image), out)

rng = random.Random(count)
layers = []
for i in range(count):
    layer = copy.deepcopy(rng.choice(page['pageList']))
    depth = (i + 1) / count
    layer['sizeRatio'] = round(0.3 + rng.random() * 1.7, 3)
    layer['parallaxScalingSpeedX'] = layer['parallaxScalingSpeedY'] = round(0.005 + depth * 0.05, 5)
    layer['decal_X_Ratio'] = round(rng.random() * 100, 1)
    layer['decal_Y_Ratio'] = round(rng.random() * 80 - 10, 1)
    layer['flipX'] = rng.random() < 0.5
    layers.append(layer)
page['pageList'] = layers
project['saving']['inside'] = [True] * count

target = os.path.join(out, 'Stress%d.plaxpj' % count)
json.dump(project, open(target, 'w'))
print(target)
