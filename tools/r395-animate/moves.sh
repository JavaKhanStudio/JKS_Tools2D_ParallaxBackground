#!/usr/bin/env bash
# moves.sh OUT — do an ai-page.sh --animate page's round stills show its patch move (r395)?
#
#   tools/ai-page.sh ... --variants 0 --animate l3='palm tree fronds tossing in a strong sea wind' \
#       --animate-rows l3=90-250 build/r395/beach
#   tools/r395-animate/moves.sh build/r395/beach
#
# Copies OUT/page to OUT/held with every patch's animFps 0 (frame 0 at every time), takes its stills through
# tools/parallax-lab-shots.sh (cage, off screen) and compares them with OUT/shots: moves.py. Writes OUT/moves.png.
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=${1:?OUT: an ai-page.sh --animate run}
rm -rf "$OUT/held" "$OUT/held-shots"
cp -r "$OUT/page" "$OUT/held"
python3 - "$OUT" <<'PY'
import glob, json, os, sys
out = sys.argv[1]
for p in glob.glob(f"{out}/held/*.jplax"):
    page = json.load(open(p))
    for layer in page["pageModel"]["pageList"]:
        if layer.get("animRegion"):
            layer["animFps"] = 0.0
    json.dump(page, open(p, "w"), indent=1)
r = f"{out}/held/round.json"
j = json.load(open(r))
src, dst = os.path.relpath(f"{out}/page"), os.path.relpath(f"{out}/held")
for s in j["scenes"]:
    s["page"], s["atlasDir"] = s["page"].replace(src, dst), s["atlasDir"].replace(src, dst)
json.dump(j, open(r, "w"), indent=1)
PY
tools/parallax-lab-shots.sh "$OUT/held" "$OUT/held-shots" >/dev/null
python3 "$(dirname "$0")/moves.py" "$OUT/shots" "$OUT/held-shots" "$OUT/moves.png"
