#!/usr/bin/env bash
# r541: proves tools/haze-lab.sh --same still sees a lab whose panels stray from A once it starts under the title chips.
# With &same=1 every panel draws as A, so only a panel-dependent change can show: the mutant lets B and D haze the
# towers (HAZE_HOOKS) as the lab does without &same. Runs the check on HEAD (must pass), then on the mutant (must fail),
# and puts HazeLab.java back. Mutates the checkout it runs in: run it in a worktree of your own.
#   tools/r541-haze-same/mutate.sh OUT_DIR
# headless: tools/haze-lab.sh --same is headless Chrome.
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT="$(realpath -m "${1:?OUT_DIR}")"
mkdir -p "$OUT"
LAB=core/browser-test/launcher/jks/tools2d/parallax/browsertest/HazeLab.java
cp "$LAB" "$OUT/HazeLab.java.orig"
trap 'cp "$OUT/HazeLab.java.orig" "$LAB"' EXIT
tools/haze-lab.sh --same "$OUT/control.png" > "$OUT/control.log" 2>&1 || { tail -5 "$OUT/control.log"; echo "FAIL: HEAD fails the check"; exit 1; }
grep 'vs A' "$OUT/control.log"
grep -q '!same && HAZE_HOOKS\[i\]' "$LAB" || { echo "FAIL: the mutated line is gone from $LAB"; exit 1; }
sed -i 's/!same && HAZE_HOOKS\[i\]/HAZE_HOOKS[i]/' "$LAB"
if tools/haze-lab.sh --same "$OUT/mutant.png" > "$OUT/mutant.log" 2>&1; then
	grep 'vs A' "$OUT/mutant.log"; echo "FAIL: the check passed a lab whose B and D haze the towers"; exit 1
fi
grep 'vs A' "$OUT/mutant.log" || { tail -5 "$OUT/mutant.log"; echo "FAIL: the mutant run died before the check"; exit 1; }
echo "OK: the check passes HEAD and fails the mutant"
