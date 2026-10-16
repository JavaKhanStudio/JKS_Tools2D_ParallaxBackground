"""r450: ram.py's reading of free memory (MemAvailable, capped by a cgroup's memory.max) and ai_page.py's account of a
gen.py that stopped (killed by a signal, refused for RAM, or failed). No GPU.

    python3 tools/r239-ai-strips/ram_test.py
"""
import os
import sys
import tempfile
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ai_page
import ram

GB = ram.GB


def fake_root(d, avail_kb, cgroup="/user.slice/app.slice/run-1.scope", caps=(), anon_kb=0, cache=None):
    """A /proc and /sys/fs/cgroup holding MemAvailable, the process's cgroup and, per cgroup path, (max, current)."""
    os.makedirs(os.path.join(d, "proc/self"))
    with open(os.path.join(d, "proc/meminfo"), "w") as f:
        f.write(f"MemTotal:       32000000 kB\nMemFree:  100 kB\nMemAvailable:   {avail_kb} kB\n")
    with open(os.path.join(d, "proc/self/cgroup"), "w") as f:
        f.write(f"0::{cgroup}\n")
    with open(os.path.join(d, "proc/self/status"), "w") as f:
        f.write(f"Name: python\nRssAnon:\t{anon_kb} kB\nRssFile:\t5 kB\n")
    for path, (cap, current) in caps:
        g = os.path.join(d, "sys/fs/cgroup", path.lstrip("/"))
        os.makedirs(g, exist_ok=True)
        with open(os.path.join(g, "memory.max"), "w") as f:
            f.write(f"{cap}\n")
        with open(os.path.join(g, "memory.current"), "w") as f:
            f.write(f"{current}\n")
        if cache is not None:
            with open(os.path.join(g, "memory.stat"), "w") as f:
                f.write(f"anon 1\nfile {sum(cache)}\nactive_file {cache[0]}\ninactive_file {cache[1]}\nshmem 0\n")
    return d


class RamTest(unittest.TestCase):

    def test_uncapped_reads_mem_available(self):
        with tempfile.TemporaryDirectory() as d:
            root = fake_root(d, 12 * 2**20, caps=[("/user.slice", ("max", 9 * GB))])
            self.assertEqual(12 * GB, ram.available(root))

    def test_a_scope_s_memory_max_caps_it(self):
        """A systemd scope with MemoryMax=5G, 1 GB used: 4 GB left, whatever the system has free."""
        with tempfile.TemporaryDirectory() as d:
            root = fake_root(d, 12 * 2**20, caps=[("/user.slice/app.slice/run-1.scope", (5 * GB, GB))])
            self.assertEqual(4 * GB, ram.available(root))

    def test_a_scope_s_page_cache_is_free(self):
        """r450: a strip read 6 GB of model files into the scope's page cache; the next strip's check counts it free."""
        with tempfile.TemporaryDirectory() as d:
            root = fake_root(d, 12 * 2**20, caps=[("/user.slice/app.slice/run-1.scope", (10 * GB, 7 * GB))],
                             cache=(2 * GB, 4 * GB))
            self.assertEqual(9 * GB, ram.available(root))

    def test_an_ancestor_s_cap_counts(self):
        with tempfile.TemporaryDirectory() as d:
            root = fake_root(d, 12 * 2**20, caps=[("/user.slice", (8 * GB, 6 * GB))])
            self.assertEqual(2 * GB, ram.available(root))

    def test_shortfall_counts_what_the_process_holds_already(self):
        """r450: 7.4 GB peak, 0.5 GB already held, 6 GB free: 0.9 GB short; 7 GB free: it fits."""
        with tempfile.TemporaryDirectory() as d:
            root = fake_root(d, 6 * 2**20, anon_kb=2**19)
            self.assertAlmostEqual(0.9, ram.shortfall(int(7.4 * GB), root) / GB, places=6)
        with tempfile.TemporaryDirectory() as d:
            root = fake_root(d, 7 * 2**20, anon_kb=2**19)
            self.assertEqual(0, ram.shortfall(int(7.4 * GB), root))


class GenFailedTest(unittest.TestCase):

    def test_a_killed_gen_names_the_oom_killer_not_vram(self):
        """r450: the kernel's SIGKILL, as run() returns it from a real process."""
        with tempfile.TemporaryDirectory() as d:
            log = os.path.join(d, "l0_gen.log")
            code = ai_page.run(["bash", "-c", "kill -9 $$"], log)
            self.assertEqual(-9, code)
            said = ai_page.gen_failed(code, log)
            self.assertIn("killed by SIGKILL (out of memory? journalctl -k)", said)
            self.assertNotIn("VRAM", said)

    def test_a_ram_refusal_is_its_own_line(self):
        with tempfile.TemporaryDirectory() as d:
            log = os.path.join(d, "l0_gen.log")
            with open(log, "w") as f:
                f.write("WARNING: torch\ngen.py: not enough RAM for a DiT: it peaks at 7.4 GB, 4.1 GB can be taken\n")
            said = ai_page.gen_failed(ai_page.GEN_RAM_REFUSED, log)
            self.assertTrue(said.startswith("gen.py: not enough RAM for a DiT"), said)

    def test_any_other_failure_still_points_at_vram(self):
        self.assertIn("free VRAM", ai_page.gen_failed(1, "x.log"))


if __name__ == "__main__":
    unittest.main()
