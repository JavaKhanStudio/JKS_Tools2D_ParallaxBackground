#!/usr/bin/env bash
# r419: does the animation lab's SHEEN jump? Two clips of the lab's "sea: SWELL + SHEEN" view at scroll 120 (under
# FOG's periods along() wraps every 10 world units, which the sea only crosses in 8 s that fast), one with SHEEN at
# strength 0, through jumps.py at 1.4x: SHEEN's own change is even (max 1.15x its median), a term that is not a whole
# number of turns over the period jumps at the wrap (MUTATE=1: the dashes' 9 turns across made 9.5, 1.68x, frame 126).
#   tools/r419-swell-loop/sheen.sh OUTDIR            exits 1 on a jump
#   MUTATE=1 tools/r419-swell-loop/sheen.sh OUTDIR   must exit 1 (puts AnimLab.java back after)
# headless: tools/anim-lab.sh --clip renders in headless Chrome.
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT="$(realpath -m "${1:?usage: sheen.sh OUTDIR}")"
mkdir -p "$OUT"
LAB=core/browser-test/launcher/jks/tools2d/parallax/browsertest/AnimLab.java
if [ "${MUTATE:-0}" = 1 ]; then
	cp "$LAB" "$OUT/AnimLab.java.keep"
	trap 'cp "$OUT/AnimLab.java.keep" "$LAB"' EXIT
	sed -i 's/sin(TAU \* (9.0 \* rq.x - 4.0/sin(TAU * (9.5 * rq.x - 4.0/' "$LAB"
	grep -q '9.5 \* rq.x' "$LAB" || { echo "the mutant no longer applies: update sheen.sh" >&2; exit 2; }
fi
tools/anim-lab.sh --slider view=9 --slider cameraScroll=120 --clip "$OUT/sheen.mp4" || exit 2
tools/anim-lab.sh --no-build --slider view=9 --slider cameraScroll=120 --slider sheenStrength=0 --clip "$OUT/flat.mp4" || exit 2
python3 tools/r419-swell-loop/jumps.py "$OUT/sheen.mp4" "$OUT/flat.mp4" --top 200 --bottom 305 --ratio 1.4
