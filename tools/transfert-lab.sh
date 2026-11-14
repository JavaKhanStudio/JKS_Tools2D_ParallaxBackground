#!/usr/bin/env bash
# The transfert lab (r220): two of round1's pages, back and forth, the transfert (the cross-fade between pages) drawn
# five times by the real reader, compiled with GWT, in WebGL: A as it ships, B through a colour grade (the reader's tint,
# shipped API), C the library's fog creep (r252), D its dissolve (r250) and E its transfert through a colour (r251).
# Sliders: the pages, the transfert's length, the hold between, the scroll, B's grade colour, C's mist, E's colour, the
# depth stagger, D's patches and edge softness.
#   tools/transfert-lab.sh               the lab: a Chrome window, served until it closes
#   tools/transfert-lab.sh --sheet DIR   stills, headless: DIR/p000.png, p025, p050, p075 at those percents of the first
#                                        transfert, p100 just after it, and back050 halfway through the way back
#   tools/transfert-lab.sh --clip F.mp4  a clip, headless: one cycle there and back at 30 fps, stepped at game speed
#   tools/transfert-lab.sh --copy F.png  checks "Copy settings", headless: moves the pages and the stagger, clicks, fails
#                                        unless the JSON names every slider and the two moved; F is the page
#   tools/transfert-lab.sh --slider K=V  before a headless mode: start control K (its data-key) at V, a choice at its option's index
# Builds with ./gradlew :core:browserTestWar first (--no-build skips it). Needs Chrome, python3 and, headless, Python
# Playwright and ffmpeg (--clip).
# on-screen: with no argument this is the transfert lab, a window Simon asked for (r220); the other modes are headless Chrome.
set -u
cd "$(dirname "$0")/.." || exit 1
MODE=open OUT="" BUILD=1 SLIDERS=""
while [ $# -gt 0 ]; do
	case "$1" in
		--sheet) MODE=sheet; OUT="$(realpath -m "$2")"; shift ;;
		--clip) MODE=clip; OUT="$(realpath -m "$2")"; shift ;;
		--copy) MODE=copy; OUT="$(realpath -m "$2")"; shift ;;
		--slider) SLIDERS="$SLIDERS $2"; shift ;;
		--no-build) BUILD=0 ;;
		*) echo "unknown argument: $1" >&2; exit 2 ;;
	esac
	shift
done
. tools/lab-serve.sh
[ $MODE = open ] && refuse_agent_window tools/transfert-lab.sh
lab_serve transfert-lab.html $BUILD
[ $MODE = open ] && lab_open 1580,900

# Headless: Playwright drives the system Chrome on the GPU (tools/lab_driver.py) and steps the lab through window.transfertLabAct.
lab_frames
python3 - "$URL?clip=1" "$CHROME" "$FRAMES" "$MODE" "$SLIDERS" <<'PY' || exit 1
import sys
sys.path.insert(0, "tools")
from lab_driver import Lab
url, chrome, frames, mode, sliders = sys.argv[1:]
TOP = {"clip": {"x": 0, "y": 0, "width": 1540, "height": 520}}
with Lab(url, chrome, "transfert", viewport=(1540, 740), strict=True) as lab:
    lab.sliders(sliders)
    lab.act(0)
    seconds = float(lab.page.input_value("[data-key=seconds]"))
    hold = float(lab.page.input_value("[data-key=hold]"))
    if mode == "sheet":
        lab.act(hold + 1 / 60)
        done = 0.0
        for percent in (0, 25, 50, 75):
            lab.act(seconds * percent / 100 - done)
            done = seconds * percent / 100
            lab.shot(frames + "/p%03d.png" % percent, **TOP)
            print("p%03d: %s" % (percent, lab.text("#transfert-readout")))
        lab.act(seconds - done + 0.1)
        lab.shot(frames + "/p100.png", **TOP)
        print("p100: %s" % lab.text("#transfert-readout"))
        lab.act(hold + seconds / 2)
        lab.shot(frames + "/back050.png", **TOP)
        print("back050: %s" % lab.text("#transfert-readout"))
    elif mode == "copy":
        lab.set("pagePair", "2")
        lab.set("depthStagger", "1.2")
        lab.copy({"pagePair", "seconds", "hold", "cameraScroll", "gradeColour", "mistColour", "throughColour", "depthStagger", "patches",
                  "softEdge"}, {"pagePair": {"started": 0, "now": 2}, "depthStagger": {"started": 0.5, "now": 1.2}}, then=1,
                 pages="PurpleFairy \u2194 calm", through="white", mist="white")
        lab.page.screenshot(path=frames + "/still.png", full_page=True)
    else:
        lab.clip(frames, int(round(2 * (hold + seconds) * 30)), **TOP)
PY
if [ $MODE = sheet ]; then
	mkdir -p "$OUT" && cp "$FRAMES"/*.png "$OUT"/ && echo "stills: $OUT/{p000,p025,p050,p075,p100,back050}.png"
elif [ $MODE = copy ]; then
	cp "$FRAMES/still.png" "$OUT" && echo "still: $OUT"
else
	lab_encode "$OUT"
fi
