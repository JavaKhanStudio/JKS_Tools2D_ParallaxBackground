#!/usr/bin/env bash
# The effects labs (r224, Simon: "one lab per feature", and one with them all): EffectsLab, compiled with GWT, in WebGL.
# Each draws its page twice by the real reader, the feature on the left, the same page without it on the right.
#   --lab fog    the page fog (r217) alone, on four parallax sets: strength, colour, scroll
#   --lab snow   a PARTICLES layer's snow alone: density, flake size, wind, its layer, its drift, its anchor
#   --lab all    every effect that ships, together on s01: page fog, FOG mist, WAVE trees, a game's towers, snow
#   --lab floor  r275's floor (FloorLab): a ground drawn row by row, each row at its depth (one panel);
#                drag the picture or hold the arrow keys to pan; Hiver or PurpleFairy, the layers in front planted on the ground
#   --lab tower  r276's tower (TowerLab, r328): a brick tower in front of Hiver, turning; radius, shade, speed, images round,
#                where the camera stands (r350: close by, the courses arc and Hiver wraps round the tower)
#   --lab planet the planet (PlanetLab, r277, r347: the page on a turning world) with r330's snow and hooked towers: radius,
#                how the towers stand, snow anchor
#   --lab lurker r369's cover (LurkerLab): r363's hooded figure behind Hiver's pines, switched by the reader's sightings
#                (r370) only while LurkerCover says one of them hides it whole (or every 2 s anywhere, to compare); a strip
#                of the last 20 s
#   --lab swell  r434's SWELL edges (SwellLab): the checker tiled on X and Y, Y, neither or X, or the beach sea, through
#                SWELL as it ships (wrapping only on the axes the page repeats on), as it was before r434, and red
#                where the two differ
# The readout gives the frame time, and each panel's draw calls and CPU time.
#   tools/effects-lab.sh --lab L                 the lab: a Chrome window, served until it closes
#   tools/effects-lab.sh --lab L --shot F.png    one still, headless (6 s into the scroll)
#   tools/effects-lab.sh --lab L --copy F.png    checks "Copy settings", headless: moves the lab's first slider and the
#                                                scroll, clicks, fails unless the JSON names every slider and the two moved
#   tools/effects-lab.sh ... --slider K=V        before --shot/--copy: start control K (its data-key) at V, a choice at its option's index
# Builds with ./gradlew :core:browserTestWar first (--no-build skips it). Needs Chrome, python3 and, headless, Python
# Playwright.
# on-screen: with no --shot/--copy this is the lab, a window Simon asked for (r224); the other modes are headless Chrome.
set -u
cd "$(dirname "$0")/.." || exit 1
MODE=open OUT="" BUILD=1 LAB=fog SLIDERS=""
while [ $# -gt 0 ]; do
	case "$1" in
		--lab) LAB="$2"; shift ;;
		--shot) MODE=shot; OUT="$(realpath -m "$2")"; shift ;;
		--copy) MODE=copy; OUT="$(realpath -m "$2")"; shift ;;
		--slider) SLIDERS="$SLIDERS $2"; shift ;;
		--no-build) BUILD=0 ;;
		*) echo "unknown argument: $1" >&2; exit 2 ;;
	esac
	shift
done
case "$LAB" in fog|snow|all|floor|tower|planet|lurker|swell) ;; *) echo "--lab is fog, snow, all, floor, tower, planet, lurker or swell, not: $LAB" >&2; exit 2 ;; esac
. tools/lab-serve.sh
[ $MODE = open ] && refuse_agent_window tools/effects-lab.sh
lab_serve effects-lab.html $BUILD "lab=$LAB"
[ $MODE = open ] && lab_open

# Headless: Playwright drives the system Chrome on the GPU (tools/lab_driver.py) and steps the lab through window.effectsLabAct.
python3 - "$URL&clip=1" "$CHROME" "$OUT" "$MODE" "$LAB" "$SLIDERS" <<'PY' || exit 1
import sys
sys.path.insert(0, "tools")
from lab_driver import Lab
url, chrome, out, mode, name, sliders = sys.argv[1:]
with Lab(url, chrome, "effects", viewport=(1540, 900), strict=True) as lab:
    page = lab.page
    lab.sliders(sliders, missing="no slider %s in the " + name + " lab")
    keys = page.eval_on_selector_all("#effects-labels [data-key], #effects-controls [data-key]", "es => es.map(e => e.dataset.key)")
    if mode == "copy":
        first = page.eval_on_selector("#effects-controls input[type=range][data-key]", "e => [e.dataset.key, Number(e.dataset.start), Number(e.max)]")
        lab.set(first[0], first[2])
        scroll = page.eval_on_selector("[data-key=cameraScroll]", "e => Number(e.dataset.start)")
        lab.set("cameraScroll", 40 if scroll == 80 else 80)
        lab.copy(keys, {first[0]: {"started": first[1], "now": first[2]}, "cameraScroll": {"started": scroll, "now": 40 if scroll == 80 else 80}},
                 lab="effects-lab " + name)
    else:
        lab.act(6)
    print("sliders: %s" % " ".join(keys))
    print(lab.text("#effects-readout"))
    lab.shot(out)
PY
echo "still: $OUT"
