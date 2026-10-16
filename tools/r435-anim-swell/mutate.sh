#!/usr/bin/env bash
# headless: tools/anim-lab.sh --views, headless Chrome
# mutate.sh OUT — r435's proof: the animation lab's 'sea: SWELL' and 'sea: both' draw through the shipped SWELL
# (GdxLayerEffects.SWELL), not a lab copy. Takes the lab's stills from this checkout, then from a scratch worktree of
# HEAD holding this checkout's AnimLab.java and a SWELL whose push is tripled. Fails unless the views holding a SWELL
# heart (detail levels, all four, both, SWELL, MIRROR or not) move and the others (GLINT, WAVE, SWELL + MIRROR) do not.
set -u
cd "$(dirname "$0")/../.." || exit 1
[ $# = 1 ] || { echo "usage: $0 OUT" >&2; exit 2; }
OUT="$(realpath -m "$1")"
LAB=core/browser-test/launcher/jks/tools2d/parallax/browsertest/AnimLab.java
G=core/src/jks/tools2d/parallax/GdxLayerEffects.java
mkdir -p "$OUT"
tools/anim-lab.sh --views "$OUT/plain" >/dev/null 2>&1 || { echo "anim-lab.sh failed here" >&2; exit 1; }
WT=$(mktemp -d -p .git r435-mutant.XXXX)
trap 'git worktree remove --force "$WT" >/dev/null 2>&1; rm -rf "$WT"' EXIT
git worktree add --detach "$WT" HEAD >/dev/null 2>&1 || exit 1
cp "$LAB" "$WT/$LAB"
sed -i 's|"	vec2 l = local() + u_effect.x \* vec2(|"	vec2 l = local() + 3.0 * u_effect.x * vec2(|' "$WT/$G"
grep -q '3.0 \* u_effect.x' "$WT/$G" || { echo "the mutant no longer applies to $G" >&2; exit 1; }
"$WT/tools/anim-lab.sh" --views "$OUT/mutant" >/dev/null 2>&1 || { echo "anim-lab.sh failed on the mutant" >&2; exit 1; }
python3 - "$OUT" <<'PY'
import sys
import numpy as np
from PIL import Image
out = sys.argv[1]
moves = {0: True, 1: True, 2: True, 3: True, 4: False, 5: False, 6: False, 7: True}
bad = 0
for k, should in moves.items():
    a = np.asarray(Image.open(f"{out}/plain/view{k}.png").convert("RGB"), float)
    b = np.asarray(Image.open(f"{out}/mutant/view{k}.png").convert("RGB"), float)
    d = np.abs(a - b).mean()
    ok = d > 0.05 if should else d == 0
    bad += not ok
    print(f"view{k}: mean {d:.4f}/255, {'moves' if should else 'stays'}: {'OK' if ok else 'FAIL'}")
sys.exit(1 if bad else 0)
PY
