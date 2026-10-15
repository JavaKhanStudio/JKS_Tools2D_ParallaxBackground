"""chips.sh's driver (r428): each lab's choices on its panels' titles, clicked, picked, the canvas compared."""
import io
import sys

import numpy as np
from PIL import Image
from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import sync_playwright

base, chrome, out = sys.argv[1:]
# lab, page, ready flag, the lab's Copy settings name; then the choice to pick and the option to pick in it. A lab
# whose clock moves its stage more than a pick does (the transfert's cross-fades) is held to its readout instead,
# which names the page it shows.
LABS = [
    ("hills", "hills-lab.html", "hillsLabReady", "hills", "hillsB", 1),
    ("hills-view", "hills-lab.html", "hillsLabReady", "hills", "view", 2),
    ("shadow", "shadow-lab.html", "shadowLabReady", "shadow", "view", 1),
    ("fog", "fog-lab.html", "fogLabReady", "fog", "parallaxSet", 1),
    ("transfert", "transfert-lab.html", "transfertLabReady", "transfert", "pagePair", 2),
    ("effects-fog", "effects-lab.html?lab=fog", "effectsLabReady", "effects", "parallaxSet", 2),
    ("effects-floor", "effects-lab.html?lab=floor", "effectsLabReady", "effects", "parallaxSet", 1),
]

failures = []
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=chrome, args=["--use-angle=swiftshader", "--enable-unsafe-swiftshader"])
    for name, path, ready, lab, key, option in LABS:
        page = browser.new_page(viewport={"width": 1580, "height": 710})
        errors = []
        page.on("pageerror", lambda e, errors=errors: errors.append(str(e)))
        page.goto(f"{base}/{path}{'&clip=1' if '?' in path else '?clip=1'}")
        page.wait_for_function(f"window.{ready} === true", timeout=90000)
        act = f"window.{ready[:-5]}Act && window.{ready[:-5]}Act(2)"
        # The camera stopped: what moves between the stills is the lab's own clock and the pick.
        page.eval_on_selector("[data-key=cameraScroll]", "e => { e.value = '0'; }")
        page.evaluate(act)
        stage = page.locator(".lab-stage")
        frame = "new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))"
        # The control: the same steps, nothing picked. The lab runs on between them, a pick must move far more.
        readout = page.locator(".lab-readout")
        said = readout.text_content()
        first = np.asarray(Image.open(io.BytesIO(stage.screenshot())).convert("RGB"), dtype=float)
        page.evaluate(act)
        page.evaluate(frame)
        before = np.asarray(Image.open(io.BytesIO(stage.screenshot())).convert("RGB"), dtype=float)
        still = float(np.abs(before - first).mean())
        select = page.locator(f".panel-pick select[data-key={key}]")
        # The mouse, where Simon's goes: what is under the chip's middle must be the select, not the grid over it.
        box = select.bounding_box()
        hit = page.evaluate("([x, y]) => document.elementFromPoint(x, y).dataset.key || null",
                            [box["x"] + box["width"] / 2, box["y"] + box["height"] / 2])
        if hit != key:
            failures.append(f"{name}: the point on {key}'s chip is {hit}")
        try:
            select.click(timeout=5000)
        except PlaywrightError:
            failures.append(f"{name}: {key}'s chip takes no click")
            page.close()
            continue
        select.select_option(str(option))
        page.evaluate(act)
        page.evaluate(frame)
        shot = stage.screenshot(path=f"{out}/{name}-after.png")
        Image.fromarray(before.astype("uint8")).save(f"{out}/{name}-before.png")
        after = np.asarray(Image.open(io.BytesIO(shot)).convert("RGB"), dtype=float)
        moved = float(np.abs(after - before).mean()) if after.shape == before.shape else 255.0
        settings = page.evaluate(f"JSON.parse(labSettings('{lab}', () => ({{}})))")
        picked = settings["settings"].get(key)
        print(f"{name}: {key} -> {option} ({select.evaluate('s => s.selectedOptions[0].text')}), stage moved {moved:.2f}/255 (unpicked {still:.2f}), "
              f"Copy settings {key}={picked}")
        now = readout.text_content()
        if name == "transfert":
            print(f"  readout: {said!r} -> {now!r}")
            if now.split(",")[0] == said.split(",")[0]:
                failures.append(f"{name}: picking {key} left the readout on {now!r}")
        elif moved < still + 1:
            failures.append(f"{name}: picking {key} moved the stage {moved:.2f}/255")
        if picked != option:
            failures.append(f"{name}: Copy settings has {key}={picked}, not {option}")
        if errors:
            failures.append(f"{name}: page errors {errors}")
        page.close()
    browser.close()
print("\n".join(failures) if failures else "every panel choice picks")
sys.exit(1 if failures else 0)
