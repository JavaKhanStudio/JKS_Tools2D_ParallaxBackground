#!/usr/bin/env bash
# headless: ai_page.py --keep --no-shots and PIL only, no GPU, no window
# check.sh OUT — r406: re-lays R1's oj4-forest with HEAD's ai_page.py (relay.sh), prints its lint and the l3
# SEQUENCE's joins (r398's probe.py), and draws OUT/joins.png: R1's cut and HEAD's at the l3_0>l3_2 and l3_2>l3_0
# joins, where a pale far pine straddles the loop (r401 keeps it whole, half clear: joins 15-16, lint ok).
set -eu
cd "$(dirname "$0")/../.."
O=${1:?OUT}
E=../JKS_Tools2D_ParallaxAI/build/rounds/r1/oj4-forest
tools/r400-ai-rules/relay.sh tools/r239-ai-strips/ai_page.py "$O" "$E"
grep -E 'lint|^ *\(e\)' "$O/oj4-forest.log"
python3 tools/r398-seq-seams/probe.py "$O/oj4-forest/page/forest.jplax" 3 | grep -E '>'
R1=$E/strips N=$O/oj4-forest/strips Z=tools/r398-seq-seams/zoom.py
python3 "$Z" "$R1/l3_cut.png" "$R1/l3v2_cut.png" "$O/r1-0-2.png"
python3 "$Z" "$N/l3_cut.png" "$N/l3v2_cut.png" "$O/head-0-2.png"
python3 "$Z" "$N/l3v2_cut.png" "$N/l3_cut.png" "$O/head-2-0.png"
python3 - "$O" <<'PY'
import sys

from PIL import Image, ImageDraw

o = sys.argv[1]
parts = [('r1-0-2', 'R1 cut l3_0>l3_2'), ('head-0-2', 'HEAD l3_0>l3_2'), ('head-2-0', 'HEAD l3_2>l3_0')]
ims = [Image.open(f'{o}/{n}.png') for n, _ in parts]
sheet = Image.new('RGB', (sum(i.width for i in ims) + 20, max(i.height for i in ims) + 24), 'white')
d, x = ImageDraw.Draw(sheet), 0
for (_, t), i in zip(parts, ims):
    d.text((x + 4, 4), t + '  (top: join at red tick, bottom: inside)', fill='black')
    sheet.paste(i, (x, 24))
    x += i.width + 10
sheet.save(f'{o}/joins.png')
print(f'{o}/joins.png')
PY
