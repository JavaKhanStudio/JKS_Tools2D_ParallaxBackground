#!/usr/bin/env bash
# demo.sh [OUT] — r375's demo: the r365 beach page's palm crowns and sea animated by Wan VACE, looped, checked, filmed.
#
# Needs ~/ComfyUI with the Wan 2.1 VACE 1.3B models (anim.py's docstring) and build/r365/after/beach-painted (r365's
# run: tools/ai-page.sh). OUT (build/r375) gets palms/ and sea/ (the loops), sea-wave/ (the WAVE shader's, the
# baseline), each with loop.json (loopcheck.py's verdict), and page-ai.mp4 / page-wave.mp4 (film.py's mock).
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
S=build/r365/after/beach-painted/strips
OUT=${1:-build/r375}
PY=${COMFY_PY:-$HOME/ComfyUI/venv/bin/python}
mkdir -p "$OUT"

python3 - "$S" "$OUT" <<'EOF'
import sys
import numpy as np
from PIL import Image, ImageFilter
S, out = sys.argv[1:]
sea = np.asarray(Image.open(f"{S}/l1_cut.png"))[..., 3] > 128
Image.fromarray((sea * 255).astype(np.uint8)).save(f"{out}/sea_mask.png")
palms = np.asarray(Image.open(f"{S}/l3_cut.png"))[..., 3] > 128
crowns = np.asarray(Image.fromarray((palms * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(9))).copy()
crowns[250:] = 0  # the trunks stay still: only the crowns, rows 90-250 of the 384, move
crowns[:90] = 0
Image.fromarray(crowns).filter(ImageFilter.GaussianBlur(3)).save(f"{out}/crown_mask.png")
EOF

animate() {  # NAME LAYER MASK PROMPT [anim.py args]
    local name=$1 layer=$2 mask=$3 prompt=$4
    shift 4
    "$PY" "$here/anim.py" "$OUT/$name" --src "$S/${layer}_ai.png" --mask "$mask" --prompt "$prompt" "$@"
    python3 "$here/loopcut.py" "$OUT/$name/raw" "$OUT/$name" --mask "$OUT/$name/mask.png"
    python3 "$here/rekey.py" "$OUT/$name" --src "$S/${layer}_ai.png" --cut "$S/${layer}_cut.png" \
        --mask "$OUT/$name/mask.png"
    python3 "$here/loopcheck.py" "$OUT/$name" --alpha "$S/${layer}_cut.png" --json "$OUT/$name/loop.json"
}

animate palms l3 "$OUT/crown_mask.png" \
    "palm tree fronds swaying gently in a warm sea breeze, leaves moving, static camera, painted illustration"
animate sea l1 "$OUT/sea_mask.png" \
    "calm tropical sea, small waves rolling and glittering in the sun, gentle water motion, static camera, painted illustration" \
    --context 64
python3 "$here/wave.py" "$S/l1_cut.png" "$OUT/sea-wave"
python3 "$here/loopcheck.py" "$OUT/sea-wave" --alpha "$S/l1_cut.png" --json "$OUT/sea-wave/loop.json"

python3 "$here/film.py" "$S" "$OUT/page-ai.mp4" --lifts 110,85,0,0 --anim "l1=$OUT/sea" --anim "l3=$OUT/palms" --seconds 10
python3 "$here/film.py" "$S" "$OUT/page-wave.mp4" --lifts 110,85,0,0 --anim "l1=$OUT/sea-wave" --anim "l3=$OUT/palms" \
    --seconds 10
