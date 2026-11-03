#!/usr/bin/env python3
"""live-link-stub.py PAGE.jplax ATLAS_DIR [--loose DIR] [--port N]: the live link's server without the editor (r492).

Serves a page from files as docs/live-link.md says the editor serves the page it has open, so the editor's and the
readers' tickets test against one fixture. Edit a file and the stream says so: the page's .jplax is a slider moved,
an atlas, its image or a .p is an image changed, a PNG in --loose DIR is a loose image repainted. Deleting the .jplax is
the editor with no page open; writing it back opens one. Write files by rename if a reader may read in the middle.

--port 0 takes any free port. The first line on stdout is the URL readers are given. Nothing outside the stdlib."""
import argparse
import hashlib
import json
import os
import posixpath
import queue
import re
import struct
import sys
import threading
import time
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DEFAULT_PORT = 9797
GENERATED_ATLAS = "live.atlas"
PAGE = "page.jplax"
EVENT_WAIT = 0.15  # the longest an edit waits for its event, so a drag costs one every 150 ms, not one per step
POLL = 0.04
KEEP_ALIVE = 15


SAID = set()


def say(message):
    """Once per message: the files are read 25 times a second."""
    if message not in SAID:
        SAID.add(message)
        print(f"live-link-stub: {message}", file=sys.stderr)


def png_size(path):
    with open(path, "rb") as f:
        head = f.read(24)
    if len(head) < 24 or head[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"{path} is not a PNG")
    return struct.unpack(">II", head[16:24])


def page_lines(lines):
    """Which lines of a libGDX .atlas name an image: a page starts after a blank line, as engines/threejs loadAtlas reads."""
    fresh = True
    for i, line in enumerate(lines):
        if not line.strip():
            fresh = True
        elif fresh:
            yield i
            fresh = False


def atlas_pages(text):
    lines = text.splitlines()
    return [lines[i].strip() for i in page_lines(lines)]


def wanted_name(name):
    """The name a browser can ask for: docs/live-link.md "An image outside the atlas's folder". A browser resolves . and
    .. and turns a backslash into a slash before it asks, so a table holding the name as written never matches."""
    slashed = name.replace("\\", "/")
    clean = posixpath.normpath(slashed)
    if clean == ".." or clean.startswith(("../", "/")) or ":" in clean:
        return posixpath.basename(clean)
    return clean


def served_names(names):
    """Each image name as written -> the name it is served under. A name that needs no change keeps it, whatever the
    order, so only the moved one takes _2, _3, as a region name does."""
    served = {name: name for name in names if wanted_name(name) == name}
    taken = set(served.values())
    for name in names:
        if name in served:
            continue
        stem, ext = posixpath.splitext(wanted_name(name))
        candidate, n = stem + ext, 1
        while candidate in taken:
            n += 1
            candidate = f"{stem}_{n}{ext}"
        served[name] = candidate
        taken.add(candidate)
    return served


def renamed_atlas(text, served):
    """The atlas's text with its page lines under their served names, every other byte as it was."""
    lines = text.splitlines(keepends=True)
    for i in page_lines(lines):
        name = lines[i].strip()
        lines[i] = lines[i].replace(name, served[name], 1)
    return "".join(lines)


def loose_atlas_text(entries):
    """Loose images as atlas pages of one region each: docs/live-link.md "A loose image". entries: (name, path)."""
    out = []
    for name, path in entries:
        w, h = png_size(path)
        region = re.sub(r"\.png$", "", name, flags=re.IGNORECASE)
        out += ["", name, f"size: {w},{h}", "filter: Linear,Linear", "repeat: none",
                region, f"  bounds: 0,0,{w},{h}"]
    return "\n".join(out) + "\n"


def read_state(page_path, atlas_dir, loose_dir):
    """What the link serves right now: the page's bytes and every other file by name. None when no page is open."""
    try:
        with open(page_path, "rb") as f:
            page = f.read()
        model = json.loads(page)
        layers = model.setdefault("pageModel", {})
    except FileNotFoundError:
        return None
    except (OSError, ValueError, AttributeError) as e:  # a file caught mid-write: the caller keeps what it has
        raise ValueError(f"{page_path}: {e}") from e
    atlas_name = layers.get("atlasName") or ""
    atlas_path = os.path.join(atlas_dir, atlas_name) if atlas_name else ""
    text = ""
    if atlas_path and os.path.isfile(atlas_path):
        with open(atlas_path, encoding="utf-8", newline="") as f:  # every byte but a renamed page line stays
            text = f.read()
    # name -> (path on disk, or None with the bytes in `generated`)
    files, generated = {}, {}
    served = served_names(atlas_pages(text))
    for name, as_served in served.items():
        files[as_served] = os.path.join(os.path.dirname(atlas_path), name.replace("\\", "/"))
    text_served = renamed_atlas(text, served)
    for layer in layers.get("pageList") or []:
        for effect in (layer.get("particlesLibgdx"), layer.get("particlesGodot")):
            if effect and os.path.isfile(os.path.join(atlas_dir, effect)):
                files[effect] = os.path.join(atlas_dir, effect)
    loose = []
    if loose_dir and os.path.isdir(loose_dir):
        for name in sorted(os.listdir(loose_dir)):
            if not name.lower().endswith(".png"):
                continue
            if name in files:
                say(f"loose {name} has the name of a file of the atlas: left out")
                continue
            loose.append((name, os.path.join(loose_dir, name)))
            files[name] = loose[-1][1]
    if loose or not text:
        if not atlas_name:  # a page with no atlas yet: the one change the link makes to its JSON
            atlas_name = GENERATED_ATLAS
            layers["atlasName"] = atlas_name
            page = json.dumps(model).encode()
        generated[atlas_name] = (text_served.rstrip("\n") + "\n" + loose_atlas_text(loose)).lstrip("\n").encode()
    elif text_served != text:
        generated[atlas_name] = text_served.encode()
    elif atlas_name:
        files[atlas_name] = atlas_path
    stamps = {}
    for name, path in files.items():
        if os.path.isfile(path):
            s = os.stat(path)
            stamps[name] = (s.st_mtime_ns, s.st_size)
    for name, data in generated.items():
        stamps[name] = hashlib.sha1(data).hexdigest()
    return {"page": page, "stamps": stamps, "files": files, "generated": generated}


class Link:
    """The revs and the listeners. A rev starts at the clock, so a restarted server never repeats one a reader holds."""

    def __init__(self, page_path, atlas_dir, loose_dir):
        self.where = (page_path, atlas_dir, loose_dir)
        self.lock = threading.Lock()
        self.rev = self.images_rev = int(time.time() * 1000)
        self.state = None
        self.page_rev = self.rev
        self.listeners = []
        self.pending = None  # {since, page, images, opened}
        self.state = read_state(*self.where)

    def event(self, page, images):
        return {"v": 1, "rev": self.rev, "imagesRev": self.images_rev, "open": self.state is not None,
                "page": page, "images": images}

    def hello(self):
        """The first event of a connection, the same shape as the others: everything there is to read."""
        if self.state is None:
            return self.event(False, False)
        return self.event(True, self.names(self.state["stamps"]))

    @staticmethod
    def names(stamps):
        # The atlas first: it says what the images are for.
        return sorted(stamps, key=lambda n: (not n.endswith(".atlas"), n))

    def listen(self):
        q = queue.Queue()
        with self.lock:
            self.listeners.append(q)
            q.put(self.hello())
        return q

    def drop(self, q):
        with self.lock:
            self.listeners.remove(q)

    def poll(self):
        """One look at the files. Whatever moved waits at most EVENT_WAIT with what moves after it, then one event."""
        try:
            new = read_state(*self.where)
        except (ValueError, OSError) as e:  # a file caught mid-write
            say(f"{e}: keeping the last good page")
            return
        now = time.monotonic()
        with self.lock:
            old = self.state
            if old is None and new is None:
                self.flush(now)
                return
            opened = (old is None) != (new is None)
            page = bool(new and (opened or new["page"] != old["page"]))
            changed = set()
            if new:
                was = old["stamps"] if old else {}
                changed = {n for n, s in new["stamps"].items() if was.get(n) != s}
            if opened or page or changed:
                self.pending = self.pending or {"since": now, "page": False, "images": set(), "opened": False}
                self.pending["page"] |= page
                self.pending["images"] |= changed
                self.pending["opened"] |= opened
            self.state = new
            self.flush(now)

    def flush(self, now):
        if not self.pending or now - self.pending["since"] < EVENT_WAIT:
            return
        p, self.pending = self.pending, None
        self.rev = max(self.rev + 1, int(time.time() * 1000))
        if p["page"]:
            self.page_rev = self.rev
        if p["images"]:
            self.images_rev = self.rev
        images = self.names(p["images"]) if p["images"] and self.state else False
        message = self.event(bool(p["page"] and self.state), images)
        for q in self.listeners:
            q.put(message)


def handler_for(link):
    class Handler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, *args):
            pass

        def cors(self):
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Private-Network", "true")
            self.send_header("Access-Control-Expose-Headers", "X-Plax-Rev")
            self.send_header("Cache-Control", "no-store")

        def refuse(self, code):
            self.send_response(code)
            self.cors()
            self.send_header("Content-Length", "0")
            self.end_headers()

        def do_OPTIONS(self):
            self.send_response(204)
            self.cors()
            self.send_header("Access-Control-Allow-Methods", "GET, OPTIONS")
            self.send_header("Access-Control-Allow-Headers", "*")
            self.send_header("Content-Length", "0")
            self.end_headers()

        def do_GET(self):
            path = urllib.parse.urlsplit(self.path).path  # a query is ignored: a client may bust its cache with one
            if not path.startswith("/live/"):
                self.refuse(404)
            elif path == "/live/events":
                self.events()
            else:
                self.file(urllib.parse.unquote(path[len("/live/"):]))

        def file(self, name):
            with link.lock:
                state, rev = link.state, link.page_rev
            if state is None:
                self.refuse(404)
                return
            extra = {}
            if name == PAGE:
                data, kind = state["page"], "application/json"
                extra["X-Plax-Rev"] = str(rev)
            elif name in state["generated"]:
                data, kind = state["generated"][name], "text/plain; charset=utf-8"
            elif name in state["files"]:  # only what the page names: never a path on disk
                try:
                    with open(state["files"][name], "rb") as f:
                        data = f.read()
                except OSError:
                    self.refuse(404)
                    return
                kind = "image/png" if name.lower().endswith(".png") else "application/octet-stream"
            else:
                self.refuse(404)
                return
            self.send_response(200)
            self.cors()
            self.send_header("Content-Type", kind)
            self.send_header("Content-Length", str(len(data)))
            for k, v in extra.items():
                self.send_header(k, v)
            self.end_headers()
            self.wfile.write(data)

        def events(self):
            self.send_response(200)
            self.cors()
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Connection", "close")
            self.end_headers()
            self.close_connection = True
            q = link.listen()
            try:
                self.wfile.write(b"retry: 2000\n\n")
                while True:
                    try:
                        message = q.get(timeout=KEEP_ALIVE)
                        line = "data: " + json.dumps(message, separators=(",", ":")) + "\n\n"
                    except queue.Empty:
                        line = ": alive\n\n"
                    self.wfile.write(line.encode())
                    self.wfile.flush()
            except OSError:
                pass  # the client left
            finally:
                link.drop(q)

    return Handler


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("page", help="the .jplax to serve (a .plaxpj is the editor's own: export it)")
    ap.add_argument("atlas_dir", help="the folder holding the page's atlas, its images and its .p")
    ap.add_argument("--loose", help="a folder of PNGs to serve as loose images")
    ap.add_argument("--port", type=int, default=DEFAULT_PORT)
    args = ap.parse_args()
    if args.page.endswith(".plaxpj"):
        ap.error("the link serves a .jplax: a .plaxpj holds paths on one machine")
    link = Link(args.page, args.atlas_dir, args.loose)
    server = ThreadingHTTPServer(("127.0.0.1", args.port), handler_for(link))
    server.daemon_threads = True
    threading.Thread(target=server.serve_forever, daemon=True).start()
    print(f"http://127.0.0.1:{server.server_address[1]}/live/", flush=True)
    try:
        while True:
            time.sleep(POLL)
            link.poll()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
