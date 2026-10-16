"""tools/depth-lab.sh's headless driver (r444): stills, a clip, and the seam check (the 3D view at 0 is the game's frame)."""
import math
import sys

import numpy as np
from PIL import Image
from playwright.sync_api import TimeoutError as PlaywrightTimeout
from playwright.sync_api import sync_playwright

url, chrome, frames, mode, big, sliders = sys.argv[1:]
PAGES = 5


def open_lab(browser, query="", viewport=(1540, 1100)):
    page = browser.new_page(viewport={"width": viewport[0], "height": viewport[1]}, device_scale_factor=1)
    errors = []
    page.on("console", lambda m: m.type == "error" and not m.text.startswith("Failed to load resource") and errors.append(m.text))
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.on("response", lambda r: r.status >= 400 and not r.url.endswith("/favicon.ico") and errors.append(f"{r.status} {r.url}"))
    page.goto(url + "?clip=1" + ("&big=1" if big == "1" else "") + query)
    try:
        page.wait_for_function("window.depthLabReady === true", timeout=60000)
    except PlaywrightTimeout:
        sys.exit(f"the lab never started: {errors}")
    for kv in sliders.split():
        key, value = kv.split("=", 1)
        set_control(page, key, value)
    return page, errors


def set_control(page, key, value):
    if page.query_selector(f"[data-key={key}]") is None:
        sys.exit(f"no control {key}")
    page.eval_on_selector(f"[data-key={key}]", "(e, v) => { e.value = v; }", str(value))


# Every shown tag's box and the page's chip, as the browser laid them out (r449).
TAG_BOXES = """() => [...document.querySelectorAll('#depth-tags .depth-tag, #depth-labels > *')]
    .filter(e => getComputedStyle(e).display !== 'none' && e.getBoundingClientRect().width > 0)
    .map(e => { const r = e.getBoundingClientRect(); return [e.innerText.replace(/\\n/g, ' '), r.left, r.top, r.right, r.bottom]; })"""


def overlaps(page):
    """Each pair of tags (or a tag and the page's chip) that cover one another, and each tag the stage cuts, by name."""
    boxes = page.evaluate(TAG_BOXES)
    found = []
    for i, (a, al, at, ar, ab) in enumerate(boxes):
        for b, bl, bt, br, bb in boxes[i + 1:]:
            if min(ar, br) - max(al, bl) > 0.5 and min(ab, bb) - max(at, bt) > 0.5:
                found.append(f"{a!r} over {b!r}")
    stage = page.evaluate("() => { const r = document.getElementById('depth-tags').getBoundingClientRect(); return [r.left, r.top, r.right, r.bottom]; }")
    found += [f"{a!r} cut by the stage's edge" for a, al, at, ar, ab in boxes
              if min(al - stage[0], at - stage[1], stage[2] - ar, stage[3] - ab) < -0.5]
    return found


def shot(page, path, what="#depth-lab"):
    # two animation frames: the step lands in the next render
    page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
    page.locator(what).screenshot(path=path)


with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=chrome, args=["--use-angle=swiftshader", "--enable-unsafe-swiftshader"])
    if mode == "shot":
        page, errors = open_lab(browser)
        page.evaluate("window.depthLabAct(4)")
        shot(page, frames + "/still.png")
    elif mode == "pages":
        # Each page as the lab opens on it (p<i>.png) and with the cards as the camera sees them (p<i>-seen.png), at the
        # slider's start unless --slider set it, then turned round the page: no two tags may cover each other (r449).
        crossed = []
        start = {kv.split("=", 1)[0] for kv in sliders.split()}
        # A phone's first (p<i>-phone.png), its tags smaller, then the desktop's.
        page, errors = open_lab(browser, viewport=(390, 844))
        for i in range(PAGES):
            set_control(page, "page", i)
            page.evaluate("window.depthLabAct(4)")
            shot(page, f"{frames}/p{i}-phone.png")
            crossed += [f"page {i} on a phone: {o}" for o in overlaps(page)]
        page.close()
        page, errors = open_lab(browser)
        for i in range(PAGES):
            set_control(page, "page", i)
            for sizes, suffix in ((None, ""), (0, "-seen")):
                if sizes is not None and "sizes" not in start:
                    set_control(page, "sizes", sizes)
                page.evaluate("window.depthLabAct(4)")
                shot(page, f"{frames}/p{i}{suffix}.png")
                crossed += [f"page {i}{suffix}: {o}" for o in overlaps(page)]
                if "yaw" not in start and "view" not in start:
                    for yaw, view in ((-60, 1), (-20, 1), (20, 1), (85, 1), (66, 0.5)):
                        set_control(page, "yaw", yaw)
                        set_control(page, "view", view)
                        page.evaluate("window.depthLabAct(0.1)")
                        page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
                        crossed += [f"page {i}{suffix} turned {yaw}, view {view}: {o}" for o in overlaps(page)]
                    set_control(page, "yaw", 66)
                    set_control(page, "view", 1)
            if "sizes" not in start:
                set_control(page, "sizes", 1)
        if crossed:
            print("FAIL: tags cover each other:\n  " + "\n  ".join(crossed), file=sys.stderr)
            sys.exit(1)
        print("OK: no tag covers another or is cut, on every page, its cards both ways, turned five ways, and on a phone")
    elif mode == "angles":
        # r458: the default page from three angles (straight on from behind the camera, the side view the lab opens on,
        # steep from above), then the same turned round a full grid of yaw and height, in each unit: no tag may cover
        # another or be cut, and the view is never empty (r458: from behind the camera, low, it was).
        crossed = []
        page, errors = open_lab(browser)
        page.evaluate("window.depthLabAct(4)")
        for name, yaw, pitch in (("straight", 0, 8), ("side", 66, 14), ("steep", 30, 62)):
            set_control(page, "yaw", yaw)
            set_control(page, "pitch", pitch)
            page.evaluate("window.depthLabAct(0.1)")
            shot(page, f"{frames}/{name}.png")
            crossed += [f"{name}: {o}" for o in overlaps(page)]
        for unit in range(3):
            set_control(page, "units", unit)
            # Yaw within 10 of 0 at a height of 3 to 6 degrees was the empty band.
            for yaw in (-85, -60, -30, -10, 0, 10, 30, 60, 85):
                for pitch in (2, 4, 6, 10, 35, 80):
                    set_control(page, "yaw", yaw)
                    set_control(page, "pitch", pitch)
                    page.evaluate("window.depthLabAct(0.05)")
                    page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
                    crossed += [f"unit {unit}, yaw {yaw}, height {pitch}: {o}" for o in overlaps(page)]
                    if not page.evaluate("[...document.querySelectorAll('#depth-tags .depth-card')].some(e => e.style.display !== 'none')"):
                        crossed.append(f"unit {unit}, yaw {yaw}, height {pitch}: no card's tag shown (an empty view?)")
        if crossed:
            print("FAIL:\n  " + "\n  ".join(crossed), file=sys.stderr)
            sys.exit(1)
        print("OK: three angles shot; no tag covers another or is cut, and a card shows, at 54 angles in each of 3 units")
    elif mode == "seam":
        worst = []
        errors = []
        for i in range(PAGES):
            shots = []
            for query in ("&flat=1", ""):
                page, errs = open_lab(browser, query)
                errors += errs
                set_control(page, "page", i)
                set_control(page, "view", 0)
                page.evaluate("window.depthLabAct(4)")
                path = f"{frames}/s{i}{'flat' if query else 'cards'}.png"
                shot(page, path, "#depth-lab canvas")
                shots.append(np.asarray(Image.open(path).convert("RGB")).astype(int))
                page.close()
            diff = np.abs(shots[0] - shots[1]).max(axis=2)
            off = int((diff > 2).sum())
            Image.fromarray((np.minimum(255, diff * 16)).astype(np.uint8)).save(f"{frames}/s{i}diff.png")
            print(f"page {i}: max {diff.max()}/255, {off} of {diff.size} pixels off by more than 2/255, mean {diff.mean():.4f}")
            worst.append((off, diff.max()))
        if any(off > diff.size * 0.001 for off, _ in worst):
            print("FAIL: the 3D view at 0 is not the game's frame", file=sys.stderr)
            sys.exit(1)
        print("OK: the 3D view at 0 is the game's frame on every page")
    else:
        page, errors = open_lab(browser)
        set_control(page, "view", 0)
        page.evaluate("window.depthLabAct(0)")
        for i in range(360):
            t = i / 30
            if i == 30:
                page.click("#depth-slide")
            # A turn round the page from 3.0 s to 9.0 s, then back to 2D.
            if 3.0 <= t <= 9.0:
                u = (t - 3.0) / 6.0
                set_control(page, "yaw", round(38 - 76 * (0.5 - 0.5 * math.cos(math.pi * u)), 2))
                set_control(page, "pitch", round(16 + 14 * math.sin(math.pi * u), 2))
            if i == 300:
                page.click("#depth-slide")
            page.evaluate(f"window.depthLabAct({0 if i == 0 else 1 / 30!r})")
            shot(page, f"{frames}/f{i:04d}.png")
    if errors:
        print(f"page errors: {errors}", file=sys.stderr)
    browser.close()
