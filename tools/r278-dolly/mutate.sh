#!/usr/bin/env bash
# r278: proves the dolly round (engines/godot/tests/dolly) sees a wrong copy of the dolly in Godot's plax_background.gd
# and plax_effects.gd, the one engine that ports it (jME runs core's DollyBatch as is). Puts in one mutant at a time,
# runs tools/godot-parallax-shots.sh engines/godot/tests/dolly for each, and fails unless every run FAILS: every layer
# at the camera's zoom (no depth); layers placed from the zoomed view's corner (scaled about it, not the middle); an
# incoming page's layers by the outgoing page's front; k squared in dolly_zoom; FOG's and WAVE's noise laid from the
# view's corner. Restores the files on exit. Writes build/r278/mutant.log.
# headless: tools/godot-parallax-shots.sh renders in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
ADDON=engines/godot/addons/jks_parallax
BG=$ADDON/plax_background.gd
FX=$ADDON/plax_effects.gd
SAVED=$(mktemp -d)
cp "$BG" "$FX" "$SAVED"
restore() { cp "$SAVED/plax_background.gd" "$BG"; cp "$SAVED/plax_effects.gd" "$FX"; }
trap 'restore; rm -rf "$SAVED"' EXIT
LOG=build/r278/mutant.log
mkdir -p build/r278
status=0
mutant() {
	# $1 the file, $2 what it is, $3 the text as it is, $4 the text mutated
	local file=$1; shift
	FROM="$2" TO="$3" python3 - "$file" <<'PY' || { echo "mutation did not apply: $1"; exit 2; }
import os, sys
path = sys.argv[1]
text = open(path).read()
if text.count(os.environ['FROM']) != 1:
    sys.exit(1)
open(path, 'w').write(text.replace(os.environ['FROM'], os.environ['TO']))
PY
	if tools/godot-parallax-shots.sh engines/godot/tests/dolly >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes the dolly round ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1))"; status=1
	else
		echo "caught: $1 ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1); over 2: $(grep -E 'mean diff ([2-9]|[1-9][0-9])' "$LOG" | grep -oE '^[a-z][0-9]+-t[0-9]+' | tr '\n' ' '))"
	fi
	restore
}
mutant "$BG" "every layer at the camera's zoom" \
	'	return PlaxEffects.dolly_zoom(zoom, PlaxEffects.front_speed_of(transfer_layers if l.incoming else layers), l.model)' \
	'	return zoom'
mutant "$BG" "placed from the zoomed view's corner" \
	'	return Vector2(world_width * (z - 1), _world_height * (z - 1)) / 2' \
	'	return Vector2.ZERO'
mutant "$BG" "an incoming page by the outgoing one's front" \
	'PlaxEffects.front_speed_of(transfer_layers if l.incoming else layers)' \
	'PlaxEffects.front_speed_of(layers)'
mutant "$FX" "k squared" \
	'	return 1.0 - k * (1.0 - zoom)' \
	'	return 1.0 - k * k * (1.0 - zoom)'
mutant "$BG" "the noise laid from the view's corner" \
	'PlaxEffects.apply(l.canvas.material, l, _effect_time, view_w, view_h, anchor.x, anchor.y)' \
	'PlaxEffects.apply(l.canvas.material, l, _effect_time, view_w, view_h)'
exit $status
