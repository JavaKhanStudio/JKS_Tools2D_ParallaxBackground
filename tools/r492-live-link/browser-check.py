#!/usr/bin/env python3
"""browser-check.py OUT: docs/live-link.md in a real Chrome, against tools/live-link-stub.py (r492).

An https page on another origin, as the GitHub Pages site is, draws a round's page through the three.js reader from the
link's URL, and redraws it on each event: a layer's numbers changed, its atlas image repainted, then a page with no
atlas yet drawn from loose images and repainted. Fails on a frame that did not move, a slider that cost an image, or an
error. Also holds down what the doc says of Chrome's Local Network Access: https without the site's permission is refused
and its EventSource stays closed; a page on the machine itself needs no permission. Stills into OUT. Headless:
Playwright drives Chrome through tools/browser.py, no window."""
import base64
import http.server
import io
import json
import mimetypes
import os
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from browser import launch  # noqa: E402
from PIL import Image, ImageChops, ImageStat  # noqa: E402
from playwright.sync_api import sync_playwright  # noqa: E402

ORIGIN = "https://plax.test"
STUB = os.path.join(ROOT, "tools", "live-link-stub.py")
failed = []


def check(ok, what):
    print(("PASS " if ok else "FAIL ") + what)
    if not ok:
        failed.append(what)


def start_stub(*args):
    proc = subprocess.Popen([sys.executable, STUB, *args, "--port", "0"], stdout=subprocess.PIPE, text=True)
    return proc, proc.stdout.readline().strip()


def write_json(path, value):
    with open(path + ".tmp", "w") as f:
        json.dump(value, f)
    os.replace(path + ".tmp", path)


def save_image(image, path):
    # A rename, as the stub asks. The name must keep its .png for Pillow.
    image.save(path + ".tmp.png")
    os.replace(path + ".tmp.png", path)


REACH = """async u => {
  const r = {};
  try { r.fetch = (await fetch(u + 'page.jplax')).status; } catch (e) { r.fetch = 'refused'; }
  r.stream = await new Promise(done => {
    const s = new EventSource(u + 'events');
    s.onmessage = () => { s.close(); done('event'); };
    s.onerror = () => { const state = s.readyState; s.close(); done('error, readyState ' + state); };
  });
  return r;
}"""


def reach(browser, origin, url):
    """What a page on `origin` gets from the link with no permission given."""
    context = browser.new_context()
    context.route("https://plax.test/**", lambda r: r.fulfill(status=200, body="<html></html>",
                                                              headers={"Content-Type": "text/html"}))
    page = context.new_page()
    page.goto(origin + "/")
    got = page.evaluate(REACH, url)
    context.close()
    return got


def local_origin():
    """The page's folder served on another port of this machine, as a game under development is."""
    class Quiet(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *args, **kw):
            super().__init__(*args, directory=os.path.join(ROOT, "tools", "r492-live-link"), **kw)

        def log_message(self, *args):
            pass
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Quiet)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server, f"http://127.0.0.1:{server.server_address[1]}"


def serve_repo(route):
    path = route.request.url[len(ORIGIN):].split("?")[0]
    file = os.path.join(ROOT, path.lstrip("/"))
    if ".." in path or not os.path.isfile(file):
        route.fulfill(status=404)
        return
    kind = mimetypes.guess_type(file)[0] or "application/octet-stream"
    with open(file, "rb") as f:
        route.fulfill(status=200, body=f.read(), headers={"Content-Type": kind})


def frame(page):
    data = page.evaluate("frame()")
    return Image.open(io.BytesIO(base64.b64decode(data.split(",")[1]))).convert("RGB")


def moved(a, b):
    return sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 3


def spread(image):
    return sum(ImageStat.Stat(image).stddev) / 3


def busy(image):
    return spread(image) > 10


def wait_loads(page, count, seconds=2.0):
    """Seconds until the page has redrawn `count` times, or None: the ticket's bar is 2 s."""
    start = time.monotonic()
    while time.monotonic() - start < seconds:
        if page.evaluate("link.loads") >= count:
            return time.monotonic() - start
        page.wait_for_timeout(20)
    return None


def main():
    out = os.path.abspath(sys.argv[1])
    os.makedirs(out, exist_ok=True)
    work = tempfile.mkdtemp(prefix="r492-")
    stubs = []
    try:
        atlas_dir = os.path.join(work, "atlas")
        os.makedirs(atlas_dir)
        samples = os.path.join(ROOT, "core", "test-data", "samples")
        with open(os.path.join(samples, "Hiver.atlas")) as f:
            atlas_text = f.read()
        shutil.copy(os.path.join(samples, "Hiver.atlas"), atlas_dir)
        image_name = next(n for n in re.findall(r"^\S+\.(?:png|jpg)$", atlas_text, re.MULTILINE))
        shutil.copy(os.path.join(samples, image_name), atlas_dir)
        page_path = os.path.join(work, "s01.jplax")
        with open(os.path.join(ROOT, "engines", "rounds", "round1", "s01.jplax")) as f:
            model = json.load(f)
        write_json(page_path, model)

        proc, url = start_stub(page_path, atlas_dir)
        stubs.append(proc)
        with sync_playwright() as p:
            browser = launch(p, os.environ.get("CHROME") or shutil.which("google-chrome"))
            got = reach(browser, ORIGIN, url)
            check(got == {"fetch": "refused", "stream": "error, readyState 2"},
                  f"https with no permission: refused, the stream closed for good: {got}")
            server, here = local_origin()
            got = reach(browser, here, url)
            server.shutdown()
            check(got == {"fetch": 200, "stream": "event"}, f"another port of this machine needs no permission: {got}")

            context = browser.new_context(viewport={"width": 960, "height": 540})
            context.route(ORIGIN + "/**", serve_repo)
            context.grant_permissions(["local-network-access"], origin=ORIGIN)
            page = context.new_page()
            noise = []
            page.on("console", lambda m: noise.append(m.text) if m.type in ("error", "warning") else None)
            page.goto(ORIGIN + "/tools/r492-live-link/check.html")
            page.wait_for_function("window.ready === true")
            check(page.evaluate("isSecureContext"), "the page is https, as GitHub Pages is")

            page.evaluate("u => connect(u)", url)
            check(wait_loads(page, 1, 15) is not None, "first event: the reader draws the page from the link's URL")
            first = frame(page)
            first.save(os.path.join(out, "1-first.png"))
            check(busy(first), f"the page is in it: spread {spread(first):.1f}")
            hello = page.evaluate("link.events[0]")
            check(hello["page"] and hello["images"] == ["Hiver.atlas", image_name], f"first event names all: {hello}")
            check(page.evaluate("link.headerRev") == str(hello["rev"]), "X-Plax-Rev can be read from another origin")

            model["pageModel"]["pageList"][-1]["decal_Y_Ratio"] += 25
            write_json(page_path, model)
            took = wait_loads(page, 2)
            check(took is not None, f"a layer's number redraws within 2 s ({took})")
            second = frame(page)
            second.save(os.path.join(out, "2-layer-moved.png"))
            event = page.evaluate("link.events.at(-1)")
            check(event["page"] and event["images"] is False, f"a slider costs no image: {event}")
            check(moved(first, second) > 1, f"the frame moved, {moved(first, second):.2f} / 255")

            image_path = os.path.join(atlas_dir, image_name)
            painted = Image.open(image_path).convert("RGBA")
            r, g, b, a = painted.split()
            save_image(Image.merge("RGBA", (b, g, r, a)), image_path)
            took = wait_loads(page, 3)
            check(took is not None, f"an image repainted redraws within 2 s ({took})")
            third = frame(page)
            third.save(os.path.join(out, "3-image-repainted.png"))
            event = page.evaluate("link.events.at(-1)")
            check(not event["page"] and event["images"] == [image_name], f"an image costs only itself: {event}")
            check(moved(second, third) > 1, f"the frame moved, {moved(second, third):.2f} / 255")
            page.evaluate("link.close()")
            check(not page.evaluate("link.errors"), f"no error: {page.evaluate('link.errors')}")

            # A page with no atlas yet: the reader has no idea, the link hands it an atlas of one page per image.
            loose_dir = os.path.join(work, "loose")
            os.makedirs(loose_dir)
            empty = os.path.join(work, "empty")
            os.makedirs(empty)
            box = (0, 0, min(painted.width, 1024), min(painted.height, 512))
            save_image(painted.crop(box), os.path.join(loose_dir, "hills.png"))
            layer = dict(model["pageModel"]["pageList"][0], regionName="hills", regionPosition=0, decal_Y_Ratio=0)
            loose = dict(model, pageModel={"atlasName": "", "outside": False, "pageList": [layer]})
            loose_path = os.path.join(work, "loose.jplax")
            write_json(loose_path, loose)
            proc, url = start_stub(loose_path, empty, "--loose", loose_dir)
            stubs.append(proc)
            page.evaluate("u => connect(u)", url)
            check(wait_loads(page, 1, 15) is not None, "a page with no atlas yet draws from loose images")
            fourth = frame(page)
            fourth.save(os.path.join(out, "4-loose.png"))
            hello = page.evaluate("link.events[0]")
            check(hello["images"] == ["live.atlas", "hills.png"], f"its first event names them: {hello['images']}")
            check(moved(third, fourth) > 1, f"it is its own frame, {moved(third, fourth):.2f} / 255")
            check(busy(fourth), f"and the image is in it, not a blank frame: spread {spread(fourth):.1f}")
            save_image(painted.crop(box).transpose(Image.Transpose.FLIP_LEFT_RIGHT), os.path.join(loose_dir, "hills.png"))
            took = wait_loads(page, 2)
            check(took is not None, f"a loose image repainted redraws within 2 s ({took})")
            fifth = frame(page)
            fifth.save(os.path.join(out, "5-loose-repainted.png"))
            check(moved(fourth, fifth) > 1, f"the frame moved, {moved(fourth, fifth):.2f} / 255")
            check(not page.evaluate("link.errors"), f"no error: {page.evaluate('link.errors')}")
            check(not noise, f"nothing on the console: {noise[:3]}")
            browser.close()
    finally:
        for proc in stubs:
            proc.kill()
            proc.wait()
            proc.stdout.close()
        shutil.rmtree(work, ignore_errors=True)
    print(f"{len(failed)} failed" if failed else "all passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
