#!/usr/bin/env python3
"""r275 (Simon: "it feel like the front element don't move with the ground correctly"): checks that the floor lab's
trees move with the ground under their foot, headless, in each parallax set. Steps the lab (clip mode, the scroll at
40) half a second and measures how far the trees moved and how far the ground row just under their foot
moved, by matching the rows of the two stills. Planted they must move the same within 1 px; with &unplanted=1 (the
page's own speeds, as before) they must not, or the measure is blind. Prints the shifts and exits 1 on a failure.

  python3 tools/r275-floor/planted.py WAR CHROME   (WAR: core/build/browserTest/war, tools/effects-lab.sh builds it;
                                                    needs Playwright, numpy, Pillow)"""
import io, socket, subprocess, sys

import numpy as np
from PIL import Image
from playwright.sync_api import sync_playwright

war, chrome = sys.argv[1:3]
# The canvas is the one panel, 960 x 540 px. Per set (make_lab.py): the trees' foot in frame heights up (Hiver: their
# decal, 8%; PurpleFairy: decal 10.3% + 95 of 1080 trimmed rows of a 0.8 x 22.5 layer), and which pixels are tree
# (Hiver's dark green; PurpleFairy's pink trunks, red well over green). Their trunks are 10 to 30 px over the foot, the
# ground rows 1 and 2 px under it (lower rows are faster, 0.1 px more a row over the half second; a
# second steps PurpleFairy's ground past its own pattern, which the match then aliases).
FRAME_W, FRAME_H = 960, 540
SETS = [("Hiver", 0.08, lambda band: band.sum(axis=2) < 450),
        ("PurpleFairy", (0.103 * 22.5 + 95 / 1080 * 0.8 * 22.5) / 22.5, lambda band: band[..., 0] - band[..., 1] > 60)]


def grab(page):
    png = page.query_selector("canvas").screenshot()
    return np.asarray(Image.open(io.BytesIO(png)).convert("RGB"), dtype=np.float64)[:FRAME_H, :FRAME_W]


def shift(a, b, rows, tree):
    """The x shift (px, b against a) that best matches the rows; for the trees only their pixels ({@code tree}) count."""
    def signal(img):
        band = img[list(rows)]
        if tree:
            return tree(band).astype(np.float64).mean(axis=0)
        return band.mean(axis=2).mean(axis=0)
    sa, sb = signal(a), signal(b)
    best, at = None, 0
    for d in range(-120, 121):
        x = sa[max(0, -d):FRAME_W - max(0, d)]
        y = sb[max(0, d):FRAME_W - max(0, -d)]
        err = np.mean((x - y) ** 2)
        if best is None or err < best:
            best, at = err, d
    return at


def run(index, unplanted):
    port = socket.socket()
    port.bind(("127.0.0.1", 0))
    number = port.getsockname()[1]
    port.close()
    server = subprocess.Popen([sys.executable, "-m", "http.server", str(number), "--bind", "127.0.0.1", "--directory", war],
                              stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    try:
        with sync_playwright() as p:
            browser = p.chromium.launch(executable_path=chrome, args=["--use-angle=swiftshader", "--enable-unsafe-swiftshader"])
            page = browser.new_page(viewport={"width": 1540, "height": 900})
            for attempt in range(50):
                try:
                    page.goto("http://127.0.0.1:%d/effects-lab.html?lab=floor&clip=1%s" % (number, "&unplanted=1" if unplanted else ""))
                    break
                except Exception:
                    page.wait_for_timeout(200)
            page.wait_for_function("window.effectsLabReady === true", timeout=60000)
            page.eval_on_selector("[data-key=parallaxSet]", "(e, v) => { e.value = v; }", str(index))
            frames = []
            for _ in range(2):
                page.evaluate("window.effectsLabAct(0.5)")
                page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
                frames.append(grab(page))
            browser.close()
    finally:
        server.kill()
    a, b = frames
    foot = round(FRAME_H * (1 - SETS[index][1]))
    return shift(a, b, range(foot - 30, foot - 10), SETS[index][2]), shift(a, b, range(foot + 1, foot + 3), None)


failed = False
for index, (set_name, _, _) in enumerate(SETS):
    for unplanted in (False, True):
        trees, ground = run(index, unplanted)
        name = "unplanted" if unplanted else "planted"
        print("%-11s %-9s  trees moved %d px, the ground under them %d px" % (set_name, name, trees, ground))
        if not unplanted and abs(trees - ground) > 1:
            print("FAIL: planted trees slide on the ground")
            failed = True
        if unplanted and abs(trees - ground) <= 1:
            print("FAIL: the trees at the page's speed move with the ground: the measure does not see a slide")
            failed = True
sys.exit(1 if failed else 0)
