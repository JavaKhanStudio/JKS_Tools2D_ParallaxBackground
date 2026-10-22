"""Lists each control's box and its children's, for one lab (r232: how shots.py's value check was found wrong). Run it from a
served war: python3 probe.py BASE CHROME PAGE READY W H."""
import os
import sys

from playwright.sync_api import sync_playwright

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))  # tools/: browser.py
from browser import launch

base, chrome, path, ready, w, h = sys.argv[1:]
with sync_playwright() as p:
    b = launch(p, chrome)
    pg = b.new_page(viewport={"width": int(w), "height": int(h)})
    pg.goto(base + "/" + path)
    pg.wait_for_function(f"window.{ready} === true", timeout=90000)
    print(pg.evaluate("""() => [...document.querySelectorAll('.ctl')].map(c => c.querySelector('[data-key]').dataset.key + ' ' +
        Math.round(c.getBoundingClientRect().right) + ': ' + [...c.children].map(k => k.tagName + '=' + Math.round(k.getBoundingClientRect().left) + '..' + Math.round(k.getBoundingClientRect().right) + ' ' + k.textContent).join(' | ')).join('\\n')"""))
    b.close()
