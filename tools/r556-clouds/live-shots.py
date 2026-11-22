"""r556: the clouds lab as Simon opens it, playing live (no stepped clock), shot for the card.

    python3 tools/r556-clouds/live-shots.py OUT [CHROME]

Serves core/build/browserTest/war as tools/lab-serve.sh does (build it first: ./gradlew :core:browserTestWar), opens
clouds-lab.html in headless Chrome on the GPU (tools/browser.py) at 1100 px wide and on a 390 px phone, lets it play
6 s of real time, and shoots the stage (the panels under their titles): OUT/wide-view<N>.png for each view at 1100,
OUT/phone-view0.png, and OUT/phone-zoom.png, the phone's first panel tapped open over the stage. Exits 1 on a page
error or a WebGL warning (tools/lab_driver.py's watch), or when the stage does not move between two shots 1 s apart: a
lab that only draws when stepped is not the lab he sees.
"""
import functools
import http.server
import io
import os
import shutil
import sys
import threading

from PIL import Image, ImageChops
from playwright.sync_api import sync_playwright

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))  # tools/: browser.py, lab_driver.py
from browser import launch
from lab_driver import watch

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
WAR = os.path.join(ROOT, "core/build/browserTest/war")
STAGE = "#clouds-lab"


class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


def stage(page):
    return Image.open(io.BytesIO(page.locator(STAGE).screenshot())).convert("RGB")


def main():
    out = sys.argv[1]
    chrome = sys.argv[2] if len(sys.argv) > 2 else shutil.which("google-chrome") or shutil.which("chromium") \
        or shutil.which("chromium-browser")
    os.makedirs(out, exist_ok=True)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(Quiet, directory=WAR))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    url = f"http://127.0.0.1:{server.server_port}/clouds-lab.html"
    failed = []
    with sync_playwright() as p:
        browser = launch(p, chrome)
        for name, width, height in (("wide", 1100, 800), ("phone", 390, 844)):
            page = browser.new_page(viewport={"width": width, "height": height})
            errors, webgl = watch(page)
            page.goto(url)
            page.wait_for_selector(STAGE + " canvas")
            page.wait_for_selector("select[data-key=view]")
            views = int(page.eval_on_selector("select[data-key=view]", "e => e.options.length"))
            for view in range(views if name == "wide" else 1):
                page.select_option("select[data-key=view]", index=view)
                page.wait_for_timeout(6000 if view == 0 else 1500)
                first = stage(page)
                page.wait_for_timeout(1000)
                second = stage(page)
                if ImageChops.difference(first, second).getbbox() is None:
                    failed.append(f"{name} view {view}: the stage did not move in 1 s of live play")
                second.save(f"{out}/{name}-view{view}.png")
                print(f"{name} view {view}: {second.size[0]}x{second.size[1]}, live")
            if name == "phone":
                box = page.locator(STAGE + " canvas").bounding_box()
                page.mouse.click(box["x"] + box["width"] / 6, box["y"] + box["height"] / 2)
                page.wait_for_timeout(1200)
                stage(page).save(f"{out}/phone-zoom.png")
                print("phone: first panel tapped open")
            if errors or webgl:
                failed.append(f"{name}: page errors {errors}, WebGL warnings {webgl}")
            page.close()
        browser.close()
    server.shutdown()
    if failed:
        sys.exit("\n".join(failed))
    print("the lab plays live with no error at 1100 and 390")


if __name__ == "__main__":
    main()
