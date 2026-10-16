#!/usr/bin/env bash
# headless: numpy on arrays, no window
# mutate.sh — r454: layer_test.py must fail on layer.py with unglow's inside pass taken out (a line along sil.py's core
# inside a pale peak), or wide_mass' act, its model condition or its whole pieces (the hill's part inside --grow,
# cleared by unglow) taken out.
set -u
cd "$(dirname "$0")/../.." || exit 1
S=tools/r239-ai-strips
bad=0
mutant() { # NAME PYTHON-EDIT
	d=$(mktemp -d -p .git r454-mutant.XXXX)
	cp "$S"/layer.py "$S"/layer_test.py "$S"/creature.py tools/parallax_rules.py "$d"/
	python3 - "$d/layer.py" "$2" <<'PY'
import sys
p, edit = sys.argv[1], sys.argv[2]
old, new = edit.split("=>")
s = open(p).read()
assert old in s, old
open(p, "w").write(s.replace(old, new))
PY
	python3 "$d"/layer_test.py >"$d"/out 2>&1
	n=$(grep -c '^FAIL:' "$d"/out)
	if [ "$n" = 0 ]; then
		echo "WRONG $1: no test fails"
		tail -3 "$d"/out | sed 's/^/      /'
		bad=1
	else
		echo "ok    $1: $n test(s) fail"
		grep -o 'AssertionError: .*' "$d"/out | sed 's/^/      /'
	fi
	rm -rf "$d"
}
mutant "no inside pass" '    inside = ~far & (alpha < 0.95) & ~spread(out < 0.5, 3 * edge)=>    inside = far & False'
mutant "no act" '        act=keep_wide),=>        act=lambda c: 0),'
mutant "any model" '.startswith(WIDE_CKPTS)),=>.startswith(WIDE_CKPTS) or True),'
mutant "past --grow only" '    return past, np.isin(label, on[on > 0])=>    return past, past'
exit $bad
