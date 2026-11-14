#!/usr/bin/env bash
# The depth lab (r444): a page turned sideways, each layer a card as far from the camera as its speed says, drawn by the
# real reader compiled with GWT, in WebGL; the ground ruled in front distances; drag to turn, the wheel to come closer,
# a button to slide between the game's 2D frame and the 3D view.
#   tools/depth-lab.sh                 the lab: a Chrome window, served until it closes
#   tools/depth-lab.sh --shot F.png    one still of the canvas, headless (4 s in)
#   tools/depth-lab.sh --pages DIR     one still per page, headless: DIR/p0.png ..., p0-seen.png ... (the cards as the
#                                      camera sees them), p0-phone.png ... (a 390px phone); fails when two tags cover
#                                      each other or the stage cuts one, there and turned round the page (r449)
#   tools/depth-lab.sh --angles DIR    three stills of the first page (r458): DIR/straight.png, side.png, steep.png; then
#                                      turned to 54 angles in each unit: fails when a tag covers another or the stage
#                                      cuts one, or no card's tag shows (an empty view)
#   tools/depth-lab.sh --clip F.mp4    a clip, headless: the slide from 2D into 3D, a turn round the page and back,
#                                      12 s at 30 fps, stepped at game speed (window.depthLabAct)
#   tools/depth-lab.sh --seam DIR      the check that the 3D view at 0 is the game's frame: every page drawn by the
#                                      heart (&flat=1), through the cards at 0 and through their 3D pictures
#                                      (&pictures=1, r503), and back at 0 after 3D, compared; fails on a difference or a page warning.
#                                      DIR keeps its stills and diffs on a failure too; tools/r507-seam/repeat.sh repeats it under load
#   tools/depth-lab.sh --big           before the rest: the canvas at 1280 x 720 (marketing captures)
#   tools/depth-lab.sh --slider K=V    before --shot/--pages/--clip: start control K (its data-key) at V, a choice at its option's index
# Everything headless draws without multisampling (&noaa=1, DEPTH_AA=1 keeps it): in headless Chrome on the NVIDIA GPU it loses the sky or a
# layer at random frames (r506, r507, r508; tools/r508-stills counts it), so a still's lines are a little harder than the window's.
# Builds with ./gradlew :core:browserTestWar first (--no-build skips it). Needs Chrome, python3 and, headless, Python
# Playwright, numpy, Pillow and ffmpeg.
# on-screen: with no argument this is the depth lab, a window Simon asked for; the rest is headless Chrome.
set -u
cd "$(dirname "$0")/.." || exit 1
MODE=open OUT="" BUILD=1 SLIDERS="" BIG=0
while [ $# -gt 0 ]; do
	case "$1" in
		--shot) MODE=shot; OUT="$(realpath -m "$2")"; shift ;;
		--clip) MODE=clip; OUT="$(realpath -m "$2")"; shift ;;
		--pages) MODE=pages; OUT="$(realpath -m "$2")"; shift ;;
		--angles) MODE=angles; OUT="$(realpath -m "$2")"; shift ;;
		--seam) MODE=seam; OUT="$(realpath -m "$2")"; shift ;;
		--slider) SLIDERS="$SLIDERS $2"; shift ;;
		--big) BIG=1 ;;
		--no-build) BUILD=0 ;;
		*) echo "unknown argument: $1" >&2; exit 2 ;;
	esac
	shift
done
. tools/lab-serve.sh
[ $MODE = open ] && refuse_agent_window tools/depth-lab.sh
lab_serve depth-lab.html $BUILD
[ $MODE = open ] && lab_open

# Headless: Playwright drives the system Chrome on the GPU (tools/r444-depth/drive.py), steps the lab through window.depthLabAct and
# screenshots its canvas, one frame per 1/30 s of game time, so the clip plays at game speed.
lab_frames
python3 tools/r444-depth/drive.py "$URL" "$CHROME" "$FRAMES" "$MODE" "$BIG" "$SLIDERS"
DRIVER=$?
if [ $MODE = pages ] || [ $MODE = seam ] || [ $MODE = angles ]; then
	# A failing run keeps its stills too: they are what it failed on (r507).
	mkdir -p "$OUT" && find "$FRAMES" -maxdepth 1 -name '*.png' -exec cp -t "$OUT" {} + && echo "stills: $OUT"
	exit $DRIVER
fi
[ $DRIVER = 0 ] || exit $DRIVER
if [ $MODE = shot ]; then
	cp "$FRAMES/still.png" "$OUT" && echo "still: $OUT"
else
	lab_encode "$OUT" 18
fi
