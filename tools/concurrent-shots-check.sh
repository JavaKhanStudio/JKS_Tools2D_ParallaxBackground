#!/usr/bin/env bash
# concurrent-shots-check.sh [godot|jme] [ROUND_DIR] — two frame checks of the same round at once in this checkout (r172).
#
#   tools/concurrent-shots-check.sh godot engines/godot/tests/conformance
#
# The board's sessions share one checkout. Runs tools/<engine>-parallax-shots.sh on ROUND twice together; passes when
# both PASS, each wrote its own build/<engine>/.runs/<round>-<pid> (tools/shots-out.sh), and both reports are the same.
set -u
cd "$(dirname "$0")/.." || exit 1
ENGINE="${1:-godot}" ROUND="${2:-engines/godot/tests/conformance}"
LOG=build/concurrent-shots; mkdir -p "$LOG"
tools/"$ENGINE"-parallax-shots.sh "$ROUND" >"$LOG/a.out" 2>&1 & A=$!
tools/"$ENGINE"-parallax-shots.sh "$ROUND" >"$LOG/b.out" 2>&1 & B=$!
wait $A; a=$?; wait $B; b=$?
ra="build/$ENGINE/.runs/$(basename "$ROUND")-$A/report.txt" rb="build/$ENGINE/.runs/$(basename "$ROUND")-$B/report.txt"
echo "run $A: exit $a, $(tail -1 "$ra" 2>/dev/null || echo 'no report')"
echo "run $B: exit $b, $(tail -1 "$rb" 2>/dev/null || echo 'no report')"
[ $a = 0 ] && [ $b = 0 ] && cmp -s "$ra" "$rb" && { echo "OK: both passed, same report"; exit 0; }
echo "FAIL: see $LOG/a.out, $LOG/b.out"; exit 1
