"""lab-check.py URL CHROME OUT SECONDS: lab-check.sh's driver (r369). See lab-check.sh."""
import io
import json
import os
import sys

import numpy as np
from PIL import Image
from playwright.sync_api import sync_playwright

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))  # tools/: browser.py
from browser import launch

url, chrome, out, seconds = sys.argv[1], sys.argv[2], sys.argv[3], float(sys.argv[4])
STEPS = round(seconds * 60)


def opened(browser, query, mode):
    page = browser.new_page(viewport={'width': 1540, 'height': 900})
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.goto(url + query)
    page.wait_for_function('window.effectsLabReady === true', timeout=90000)
    page.evaluate(f"""() => {{ const s = document.querySelector('[data-key=switchWhen]');
        s.value = '{mode}'; s.dispatchEvent(new Event('change')); }}""")
    return page, errors


def step(page):
    page.evaluate('window.effectsLabAct(1 / 60)')
    return page.evaluate("document.getElementById('effects-readout').textContent")


def frame(page):
    page.evaluate('new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))')
    return np.asarray(Image.open(io.BytesIO(page.locator('canvas').first.screenshot())).convert('RGB')).astype(int)


report = {'seconds': seconds}
failures = []
with sync_playwright() as p:
    browser = launch(p, chrome)
    there, errors_a = opened(browser, '', 2)
    gone, errors_b = opened(browser, '&figure=gone', 2)
    covered, worst, uncovered_seen, uncovered_shot = 0, (0, None, None), 0, None
    for n in range(STEPS):
        line = step(there)
        step(gone)
        if 'covered by a nearer layer' in line:
            covered += 1
            a, b = frame(there), frame(gone)
            d = int(np.abs(a - b).max())
            if d >= worst[0]:
                worst = (d, n, np.concatenate([a, b], axis=0))
        elif 'uncovered' in line and n % 20 == 0:
            a, b = frame(there), frame(gone)
            diff = np.abs(a - b).max(axis=2)
            if (diff > 0).any():
                uncovered_seen += 1
                if uncovered_shot is None or (diff > 0).sum() > uncovered_shot[0]:
                    uncovered_shot = ((diff > 0).sum(), n, np.concatenate([a, b], axis=0))
    report.update({'coveredSteps': covered, 'worstCoveredDiff': worst[0], 'worstCoveredStep': worst[1],
                   'uncoveredSampledWithFigure': uncovered_seen})
    if worst[2] is not None:
        Image.fromarray(worst[2].astype(np.uint8)).save(f'{out}/worst-covered.png')
    if uncovered_shot is not None:
        Image.fromarray(uncovered_shot[2].astype(np.uint8)).save(f'{out}/uncovered.png')
        report['uncoveredShotStep'] = uncovered_shot[1]
    if covered == 0:
        failures.append('no step was covered: nothing checked')
    if worst[0] > 0:
        failures.append(f'a covered step shows the figure: {worst[0]}/255 at step {worst[1]}')
    if uncovered_seen == 0:
        failures.append('no uncovered step showed the figure: the check sees nothing')
    for mode, name in ((0, 'readerSightings'), (1, 'everyTwoSeconds')):
        page, errors = opened(browser, '', mode)
        line = page.evaluate(f'window.effectsLabAct({seconds}); document.getElementById("effects-readout").textContent')
        switches = int(line.split('; ')[1].split(' switches')[0])
        in_sight = int(line.split(' switches, ')[1].split(' of them')[0])
        report[name] = {'switches': switches, 'whileUncovered': in_sight, 'readout': line}
        errors_a += errors
    if report['readerSightings']['whileUncovered'] or not report['readerSightings']['switches']:
        failures.append(f"the reader's sightings: {report['readerSightings']['readout']}")
    if not report['everyTwoSeconds']['whileUncovered']:
        failures.append(f"every 2 s never switched in sight: {report['everyTwoSeconds']['readout']}")
    if errors_a or errors_b:
        failures.append(f'page errors: {errors_a + errors_b}')
report['failures'] = failures
with open(f'{out}/report.json', 'w') as f:
    json.dump(report, f, indent=1)
print(json.dumps(report, indent=1))
print('FAIL' if failures else 'OK: every covered step is the same to the pixel with and without the figure')
sys.exit(1 if failures else 0)
