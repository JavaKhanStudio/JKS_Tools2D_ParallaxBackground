"""r549: the labs page (core/browser-test/webapp/index.html) served as is, its tiles at desktop and phone width.
  python3 tools/r549-tiles/page.py OUT_DIR       writes OUT_DIR/desktop.png and OUT_DIR/phone.png
"""
import functools
import http.server
import os
import sys
import threading

from playwright.sync_api import sync_playwright

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))  # tools/: browser.py
from browser import launch

out = sys.argv[1]
root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "../../core/browser-test/webapp")
handler = functools.partial(http.server.SimpleHTTPRequestHandler, directory=root)
server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
threading.Thread(target=server.serve_forever, daemon=True).start()
url = f"http://127.0.0.1:{server.server_address[1]}/index.html"
chrome = os.environ.get("CHROME", "/usr/bin/google-chrome")
with sync_playwright() as p:
    browser = launch(p, chrome)
    for name, width in (("desktop", 1580), ("phone", 390)):
        page = browser.new_page(viewport={"width": width, "height": 800})
        page.goto(url)
        page.wait_for_function("[...document.images].every(i => i.complete)")
        broken = page.evaluate("[...document.images].filter(i => !i.naturalWidth).map(i => i.src)")
        page.screenshot(path=f"{out}/{name}.png", full_page=True)
        print(name, "broken images:", broken)
    browser.close()
server.shutdown()
