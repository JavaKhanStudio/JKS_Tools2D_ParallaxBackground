#!/usr/bin/env bash
# headless: numpy on arrays, no window
# mutate.sh — r400: layer_test.py's MistyPinesTest must fail on layer.py with misty_pines' act, its model condition or
# its width test taken out (a lit edge a px wide would key out with the mist), or its half-clear rim (r476: a pale
# line left across the gap).
set -u
cd "$(dirname "$0")/../.." || exit 1
S=tools/r239-ai-strips
bad=0
mutant() { # NAME PYTHON-EDIT
	d=$(mktemp -d -p .git r400-mutant.XXXX)
	cp "$S"/layer.py "$S"/layer_test.py "$S"/creature.py tools/parallax_rules.py "$d"/
	python3 - "$d/layer.py" "$2" <<'PY'
import sys
p, edit = sys.argv[1], sys.argv[2]
old, new = edit.split("=>")
s = open(p).read()
assert old in s, old
open(p, "w").write(s.replace(old, new))
PY
	if python3 "$d"/layer_test.py MistyPinesTest >"$d"/out 2>&1; then
		echo "WRONG $1: the tests pass"
		bad=1
	else
		echo "ok    $1: $(grep -c '^FAIL' "$d"/out) test(s) fail, $(grep -c '^ERROR' "$d"/out) error(s)"
		grep -o 'AssertionError: .*' "$d"/out | sed 's/^/      /'
	fi
	rm -rf "$d"
}
mutant "no act" '        act=clear_misty),=>        act=lambda c: 0),'
mutant "any model" ' in MISTY_CKPTS)=> or True)'
mutant "any kind" 'c["kind"] == "pines")=>True)'
mutant "no width test" 'MISTY_CORE, MISTY_MIN = 0.6, 0.85, 2, 300=>MISTY_CORE, MISTY_MIN = 0.6, 0.85, 0, 300'
mutant "no rim (r476)" 'c["misty"] | spread(c["misty"], MISTY_CORE) & (before < 0.5)=>c["misty"]'
exit $bad
