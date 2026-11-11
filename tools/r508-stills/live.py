#!/usr/bin/env python3
"""Does the live depth lab drop layers too? (r508) Opens it as Simon does (no &clip=1, its own requestAnimationFrame loop), multisampled
or not, and watches what it draws two ways, never both on one page:
  readback    in the same task as each of the lab's frames, the canvas drawn small and compared with the frame before;
  screenshot  page.screenshot in a loop: what the compositor shows, with nothing in the page reading the canvas.
  python3 tools/r508-stills/live.py OUT SECONDS [--window] [--intel]    (after ./gradlew :core:browserTestWar)
--window a real window with tools/depth-lab.sh's flags (none for the GPU), so through tools/offscreen.sh; --intel Playwright's chromium, which finds the Intel GPU, as a desktop window does.
R508_FLAGS, Chrome flags added to the launch, headless on the GPU's or in a window. R508_CONTEXT='{stencil: false}' forces context options on both arms, to look for the cause.
A jump is a frame's step over JUMP/255 on average or, read back at each frame, over SHARE of the pixels off by OFF/255: the lab moves
slowly, a layer lost does not. Screenshots are 100s of ms apart, so only the average counts there.
OUT/<arm>-<way>-p<page>-v<view>/ keeps the frames either side of its worst jump. Prints one line per run; a last line per arm.
"""
import functools
import http.server
import io
import os
import shutil
import sys
import threading
from pathlib import Path

import numpy as np
from PIL import Image
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "tools"))
from browser import GPU, launch  # noqa: E402

FLAGS = os.environ.get("R508_FLAGS", "").split()
WAR = ROOT / "core/build/browserTest/war"
JUMP, OFF, SHARE = 15, 8, 0.01
WIDTH, HEIGHT = 320, 180
ARMS = {"aa": "", "noaa": "&noaa=1"}
PLACES = ((1, 1), (4, 1), (0, 0), (4, 0))
# Runs a frame's callbacks, then reads the canvas in the same task: what the page hands the compositor.
WATCH = """(() => { const real = window.requestAnimationFrame.bind(window);
  window.r508 = {on: false, frames: 0, steps: [], keep: null, worst: -1};
  const small = document.createElement('canvas');
  small.width = %d; small.height = %d;
  const c = small.getContext('2d', {willReadFrequently: true});
  let last = null;
  window.requestAnimationFrame = cb => real(t => { cb(t);
    const live = document.querySelector('#depth-lab canvas');
    if (!window.r508.on || !live) return;
    c.drawImage(live, 0, 0, small.width, small.height);
    const now = c.getImageData(0, 0, small.width, small.height).data;
    window.r508.frames++;
    if (last) { let sum = 0, off = 0;
      for (let i = 0; i < now.length; i += 4) { const d = Math.max(Math.abs(now[i] - last[i]), Math.abs(now[i + 1] - last[i + 1]), Math.abs(now[i + 2] - last[i + 2])); sum += d; if (d > %d) off++; }
      const mean = sum / (now.length / 4), share = off / (now.length / 4);
      window.r508.steps.push([mean, share]);
      if (mean > window.r508.worst) { window.r508.worst = mean; window.r508.keep = [last, now]; } }
    last = now; });
})()"""
CONTEXT = """(() => { const real = HTMLCanvasElement.prototype.getContext;
  HTMLCanvasElement.prototype.getContext = function (kind, options) { return real.call(this, kind, Object.assign({}, options, %s)); }; })()"""
SET = "([k, v]) => { document.querySelector(`[data-key=\"${k}\"]`).value = v; }"
RENDERER = """() => { const g = document.querySelector('#depth-lab canvas'); const c = g.getContext('webgl2') || g.getContext('webgl');
  const x = c.getExtension('WEBGL_debug_renderer_info'); return c.getParameter(x.UNMASKED_RENDERER_WEBGL).replace(/.*\\((.*)\\).*/, '$1').slice(0, 40); }"""
KEEP = "() => window.r508.keep && window.r508.keep.map(a => Array.from(a))"


class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


def jumps(steps, shares=True):
    return sum(1 for mean, share in steps if mean > JUMP or (shares and share > SHARE))


def save(where, frames):
    where.mkdir(parents=True, exist_ok=True)
    for name, frame in zip(("before", "after"), frames):
        frame.save(where / f"{name}.png")


def run(browser, url, arm, way, pg, view, seconds, out):
    page = browser.new_page(viewport={"width": 1540, "height": 1100}, device_scale_factor=1)
    warned = []
    page.on("console", lambda m: m.type in ("warning", "error") and not m.text.startswith("Failed to load") and warned.append(m.text))
    if way == "readback":
        page.add_init_script(WATCH % (WIDTH, HEIGHT, OFF))
    if os.environ.get("R508_CONTEXT"):
        page.add_init_script(CONTEXT % os.environ["R508_CONTEXT"])
    page.goto(url + "?x=1" + ARMS[arm])
    page.wait_for_function("document.querySelector('[data-key=\"view\"]') !== null", timeout=120000)
    context = page.evaluate("(c => JSON.stringify((c.getContext('webgl2') || c.getContext('webgl')).getContextAttributes().antialias))(document.querySelector('#depth-lab canvas'))")
    if context != ("true" if arm == "aa" else "false"):
        sys.exit(f"{arm}: the canvas's antialias reads {context}")
    renderer = page.evaluate(RENDERER)
    page.evaluate(SET, ["page", str(pg)])
    page.evaluate(SET, ["view", str(view)])
    page.wait_for_timeout(1500)  # the page's change is not a jump
    name = f"{arm}-{way}-p{pg}-v{view}"
    if way == "readback":
        page.evaluate("window.r508.on = true")
        page.wait_for_timeout(seconds * 1000)
        page.evaluate("window.r508.on = false")
        steps = page.evaluate("window.r508.steps")
        count = jumps(steps)
        if count:
            kept = page.evaluate(KEEP)
            save(out / name, [Image.fromarray(np.array(k, dtype=np.uint8).reshape(HEIGHT, WIDTH, 4)[:, :, :3]) for k in kept])
    else:
        steps, last, kept, worst = [], None, None, -1
        locator = page.locator("#depth-lab canvas")
        end = page.evaluate("performance.now()") + seconds * 1000
        while page.evaluate("performance.now()") < end:
            img = Image.open(io.BytesIO(locator.screenshot(type="png"))).convert("RGB").resize((WIDTH, HEIGHT), Image.BILINEAR)
            now = np.asarray(img).astype(int)
            if last is not None:
                d = np.abs(now - last[0]).max(axis=2)
                steps.append((float(d.mean()), float((d > OFF).mean())))
                if steps[-1][0] > worst:
                    worst, kept = steps[-1][0], (last[1], img)
            last = (now, img)
        count = jumps(steps, False)
        if count:
            save(out / name, kept)
    page.close()
    worst_mean = max((s[0] for s in steps), default=0)
    print(f"{name}: {renderer} {count} jumps of {len(steps)} steps, worst {worst_mean:.1f}/255 on average, {max((s[1] for s in steps), default=0):.3f} of the pixels; warnings {len(warned)}", flush=True)
    return count, len(steps)


def main():
    out = Path(sys.argv[1]).resolve()
    seconds = int(sys.argv[2])
    window = "--window" in sys.argv
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(Quiet, directory=str(WAR)))
    threading.Thread(target=server.serve_forever, daemon=True).start()
    url = f"http://127.0.0.1:{server.server_port}/depth-lab.html"
    totals = {}
    with sync_playwright() as p:
        chrome = shutil.which("google-chrome") or shutil.which("chromium")
        # Not tools/browser.py's launch (AGENTS.md, r488) on either branch below: --window opens a real Chrome
        # window on Wayland (headless=False), and FLAGS are the variable r508 probes, which browser.py would swap
        # for its own and could hide behind its SwiftShader fallback.
        if window:
            browser = p.chromium.launch(executable_path=chrome, headless=False, args=["--ozone-platform=wayland"] + FLAGS)
        elif FLAGS:
            browser = p.chromium.launch(executable_path=chrome, args=GPU + FLAGS)
        else:
            browser = launch(p, None if "--intel" in sys.argv else chrome)
        for pg, view in PLACES:
            for way in ("readback", "screenshot"):
                for arm in ARMS:  # turn about, so both arms see the same load
                    count, steps = run(browser, url, arm, way, pg, view, seconds, out)
                    a = totals.setdefault((arm, way), [0, 0])
                    a[0] += count
                    a[1] += steps
        browser.close()
    for (arm, way), (count, steps) in totals.items():
        print(f"TOTAL {arm} {way}: {count} jumps of {steps} steps", flush=True)


if __name__ == "__main__":
    main()
