#!/usr/bin/env bash
# headless: tools/anim-lab.sh --views, headless Chrome
# lab-mutate.sh OUT — r455's proof: the animation lab's MIRROR + GLINT hearts (the detail levels' 'high', 'sea: SWELL +
# MIRROR' and the last of 'sea: MIRROR or not') draw through the shipped MIRROR (GdxLayerEffects.MIRROR), not a lab
# copy. Takes the lab's stills from a scratch worktree of HEAD, then from the same worktree with the shipped MIRROR's
# relief tripled. Fails unless views 0, 6 and 7 move (by over 0.01/255: in 0 and 7 the heart is one small panel of
# three or four, about 0.05) and the others do not move at all (r435's mutate.sh, for SWELL).
set -u
cd "$(dirname "$0")/../.." || exit 1
[ $# = 1 ] || { echo "usage: $0 OUT" >&2; exit 2; }
OUT="$(realpath -m "$1")"
G=core/src/jks/tools2d/parallax/GdxLayerEffects.java
mkdir -p "$OUT"
WT=$(mktemp -d -p .git r455-mutant.XXXX)
trap 'git worktree remove --force "$WT" >/dev/null 2>&1; rm -rf "$WT"' EXIT
git worktree add --detach "$WT" HEAD >/dev/null 2>&1 || exit 1
"$WT/tools/anim-lab.sh" --views "$OUT/plain" >/dev/null 2>&1 || { echo "anim-lab.sh failed on HEAD" >&2; exit 1; }
sed -i 's|"	float facet = tilt + 0.5 \* u_mirror.y \* relief;|"	float facet = tilt + 1.5 * u_mirror.y * relief;|' "$WT/$G"
grep -q 'facet = tilt + 1.5 \* u_mirror.y' "$WT/$G" || { echo "the mutant no longer applies to $G" >&2; exit 1; }
"$WT/tools/anim-lab.sh" --views "$OUT/mutant" >/dev/null 2>&1 || { echo "anim-lab.sh failed on the mutant" >&2; exit 1; }
python3 - "$OUT" <<'PY'
import sys
import numpy as np
from PIL import Image
out = sys.argv[1]
moves = {0: True, 1: False, 2: False, 3: False, 4: False, 5: False, 6: True, 7: True}
bad = 0
for k, should in moves.items():
    a = np.asarray(Image.open(f"{out}/plain/view{k}.png").convert("RGB"), float)
    b = np.asarray(Image.open(f"{out}/mutant/view{k}.png").convert("RGB"), float)
    d = np.abs(a - b).mean()
    ok = d > 0.01 if should else d == 0
    bad += not ok
    print(f"view{k}: mean {d:.4f}/255, {'moves' if should else 'stays'}: {'OK' if ok else 'FAIL'}")
sys.exit(1 if bad else 0)
PY
