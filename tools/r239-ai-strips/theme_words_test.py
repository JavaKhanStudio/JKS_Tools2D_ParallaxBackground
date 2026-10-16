"""ai_page.theme_for (r437): a layer's prompt keeps the theme's setting and light, not the things another layer of the
page is for. No GPU.

    python3 tools/r239-ai-strips/theme_words_test.py

The three themes are examples.sh's, with their kinds.
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ai_page

BEACH = "tropical beach, palm trees, warm sunny afternoon"
FOREST = "misty pine forest in the mountains at dawn"
POSTAPO = "post-apocalyptic ruined city, dusty orange haze, desolate"


class ThemeWordsTest(unittest.TestCase):
    def test_the_beach_s_palms_stay_out_of_its_mountains_sea_and_dunes(self):
        ks = ["mountains", "sea", "dunes", "palms"]
        self.assertEqual(ai_page.theme_for(BEACH, "sea", ks), ("tropical beach, warm sunny afternoon", ["palm trees"]))
        for kind in ("mountains", "dunes"):
            self.assertIn("palm trees", ai_page.theme_for(BEACH, kind, ks)[1])
        self.assertEqual(ai_page.theme_for(BEACH, "palms", ks)[0], "palm trees, warm sunny afternoon")

    def test_a_sea_layer_keeps_the_sea_out_of_the_others_even_unnamed(self):
        # r442: "tropical beach" alone painted sea between the dunes (r438); the sea layer keeps the whole theme.
        ks = ["mountains", "sea", "dunes", "palms"]
        for kind in ("mountains", "dunes", "palms"):
            self.assertEqual(ai_page.theme_for(BEACH, kind, ks)[1][:4], ["tropical beach", "sea", "ocean", "water"])
        self.assertEqual(ai_page.theme_for("turquoise sea, white sand", "dunes", ks),
                         ("turquoise, white sand", ["sea", "ocean", "water"]))

    def test_a_sea_layer_s_beach_is_struck_from_the_others(self):
        # r443: under cfg 2 the negative alone left sea between the dunes on 6 seeds of 10; "tropical beach" struck from
        # the dunes' theme took it off all ten (tools/r443-dunes-sea).
        ks = ["mountains", "sea", "dunes", "palms"]
        self.assertEqual(ai_page.theme_for(BEACH, "dunes", ks)[0], "warm sunny afternoon")
        self.assertEqual(ai_page.theme_for("a lighthouse on a rocky coast at sunset", "mountains", ks)[1][0],
                         "rocky coast")
        self.assertEqual(ai_page.theme_for(BEACH, "dunes", ["mountains", "dunes", "palms"])[0],
                         "tropical beach, warm sunny afternoon")
        self.assertEqual(ai_page.theme_for("turquoise sea, white sand", "sea", ks), ("turquoise sea, white sand", []))
        self.assertNotIn("sea", ai_page.theme_for(BEACH, "dunes", ["mountains", "dunes", "palms"])[1])

    def test_the_forest_s_pines_keep_their_words_the_ranges_lose_them(self):
        ks = ai_page.kinds(4, "pines")
        self.assertEqual(ai_page.theme_for(FOREST, "pines", ks), (FOREST, []))
        self.assertEqual(ai_page.theme_for(FOREST, "hills", ks),
                         ("misty in the mountains at dawn", ["pine forest"]))

    def test_the_city_s_ruins_keep_their_words(self):
        ks = ["mountains", "ruins", "ruins", "ruins"]
        self.assertEqual(ai_page.theme_for(POSTAPO, "ruins", ks), (POSTAPO, []))
        self.assertEqual(ai_page.theme_for(POSTAPO, "mountains", ks),
                         ("post-apocalyptic, dusty orange haze, desolate", ["ruined city"]))

    def test_a_theme_naming_no_other_layer_s_things_is_left_whole(self):
        theme = "snowy pine valley at dawn"
        self.assertEqual(ai_page.theme_for(theme, "mountains", ["mountains", "hills", "hills"]), (theme, []))


if __name__ == "__main__":
    unittest.main()
