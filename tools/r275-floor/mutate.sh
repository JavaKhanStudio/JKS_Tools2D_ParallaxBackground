#!/usr/bin/env bash
# r275: proves the floor round sees a wrong copy of the floor in Godot's plax_background.gd and plax_floor.gd (its rows,
# measured: r307), the one engine that ports it (jME runs core's ParallaxLayer.drawFloor as is, through JmeBatch's draw(region, x, y, width, height)). Puts in one
# mutant at a time, runs tools/godot-parallax-shots.sh engines/godot/tests/floor for each, and fails unless every run
# FAILS: every row at the bottom row's speed; no layer planted (each at its page speed); no row stretched (mode 7 as
# line scroll's widths); mode 7's strips as line scroll's (the image's rows not spaced by depth). Restores the files on
# exit. Writes build/r275/mutant.log.
# headless: tools/godot-parallax-shots.sh renders in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
ADDON=engines/godot/addons/jks_parallax
BG=$ADDON/plax_background.gd
FLOOR=$ADDON/plax_floor.gd
SAVED=$(mktemp -d)
cp "$BG" "$FLOOR" "$SAVED"
restore() { cp "$SAVED/plax_background.gd" "$BG"; cp "$SAVED/plax_floor.gd" "$FLOOR"; }
trap 'restore; rm -rf "$SAVED"' EXIT
LOG=build/r275/mutant.log
mkdir -p build/r275
status=0
mutant() {
	# $1 the file, $2 what it is, $3 the line as it is, $4 the line mutated
	local file=$1; shift
	FROM="$2" TO="$3" python3 - "$file" <<'PY' || { echo "mutation did not apply: $1"; exit 2; }
import os, sys
path = sys.argv[1]
text = open(path).read()
if text.count(os.environ['FROM']) != 1:
    sys.exit(1)
open(path, 'w').write(text.replace(os.environ['FROM'], os.environ['TO']))
PY
	if tools/godot-parallax-shots.sh engines/godot/tests/floor >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes the floor round ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1))"; status=1
	else
		echo "caught: $1 ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1))"
	fi
	restore
}
mutant "$BG" "every row at the bottom row's speed" \
	'var distance: float = l.row_distance[i] - delta * (m.speedXAtRest + speed_x) * l.row_speed[i]' \
	'var distance: float = l.row_distance[i] - delta * (m.speedXAtRest + speed_x) * m.parallaxScalingSpeedX'
mutant "$BG" "no layer planted" \
	'page_layers[i].planted = _planted_speed_on(page_layers[floor], page_layers[i]) if floor >= 0 and i > floor else NAN' \
	'page_layers[i].planted = NAN'
mutant "$FLOOR" "no row stretched" \
	'l.row_stretch[i] = maxf(0.02, speed / bottom) if mode7 and bottom != 0 else 1.0' \
	'l.row_stretch[i] = 1.0'
mutant "$FLOOR" "mode 7's strips as line scroll's" \
	'		if recedes:
			from = _along' \
	'		if false:
			from = _along'
exit $status
