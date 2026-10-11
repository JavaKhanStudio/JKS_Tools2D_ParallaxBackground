#!/usr/bin/env bash
# examples.sh [OUT] — r279's proof: two paintings cut into pages by their depth, and one that does not loop.
#
# Paints them first (gen.py txt2img --tile x, seeds 1 and 2): paint1 has a pine rising from the front into the sky,
# the hard case for a depth cut; paint2 a river valley with no such thing. paint1-noloop is paint1 cut short, so its
# join shows: the tool repaints a band round it. Each page goes through tools/depth-page.sh, which renders it with
# tools/parallax-lab-shots.sh (cage). Writes OUT (default build/r279)/{valley,river,noloop}/ and their .log.
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT="${1:-build/r279}"
PY="${COMFYUI_DIR:-$HOME/ComfyUI}/venv/bin/python"
mkdir -p "$OUT"
PROMPT="wide painterly 2d game background, a green valley with a winding river, distant blue mountains,"
PROMPT="$PROMPT rolling hills in the middle distance, tall pine trees in the foreground, clear sky, landscape painting"
for s in 1 2; do
	[ -f "$OUT/paint$s.png" ] || "$PY" tools/r239-ai-strips/gen.py txt2img --w 1024 --h 576 --tile x --seed $s \
		--prompt "$PROMPT" --negative "text, watermark, frame, border, people, buildings, birds, animals" \
		--out "$OUT/paint$s.png" >"$OUT/paint$s.log" 2>&1 || { echo "gen.py failed: $OUT/paint$s.log"; exit 1; }
done
python3 -c "from PIL import Image; import sys; Image.open(sys.argv[1]).crop((0, 0, 896, 576)).save(sys.argv[2])" \
	"$OUT/paint1.png" "$OUT/paint1-noloop.png"
T="green valley with a river, pine forest, blue mountains"
tools/depth-page.sh "$OUT/paint1.png" "$OUT/valley" --theme "$T" >"$OUT/valley.log" 2>&1; echo "valley: exit $?"
tools/depth-page.sh "$OUT/paint2.png" "$OUT/river" --theme "green hills, pine forest, winding river, blue mountains" \
	>"$OUT/river.log" 2>&1; echo "river: exit $?"
tools/depth-page.sh "$OUT/paint1-noloop.png" "$OUT/noloop" --theme "$T" >"$OUT/noloop.log" 2>&1; echo "noloop: exit $?"
