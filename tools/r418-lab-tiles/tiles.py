"""tiles.sh's driver (r418): each lab's canvas at the desktop window's size, cut to 16:9 round its middle, 480x270."""
import io
import os
import sys

from PIL import Image
from playwright.sync_api import sync_playwright

base, chrome, out = sys.argv[1:]
# name (the tile's file), page, the lab's ready flag, its panels across and down, and which panel of the top row is the
# tile when not the first: the same pages as tools/r232-lab-kit/shots.py, the suite left out.
LABS = [
    ("anim", "anim-lab.html", "animLabReady", 2, 2),
    ("hills", "hills-lab.html", "hillsLabReady", 2, 1),
    ("pines", "pines-lab.html", "pinesLabReady", 2, 1, 1),
    ("shadow", "shadow-lab.html", "shadowLabReady", 2, 1, 1),
    ("fog", "fog-lab.html", "fogLabReady", 2, 2),
    ("haze", "haze-lab.html", "hazeLabReady", 2, 2),
    ("transfert", "transfert-lab.html", "transfertLabReady", 3, 2),
    ("effects-all", "effects-lab.html?lab=all", "effectsLabReady", 2, 1),
    ("effects-fog", "effects-lab.html?lab=fog", "effectsLabReady", 2, 1),
    ("effects-snow", "effects-lab.html?lab=snow", "effectsLabReady", 2, 1),
    ("effects-floor", "effects-lab.html?lab=floor", "effectsLabReady", 1, 1),
    ("effects-tower", "effects-lab.html?lab=tower", "effectsLabReady", 1, 1),
    ("effects-planet", "effects-lab.html?lab=planet", "effectsLabReady", 1, 1),
    ("effects-lurker", "effects-lab.html?lab=lurker", "effectsLabReady", 1, 1),
]
W, H = 480, 270

os.makedirs(out, exist_ok=True)
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=chrome, args=["--use-angle=swiftshader", "--enable-unsafe-swiftshader"])
    for name, path, ready, cols, rows, *which in LABS:
        page = browser.new_page(viewport={"width": 1580, "height": 710})
        page.goto(f"{base}/{path}{'&' if '?' in path else '?'}clip=1")
        page.wait_for_function(f"window.{ready} === true", timeout=90000)
        page.evaluate(f"window.{ready[:-5]}Act && window.{ready[:-5]}Act(6)")
        page.add_style_tag(content=".lab-labels { display: none; }")  # the panels' titles: the tile names the lab
        page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
        # One panel: a four-panel lab's top left, a two-panel lab's left, a one-panel lab whole; or the one named.
        shot = Image.open(io.BytesIO(page.locator(".lab-stage canvas").screenshot())).convert("RGB")
        page.close()
        x = (which[0] if which else 0) * shot.width // cols
        panel = shot.crop((x, 0, x + shot.width // cols, shot.height // rows))
        scale = max(W / panel.width, H / panel.height)
        panel = panel.resize((round(panel.width * scale), round(panel.height * scale)), Image.LANCZOS)
        left, top = (panel.width - W) // 2, (panel.height - H) // 2
        panel.crop((left, top, left + W, top + H)).save(f"{out}/{name}.jpg", quality=82)
        print(f"{out}/{name}.jpg")
    browser.close()
