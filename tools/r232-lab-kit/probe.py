"""Lists each control's box and its children's, for one lab (r232: how shots.py's value check was found wrong). Run it from a
served war: python3 probe.py BASE CHROME PAGE READY W H."""
import sys
from playwright.sync_api import sync_playwright
base, chrome, path, ready, w, h = sys.argv[1:]
with sync_playwright() as p:
    b = p.chromium.launch(executable_path=chrome, args=["--use-angle=swiftshader", "--enable-unsafe-swiftshader"])
    pg = b.new_page(viewport={"width": int(w), "height": int(h)})
    pg.goto(base + "/" + path)
    pg.wait_for_function("window.%s === true" % ready, timeout=90000)
    print(pg.evaluate("""() => [...document.querySelectorAll('.ctl')].map(c => c.querySelector('input').dataset.key + ' ' +
        Math.round(c.getBoundingClientRect().right) + ': ' + [...c.children].map(k => k.tagName + '=' + Math.round(k.getBoundingClientRect().left) + '..' + Math.round(k.getBoundingClientRect().right) + ' ' + k.textContent).join(' | ')).join('\\n')"""))
    b.close()
