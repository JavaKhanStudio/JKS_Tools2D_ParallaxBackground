#!/usr/bin/env bash
# headless: numpy on arrays, no window
# mutate.sh — r401: layer_test.py must fail on layer.py with each half of far_range taken out: the rule off (the range
# keyed in blobs), the islands cut without it (a mesa standing in the range goes), its edge keyed against the top rows'
# sky (a sky warming beside it glows), no outline test (that sky taken for a range), no thickness test (r358's haze ring
# taken for one). Then r382's and r358's mutants, which must still fail it.
set -u
cd "$(dirname "$0")/../.." || exit 1
S=tools/r239-ai-strips
bad=0
mutant() { # NAME PYTHON-EDIT
	d=$(mktemp -d -p .git r401-mutant.XXXX)
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
mutant "no far range" '    if m is not None and a.gap and haze:=>    if False:'
mutant "islands without it" 'cut = islands((alpha > 0.5) | ranged, =>cut = islands((alpha > 0.5), '
mutant "edge on the top rows' sky" '    t = np.where(body, t, t - t0)=>    t = t'
mutant "no outline test" 'np.median(step[rim]) > FAR_STEP=>True'
mutant "no thickness test" 'core.sum() >= FAR_MIN * k * k and=>near.sum() > 0 and'
tools/r382-pale-foot/mutate.sh || bad=1
exit $bad
