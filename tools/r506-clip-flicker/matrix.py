#!/usr/bin/env python3
"""What makes a stepped lab drop layers on the GPU? (r506) Steps the depth lab as tools/r444-depth/drive.py's clip does, 60
frames in 2D and 60 in 3D, under one or more variants, and counts the frames that jump, read back small in the same task as
the draw.
  python3 tools/r506-clip-flicker/matrix.py OUT VARIANT...     (after ./gradlew :core:browserTestWar)
A variant is a name of VARIANTS. OUT/<variant>/ keeps the frames either side of the worst jump.
"""
import functools
import http.server
import shutil
import sys
import threading
from pathlib import Path

import numpy as np
from PIL import Image
from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
WAR = ROOT / 'core/build/browserTest/war'
GPU = ['--ignore-gpu-blocklist', '--enable-gpu']
CONTEXT = """(() => { const real = HTMLCanvasElement.prototype.getContext;
  HTMLCanvasElement.prototype.getContext = function (kind, options) {
    return real.call(this, kind, Object.assign({}, options, %s)); }; })()"""
# name: Chrome's flags, the context options forced, gl.finish before the read, a screenshot each frame.
VARIANTS = {
    'base': (['--use-gl=angle', '--use-angle=gl-egl'], '{}', False, True),
    'noshot': (['--use-gl=angle', '--use-angle=gl-egl'], '{}', False, False),
    'noaa': (['--use-gl=angle', '--use-angle=gl-egl'], '{antialias: false}', False, True),
    'finish': (['--use-gl=angle', '--use-angle=gl-egl'], '{}', True, True),
    'preserve': (['--use-gl=angle', '--use-angle=gl-egl'], '{preserveDrawingBuffer: true}', False, True),
    'vulkan': (['--use-angle=vulkan', '--enable-features=Vulkan'], '{}', False, True),
    'angle-gl': (['--use-gl=angle', '--use-angle=gl'], '{}', False, True),
    'soft': (['--use-angle=swiftshader', '--enable-unsafe-swiftshader'], '{}', False, True),
}
JUMP = 15
WIDTH, HEIGHT = 160, 90
# Draws, and reads the canvas back small in the same task.
READ = """(finish) => {
  window.depthLabAct(%s);
  const live = document.querySelector('#depth-lab canvas');
  if (finish) { const g = live.getContext('webgl'); g.finish(); }
  const small = document.createElement('canvas');
  small.width = %d; small.height = %d;
  const c = small.getContext('2d', {willReadFrequently: true});
  c.drawImage(live, 0, 0, small.width, small.height);
  return Array.from(c.getImageData(0, 0, small.width, small.height).data);
}"""
SET = """([key, value]) => { const c = document.querySelector(`[data-key="${key}"]`);
  c.value = value; c.dispatchEvent(new Event('input', {bubbles: true})); c.dispatchEvent(new Event('change', {bubbles: true})); }"""


class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


def renderer(page):
    return page.evaluate("""() => { const g = document.querySelector('#depth-lab canvas').getContext('webgl');
      const x = g.getExtension('WEBGL_debug_renderer_info');
      return [x ? g.getParameter(x.UNMASKED_RENDERER_WEBGL) : '?', JSON.stringify(g.getContextAttributes())]; }""")


def run(p, chrome, url, name, out):
    flags, options, finish, screenshots = VARIANTS[name]
    # Not tools/browser.py's launch (AGENTS.md, r488): the launch flags ARE what r506 varies, and browser.py would
    # pick its own and fall back to SwiftShader, hiding the GPU drop being probed.
    browser = p.chromium.launch(executable_path=chrome, args=GPU + flags)
    page = browser.new_page(viewport={'width': 1540, 'height': 1100}, device_scale_factor=1)
    page.add_init_script(CONTEXT % options)
    page.goto(url + '?clip=1&big=1')
    page.wait_for_function('window.depthLabReady === true', timeout=120000)
    print(name, *renderer(page), flush=True)
    frames, steps = [], []
    page.evaluate(SET, ['page', '4'])
    page.evaluate(SET, ['view', '0'])
    for i in range(120):
        if i == 60:
            page.evaluate(SET, ['view', '1'])
        if i > 60:
            page.evaluate(SET, ['yaw', str(66 - (i - 60) * 0.3)])
        raw = page.evaluate(READ % ('1/30', WIDTH, HEIGHT), finish)
        frame = np.array(raw, dtype=np.uint8).reshape(HEIGHT, WIDTH, 4)[:, :, :3]
        if frames:
            steps.append(float(np.abs(frame.astype(int) - frames[-1].astype(int)).mean()))
        frames.append(frame)
        if screenshots:
            page.locator('#depth-lab').screenshot(type='jpeg')
    browser.close()
    steps = np.array(steps)
    # The step from 2D to 3D at frame 60 is the only cut the clip asks for.
    jumps = [int(j) for j in np.nonzero(steps > JUMP)[0] if j != 59]
    print(f'{name}: {len(jumps)} jumps over {JUMP}/255 of 119, worst {steps.max():.0f}: {jumps[:30]}', flush=True)
    if jumps:
        where = out / name
        where.mkdir(parents=True, exist_ok=True)
        worst = max(jumps, key=lambda j: steps[j])
        for k in (worst, worst + 1):
            Image.fromarray(frames[k]).resize((640, 360)).save(where / f'f{k:03d}.png')
    return len(jumps)


def main():
    out = Path(sys.argv[1]).resolve()
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), functools.partial(Quiet, directory=str(WAR)))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    chrome = shutil.which('google-chrome') or shutil.which('chromium')
    with sync_playwright() as p:
        for name in sys.argv[2:]:
            try:
                run(p, chrome, f'http://127.0.0.1:{server.server_port}/depth-lab.html', name, out)
            except PlaywrightError as e:  # a backend that cannot start is an answer too
                print(f'{name}: FAILED TO RUN: {str(e).splitlines()[0]}', flush=True)


if __name__ == '__main__':
    main()
