#!/usr/bin/env bash
# headless: PIL on PNGs; the films go through ai-film.sh, which renders in cage (parallax-lab-shots.sh)
# compare.sh BEFORE AFTER OUT — r384's compare page for Simon: the AFTER page is BEFORE's page with only AFTER's hills
# strip (l2_cut) pasted at its atlas region, so the two films and stills differ by the hills alone; both filmed by
# the real reader, then compare_page.py writes OUT/page/r384-compare.html and its media (attach them all together).
#   tools/r384-hills/compare.sh build/r365/after/forest-painted build/r384/forest build/r384/compare
set -eu
cd "$(dirname "$0")/../.."
B=${1%/} A=${2%/} O=${3:?OUT}
rm -rf "$O/after"
mkdir -p "$O/after"
cp -r "$B/page" "$B/strips" "$B/shots" "$O/after/"
cp "$A/strips/l2_cut.png" "$A/strips/l2_ai.png" "$O/after/strips/"
python3 - "$O/after" <<'PY'
import glob, json, os, re, sys
from PIL import Image
out = os.path.abspath(sys.argv[1])
atlas = glob.glob(f"{out}/page/*.atlas")[0]
sheet = Image.open(atlas[:-6] + ".png").convert("RGBA")
x, y, w, h = map(int, re.search(r"^l2\n  rotate: \w+\n  xy: (\d+), (\d+)\n  size: (\d+), (\d+)", open(atlas).read(), re.M).groups())
img = Image.open(f"{out}/strips/l2_cut.png").convert("RGBA")
assert img.size == (w, h), img.size
sheet.paste(img, (x, y))
sheet.save(atlas[:-6] + ".png")
r = json.load(open(f"{out}/page/round.json"))
for s in r["scenes"]:
    s["page"] = os.path.join(out, "page", os.path.basename(s["page"]))
    s["atlasDir"] = os.path.join(out, "page")
json.dump(r, open(f"{out}/page/round.json", "w"), indent=1)
PY
tools/ai-film.sh "$B/page" "$O/film-before" 10 30 >"$O/film-before.log" 2>&1
tools/ai-film.sh "$O/after/page" "$O/film-after" 10 30 >"$O/film-after.log" 2>&1
python3 tools/r384-hills/compare_page.py "$B" "$O/after" "$O/page" --films "$O/film-before" "$O/film-after"
