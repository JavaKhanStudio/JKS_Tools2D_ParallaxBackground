#!/usr/bin/env bash
# r319: proves tools/haze-lab.sh --same still sees a panel that draws differently. Builds the lab with B and D hazing the
# towers under &same=1 too (HazeLab's `!same && HAZE_HOOKS[i]` made `HAZE_HOOKS[i]`), runs --same into OUT, and fails
# unless --same does. HazeLab.java is put back on exit; rebuild the war after (tools/haze-lab.sh does).
# headless: haze-lab.sh --same is headless Chrome.
set -u
OUT="$(realpath -m "${1:?usage: mutate.sh OUT.png}")"
cd "$(dirname "$0")/../.." || exit 2
LAB=core/browser-test/launcher/jks/tools2d/parallax/browsertest/HazeLab.java
SAVED=$(mktemp)
cp "$LAB" "$SAVED"
trap 'cp "$SAVED" "$LAB"; rm -f "$SAVED"' EXIT
sed -i 's/!same \&\& HAZE_HOOKS\[i\]/HAZE_HOOKS[i]/' "$LAB"
cmp -s "$LAB" "$SAVED" && { echo "the mutation no longer applies: HazeLab has no '!same && HAZE_HOOKS[i]'" >&2; exit 2; }
if tools/haze-lab.sh --same "$OUT"; then
	echo "MUTANT PASSED: --same did not see B and D hazing the towers" >&2
	exit 1
fi
echo "mutant caught: --same fails when B and D draw differently"
