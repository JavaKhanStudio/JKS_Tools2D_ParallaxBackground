#!/usr/bin/env bash
# r444: proves tools/depth-lab.sh --seam sees a 3D view that is not the game's frame at 0. Scales every card by 1% more
# than its distance (DepthLab.cardAt), builds, runs the seam check, which must FAIL, and puts the lab back.
#   tools/r444-depth/mutate.sh
# headless: runs tools/depth-lab.sh --seam only, headless Chrome
set -u
cd "$(dirname "$0")/../.." || exit 1
LAB=core/browser-test/launcher/jks/tools2d/parallax/browsertest/DepthLab.java
KEEP=$(mktemp)
cp "$LAB" "$KEEP"
trap 'cp "$KEEP" "$LAB"; rm -f "$KEEP"; ./gradlew -q :core:browserTestWar >/dev/null 2>&1' EXIT
sed -i 's/\t\tfloat s = scaleOf(k);/\t\tfloat s = scaleOf(k) * 1.01f;/' "$LAB"
grep -q 'scaleOf(k) \* 1.01f' "$LAB" || { echo "mutant not applied: DepthLab.cardAt changed" >&2; exit 2; }
OUT=$(mktemp -d)
if tools/depth-lab.sh --seam "$OUT" | tail -6; then
	echo "FAIL: the seam check passed a card scaled 1% off" >&2
	exit 1
fi
echo "OK: the seam check fails a card scaled 1% off"
