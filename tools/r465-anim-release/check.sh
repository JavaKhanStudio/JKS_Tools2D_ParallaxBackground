#!/usr/bin/env bash
# headless: tools/anim-lab.sh --views, headless Chrome
# check.sh OUT — r465's proof: the animation lab draws its page's plain layers as a game does. Its AnimEffects
# hands release and the page fog to its GdxLayerEffects; the beach's palms, in front with no fog, are drawn after a
# release. Takes the lab's stills from this checkout, then from scratch worktrees of HEAD holding this checkout's
# AnimLab.java with (a) the page fog not handed over (the reader then fogs each layer through its own begin, and
# releases before the palms): must match within 1/255, the two paths a game may take agreeing; (b) release not handed
# over either (the lab before r465: the palms drawn through the program the layer behind them left, with its fog):
# must differ by more than 8/255 somewhere in every view.
set -u
cd "$(dirname "$0")/../.." || exit 1
[ $# = 1 ] || { echo "usage: $0 OUT" >&2; exit 2; }
OUT="$(realpath -m "$1")"
LAB=core/browser-test/launcher/jks/tools2d/parallax/browsertest/AnimLab.java
mkdir -p "$OUT"
tools/anim-lab.sh --views "$OUT/lab" >/dev/null 2>&1 || { echo "anim-lab.sh failed here" >&2; exit 1; }
variant() { # NAME METHOD...: the lab without those overrides
	local name=$1 wt
	shift
	wt=$(mktemp -d -p .git r465-"$name".XXXX)
	git worktree add --detach "$wt" HEAD >/dev/null 2>&1 || return 1
	python3 - "$LAB" "$wt/$LAB" "$@" <<'PY' || { git worktree remove --force "$wt"; return 1; }
import re, sys
src, dst, methods = sys.argv[1], sys.argv[2], sys.argv[3:]
s = open(src).read()
for m in methods:
    # The override: its @Override line through its body, a one-line {...} or a block.
    pattern = r"\t\t@Override\n\t\tpublic \w+ " + m + r"\(Batch batch[^)]*\)\n\t\t\{[^\n]*\}\n"
    s, n = re.subn(pattern, "", s)
    if n != 1:
        sys.exit(f"{m}: {n} overrides found in {src}")
open(dst, "w").write(s)
PY
	"$wt/tools/anim-lab.sh" --views "$OUT/$name" >/dev/null 2>&1
	local status=$?
	git worktree remove --force "$wt" >/dev/null 2>&1
	return $status
}
variant per-layer beginPageFog endPageFog || { echo "the per-layer variant failed" >&2; exit 1; }
variant unreleased beginPageFog endPageFog release || { echo "the unreleased variant failed" >&2; exit 1; }
python3 - "$OUT" <<'PY'
import sys
import numpy as np
from PIL import Image
out = sys.argv[1]
bad = 0
for k in range(8):
    lab = np.asarray(Image.open(f"{out}/lab/view{k}.png").convert("RGB"), int)
    per = np.abs(lab - np.asarray(Image.open(f"{out}/per-layer/view{k}.png").convert("RGB"), int)).max()
    unrel = np.abs(lab - np.asarray(Image.open(f"{out}/unreleased/view{k}.png").convert("RGB"), int)).max()
    ok = per <= 1 and unrel > 8
    bad += not ok
    print(f"view{k}: per-layer fog max {per}/255 (<= 1), unreleased max {unrel}/255 (> 8): {'OK' if ok else 'FAIL'}")
sys.exit(1 if bad else 0)
PY
