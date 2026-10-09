#!/usr/bin/env bash
# r218's mutant: makes ParallaxLayer.insetByHalfATexel return the region as is, runs the browser suite (headless
# Chrome), expects webgl: tileJoinsDoNotReadPastTheRegion to fail (a grey column at a join), then restores the file and
# runs the suite again, so the war left behind is the real one.
set -u
cd "$(dirname "$0")/../.." || exit 1
F=core/src/jks/tools2d/parallax/ParallaxLayer.java
git diff --quiet -- "$F" || { echo "$F has uncommitted changes: commit or stash them first" >&2; exit 2; }
trap 'git checkout -- "$F"' EXIT
sed -i 's/if (texture == null || Math.abs(region.getRegionWidth()) < 2/if (true || texture == null || Math.abs(region.getRegionWidth()) < 2/' "$F"
grep -q 'if (true || texture == null' "$F" || { echo "the mutation did not apply" >&2; exit 2; }
MUTANT=$(tools/browser-test.sh 2>/dev/null | grep -v WARNING)
git checkout -- "$F"
echo "mutant:   $(echo "$MUTANT" | head -1)"
echo "$MUTANT" | grep -i 'join\|shader'
RESTORED=$(tools/browser-test.sh 2>/dev/null | grep -v WARNING)
echo "restored: $(echo "$RESTORED" | head -1)"
echo "$MUTANT" | grep -q 'tileJoinsDoNotReadPastTheRegion' && echo "$RESTORED" | head -1 | grep -q ' 0 failed'
