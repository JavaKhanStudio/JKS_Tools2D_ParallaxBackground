#!/usr/bin/env bash
# headless: numpy on drawn arrays, no window
# mutate.sh: each of r500's rules earns its place. Takes one out of a copy of tools/r239-ai-strips at a time and runs
# creature_test.py on it, which must then fail; the unmutated copy, run last, must pass, or the kills prove nothing.
set -u
cd "$(dirname "$0")/../.." || exit 1
export PYTHONDONTWRITEBYTECODE=1
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
D="$T/tools/r239-ai-strips"
mkdir -p "$T/tools" && cp tools/parallax_rules.py "$T/tools/"
bad=0
mutate() { # NAME OLD NEW
	rm -rf "$D" && cp -r tools/r239-ai-strips "$D"
	python3 - "$D/creature.py" "$2" "$3" <<'PY' || { echo "$1: no such line"; exit 2; }
import sys
p, old, new = sys.argv[1:]
s = open(p).read()
assert old in s, old
open(p, "w").write(s.replace(old, new))
PY
	if python3 "$D/creature_test.py" >"$T/log" 2>&1; then
		echo "SURVIVED $1"
		bad=1
	else
		echo "killed   $1: $(grep -cE '^(FAIL|ERROR):' "$T/log") test(s)"
	fi
}
mutate "no foot rule" " | set(np.unique(label[-3:]))" ""
mutate "no sky rule" "or sky_through(rgb, solid, blob, sky)" ""
mutate "sky: wide ones too" "if not ring.any() or np.ptp(ys) + 1 < 1.5 * (np.ptp(xs) + 1):" "if not ring.any():"
mutate "sky: any colour" "return t < near and np.linalg.norm" "return np.linalg.norm"
mutate "no runs-on rule" "or runs_on(rgb, solid, blob, far, hi)):" "):"
mutate "runs on: no size" "return sizes[label[blob][0] - 1] > hi" "return True"
mutate "runs on: no ring share" "< share:" "< 0:"
mutate "neck: no cap" " or size > biggest * r * r:" ":"
mutate "neck: cap too low" "biggest=14" "biggest=4"
rm -rf "$D" && cp -r tools/r239-ai-strips "$D"
python3 "$D/creature_test.py" >"$T/log" 2>&1 || { echo "control: the unmutated copy fails"; tail -5 "$T/log"; exit 2; }
echo "control passes"
if [ $bad != 0 ]; then
	echo "mutate: rules nothing proves, above"
	exit 1
fi
echo "mutate: every rule proved"
