#!/usr/bin/env bash
# r419: does the animation lab's MIRROR jump? Two clips of the lab's "sea: SWELL + MIRROR" view (by the water's
# colour, + GLINT) at scroll 120 (under FOG's periods along() wraps every 10 world units, which the sea only crosses
# in 8 s that fast), one with MIRROR at strength 0, through jumps.py at 1.4x: MIRROR's own change is even, a ripple
# term that is not a whole number of turns over the period jumps at the wrap (MUTATE=1: the first ripple's 13 turns
# across made 13.5).
#   tools/r419-swell-loop/mirror.sh OUTDIR            exits 1 on a jump
#   MUTATE=1 tools/r419-swell-loop/mirror.sh OUTDIR   must exit 1 (puts AnimLab.java back after)
# headless: tools/anim-lab.sh --clip renders in headless Chrome.
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT="$(realpath -m "${1:?usage: mirror.sh OUTDIR}")"
mkdir -p "$OUT"
LAB=core/browser-test/launcher/jks/tools2d/parallax/browsertest/AnimLab.java
if [ "${MUTATE:-0}" = 1 ]; then
	cp "$LAB" "$OUT/AnimLab.java.keep"
	trap 'cp "$OUT/AnimLab.java.keep" "$LAB"' EXIT
	sed -i 's/sin(TAU \* (13.0 \* mq.x + 41.0/sin(TAU * (13.5 * mq.x + 41.0/' "$LAB"
	grep -q '13.5 \* mq.x' "$LAB" || { echo "the mutant no longer applies: update mirror.sh" >&2; exit 2; }
fi
tools/anim-lab.sh --slider view=6 --slider cameraScroll=120 --clip "$OUT/mirror.mp4" || exit 2
tools/anim-lab.sh --no-build --slider view=6 --slider cameraScroll=120 --slider mirrorStrength=0 --clip "$OUT/flat.mp4" || exit 2
python3 tools/r419-swell-loop/jumps.py "$OUT/mirror.mp4" "$OUT/flat.mp4" --top 200 --bottom 305 --ratio 1.4
