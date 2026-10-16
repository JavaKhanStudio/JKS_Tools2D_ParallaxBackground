#!/usr/bin/env bash
# r451: does the clouds lab's BILLOW jump? Two clips of its view 'alone, side by side' at scroll 120, one with BILLOW's
# amplitude 0, through tools/r419-swell-loop/jumps.py on panel C (the clouds alone under BILLOW): BILLOW's own change
# is even from frame to frame, a term that is not a whole number of turns over the layer's period (FOG's, 8
# wavelengths across) jumps each time along() wraps there (MUTATE=1: the 2 turns a wavelength of dy's first wave made
# 2.1).
#   tools/r451-clouds-lab/billow-loop.sh OUTDIR            exits 1 on a jump
#   MUTATE=1 tools/r451-clouds-lab/billow-loop.sh OUTDIR   must exit 1 (puts CloudsLab.java back after)
# headless: tools/clouds-lab.sh --clip renders in headless Chrome.
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT="$(realpath -m "${1:?usage: billow-loop.sh OUTDIR}")"
mkdir -p "$OUT"
LAB=core/browser-test/launcher/jks/tools2d/parallax/browsertest/CloudsLab.java
if [ "${MUTATE:-0}" = 1 ]; then
	cp "$LAB" "$OUT/CloudsLab.java.keep"
	trap 'cp "$OUT/CloudsLab.java.keep" "$LAB"' EXIT
	sed -i 's/sin(TAU \* (2.0 \* p.x + p.y - t + 0.6))/sin(TAU * (2.1 * p.x + p.y - t + 0.6))/' "$LAB"
	grep -q '2.1 \* p.x + p.y' "$LAB" || { echo "the mutant no longer applies: update billow-loop.sh" >&2; exit 2; }
fi
tools/clouds-lab.sh --slider view=2 --slider cameraScroll=120 --clip "$OUT/billow.mp4" || exit 2
tools/clouds-lab.sh --no-build --slider view=2 --slider cameraScroll=120 --slider billowAmplitude=0 --clip "$OUT/flat.mp4" || exit 2
python3 tools/r419-swell-loop/jumps.py "$OUT/billow.mp4" "$OUT/flat.mp4" --top 270 --bottom 490 --left 12 --right 767 --ratio 1.4
