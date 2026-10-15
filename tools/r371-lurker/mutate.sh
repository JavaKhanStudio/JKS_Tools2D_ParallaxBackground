#!/usr/bin/env bash
# r371: proves Godot's frame check on the lurker round (engines/rounds/lurker) sees a wrong copy of the sightings in
# plax_lurker.gd, the one engine that ports them (jME runs core's ParallaxPageReader). Puts in one mutant at a time,
# runs engines/godot/tools/frame-check.sh engines/rounds/lurker for each, and fails unless every run FAILS:
#   always-there  the figure drawn whatever its sightings say (Godot before r371)
#   in-sight      the figure decided again on entering sight too: a switch in a gap
#   seen          the seen rule left out: the figure stays after it was seen
#   unmasked      the xorshift step not masked to 32 bits: GDScript's 64-bit ints draw other picks
#   no-cover      the cover ignored: the figure switches only outside the view
# Restores the file on exit. Writes build/r371/<mutant>.log.
# headless: engines/godot/tools/frame-check.sh renders in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
FILE=engines/godot/addons/jks_parallax/plax_lurker.gd
SAVED=$(mktemp -d)
cp "$FILE" "$SAVED"
restore() { cp "$SAVED/plax_lurker.gd" "$FILE"; }
trap 'restore; rm -rf "$SAVED"' EXIT
mkdir -p build/r371
status=0
mutant() {
	# $1 its name, then pairs: the text as it is, the text mutated; every occurrence is replaced, at least one
	python3 - "$FILE" "${@:2}" <<'PY' || { echo "mutation did not apply: $1"; exit 2; }
import sys
path, pairs = sys.argv[1], sys.argv[2:]
text = open(path).read()
for before, after in zip(pairs[::2], pairs[1::2]):
    if before not in text:
        sys.exit(1)
    text = text.replace(before, after)
open(path, 'w').write(text)
PY
	LOG=build/r371/$1.log
	if engines/godot/tools/frame-check.sh engines/rounds/lurker >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes the lurker round ($(grep '^PASS' "$LOG"))"; status=1
	else
		echo "caught: $1 ($(grep -E 'pixels off by >32: 0\.0[2-9]|pixels off by >32: 0\.[1-9]|pixels off by >32: [1-9]' "$LOG" | cut -d' ' -f1 | tr '\n' ' '))"
	fi
	restore
}
mutant always-there \
	'	return not l.get("lurker_started", false) or l.lurker_there' \
	'	return true'
mutant in-sight \
	'		elif not in_sight and figure.lurker_in_sight:' \
	'		elif in_sight != figure.lurker_in_sight:'
mutant seen \
	'	elif figure.lurker_seen:' \
	'	elif false:'
mutant unmasked \
	'PlaxPage.sequence_next(' \
	'_unmasked(' \
	'extends RefCounted
' \
	'extends RefCounted


static func _unmasked(state: int) -> int:
	state ^= state << 13
	state ^= state >> 17
	state ^= state << 5
	return state
'
mutant no-cover \
	'					if not (covers and row == 0 and _covered(bg, page, index, x - anchor.x)):' \
	'					if true:'
exit $status
