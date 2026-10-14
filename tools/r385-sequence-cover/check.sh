#!/usr/bin/env bash
# check.sh [PAGE_DIR] [FILM] — r366's ai-page.sh lurker page, its front a SEQUENCE, baked and held to its film (r385).
#
#   tools/ai-page.sh --theme "dark pine forest at dusk" --seed 3 --lurker "a hooded figure" build/r366/page
#   tools/r366-lurker/film.sh build/r366/page/page build/r366     # once: the frames, off screen
#   tools/r385-sequence-cover/check.sh build/r366/page/page build/r366/film
#
# Copies the page to build/r385, bakes its lurkerCover (tools/lurker-cover.sh), tracks the figure over the film's 24 s
# at 24 fps, and runs r369's agree.py at two thresholds: 30 (moments.py's) and 0, any difference at all. Fails when
# the cover is empty or a frame inside an interval shows a figure pixel.
set -eu
cd "$(dirname "$0")/../.." || exit 1
PAGE_DIR="${1:-build/r366/page/page}" FILM="${2:-build/r366/film}"
mkdir -p build/r385
cp "$PAGE_DIR"/*.jplax "$PAGE_DIR"/*.atlas "$PAGE_DIR"/*.png build/r385/
PAGE=$(ls build/r385/*.jplax)
LURKER_TRACK="60 24 576" tools/lurker-cover.sh "$PAGE" > build/r385/track.txt
head -1 build/r385/track.txt
if head -1 build/r385/track.txt | grep -q ': 0 intervals'; then
	echo "FAIL: no cover interval"
	exit 1
fi
python3 tools/r369-lurker-cover/agree.py build/r385/track.txt "$FILM" a01 a00 30
python3 tools/r369-lurker-cover/agree.py build/r385/track.txt "$FILM" a01 a00 0
