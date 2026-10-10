#!/usr/bin/env bash
# strength.sh — how much the dissolve moves the transfer round's stills (r250).
#
# The reader rounds fail past a mean 2/255 apart: a reader that drew a dissolve as a plain fade would pass a scene the
# dissolve moves by less. t09 and t10 are t01 and t02 (the same pages, the same 4 s transfert, a plain fade) as a
# dissolve: this renders the round with libGDX (tools/parallax-lab-shots.sh, off screen) and prints, per mid-transfert
# still, how far the dissolve is from the fade; it fails when one is under MIN (default 2.5, past the rounds' 2).
# Writes build/r250/strength/.
# headless: tools/parallax-lab-shots.sh renders in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
MIN="${MIN:-2.5}" OUT=build/r250/strength
rm -rf "$OUT"; mkdir -p "$OUT"
tools/parallax-lab-shots.sh engines/godot/tests/transfer "$OUT" >/dev/null || exit 1
python3 - "$OUT" "$MIN" <<'PY'
import os, sys
from PIL import Image, ImageChops, ImageStat
out, least = sys.argv[1], float(sys.argv[2])
worst = None
for dissolve, fade in (('t09', 't01'), ('t10', 't02')):
    a = Image.open(os.path.join(out, dissolve + '-t6.png')).convert('RGB')
    b = Image.open(os.path.join(out, fade + '-t6.png')).convert('RGB')
    mean = sum(ImageStat.Stat(ImageChops.difference(a, b)).mean) / 3
    print(f'{dissolve}-t6.png  the dissolve moves it from {fade}\'s fade by {mean:.2f} / 255')
    worst = mean if worst is None else min(worst, mean)
print(('PASS' if worst >= least else 'FAIL') + f': weakest {worst:.2f} (at least {least})')
sys.exit(0 if worst >= least else 1)
PY
