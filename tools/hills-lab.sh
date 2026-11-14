#!/usr/bin/env bash
# The hills lab (r384): r365's AI forest page drawn twice by the real reader compiled with GWT, in WebGL, its hills layer
# in each panel painted by the recipe that panel's choice picks (core/test-data/hills: tools/r384-hills/try.py's
# candidates over three seeds). Choices: A's and B's recipe, the seed, the view (the page, without the pines, the hills
# alone); a slider: the camera's scroll.
#   tools/hills-lab.sh                 the lab: a Chrome window, served until it closes
#   tools/hills-lab.sh --shot F.png    one still, headless (6 s in)
#   tools/hills-lab.sh --views DIR     one still per candidate in B (A today's), headless: DIR/b0.png ...
#   tools/hills-lab.sh --clip F.mp4    a clip, headless: 8 s at 30 fps, stepped at game speed (window.hillsLabAct)
#   tools/hills-lab.sh --slider K=V    before --shot/--views/--clip: start control K (its data-key) at V, a choice at its option's index
#   tools/hills-lab.sh --copy F.png    checks "Copy settings", headless: moves two controls, clicks, fails unless the JSON
#                                      names every control and the two moved; F is the page
# Builds with ./gradlew :core:browserTestWar first (--no-build skips it). Needs Chrome, python3 and, headless, Python
# Playwright and ffmpeg.
# on-screen: with no argument this is the hills lab, a window Simon asked for; the rest is headless Chrome.
set -u
cd "$(dirname "$0")/.." || exit 1
MODE=open OUT="" BUILD=1 SLIDERS=""
while [ $# -gt 0 ]; do
	case "$1" in
		--shot) MODE=shot; OUT="$(realpath -m "$2")"; shift ;;
		--clip) MODE=clip; OUT="$(realpath -m "$2")"; shift ;;
		--copy) MODE=copy; OUT="$(realpath -m "$2")"; shift ;;
		--views) MODE=views; OUT="$(realpath -m "$2")"; shift ;;
		--slider) SLIDERS="$SLIDERS $2"; shift ;;
		--no-build) BUILD=0 ;;
		*) echo "unknown argument: $1" >&2; exit 2 ;;
	esac
	shift
done
. tools/lab-serve.sh
[ $MODE = open ] && refuse_agent_window tools/hills-lab.sh
lab_serve hills-lab.html $BUILD
[ $MODE = open ] && lab_open

# Headless: Playwright drives the system Chrome on the GPU (tools/lab_driver.py), steps the lab through window.hillsLabAct and
# screenshots it.
lab_frames
python3 - "$URL?clip=1" "$CHROME" "$FRAMES" "$MODE" "$SLIDERS" <<'PY' || exit 1
import sys
sys.path.insert(0, "tools")
from lab_driver import Lab
url, chrome, frames, mode, sliders = sys.argv[1:]
with Lab(url, chrome, "hills") as lab:
    lab.sliders(sliders)
    if mode == "shot":
        lab.act(6)
        lab.shot(frames + "/still.png")
    elif mode == "views":
        lab.act(6)
        for i in range(lab.options("hillsB")):
            lab.set("hillsB", i)
            lab.act(0.5)
            lab.shot(frames + "/b%d.png" % i)
    elif mode == "copy":
        lab.set("hillsB", "4")
        lab.set("seed", "1")
        lab.copy({"hillsA", "hillsB", "seed", "view", "cameraScroll"},
                 {"hillsB": {"started": 2, "now": 4}, "seed": {"started": 0, "now": 1}}, B="inpaint 1.0, round")
        lab.shot(frames + "/still.png")
    else:
        lab.clip(frames, 240)
PY
if [ $MODE = views ]; then
	mkdir -p "$OUT" && cp "$FRAMES"/b*.png "$OUT"/ && echo "stills: $OUT/b*.png"
elif [ $MODE = shot ] || [ $MODE = copy ]; then
	cp "$FRAMES/still.png" "$OUT" && echo "still: $OUT"
else
	lab_encode "$OUT"
fi
