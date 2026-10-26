#!/usr/bin/env bash
# r49: proves engines/godot/tools/frame-check.sh engines/rounds/rings catches a PlaxRings that strays from core's
# ParallaxRings. Runs it as is (it must PASS), then with each mutation of addons/jks_parallax/plax_rings.gd (each must
# FAIL): the page's scale dropped from elevation_of; far ignored (the library's depths always); the bands ordered near
# first; a tile's repeat rounded up. Restores the file on exit. Headless: the rings round has no stills.
set -u
cd "$(dirname "$0")/../../../.." || exit 1
ROUND=engines/rounds/rings
F=engines/godot/addons/jks_parallax/plax_rings.gd
SAVED=$(mktemp -d)
cp "$F" "$SAVED"
trap 'cp "$SAVED/$(basename "$F")" "$F"; rm -rf "$SAVED"' EXIT
LOG=build/r49/mutant.log
mkdir -p build/r49
status=0
if engines/godot/tools/frame-check.sh "$ROUND" >"$LOG" 2>&1; then
	echo "clean: passes"
else
	echo "CLEAN FAILS: see $LOG"; status=1
fi
# name | sed expression | a fixed string the mutated file then holds
MUTATIONS=(
	"scale dropped|s/^\treturn minf(MAX_ELEVATION, (page_y + o.lift) \* per_unit \* o.scale)$/\treturn minf(MAX_ELEVATION, (page_y + o.lift) * per_unit)/|o.lift) * per_unit)"
	"far ignored|s/^\tif o.far > 0 and slowest > 0 and slowest < front:$/\tif false:/|	if false:"
	"near first|s/^\t\twhile j > 0 and sorted\[j\].radius > sorted\[j - 1\].radius:$/\t\twhile j > 0 and sorted[j].radius < sorted[j - 1].radius:/|sorted[j].radius < sorted"
	"repeat rounded up|s/band.repeat = maxi(1, roundi(TAU \* band.radius/band.repeat = maxi(1, ceili(TAU * band.radius/|ceili(TAU * band.radius"
)
for m in "${MUTATIONS[@]}"; do
	IFS='|' read -r name expr check <<<"$m"
	cp "$SAVED/$(basename "$F")" "$F"
	sed -i "$expr" "$F"
	grep -qF "$check" "$F" || { echo "mutation '$name' did not apply"; exit 2; }
	if engines/godot/tools/frame-check.sh "$ROUND" >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $name"; status=1
	else
		echo "caught: $name ($(grep -m1 '^FAIL' "$LOG" | cut -c1-100))"
	fi
done
exit $status
