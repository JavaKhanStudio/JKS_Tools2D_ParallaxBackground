#!/usr/bin/env python3
"""live-check.py OUT: connectLive against tools/live-link-stub.py, in a real Chrome on an https origin (r57).

A layer's speed and decal and an image change in the stub's files: the drawn frame must follow within 2 s, with no reload,
no jump, no file read that the event did not name, and what the page does not hold left as the game set it. Then what
docs/live-link.md's "What a reader does" asks: a repaint arriving during a read, a slider's step on a FLOOR, a LURKER and snow, a refused page, a failed read, no page
open, the stream dropped, an unknown version, close(), and Chrome's refusal said on the console. Stills into OUT. Needs
Chrome, playwright and Pillow; not in CI, as tools/r492-live-link/browser-check.py."""
import base64
import io
import json
import mimetypes
import os
import shutil
import subprocess
import sys
import tempfile
import time

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from browser import launch  # noqa: E402
from PIL import Image, ImageChops, ImageStat  # noqa: E402
from playwright.sync_api import sync_playwright  # noqa: E402

ORIGIN = "https://plax.test"
STUB = os.path.join(ROOT, "tools", "live-link-stub.py")
PAGE_PATH = "/engines/threejs/tests/live_link.html"
SAMPLES = os.path.join(ROOT, "core", "test-data", "samples")
failed = []


def check(ok, what):
    print(("PASS " if ok else "FAIL ") + what)
    if not ok:
        failed.append(what)


def start_stub(*args, port=0):
    proc = subprocess.Popen([sys.executable, STUB, *args, "--port", str(port)], stdout=subprocess.PIPE, text=True)
    return proc, proc.stdout.readline().strip()


def write_json(path, value):
    with open(path + ".tmp", "w") as f:
        json.dump(value, f)
    os.replace(path + ".tmp", path)


def save_image(image, path):
    # A rename, from outside the folder: the stub would take a .png in --loose for a loose image.
    temp = os.path.join(os.path.dirname(os.path.dirname(path)), "next.png")
    image.save(temp)
    os.replace(temp, path)


def swapped(path):
    """The image at `path` with red and blue swapped, written by rename."""
    r, g, b, a = Image.open(path).convert("RGBA").split()
    save_image(Image.merge("RGBA", (b, g, r, a)), path)


def serve_repo(route):
    path = route.request.url[len(ORIGIN):].split("?")[0]
    file = os.path.join(ROOT, path.lstrip("/"))
    if ".." in path or not os.path.isfile(file):
        route.fulfill(status=404)
        return
    with open(file, "rb") as f:
        route.fulfill(status=200, body=f.read(),
                      headers={"Content-Type": mimetypes.guess_type(file)[0] or "application/octet-stream"})


def shot(page, what="frame()"):
    data = page.evaluate(what)
    return Image.open(io.BytesIO(base64.b64decode(data.split(",")[1]))).convert("RGB")


def moved(a, b):
    return sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 3


def wait_pages(page, count, seconds=2.0):
    """Seconds until onPage has run `count` times, or None."""
    start = time.monotonic()
    while time.monotonic() - start < seconds:
        if page.evaluate("live.pages") >= count:
            return round(time.monotonic() - start, 2)
        page.wait_for_timeout(20)
    return None


def new_page(browser, grant=True):
    context = browser.new_context(viewport={"width": 960, "height": 540})
    context.route(ORIGIN + "/**", serve_repo)
    if grant:
        context.grant_permissions(["local-network-access"], origin=ORIGIN)
    page = context.new_page()
    page.goto(ORIGIN + PAGE_PATH)
    page.wait_for_function("window.ready === true")
    return context, page


def main_scenario(browser, work, out, stubs):
    atlas_dir = os.path.join(work, "atlas")
    os.makedirs(atlas_dir)
    with open(os.path.join(SAMPLES, "Hiver.atlas")) as f:
        image_name = next(line.strip() for line in f if line.strip().endswith((".png", ".jpg")))
    shutil.copy(os.path.join(SAMPLES, "Hiver.atlas"), atlas_dir)
    shutil.copy(os.path.join(SAMPLES, image_name), atlas_dir)
    page_path = os.path.join(work, "s01.jplax")
    with open(os.path.join(ROOT, "engines", "rounds", "round1", "s01.jplax")) as f:
        model = json.load(f)
    write_json(page_path, model)
    proc, url = start_stub(page_path, atlas_dir)
    stubs.append(proc)
    port = url.split(":")[2].split("/")[0]

    context, page = new_page(browser)
    asked, said = [], []
    page.on("request", lambda r: asked.append(r.url[len(url):]) if r.url.startswith(url) and not r.url.endswith("events") else None)
    page.on("console", lambda m: said.append(m.text) if m.type in ("error", "warning") else None)

    page.evaluate("u => connect(u)", url)
    check(wait_pages(page, 1, 15) is not None, "the first event draws the page")
    check(sorted(asked) == sorted(["page.jplax", "Hiver.atlas", image_name]), f"it read the page and its files once: {asked}")
    page.evaluate("setGame()")
    page.evaluate("step(2)")
    kept = page.evaluate("gameState()")
    before = shot(page)
    before.save(os.path.join(out, "1-first.png"))
    ring_before = shot(page, "ringFrame()")
    ring_before.save(os.path.join(out, "1-rings.png"))
    # The sky is one colour along a row: a band is what varies along one.
    across = max(ImageStat.Stat(ring_before.crop((0, y, 960, y + 1))).stddev[0] for y in range(0, 540, 10))
    check(across > 5, f"the rings draw the page, not only the sky: {across:.1f} along a row")
    scene_size = page.evaluate("scenes()")

    # What the front layer would do at its old speed, to tell the new speed from it.
    page.evaluate("step(1)")
    old_speed = shot(page)
    page.evaluate("step(-1)")
    distances = page.evaluate("distances()")

    asked.clear()
    model["pageModel"]["pageList"][-1]["parallaxScalingSpeedX"] *= 4
    write_json(page_path, model)
    took = wait_pages(page, 2)
    check(took is not None, f"a layer's speed: redrawn within 2 s ({took})")
    check(asked == ["page.jplax"], f"a slider costs the page, no image: {asked}")
    same = shot(page)
    check(moved(before, same) < 0.2, f"nothing jumps on a slider's step: {moved(before, same):.3f} / 255")
    # Within rounding: a new layer's distance is decal + scrolled - decal.
    off = max(abs(a - b) for was, now in zip(distances, page.evaluate("distances()")) for a, b in zip(was, now))
    check(off < 1e-5, f"each layer keeps what it has scrolled: {off:.1e} off")
    check(page.evaluate("gameState()") == kept, f"what the page does not hold stays: {page.evaluate('gameState()')}")
    check(page.evaluate("scenes()") == scene_size, "the old layers are gone from the scene")
    page.evaluate("step(1)")
    new_speed = shot(page)
    new_speed.save(os.path.join(out, "2-speed.png"))
    check(moved(old_speed, new_speed) > 0.5, f"the new speed is what moves it: {moved(old_speed, new_speed):.2f} / 255")

    settled = new_speed
    model["pageModel"]["pageList"][-1]["decal_Y_Ratio"] += 25
    write_json(page_path, model)
    took = wait_pages(page, 3)
    check(took is not None, f"a layer's decal: redrawn within 2 s ({took})")
    decal = shot(page)
    decal.save(os.path.join(out, "3-decal.png"))
    check(moved(settled, decal) > 0.5, f"an edited decal still moves its layer: {moved(settled, decal):.2f} / 255")

    asked.clear()
    ring_old = shot(page, "ringFrame()")
    swapped(os.path.join(atlas_dir, image_name))
    took = wait_pages(page, 4)
    check(took is not None, f"an image repainted: redrawn within 2 s ({took})")
    check(asked == [image_name], f"an image costs only itself: {asked}")
    repainted = shot(page)
    repainted.save(os.path.join(out, "4-image.png"))
    check(moved(decal, repainted) > 1, f"the frame moved, {moved(decal, repainted):.2f} / 255")
    ring_new = shot(page, "ringFrame()")
    ring_new.save(os.path.join(out, "4-rings.png"))
    check(moved(ring_old, ring_new) > 1, f"the rings on it follow, {moved(ring_old, ring_new):.2f} / 255")
    check(page.evaluate("gameState()")["rings"] == kept["rings"], "a PlaxRings keeps its own options")

    # A cross-fade under way ends on its page, the edit on top of it.
    page.evaluate("fade()")
    model["pageModel"]["pageList"][-1]["decal_Y_Ratio"] -= 5
    write_json(page_path, model)
    check(wait_pages(page, 5) is not None and not page.evaluate("fading()"), "an edit during a game's cross-fade ends it")

    # A page this reader refuses keeps the last one, and the next edit may fix it.
    last = shot(page)
    said.clear()
    bad = json.loads(json.dumps(model))
    bad["pageModel"]["pageList"][-1]["kind"] = "NOT_A_KIND"
    write_json(page_path, bad)
    page.wait_for_timeout(700)
    check(any("refused" in s for s in said), f"a refused page says why: {said[:1]}")
    check(moved(last, shot(page)) < 0.01, "and keeps the last page")
    model["pageModel"]["pageList"][-1]["decal_Y_Ratio"] += 5
    write_json(page_path, model)
    check(wait_pages(page, 6) is not None, "the next edit fixes it")
    check(not page.evaluate("fading()"), "with no fade left over")

    # A failed read holds nothing new and reads all again on a new stream.
    said.clear()
    context.route(url + "page.jplax", lambda r: r.abort(), times=1)
    settled = shot(page)
    model["pageModel"]["pageList"][-1]["decal_Y_Ratio"] += 5
    write_json(page_path, model)
    page.wait_for_timeout(300)
    check(any("a read on the live link failed" in s for s in said), f"a read that failed says so: {said[:2]}")
    check(moved(settled, shot(page)) < 0.01, "and the last page stays")
    check(wait_pages(page, 7, 4) is not None, "a new stream after 2 s reads it")
    check(moved(settled, shot(page)) > 0.1, "and draws it")

    # No page open: keep drawing. Opening one reads it.
    settled = shot(page)
    os.remove(page_path)
    page.wait_for_timeout(500)
    check(moved(settled, shot(page)) < 0.01, "no page open: what it has stays drawn")
    model["pageModel"]["pageList"][-1]["decal_Y_Ratio"] += 5
    write_json(page_path, model)
    check(wait_pages(page, 8) is not None, "a page opened draws within 2 s")

    # The stream dropped: the editor gone, and back on its port.
    settled = shot(page)
    proc.kill()
    proc.wait()
    proc.stdout.close()
    stubs.remove(proc)
    page.wait_for_timeout(500)
    check(moved(settled, shot(page)) < 0.01, "the editor gone: what it has stays drawn")
    model["pageModel"]["pageList"][-1]["decal_Y_Ratio"] += 5
    write_json(page_path, model)
    proc, _ = start_stub(page_path, atlas_dir, port=port)
    stubs.append(proc)
    check(wait_pages(page, 9, 8) is not None, "the editor back: it reconnects and draws")
    check(moved(settled, shot(page)) > 0.1, "the edit made meanwhile")

    # close() stops all of it.
    page.evaluate("close_()")
    settled = shot(page)
    count = page.evaluate("live.pages")
    model["pageModel"]["pageList"][-1]["decal_Y_Ratio"] += 5
    write_json(page_path, model)
    page.wait_for_timeout(600)
    check(page.evaluate("live.pages") == count and moved(settled, shot(page)) < 0.01, "close(): no more redraws, the page stays")

    # A version this reader does not know: stop, and say so.
    said.clear()
    page.evaluate("""() => {
      const real = window.EventSource;
      window.EventSource = class extends EventTarget {
        static CLOSED = 2;
        constructor() { super(); this.readyState = 1; setTimeout(() => this.onmessage?.({ data: '{"v":2,"open":true,"page":true,"images":false,"rev":1,"imagesRev":1}' }), 0); }
        close() { window.closedIt = true; }
      };
      connect('http://127.0.0.1:1/live/');
      setTimeout(() => { window.EventSource = real; }, 100);
    }""")
    page.wait_for_timeout(200)
    check(page.evaluate("window.closedIt === true") and any("version 2" in s for s in said), f"an unknown version: stopped and said: {said[:1]}")
    context.close()


def merged_scenario(browser, work, stubs):
    """A repaint named by an event that came during a read must be read, though a later slider's event names no image."""
    loose = os.path.join(work, "loose")
    empty = os.path.join(work, "empty")
    os.makedirs(loose)
    os.makedirs(empty)
    with open(os.path.join(SAMPLES, "Hiver.atlas")) as f:
        image_name = next(line.strip() for line in f if line.strip().endswith((".png", ".jpg")))
    image = Image.open(os.path.join(SAMPLES, image_name)).convert("RGBA")
    box = (0, 0, min(image.width, 1024), min(image.height, 512))
    save_image(image.crop(box), os.path.join(loose, "a.png"))
    save_image(image.crop(box).transpose(Image.Transpose.FLIP_LEFT_RIGHT), os.path.join(loose, "b.png"))
    with open(os.path.join(ROOT, "engines", "rounds", "round1", "s01.jplax")) as f:
        base = json.load(f)["pageModel"]["pageList"][0]
    layer_a = dict(base, regionName="a", regionPosition=0, decal_Y_Ratio=0)
    layer_b = dict(base, regionName="b", regionPosition=0, decal_Y_Ratio=50, sizeRatio=1)
    model = {"pageModel": {"atlasName": "", "outside": False, "pageList": [layer_a, layer_b]}}
    page_path = os.path.join(work, "loose.jplax")
    write_json(page_path, model)
    proc, url = start_stub(page_path, empty, "--loose", loose)
    stubs.append(proc)
    context, page = new_page(browser)
    page.evaluate("u => connect(u)", url)
    check(wait_pages(page, 1, 15) is not None, "a page with no atlas yet draws from loose images")
    first = shot(page)

    held = []
    context.route(url + "a.png", lambda r: held.append(r))
    swapped(os.path.join(loose, "a.png"))
    page.wait_for_timeout(400)
    check(len(held) == 1, f"a read is under way, held: {len(held)}")
    swapped(os.path.join(loose, "b.png"))
    page.wait_for_timeout(300)
    model["pageModel"]["pageList"][0]["decal_X_Ratio"] = 0.0
    model["pageModel"]["pageList"][1]["sizeRatio"] = 0.98
    write_json(page_path, model)
    page.wait_for_timeout(300)
    context.unroute(url + "a.png")
    held[0].continue_()
    check(wait_pages(page, 3, 2) is not None, f"the read, then what came during it: {page.evaluate('live.pages')} pages")
    final = shot(page)
    # The whole truth: a fresh connection draws what is on the link now.
    page.evaluate("u => connect(u)", url)
    check(wait_pages(page, 1, 15) is not None, "a fresh connection draws it too")
    fresh = shot(page)
    check(moved(final, first) > 1, f"the page moved, {moved(final, first):.2f} / 255")
    check(moved(final, fresh) < 0.05, f"a repaint during a read is not lost: {moved(final, fresh):.3f} / 255 from a fresh read")
    context.close()


def pixels_off(a, b):
    """Pixels apart by over 32 on a channel: a few flakes elsewhere hardly move a mean."""
    r, g, b = ImageChops.difference(a, b).split()
    far = ImageChops.lighter(ImageChops.lighter(r, g), b).point(lambda v: 255 if v > 32 else 0)
    return far.histogram()[255]


def carried_scenario(browser, work, out, stubs):
    """A slider's step leaves what a kind runs on its own: a FLOOR's rows, a LURKER's sightings, a PARTICLES layer's snow.
    On their own folders, as they are: each atlas names its image in ../ (r74)."""
    for name, page_file, atlas_dir in (("floor", "engines/rounds/floor/g01.jplax", "core/test-data/floor"),
                                       ("lurker", "engines/rounds/lurker/l01.jplax", "core/test-data/lurker"),
                                       ("snow", "core/test-data/particles/p01.jplax", "core/test-data/particles")):
        with open(os.path.join(ROOT, page_file)) as f:
            model = json.load(f)
        page_path = os.path.join(work, name + ".jplax")
        write_json(page_path, model)
        proc, url = start_stub(page_path, os.path.join(ROOT, atlas_dir))
        stubs.append(proc)
        context, page = new_page(browser)
        page.evaluate("u => connect(u)", url)
        check(wait_pages(page, 1, 15) is not None, f"{name}: drawn")
        page.evaluate("setGame()")
        # 14 s in small steps: past the lurker round's "gone at the next gap", its draws moved on.
        page.evaluate("for (let i = 0; i < 140; i++) { step(0.1); frame(); }")
        before = shot(page)
        before.save(os.path.join(out, f"5-{name}.png"))
        # A failed image draws the gradient alone, one colour along a row: its layers are what varies along one.
        across = max(ImageStat.Stat(before.crop((0, y, 960, y + 1))).stddev[0] for y in range(0, 540, 10))
        check(across > 5, f"{name}: its image is drawn: {across:.1f} along a row")
        state = page.evaluate("carried()")
        # A number that changes nothing drawn while the page does not scroll up or down.
        model["pageModel"]["pageList"][0]["parallaxScalingSpeedY"] = 0.123
        write_json(page_path, model)
        check(wait_pages(page, 2) is not None, f"{name}: a slider's step redraws")
        after = shot(page)
        check(pixels_off(before, after) == 0, f"{name}: nothing jumps: {pixels_off(before, after)} pixels off")
        check(name == "snow" or (state and page.evaluate("carried()") == state), f"{name}: its own state runs on: {state}")
        context.close()


def refused_scenario(browser, work, stubs):
    """Chrome 156 with no permission: the stream ends CLOSED, which reads like an editor not running. Say so."""
    empty = os.path.join(work, "empty2")
    os.makedirs(empty)
    page_path = os.path.join(work, "none.jplax")
    with open(os.path.join(ROOT, "engines", "rounds", "round1", "s01.jplax")) as f:
        write_json(page_path, json.load(f))
    proc, url = start_stub(page_path, empty)
    stubs.append(proc)
    context, page = new_page(browser, grant=False)
    said = []
    page.on("console", lambda m: said.append(m.text) if m.type == "warning" else None)
    page.evaluate("u => connect(u)", url)
    page.wait_for_timeout(2600)
    named = [s for s in said if "site settings" in s]
    check(len(named) == 1, f"a refused permission is said once, naming the site settings: {len(named)}: {said[:2]}")
    context.close()


def main():
    out = os.path.abspath(sys.argv[1])
    os.makedirs(out, exist_ok=True)
    work = tempfile.mkdtemp(prefix="r57-")
    stubs = []
    try:
        with sync_playwright() as p:
            browser = launch(p, os.environ.get("CHROME") or shutil.which("google-chrome"))
            main_scenario(browser, work, out, stubs)
            merged_scenario(browser, work, stubs)
            carried_scenario(browser, work, out, stubs)
            refused_scenario(browser, work, stubs)
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
