#!/usr/bin/env bash
# headless: unit tests, no window
# mutate.sh — r387's proof: each part of a moved field kept whole and seeding the layers behind taken out, a case in
# tools/r279-depth-page/rules_test.py must fail (r426: and the repaint's negative naming tracks).
#
# Copies tools/*.py, tools/r279-depth-page and tools/r239-ai-strips into a scratch folder, breaks one line of depth_page.py at a time and
# runs rules_test.py in the ComfyUI venv: a mutant the test passes is a fix nothing proves. Exits 1 when one survives.
set -u
cd "$(dirname "$0")/../.." || exit 1
PY="${COMFYUI_DIR:-$HOME/ComfyUI}/venv/bin/python"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
cp tools/*.py "$WORK/"
cp -r tools/r279-depth-page tools/r239-ai-strips "$WORK/"
DP=r279-depth-page/depth_page.py
survived=0
mutate() {  # name, the line as it is, the line broken
	cp "tools/$DP" "$WORK/$DP"
	"$PY" - "$WORK/$DP" "$2" "$3" <<'PY' || { echo "$1: the line is gone, update mutate.sh"; exit 2; }
import sys
p, old, new = sys.argv[1:]
s = open(p).read()
if s.count(old) != 1:
    sys.exit(1)
open(p, "w").write(s.replace(old, new))
PY
	if "$PY" "$WORK/r279-depth-page/rules_test.py" >"$WORK/$1.log" 2>&1; then
		echo "SURVIVED  $1"
		survived=1
	else
		echo "killed    $1: $(grep -m1 -oE '^(FAIL|ERROR): test_[a-z_]+' "$WORK/$1.log")"
	fi
	cp "tools/$DP" "$WORK/$DP"
}
"$PY" "$WORK/r279-depth-page/rules_test.py" >"$WORK/as-is.log" 2>&1 || { echo "rules_test.py fails as is"; exit 2; }
mutate "tuft-left-out" "        out[take] = k" "        pass"
mutate "top-edge-taken" "under = np.maximum.accumulate(stayed & (L == k), axis=0)" "under = np.ones_like(stayed)"
mutate "notches-kept" "        out[shut & (out < k)] = k" "        pass"
mutate "bays-closed" "def kept_fields(L, moved, fields, edge=6):" "def kept_fields(L, moved, fields, edge=30):"
mutate "track-seeded" "def field_colours(img, field, sigma=8):" "def field_colours(img, field, sigma=0.3):"
mutate "forest-seeded" "        src[field] = field_colours(img, ground & (L > k))[field]" "        pass"
mutate "tracks-drawn" '"--negative", NEG + TRACKS,' '"--negative", NEG,'
exit "$survived"
