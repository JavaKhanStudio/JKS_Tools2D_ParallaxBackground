#!/usr/bin/env bash
# The FOG lab (r204, doubt d15): the shaders round's s01 page drawn four times by the real reader, compiled with GWT,
# in WebGL: its mist band through FOG with patches as tall as wide (A, what ships), half as tall (B), as tall as a
# slider says (C), and plain. Sliders: amplitude, wavelength, speed, C's height, the camera's scroll, the trees' WAVE ripple,
# the mist's layer, the page fog's strength and colour (r217), the parallax set, the mist's size and height. It opens on Simon's r208 settings.
#   tools/fog-lab.sh                 the lab: a Chrome window, served until it closes
#   tools/fog-lab.sh --shot F.png    one still, headless (6 s into the scroll)
#   tools/fog-lab.sh --clip F.mp4    a clip, headless: 12 s at 30 fps, stepped at game speed (window.fogLabAct)
#   tools/fog-lab.sh --sets DIR      one still per parallax set (r208: s01, round1's calm, PurpleFairy, OneNight), and the
#                                    last with the mist's size at 2.5x, headless: DIR/set0.png ...
#   tools/fog-lab.sh --slider K=V   before --shot/--clip/--sets: start control K (its data-key) at V, a choice at its option's index
#   tools/fog-lab.sh --copy F.png    checks "Copy settings", headless: moves C's slider and the mist to the front,
#                                    clicks, fails unless the JSON names every slider and the two moved; F is the page
# Builds with ./gradlew :core:browserTestWar first (--no-build skips it). Needs Chrome, python3 and, for --shot and
# --clip, Python Playwright and ffmpeg.
# on-screen: with no argument this is the fog lab, a window Simon asked for; --shot and --clip are headless Chrome.
set -u
cd "$(dirname "$0")/.." || exit 1
MODE=open OUT="" BUILD=1 SLIDERS=""
while [ $# -gt 0 ]; do
	case "$1" in
		--shot) MODE=shot; OUT="$(realpath -m "$2")"; shift ;;
		--clip) MODE=clip; OUT="$(realpath -m "$2")"; shift ;;
		--copy) MODE=copy; OUT="$(realpath -m "$2")"; shift ;;
		--sets) MODE=sets; OUT="$(realpath -m "$2")"; shift ;;
		--slider) SLIDERS="$SLIDERS $2"; shift ;;
		--no-build) BUILD=0 ;;
		*) echo "unknown argument: $1" >&2; exit 2 ;;
	esac
	shift
done
. tools/lab-serve.sh
[ $MODE = open ] && refuse_agent_window tools/fog-lab.sh
lab_serve fog-lab.html $BUILD
[ $MODE = open ] && lab_open

# Headless: Playwright drives the system Chrome on the GPU (tools/lab_driver.py), steps the lab through window.fogLabAct and
# screenshots it.
lab_frames
python3 - "$URL?clip=1" "$CHROME" "$FRAMES" "$MODE" "$SLIDERS" <<'PY' || exit 1
import sys
sys.path.insert(0, "tools")
from lab_driver import Lab
url, chrome, frames, mode, sliders = sys.argv[1:]
with Lab(url, chrome, "fog") as lab:
    lab.sliders(sliders)
    if mode == "shot":
        lab.act(6)
        lab.shot(frames + "/still.png")
    elif mode == "sets":
        # Every set the slider picks, one still each, then the last one with the mist grown to cover the frame.
        names = []
        for i in range(lab.options("parallaxSet")):
            lab.page.evaluate("window.fogLabPick(%d)" % i)
            lab.act(6)
            names.append(lab.text("#fog-readout"))
            lab.shot(frames + "/set%d.png" % i)
        lab.set("mistSize", "2.5")
        lab.act(1)
        lab.shot(frames + "/set%d.png" % (i + 1))
        print("\n".join(names))
    elif mode == "copy":
        lab.set("patchHeightC", "0.6")
        lab.set("mistLayer", "7")
        lab.copy({"mistAmplitude", "mistWavelength", "mistSpeed", "patchHeightC", "cameraScroll", "treesWaveRipple", "mistLayer",
                  "fogStrength", "fogColor", "parallaxSet", "mistSize", "mistRise"},
                 {"patchHeightC": {"started": 0.45, "now": 0.6}, "mistLayer": {"started": 5, "now": 7}})
        lab.shot(frames + "/still.png")
    else:
        lab.clip(frames, 360)
PY
if [ $MODE = sets ]; then
	mkdir -p "$OUT" && cp "$FRAMES"/set*.png "$OUT"/ && echo "stills: $OUT/set*.png (the last: the mist at 2.5x)"
elif [ $MODE = shot ] || [ $MODE = copy ]; then
	cp "$FRAMES/still.png" "$OUT" && echo "still: $OUT"
else
	lab_encode "$OUT"
fi
