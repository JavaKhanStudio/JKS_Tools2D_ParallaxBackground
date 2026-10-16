#!/usr/bin/env bash
# headless: unit tests, no window
# mutate.sh — r446's proof: ai_page.Pipeline.bottoms' lowering taken out, or blind to a SEQUENCE's clearest segment, bottoms_test.py must
# fail. Copies tools/*.py and tools/r239-ai-strips into a scratch folder, breaks one line at a time and runs the test:
# a mutant the test passes is a fix nothing proves. Exits 1 when one survives.
set -u
cd "$(dirname "$0")/../.." || exit 1
PY="${PYTHON:-python3}"
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
cp tools/*.py "$WORK/"
cp -r tools/r239-ai-strips "$WORK/"
survived=0
mutate() {  # name, file under tools/, the line as it is, the line broken
	cp "tools/$2" "$WORK/$2"
	"$PY" - "$WORK/$2" "$3" "$4" <<'PY' || { echo "$1: the line is gone, update mutate.sh"; exit 2; }
import sys
p, old, new = sys.argv[1:]
s = open(p).read()
if s.count(old) != 1:
    sys.exit(1)
open(p, "w").write(s.replace(old, new))
PY
	if "$PY" "$WORK/r239-ai-strips/bottoms_test.py" >"$WORK/$1.log" 2>&1; then
		echo "SURVIVED  $1"
		survived=1
	elif grep -qE '^(FAIL|ERROR): test_' "$WORK/$1.log"; then
		echo "killed    $1: $(grep -m1 -oE '^(FAIL|ERROR): test_[a-z_]+' "$WORK/$1.log")"
	else
		echo "BROKEN    $1: no test failed, the run did (see below)"; tail -3 "$WORK/$1.log"; survived=1
	fi
	cp "tools/$2" "$WORK/$2"
}
AP=r239-ai-strips/ai_page.py
mutate "no-lowering" $AP "            while bottoms[k] - row >= floor and" "            while False and bottoms[k] - row >= floor and"
mutate "last-segment-only" $AP \
	'            clears.append(lab.sequence_clear([lab._clear(Image.open(p).convert("RGBA")) for p in paths]))' \
	'            clears.append(lab._clear(Image.open(paths[-1]).convert("RGBA")))'
exit "$survived"
