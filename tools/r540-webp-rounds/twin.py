"""twin.py ROUND OUT: r540's WebP twin of a reader round, written to OUT (a path from the checkout's root): its pages,
round.json naming them, and each atlas the pages name copied beside them with its page images as lossless WebP
(Pillow, exact=True), the atlas's page lines renamed. Prints the PNG and WebP bytes."""
import json
import os
import shutil
import sys

from PIL import Image


def twin_atlas(atlas_dir, name, out_dir, sizes):
    os.makedirs(out_dir, exist_ok=True)
    with open(os.path.join(atlas_dir, name)) as f:
        lines = f.read().split('\n')
    for i, line in enumerate(lines):
        image = line.strip()
        if image.lower().endswith('.png') and os.path.isfile(os.path.join(atlas_dir, image)):
            webp = image[:-4] + '.webp'
            target = os.path.join(out_dir, webp)
            if not os.path.exists(target):
                Image.open(os.path.join(atlas_dir, image)).save(target, lossless=True, exact=True, quality=100, method=6)
                sizes[0] += os.path.getsize(os.path.join(atlas_dir, image))
                sizes[1] += os.path.getsize(target)
            lines[i] = line.replace(image, webp)
    with open(os.path.join(out_dir, name), 'w') as f:
        f.write('\n'.join(lines))


def main(round_dir, out):
    with open(os.path.join(round_dir, 'round.json')) as f:
        data = json.load(f)
    os.makedirs(out, exist_ok=True)
    dirs, sizes = {}, [0, 0]

    def twin(scene):
        atlas_dir = scene['atlasDir']
        target = dirs.setdefault(atlas_dir, os.path.join(out, f'atlas{len(dirs)}'))
        with open(scene['page']) as f:
            page = json.load(f)
        twin_atlas(atlas_dir, page['pageModel']['atlasName'], target, sizes)
        copy = os.path.join(out, os.path.basename(scene['page']))
        shutil.copyfile(scene['page'], copy)
        scene['page'], scene['atlasDir'] = copy, target

    for scene in data['scenes']:
        twin(scene)
        if 'transfer' in scene:
            twin(scene['transfer'])
    data['round'] = data.get('round', os.path.basename(round_dir)) + '-webp'
    with open(os.path.join(out, 'round.json'), 'w') as f:
        json.dump(data, f, indent=1)
    print(f'{out}: atlas pages PNG {sizes[0] // 1024} KiB, WebP {sizes[1] // 1024} KiB')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
