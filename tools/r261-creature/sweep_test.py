"""sweep.sh in CI (r505): on a clean checkout, every creature it plants is flagged and every strip with nothing in it
passes, from the fixtures committed beside it. A fixture gone is a failure.

    python3 tools/r261-creature/sweep_test.py
"""
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
SWEEP = os.path.join(HERE, "sweep.sh")


def sweep(*args):
    return subprocess.run([SWEEP, *args], capture_output=True, text=True, check=False,
                          env={**os.environ, "PYTHON": sys.executable})


class Sweep(unittest.TestCase):
    def test_every_answer_right_on_the_committed_fixtures(self):
        r = sweep()
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertIn("every answer right", r.stdout)

    def test_a_missing_fixture_fails_instead_of_skipping(self):
        with tempfile.TemporaryDirectory() as empty:
            r = sweep(empty)
        self.assertEqual(r.returncode, 1, r.stdout + r.stderr)
        self.assertIn("MISSING", r.stdout)
        self.assertNotIn("every answer right", r.stdout)


if __name__ == "__main__":
    unittest.main()
