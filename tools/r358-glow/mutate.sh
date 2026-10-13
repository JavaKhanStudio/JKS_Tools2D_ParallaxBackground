#!/usr/bin/env bash
# headless: numpy on arrays, no window
# mutate.sh — r358: layer_test.py must fail on layer.py with either half of the glow fix taken out: the sky read from
# the top rows only (local_sky returns it), or the half-clear haze past the edge left as keyed (unglow off).
set -u
cd "$(dirname "$0")/../.." || exit 1
S=tools/r239-ai-strips
bad=0
mutant() { # NAME PYTHON-EDIT
	d=$(mktemp -d -p .git r358-mutant.XXXX)
	cp "$S"/layer.py "$S"/layer_test.py "$S"/creature.py "$d"/
	python3 - "$d/layer.py" "$2" <<'PY'
import sys
p, edit = sys.argv[1], sys.argv[2]
old, new = edit.split("=>")
s = open(p).read()
assert old in s, old
open(p, "w").write(s.replace(old, new))
PY
	if python3 "$d"/layer_test.py >"$d"/out 2>&1; then
		echo "WRONG $1: the tests pass"
		bad=1
	else
		echo "ok    $1: $(grep -c '^FAIL' "$d"/out) test(s) fail"
		grep -o 'AssertionError: .*' "$d"/out | sed 's/^/      /'
	fi
	rm -rf "$d"
}
mutant "one sky from the top rows" '    out = np.broadcast_to(sky, img.shape).copy()=>    return np.broadcast_to(sky, img.shape).copy()'
mutant "no unglow" '    if a.edge:=>    if False:'
exit $bad
