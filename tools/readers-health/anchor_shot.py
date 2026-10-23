"""Open the readers' dashboard at #features as the parallax board's Site button does (r33), say where the Features
heading lands and shoot the viewport. Needs a serve on 8797 (tools/readers-health.sh --serve 8797).

    python3 tools/readers-health/anchor_shot.py OUT.png [URL]
"""
import sys

from playwright.sync_api import sync_playwright

out = sys.argv[1]
url = sys.argv[2] if len(sys.argv) > 2 else 'http://127.0.0.1:8797/readers-health/#features'
with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page(viewport={'width': 1300, 'height': 900})
    pg.goto(url)
    pg.wait_for_load_state('networkidle')
    top = pg.evaluate("() => { const h = document.getElementById('features'); return h ? Math.round(h.getBoundingClientRect().top) : null; }")
    print(f'#features heading at {top}px from the top of the window, scrolled {pg.evaluate("scrollY")}px')
    pg.screenshot(path=out)
    b.close()
    sys.exit(0 if top is not None and 0 <= top < 100 else 1)
