#!/usr/bin/env bash
# The haze lab (r215, doubt d17): the haze round's h01 with towers (an EMPTY layer, drawn by the lab's hook) and snow (a
# PARTICLES layer) behind its mist, drawn four times by the real reader, compiled with GWT, in WebGL: A as the depth haze
# ships (towers and snow not hazed, the haze's white untinted), B the towers and snow hazed too, C the haze's white
# times the page's tint, D both. Sliders: the mist's haze, the tint and its strength, the scroll, the towers' brightness.
#   tools/haze-lab.sh                 the lab: a Chrome window, served until it closes
#   tools/haze-lab.sh --shot F.png    one still, headless (6 s into the scroll)
#   tools/haze-lab.sh --same F.png    checks the lab's drawing, headless: with &same=1, B to D draw as A does, without
#                                     the snow (random in each panel), and must match A; F is the still
#   tools/haze-lab.sh --copy F.png    checks "Copy settings", headless: moves the haze and the tint, clicks, fails unless
#                                     the JSON names every slider and the two moved; F is the page
# Builds with ./gradlew :core:browserTestWar first (--no-build skips it). Needs Chrome, python3 and, headless, Python
# Playwright and Pillow.
# on-screen: with no argument this is the haze lab, a window Simon asked for (d17); the other modes are headless Chrome.
set -u
cd "$(dirname "$0")/.." || exit 1
MODE=open OUT="" BUILD=1
while [ $# -gt 0 ]; do
	case "$1" in
		--shot) MODE=shot; OUT="$(realpath -m "$2")"; shift ;;
		--same) MODE=same; OUT="$(realpath -m "$2")"; shift ;;
		--copy) MODE=copy; OUT="$(realpath -m "$2")"; shift ;;
		--no-build) BUILD=0 ;;
		*) echo "unknown argument: $1" >&2; exit 2 ;;
	esac
	shift
done
. tools/lab-serve.sh
[ $MODE = open ] && refuse_agent_window tools/haze-lab.sh
lab_serve haze-lab.html $BUILD
[ $MODE = open ] && lab_open

# Headless: Playwright drives the system Chrome on the GPU (tools/lab_driver.py) and steps the lab through window.hazeLabAct.
QUERY="?clip=1"
[ $MODE = same ] && QUERY="?clip=1&same=1"
python3 - "$URL$QUERY" "$CHROME" "$OUT" "$MODE" <<'PY' || exit 1
import sys, json
sys.path.insert(0, "tools")
from lab_driver import Lab
url, chrome, out, mode = sys.argv[1:]
# --same compares panels pixel for pixel: wide enough that lab.css draws the canvas 1:1, no resampling between rows.
with Lab(url, chrome, "haze", viewport=(1600 if mode == "same" else 1540, 760), strict=True) as lab:
    if mode == "copy":
        lab.set("depthHaze", "0.4")
        lab.set("pageTint", "2")
        lab.copy({"depthHaze", "pageTint", "tintStrength", "cameraScroll", "towersLight"},
                 {"depthHaze": {"started": 0.25, "now": 0.4}, "pageTint": {"started": 0, "now": 2}}, tint="night blue")
    else:
        lab.act(6)
    print(lab.text("#haze-readout"))
    lab.shot(out)
    if mode == "same":
        # Where the canvas is in the still, and its size in GL pixels: lab.css may scale it (r232), the panels with it.
        # And where each panel's title chip ends, in GL pixels from the canvas top (r541: lab.css's chips outgrew a
        # fixed 30-pixel band, and the check compared A's longer chip with B's picture).
        canvas = lab.page.evaluate("""() => { const c = document.querySelector('canvas'), r = c.getBoundingClientRect();
            const sy = r.height / c.height;
            const chips = [...document.querySelectorAll('.panel-label')].map(e => (e.getBoundingClientRect().bottom - r.top) / sy);
            return [r.left + scrollX, r.top + scrollY, r.width / c.width, sy, chips]; }""")
        with open(out + ".canvas", "w") as f:
            json.dump(canvas, f)
PY
echo "still: $OUT"
[ $MODE = same ] || exit 0
# The panels are 760 x 250 GL pixels, 4 apart (HazeLab, FogLab's sizes), their title chips over their tops; where the
# canvas sits, its scale and where the chips end come from the page (STILL.canvas), not from the layout of the day
# (r319: r232's kit moved them, and a fixed crop failed a lab that drew right; r541: the chips grew past a fixed 30).
# Under the lowest chip and 3 px in from the panels' edges (the scaling blends the gap in), no pixel may differ from
# A's by more than 2/255, or the lab's drawing is not the reader's.
python3 - "$OUT" <<'PY'
import json, math, sys
from PIL import Image, ImageChops
im = Image.open(sys.argv[1]).convert("RGB")
left, top, sx, sy, chips = json.load(open(sys.argv[1] + ".canvas"))
W, H, GAP, IN = 760, 250, 4, 3
if len(chips) != 4:
    sys.exit("expected 4 panel titles, the page has %d" % len(chips))
# Each chip's bottom within its own panel's row, the lowest of them for every panel: A and the one compared are cut alike.
TITLE = math.ceil(max(b - (H + GAP) * (b >= H + GAP) for b in chips)) + 1
def box(col, row):
    x0, y0 = left + col * (W + GAP) * sx, top + row * (H + GAP) * sy
    return im.crop((round(x0 + IN), round(y0 + TITLE * sy), round(x0 + W * sx - IN), round(y0 + H * sy - IN)))
a = box(0, 0)
worst = 0
for name, (col, row) in {"B": (1, 0), "C": (0, 1), "D": (1, 1)}.items():
    diff = ImageChops.difference(a, box(col, row)).convert("L")
    hist = diff.histogram()
    off = sum(hist[3:])
    mean = sum(i * n for i, n in enumerate(hist)) / (a.width * a.height)
    print("%s vs A: %d px differ by more than 2/255, mean %.3f/255 (%d x %d compared)" % (name, off, mean, a.width, a.height))
    worst = max(worst, off)
sys.exit("the lab's one-layer-at-a-time drawing is not the reader's" if worst > 0 else 0)
PY
