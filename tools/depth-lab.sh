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
#                                      heart (&flat=1) and through the cards at 0, compared; fails on a difference
#   tools/depth-lab.sh --big           before the rest: the canvas at 1280 x 720 (marketing captures)
#   tools/depth-lab.sh --slider K=V    before --shot/--pages/--clip: start control K (its data-key) at V, a choice at its option's index
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
if [ $MODE = open ]; then . tools/agent-window-guard.sh; refuse_agent_window tools/depth-lab.sh; fi

CHROME="${CHROME:-$(command -v google-chrome || command -v chromium || command -v chromium-browser)}"
[ -n "$CHROME" ] || { echo "no Chrome found: set CHROME=" >&2; exit 2; }
WAR="$PWD/core/build/browserTest/war"
if [ $BUILD = 1 ]; then
	./gradlew -q :core:browserTestWar >/dev/null || exit 1
fi
[ -f "$WAR/depth-lab.html" ] || { echo "$WAR has no depth-lab.html: run without --no-build" >&2; exit 2; }

PORT=$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1])')
python3 -m http.server "$PORT" --bind 127.0.0.1 --directory "$WAR" >/dev/null 2>&1 &
SERVER=$!
PROFILE=$(mktemp -d)
trap 'kill $SERVER 2>/dev/null; rm -rf "$PROFILE"' EXIT
URL="http://127.0.0.1:$PORT/depth-lab.html"
for _ in $(seq 50); do python3 -c "import urllib.request; urllib.request.urlopen('$URL')" 2>/dev/null && break; sleep 0.1; done

if [ $MODE = open ]; then
	FLAGS=(--user-data-dir="$PROFILE" --no-first-run --no-default-browser-check)
	# As tools/browser-test.sh --open: the board's server has no DISPLAY, point Chrome at the session's Wayland socket.
	if [ -z "${DISPLAY:-}${WAYLAND_DISPLAY:-}" ] && [ -S "${XDG_RUNTIME_DIR:-/run/user/$(id -u)}/wayland-0" ]; then
		export XDG_RUNTIME_DIR="${XDG_RUNTIME_DIR:-/run/user/$(id -u)}" WAYLAND_DISPLAY=wayland-0
		FLAGS+=(--ozone-platform=wayland)
	fi
	echo "serving $URL until the Chrome window closes"
	START=$SECONDS
	"$CHROME" "${FLAGS[@]}" --new-window --window-size=1580,800 "$URL" 2>&1 | grep -E 'ERROR|FATAL' >&2
	[ $((SECONDS - START)) -ge 5 ] || { echo "Chrome closed at once: no window (see its errors above)" >&2; exit 1; }
	exit 0
fi

# Headless: Playwright drives the system Chrome on SwiftShader, steps the lab through window.depthLabAct and screenshots
# its canvas, one frame per 1/30 s of game time, so the clip plays at game speed.
FRAMES=$(mktemp -d)
trap 'kill $SERVER 2>/dev/null; rm -rf "$PROFILE" "$FRAMES"' EXIT
python3 tools/r444-depth/drive.py "$URL" "$CHROME" "$FRAMES" "$MODE" "$BIG" "$SLIDERS" || exit 1
if [ $MODE = pages ] || [ $MODE = seam ] || [ $MODE = angles ]; then
	mkdir -p "$OUT" && cp "$FRAMES"/*.png "$OUT"/ && echo "stills: $OUT"
elif [ $MODE = shot ]; then
	cp "$FRAMES/still.png" "$OUT" && echo "still: $OUT"
else
	ffmpeg -loglevel error -y -framerate 30 -i "$FRAMES/f%04d.png" -vf "pad=ceil(iw/2)*2:ceil(ih/2)*2" \
		-c:v libx264 -pix_fmt yuv420p -crf 18 "$OUT" && echo "clip: $OUT"
fi
