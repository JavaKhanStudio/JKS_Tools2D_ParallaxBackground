#!/usr/bin/env bash
# headless: tools/anim-lab.sh --views, headless Chrome
# lab-mutate.sh OUT — r464's proof: the animation lab's "detail levels" view draws its three panels through the
# shipped detail levels (Parallax_Heart.setDetail), not lab copies. Takes the lab's stills from a scratch worktree of
# HEAD, then from the same worktree with ParallaxPageReader.setDetail ignoring the level (every heart at HIGH). Fails
# unless view 0 moves (by over 0.01/255) and the others do not move at all, or when view 0's three panels on HEAD are
# not three different pictures (each pair of panels apart by over 0.2/255 on the mean, the panels found on the
# mutant's stills, where all three are HIGH).
set -u
cd "$(dirname "$0")/../.." || exit 1
[ $# = 1 ] || { echo "usage: $0 OUT" >&2; exit 2; }
OUT="$(realpath -m "$1")"
R=core/src/jks/tools2d/parallax/ParallaxPageReader.java
mkdir -p "$OUT"
WT=$(mktemp -d -p .git r464-mutant.XXXX)
trap 'git worktree remove --force "$WT" >/dev/null 2>&1; rm -rf "$WT"' EXIT
git worktree add --detach "$WT" HEAD >/dev/null 2>&1 || exit 1
"$WT/tools/anim-lab.sh" --views "$OUT/plain" >/dev/null 2>&1 || { echo "anim-lab.sh failed on HEAD" >&2; exit 1; }
sed -i 's|{detail = level == null ? Enum_Detail.HIGH : level;}|{detail = Enum_Detail.HIGH;}|' "$WT/$R"
grep -q '{detail = Enum_Detail.HIGH;}' "$WT/$R" || { echo "the mutant no longer applies to $R" >&2; exit 1; }
"$WT/tools/anim-lab.sh" --views "$OUT/mutant" >/dev/null 2>&1 || { echo "anim-lab.sh failed on the mutant" >&2; exit 1; }
python3 - "$OUT" <<'PY'
import sys
import numpy as np
from PIL import Image
out = sys.argv[1]
bad = 0
for k in range(8):
    a = np.asarray(Image.open(f"{out}/plain/view{k}.png").convert("RGB"), float)
    b = np.asarray(Image.open(f"{out}/mutant/view{k}.png").convert("RGB"), float)
    d = np.abs(a - b).mean()
    should = k == 0
    ok = d > 0.01 if should else d == 0
    bad += not ok
    print(f"view{k}: mean {d:.4f}/255, {'moves' if should else 'stays'}: {'OK' if ok else 'FAIL'}")
# View 0's panels: high top left, medium top right, off under high. Under the mutant all three are HIGH, the same
# picture: where the other two sit against the first is where they match it best there. A box inside the first panel,
# under its title chip, is then compared with the same box in each other panel on HEAD.
m = np.asarray(Image.open(f"{out}/mutant/view0.png").convert("RGB"), float)
v = np.asarray(Image.open(f"{out}/plain/view0.png").convert("RGB"), float)
H, W = v.shape[:2]
y0, y1, x0, x1 = int(H * 0.13), int(H * 0.3), int(W * 0.02), int(W * 0.45)
box = m[y0:y1, x0:x1]
def best(dx_range, dy_range):
    return min((np.abs(box - m[y0 + dy:y1 + dy, x0 + dx:x1 + dx]).mean(), dx, dy) for dx in dx_range for dy in dy_range)
right = best(range(int(W * 0.45), int(W * 0.55)), range(-2, 3))
down = best(range(-2, 3), range(int(H * 0.2), int(H * 0.32)))
print(f"panels found at +{right[1]} across (mutant {right[0]:.3f}/255) and +{down[2]} down (mutant {down[0]:.3f}/255)")
bad += right[0] > 0.1 or down[0] > 0.1
panels = {name: v[y0 + dy:y1 + dy, x0 + dx:x1 + dx] for name, dx, dy in
          [('high', 0, 0), ('medium', right[1], right[2]), ('off', down[1], down[2])]}
for x, y in [('high', 'medium'), ('medium', 'off'), ('high', 'off')]:
    d = np.abs(panels[x] - panels[y]).mean()
    ok = d > 0.2
    bad += not ok
    print(f"view0 {x} against {y}: mean {d:.2f}/255: {'OK' if ok else 'FAIL'}")
sys.exit(1 if bad else 0)
PY
