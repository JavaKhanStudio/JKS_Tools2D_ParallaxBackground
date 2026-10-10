#!/usr/bin/env bash
# ai-film.sh ROUND_DIR OUT [SECONDS] [FPS] — each scene of a round scrolling, as OUT/<id>.mp4 and OUT/<id>.gif (r239).
#
#   tools/ai-film.sh build/ai/forest/page build/ai/forest/film 8 30
#
# The frames are core's, off screen: tools/parallax-lab-shots.sh on a copy of the round whose scenes carry
# "film": {"seconds": SECONDS, "fps": FPS} (ParallaxShots goes on scrolling after its 12 s still and saves every frame).
# The mp4 is 1280x720 at FPS; the gif 640x360 at 15 fps, one palette for the whole film. Needs ffmpeg. Frames are kept
# in OUT/frames.
set -u
cd "$(dirname "$0")/.." || exit 1
ROUND="$1" OUT="$(realpath -m "$2")" SECONDS_="${3:-8}" FPS="${4:-30}"
mkdir -p "$OUT/frames"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
python3 - "$ROUND/round.json" "$TMP/round.json" "$SECONDS_" "$FPS" <<'PY' || exit 1
import json, sys
src, dst, seconds, fps = sys.argv[1], sys.argv[2], float(sys.argv[3]), int(sys.argv[4])
r = json.load(open(src))
for s in r["scenes"]:
    s["film"] = {"seconds": seconds, "fps": fps}
json.dump(r, open(dst, "w"), indent=1)
PY
tools/parallax-lab-shots.sh "$TMP" "$OUT/frames" || exit 1
rc=0
for first in "$OUT"/frames/*-f0000.png; do
	id=$(basename "$first" -f0000.png)
	ffmpeg -loglevel error -y -framerate "$FPS" -i "$OUT/frames/$id-f%04d.png" -c:v libx264 -pix_fmt yuv420p -crf 18 \
		"$OUT/$id.mp4" || rc=1
	ffmpeg -loglevel error -y -framerate "$FPS" -i "$OUT/frames/$id-f%04d.png" -vf \
		"fps=15,scale=640:-1:flags=lanczos,split[a][b];[a]palettegen=stats_mode=full[p];[b][p]paletteuse=dither=sierra2_4a" \
		"$OUT/$id.gif" || rc=1
	echo "$OUT/$id.mp4 $OUT/$id.gif"
done
exit $rc
