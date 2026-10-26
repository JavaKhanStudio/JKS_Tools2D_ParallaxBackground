#!/usr/bin/env bash
# r59: proves the Godot rings draw a SHADER band's effect moving, and stretch it to close its ring without a step
# (three.js r53, r58). Moving: shoots the rings lab on g03 with the camera still (speed=0) and measures what changes
# from 0 to 8 s (moving.py), then again with the band's effect clock frozen at 0; fails unless the mist changes by over
# 1 and the frozen one by under a tenth of that. The join: shoots it on r58's fixture (engines/threejs/tests/
# rings-seam: a FOG period of 24 that 20-unit tiles do not fill round the ring) and measures the join shot (seam.py);
# then again with plax_rings.gd's stretch put back to 1. Fails unless the mutant's step at the join is over twice the
# real one's and the real one's is within 1 of the band's sharpest columns (its 99th percentile).
# Restores the file on exit.
# headless: engines/godot/tools/rings-lab.sh --shot renders in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
GD=engines/godot/addons/jks_parallax/plax_rings.gd
OUT=build/r59/seam
SAVED=$(mktemp)
cp "$GD" "$SAVED"
trap 'cp "$SAVED" "$GD"; rm -f "$SAVED"' EXIT
lab() {
	engines/godot/tools/rings-lab.sh --shot "$OUT/$1" "${@:2}" >"$OUT/$1.log" 2>&1 \
		|| { echo "the lab failed: $OUT/$1.log"; exit 2; }
}
shoot() {
	lab "$1" round=engines/threejs/tests/rings-seam
	python3 tools/r59-ring-seam/seam.py "$OUT/$1/j01-join-t8.png"
}
mkdir -p "$OUT"
status=0
lab still scenes=g03 speed=0
STILL=$(python3 tools/r59-ring-seam/moving.py "$OUT/still")
echo "moving: $STILL"
sed -i 's|PlaxEffects.apply(m, l, background._effect_time, 1, 1)|PlaxEffects.apply(m, l, 0.0, 1, 1)|' "$GD"
grep -q "PlaxEffects.apply(m, l, 0.0, 1, 1)" "$GD" || { echo "mutation 1 did not apply"; exit 2; }
lab frozen scenes=g03 speed=0
FROZEN=$(python3 tools/r59-ring-seam/moving.py "$OUT/frozen")
echo "frozen: $FROZEN"
cp "$SAVED" "$GD"
python3 - "$STILL" "$FROZEN" <<'PY' || status=1
import sys
real, frozen = (float(s.split()[1]) for s in sys.argv[1:3])
ok = real > 1 and frozen < real / 10
print('PASS' if ok else 'FAIL', f'the mist moves {real} vs frozen {frozen}')
sys.exit(0 if ok else 1)
PY
REAL=$(shoot real)
echo "stretched: $REAL"
sed -i 's|var stretch := maxf(1, roundf(ring / period)) \* period / ring if period > 0 and ring > 0 else 1.0|var stretch := 1.0|' "$GD"
grep -q "var stretch := 1.0$" "$GD" || { echo "mutation 2 did not apply"; exit 2; }
MUTANT=$(shoot mutant)
echo "not stretched: $MUTANT"
python3 - "$REAL" "$MUTANT" <<'PY' || status=1
import sys
real, mutant = ([float(w) for w in s.split()[1::2]] for s in sys.argv[1:3])
ok = mutant[0] > 2 * real[0] and real[0] < real[1] + 1
print('PASS' if ok else 'FAIL', f'join {real[0]} vs mutant {mutant[0]}, body {real[1]}')
sys.exit(0 if ok else 1)
PY
exit $status
