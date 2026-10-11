#!/usr/bin/env python3
"""r275: checks the floor lab's pan by drag, headless (the lab live, not stepped): the scroll at 0, a still, then the
mouse dragged 200 px right across the panel and held still: the ground must have moved, and two stills taken 0.5 s apart
with the button still down must be the same (a pan that keeps going under a still pointer fails). Prints the mean
differences and exits 1 on a failure. Serve core/build/browserTest/war first.

  python3 tools/r275-floor/drag.py URL CHROME OUTDIR   (URL: .../effects-lab.html?lab=floor; needs Playwright, Pillow)"""
import io
import os
import sys

from PIL import Image, ImageChops, ImageStat
from playwright.sync_api import sync_playwright

url, chrome, out = sys.argv[1:4]
os.makedirs(out, exist_ok=True)


def grab(page, name):
    png = page.query_selector("canvas").screenshot()
    with open(os.path.join(out, name), "wb") as f:
        f.write(png)
    return Image.open(io.BytesIO(png)).convert("L")


def diff(a, b):
    return ImageStat.Stat(ImageChops.difference(a, b)).mean[0]


with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=chrome, args=["--use-angle=swiftshader", "--enable-unsafe-swiftshader"])
    page = browser.new_page(viewport={"width": 1540, "height": 900})
    page.goto(url)
    page.wait_for_selector("[data-key=cameraScroll]", timeout=60000)
    page.eval_on_selector("[data-key=cameraScroll]", "e => { e.value = '0'; }")
    page.wait_for_timeout(500)
    before = grab(page, "before.png")
    box = page.query_selector("canvas").bounding_box()
    x, y = box["x"] + 200, box["y"] + box["height"] * 0.85
    page.mouse.move(x, y)
    page.mouse.down()
    for i in range(1, 21):
        page.mouse.move(x + 10 * i, y)
        page.wait_for_timeout(30)
    page.wait_for_timeout(300)
    held1 = grab(page, "held1.png")
    page.wait_for_timeout(500)
    held2 = grab(page, "held2.png")
    page.mouse.up()
    browser.close()
moved, drift = diff(before, held1), diff(held1, held2)
print("drag moved the picture by a mean %.2f/255; while held still it moved %.2f/255" % (moved, drift))
sys.exit(0 if moved > 2 and drift < 0.5 else 1)
