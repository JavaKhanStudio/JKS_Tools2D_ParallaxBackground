#!/usr/bin/env bash
# r503: proves the depth lab's two checks see its cards go wrong. A picture drawn upside down must fail
# tools/depth-lab.sh --seam; a dark layer's pane left faint must fail tools/r503-depth-cards/palms.py. Each builds; the
# lab is put back and rebuilt at the end. Run with nothing else editing DepthLab.java.
#   tools/r503-depth-cards/mutate.sh
# headless: runs tools/depth-lab.sh --seam and --shot only, headless Chrome
set -u
cd "$(dirname "$0")/../.." || exit 1
LAB=core/browser-test/launcher/jks/tools2d/parallax/browsertest/DepthLab.java
KEEP=$(mktemp)
OUT=$(mktemp -d)
cp "$LAB" "$KEEP"
trap 'cp "$KEEP" "$LAB"; rm -rf "$KEEP" "$OUT"; ./gradlew -q :core:browserTestWar >/dev/null 2>&1' EXIT
FAILED=0

sed -i 's/batch.draw(painted.getColorBufferTexture(), 0, 0, vw, vh, 0, 0, 1, 1);/batch.draw(painted.getColorBufferTexture(), 0, 0, vw, vh, 0, 1, 1, 0);/' "$LAB"
grep -q 'vw, vh, 0, 1, 1, 0);' "$LAB" || { echo "mutant not applied: DepthLab.card changed" >&2; exit 2; }
if tools/depth-lab.sh --seam "$OUT/seam" >"$OUT/seam.log" 2>&1; then
	echo "FAIL: the seam check passed a picture upside down" >&2
	FAILED=1
elif grep -q "pictures are not the game's frame" "$OUT/seam.log"; then
	echo "OK: the seam check fails a picture upside down"
else
	echo "FAIL: the seam check failed for another reason:" >&2
	tail -15 "$OUT/seam.log" >&2
	FAILED=1
fi
cp "$KEEP" "$LAB"

sed -i 's/PANE_DARK = 0.6f/PANE_DARK = 0.1f/' "$LAB"
grep -q 'PANE_DARK = 0.1f' "$LAB" || { echo "mutant not applied: DepthLab.PANE_DARK changed" >&2; exit 2; }
tools/depth-lab.sh --big --slider page=1 --slider view=1 --shot "$OUT/faint.png" >"$OUT/faint.log" 2>&1 || { echo "the faint still did not render:" >&2; tail -15 "$OUT/faint.log" >&2; exit 2; }
if python3 tools/r503-depth-cards/palms.py "$OUT/faint.png"; then
	echo "FAIL: palms.py passed a faint pane behind the palms" >&2
	FAILED=1
else
	echo "OK: palms.py fails a faint pane behind the palms"
fi
exit "$FAILED"
