"""themes.py (r456): a theme takes its themes.json entry, and ai_page runs the hills' canopy gate only where the entry
says. No GPU.

    python3 tools/r239-ai-strips/theme_gate_test.py

The tested themes are r441's (tools/r441-hills-themes/themes.py), each measured on ten seeds.
"""
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import themes  # noqa: E402

R441 = {"forest": "misty pine forest in the mountains at dawn", "desert": "sandy desert canyon at sunset",
        "snow": "snowy mountains in winter at dusk", "autumn": "autumn countryside at dusk",
        "ruins": "post-apocalyptic ruined city at dusk"}


def gate_line(theme):
    """ai_page --dry-run's line for the hills depth: its entry and whether the gate runs."""
    with tempfile.TemporaryDirectory() as out:
        err = subprocess.run([sys.executable, os.path.join(HERE, "ai_page.py"), "--theme", theme, out, "--dry-run"],
                             capture_output=True, text=True, check=True).stderr
    return next(x for x in err.splitlines() if "theme entry" in x)


class ThemeGateTest(unittest.TestCase):
    def test_every_r441_theme_takes_its_measured_entry(self):
        for name, theme in R441.items():
            e = themes.theme_entry(theme)
            self.assertEqual((e["name"], e["status"]), (name, "tested"), theme)

    def test_the_gate_runs_on_the_forest_only_of_the_measured(self):
        self.assertEqual({n for n, t in R441.items() if themes.theme_entry(t)["gate"]}, {"forest"})

    def test_woods_win_over_another_word(self):
        self.assertEqual(themes.theme_entry("moonlit pine forest at night")["name"], "forest")
        self.assertEqual(themes.theme_entry("snowy pine valley at dawn")["name"], "forest")

    def test_a_word_inside_another_entry_s_longer_match_does_not_count(self):
        # r481: examples.sh's beach took the forest's entry by its "palm trees", and with it the canopy gate.
        self.assertEqual(themes.theme_entry("tropical beach, palm trees, warm sunny afternoon")["name"], "beach")
        self.assertEqual(themes.theme_entry("a tropical forest at dawn")["name"], "jungle")
        self.assertEqual(themes.theme_entry("forest by the shore")["name"], "forest")
        self.assertEqual(themes.theme_entry("night forest")["name"], "forest")

    def test_examples_sh_s_themes_take_their_entries(self):
        self.assertEqual(themes.theme_entry("misty pine forest in the mountains at dawn")["name"], "forest")
        self.assertEqual(themes.theme_entry("post-apocalyptic ruined city, dusty orange haze, desolate")["name"], "ruins")

    def test_a_word_inside_another_does_not_match(self):
        self.assertEqual(themes.theme_entry("a waterfall over the driftwood")["name"], "default")

    def test_an_unknown_theme_takes_the_default_gate_off(self):
        e = themes.theme_entry("volcanic islands under a red moon sky")
        self.assertEqual((e["name"], e["gate"], e["status"]), ("default", False, "untested"))

    def test_every_entry_has_its_keys_and_a_tested_one_its_measures(self):
        for e in themes.load():
            self.assertTrue(set(themes.KEYS) <= set(e), e["name"])
            if e["status"] == "tested":
                self.assertTrue({"seeds", "pass", "strips", "looks"} <= set(e), e["name"])
            self.assertIn(e["status"], ("tested", "untested"))

    def test_the_dry_run_gates_the_forest_s_hills_and_not_the_desert_s(self):
        self.assertIn("forest (tested), canopy gate on, up to 10 seeds", gate_line(R441["forest"]))
        self.assertIn("desert (tested), canopy gate off, up to 5 seeds", gate_line(R441["desert"]))


if __name__ == "__main__":
    unittest.main()
