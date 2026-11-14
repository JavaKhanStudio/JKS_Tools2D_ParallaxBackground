"""The depth lab's input, out of the lab since r539 (DepthOrbit): a drag over the canvas turns the page (the "yaw" and
"pitch" sliders move), the wheel brings it closer ("closer"), and space runs the 2D/3D slide. No frame check drives the
lab's input, so this one does, live, in headless Chrome.

    python3 tools/r539-lab-split/orbit_check.py WAR [CHROME]

Exits 1 when a gesture moves no slider, the wrong one, or by another amount.
"""
import functools
import http.server
import os
import sys
import threading

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))  # tools/: browser.py
from browser import launch
from playwright.sync_api import sync_playwright


class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


def serve(war):
    handler = functools.partial(Quiet, directory=war)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    server = serve(os.path.abspath(sys.argv[1]))
    url = f"http://127.0.0.1:{server.server_address[1]}/depth-lab.html"
    fails = []
    with sync_playwright() as p:
        browser = launch(p, sys.argv[2] if len(sys.argv) > 2 else None)
        page = browser.new_page(viewport={"width": 1540, "height": 800})
        page.goto(url)
        page.wait_for_selector("[data-key=yaw]", timeout=60000)
        page.wait_for_timeout(1500)

        def read():
            return {k: float(page.eval_on_selector(f"[data-key={k}]", "e => e.value")) for k in ("yaw", "pitch", "closer")}

        box = page.query_selector("canvas").bounding_box()
        cx, cy = box["x"] + box["width"] / 2, box["y"] + box["height"] / 2
        before = read()
        page.mouse.move(cx, cy)
        page.mouse.down()
        for i in range(1, 6):
            page.mouse.move(cx - 8 * i, cy + 4 * i)
            page.wait_for_timeout(20)
        page.mouse.up()
        dragged = read()
        print("drag:", before, "->", dragged)
        # 40 px left, 20 down: yaw 0.25 a canvas pixel, pitch 0.2, the page's pixels scaled to the canvas's (1 to 2), the
        # sliders on whole degrees. Swapped axes, a sign or a factor off fall outside.
        turned, raised = dragged["yaw"] - before["yaw"], dragged["pitch"] - before["pitch"]
        if not (9 <= turned <= 21 and 3 <= raised <= 9 and dragged["closer"] == before["closer"]):
            fails.append(f"a drag 40 px left and 20 down turned {turned} (9 to 21 wanted), raised {raised} (3 to 9)")
        page.mouse.wheel(0, -300)
        page.wait_for_timeout(300)
        wheeled = read()
        print("wheel:", wheeled)
        if not wheeled["closer"] > dragged["closer"] or wheeled["yaw"] != dragged["yaw"]:
            fails.append("the wheel up should raise closer alone")
        browser.close()
    server.shutdown()
    for f in fails:
        print("FAIL:", f)
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
