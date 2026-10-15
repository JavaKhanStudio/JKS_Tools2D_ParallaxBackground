#!/usr/bin/env bash
# headless: numpy on arrays, no window
# mutate.sh — r382: layer_test.py must fail on layer.py with local_sky reading the mass SD painted past the mask as
# sky again (masslike off), and r358's glow mutants must still fail it (tools/r358-glow/mutate.sh).
set -u
cd "$(dirname "$0")/../.." || exit 1
S=tools/r239-ai-strips
d=$(mktemp -d -p .git r382-mutant.XXXX)
cp "$S"/layer.py "$S"/layer_test.py "$S"/creature.py "$d"/
python3 - "$d/layer.py" <<'PY'
import sys
p = sys.argv[1]
old = "    return (img - sky) @ axis / max(float(axis @ axis), 1.0) > 0.5"
s = open(p).read()
assert old in s, old
open(p, "w").write(s.replace(old, "    return np.zeros(m.shape, bool)"))
PY
bad=0
if python3 "$d"/layer_test.py >"$d"/out 2>&1; then
	echo "WRONG masslike off: the tests pass"
	bad=1
else
	echo "ok    masslike off: $(grep -c '^FAIL' "$d"/out) test(s) fail"
	grep -o 'AssertionError: .*' "$d"/out | sed 's/^/      /'
fi
rm -rf "$d"
tools/r358-glow/mutate.sh || bad=1
exit $bad
