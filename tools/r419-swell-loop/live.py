"""The anim-lab as Simon opens it (r419): no ?clip=1, the lab's own clock, on a chosen GL, one still per view.

tools/anim-lab.sh's headless modes step the lab through window.animLabAct on SwiftShader; Simon's window runs it in real
time on his GPU, and saw WAVE pure white and no reflection there (r419, 15 Oct 21:38) where the clips showed both.

    python3 tools/r419-swell-loop/live.py WAR OUT_DIR [--gl swiftshader|gpu] [--wait S] [--page P --choice KEY]

--page and --choice open another lab and step through another choice's options (r434: --page
"effects-lab.html?lab=swell" --choice page).

WAR: core/build/browserTest/war (./gradlew :core:browserTestWar). Prints the WebGL renderer and every page error.
Headless Chrome: no window opens.
"""
import argparse
import functools
import http.server
import os
import shutil
import threading

from playwright.sync_api import sync_playwright

ap = argparse.ArgumentParser()
ap.add_argument("war")
ap.add_argument("out")
ap.add_argument("--gl", default="gpu", choices=["swiftshader", "gpu"])
ap.add_argument("--wait", type=float, default=4)
ap.add_argument("--page", default="anim-lab.html")
ap.add_argument("--choice", default="view")
args = ap.parse_args()
os.makedirs(args.out, exist_ok=True)

class Quiet(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *a):
        pass


handler = functools.partial(Quiet, directory=args.war)
server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
threading.Thread(target=server.serve_forever, daemon=True).start()
url = f"http://127.0.0.1:{server.server_address[1]}/{args.page}"

flags = ["--use-angle=swiftshader", "--enable-unsafe-swiftshader"] if args.gl == "swiftshader" else [
    "--use-angle=gl-egl", "--ignore-gpu-blocklist", "--enable-gpu"]
chrome = os.environ.get("CHROME") or shutil.which("google-chrome") or shutil.which("chromium")
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path=chrome, args=flags)
    page = browser.new_page(viewport={"width": 1580, "height": 800})
    errors = []
    page.on("console", lambda m: m.type in ("error", "warning") and errors.append(m.text))
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.goto(url)
    # Live, the lab sets no animLabReady (only ?clip=1 does): its controls are built after its pages load.
    page.wait_for_selector(f"[data-key={args.choice}]", timeout=60000)
    print("WebGL:", page.evaluate("""() => { const g = document.createElement('canvas').getContext('webgl');
        const d = g.getExtension('WEBGL_debug_renderer_info'); return d ? g.getParameter(d.UNMASKED_RENDERER_WEBGL) : '?'; }"""))
    views = page.eval_on_selector(f"[data-key={args.choice}]", "e => [...e.options].map(o => o.text)")
    for i, name in enumerate(views):
        page.eval_on_selector(f"[data-key={args.choice}]", "(e, v) => { e.value = v; e.dispatchEvent(new Event('input')); e.dispatchEvent(new Event('change')); }", str(i))
        page.wait_for_timeout(args.wait * 1000)
        page.screenshot(path=os.path.join(args.out, f"view{i}.png"))
        print(f"view{i}: {name}, {len(errors)} page errors so far")
    print(f"page errors (first 5 of {len(errors)}, each once):", list(dict.fromkeys(errors))[:5] or "none")
    browser.close()
server.shutdown()
