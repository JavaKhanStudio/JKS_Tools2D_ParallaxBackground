#!/usr/bin/env bash
# r452: proves zoom.sh sees a zoom that draws another panel than the one clicked. LabKit.frame draws panel focus ^ 1
# (A for B, C for D; past the last, nothing) over the stage, builds, runs zoom.sh, which must FAIL, and puts LabKit back.
#   tools/r452-lab-zoom/mutate.sh
# headless: runs tools/r452-lab-zoom/zoom.sh only, headless Chrome
set -u
cd "$(dirname "$0")/../.." || exit 1
KIT=core/browser-test/launcher/jks/tools2d/parallax/browsertest/LabKit.java
KEEP=$(mktemp)
cp "$KIT" "$KEEP"
trap 'cp "$KEEP" "$KIT"; rm -f "$KEEP"; ./gradlew -q :core:browserTestWar >/dev/null 2>&1' EXIT
sed -i 's/^\t\tif (i != focus)$/\t\tif (i != (focus ^ 1))/' "$KIT"
cmp -s "$KIT" "$KEEP" && { echo "mutant not applied: LabKit.frame changed" >&2; exit 2; }
OUT=$(mktemp -d)
LOG=$(mktemp)
tools/r452-lab-zoom/zoom.sh "$OUT" >"$LOG" 2>&1
FAILED=$?
grep -E "looks like|off its own" "$LOG" | head -6
echo "... $(grep -cE "looks like|off its own" "$LOG") such lines"
# Every lab of zoom.py must see it on a desktop, but pines, whose two panels are the same picture at the check's time
# (a wrong zoom shows nothing else); on a phone, only the coarse likeness runs, which panels a slider's values apart
# (anim, hills, pines, snow) pass.
MISSED=""
for lab in $(grep -oE '^    \("[a-z-]+"' tools/r452-lab-zoom/zoom.py | tr -d ' ("'); do
	for size in desktop phone; do
		case "$lab/$size" in pines/desktop | anim/phone | hills/phone | pines/phone | effects-snow/phone) continue ;; esac
		grep -qE "^$lab $size: .*(looks like|off its own)" "$LOG" || MISSED="$MISSED $lab/$size"
	done
done
rm -f "$LOG"
if [ $FAILED = 0 ] || [ -n "$MISSED" ]; then
	echo "FAIL: zoom.sh passed a zoom drawing the wrong panel in:${MISSED:- every lab}" >&2
	exit 1
fi
echo "OK: zoom.sh fails a zoom drawing the wrong panel"
