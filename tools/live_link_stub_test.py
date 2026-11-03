"""The live link's stub holds to docs/live-link.md (r492), over real HTTP against the real script. Stdlib only."""
import http.client
import json
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import threading
import time
import unittest
import zlib

STUB = os.path.join(os.path.dirname(os.path.abspath(__file__)), "live-link-stub.py")


def png(w=2, h=2, shade=0):
    def chunk(kind, data):
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))
    raw = b"".join(b"\0" + bytes([shade, shade, shade]) * w for _ in range(h))
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


def page(atlas="a.atlas", speed=1.0, regions=("sky",)):
    return {"pageModel": {"atlasName": atlas, "outside": False,
                          "pageList": [{"regionName": r, "regionPosition": 0, "speed": speed} for r in regions]},
            "repeatOnX": True}


ATLAS = "\na.png\nsize: 2,2\nfilter: Linear,Linear\nrepeat: none\nsky\n  rotate: false\n  xy: 0, 0\n  size: 2, 2\n"


class Stream:
    """A client of /live/events: `next` is the next event, or fails after `wait` seconds."""

    def __init__(self, port):
        self.conn = http.client.HTTPConnection("127.0.0.1", port, timeout=30)
        self.conn.request("GET", "/live/events")
        self.response = self.conn.getresponse()
        self.events, self.ready = [], threading.Condition()
        threading.Thread(target=self.read, daemon=True).start()

    def read(self):
        try:
            for line in self.response:
                if line.startswith(b"data: "):
                    with self.ready:
                        self.events.append(json.loads(line[6:]))
                        self.ready.notify_all()
        except (OSError, ValueError, AttributeError):
            pass

    def next(self, wait=5):
        with self.ready:
            if not self.ready.wait_for(lambda: self.events, wait):
                raise AssertionError(f"no event in {wait} s")
            return self.events.pop(0)

    def quiet(self, wait):
        """Fails if an event comes within `wait` seconds."""
        with self.ready:
            if self.ready.wait_for(lambda: self.events, wait):
                raise AssertionError(f"unexpected event {self.events[0]}")

    def close(self):
        self.conn.close()


class StubTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.mkdtemp(prefix="live-link-")
        self.addCleanup(shutil.rmtree, self.dir)
        self.atlas_dir = os.path.join(self.dir, "atlas")
        self.loose_dir = os.path.join(self.dir, "loose")
        os.makedirs(self.atlas_dir)
        os.makedirs(self.loose_dir)
        self.page = os.path.join(self.dir, "p.jplax")
        self.write(self.page, json.dumps(page()))
        self.write(os.path.join(self.atlas_dir, "a.atlas"), ATLAS)
        self.write(os.path.join(self.atlas_dir, "a.png"), png())
        self.write(os.path.join(self.atlas_dir, "secret.png"), png(shade=9))

    def write(self, path, content):
        tmp = path + ".tmp"
        with open(tmp, "wb") as f:
            f.write(content.encode() if isinstance(content, str) else content)
        os.replace(tmp, path)

    def start(self, *args):
        proc = subprocess.Popen([sys.executable, "-I", STUB, self.page, self.atlas_dir, "--port", "0", *args],
                                stdout=subprocess.PIPE, text=True)
        self.addCleanup(proc.wait)
        self.addCleanup(proc.kill)
        self.addCleanup(proc.stdout.close)
        url = proc.stdout.readline().strip()
        self.assertRegex(url, r"^http://127\.0\.0\.1:\d+/live/$")
        self.port = int(url.split(":")[2].split("/")[0])
        return proc

    def get(self, path, method="GET", headers=None):
        conn = http.client.HTTPConnection("127.0.0.1", self.port, timeout=5)
        conn.request(method, path, headers=headers or {})
        r = conn.getresponse()
        body = r.read()
        conn.close()
        return r, body

    def stream(self):
        s = Stream(self.port)
        self.addCleanup(s.close)
        return s

    def test_page_is_the_file_byte_for_byte_with_its_rev_beside_it(self):
        self.start()
        r, body = self.get("/live/page.jplax")
        self.assertEqual(r.status, 200)
        with open(self.page, "rb") as f:
            self.assertEqual(body, f.read())
        self.assertNotIn(b'"rev"', body)
        hello = self.stream().next()
        self.assertEqual(int(r.headers["X-Plax-Rev"]), hello["rev"])

    def test_every_answer_can_be_read_from_another_origin_and_is_never_cached(self):
        self.start()
        for path in ("/live/page.jplax", "/live/a.atlas", "/live/a.png", "/live/nothing"):
            r, _ = self.get(path)
            self.assertEqual(r.headers["Access-Control-Allow-Origin"], "*", path)
            self.assertEqual(r.headers["Access-Control-Allow-Private-Network"], "true", path)
            self.assertEqual(r.headers["Cache-Control"], "no-store", path)
        self.assertIn("X-Plax-Rev", self.get("/live/page.jplax")[0].headers["Access-Control-Expose-Headers"])

    def test_a_preflight_is_answered(self):
        self.start()
        r, _ = self.get("/live/page.jplax", "OPTIONS", {"Origin": "https://a.example",
                                                        "Access-Control-Request-Method": "GET",
                                                        "Access-Control-Request-Private-Network": "true"})
        self.assertEqual(r.status, 204)
        self.assertEqual(r.headers["Access-Control-Allow-Private-Network"], "true")
        self.assertIn("GET", r.headers["Access-Control-Allow-Methods"])

    def test_only_what_the_page_names_is_served(self):
        self.start()
        self.assertEqual(self.get("/live/a.png")[0].status, 200)
        for path in ("/live/secret.png", "/live/../p.jplax", "/live/..%2Fp.jplax", "/live/%2Fetc%2Fpasswd", "/page.jplax"):
            self.assertEqual(self.get(path)[0].status, 404, path)
        self.assertEqual(self.get("/live/a.png?v=12")[0].status, 200, "a query is ignored")
        self.assertEqual(self.get("/live/page.jplax", "POST")[0].status, 501)

    def test_first_event_names_everything_and_a_slider_costs_only_the_page(self):
        self.start()
        s = self.stream()
        self.assertEqual(s.response.getheader("Content-Type"), "text/event-stream")
        hello = s.next()
        self.assertEqual((hello["v"], hello["open"], hello["page"]), (1, True, True))
        self.assertEqual(hello["images"], ["a.atlas", "a.png"], "the atlas first")
        self.write(self.page, json.dumps(page(speed=2.0)))
        edit = s.next()
        self.assertEqual((edit["page"], edit["images"]), (True, False))
        self.assertGreater(edit["rev"], hello["rev"])
        self.assertEqual(edit["imagesRev"], hello["imagesRev"], "a page edit moves no image")
        self.assertEqual(json.loads(self.get("/live/page.jplax")[1])["pageModel"]["pageList"][0]["speed"], 2.0)

    def test_an_image_edit_names_only_that_image(self):
        self.start()
        s = self.stream()
        hello = s.next()
        self.write(os.path.join(self.atlas_dir, "a.png"), png(shade=200))
        edit = s.next()
        self.assertEqual((edit["page"], edit["images"]), (False, ["a.png"]))
        self.assertGreater(edit["imagesRev"], hello["imagesRev"])
        self.assertEqual(edit["imagesRev"], edit["rev"])
        self.assertEqual(self.get("/live/a.png")[1], png(shade=200))

    def test_a_drag_is_merged_and_its_last_step_arrives(self):
        self.start()
        s = self.stream()
        s.next()
        for i in range(60):
            self.write(self.page, json.dumps(page(speed=float(i))))
            time.sleep(0.01)
        time.sleep(0.5)
        got = []
        with s.ready:
            got, s.events = s.events, []
        self.assertLess(len(got), 15, f"{len(got)} events for 60 steps")
        self.assertGreater(len(got), 0)
        self.assertEqual(json.loads(self.get("/live/page.jplax")[1])["pageModel"]["pageList"][0]["speed"], 59.0)

    def test_a_reconnect_gets_the_current_rev_and_nothing_old(self):
        self.start()
        s = self.stream()
        s.next()
        self.write(self.page, json.dumps(page(speed=2.0)))
        last = s.next()
        s.close()
        again = self.stream()
        hello = again.next()
        self.assertEqual((hello["rev"], hello["imagesRev"]), (last["rev"], last["imagesRev"]))
        again.quiet(0.4)

    def test_a_restarted_server_never_repeats_a_rev(self):
        proc = self.start()
        s = self.stream()
        first = s.next()
        s.close()
        proc.kill()
        proc.wait()
        time.sleep(0.01)
        self.start()
        self.assertGreater(self.stream().next()["rev"], first["rev"])

    def test_no_page_open_keeps_the_stream_and_refuses_the_files(self):
        self.start()
        s = self.stream()
        s.next()
        kept = self.page + ".kept"
        os.rename(self.page, kept)
        closed = s.next()
        self.assertEqual((closed["open"], closed["page"], closed["images"]), (False, False, False))
        self.assertEqual(self.get("/live/page.jplax")[0].status, 404)
        late = self.stream()
        self.assertEqual(late.next()["open"], False)
        os.rename(kept, self.page)
        opened = s.next()
        self.assertEqual((opened["open"], opened["page"]), (True, True))
        self.assertEqual(self.get("/live/page.jplax")[0].status, 200)

    def test_a_page_with_no_atlas_yet_is_served_as_loose_images(self):
        self.write(self.page, json.dumps(page(atlas="", regions=("hills",))))
        os.remove(os.path.join(self.atlas_dir, "a.atlas"))
        self.write(os.path.join(self.loose_dir, "hills.png"), png(4, 3))
        self.start("--loose", self.loose_dir)
        s = self.stream()
        hello = s.next()
        self.assertEqual(hello["images"], ["live.atlas", "hills.png"])
        self.assertEqual(json.loads(self.get("/live/page.jplax")[1])["pageModel"]["atlasName"], "live.atlas")
        text = self.get("/live/live.atlas")[1].decode()
        self.assertEqual(text.splitlines(), ["hills.png", "size: 4,3", "filter: Linear,Linear", "repeat: none", "hills",
                                             "  bounds: 0,0,4,3"], "indented: unindented is a region to the readers")
        self.write(os.path.join(self.loose_dir, "hills.png"), png(4, 3, shade=99))
        repaint = s.next()
        self.assertEqual((repaint["page"], repaint["images"]), (False, ["hills.png"]), "same size: same atlas text")
        self.write(os.path.join(self.loose_dir, "hills.png"), png(8, 3))
        resized = s.next()
        self.assertEqual(resized["images"], ["live.atlas", "hills.png"], "new size: the atlas text moved")

    def test_loose_images_follow_a_real_atlas_whose_regions_keep_their_place(self):
        self.write(os.path.join(self.loose_dir, "hills.png"), png(4, 3))
        self.write(os.path.join(self.loose_dir, "a.png"), png(9, 9))
        self.start("--loose", self.loose_dir)
        text = self.get("/live/a.atlas")[1].decode()
        self.assertLess(text.index("sky"), text.index("hills.png"))
        self.assertTrue(text.startswith("a.png\n"))
        self.assertEqual(text.count("a.png"), 1, "a loose file named as an atlas page is left out")
        self.assertEqual(self.get("/live/a.png")[1], png(), "the atlas page is served, not the loose file")
        self.assertEqual(self.get("/live/hills.png")[1], png(4, 3))

    def outside(self, *pages):
        """An atlas in the atlas folder naming `pages`, the first holding the page's region."""
        text = "".join(f"\n{name}\nsize: 2,2\nfilter: Linear,Linear\nrepeat: none\nr{i}\n  bounds: 0,0,2,2\n"
                       for i, name in enumerate(pages))
        self.write(os.path.join(self.atlas_dir, "a.atlas"), text)
        return text

    def test_an_image_outside_the_atlas_folder_is_served_under_its_base_name(self):
        samples = os.path.join(self.dir, "samples")
        os.makedirs(samples)
        self.write(os.path.join(samples, "Hiver.png"), png(shade=50))
        text = self.outside("../samples/Hiver.png")
        self.start()
        s = self.stream()
        self.assertEqual(s.next()["images"], ["a.atlas", "Hiver.png"])
        self.assertEqual(self.get("/live/a.atlas")[1].decode(), text.replace("../samples/Hiver.png", "Hiver.png"),
                         "the page line alone changes")
        self.assertEqual(self.get("/live/Hiver.png")[1], png(shade=50))
        self.assertEqual(self.get("/live/../samples/Hiver.png")[0].status, 404, "the name as written is not in the table")
        self.assertEqual(self.get("/samples/Hiver.png")[0].status, 404, "what a browser makes of it")
        self.write(os.path.join(samples, "Hiver.png"), png(shade=60))
        edit = s.next()
        self.assertEqual((edit["page"], edit["images"]), (False, ["Hiver.png"]), "a repaint goes by the served name")
        self.assertEqual(self.get("/live/Hiver.png")[1], png(shade=60))
        self.outside("../samples/Hiver.png", "a.png")
        self.assertEqual(s.next()["images"], ["a.atlas", "a.png"], "an edit of the atlas names the atlas")

    def test_a_base_name_taken_goes_to_the_moved_image_whatever_the_order(self):
        for folder, shade in (("one", 10), ("two", 20)):
            os.makedirs(os.path.join(self.dir, folder))
            self.write(os.path.join(self.dir, folder, "a.png"), png(shade=shade))
        self.outside("../one/a.png", "a.png", "../two/a.png")
        self.start()
        self.assertEqual(self.get("/live/a.png")[1], png(), "the image already in the folder keeps its name")
        self.assertEqual(self.get("/live/a_2.png")[1], png(shade=10))
        self.assertEqual(self.get("/live/a_3.png")[1], png(shade=20))
        text = self.get("/live/a.atlas")[1].decode()
        self.assertEqual([line for line in text.splitlines() if line.endswith(".png")], ["a_2.png", "a.png", "a_3.png"])

    def test_a_name_a_browser_would_rewrite_inside_the_folder_loses_only_that(self):
        os.makedirs(os.path.join(self.atlas_dir, "sub"))
        self.write(os.path.join(self.atlas_dir, "sub", "b.png"), png(shade=30))
        self.write(os.path.join(self.atlas_dir, "sub", "c.png"), png(shade=40))
        self.write(os.path.join(self.atlas_dir, "d.png"), png(shade=70))
        self.outside("./sub/b.png", "sub\\c.png", "sub/../d.png")
        self.start()
        self.assertEqual(self.get("/live/sub/b.png")[1], png(shade=30))
        self.assertEqual(self.get("/live/sub/c.png")[1], png(shade=40))
        self.assertEqual(self.get("/live/d.png")[1], png(shade=70))

    def test_an_atlas_with_nothing_to_rename_is_the_file_and_a_region_named_alike_stays(self):
        self.start()
        with open(os.path.join(self.atlas_dir, "a.atlas"), "rb") as f:
            self.assertEqual(self.get("/live/a.atlas")[1], f.read())
        os.makedirs(os.path.join(self.dir, "up"))
        self.write(os.path.join(self.dir, "up", "e.png"), png())
        self.write(os.path.join(self.atlas_dir, "a.atlas"),
                   "\n../up/e.png\r\nsize: 2,2\r\n../up/e.png\r\n  bounds: 0,0,2,2\r\n")
        time.sleep(0.4)
        self.assertEqual(self.get("/live/a.atlas")[1], b"\ne.png\r\nsize: 2,2\r\n../up/e.png\r\n  bounds: 0,0,2,2\r\n",
                         "only the page's line, line ends kept")

    def test_a_loose_image_follows_a_renamed_atlas_and_cannot_take_its_name(self):
        os.makedirs(os.path.join(self.dir, "up"))
        self.write(os.path.join(self.dir, "up", "e.png"), png(shade=5))
        self.outside("../up/e.png")
        self.write(os.path.join(self.loose_dir, "e.png"), png(9, 9))
        self.write(os.path.join(self.loose_dir, "f.png"), png(4, 3))
        self.start("--loose", self.loose_dir)
        text = self.get("/live/a.atlas")[1].decode()
        self.assertTrue(text.startswith("e.png\n"), text[:20])
        self.assertLess(text.index("r0"), text.index("f.png"))
        self.assertEqual(self.get("/live/e.png")[1], png(shade=5), "the atlas's page, not the loose file")

    def test_an_unreadable_page_keeps_the_last_good_one(self):
        self.start()
        s = self.stream()
        s.next()
        good = self.get("/live/page.jplax")[1]
        for bad in ("{ half a fi", "[]"):
            self.write(self.page, bad)
            s.quiet(0.4)
            self.assertEqual(self.get("/live/page.jplax")[1], good)

    def test_a_plaxpj_is_refused(self):
        project = self.page + ".plaxpj"
        self.write(project, "{}")
        run = subprocess.run([sys.executable, "-I", STUB, project, self.atlas_dir, "--port", "0"],
                             check=False, capture_output=True, text=True, timeout=20)
        self.assertEqual(run.returncode, 2)
        self.assertIn("plaxpj", run.stderr)


if __name__ == "__main__":
    unittest.main()
