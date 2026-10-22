#!/usr/bin/env bash
# r277, r347: proves the planet round (engines/rounds/planet) sees a wrong planet, in Godot's copy (plax_planet.gd,
# plax_background.gd) and in core's law (PlanetBatch, ParallaxPageReader.planetInView), which libGDX draws and jME runs
# as is. Puts in one mutant at a time, runs engines/godot/tools/frame-check.sh engines/rounds/planet for each, and fails
# unless every run FAILS. Godot: no planet; a leaning-less copy (each point only lowered by the ground's drop under it,
# not wrapped along the radius); the angle from the view's left, not its middle; the incoming page round the outgoing
# page's planet; a dollied layer's radius not scaled to its view (p06); a hook not turned (planet_angle_at 0) and not
# placed (planet_place_at as is: p08). Core: leaning-less; the dolly's radius not scaled; a hook not turned. Godot's
# particle nodes not turned, per LAYER tile and for a VIEW effect, and placed but not rotated (engines/rounds/particles,
# q09 and q10, whose particles.gd works the turn out apart: libGDX's is held by core's PlanetCases instead).
# Restores the files on exit. Writes build/r277/mutant.log, and each mutant's report in build/r277/mutants/.
# headless: engines/godot/tools/frame-check.sh renders in cage.
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
	local round=engines/rounds/${ROUND:-planet}
	n=$((n + 1))
	FROM="$2" TO="$3" python3 - "$file" <<'PY' || { echo "mutation did not apply: $1"; exit 2; }
import os, sys
path = sys.argv[1]
text = open(path).read()
if text.count(os.environ['FROM']) != 1:
    sys.exit(1)
open(path, 'w').write(text.replace(os.environ['FROM'], os.environ['TO']))
PY
	if engines/godot/tools/frame-check.sh "$round" >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes the $(basename "$round") round ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1))"; status=1
	elif [ "$(basename "$round")" = particles ]; then
		# Not pixels: particles.gd's FAIL lines, by scene.
		failed=$(grep -o '^FAIL q[0-9]*' "build/godot/.mutant/particles/godot.log" | sort -u | cut -c6- | tr '\n' ' ')
		echo "caught: $1 (particles.gd fails ${failed:-nothing, see the log})"
	else
		# Which stills it fails: the report's lines over the threshold.
		failed=$(awk '/\.png  mean diff/ && $4 > 2 {printf "%s ", $1}' "$LOG")
		echo "caught: $1 ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1); over 2: ${failed:-none, see the log})"
	fi
	cp "$LOG" "build/r277/mutants/$n.log"
	restore
}
mutant "$PLANET" "Godot: no planet, every layer drawn flat" \
	'return r * z / zoom if r > 0 else 0.0' \
	'return 0.0'
mutant "$PLANET" "Godot: leaning-less, each point only lowered by the ground's drop under it" \
	'return Vector2(view_w / 2 + (radius + y) * sin(a), -radius + (radius + y) * cos(a))' \
	'return Vector2(x, y - radius * (1 - cos(a)))'
mutant "$PLANET" "Godot: the angle from the view's left, not its middle" \
	'var a := (x - view_w / 2) / radius' \
	'var a := x / radius'
mutant "$BG" "Godot: the incoming page round the outgoing page's planet" \
	'var from_page := transfer_page if l.incoming else page' \
	'var from_page := page'
mutant "$PLANET" "Godot: a dollied layer's radius not scaled to its view" \
	'return r * z / zoom if r > 0 else 0.0' \
	'return r if r > 0 else 0.0'
mutant "$BG" "Godot: a hook not turned (planet_angle_at 0)" \
	'return PlaxPlanet.angle_at(_planet_of(l), _view_w, x / _ppu)' \
	'return 0.0 * PlaxPlanet.angle_at(_planet_of(l), _view_w, x / _ppu)'
mutant "$BG" "Godot: a hook not placed (planet_place_at as is)" \
	'	if not radius > 0:
		return at
	var to := PlaxPlanet.place' \
	'	if true:
		return at
	var to := PlaxPlanet.place'
mutant "$BATCH" "core: leaning-less, each point only lowered by the ground's drop under it" \
	'{return middle + (radius + y - ground) * (float) Math.sin((x - middle) / radius);}' \
	'{return x;}'
mutant "$READER" "core: a dollied layer's radius not scaled to its view" \
	'return radius > 0 ? radius * viewWidth / (viewportWidth * cameraZoom) : 0;' \
	'return radius > 0 ? radius : 0;'
mutant "$READER" "core: a hook not turned (planetAngleAt 0)" \
	'return radius > 0 ? -(x - (viewLeft + viewWidth / 2)) / radius * MathUtils.radiansToDegrees : 0;' \
	'return 0;'
ROUND=particles mutant "$PARTICLES" "Godot: a LAYER tile's particle node not turned" \
	'node.position = _turned(l, Vector2(x, y), middle)' \
	'node.position = Vector2(x * ppu, _bg._screen_h - y * ppu)'
ROUND=particles mutant "$PARTICLES" "Godot: a VIEW effect's particle node not turned" \
	'node.position = _turned(l, Vector2(anchor.x + l.travel_x, anchor.y + l.travel_y), middle)' \
	'node.position = Vector2((anchor.x + l.travel_x) * ppu, _bg._screen_h - (anchor.y + l.travel_y) * ppu)'
ROUND=particles mutant "$PARTICLES" "Godot: particle nodes placed round the planet but not rotated" \
	'* ppu, -(at.y - middle.y) * ppu).rotated(turn)' \
	'* ppu, -(at.y - middle.y) * ppu).rotated(0.0)'
exit $status
