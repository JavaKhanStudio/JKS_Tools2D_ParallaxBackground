#!/usr/bin/env bash
# check.sh [FILM] — r363's demo with its lurkerCover baked, against the film of it (r369).
#
#   tools/r363-reveal/film.sh build/r363     # once: the frames, off screen
#   tools/r369-lurker-cover/check.sh build/r363/film
#
# make.py writes the round into build/r369/round, tools/lurker-cover.sh bakes v01's cover and tracks the figure over
# film.sh's 24 s at 24 fps, agree.py fails when a frame inside a cover interval shows a figure pixel.
set -eu
cd "$(dirname "$0")/../.." || exit 1
FILM="${1:-build/r363/film}"
python3 tools/r363-reveal/make.py build/r369/round
LURKER_TRACK="60 24 576" tools/lurker-cover.sh build/r369/round/v01.jplax > build/r369/v01-track.txt
head -1 build/r369/v01-track.txt
python3 tools/r369-lurker-cover/agree.py build/r369/v01-track.txt "$FILM"
