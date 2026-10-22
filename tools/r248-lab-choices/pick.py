"""r248: each lab's choices (LabControls.choice) picked as a person picks them, through the dropdown, and what the lab
does with it: the readout or Copy settings must follow. Run from a served war: python3 pick.py BASE CHROME."""
import json
import os
import sys

from playwright.sync_api import sync_playwright

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))  # tools/: browser.py
from browser import launch

base, chrome = sys.argv[1:]
# lab page, ready flag, control, option label to pick, what must then be in the readout (or the copied JSON's field)
CASES = [
    ("fog-lab.html", "fogLabReady", "parallaxSet", "OneNight (round1 s06)", ("#fog-readout", "OneNight (round1 s06):")),
    ("fog-lab.html", "fogLabReady", "fogColor", "dusk pink", ("fog", "fogColor", 3)),
    ("haze-lab.html", "hazeLabReady", "pageTint", "dusk pink", ("#haze-readout", "tint 1 0.6 0.75")),
    ("transfert-lab.html", "transfertLabReady", "pagePair", "PurpleFairy ↔ calm", ("transfert", "pages", "PurpleFairy ↔ calm")),
    ("transfert-lab.html", "transfertLabReady", "gradeColour", "black", ("transfert", "grade", "black")),
    ("effects-lab.html?lab=fog", "effectsLabReady", "parallaxSet", "calm (round1 s02)", ("effects", "parallaxSet", 1)),
    ("effects-lab.html?lab=snow", "effectsLabReady", "snowAnchor", "LAYER", ("effects", "snowAnchor", 1)),
]
failures = []
with sync_playwright() as p:
    browser = launch(p, chrome)
    for path, ready, key, label, expect in CASES:
        page = browser.new_page(viewport={"width": 1580, "height": 710})
        page.goto(f"{base}/{path}{'&clip=1' if '?' in path else '?clip=1'}")
        page.wait_for_function(f"window.{ready} === true", timeout=90000)
        page.select_option(f"select[data-key={key}]", label=label)
        page.evaluate(f"window.{ready[:-5]}Act(1)")
        if expect[0].startswith("#"):
            got = page.text_content(expect[0])
            ok = expect[1] in got
        else:
            page.context.grant_permissions(["clipboard-read", "clipboard-write"])
            page.click(f"#{expect[0]}-copy")
            page.wait_for_function(f"document.getElementById('{expect[0]}-copy-state').textContent !== ''")
            copied = json.loads(page.input_value(f"#{expect[0]}-copy-json"))
            got = copied.get(expect[1], copied["settings"].get(expect[1]))
            ok = got == expect[2]
        print(f"{'ok  ' if ok else 'FAIL'} {path} {key} = {label!r}: {got!r}")
        if not ok:
            failures.append(key)
        page.close()
    browser.close()
sys.exit(1 if failures else 0)
