#!/usr/bin/env bash
# r276: proves the tower round (engines/rounds/tower) sees a wrong tower, in Godot's copy (plax_tower.gd) and in
# core's law (ParallaxLayer.drawTower, GdxLayerEffects' page fog), which libGDX draws and jME runs as is. Puts in one
# mutant at a time, runs engines/godot/tools/frame-check.sh engines/rounds/tower for each, and fails unless every run
# FAILS. Godot: no wrap (the layer drawn flat); no shade; the strips placed along the arc, not at radius sin(a) (no
# narrowing); the tower turning the wrong way; seen from close by (r350), the courses straight, and the set behind left
# flat. Core: the strips placed along the arc; the page fog's shader ignoring the darkness in the blue; the courses
# straight; the panoramas unscaled (PanoramaBatch). Restores the files on exit. Writes
# build/r276/mutant.log, and each mutant's report in build/r276/mutants/.
# headless: engines/godot/tools/frame-check.sh renders in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
TOWER=engines/godot/addons/jks_parallax/plax_tower.gd
PANORAMA=engines/godot/addons/jks_parallax/plax_panorama.gd
LAYER=core/src/jks/tools2d/parallax/ParallaxLayer.java
EFFECTS=core/src/jks/tools2d/parallax/GdxLayerEffects.java
BATCH=core/src/jks/tools2d/parallax/PanoramaBatch.java
SAVED=$(mktemp -d)
cp "$TOWER" "$PANORAMA" "$LAYER" "$EFFECTS" "$BATCH" "$SAVED"
restore() {
	cp "$SAVED/plax_tower.gd" "$TOWER"; cp "$SAVED/plax_panorama.gd" "$PANORAMA"; cp "$SAVED/ParallaxLayer.java" "$LAYER"
	cp "$SAVED/GdxLayerEffects.java" "$EFFECTS"; cp "$SAVED/PanoramaBatch.java" "$BATCH"
}
trap 'restore; rm -rf "$SAVED"' EXIT
LOG=build/r276/mutant.log
mkdir -p build/r276/mutants
status=0
n=0
mutant() {
	# $1 the file, $2 what it is, $3 the text as it is, $4 the text mutated
	local file=$1; shift
	n=$((n + 1))
	FROM="$2" TO="$3" python3 - "$file" <<'PY' || { echo "mutation did not apply: $1"; exit 2; }
import os, sys
path = sys.argv[1]
text = open(path).read()
if text.count(os.environ['FROM']) != 1:
    sys.exit(1)
open(path, 'w').write(text.replace(os.environ['FROM'], os.environ['TO']))
PY
	if engines/godot/tools/frame-check.sh engines/rounds/tower >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes the tower round ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1))"; status=1
	else
		# Which stills it fails: the report's lines over the threshold.
		failed=$(awk '/\.png  mean diff/ && $4 > 2 {printf "%s ", $1}' "$LOG")
		echo "caught: $1 ($(grep -o 'worst mean diff [0-9.]*' "$LOG" | head -1); over 2: ${failed:-none, see the log})"
	fi
	cp "$LOG" "build/r276/mutants/$n.log"
	restore
}
mutant "$TOWER" "Godot: no wrap, the layer drawn flat" \
	'return l.model.kind == "IMAGE" and l.model.towerRadius > 0' \
	'return false'
mutant "$TOWER" "Godot: no shade" \
	'var bright: float = 1 - m.towerShade * (1 - cos(mid))' \
	'var bright: float = 1.0'
mutant "$TOWER" "Godot: strips along the arc, not at radius sin(a)" \
	'var x0 := axis_x + radius * sin(p0 / radius)
			var x1 := axis_x + radius * sin(p1 / radius)' \
	'var x0 := axis_x + p0 * 2 / PI
			var x1 := axis_x + p1 * 2 / PI'
mutant "$TOWER" "Godot: the tower turning the wrong way" \
	'var corner := x - axis_x + image_left' \
	'var corner := axis_x - x + image_left'
mutant "$LAYER" "core: strips along the arc, not at radius sin(a)" \
	'float x0 = axisX + radius * (float) Math.sin(p0 / radius), x1 = axisX + radius * (float) Math.sin(p1 / radius);' \
	'float x0 = axisX + p0 * 2 / (float) Math.PI, x1 = axisX + p1 * 2 / (float) Math.PI;'
mutant "$EFFECTS" "core: the page fog's shader ignoring the darkness in the blue" \
	'vec4(u_tint * (1.0 - v_color.b), v_color.a)' \
	'vec4(u_tint, v_color.a)'
mutant "$TOWER" "Godot: seen from close by, the courses straight" \
	'return (camera - 1) / (camera - cos(a)) if camera > 1 else 1.0' \
	'return 1.0'
mutant "$PANORAMA" "Godot: seen from close by, the set behind left flat" \
	'	if tower.is_empty() or is_same(tower, l) or PlaxTower.is_tower(l):' \
	'	if true:'
mutant "$LAYER" "core: seen from close by, the courses straight" \
	'{return camera > 1 ? (camera - 1) / (camera - (float) Math.cos(a)) : 1;}' \
	'{return 1;}'
mutant "$BATCH" "core: the panoramas placed but not scaled about the eye" \
	'float scale = scales[strip], ' \
	'float scale = 1, '
exit $status
