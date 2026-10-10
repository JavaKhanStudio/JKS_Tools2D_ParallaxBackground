#!/usr/bin/env bash
# headless: numpy on PNGs, no window
# mutate.sh [BUILD] — r261: each of creature.py's rules earns its place. Takes one out of a copy of
# tools/r239-ai-strips at a time and runs sweep.sh on it, which must then give a wrong answer (a strip flagged, or a
# planted creature missed). A mutation the sweep still passes is a rule nothing proves: exits 1.
set -u
cd "$(dirname "$0")/../.."
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
bad=0
mutate() { # NAME PYTHON-REPLACE-OLD PYTHON-REPLACE-NEW
	rm -rf "$T/d" && cp -r tools/r239-ai-strips "$T/d"
	python3 - "$T/d/creature.py" "$2" "$3" <<'PY' || { echo "$1: no such line"; exit 2; }
import sys
p, old, new = sys.argv[1:]
s = open(p).read()
assert old in s, old
open(p, "w").write(s.replace(old, new))
PY
	if R239_DIR="$T/d" tools/r261-creature/sweep.sh "$B" >"$T/log" 2>&1; then
		echo "SURVIVED $1: no strip proves this rule"
		bad=1
	else
		echo "killed   $1: $(grep -c WRONG "$T/log") wrong answer(s)"
	fi
}
B="${1:-build}"
mutate "one pass (the blob in its own mean)" \
	"out = standing_out(rgb, solid, (solid & ~spread(out, 3))" "out = out & standing_out(rgb, solid, solid"
mutate "no crowd rule" "if like_it <= crowd:" "if True:"
mutate "crowd by colour only" "and 1 / 3 < t / tall < 3 and 1 / 3 < n / size < 3" ""
mutate "crowd without size" " and 1 / 3 < n / size < 3" ""
mutate "no edge rule" "if k in edge or not" "if not"
mutate "solid only (alpha >= 0.95)" "solid = spread(alpha >= 0.5, -4)" "solid = spread(alpha >= 0.95, -4)"
[ $bad = 0 ] && echo "mutate: every rule killed" || echo "mutate: rules no strip proves, above"
exit $bad
