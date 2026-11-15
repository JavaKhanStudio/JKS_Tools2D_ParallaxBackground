#!/usr/bin/env bash
# The clouds lab (r451, r556): candidate cloud shaders as lab GLSL, drawn by the real reader compiled with GWT, in
# WebGL, in three settings (a Mediterranean day, a cold forest night, a medieval fantasy evening): WISPS, LOBES and PUFFS
# made in the shader, OneNight's and calm's painted clouds churned (BILLOW) and relit, and the painting still. Sliders: zoom, the camera's
# scroll, the clouds' drift, the made clouds' numbers and BILLOW's amplitude; the view on the first panel's title.
#   tools/clouds-lab.sh                 the lab: a Chrome window, served until it closes
#   tools/clouds-lab.sh --shot F.png    one still, headless (6 s in)
#   tools/clouds-lab.sh --views DIR     one still per view, headless: DIR/view0.png ...
#   tools/clouds-lab.sh --clip F.mp4    a clip, headless: 8 s at 30 fps, stepped at game speed (window.cloudsLabAct)
#   tools/clouds-lab.sh --start S       before --clip: S seconds of the lab stepped first, unseen (the clouds layer wraps
#                                       once it has drifted its width)
#   tools/clouds-lab.sh --before-wrap S before --clip: the lab stepped, unseen, to S seconds before the painted clouds' churn
#                                       wraps (their effect's start passing its period, which the readout's
#                                       data-billow-start says): tools/r451-clouds-lab/billow-loop.sh films that (r559)
#   tools/clouds-lab.sh --slider K=V    before --shot/--views/--clip: start control K (its data-key) at V, a choice at its option's index
#   tools/clouds-lab.sh --copy F.png    checks "Copy settings", headless: moves two controls, clicks, fails unless the JSON
#                                       names every control and the two moved; F is the page
# Builds with ./gradlew :core:browserTestWar first (--no-build skips it). Needs Chrome, python3 and, headless, Python
# Playwright and ffmpeg.
# on-screen: with no argument this is the clouds lab, a window Simon asked for; the rest is headless Chrome.
set -u
cd "$(dirname "$0")/.." || exit 1
MODE=open OUT="" BUILD=1 SLIDERS="" START=0 WRAP=""
while [ $# -gt 0 ]; do
	case "$1" in
		--shot) MODE=shot; OUT="$(realpath -m "$2")"; shift ;;
		--clip) MODE=clip; OUT="$(realpath -m "$2")"; shift ;;
		--copy) MODE=copy; OUT="$(realpath -m "$2")"; shift ;;
		--views) MODE=views; OUT="$(realpath -m "$2")"; shift ;;
		--slider) SLIDERS="$SLIDERS $2"; shift ;;
		--start) START="$2"; shift ;;
		--before-wrap) WRAP="$2"; shift ;;
		--no-build) BUILD=0 ;;
		*) echo "unknown argument: $1" >&2; exit 2 ;;
	esac
	shift
done
. tools/lab-serve.sh
[ $MODE = open ] && refuse_agent_window tools/clouds-lab.sh
lab_serve clouds-lab.html $BUILD
[ $MODE = open ] && lab_open

# Headless: Playwright drives the system Chrome on the GPU (tools/lab_driver.py), steps the lab through window.cloudsLabAct and
# screenshots it.
lab_frames
python3 - "$URL?clip=1" "$CHROME" "$FRAMES" "$MODE" "$SLIDERS" "$START" "$WRAP" <<'PY' || exit 1
import sys
sys.path.insert(0, "tools")
from lab_driver import Lab


def billow_start(lab):
    """The painted clouds' effect start across and its period, as the last step left them."""
    read = "e => [e.getAttribute('data-billow-start'), e.getAttribute('data-billow-period')]"
    return [float(v) for v in lab.page.eval_on_selector("#clouds-readout", read)]


def before_wrap(lab, lead, sliders, ahead, step=0.25, within=180):
    """Steps the lab to lead seconds before the painted clouds' effect start next jumps by its period. Where it jumps is
    not reckoned from its pace (the layer's own wrap at its width carries a period back too, r559): the lab is stepped
    until the start jumps, then loaded afresh with the same sliders and stepped to lead seconds before (it is
    deterministic: the same steps, the same floats). ahead: the seconds --start stepped first, stepped again."""
    lab.act(0)
    last, period = billow_start(lab)
    t = 0.0
    while t < within:
        lab.act(step)
        t += step
        now, _ = billow_start(lab)
        if abs(now - last) > period / 2:
            break
        last = now
    else:
        sys.exit(f"the painted clouds' churn did not wrap in {within} s: no wrap to film")
    print(f"painted churn: its start jumped {last:.2f} -> {now:.2f} (period {period:g}) between {t - step:g} and {t:g} s",
          file=sys.stderr)
    lab.page.reload()
    lab.page.wait_for_function("window.cloudsLabReady === true", timeout=60000)
    lab.sliders(sliders)
    lab.act(ahead)
    if t - step > lead:
        lab.act(t - step - lead)

url, chrome, frames, mode, sliders, start, wrap = sys.argv[1:]
with Lab(url, chrome, "clouds") as lab:
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
        lab.set("billowAmplitude", "0.5")
        lab.set("view", "2")
        lab.copy({"view", "zoom", "cameraScroll", "cloudDrift", "cloudCoverage", "cloudSize", "cloudSoftness", "cloudChurn", "lightAngle",
                  "toonSteps", "billowAmplitude"},
                 {"billowAmplitude": {"started": 0.3, "now": 0.5}, "view": {"started": 0, "now": 2}})
        lab.shot(frames + "/still.png")
    else:
        if float(start) > 0:
            lab.act(float(start))
        if wrap:
            before_wrap(lab, float(wrap), sliders, float(start))
        lab.clip(frames, 240)
PY
if [ $MODE = views ]; then
	mkdir -p "$OUT" && cp "$FRAMES"/view*.png "$OUT"/ && echo "stills: $OUT/view*.png"
elif [ $MODE = shot ] || [ $MODE = copy ]; then
	cp "$FRAMES/still.png" "$OUT" && echo "still: $OUT"
else
	lab_encode "$OUT"
fi
