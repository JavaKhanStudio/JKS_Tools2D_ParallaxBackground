#!/usr/bin/env bash
# r277: proves the planet round (engines/godot/tests/planet) sees a wrong bend, in Godot's copy (plax_planet.gd,
# plax_background.gd) and in core's law (ParallaxPageReader.planetBendOf, PlanetBatch), which libGDX draws and jME runs
# as is. Puts in one mutant at a time, runs tools/godot-parallax-shots.sh engines/godot/tests/planet for each, and fails
# unless every run FAILS. Godot: no bend; every layer bent as the front (no depth); dx from the view's left, not its
# middle; the incoming page bent by the outgoing page's planet. Core: dx from the view's left; every layer bent as the
# front. r330's: a hook handed no drop (Godot's planet_drop_at, core's planetDropAt: the planet round's p08), and
# Godot's particle nodes left flat, per LAYER tile and for a VIEW effect (engines/godot/tests/particles, q09 and q10,
# whose particles.gd works the drop out apart: libGDX's particle drop is held by core's PlanetCases instead).
# Restores the files on exit. Writes build/r277/mutant.log, and each mutant's report in build/r277/mutants/.
# headless: tools/godot-parallax-shots.sh renders in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
PLANET=engines/godot/addons/jks_parallax/plax_planet.gd
BG=engines/godot/addons/jks_parallax/plax_background.gd
READER=core/src/jks/tools2d/parallax/ParallaxPageReader.java
BATCH=core/src/jks/tools2d/parallax/PlanetBatch.java
PARTICLES=engines/godot/addons/jks_parallax/plax_particles.gd
SAVED=$(mktemp -d)
cp "$PLANET" "$BG" "$READER" "$BATCH" "$PARTICLES" "$SAVED"
restore() {
	cp "$SAVED/plax_planet.gd" "$PLANET"; cp "$SAVED/plax_background.gd" "$BG"
	cp "$SAVED/ParallaxPageReader.java" "$READER"; cp "$SAVED/PlanetBatch.java" "$BATCH"
	cp "$SAVED/plax_particles.gd" "$PARTICLES"
}
trap 'restore; rm -rf "$SAVED"' EXIT
LOG=build/r277/mutant.log
mkdir -p build/r277/mutants
status=0
n=0
mutant() {
	# $1 the file, $2 what it is, $3 the text as it is, $4 the text mutated; ROUND the round it runs (planet)
	local file=$1; shift
	local round=engines/godot/tests/${ROUND:-planet}
	n=$((n + 1))
	FROM="$2" TO="$3" python3 - "$file" <<'PY' || { echo "mutation did not apply: $1"; exit 2; }
import os, sys
path = sys.argv[1]
text = open(path).read()
if text.count(os.environ['FROM']) != 1:
    sys.exit(1)
open(path, 'w').write(text.replace(os.environ['FROM'], os.environ['TO']))
PY
	if tools/godot-parallax-shots.sh "$round" >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes the $(basename "$round") round ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1))"; status=1
	elif [ "$(basename "$round")" = particles ]; then
		# Not pixels: particles.gd's FAIL lines, by scene.
		failed=$(grep -o '^FAIL q[0-9]*' "build/godot/particles/godot.log" | sort -u | cut -c6- | tr '\n' ' ')
		echo "caught: $1 (particles.gd fails ${failed:-nothing, see the log})"
	else
		# Which stills it fails: the report's lines over the threshold.
		failed=$(awk '/\.png  mean diff/ && $4 > 2 {printf "%s ", $1}' "$LOG")
		echo "caught: $1 ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1); over 2: ${failed:-none, see the log})"
	fi
	cp "$LOG" "build/r277/mutants/$n.log"
	restore
}
mutant "$PLANET" "Godot: no bend, every layer drawn flat" \
	'return front / speed / (2 * r)' \
	'return 0.0'
mutant "$PLANET" "Godot: every layer bent as the front, not by its depth" \
	'return front / speed / (2 * r)' \
	'return 1.0 / (2 * r)'
mutant "$PLANET" "Godot: dx from the view's left, not its middle" \
	'var dx := (strip + 0.5 - STRIPS / 2.0) * (view_w / STRIPS)' \
	'var dx := (strip + 0.5) * (view_w / STRIPS)'
# Two tabs in: _draw_layer's, not planet_drop_at's.
mutant "$BG" "Godot: the incoming page bent by the outgoing page's planet" \
	$'\t\tvar from_page := transfer_page if l.incoming else page' \
	$'\t\tvar from_page := page'
mutant "$BATCH" "core: dx from the view's left, not its middle" \
	'float dx = (strip + 0.5f - STRIPS / 2f) * (viewWidth / STRIPS);' \
	'float dx = (strip + 0.5f) * (viewWidth / STRIPS);'
mutant "$READER" "core: every layer bent as the front, not by its depth" \
	'return front / speed / (2 * r);' \
	'return 1 / (2 * r);'
mutant "$BG" "Godot: a hook handed no drop (planet_drop_at 0)" \
	'return bend * dx * dx * _ppu' \
	'return 0.0 * bend * dx * dx * _ppu'
mutant "$READER" "core: a hook handed no drop (planetDropAt 0)" \
	'		return bend * dx * dx;
	}' \
	'		return 0 * bend * dx * dx;
	}'
ROUND=particles mutant "$PARTICLES" "Godot: a LAYER tile's particle node left flat" \
	'_bg._screen_h - y * ppu + _bg.planet_drop_at(l, (x + width / 2) * ppu)' \
	'_bg._screen_h - y * ppu'
ROUND=particles mutant "$PARTICLES" "Godot: a VIEW effect's particle node left flat" \
	'var drop := _bg.planet_drop_at(l, (anchor.x + decal_x + l.width / 2) * ppu)' \
	'var drop := 0.0'
exit $status
