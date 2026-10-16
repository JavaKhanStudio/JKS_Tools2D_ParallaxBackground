#!/usr/bin/env bash
# r458: proves tools/depth-lab.sh --angles sees the depth lab's empty view. DepthLab.fit gets r444's way back (a corner
# behind the eye clamped to just ahead of it, the pivot free to go under the ground: seen from behind the camera, low,
# the view came out empty), builds, runs --angles, which must FAIL, and puts DepthLab back.
#   tools/r458-depth-ui/mutate.sh
# headless: runs tools/depth-lab.sh --angles only, headless Chrome
set -u
cd "$(dirname "$0")/../.." || exit 1
LAB=core/browser-test/launcher/jks/tools2d/parallax/browsertest/DepthLab.java
KEEP=$(mktemp)
cp "$LAB" "$KEEP"
trap 'cp "$KEEP" "$LAB"; rm -f "$KEEP"; ./gradlew -q :core:browserTestWar >/dev/null 2>&1' EXIT
python3 - "$LAB" <<'PY' || exit 2
import sys
path = sys.argv[1]
with open(path) as f:
    s = f.read()
old = "\t\t\t\t\tif (along < d * 0.05f)\n\t\t\t\t\t{\n\t\t\t\t\t\tbehind = true;\n\t\t\t\t\t\tcontinue;\n\t\t\t\t\t}\n"
if old not in s or "\t\t\tpivot.y = Math.max(0, pivot.y);\n" not in s:
    sys.exit("mutant not applied: DepthLab.fit changed")
s = s.replace(old, "\t\t\t\t\talong = Math.max(along, d * 0.05f);\n").replace("\t\t\tpivot.y = Math.max(0, pivot.y);\n", "")
with open(path, "w") as f:
    f.write(s)
PY
./gradlew -q :core:browserTestWar >/dev/null || exit 2
OUT=$(mktemp -d)
LOG=$(mktemp)
tools/depth-lab.sh --no-build --angles "$OUT" >"$LOG" 2>&1
FAILED=$?
grep -c "no card's tag shown" "$LOG" | sed 's/$/ empty views/'
rm -rf "$OUT"
if [ $FAILED = 0 ] || ! grep -q "no card's tag shown" "$LOG"; then
	rm -f "$LOG"
	echo "FAIL: --angles passed the depth lab's empty view" >&2
	exit 1
fi
rm -f "$LOG"
echo "OK: --angles fails the empty view"
