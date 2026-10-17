"""The shadow lab's whole day as Simon plays it (r425, 16 Oct 17:19: "Why is it shaking so much?"): no ?clip=1, the lab's
own clock, on the GPU, the day button pressed; a still every --every seconds, and the sun's track measured on them.

    python3 tools/r425-shadow-day/live_day.py WAR OUT_DIR [--gl swiftshader|gpu] [--day S] [--every S]

WAR: core/build/browserTest/war (./gradlew :core:browserTestWar). Writes OUT_DIR/NNN.png (both panels) and prints, per
still, the sun's centre in panel B (its disc's brightest pixels, where the whole disc shows; A's rays flood it), the sky's mean colour and
A's shade. Fails (exit 1) on a WebGL warning or page error, or when the sun steps back, stands still between two stills
while the day plays, or moves more than twice its median step at once (the shake), or shows whole in under 21 stills; or when panel A's shade, its
difference from B, changes by more than a quarter of its day's range between two stills (the flash at sunrise).
Since Simon's 11:08 (r425) the button plays 24 hours, night too, and A darkens with the light: the noon height is set
to 30, so the sun stays in the picture all day.
Proved on mutants of ShadowLab: the day's sun rounded to its sliders' steps (uneven steps); TWILIGHT 0.001 (the shade
moved 2.1 of its 6.5 in one still); and, on the 24 hours, the light switched at the horizon (bright 1 over it, 0.1
under). Headless Chrome: no window opens.
"""
import argparse
import functools
import http.server
import os
import shutil
import sys
import threading

import numpy as np
from PIL import Image
from playwright.sync_api import sync_playwright

ap = argparse.ArgumentParser()
ap.add_argument("war")
ap.add_argument("out")
ap.add_argument("--gl", default="gpu", choices=["swiftshader", "gpu"])
ap.add_argument("--day", type=float, default=40)
ap.add_argument("--every", type=float, default=0.25)
args = ap.parse_args()
os.makedirs(args.out, exist_ok=True)


class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *a):
        pass


server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(Quiet, directory=args.war))
threading.Thread(target=server.serve_forever, daemon=True).start()
url = f"http://127.0.0.1:{server.server_address[1]}/shadow-lab.html"
flags = ["--use-angle=swiftshader", "--enable-unsafe-swiftshader"] if args.gl == "swiftshader" else [
    "--use-angle=gl-egl", "--ignore-gpu-blocklist", "--enable-gpu"]
chrome = os.environ.get("CHROME") or shutil.which("google-chrome") or shutil.which("chromium")
track = []
shade = []
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=chrome, args=flags)
    page = browser.new_page(viewport={"width": 1580, "height": 800})
    errors = []
    page.on("console", lambda m: m.type in ("error", "warning") and "404" not in m.text and errors.append(m.text))
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.goto(url)
    page.wait_for_selector("#shadow-day", timeout=60000)
    page.wait_for_timeout(2000)
    print("WebGL:", page.evaluate("""() => { const g = document.createElement('canvas').getContext('webgl');
        const d = g.getExtension('WEBGL_debug_renderer_info'); return d ? g.getParameter(d.UNMASKED_RENDERER_WEBGL) : '?'; }"""))
    page.eval_on_selector("[data-key=dayLength]", "(e, v) => { e.value = v; e.dispatchEvent(new Event('input')); }", str(args.day))
    page.eval_on_selector("[data-key=noonHeight]", "e => { e.value = '30'; e.dispatchEvent(new Event('input')); }")
    page.wait_for_timeout(300)
    page.click("#shadow-day")
    box = page.eval_on_selector("canvas", "e => { const r = e.getBoundingClientRect(); return [r.x, r.y, r.width, r.height]; }")
    for i in range(int(args.day / args.every)):
        path = os.path.join(args.out, f"{i:03d}.png")
        page.screenshot(path=path, clip={"x": box[0], "y": box[1], "width": box[2], "height": box[3]})
        page.wait_for_timeout(args.every * 1000)
    browser.close()
server.shutdown()

for i in range(int(args.day / args.every)):
    both = np.asarray(Image.open(os.path.join(args.out, f"{i:03d}.png")).convert("RGB")).astype(int)
    # Panel A (shadows) the left half, B (as painted, the same sun) the right one.
    half = both.shape[1] // 2
    a, b = both[:, : half - 2], both[:, half + 2: 2 * half - 2]
    a = a[:, : b.shape[1]]
    # How much A's layers are shaded: the panels' difference over their lower three quarters, the sun in both.
    shade.append((b[b.shape[0] // 4:] - a[a.shape[0] // 4:]).mean())
    # The disc in B: A's glare (its rays, Simon at 20:26) floods it, B draws the same sun without.
    disc = (b[..., 0] > 225) & (b[..., 1] > 215) & (b[..., 2] < 225)
    ys, xs = np.nonzero(disc)
    sky = a[: a.shape[0] // 2].mean(axis=(0, 1))
    sun = (round(xs.mean(), 1), round(ys.mean(), 1)) if len(xs) > 30 else None
    track.append((sun, len(xs)))
    print(f"{i:03d}", "sun", sun, len(xs), "px", "sky", np.round(sky, 1), f"shade {shade[-1]:.2f}")
# Only the stills that show the whole disc: a half-hidden one's centre is off its disc's. And only the sun's: the day
# starts at 8 and the sun sets at 18, after 10 of its 24 hours; the full moon, up after, is a disc too.
whole = max(n for _, n in track) * 0.8
days = len(track) * 10 // 24
xs = [s[0] for s, n in track[:days] if s and n >= whole]
steps = np.diff(xs)
bad = []
if len(steps) < 20:
    # A sun the tracker cannot find proves nothing about its steps.
    bad = [f"only {len(xs)} stills show the whole disc"]
    print("sun's steps across:", bad[0])
else:
    median = np.median(steps)
    # The sun's path tilts on a tan near the view's edges: a step may grow, but not double, between two stills.
    bad = [k for k, d in enumerate(steps) if d <= 0 or d > 2 * median]
    print(f"sun's steps across: median {median:.1f} px, min {steps.min():.1f}, max {steps.max():.1f}; uneven at {bad}")
# The flash at sunrise (Simon at 17:19: "the whole thing lights up as a bugged shader when it appears"): the night's
# shade swapped for the cast shadows in one frame. Over a day it must change by under a quarter of its range a still.
jumps = np.abs(np.diff(shade))
span = max(shade) - min(shade)
flash = [k + 1 for k, d in enumerate(jumps) if d > span / 4]
print(f"shade: {min(shade):.2f} to {max(shade):.2f}, largest change between two stills {jumps.max():.2f} at "
      f"{jumps.argmax() + 1}; flash at {flash}")
print("page errors:", list(dict.fromkeys(errors))[:5] or "none")
sys.exit(1 if errors or bad or flash else 0)
