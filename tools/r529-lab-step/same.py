"""r529: every lab's stepped still, before and after LabKit took their clip-or-live step and frame cost, pixel for pixel.

    python3 tools/r529-lab-step/same.py WAR_BEFORE WAR_AFTER OUT [CHROME]

Each lab (?clip=1) is stepped 6 s through window.<name>LabAct and the whole page shot, from each WAR: the canvas and the
readout must be byte-identical. A lab that draws differently run to run (NOISY: random snow, a CPU time in its readout)
is held to its page's text instead, its CPU times masked. Then each lab with a frame cost runs live in WAR_AFTER, and
its readout must carry it as before ("frame N ms; M draw calls", the effects lab's "frame N ms; A M draw calls, ...")
within 10 s. Exits 1 on a difference.
"""
import functools
import http.server
import os
import re
import shutil
import sys
import threading

from PIL import Image, ImageChops

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))  # tools/: lab_driver.py
from browser import launch
from lab_driver import Lab
from playwright.sync_api import sync_playwright

LABS = [
    ("fog", "fog-lab.html", "fog"), ("haze", "haze-lab.html", "haze"), ("anim", "anim-lab.html", "anim"),
    ("clouds", "clouds-lab.html", "clouds"), ("hills", "hills-lab.html", "hills"), ("pines", "pines-lab.html", "pines"),
    ("shadow", "shadow-lab.html", "shadow"), ("depth", "depth-lab.html", "depth"),
    ("effects-fog", "effects-lab.html?lab=fog", "effects"), ("effects-all", "effects-lab.html?lab=all", "effects"),
    ("effects-floor", "effects-lab.html?lab=floor", "effects"), ("effects-tower", "effects-lab.html?lab=tower", "effects"),
    ("effects-planet", "effects-lab.html?lab=planet", "effects"), ("effects-lurker", "effects-lab.html?lab=lurker", "effects"),
    ("effects-swell", "effects-lab.html?lab=swell", "effects"),
]
# Not the same twice from the same WAR (r529's control run): the haze and effects labs' snow and the depth lab's eye.
NOISY = {"haze", "depth", "effects-fog", "effects-all", "effects-planet"}
# The labs whose readout carries a frame cost, and its shape live.
COST = {
    "depth": r"frame \d+(\.\d+)? ms; \d+ draw calls",
    "effects-fog": r"frame \d+(\.\d+)? ms; A \d+ draw calls, \d+(\.\d+)? ms CPU; B \d+ draw calls",
    "effects-all": r"frame \d+(\.\d+)? ms; A \d+ draw calls, \d+(\.\d+)? ms CPU; B \d+ draw calls",
    "effects-floor": r"frame \d+(\.\d+)? ms; \d+ draw calls",
    "effects-tower": r"frame \d+(\.\d+)? ms; \d+ draw calls",
    "effects-planet": r"frame \d+(\.\d+)? ms; \d+ draw calls",
}


def serve(war):
    class Quiet(http.server.SimpleHTTPRequestHandler):
        def log_message(self, *args):
            pass

    handler = functools.partial(Quiet, directory=war)
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    return server, f"http://127.0.0.1:{server.server_address[1]}/"


def still(base, page, name, path, chrome):
    """The page's shot at path, and its text, CPU times masked."""
    sep = "&" if "?" in page else "?"
    with Lab(base + page + sep + "clip=1", chrome, name, viewport=(1580, 760)) as lab:
        lab.act(6)
        lab.shot(path)
        return re.sub(r"\d+(\.\d+)? ms CPU", "_ ms CPU", lab.page.inner_text("body"))


def live(base, page, pattern, chrome):
    """The frame cost the live lab shows, None when it shows none in 10 s."""
    with sync_playwright() as p:
        browser = launch(p, chrome)
        tab = browser.new_page(viewport={"width": 1580, "height": 760})
        tab.goto(base + page)
        found = None
        for _ in range(40):
            tab.wait_for_timeout(250)
            found = re.search(pattern, tab.inner_text("body"))
            if found:
                break
        browser.close()
        return found and found.group(0)


def main():
    before, after, out = (os.path.abspath(a) for a in sys.argv[1:4])
    chrome = sys.argv[4] if len(sys.argv) > 4 else shutil.which("google-chrome") or shutil.which("chromium")
    os.makedirs(out, exist_ok=True)
    servers = {side: serve(war) for side, war in (("before", before), ("after", after))}
    bad = []
    for key, page, name in LABS:
        paths, texts = {}, {}
        for side, (_, base) in servers.items():
            paths[side] = os.path.join(out, f"{key}-{side}.png")
            texts[side] = still(base, page, name, paths[side], chrome)
        if key in NOISY:
            same = texts["before"] == texts["after"]
            print(f"{key:<15} " + ("text identical (noisy: not by pixels)" if same else "TEXT DIFFERS"))
        else:
            a, b = (Image.open(paths[s]).convert("RGB") for s in ("before", "after"))
            box = ImageChops.difference(a, b).getbbox() if a.size == b.size else (0, 0) + a.size
            same = box is None
            print(f"{key:<15} " + ("identical" if same else f"DIFFERS in {box}"), flush=True)
        if not same:
            bad.append(key)
    base = servers["after"][1]
    for key, page, name in LABS:
        if key not in COST:
            continue
        found = live(base, page, COST[key], chrome)
        print(f"{key:<15} live: {found or 'NO COST LINE'}", flush=True)
        if not found:
            bad.append(key + " (live)")
    for server, _ in servers.values():
        server.shutdown()
    sys.exit("differs: " + ", ".join(bad) if bad else 0)


main()
