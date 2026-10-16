"""zoom.sh's driver (r452): every panel of every lab of several, zoomed, stepped and put back, on a desktop and a phone."""
import io
import os
import sys

import numpy as np
from PIL import Image
from playwright.sync_api import sync_playwright

base, chrome, out = sys.argv[1:]
LABS = [
    ("fog", "fog-lab.html", "fogLabReady", "fog"),
    ("haze", "haze-lab.html", "hazeLabReady", "haze"),
    ("transfert", "transfert-lab.html", "transfertLabReady", "transfert"),
    ("anim", "anim-lab.html", "animLabReady", "anim"),
    ("clouds", "clouds-lab.html", "cloudsLabReady", "clouds"),
    ("hills", "hills-lab.html", "hillsLabReady", "hills"),
    ("pines", "pines-lab.html", "pinesLabReady", "pines"),
    ("shadow", "shadow-lab.html", "shadowLabReady", "shadow"),
    ("effects-all", "effects-lab.html?lab=all", "effectsLabReady", "effects"),
    ("effects-fog", "effects-lab.html?lab=fog", "effectsLabReady", "effects"),
    ("effects-snow", "effects-lab.html?lab=snow", "effectsLabReady", "effects"),
    ("effects-swell", "effects-lab.html?lab=swell", "effectsLabReady", "effects"),
]
# ZOOM_LABS="anim hills": those labs only.
if os.environ.get("ZOOM_LABS"):
    LABS = [lab for lab in LABS if lab[0] in os.environ["ZOOM_LABS"].split()]
SIZES = {"desktop": (1580, 710), "phone": (390, 844)}
FRAME = "new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))"
# Each panel's title as lab.js finds it: the shown chip whose top left corner lies in the panel.
TITLE = """const title = chip => [...chip.querySelectorAll('*:not(option)'), chip].flatMap(e => [...e.childNodes]).map(n => n.nodeType === 3
    ? n.textContent : n.tagName === 'SELECT' ? n.selectedOptions[0].text : '').join(' ').replace(/\\s+/g, ' ').trim();"""
CHIPS = """() => { """ + TITLE + """
    const c = document.querySelector('#embed-browsertests canvas').getBoundingClientRect(), k = c.width / document
        .querySelector('#embed-browsertests canvas').width;
    const chips = [...document.querySelectorAll('.lab-labels > .panel-label')].filter(e => getComputedStyle(e).visibility !== 'hidden');
    const h = document.querySelector('#embed-browsertests canvas').height;
    return window.labPanelRects.slice(0, 16).map(r => { const p = { l: c.left + r[0] * k, t: c.top + (h - r[1] - r[3]) * k,
        r: c.left + (r[0] + r[2]) * k, b: c.top + (h - r[1]) * k };
        const chip = chips.find(e => { const b = e.getBoundingClientRect(); return b.left >= p.l - 2 && b.left < p.r && b.top >= p.t - 2 && b.top < p.b; });
        return { centre: [(p.l + p.r) / 2, (p.t + p.b) / 2], title: chip ? title(chip) : null }; });
}"""
FOCUSED = """() => { """ + TITLE + """ const f = document.querySelector('.lab-labels > .panel-label.lab-focus'); return f ? title(f) : null; }"""


def canvas_image(page):
    """The canvas's picture, without the titles, the zoom's buttons and the shadow lab's dial over it."""
    hide = ".lab-labels, .lab-labels *, .lab-zoom-nav, .lab-zoom-nav *, canvas.sun-dial { visibility: hidden !important; }"
    page.evaluate(f"() => {{ const s = document.createElement('style'); s.id = 'zoom-check'; s.textContent = {hide!r}; document.head.append(s); }}")
    shot = page.locator("#embed-browsertests canvas").screenshot()
    page.evaluate("() => document.getElementById('zoom-check').remove()")
    return np.asarray(Image.open(io.BytesIO(shot)).convert("RGB"), dtype=float)


def thumb(image, box, size=(48, 24)):
    """The part of image in box (fractions of its size: left, top, right, bottom), size wide and tall, for a likeness."""
    h, w = image.shape[:2]
    part = image[int(box[1] * h):max(int(box[1] * h) + 1, int(box[3] * h)), int(box[0] * w):max(int(box[0] * w) + 1, int(box[2] * w))]
    return np.asarray(Image.fromarray(part.astype(np.uint8)).resize(size, Image.BOX), dtype=float)


def where_apart(a, b):
    """The pixels where pictures a and b differ by 8/255 a channel or more; None when fewer than 40 do (panels alike)."""
    mask = np.abs(a - b).mean(axis=2) >= 8
    return mask if mask.sum() >= 40 else None


failures = []
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=chrome, args=["--use-angle=swiftshader", "--enable-unsafe-swiftshader"])
    for name, path, ready, lab in LABS:
        for size, (w, h) in SIZES.items():
            phone = size == "phone"
            # A phone at 4 device pixels a CSS pixel: its screenshots as fine as the canvas itself (about 1500 wide), or
            # the effects a panel shows are lost in the pixels.
            context = browser.new_context(viewport={"width": w, "height": h}, has_touch=phone, is_mobile=phone, device_scale_factor=4 if phone else 1)
            page = context.new_page()
            errors = []
            page.on("pageerror", lambda e, errors=errors: errors.append(str(e)))
            page.goto(f"{base}/{path}{'&clip=1' if '?' in path else '?clip=1'}")
            page.wait_for_function(f"window.{ready} === true", timeout=90000)
            page.evaluate(f"window.{ready[:-5]}Act(2)")
            page.evaluate(FRAME)
            settings = page.evaluate(f"labSettings('{lab}', () => ({{}}))")
            count = page.evaluate("document.querySelector('.lab-stage').classList.contains('lab-zoomable') ? window.labPanelRects.length : 0")
            rects = page.evaluate("window.labPanelRects.map(r => r.slice())")
            home = page.evaluate("[document.querySelector('#embed-browsertests canvas').width, document.querySelector('#embed-browsertests canvas').height]")
            panels = page.evaluate(CHIPS)[:count]
            if count < 2:
                failures.append(f"{name} {size}: no zoom ({count} panels)")
                context.close()
                continue
            page.locator(".lab-stage").screenshot(path=f"{out}/{name}-{size}-all.png")
            every = canvas_image(page)
            # Each panel's picture with every panel shown, and where it is zoomed: its shape, scaled about the middle.
            bw, bh = home
            boxes = [(x / bw, (bh - y - ph) / bh, (x + pw) / bw, (bh - y) / bh) for x, y, pw, ph in rects[:count]]
            shown = [thumb(every, b) for b in boxes]
            # The same, at the panel's own pixels: panels a slider's values apart (hills, snow, the sea's
            # four effects) differ in a few places only, which the coarse likeness averages away.
            fine = (rects[0][2], rects[0][3])
            shown_fine = [thumb(every, b, fine) for b in boxes]
            for i, panel in enumerate(panels):
                cx, cy = panel["centre"]
                if phone:
                    page.touchscreen.tap(cx, cy)
                else:
                    page.mouse.click(cx, cy)
                page.evaluate(FRAME)
                page.evaluate(FRAME)
                focus = page.evaluate("window.labFocus")
                if focus != i:
                    failures.append(f"{name} {size}: a click on panel {i} zoomed {focus}")
                    page.evaluate("labZoom(-1)")
                    page.evaluate(FRAME)
                    continue
                page.locator(".lab-stage").screenshot(path=f"{out}/{name}-{size}-p{i}.png")
                zoomed = canvas_image(page)
                zh, zw = zoomed.shape[:2]
                pw, ph = rects[i][2], rects[i][3]
                k = min(zw / pw, zh / ph)
                box = ((zw - pw * k) / 2 / zw, (zh - ph * k) / 2 / zh, (zw + pw * k) / 2 / zw, (zh + ph * k) / 2 / zh)
                seen = thumb(zoomed, box)
                off = [float(np.abs(seen - s).mean()) for s in shown]
                grew = zw * box[2] - zw * box[0] > every.shape[1] * pw / bw * 1.3 if every.shape[1] else False
                print(f"{name} {size} panel {i} ({panel['title']!r}): zoomed {zw}x{zh}, off each panel {[round(o, 1) for o in off]}, larger: {grew}")
                # Not clearly closer to another panel's picture than to its own, of the panels that look different from it (by
                # 3/255 or more; a wrong panel is 6 to 35/255 off): panels alike, a slider's three values, are told apart by
                # their titles below. Fine patterns (D's dissolve) resampled to a phone are 2.5/255 off their own.
                if off[i] > 8:
                    failures.append(f"{name} {size}: zoomed panel {i} is {off[i]:.1f}/255 off its own picture (2.8 at most when right)")
                for j in range(count):
                    apart = float(np.abs(shown[i] - shown[j]).mean())
                    if j != i and apart >= 3 and off[j] < 0.6 * off[i]:
                        failures.append(f"{name} {size}: zoomed panel {i} looks like panel {j} ({off[j]:.1f}/255 off), not itself ({off[i]:.1f}/255)")
                # Finer, on only the pixels where panel i and panel j differ: closer to j's there is j's picture. On a desktop
                # only, the canvas 1:1: a phone's is scaled by its page, its panels a fraction of a pixel off, which reads
                # as a difference wherever the panels differ (a right zoom was 2x closer to another panel there).
                seen_fine = thumb(zoomed, box, fine)
                for j in range(count if not phone else 0):
                    mask = None if j == i else where_apart(shown_fine[i], shown_fine[j])
                    if mask is None:
                        continue
                    own, other = (float(np.abs(seen_fine - s)[mask].mean()) for s in (shown_fine[i], shown_fine[j]))
                    if other < own:
                        failures.append(f"{name} {size}: zoomed panel {i}, where it and panel {j} differ ({mask.sum()} px), looks like"
                                        f" panel {j} ({other:.1f}/255 off), not itself ({own:.1f}/255)")
                if not grew:
                    failures.append(f"{name} {size}: panel {i} zoomed no larger than it was")
                title = page.evaluate(FOCUSED)
                if panel["title"] and title != panel["title"]:
                    failures.append(f"{name} {size}: panel {i} zoomed under the title {title!r}, not {panel['title']!r}")
                # One step on (back from the last, whose next is off), and back to all.
                on = 1 if i < count - 1 else -1
                if phone:
                    page.locator(".lab-zoom-next" if on > 0 else ".lab-zoom-prev").tap()
                else:
                    page.keyboard.press("ArrowRight" if on > 0 else "ArrowLeft")
                page.evaluate(FRAME)
                stepped = page.evaluate("window.labFocus")
                if stepped != i + on:
                    failures.append(f"{name} {size}: a step from panel {i} went to {stepped}")
                if phone:
                    page.locator(".lab-zoom-back").tap()
                else:
                    page.keyboard.press("Escape")
                page.evaluate(FRAME)
                page.evaluate(FRAME)
                back = page.evaluate("[window.labFocus, document.querySelector('#embed-browsertests canvas').width, document.querySelector('#embed-browsertests canvas').height]")
                if back != [-1] + home:
                    failures.append(f"{name} {size}: back from panel {i} is {back}, not {[-1] + home}")
            again = canvas_image(page)
            if again.shape != every.shape or float(np.abs(again - every).mean()) > 1:
                failures.append(f"{name} {size}: the stage came back changed")
            if page.evaluate(f"labSettings('{lab}', () => ({{}}))") != settings:
                failures.append(f"{name} {size}: Copy settings moved")
            if errors:
                failures.append(f"{name} {size}: page errors {errors}")
            context.close()
    browser.close()
print("\n".join(failures) if failures else "every panel zooms, steps and comes back")
sys.exit(1 if failures else 0)
