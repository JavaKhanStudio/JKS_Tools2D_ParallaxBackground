"""make_pages.py IMAGE OUT: r523's two pages, OUT/png and OUT/webp: one IMAGE layer of IMAGE, its atlas page a PNG or
its lossless WebP twin (Pillow, exact=True). Used by frame-check.sh and tools/android-webp-check.sh."""
import json
import os
import sys

from PIL import Image


def main(image, out):
    im = Image.open(image).convert('RGBA')
    for kind in ('png', 'webp'):
        os.makedirs(os.path.join(out, kind), exist_ok=True)
    im.save(os.path.join(out, 'png', 'page.png'))
    im.save(os.path.join(out, 'webp', 'page.webp'), lossless=True, exact=True, quality=100, method=4)
    w, h = im.size
    white = {'r': 1.0, 'g': 1.0, 'b': 1.0, 'a': 1.0}
    layer = {'regionName': 'image', 'regionPosition': 0, 'flipX': False, 'flipY': False,
             'parallaxScalingSpeedX': 0.5, 'parallaxScalingSpeedY': 0.5, 'speedXAtRest': 0.0, 'sizeRatio': 1.2,
             'decal_X_Ratio': 0.0, 'decal_Y_Ratio': 0.0, 'padX': 0.0, 'padXFactor': 0.0, 'padY': 0.0, 'padYFactor': 0.0}
    page = {'topHalf_top': white, 'topHalf_bottom': white, 'topHalfSize': 0.5, 'bottomHalf_top': white,
            'bottomHalf_bottom': white, 'bottomHalfSize': 0.5, 'repeatOnX': True, 'repeatOnY': False,
            'pageModel': {'atlasName': 'page.atlas', 'outside': False, 'pageList': [layer]}}
    for kind in ('png', 'webp'):
        with open(os.path.join(out, kind, 'page.atlas'), 'w') as f:
            f.write(f'page.{kind}\nsize: {w}, {h}\nformat: RGBA8888\nfilter: Linear,Linear\nrepeat: none\n'
                    f'image\n  rotate: false\n  xy: 0, 0\n  size: {w}, {h}\n  orig: {w}, {h}\n  offset: 0, 0\n'
                    '  index: -1\n')
        with open(os.path.join(out, kind, 'page.jplax'), 'w') as f:
            json.dump(page, f, indent=1)
    png_kib = os.path.getsize(os.path.join(out, 'png', 'page.png')) // 1024
    webp_kib = os.path.getsize(os.path.join(out, 'webp', 'page.webp')) // 1024
    print(f'{image}: {w}x{h}, PNG {png_kib} KiB, WebP {webp_kib} KiB')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
