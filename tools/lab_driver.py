"""The headless half every tools/*-lab.sh launcher shares (r528): its server half is tools/lab-serve.sh.

    from lab_driver import Lab
    with Lab(url, chrome, "fog", viewport=(1540, 700)) as lab:   # window.fogLabReady awaited
        lab.sliders("cameraScroll=40 view=1")                     # --slider K=V ...
        lab.act(6)                                                # window.fogLabAct(6)
        lab.shot(path)                                            # two animation frames, then the page

Leaving the block fails the run (exit) on a WebGL warning (r419), and on any page error when strict, else prints them.
A launcher keeps only its own modes; what is the same in ten of them is here.
"""
import json
import os
import sys

from playwright.sync_api import Error as PlaywrightError
from playwright.sync_api import sync_playwright

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))  # tools/: browser.py
from browser import launch


def watch(page):
    """Every error the page shows, in errors, and WebGL's own warnings, in webgl: (errors, webgl), filled as it runs."""
    errors, webgl = [], []
    # A failed load is reported below with its URL: the console's line names none (r320: it was the favicon).
    page.on("console", lambda m: m.type == "error" and not m.text.startswith("Failed to load resource") and errors.append(m.text))
    # WebGL's own errors come as warnings, and the frame still draws: a uniform the lab set on a shipped shader of the
    # wrong size painted WAVE's sea white with nothing failing (r419). Any of them fails the run.
    page.on("console", lambda m: m.type == "warning" and "WebGL" in m.text and webgl.append(m.text))
    page.on("pageerror", lambda e: errors.append(str(e)))
    # Chrome asks every page for a favicon: the lab has none.
    page.on("response", lambda r: r.status >= 400 and not r.url.endswith("/favicon.ico") and errors.append(f"{r.status} {r.url}"))
    return errors, webgl


class Lab:
    """One lab page in headless Chrome, its name the prefix of window.<name>LabReady, <name>LabAct and #<name>-copy."""

    def __init__(self, url, chrome, name, viewport=(1540, 700), strict=False):
        self.url, self.chrome, self.name, self.viewport, self.strict = url, chrome, name, viewport, strict

    def __enter__(self):
        self._playwright = sync_playwright().start()
        self.browser = launch(self._playwright, self.chrome)
        self.page = self.browser.new_page(viewport={"width": self.viewport[0], "height": self.viewport[1]})
        self.errors, self.webgl = watch(self.page)
        self.page.goto(self.url)
        try:
            self.page.wait_for_function(f"window.{self.name}LabReady === true", timeout=60000)
        except PlaywrightError:
            self._stop()
            sys.exit(f"the lab never started: {self.errors}")
        return self

    def __exit__(self, kind, value, trace):
        if kind is not None:
            self._stop()
            return False
        try:
            if self.errors:
                if self.strict:
                    sys.exit(f"page errors: {self.errors}")
                print(f"page errors: {self.errors}", file=sys.stderr)
            if self.webgl:
                sys.exit(f"WebGL errors ({len(self.webgl)}), first: {self.webgl[0]}")
        finally:
            self._stop()
        return False

    def _stop(self):
        try:
            self.browser.close()
        finally:
            self._playwright.stop()

    def set(self, key, value):
        """Control `key` (its data-key) to value: a choice at its option's index."""
        self.page.eval_on_selector(f"[data-key={key}]", "(e, v) => { e.value = v; }", str(value))

    def sliders(self, pairs, missing="no slider %s"):
        """--slider's K=V K=V ..., failing on a control the lab has not."""
        for kv in pairs.split():
            key, value = kv.split("=", 1)
            if self.page.query_selector(f"[data-key={key}]") is None:
                sys.exit(missing % key)
            self.set(key, value)

    def options(self, key):
        """How many options choice `key` has."""
        return int(self.page.eval_on_selector(f"[data-key={key}]", "e => e.options.length"))

    def act(self, seconds):
        self.page.evaluate(f"window.{self.name}LabAct({seconds!r})")

    def text(self, selector):
        return self.page.text_content(selector)

    def shot(self, path, **screenshot):
        """The page (full_page unless a clip is given), two animation frames on: the step lands in the next render."""
        self.page.evaluate("new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)))")
        if "clip" not in screenshot:
            screenshot["full_page"] = True
        self.page.screenshot(path=path, **screenshot)

    def clip(self, frames, count, before=None, **screenshot):
        """count frames into frames/f%04d.png, one per 1/30 s of game time, so the clip plays at game speed; before(i)
        runs ahead of each step."""
        for i in range(count):
            if before:
                before(i)
            self.act(0 if i == 0 else 1 / 30)
            self.shot(f"{frames}/f{i:04d}.png", **screenshot)

    def copy(self, keys, moved, then=6, **named):
        """Clicks "Copy settings" after `then` seconds and fails unless its JSON names every control in keys (a set), the
        ones moved as moved says ({key: {"started": a, "now": b}}), and each named field as given; prints and returns it."""
        self.page.context.grant_permissions(["clipboard-read", "clipboard-write"])
        self.act(then)
        self.page.click(f"#{self.name}-copy")
        self.page.wait_for_function(f"document.getElementById('{self.name}-copy-state').textContent !== ''")
        got = json.loads(self.page.input_value(f"#{self.name}-copy-json"))
        if set(got["settings"]) != set(keys) or got["changed"] != moved or any(got.get(k) != v for k, v in named.items()):
            sys.exit(f"Copy settings gave the wrong JSON: {json.dumps(got)}")
        print(f"Copy settings: {self.text(f'#{self.name}-copy-state')}; {json.dumps(got['settings'])}")
        return got
