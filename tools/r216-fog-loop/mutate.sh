#!/usr/bin/env bash
# r216: proves the shaders round sees FOG's 8-wavelength period. Puts back, in Godot's copy only, first the old
# frequencies (1, 2, 3 per wavelength), then the old phase wrap (one wavelength), runs tools/godot-parallax-shots.sh
# on engines/godot/tests/shaders for each, and fails unless both runs FAIL. Restores plax_effects.gd on exit.
# headless: tools/godot-parallax-shots.sh renders in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
GD=engines/godot/addons/jks_parallax/plax_effects.gd
SAVED=$(mktemp)
cp "$GD" "$SAVED"
trap 'cp "$SAVED" "$GD"; rm -f "$SAVED"' EXIT
status=0
run() {
	if tools/godot-parallax-shots.sh engines/godot/tests/shaders >/tmp/r216-mutant.log 2>&1; then
		echo "NOT CAUGHT: $1 passes the shaders round"; status=1
	else
		echo "caught: $1 ($(grep -o 'worst mean diff [0-9.]*' /tmp/r216-mutant.log | head -1))"
	fi
	cp "$SAVED" "$GD"
}
sed -i 's/TAU \* 0\.875 \* p\.x/TAU * p.x/; s/2\.125 \* p\.x/2.0 * p.x/; s/2\.875 \* p\.x/3.0 * p.x/' "$GD"
grep -q "TAU \* p.x" "$GD" || { echo "mutation 1 did not apply"; exit 2; }
run "old frequencies in Godot"
sed -i 's/(FOG_PERIOD if model.shaderEffect == "FOG" else 1)/1/' "$GD"
grep -q "var period := wavelength \* 1" "$GD" || { echo "mutation 2 did not apply"; exit 2; }
run "phase wrapped at one wavelength in Godot"
exit $status
