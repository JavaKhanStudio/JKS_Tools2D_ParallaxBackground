#!/usr/bin/env bash
# headless: numpy on PNGs, no window
# mutate.sh [FIXTURES] — r261: each of creature.py's rules earns its place. Takes one out of a copy of
# tools/r239-ai-strips at a time and runs sweep.sh on it, which must then give a wrong answer (a strip flagged, or a
# planted creature missed). A mutation the sweep still passes is a rule nothing proves: exits 1.
set -u
cd "$(dirname "$0")/../.." || exit 1
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
# The copy keeps tools/'s layout: layer.py imports tools/parallax_rules.py from its parent (r400). Without it every
# mutant failed on an ImportError and was "killed" (r438).
D="$T/tools/r239-ai-strips"
mkdir -p "$T/tools" && cp tools/parallax_rules.py "$T/tools/"
mutate() { # NAME PYTHON-REPLACE-OLD PYTHON-REPLACE-NEW: each in a copy of its own, JOBS at a time (r501: a sweep is minutes)
	while [ "$(jobs -r | wc -l)" -ge "${JOBS:-6}" ]; do wait -n; done
	(
		M=$(mktemp -d -p "$T")
		mkdir -p "$M/tools" && cp tools/parallax_rules.py "$M/tools/"
		cp -r tools/r239-ai-strips "$M/tools/"
		python3 - "$M/tools/r239-ai-strips/creature.py" "$2" "$3" <<'PY' || { echo "$1: no such line" | tee -a "$T/bad"; exit 2; }
import sys
p, old, new = sys.argv[1:]
s = open(p).read()
assert old in s, old
open(p, "w").write(s.replace(old, new))
PY
		R239_DIR="$M/tools/r239-ai-strips" tools/r261-creature/sweep.sh "$B" >"$M/log" 2>&1
		rc=$?
		[ -n "${KEEP:-}" ] && cp "$M/log" "$KEEP/$(echo "$1" | tr -c 'a-zA-Z0-9\n' _).log" # for tools/r505-sweep-fixtures/matrix.py
		if [ $rc = 0 ]; then
			echo "SURVIVED $1: no strip proves this rule" | tee -a "$T/bad"
		else
			echo "killed   $1: $(grep -c "  WRONG" "$M/log") wrong answer(s)"
		fi
		rm -rf "$M"
	) &
}
B="${1:-tools/r261-creature/fixtures}"
# The control: the copy unmutated must give every answer right, or a kill below proves nothing.
rm -rf "$D" && cp -r tools/r239-ai-strips "$D"
R239_DIR="$D" tools/r261-creature/sweep.sh "$B" >"$T/log" 2>&1 ||
	{ echo "control: the unmutated copy gives wrong answers:"; grep -B1 WRONG "$T/log" | head; exit 2; }
[ -n "${KEEP:-}" ] && cp "$T/log" "$KEEP/control.log"
mutate "one pass (the blob in its own mean)" \
	"out = standing_out(rgb, solid, (solid & ~spread(out, 3))" "out = out & standing_out(rgb, solid, solid"
mutate "no crowd rule" "if like_it > crowd:" "if False:"
mutate "crowd by colour only" "and 1 / 3 < t / tall < 3 and 1 / 3 < n / size < 3" ""
mutate "crowd without size" " and 1 / 3 < n / size < 3" ""
mutate "no edge rule" "if k in edge or not" "if not"
mutate "solid only (alpha >= 0.95)" "solid = spread(alpha >= 0.5, -4)" "solid = spread(alpha >= 0.95, -4)"
# r438's shading rule
mutate "no shading rule" "if not (shading(rgb, solid, blob, shade, hue) or" "if not (False or"
mutate "shading: any luminance" "return shade < dark < 1 / shade and off < hue" "return off < hue"
mutate "shading: any hue" "return shade < dark < 1 / shade and off < hue" "return shade < dark < 1 / shade"
# r349's ridge check
mutate "no ridge check" "found += on_ridge(rgb, alpha, sky," "found += 0 * on_ridge(rgb, alpha, sky,"
mutate "ridge on the mass's edge too" "above = spread(mass, 2 * r) & ~spread(mass, -1)" "above = spread(mass, 2 * r) & ~solid"
mutate "ridge: far from the sky only" "off = np.linalg.norm(rgb - (sky + t[..., None] * seg), axis=2)" \
	"off = np.linalg.norm(rgb - sky, axis=2)"
mutate "ridge: far from the mass only" "off = np.linalg.norm(rgb - (sky + t[..., None] * seg), axis=2)" \
	"off = np.linalg.norm(rgb - land, axis=2)"
# r362's neck check
mutate "no neck check" "found += on_neck(rgb, alpha, sky, r)" "found += 0 * on_neck(rgb, alpha, sky, r)"
mutate "neck: the cut's outline only" "m = mass | (spread(mass, 4 * r) & (t > 0.5))" "m = mass"
mutate "neck: far towers in the outline" "& (t > 0.5))" "& (t > 0.1))"
mutate "neck: the outline far from the mass" "spread(mass, 4 * r) & (t" "spread(mass, 12 * r) & (t"
mutate "neck: any head size" "if size < (2 * n + 1) ** 2 // 2 or" "if size < 8 or"
mutate "neck: any head proportions" "if not 0.6 < h / w < 1.8 or" "if"
mutate "neck: any head shape" " or size < 0.6 * w * h:" ":"
mutate "neck: no sky over it" "].mean() >= cap:" "].mean() >= 2:"
mutate "neck: straight down" "x = (a + b) // 2  # a neck leans" "pass  # a neck leans"
mutate "neck: through the sky" "if not m[y, x]:
                    break" "pass"
mutate "neck: no shoulders" "if b - a + 1 >= 0.8 * w:" "if True:"
wait
if [ -e "$T/bad" ]; then
	echo "mutate: rules no strip proves, above"
	exit 1
fi
echo "mutate: every rule killed"
