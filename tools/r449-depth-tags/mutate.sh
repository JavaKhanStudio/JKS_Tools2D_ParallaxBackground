#!/usr/bin/env bash
# r449: proves tools/depth-lab.sh --pages fails tags that cover each other. Takes out DepthLab.spread (the tags back at
# their r444 places: the deep pages' cards and the sky's over the back layer's cross), builds, runs the stills, which
# must FAIL, and puts the lab back.
#   tools/r449-depth-tags/mutate.sh
# headless: runs tools/depth-lab.sh --pages only, headless Chrome
set -u
cd "$(dirname "$0")/../.." || exit 1
LAB=core/browser-test/launcher/jks/tools2d/parallax/browsertest/DepthLab.java
KEEP=$(mktemp)
cp "$LAB" "$KEEP"
trap 'cp "$KEEP" "$LAB"; rm -f "$KEEP"; ./gradlew -q :core:browserTestWar >/dev/null 2>&1' EXIT
sed -i 's/^\t\tspread(overlay, labels);$//' "$LAB"
cmp -s "$LAB" "$KEEP" && { echo "mutant not applied: DepthLab.draw3D changed" >&2; exit 2; }
OUT=$(mktemp -d)
LOG=$(mktemp)
tools/depth-lab.sh --pages "$OUT" >"$LOG" 2>&1
FAILED=$?
# The crossings the r449 card named: the deep pages' cards, and the sky's over the back layer's.
grep -E "^  page [234]: '[0-9.]+×' over|sky' over|over '∞ sky'" "$LOG" | head -6
rm -f "$LOG"
if [ $FAILED = 0 ]; then
	echo "FAIL: the stills passed tags at their old places" >&2
	exit 1
fi
echo "OK: the stills fail tags at their old places"
