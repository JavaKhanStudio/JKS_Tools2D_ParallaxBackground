#!/usr/bin/env bash
# film.sh [OUT] — the r363 reveal demo: a figure behind Hiver's pines, filmed off screen, and its stills (r363).
#
#   tools/r363-reveal/film.sh build/r363
#
# make.py writes the round (OUT/round), tools/ai-film.sh films 24 s of each scene from 12 s on (OUT/film/v01.mp4 ...,
# cage), moments.py counts the figure's pixels on screen and writes OUT/film/v01-moments.png and v02-giant.png.
# vanish.py writes OUT/film/v03.mp4 and v03-vanish.png: the figure seen once, gone in the next gap.
set -eu
cd "$(dirname "$0")/../.." || exit 1
OUT="${1:-build/r363}"
python3 tools/r363-reveal/make.py "$OUT/round"
tools/ai-film.sh "$OUT/round" "$OUT/film" 24 24
python3 tools/r363-reveal/moments.py "$OUT/film"
python3 tools/r363-reveal/vanish.py "$OUT/film"
