#!/usr/bin/env bash
# The pines lab (r421): R1's ds8-forest drawn twice by the real reader compiled with GWT, in WebGL, its front pines in
# each panel cut with layer.py's misty_pines rule on (as it ships) or off (core/test-data/pines). Choices: A's and B's
# cut, the view (the page, the pines alone), the front strip (the cycle, or one strip the rule changes), red marks
# where on and off differ; a slider: the camera's scroll.
#   tools/pines-lab.sh                 the lab: a Chrome window, served until it closes
#   tools/pines-lab.sh --shot F.png    one still, headless (6 s in)
#   tools/pines-lab.sh --views DIR     one still per front strip choice, marks on, headless: DIR/b0.png ...
#   tools/pines-lab.sh --clip F.mp4    a clip, headless: 8 s at 30 fps, stepped at game speed (window.pinesLabAct)
#   tools/pines-lab.sh --slider K=V    before --shot/--views/--clip: start control K (its data-key) at V, a choice at its option's index
#   tools/pines-lab.sh --copy F.png    checks "Copy settings", headless: moves two controls, clicks, fails unless the JSON
#                                      names every control and the two moved; F is the page
# Builds with ./gradlew :core:browserTestWar first (--no-build skips it). Needs Chrome, python3 and, headless, Python
# Playwright and ffmpeg.
# on-screen: with no argument this is the pines lab, a window Simon asked for; the rest is headless Chrome.
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
[ $MODE = open ] && refuse_agent_window tools/pines-lab.sh
lab_serve pines-lab.html $BUILD
[ $MODE = open ] && lab_open

# Headless: Playwright drives the system Chrome on the GPU (tools/lab_driver.py), steps the lab through window.pinesLabAct and
# screenshots it.
lab_frames
python3 - "$URL?clip=1" "$CHROME" "$FRAMES" "$MODE" "$SLIDERS" <<'PY' || exit 1
import sys
sys.path.insert(0, "tools")
from lab_driver import Lab
url, chrome, frames, mode, sliders = sys.argv[1:]
with Lab(url, chrome, "pines") as lab:
    lab.sliders(sliders)
    if mode == "shot":
        lab.act(6)
        lab.shot(frames + "/still.png")
    elif mode == "views":
        lab.set("marks", "1")
        lab.act(6)
        for i in range(lab.options("front")):
            lab.set("front", i)
            lab.act(0.5)
            lab.shot(frames + "/b%d.png" % i)
    elif mode == "copy":
        lab.set("front", "2")
        lab.set("marks", "1")
        lab.copy({"pinesA", "pinesB", "view", "front", "marks", "cameraScroll"},
                 {"front": {"started": 0, "now": 2}, "marks": {"started": 0, "now": 1}}, front="l3_2 alone")
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
