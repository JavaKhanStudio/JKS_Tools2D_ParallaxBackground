#!/usr/bin/env bash
# headless: unit tests, no window
# mutate.sh — r409's proof: each part of the clouds' own layer taken out, the test that holds it must fail (r416:
# the range's thin pieces hanging over its top too).
#
# Copies tools/*.py, tools/r279-depth-page and tools/r239-ai-strips into a scratch folder, breaks one line at a time
# (depth_page.py against clouds_test.py, ai_page.py against cloud_strip_test.py) and runs the test in the ComfyUI venv: a
# mutant the test passes is a fix nothing proves. Exits 1 when one survives.
set -u
cd "$(dirname "$0")/../.." || exit 1
PY="${COMFYUI_DIR:-$HOME/ComfyUI}/venv/bin/python"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
cp tools/*.py "$WORK/"
cp -r tools/r279-depth-page tools/r239-ai-strips "$WORK/"
survived=0
mutate() {  # name, file under tools/, its test under tools/, the line as it is, the line broken
	cp "tools/$2" "$WORK/$2"
	"$PY" - "$WORK/$2" "$4" "$5" <<'PY' || { echo "$1: the line is gone, update mutate.sh"; exit 2; }
import sys
p, old, new = sys.argv[1:]
s = open(p).read()
if s.count(old) != 1:
    sys.exit(1)
open(p, "w").write(s.replace(old, new))
PY
	if "$PY" "$WORK/$3" >"$WORK/$1.log" 2>&1; then
		echo "SURVIVED  $1"
		survived=1
	else
		echo "killed    $1: $(grep -m1 -oE '^(FAIL|ERROR): test_[a-z_]+' "$WORK/$1.log")"
	fi
	cp "tools/$2" "$WORK/$2"
}
DP=r279-depth-page/depth_page.py
CT=r279-depth-page/clouds_test.py
mutate "no-clouds" $DP $CT "    return out & (L == 0)" "    return out & False"
mutate "range-specks-kept" $DP $CT "    for k in (0, -1):" "    for k in (-1,):"
mutate "sky-specks-kept" $DP $CT "    hole = hole | grow(specks, 2)" "    hole = hole"
mutate "speck-median-over-the-crown" $DP $CT "ndimage.median_filter(v, size=window), np.where(hole, clear, lum))" \
	"ndimage.median_filter(v, size=window), lum)"
mutate "no-fade" $DP $CT "    alpha *= t * t * (3 - 2 * t)" "    alpha *= 1"
mutate "no-unmix" $DP $CT "bg + (f - bg) / np.maximum(alpha, 0.02)[..., None]" "f"
mutate "range-is-back" $DP $CT "    back = k == 0 and not apart  # the opaque back layer, the painted sky" "    back = k == 0"
mutate "sky-under-the-range" $DP $CT "    S[(S == -1) & (np.arange(S.shape[0])[:, None] >= range_top(S)[None])] = 0" "    pass"
mutate "halo-kept" $DP $CT "    if img is not None:" "    if False:"
mutate "flank-mirrored" $DP $CT \
	"        alpha = (L == 0) | ((np.arange(h)[:, None] >= range_top(L)[None]) & (L > 0))" \
	"        alpha = alpha_of(L, k)"
mutate "hanging-gaps-kept" $DP $CT "            S[sl][piece] = -1" "            pass"
mutate "range-edge-nibbled" $DP $CT "        if rise[sl][piece].max() > neck and (S[sl][ring] > 0).mean() >= nearer:" \
	"        if (S[sl][ring] > 0).mean() >= nearer:"
mutate "tip-taken" $DP $CT "        if rise[sl][piece].max() > neck and (S[sl][ring] > 0).mean() >= nearer:" \
	"        if rise[sl][piece].max() > neck:"
mutate "thin-read-as-sky" $DP $CT "    rise = range_top(np.where(thin, 1, S))[None]" "    rise = range_top(np.where(thin, -1, S))[None]"
mutate "heaps-cut" r239-ai-strips/ai_page.py r239-ai-strips/cloud_strip_test.py \
	'    return ["--keep-islands", "--no-creatures"] if kind == "clouds" else []' \
	'    return ["--no-creatures"] if kind == "clouds" else []'
exit "$survived"
