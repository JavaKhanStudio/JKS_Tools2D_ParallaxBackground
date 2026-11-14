"""The shadow lab's light tables are daylight.py's (r539): Daylight.java's LIGHT_* arrays, word for word what
`daylight.py --java` prints. A change to the model there, not pasted into the lab, fails here.

    python3 tools/r425-daylight/daylight_test.py
"""
import os
import subprocess
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
JAVA = os.path.join(ROOT, "core/browser-test/launcher/jks/tools2d/parallax/browsertest/Daylight.java")


class DaylightTablesTest(unittest.TestCase):
    def test_java_tables_are_the_models(self):
        printed = subprocess.run([sys.executable, os.path.join(HERE, "daylight.py"), "--java"],
                                 check=True, capture_output=True, text=True).stdout
        tables = [line.strip() for line in printed.splitlines() if line.strip().startswith("static final float[] LIGHT_")]
        self.assertEqual(len(tables), 6, printed)
        with open(JAVA, encoding="utf-8") as f:
            java = {line.strip() for line in f}
        for table in tables:
            self.assertIn(table, java, f"Daylight.java lacks {table.split('=')[0].strip()} as daylight.py prints it")


if __name__ == "__main__":
    unittest.main()
