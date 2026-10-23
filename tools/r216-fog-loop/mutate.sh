#!/usr/bin/env bash
# r216: proves the shaders round sees FOG's 8-wavelength period. Puts back, in Godot's copy only, first the old
# frequencies (1, 2, 3 per wavelength), then the old phase wrap (FOG alone at one wavelength), runs engines/godot/tools/frame-check.sh
# on engines/rounds/shaders for each, and fails unless both runs FAIL. Restores plax_effects.gd on exit.
# headless: engines/godot/tools/frame-check.sh renders in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
GD=engines/godot/addons/jks_parallax/plax_effects.gd
SAVED=$(mktemp)
cp "$GD" "$SAVED"
trap 'cp "$SAVED" "$GD"; rm -f "$SAVED"' EXIT
status=0
run() {
	if engines/godot/tools/frame-check.sh engines/rounds/shaders >/tmp/r216-mutant.log 2>&1; then
		echo "NOT CAUGHT: $1 passes the shaders round"; status=1
	else
		echo "caught: $1 ($(grep -o 'worst mean diff [0-9.]*' /tmp/r216-mutant.log | head -1))"
	fi
	cp "$SAVED" "$GD"
}
sed -i 's/TAU \* 0\.875 \* p\.x/TAU * p.x/; s/2\.125 \* p\.x/2.0 * p.x/; s/2\.875 \* p\.x/3.0 * p.x/' "$GD"
grep -q "TAU \* p.x" "$GD" || { echo "mutation 1 did not apply"; exit 2; }
run "old frequencies in Godot"
# FOG alone back to one wavelength: SWELL, MIRROR and the other glinting layers keep _across(), so a catch is FOG's own wrap.
sed -i 's/var period := wavelength \* _across(model)$/var period := wavelength * (1 if model.get("shaderEffect", "") == "FOG" else _across(model))/' "$GD"
grep -q 'var period := wavelength \* (1 if model.get("shaderEffect", "") == "FOG" else _across(model))$' "$GD" \
	|| { echo "mutation 2 did not apply"; exit 2; }
run "FOG phase wrapped at one wavelength in Godot"
exit $status
