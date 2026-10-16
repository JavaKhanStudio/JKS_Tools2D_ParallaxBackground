"""The hills' canopy gate per theme (r441, r456): the one entry of themes.json a theme takes. No GPU.

    python3 tools/r239-ai-strips/themes.py "sandy desert canyon at sunset"

prints that entry alone: whether ai_page refuses hills strips without round trees on that theme, what the gate is
worth there (measured or an estimate), and the flags that suit it. ai_page.py reads the same entry.
"""
import json
import os
import re
import sys

TABLE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "themes.json")
KEYS = ("name", "match", "gate", "tries", "status", "why", "recommend")


def load(path=TABLE):
    with open(path) as f:
        return json.load(f)["themes"]


def theme_entry(theme, entries=None):
    """The first entry one of whose match words (or phrases) is in the theme, whole, any case; else 'default'."""
    entries = load() if entries is None else entries
    for e in entries:
        if any(re.search(rf"(?<![\w-]){re.escape(w)}(?![\w-])", theme, re.IGNORECASE) for w in e["match"]):
            return e
    return next(e for e in entries if e["name"] == "default")


def describe(e):
    """The entry as a few lines for the skill to read."""
    out = [f"theme entry: {e['name']} ({e['status']})",
           f"hills gate: on, up to {e['tries']} seeds, then the most wooded" if e["gate"] else
           "hills gate: off, the first seed layer.py takes"]
    if e["status"] == "tested":
        out += [f"measured: seeds {e['seeds']}; pass {e['pass']:.0%}, {e['strips']} hills strips painted",
                f"looks: {e['looks']}"]
    else:
        out.append("untested: an estimate; measure with tools/r441-hills-themes/themes.py OUT, then edit themes.json")
    out += [f"why: {e['why']}", f"recommend: {e['recommend']}"]
    return "\n".join(out)


if __name__ == "__main__":
    if len(sys.argv) != 2:
        sys.exit(__doc__.strip())
    print(describe(theme_entry(sys.argv[1])))
