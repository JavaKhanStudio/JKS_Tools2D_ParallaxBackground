"""live_link_harness.py: what tools/r492-live-link/browser-check.py and engines/threejs/tools/live-check.py share
(r531): tools/live-link-stub.py started and stopped, a page written by rename, the repository served on an https
origin in Playwright, and how far two frames are apart. Imported, never run. LIVE_LINK_STUB names another stub to
start: a mutant, to prove a check sees it."""
import json
import mimetypes
import os
import subprocess
import sys

from PIL import ImageChops, ImageStat

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ORIGIN = "https://plax.test"
STUB = os.environ.get("LIVE_LINK_STUB") or os.path.join(ROOT, "tools", "live-link-stub.py")


class Stubs:
    """The stubs a check started, every one stopped on leaving, whatever failed."""

    def __init__(self):
        self.procs = []

    def start(self, *args, port=0):
        """A stub serving args: (its process, the URL it printed)."""
        proc = subprocess.Popen([sys.executable, STUB, *args, "--port", str(port)], stdout=subprocess.PIPE, text=True)
        self.procs.append(proc)
        return proc, proc.stdout.readline().strip()

    def stop(self, proc):
        proc.kill()
        proc.wait()
        proc.stdout.close()
        self.procs.remove(proc)

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        for proc in list(self.procs):
            self.stop(proc)


def write_json(path, value):
    """By rename, as the stub asks: it never reads a page half written."""
    with open(path + ".tmp", "w") as f:
        json.dump(value, f)
    os.replace(path + ".tmp", path)


def serve_repo(route):
    """A Playwright route answering ORIGIN's paths with the repository's files."""
    path = route.request.url[len(ORIGIN):].split("?")[0]
    file = os.path.join(ROOT, path.lstrip("/"))
    if ".." in path or not os.path.isfile(file):
        route.fulfill(status=404)
        return
    kind = mimetypes.guess_type(file)[0] or "application/octet-stream"
    with open(file, "rb") as f:
        route.fulfill(status=200, body=f.read(), headers={"Content-Type": kind})


def moved(a, b):
    """Mean difference of two frames, out of 255."""
    return sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 3
