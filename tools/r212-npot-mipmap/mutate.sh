#!/usr/bin/env bash
# r212's mutant: turns Utils_Etc2_Atlas's non-power-of-two fallback off, runs the browser suite (headless Chrome), and
# expects webgl: npotMipMapAtlasDraws* to fail (calm.atlas drawn black, 0 of 1024 pixels lit), then restores the file
# and runs the suite again, so the war left behind is the real one.
set -u
cd "$(dirname "$0")/../.." || exit 1
F=core/src/jks/tools2d/parallax/pages/Utils_Etc2_Atlas.java
git diff --quiet -- "$F" || { echo "$F has uncommitted changes: commit or stash them first" >&2; exit 2; }
trap 'git checkout -- "$F"' EXIT
sed -i 's/fit(data, isEtc2Atlas(\(.*\)), npotMipMapsRefused());/fit(data, isEtc2Atlas(\1), false);/' "$F"
[ "$(grep -c ', false);' "$F")" -ge 2 ] || { echo "the mutation did not apply" >&2; exit 2; }
MUTANT=$(tools/browser-test.sh 2>/dev/null | grep -v WARNING)
git checkout -- "$F"
echo "mutant:   $(echo "$MUTANT" | head -1)"
echo "$MUTANT" | grep npotMipMap
RESTORED=$(tools/browser-test.sh 2>/dev/null | grep -v WARNING)
echo "restored: $(echo "$RESTORED" | head -1)"
[ "$(echo "$MUTANT" | grep -c 'npotMipMapAtlasDraws')" = 2 ] && echo "$RESTORED" | head -1 | grep -q ' 0 failed'
