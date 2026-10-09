"""r248: each lab's choices (LabControls.choice) picked as a person picks them, through the dropdown, and what the lab
does with it: the readout or Copy settings must follow. Run from a served war: python3 pick.py BASE CHROME."""
import json
import sys

from playwright.sync_api import sync_playwright

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
    browser = p.chromium.launch(executable_path=chrome, args=["--use-angle=swiftshader", "--enable-unsafe-swiftshader"])
    for path, ready, key, label, expect in CASES:
        page = browser.new_page(viewport={"width": 1580, "height": 710})
        page.goto("%s/%s%s" % (base, path, "&clip=1" if "?" in path else "?clip=1"))
        page.wait_for_function("window.%s === true" % ready, timeout=90000)
        page.select_option("select[data-key=%s]" % key, label=label)
        page.evaluate("window.%sAct(1)" % ready[:-5])
        if expect[0].startswith("#"):
            got = page.text_content(expect[0])
            ok = expect[1] in got
        else:
            page.context.grant_permissions(["clipboard-read", "clipboard-write"])
            page.click("#%s-copy" % expect[0])
            page.wait_for_function("document.getElementById('%s-copy-state').textContent !== ''" % expect[0])
            copied = json.loads(page.input_value("#%s-copy-json" % expect[0]))
            got = copied.get(expect[1], copied["settings"].get(expect[1]))
            ok = got == expect[2]
        print("%s %s %s = %r: %r" % ("ok  " if ok else "FAIL", path, key, label, got))
        if not ok:
            failures.append(key)
        page.close()
    browser.close()
sys.exit(1 if failures else 0)
