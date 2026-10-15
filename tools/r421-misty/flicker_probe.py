"""r421: what a compare page's Flicker does, in headless Chrome: per view, what shows each tick, its tags and line,
and for films how far the second drifts from the first and how often it is sought (a seek is a stutter).

    python3 tools/r421-misty/flicker_probe.py URL_OR_HTML [SECONDS]
"""
import os
import sys

from playwright.sync_api import sync_playwright

url, secs = sys.argv[1], float(sys.argv[2]) if len(sys.argv) > 2 else 6
if os.path.exists(url):
    url = 'file://' + os.path.abspath(url)
with sync_playwright() as p:
    b = p.chromium.launch(executable_path='/usr/bin/google-chrome', headless=True,
                          args=['--autoplay-policy=no-user-gesture-required'])
    pg = b.new_page(viewport={'width': 1400, 'height': 900})
    pg.goto(url)
    pg.wait_for_timeout(1500)
    pg.evaluate("""() => { window.seeks = 0; window.drift = [];
      document.querySelectorAll('video').forEach(v => v.addEventListener('seeking', () => window.seeks++)); }""")
    pg.locator('.view').first.locator('.flick').click()
    states = []
    for _ in range(int(secs * 4)):
        pg.wait_for_timeout(250)
        states.append(pg.evaluate("""() => { const v = document.querySelector('.view'), box = v.querySelector('.box');
          const it = [...box.querySelectorAll('.item')];
          const vis = it.map(e => { const s = getComputedStyle(e); return s.visibility === 'visible' ?
              (s.clipPath === 'none' ? 'whole' : s.clipPath) : '-'; });
          const vids = it.filter(e => e.tagName === 'VIDEO');
          return {vis, tags: [...v.querySelectorAll('.tag')].map(t => t.textContent + (t.offsetParent ? '' : '(hidden)')),
            line: getComputedStyle(v.querySelector('.line')).left + ' ' + getComputedStyle(v.querySelector('.line')).display,
            flickOn: v.querySelector('.flick').classList.contains('on'),
            drift: vids.length > 1 ? +(vids[1].currentTime - vids[0].currentTime).toFixed(3) : null}; }"""))
    for s in states:
        print(s)
    print('seeks in', secs, 's:', pg.evaluate('window.seeks'))
    b.close()
