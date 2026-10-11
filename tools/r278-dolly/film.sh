#!/usr/bin/env bash
# film.sh OUT — r278: Hiver dollied beside the same page zoomed as one picture, as OUT/side.mp4 and OUT/side.gif
# (dolly left, plain right), from tools/r278-dolly/film/round.json through tools/ai-film.sh (libGDX's frames).
# headless: the frames are tools/parallax-lab-shots.sh's, in cage.
set -u
cd "$(dirname "$0")/../.." || exit 1
OUT="$(realpath -m "${1:?usage: film.sh OUT}")"
tools/ai-film.sh tools/r278-dolly/film "$OUT" 8 30 >/dev/null || exit 1
ffmpeg -loglevel error -y -i "$OUT/dolly.mp4" -i "$OUT/plain.mp4" -filter_complex \
	"[0:v]scale=960:-2[a];[1:v]scale=960:-2[b];[a][b]hstack" -c:v libx264 -pix_fmt yuv420p -crf 20 "$OUT/side.mp4" || exit 1
ffmpeg -loglevel error -y -i "$OUT/side.mp4" -vf \
	"fps=12,scale=960:-1:flags=lanczos,split[a][b];[a]palettegen=stats_mode=full[p];[b][p]paletteuse=dither=sierra2_4a" \
	"$OUT/side.gif" || exit 1
echo "$OUT/side.mp4 $OUT/side.gif"
