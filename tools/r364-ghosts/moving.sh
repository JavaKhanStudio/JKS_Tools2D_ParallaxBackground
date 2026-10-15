#!/usr/bin/env bash
# headless: numpy on PNGs; the films go through ai-film.sh, which renders in cage (parallax-lab-shots.sh)
# moving.sh OUT CELL... — r364's moving example: each painted ai-page.sh CELL (a page folder with recipe.json, strips/,
# page/) filmed scrolling as it was cut (its own page) and as today's layer.py cuts the same strips (recut.sh, pasted
# into a copy of its atlas at the same regions), the two films stacked, R1 above HEAD, as OUT/<cell>.mp4 and .gif.
#   tools/r364-ghosts/moving.sh build/r364/moving ../JKS_Tools2D_ParallaxAI/build/rounds/r1/{ds8-forest,dsxlt-postapo}
set -u
cd "$(dirname "$0")/../.." || exit 1
O=${1:?OUT}
shift
SECONDS_=${SECONDS_:-10}
tools/r364-ghosts/recut.sh tools/r239-ai-strips/layer.py "$O/cut" "$@"
rc=0
for e in "$@"; do
	e=${e%/}
	c=${e##*/}
	python3 - "$e" "$O/cut/$c" "$O/$c" <<'PY' || { rc=1; continue; }
import glob, json, os, re, shutil, sys
from PIL import Image
cell, cut, out = sys.argv[1:]
for side in ("r1", "head"):
    shutil.rmtree(f"{out}/{side}", ignore_errors=True)
    shutil.copytree(f"{cell}/page", f"{out}/{side}/page")
atlas = glob.glob(f"{out}/head/page/*.atlas")[0]
sheet = Image.open(atlas[:-6] + ".png").convert("RGBA")
rx = r"^(\S+)\n  rotate: \w+\n  xy: (\d+), (\d+)\n  size: (\d+), (\d+)"
for name, x, y, w, h in re.findall(rx, open(atlas).read(), re.M):
    m = re.match(r"(l\d)(?:_(\d))?$", name)  # l3_2 is strip l3v2, l3_0 strip l3
    strip = m[1] + ("v" + m[2] if m[2] and m[2] != "0" else "")
    img = Image.open(f"{cut}/{strip}.png").convert("RGBA")
    assert img.size == (int(w), int(h)), (name, img.size)
    sheet.paste(img, (int(x), int(y)))
sheet.save(atlas[:-6] + ".png")
for side in ("r1", "head"):
    r = json.load(open(f"{cell}/page/round.json"))
    for s in r["scenes"]:
        s["page"] = os.path.join(out, side, "page", os.path.basename(s["page"]))
        s["atlasDir"] = os.path.join(out, side, "page")
    json.dump(r, open(f"{out}/{side}/page/round.json", "w"), indent=1)
PY
	for side in r1 head; do
		tools/ai-film.sh "$O/$c/$side/page" "$O/$c/$side/film" "$SECONDS_" 30 >"$O/$c/$side.film.log" 2>&1 || rc=1
	done
	a=$(ls "$O/$c/r1/film/"*.mp4) b=$(ls "$O/$c/head/film/"*.mp4)
	label="drawtext=font=sans:fontsize=28:fontcolor=white:box=1:boxcolor=black@0.6:boxborderw=8:x=16:y=16:text"
	ffmpeg -loglevel error -y -i "$a" -i "$b" -filter_complex \
		"[0]$label='$c  R1 cut (before r358)'[t];[1]$label='$c  HEAD cut (layer.py today)'[u];[t][u]vstack" \
		-c:v libx264 -pix_fmt yuv420p -crf 20 "$O/$c.mp4" || rc=1
	ffmpeg -loglevel error -y -i "$O/$c.mp4" -vf \
		"fps=15,scale=640:-1:flags=lanczos,split[a][b];[a]palettegen=stats_mode=full[p];[b][p]paletteuse=dither=sierra2_4a" \
		"$O/$c.gif" || rc=1
	echo "$O/$c.mp4 $O/$c.gif"
done
exit $rc
