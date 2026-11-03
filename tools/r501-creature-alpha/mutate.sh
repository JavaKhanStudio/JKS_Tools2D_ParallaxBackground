#!/usr/bin/env bash
# headless: numpy on PNGs, no window
# mutate.sh — r501: creature_test.py's CutAlpha fails when the alpha is stored or read another way. Control first.
set -u
cd "$(dirname "$0")/../.." || exit 1
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
D="$T/tools/r239-ai-strips"
mkdir -p "$T/tools" && cp tools/parallax_rules.py "$T/tools/"
bad=0
run() { # NAME [OLD NEW]: 0 when CutAlpha passes on the copy
	rm -rf "$D" && cp -r tools/r239-ai-strips "$D"
	if [ $# -gt 1 ]; then
		python3 - "$D" "$2" "$3" <<'PY' || { echo "$1: no such line"; exit 2; }
import sys
d, old, new = sys.argv[1:]
hit = False
for f in ("layer.py", "creature.py"):
    s = open(f"{d}/{f}").read()
    if old in s:
        hit = True
        open(f"{d}/{f}", "w").write(s.replace(old, new))
assert hit, old
PY
	fi
	PYTHONDONTWRITEBYTECODE=1 python3 "$D/creature_test.py" CutAlpha >"$T/log" 2>&1
}
run control || { echo "control: fails unmutated"; tail -20 "$T/log"; exit 2; }
echo "control  passes"
mutant() {
	if run "$@"; then
		echo "SURVIVED $1"
		bad=1
	else
		echo "killed   $1"
	fi
}
mutant "read as v / 255" "(v + 0.5) / 255)" "v / 255)"
mutant "read past 1" "np.where((v == 0) | (v == 255), v / 255, (v + 0.5) / 255)" "(v + 0.5) / 255"
mutant "under a half stored as 127" "v[(v == 127) & (alpha < 0.5)] = 126" "pass"
mutant "the command line reads v / 255" "read_alpha(cut.getchannel(\"A\").resize(src.size, Image.NEAREST))" \
	"np.asarray(cut.getchannel(\"A\").resize(src.size, Image.NEAREST)) / 255"
[ $bad = 0 ] && echo "mutate: every mutant killed" || echo "mutate: mutants survived, above"
exit $bad
