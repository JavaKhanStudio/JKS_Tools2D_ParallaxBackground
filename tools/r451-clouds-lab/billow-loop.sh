#!/usr/bin/env bash
# r451: does the clouds lab's BILLOW jump? Two clips of its view 'earlier rounds' (r556), one with BILLOW's amplitude 0,
# through tools/r419-swell-loop/jumps.py on panel D (the painted clouds, churned and relit): BILLOW's own change is even
# from frame to frame, a term that is not a whole number of turns over the layer's period (FOG's, 8 wavelengths across)
# jumps each time along() wraps there (MUTATE=1: the 2 turns a wavelength of dy's first wave made 2.1, a 0.8 turn jump).
# Each clip starts 3 s before the painted layer's effect start wraps (tools/clouds-lab.sh --before-wrap: found by
# stepping the lab, not reckoned, r559), the camera still (cameraScroll=0): a scroll slides the difference every frame
# and drowned the jump (at 120: the mutant 1.5x the median, the plain 1.2x; still: 3.7x and 1.2x).
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
tools/clouds-lab.sh --slider view=4 --slider cameraScroll=0 --before-wrap 3 --clip "$OUT/billow.mp4" || exit 2
tools/clouds-lab.sh --no-build --slider view=4 --slider cameraScroll=0 --slider billowAmplitude=0 --before-wrap 3 --clip "$OUT/flat.mp4" || exit 2
python3 tools/r419-swell-loop/jumps.py "$OUT/billow.mp4" "$OUT/flat.mp4" --top 306 --bottom 430 --left 12 --right 515 --ratio 2.0
