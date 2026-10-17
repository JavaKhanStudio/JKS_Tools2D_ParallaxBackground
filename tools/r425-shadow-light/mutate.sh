#!/usr/bin/env bash
# r425 (Simon at 20:26): proves tools/shadow-lab.sh --copy sees a wrong sun behind you and wrong rays. Puts in, one at
# a time: the ground's shadows laid toward the sun's side (groundOf's x turned round); the layers in front of the sun
# left out of the light it sends (every ray as if nothing hid it); and for Simon at 23:35: the sun over the far peaks
# (drawn after l0, as bac21de had it); the relief left out; the relief lighting the flanks away from the sun. Fails
# unless --copy fails each on its own check.
# Restores the file on exit.
# headless: tools/shadow-lab.sh --copy runs headless Chrome.
set -u
cd "$(dirname "$0")/../.." || exit 1
LAB=core/browser-test/launcher/jks/tools2d/parallax/browsertest/ShadowLab.java
SAVED=$(mktemp)
cp "$LAB" "$SAVED"
trap 'cp "$SAVED" "$LAB"; rm -f "$SAVED"' EXIT
LOG=build/r425-shadow-light/mutant.log
mkdir -p build/r425-shadow-light
status=0
run() { # name, what --copy must say
	if tools/shadow-lab.sh --copy build/r425-shadow-light/still.png >"$LOG" 2>&1; then
		echo "NOT CAUGHT: $1 passes --copy"; status=1
	elif grep -q "$2" "$LOG"; then
		echo "caught: $1 ($(tail -1 "$LOG"))"
	else
		echo "FAILED ELSEWHERE: $1: $(tail -1 "$LOG")"; status=1
	fi
	cp "$SAVED" "$LAB"
}
mutate() {
	sed -i "$1" "$LAB"
	grep -q "$2" "$LAB" || { echo "mutation did not apply: $1"; exit 2; }
}
mutate 's/return new float\[\] { (float) (-Math.sin(a)/return new float[] { (float) (Math.sin(a)/' 'float) (Math.sin(a)'
run "the ground's shadows toward the sun" "do not run away from its side"
mutate 's/				drawLayers(MASK + k);/				continue;/' '				continue;'
run "nothing hides the sun's light" "the rays:"
mutate '/panel(FRAME_WIDTH + 4/,/endPanels/ s/drawLayers(DRAWN + k);/{ drawLayers(DRAWN + k); if (k == 0) suns(); }/' 'if (k == 0) suns(); }'
run "the sun over the far peaks" "the sun behind the far peaks:"
mutate 's/if (!behind || strength <= 0 || h <= 0)/if (true)/' 'if (true)'
run "no relief" "the relief: A off B"
mutate 's/(float) (Math.sin(a) \* Math.cos(up))/(float) (-Math.sin(a) * Math.cos(up))/' '(-Math.sin(a) \* Math.cos(up))'
run "the relief lighting the flanks away from the sun" "lights the flanks away"
# Leave a build of the lab as it is.
./gradlew -q :core:browserTestWar >/dev/null 2>&1
exit $status
