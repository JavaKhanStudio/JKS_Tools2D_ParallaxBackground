#!/usr/bin/env bash
# headless: numpy on arrays, no window
# mutate.sh — r474: layer_test.py's EdgesOfTest must fail on layer.py with --edges-of's lay-back taken out, its kept
# columns cut short, its blend taken unpremultiplied (a clear pixel's colour bleeding into the ramp), or the islands
# cut blind to what the front keeps on those columns (a stub faded over the ramp).
set -u
cd "$(dirname "$0")/../.." || exit 1
S=tools/r239-ai-strips
bad=0
mutant() { # NAME PYTHON-EDIT
	d=$(mktemp -d -p .git r474-mutant.XXXX)
	cp "$S"/layer.py "$S"/layer_test.py "$S"/creature.py tools/parallax_rules.py "$d"/
	python3 - "$d/layer.py" "$2" <<'PY'
import sys
p, edit = sys.argv[1], sys.argv[2]
old, new = edit.split("=>")
s = open(p).read()
assert old in s, old
open(p, "w").write(s.replace(old, new))
PY
	if python3 "$d"/layer_test.py EdgesOfTest >"$d"/out 2>&1; then
		echo "WRONG $1: the tests pass"
		bad=1
	else
		echo "ok    $1: $(grep -c '^FAIL:' "$d"/out) test(s) fail, $(grep -c '^ERROR' "$d"/out) error(s)"
		grep -o 'AssertionError: .*' "$d"/out | head -2 | sed 's/^/      /'
	fi
	rm -rf "$d"
}
mutant "no lay-back" '        out = edges_of(out, Image.open(a.edges_of))=>        pass'
mutant "7 columns kept" 'x) - EDGE_KEEP + 1)=>x) - EDGE_KEEP + 2)'
mutant "straight blend" 'pm / np.maximum(a, 1e-6)=>f[..., :3] * (1 - r) + o[..., :3] * r'
mutant "islands blind to the front" ', front_edges(a, alpha.shape))=>)'
exit $bad
