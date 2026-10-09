"""shots.sh's driver (r232): each lab at the desktop window's size and on a phone, screenshots and fit checks."""
import json
import sys

from playwright.sync_api import sync_playwright

base, chrome, out = sys.argv[1:]
LABS = [
    ("fog", "fog-lab.html", "fogLabReady"),
    ("haze", "haze-lab.html", "hazeLabReady"),
    ("transfert", "transfert-lab.html", "transfertLabReady"),
    ("effects-fog", "effects-lab.html?lab=fog", "effectsLabReady"),
    ("effects-snow", "effects-lab.html?lab=snow", "effectsLabReady"),
    ("effects-all", "effects-lab.html?lab=all", "effectsLabReady"),
    ("browser-tests", "index.html", None),
]
SIZES = {"desktop": (1580, 710), "phone": (390, 844)}

# What the page measures of itself: the lowest slider's bottom, the smallest control text, the page's width.
MEASURE = """() => {
    const inputs = [...document.querySelectorAll('input[type=range], .ctl select')];
    const texts = [...document.querySelectorAll('input[type=range]')].flatMap(i => [...i.parentElement.children].filter(c => c !== i))
        .concat([...document.querySelectorAll('.ctl select')].flatMap(s => [...s.parentElement.children]));
    // A select clips its picked name without a scrollbar: measure the longest option's text against the room left of
    // the arrow (r248).
    const ctx = document.createElement('canvas').getContext('2d');
    const cut = s => { const st = getComputedStyle(s); ctx.font = st.font;
        const room = s.clientWidth - parseFloat(st.paddingLeft) - parseFloat(st.paddingRight) - 20;
        return Math.max(...[...s.options].map(o => ctx.measureText(o.text).width)) > room; };
    return {
        sliders: inputs.length,
        choices: [...document.querySelectorAll('.ctl select')].map(s => s.dataset.key),
        // A slider whose value reads as a name picks among names: it should be a choice (r248).
        namedSliders: [...document.querySelectorAll('input[type=range]')].filter(i => i.nextElementSibling
            && i.nextElementSibling.textContent.trim() !== '' && isNaN(Number(i.nextElementSibling.textContent))).map(i => i.dataset.key),
        lastSliderBottom: inputs.length ? Math.max(...inputs.map(i => i.getBoundingClientRect().bottom)) : 0,
        smallestControlText: texts.length ? Math.min(...texts.map(t => parseFloat(getComputedStyle(t).fontSize))) : null,
        overrun: [...document.querySelectorAll('.ctl')].filter(c => [...c.children].some(k => k.scrollWidth > k.clientWidth + 1 || k.getBoundingClientRect().right > c.getBoundingClientRect().right + 1
                || k.tagName === 'SELECT' && cut(k)))
            .map(c => c.querySelector('[data-key]').dataset.key),
        brokenCells: [...document.querySelectorAll('.lab-results td:first-child')].filter(t => { const r = document.createRange(); r.selectNodeContents(t); return r.getClientRects().length > 1; }).length,
        scrollWidth: document.documentElement.scrollWidth,
        innerWidth: innerWidth,
        innerHeight: innerHeight,
    };
}"""

failures = []
report = {}
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=chrome, args=["--use-angle=swiftshader", "--enable-unsafe-swiftshader"])
    for name, path, ready in LABS:
        for size, (w, h) in SIZES.items():
            page = browser.new_page(viewport={"width": w, "height": h})
            errors = []
            page.on("pageerror", lambda e: errors.append(str(e)))
            page.goto("%s/%s%s" % (base, path, "&clip=1" if "?" in path else "?clip=1") if ready else "%s/%s" % (base, path))
            if ready:
                page.wait_for_function("window.%s === true" % ready, timeout=90000)
                page.evaluate("window.%sAct && window.%sAct(6)" % (ready[:-5], ready[:-5]))
            else:
                page.wait_for_selector("#summary", timeout=90000)
            page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
            m = page.evaluate(MEASURE)
            report["%s-%s" % (name, size)] = m
            page.screenshot(path="%s/%s-%s.png" % (out, name, size), full_page=(size == "phone"))
            if errors:
                failures.append("%s %s: page errors %s" % (name, size, errors))
            if size == "desktop" and m["sliders"] and m["lastSliderBottom"] > m["innerHeight"]:
                failures.append("%s: the last slider ends at %dpx, below the %dpx window" % (name, m["lastSliderBottom"], m["innerHeight"]))
            if m["smallestControlText"] is not None and m["smallestControlText"] < 14:
                failures.append("%s %s: control text at %spx" % (name, size, m["smallestControlText"]))
            if m["overrun"]:
                failures.append("%s %s: a value runs out of its slider's box: %s" % (name, size, m["overrun"]))
            if m["namedSliders"]:
                failures.append("%s %s: sliders over names, not choices: %s" % (name, size, m["namedSliders"]))
            if m["brokenCells"]:
                failures.append("%s %s: %d PASS/FAIL cells broken over lines" % (name, size, m["brokenCells"]))
            if size == "phone" and m["scrollWidth"] > m["innerWidth"]:
                failures.append("%s: %dpx wide on a %dpx phone" % (name, m["scrollWidth"], m["innerWidth"]))
            page.close()
    browser.close()
print(json.dumps(report, indent=1))
print("\n".join(failures) if failures else "every lab fits")
sys.exit(1 if failures else 0)
