#!/usr/bin/env python3
"""Is the depth lab's half-white Hiver frame the page or the lab? (r502)

Reads the page's numbers, and draws the lab's Hiver twice from a scratch copy of the built war: as it is, and with every
layer lowered and the gradients coloured as r414 did for the lurker lab. Fails unless the first frame's white starts where
the numbers say and the second has none, which makes the page's layout the cause. The tree cut is the front layer's
decal_Y_Ratio; 2019's Hiver holds the same numbers and white gradients.
  python3 tools/r502-hiver-frame/check.py OUT    (after ./gradlew :core:browserTestWar; writes OUT/asis.png, lowered.png)
"""
import functools
import http.server
import json
import shutil
import subprocess
import sys
import tempfile
import threading
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
WAR = ROOT / 'core/build/browserTest/war'
PAGE = ROOT / 'engines/rounds/shaders/s01.jplax'
SAMPLE = ROOT / 'core/test-data/samples/hiver/Hiver.plaxpj'
CHROME = shutil.which('google-chrome') or shutil.which('chromium') or shutil.which('chromium-browser')
# As core/test-data/lurker/make_lab.py (r414).
DROP = 45.0
SKY = {'r': 170 / 255, 'g': 168 / 255, 'b': 190 / 255, 'a': 1.0}
SNOW = {'r': 225 / 255, 'g': 233 / 255, 'b': 222 / 255, 'a': 1.0}


class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


def still(war, out):
    """The lab's Hiver at its 2D view (page=4, view=0), through its own driver."""
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), functools.partial(Quiet, directory=str(war)))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    with tempfile.TemporaryDirectory() as frames:
        subprocess.run([sys.executable, str(ROOT / 'tools/r444-depth/drive.py'),
                        f'http://127.0.0.1:{server.server_port}/depth-lab.html', CHROME, frames, 'shot', '1',
                        ' page=4 view=0'], check=True)
        shutil.copy(Path(frames) / 'still.png', out)
    server.shutdown()


def white_rows(path):
    """Of the frame's rows, the fraction of each that is pure page white."""
    pixels = np.asarray(Image.open(path).convert('RGB'))
    return (pixels.min(axis=2) >= 250).mean(axis=1)


def lowest_art(rows):
    """How far down the frame, in percent, the last row with anything but white ends."""
    drawn = np.nonzero(rows < 1)[0]
    return 100 * (drawn[-1] + 1) / len(rows)


def main():
    out = Path(sys.argv[1]).resolve()
    out.mkdir(parents=True, exist_ok=True)
    page = json.loads(PAGE.read_text())
    sample = json.loads(SAMPLE.read_text())['saving']
    front = page['pageModel']['pageList'][-1]['decal_Y_Ratio']
    sample_decals = [layer['decal_Y_Ratio'] for layer in sample['pageModel']['pageList']]
    print(f'2019 Hiver decal_Y_Ratio {sample_decals}, the lab page\'s front layer {front}, page white gradients: '
          f'{page["bottomHalf_bottom"]}')

    with tempfile.TemporaryDirectory() as scratch:
        war = Path(scratch) / 'war'
        shutil.copytree(WAR, war)
        still(war, out / 'asis.png')
        target = next(war.glob('assets/fog-lab/s01-*.jplax'))
        page.update({'topHalf_top': SKY, 'topHalf_bottom': SKY, 'bottomHalf_top': SNOW, 'bottomHalf_bottom': SNOW})
        for layer in page['pageModel']['pageList']:
            layer['decal_Y_Ratio'] = layer['decal_Y_Ratio'] - DROP
        target.write_text(json.dumps(page))
        still(war, out / 'lowered.png')

    asis, lowered = white_rows(out / 'asis.png'), white_rows(out / 'lowered.png')
    ends = lowest_art(asis)
    print(f'as is: art ends {ends:.1f}% down (the front layer\'s decal says {100 - front:.1f}%); '
          f'{100 * (asis == 1).mean():.1f}% of rows wholly white')
    print(f'lowered {DROP}: {100 * (lowered >= 0.5).mean():.1f}% of rows at least half white')
    if abs(ends - (100 - front)) > 1:
        sys.exit('FAIL: the white does not start where the front layer\'s decal says')
    if (lowered >= 0.5).any():
        sys.exit('FAIL: lowering the layers left white: something else draws it')
    print('OK: the page\'s layout is the cause')


if __name__ == '__main__':
    main()
