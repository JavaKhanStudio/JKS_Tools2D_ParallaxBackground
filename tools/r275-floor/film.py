#!/usr/bin/env python3
"""r275: films the floor lab (FloorLab, effects-lab.html?lab=floor) headless, so its motion can be judged and attached:
steps it 1/30 s at a time through window.effectsLabAct and grabs the page's canvas, then ffmpeg makes OUT.mp4.
Serve core/build/browserTest/war first (tools/effects-lab.sh builds it).

  python3 tools/r275-floor/film.py URL CHROME OUT.mp4 [SECONDS] [key=value ...]   (needs Playwright, ffmpeg)

URL: http://127.0.0.1:PORT/effects-lab.html?lab=floor&clip=1. A key=value sets that control (its data-key) first."""
import os
import subprocess
import sys
import tempfile

from playwright.sync_api import sync_playwright

url, chrome, out = sys.argv[1:4]
seconds = float(sys.argv[4]) if len(sys.argv) > 4 else 4
frames = tempfile.mkdtemp()
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=chrome, args=["--use-angle=swiftshader", "--enable-unsafe-swiftshader"])
    page = browser.new_page(viewport={"width": 1540, "height": 900})
    page.goto(url)
    page.wait_for_function("window.effectsLabReady === true", timeout=60000)
    for pair in sys.argv[5:]:
        key, value = pair.split("=", 1)
        page.eval_on_selector(f"[data-key={key}]", "(e, v) => { e.value = v; }", value)
    canvas = page.query_selector("canvas")
    for i in range(int(seconds * 30)):
        page.evaluate("window.effectsLabAct(1 / 30)")
        page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
        canvas.screenshot(path=os.path.join(frames, f"f{i:04d}.png"))
    browser.close()
subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-framerate", "30", "-i", os.path.join(frames, "f%04d.png"),
                "-pix_fmt", "yuv420p", "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2", out], check=True)
print(out)
