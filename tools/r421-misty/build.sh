#!/usr/bin/env bash
# headless: PIL on PNGs; the films go through ai-film.sh, which renders in cage (parallax-lab-shots.sh)
# build.sh PAGE OUT — r421: PAGE (an ai-page.sh page painted by dreamshaper_8, R1's ds8-forest) cut by today's layer.py
# with misty_pines on and off (recut.sh, no GPU), each pasted into a copy of its page (r401's variants.page_copy),
# filmed 10 s by the real reader, and laid out by tools/compare_page.py with the strips the rule moves, over the layers
# behind, cropped where on and off differ, at twice their size: OUT/page/r421-compare.html.
#   tools/r421-misty/build.sh ../JKS_Tools2D_ParallaxAI/build/rounds/r1/ds8-forest build/r421
set -eu
cd "$(dirname "$0")/../.."
P=${1%/} O=${2:?OUT}
n=${P##*/}
tools/r364-ghosts/recut.sh tools/r239-ai-strips/layer.py "$O/on" "$P" >/dev/null
tools/r364-ghosts/recut.sh tools/r421-misty/layer_off.py "$O/off" "$P" >/dev/null
python3 - "$P" "$O" "$n" <<'PY'
import glob, json, os, re, sys
import numpy as np
from PIL import Image
sys.path.insert(0, "tools/r401-far-range")
import variants
page, out, n = sys.argv[1:]
atlas = glob.glob(f"{page}/page/*.atlas")[0]
names = [m[0] for m in re.findall(variants.RX, open(atlas).read(), re.MULTILINE)]
items = []
for v in ("on", "off"):
    cuts = {k: Image.open(f"{out}/{v}/{n}/{variants.strip_of(k)}.png").convert("RGBA") for k in names}
    variants.page_copy(page, f"{out}/pages/{v}", cuts)
strips = []
for s in sorted({variants.strip_of(k) for k in names}):
    a = [np.asarray(Image.open(f"{out}/{v}/{n}/{s}.png"))[..., 3].astype(int) for v in ("on", "off")]
    moved = np.abs(a[0] - a[1]) > 8
    if moved.sum() < 50:
        continue
    ys, xs = np.nonzero(moved)
    y0, y1 = max(ys.min() - 40, 0), min(ys.max() + 40, moved.shape[0])
    x0, x1 = max(xs.min() - 60, 0), min(xs.max() + 60, moved.shape[1])
    for v in ("on", "off"):
        sky = Image.open(f"{page}/strips/{s}_ai.png").convert("RGB").getpixel((0, 0))
        bg = Image.new("RGBA", moved.shape[::-1], sky + (255,))
        for k in range(int(s[1])):
            bg.alpha_composite(Image.open(f"{out}/{v}/{n}/l{k}.png").convert("RGBA"))
        bg.alpha_composite(Image.open(f"{out}/{v}/{n}/{s}.png").convert("RGBA"))
        crop = bg.crop((x0, y0, x1, y1))
        crop.resize((crop.width * 2, crop.height * 2), Image.LANCZOS).convert("RGB").save(f"{out}/{s}-{v}.png")
    strips.append((s, int(moved.sum())))
json.dump(strips, open(f"{out}/strips.json", "w"))
print("moved:", strips)
PY
for v in on off; do
	tools/ai-film.sh "$O/pages/$v/page" "$O/film-$v" 10 30 >"$O/film-$v.log" 2>&1
done
python3 - "$O" "$n" <<'PY'
import json, subprocess, sys
out, n = sys.argv[1:]
about = {"on": "today: the misty farther pines at the forest's foot keyed out along the sky-to-pines line",
         "off": "those pines kept as dreamshaper_8 painted them, pale, a depth cue"}
secs = [{"title": f"{n}: the page scrolling (10 s, loops)",
         "about": "The real reader's frames. The rule only touches two of the pines layer's four segments (l3v1, l3v2), "
                  "so the difference comes and goes as they pass, and it is small: a few pale pines at the forest's foot, "
                  "near the bottom. Press Pause, then Flicker, to see them on a still frame (the last 4 s show the most).",
         "items": [{"label": f"misty_pines {v}", "about": about[v], "src": f"{out}/film-{v}/a01.mp4"} for v in ("on", "off")]}]
for s, px in json.load(open(f"{out}/strips.json")):
    secs.append({"title": f"{n} {s}: the pines strip over the layers behind, cropped where on and off differ, twice the size",
                 "about": f"{px} px of alpha differ.",
                 "items": [{"label": f"misty_pines {v}", "about": about[v], "src": f"{out}/{s}-{v}.png"} for v in ("on", "off")]})
spec = {"name": "r421", "title": "r421: the misty pines at the forest's foot: key them out (misty_pines on) or keep them?",
        "intro": "dreamshaper_8 paints farther pines in mist at the foot of the forest's pines layer. r400's rule "
                 "misty_pines keys them out; nobody chose it. Pick two versions under <b>Left</b> and <b>Right</b>, "
                 "drag the yellow line, or press <b>Flicker</b> (the clearest: the same place, one then the other, its name "
                 "in the corner).",
        "sections": secs}
json.dump(spec, open(f"{out}/spec.json", "w"), indent=1)
subprocess.run([sys.executable, "tools/compare_page.py", f"{out}/spec.json", f"{out}/page"], check=True)
PY
