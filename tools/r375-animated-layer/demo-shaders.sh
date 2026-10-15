#!/usr/bin/env bash
# demo-shaders.sh [OUT] — r375's second round: shader animations for water and clouds (shaders.py), and a stronger
# AI palm (anim.py --free), each checked by loopcheck.py, compared and filmed.
#
# Needs build/r365/after/beach-painted (r365's run: tools/ai-page.sh), build/ai/depth/valley (tools/depth-page.sh on
# build/r279/paint1.png: its back layer has the clouds) and, for the palms, demo.sh's ComfyUI with Wan 2.1 VACE and
# OUT0 (build/r375, demo.sh's) for the first palms. OUT (build/r375b) gets sea-swell/, sea-glint/, sea-swell-glint/,
# clouds-billow/, palms-free0.6/, palms-free1.0/ (each with loop.json), sea-compare.mp4, clouds-billow.mp4,
# palms-strength.mp4 and page-shaders.mp4 (film.py's mock: the sea swelling and glinting, the palms at free 1.0).
# headless: frames and films written to files, no window.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
S=build/r365/after/beach-painted/strips
V=build/ai/depth/valley/cut/l0.png
OUT=${1:-build/r375b}
OUT0=${OUT0:-build/r375}
PY=${COMFY_PY:-$HOME/ComfyUI/venv/bin/python}
mkdir -p "$OUT" "$OUT/still-valley"

for fx in swell glint swell,glint; do
    d="$OUT/sea-${fx/,/-}"
    python3 "$here/shaders.py" "$fx" "$S/l1_cut.png" "$d"
    python3 "$here/loopcheck.py" "$d" --alpha "$S/l1_cut.png" --json "$d/loop.json"
done
python3 "$here/shaders.py" billow "$V" "$OUT/clouds-billow"
python3 "$here/loopcheck.py" "$OUT/clouds-billow" --json "$OUT/clouds-billow/loop.json"
cp "$OUT/clouds-billow/f000.png" "$OUT/still-valley/f000.png"

for free in 0.6 1.0; do
    d="$OUT/palms-free$free"
    "$PY" "$here/anim.py" "$d" --src "$S/l3_ai.png" --mask "$OUT0/crown_mask.png" --free "$free" \
        --prompt "palm tree fronds tossing and swaying in a strong sea wind, leaves blowing, static camera, painted illustration"
    python3 "$here/loopcut.py" "$d/raw" "$d" --mask "$d/mask.png"
    python3 "$here/rekey.py" "$d" --src "$S/l3_ai.png" --cut "$S/l3_cut.png" --mask "$d/mask.png"
    python3 "$here/loopcheck.py" "$d" --alpha "$S/l3_cut.png" --json "$d/loop.json"
done

python3 "$here/compare.py" "$OUT/sea-compare.mp4" --box 200,296,712,384 --scale 2 \
    "WAVE (in the library today)=$OUT0/sea-wave" "swell=$OUT/sea-swell" "glint=$OUT/sea-glint" \
    "swell + glint=$OUT/sea-swell-glint" "Wan VACE frames (r375)=$OUT0/sea"
python3 "$here/compare.py" "$OUT/clouds-billow.mp4" --box 520,0,1024,240 \
    "still=$OUT/still-valley" "billow=$OUT/clouds-billow"
python3 "$here/compare.py" "$OUT/palms-strength.mp4" --box 300,90,760,260 --scale 2 --fps 12 --bg 96bee6 \
    "r375 palms (free 0)=$OUT0/palms" "free 0.6, strong wind=$OUT/palms-free0.6" "free 1.0, strong wind=$OUT/palms-free1.0"
python3 "$here/film.py" "$S" "$OUT/page-shaders.mp4" --lifts 110,85,0,0 --anim "l1=$OUT/sea-swell-glint" \
    --anim "l3=$OUT/palms-free1.0" --seconds 10 --anim-fps 12
