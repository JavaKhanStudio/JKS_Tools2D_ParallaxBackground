#!/usr/bin/env bash
# mutate.sh — breaks Godot's SEQUENCE port (r183) one way at a time and runs the sequence round
# (tools/godot-parallax-shots.sh engines/godot/tests/sequence) on each: every mutation must FAIL, the unmutated port first
# must PASS. The files are restored on exit, whatever happens.
set -u
cd "$(dirname "$0")/../.." || exit 1
BG=engines/godot/addons/jks_parallax/plax_background.gd
PAGE=engines/godot/addons/jks_parallax/plax_page.gd
SAVE=$(mktemp -d)
cp "$BG" "$PAGE" "$SAVE/"
restore() { cp "$SAVE/plax_background.gd" "$BG"; cp "$SAVE/plax_page.gd" "$PAGE"; rm -rf "$SAVE"; }
trap restore EXIT INT TERM PIPE
# "ok" or "broken", then the frames' verdict and sequence_cycle.gd's: either failing fails the script.
run() {
	local out code
	out=$(tools/godot-parallax-shots.sh engines/godot/tests/sequence 2>&1); code=$?
	echo "$([ $code = 0 ] && echo ok || echo broken) | $(echo "$out" | grep -E "^(PASS|FAIL): worst" | tail -1) | $(echo "$out" | grep -c "^FAIL") FAIL lines"
}

base=$(run)
echo "baseline: $base"
case "$base" in ok*) ;; *) echo "the unmutated port does not pass: stopping"; exit 1 ;; esac
status=0
while IFS='|' read -r file what from to; do
	[ -z "$file" ] && continue
	restore_one() { cp "$SAVE/$(basename "$file")" "$file"; }
	restore_one
	python3 - "$file" "$from" "$to" <<'PY' || { echo "NOT APPLIED: $what"; status=1; continue; }
import sys
path, old, new = sys.argv[1:]
text = open(path).read()
if text.count(old) != 1:
    sys.exit(1)
open(path, 'w').write(text.replace(old, new))
PY
	result=$(run)
	echo "$what: $result"
	case "$result" in broken*) ;; *) status=1 ;; esac
	restore_one
done <<'MUTANTS'
engines/godot/addons/jks_parallax/plax_page.gd|seed off by one|var state := seed & _U32|var state := (seed + 1) & _U32
engines/godot/addons/jks_parallax/plax_page.gd|pick from the state, not its top 31 bits|var pick := (state >> 1) %|var pick := state %
engines/godot/addons/jks_parallax/plax_background.gd|game seed not XORed with the layer's|(_sequence_seed ^ l.model.sequenceSeed if|(_sequence_seed if
engines/godot/addons/jks_parallax/plax_background.gd|slots without their pads|var left: float = x + height * edges[slot] + slot * pad|var left: float = x + height * edges[slot]
engines/godot/addons/jks_parallax/plax_background.gd|segments not flipped|_draw_region(segment, left, y, width, height, fx, fy)|_draw_region(segment, left, y, width, height, false, false)
engines/godot/addons/jks_parallax/plax_background.gd|first slot in view skipped|for slot in range(first, slots):|for slot in range(first + 1, slots):
engines/godot/addons/jks_parallax/plax_background.gd|cycle width without its pads|l.width = l.height * l.edges[l.cycle.size()] + (l.cycle.size() - 1) * l.model.padX|l.width = l.height * l.edges[l.cycle.size()]
engines/godot/addons/jks_parallax/plax_background.gd|segments ignore useOriginalSize|segments.append(_measure_region(region, from_page.use_original_size))|segments.append(_measure_region(region, false))
engines/godot/addons/jks_parallax/plax_background.gd|overlapping slots searched as if ordered|var ordered: bool = height * l.narrowest + pad > 0|var ordered: bool = true
engines/godot/addons/jks_parallax/plax_background.gd|search on a slot's left edge, not its right|if x + height * edges[middle + 1] + middle * pad > from_x:|if x + height * edges[middle] + middle * pad > from_x:
engines/godot/addons/jks_parallax/plax_background.gd|walk stops a slot late|		if left >= to_x:|		if left > to_x + 1:
engines/godot/addons/jks_parallax/plax_page.gd|negative weights not floored|var weight := maxi(weights[segment], 0) if total > 0 else 1|var weight := weights[segment] if total > 0 else 1
MUTANTS
exit $status
