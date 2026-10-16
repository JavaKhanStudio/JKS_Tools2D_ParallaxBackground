"""r453: where the pines lab's panels differ. Marks on, the clip stepped a second at a time from its start, the red
pixels (where misty_pines on and off differ) counted on the canvas each second: 0 for the first 9 s before r453's
start of 19 s, about 700 to 1100 for the first 8 s after. Serve core/build/browserTest/war first (tools/pines-lab.sh).
    python3 tools/r453-pines-start/scan.py http://127.0.0.1:PORT/pines-lab.html CHROME SECONDS
"""
import io
import sys

import numpy as np
from PIL import Image
from playwright.sync_api import sync_playwright

url, chrome, seconds = sys.argv[1], sys.argv[2], int(sys.argv[3])
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=chrome, args=["--use-angle=swiftshader", "--enable-unsafe-swiftshader"])
    page = browser.new_page(viewport={"width": 1580, "height": 710})
    page.goto(url + "?clip=1")
    page.wait_for_function("window.pinesLabReady === true", timeout=90000)
    page.eval_on_selector("[data-key=marks]", "e => { e.value = '1'; }")
    for t in range(seconds):
        page.evaluate(f"window.pinesLabAct({0 if t == 0 else 1})")
        page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
        shot = page.locator("#embed-browsertests canvas").screenshot()
        img = np.asarray(Image.open(io.BytesIO(shot)).convert("RGB")).astype(int)
        print(t, int(((img[..., 0] > 200) & (img[..., 1] < 60) & (img[..., 2] < 60)).sum()))
    browser.close()
