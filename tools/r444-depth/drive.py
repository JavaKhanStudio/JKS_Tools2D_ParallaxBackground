"""tools/depth-lab.sh's headless driver (r444): stills, a clip, and the seam check (the 3D view at 0 is the game's frame)."""
import math
import sys

import numpy as np
from PIL import Image
from playwright.sync_api import TimeoutError as PlaywrightTimeout
from playwright.sync_api import sync_playwright

url, chrome, frames, mode, big, sliders = sys.argv[1:]
PAGES = 5


def open_lab(browser, query=""):
    page = browser.new_page(viewport={"width": 1540, "height": 1100}, device_scale_factor=1)
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
        page, errors = open_lab(browser)
        for i in range(PAGES):
            set_control(page, "page", i)
            page.evaluate("window.depthLabAct(4)")
            shot(page, f"{frames}/p{i}.png")
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
