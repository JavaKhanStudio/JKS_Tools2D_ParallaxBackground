#!/usr/bin/env bash
# The animation lab (r407, after r375): r375's shader animations as lab GLSL, drawn by the real reader compiled with
# GWT, in WebGL: r365's beach sea under WAVE (what ships), SWELL, GLINT and SWELL + GLINT; and the sea under SWELL
# with MIRROR, the whole water a mirror of the sky, like polished metal (r419, after REFLECT and SHEEN), as tuned and
# by the water's colour, with GLINT or not, beside SWELL alone.
# Sliders: the view (four panels, two one over the other, or one close), zoom, the camera's scroll, and each effect's
# numbers. The clouds' BILLOW is in tools/clouds-lab.sh since r451.
#   tools/anim-lab.sh                 the lab: a Chrome window, served until it closes
#   tools/anim-lab.sh --shot F.png    one still, headless (6 s in)
#   tools/anim-lab.sh --views DIR     one still per view, headless: DIR/view0.png ...
#   tools/anim-lab.sh --clip F.mp4    a clip, headless: 8 s at 30 fps, stepped at game speed (window.animLabAct)
#   tools/anim-lab.sh --slider K=V    before --shot/--views/--clip: start control K (its data-key) at V, a choice at its option's index
#   tools/anim-lab.sh --copy F.png    checks "Copy settings", headless: moves two sliders, clicks, fails unless the JSON
#                                     names every control and the two moved; F is the page
# Builds with ./gradlew :core:browserTestWar first (--no-build skips it). Needs Chrome, python3 and, headless, Python
# Playwright and ffmpeg.
# on-screen: with no argument this is the animation lab, a window Simon asked for; the rest is headless Chrome.
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
[ $MODE = open ] && refuse_agent_window tools/anim-lab.sh
lab_serve anim-lab.html $BUILD
[ $MODE = open ] && lab_open

# Headless: Playwright drives the system Chrome on the GPU (tools/lab_driver.py), steps the lab through window.animLabAct and
# screenshots it.
lab_frames
python3 - "$URL?clip=1" "$CHROME" "$FRAMES" "$MODE" "$SLIDERS" <<'PY' || exit 1
import sys
sys.path.insert(0, "tools")
from lab_driver import Lab
url, chrome, frames, mode, sliders = sys.argv[1:]
with Lab(url, chrome, "anim") as lab:
    lab.sliders(sliders)
    if mode == "shot":
        lab.act(6)
        lab.shot(frames + "/still.png")
    elif mode == "views":
        for i in range(lab.options("view")):
            lab.set("view", i)
            lab.act(6 if i == 0 else 0.5)
            lab.shot(frames + "/view%d.png" % i)
    elif mode == "copy":
        lab.set("swellAmplitude", "0.2")
        lab.set("view", "5")
        lab.copy({"view", "zoom", "cameraScroll", "waveAmplitude", "waveWavelength", "waveSpeed", "swellAmplitude", "swellWavelength",
                  "swellSpeed", "glintStrength", "glintSize", "glintDensity", "glintSpeed", "glintCrest", "sunX", "sunWidth",
                  "mirrorStrength", "mirrorRelief", "mirrorShine", "mirrorTint"},
                 {"swellAmplitude": {"started": 0.12, "now": 0.2}, "view": {"started": 0, "now": 5}})
        lab.shot(frames + "/still.png")
    else:
        lab.clip(frames, 240)
PY
if [ $MODE = views ]; then
	mkdir -p "$OUT" && cp "$FRAMES"/view*.png "$OUT"/ && echo "stills: $OUT/view*.png"
elif [ $MODE = shot ] || [ $MODE = copy ]; then
	cp "$FRAMES/still.png" "$OUT" && echo "still: $OUT"
else
	lab_encode "$OUT"
fi
