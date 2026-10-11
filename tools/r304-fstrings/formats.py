"""r304: the format strings same.sh cannot reach (shots.py's failure lines: no lab fails today), old % against the new
f-string, on values of each type the page can hand them (a bounding box is a float, a count an int)."""
name, size, errors = "fog", "desktop", ["boom"]
# ruff: noqa: UP031  (the old % lines are what this compares against)
cases = 0
for m in [{"lastSliderBottom": 812.6, "innerHeight": 710, "smallestControlText": 13.5, "overrun": ["k"],
           "namedSliders": ["mode"], "brokenCells": 2, "scrollWidth": 402, "innerWidth": 390},
          {"lastSliderBottom": 801, "innerHeight": 710.0, "smallestControlText": 12, "overrun": [], "namedSliders": [],
           "brokenCells": 1.0, "scrollWidth": 400.7, "innerWidth": 390}]:
    for old, new in [
        ("%s %s: page errors %s" % (name, size, errors), f"{name} {size}: page errors {errors}"),
        ("%s: the last slider ends at %dpx, below the %dpx window" % (name, m["lastSliderBottom"], m["innerHeight"]),
         f"{name}: the last slider ends at {int(m['lastSliderBottom'])}px, below the {int(m['innerHeight'])}px window"),
        ("%s %s: control text at %spx" % (name, size, m["smallestControlText"]),
         f"{name} {size}: control text at {m['smallestControlText']}px"),
        ("%s %s: a value runs out of its slider's box: %s" % (name, size, m["overrun"]),
         f"{name} {size}: a value runs out of its slider's box: {m['overrun']}"),
        ("%s %s: sliders over names, not choices: %s" % (name, size, m["namedSliders"]),
         f"{name} {size}: sliders over names, not choices: {m['namedSliders']}"),
        ("%s %s: %d PASS/FAIL cells broken over lines" % (name, size, m["brokenCells"]),
         f"{name} {size}: {int(m['brokenCells'])} PASS/FAIL cells broken over lines"),
        ("%s: %dpx wide on a %dpx phone" % (name, m["scrollWidth"], m["innerWidth"]),
         f"{name}: {int(m['scrollWidth'])}px wide on a {int(m['innerWidth'])}px phone"),
    ]:
        assert old == new, (old, new)
        cases += 1
print(f"SAME formats: {cases} cases")
