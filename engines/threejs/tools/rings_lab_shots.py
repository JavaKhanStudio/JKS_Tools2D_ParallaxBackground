#!/usr/bin/env python3
"""rings_lab_shots.py URL CHROME OUT shot|clip "SCENES" [K=V...] — rings-lab.sh --shot and --clip: the rings lab
headless on the GPU (tools/browser.py), sliders started at K=V. shot: each scene in SCENES (ids of engines/rounds/rings)
side by side at 0, 8 and 20 s of the flight (window.ringsLab.at), OUT/<scene>-both-t<s>.png. clip: the first scene,
12 s at 30 fps, into the video OUT (ffmpeg). Every console message of the page and of WebGL goes to OUT's
console.log (beside the clip); a warning or an error fails the run (exit 1), as a lab must draw clean on the GPU."""
import os
import subprocess
import sys
import tempfile

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', '..', '..', 'tools'))
from browser import launch
from playwright.sync_api import sync_playwright

url, chrome, out, mode, scenes, sliders = sys.argv[1], sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5].split(), sys.argv[6:]
clip = out if mode == 'clip' else None
if clip:
    out = os.path.dirname(clip)
    frames = tempfile.mkdtemp()
    scenes = scenes[:1]
os.makedirs(out, exist_ok=True)
messages, bad = [], []
with sync_playwright() as p:
    browser = launch(p, chrome)
    page = browser.new_page(viewport={'width': 1600, 'height': 760})

    def seen(m):
        messages.append(f'{m.type}: {m.text}')
        if m.type in ('warning', 'error') or 'GL_' in m.text or 'WebGL' in m.text:
            bad.append(m.text)
    page.on('console', seen)
    page.on('pageerror', lambda e: bad.append(str(e)))
    renderer = None
    for scene in scenes:
        query = '&'.join([f'scene={scene}', 'view=both', 'still=1', *sliders])
        page.goto(f'{url}?{query}')
        page.evaluate('window.ringsLab.ready')
        renderer = renderer or page.evaluate("""() => { const g = document.querySelector('canvas').getContext('webgl2');
            const x = g.getExtension('WEBGL_debug_renderer_info'); return g.getParameter(x ? x.UNMASKED_RENDERER_WEBGL : g.RENDERER); }""")
        if clip:
            for i in range(12 * 30):
                page.evaluate(f'window.ringsLab.at({i / 30})')
                page.screenshot(path=os.path.join(frames, f'f{i:04d}.png'))
        else:
            for t in (0, 8, 20):
                page.evaluate(f'window.ringsLab.at({t})')
                page.screenshot(path=os.path.join(out, f'{scene}-both-t{t}.png'))
            # A page with a SHADER layer: panel B turned to where its band closes the ring (r58), at 8 s.
            look = page.evaluate('window.ringsLab.at(8), window.ringsLab.lookAtJoin(8)')
            if look is not None:
                page.evaluate(f'window.ringsLab.set({{look: {look}}}), window.ringsLab.at(8)')
                page.screenshot(path=os.path.join(out, f'{scene}-join-t8.png'))
                page.evaluate('window.ringsLab.set({look: 0})')
        print(f'{scene}: {page.evaluate("JSON.stringify(window.ringsLab.knobs())")}')
    if not clip:
        # A cross-fade into the next scene's page, halfway (2 s of 4): both pages' bands at their alphas.
        page.evaluate('window.ringsLab.at(8)')
        page.evaluate('window.ringsLab.fade(4)')
        page.evaluate('window.ringsLab.step(2)')
        page.screenshot(path=os.path.join(out, f'{scenes[-1]}-fade-half.png'))
        page.evaluate('window.ringsLab.step(2.5)')
        page.screenshot(path=os.path.join(out, f'{scenes[-1]}-fade-done.png'))
        # Copy settings: a slider moved shows in "moved", from what it opened at.
        page.evaluate('window.ringsLab.set({front: 400})')
        page.click('#copy')
        copied = page.evaluate("JSON.parse(document.getElementById('settings').value)")
        if copied['moved'].get('front', [None, None])[1] != 400:
            bad.append(f'Copy settings: front moved to 400 is not in {copied["moved"]}')
    browser.close()
with open(os.path.join(out, 'console.log'), 'w') as f:
    f.write(f'renderer: {renderer}\n' + '\n'.join(messages) + '\n')
print(f'renderer: {renderer}')
if clip:
    subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-framerate', '30', '-i', os.path.join(frames, 'f%04d.png'),
                    '-c:v', 'libx264', '-pix_fmt', 'yuv420p', '-crf', '20', clip], check=True)
    print(f'clip: {clip}')
if bad:
    print('FAIL: the page warned or failed:\n  ' + '\n  '.join(bad[:10]))
    sys.exit(1)
print(f'rings lab: {len(scenes)} scenes into {clip or out}, no warning')
