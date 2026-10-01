#!/usr/bin/env bash
# offscreen-lint-test.sh — proves tools/offscreen-lint.sh (r171) on today's scripts and on broken copies of them.
# Exit 0 when every case gives the expected verdict.
set -uo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd) || exit 2
cd "$ROOT" || exit 2
LINT=tools/offscreen-lint.sh
WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
status=0

# expect pass|fail NAME SCRIPT [TEXT]: the lint's verdict on SCRIPT, and on a fail that its report names SCRIPT and TEXT.
expect() {
	local want=$1 name=$2 script=$3 text=${4:-}
	out=$("$LINT" "$script" 2>&1)
	code=$?
	if [ "$want" = pass ] && [ $code = 0 ]; then echo "ok    $name"; return; fi
	if [ "$want" = fail ] && [ $code = 1 ] && grep -qF "$script:" <<<"$out" && grep -qF -- "$text" <<<"$out"; then
		echo "ok    $name"; return
	fi
	echo "WRONG $name (wanted $want, exit $code)"; echo "$out" | sed 's/^/      /'; status=1
}

out=$("$LINT"); code=$?
if [ $code = 0 ]; then echo "ok    today's tools/*.sh pass"; else echo "WRONG today's tools/*.sh"; echo "$out"; status=1; fi

# parallax-lab-shots.sh with its cage line made a plain run: ParallaxShots would open on the screen.
grep -q 'cage -- tools/nested-x.sh' tools/parallax-lab-shots.sh || { echo "parallax-lab-shots.sh changed: fix this test" >&2; exit 2; }
sed 's/WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 300 cage -- tools\/nested-x.sh 1280x1280 bash -c "\$RUN"/bash -c "$RUN"/' \
	tools/parallax-lab-shots.sh >"$WORK/lab-shots-plain.sh"
expect fail "parallax-lab-shots.sh without cage" "$WORK/lab-shots-plain.sh" "runs java"

{ head -1 "$WORK/lab-shots-plain.sh"; echo "# on-screen: a test of the marker"; tail -n +2 "$WORK/lab-shots-plain.sh"; } \
	>"$WORK/lab-shots-marked.sh"
expect pass "the same, with the on-screen marker" "$WORK/lab-shots-marked.sh"

# A nested X server on a fixed display, as the shot scripts had before r142: fails even inside cage. (The server's name
# is a printf argument, or this script would be the lint's first catch.)
printf '#!/usr/bin/env bash\nWLR_BACKENDS=headless cage -- bash -c "%s :9 & DISPLAY=:9 shots/build/install/shots/bin/shots"\n' \
	Xwayland >"$WORK/fixed-display.sh"
expect fail "a fixed display number inside cage" "$WORK/fixed-display.sh" "fixed display"

printf '#!/usr/bin/env bash\nGODOT="${GODOT:-godot}"\n"$GODOT" --headless --path engines/godot --import\n' >"$WORK/godot-import.sh"
expect pass "godot --headless" "$WORK/godot-import.sh"
printf '#!/usr/bin/env bash\ngodot --path engines/godot\n' >"$WORK/godot-window.sh"
expect fail "godot with a window" "$WORK/godot-window.sh" "runs godot"
printf '#!/usr/bin/env bash\njava -cp "$CP" jks.tools2d.parallax.shots.ParallaxShots r out\n' >"$WORK/java-window.sh"
expect fail "java -cp outside cage" "$WORK/java-window.sh" "runs java"

# A source launch of a program that opens no window (r178-particle-stress/run.sh's ToPlax, r197): `# headless: <why>`
# above it passes it, and only it: a window opened after the next blank line still fails.
printf '#!/usr/bin/env bash\n# headless: converts a file\njava -cp "$CP" ToPlax.java a b\njava -cp "$CP" ToPlax.java c d\n' \
	>"$WORK/java-no-window.sh"
expect pass "java -cp under a headless marker" "$WORK/java-no-window.sh"
{ cat "$WORK/java-no-window.sh"; printf '\njava -cp "$CP" jks.tools2d.parallax.shots.ParallaxShots r out\n'; } \
	>"$WORK/java-window-after-marker.sh"
expect fail "a window after the headless marker's block" "$WORK/java-window-after-marker.sh" "ParallaxShots"

# A script that hands its window to a tools/*.sh that runs cage (the editor repository's r74-probe-race.sh does,
# through its driver-probe.sh).
printf '#!/usr/bin/env bash\nBIN=shots/build/install/shots/bin/shots\nSHOTS_BIN="$BIN" "$ROOT/tools/parallax-lab-shots.sh" r out\n' \
	>"$WORK/through-probe.sh"
expect pass "a window handed to parallax-lab-shots.sh" "$WORK/through-probe.sh"
printf '#!/usr/bin/env bash\ntools/offscreen.sh shots/build/install/shots/bin/shots\n' >"$WORK/through-offscreen.sh"
expect pass "a window through tools/offscreen.sh" "$WORK/through-offscreen.sh"

[ $status = 0 ] && echo PASS || echo FAIL
exit $status
