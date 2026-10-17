#!/usr/bin/env bash
# r425 (Simon at 20:26): proves tools/shadow-lab.sh --copy sees a wrong sun behind you and wrong rays. Puts in, one at
# a time: the ground's shadows laid toward the sun's side (groundOf's x turned round); the layers in front of the sun
# left out of the light it sends (every ray as if nothing hid it); and for Simon at 23:35: the sun over the far peaks
# (drawn after l0, as bac21de had it); the relief left out; the relief lighting the flanks away from the sun; and for
# Simon at 11:08 (the light of the hour): the light left off A; the brightness even over the sun's height; the shadows as
# dark at sunrise as at noon (no direct share); no night blue; the faces turned to a low sun lit as level ground (no
# golden hour); the moon always full. Fails unless --copy fails each on its own check.
# ONLY=<words>: runs only the mutants whose name holds them. Restores the file on exit.
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
	if [ -n "${ONLY:-}" ] && [[ "$1" != *"$ONLY"* ]]; then cp "$SAVED" "$LAB"; return; fi
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
mutate 's/			lightUp(true);/			lightUp(false);/' '			lightUp(false);'
run "the light left off A" "the light is not on A"
mutate 's|			float s = clamp((log10(lux) - LOG_NIGHT)|			float s = sunHeight > 0 ? clamp(0.77f + 0.23f * sunHeight / fullFrom) : clamp((log10(lux) - LOG_NIGHT)|' 'clamp(0.77f + 0.23f'
run "the brightness even over the sun's height" "no linear fashion"
mutate 's/cut(sunShade, sunHeight, darkness \* sunDirect,/cut(sunShade, sunHeight, darkness * 0.9f * sunLux,/' 'darkness \* 0.9f \* sunLux'
run "the shadows not by the direct share" "the shadows by the direct share"
mutate 's/			float seen = nightBlue \* smooth(/			float seen = 0 * smooth(/' 'float seen = 0 \* smooth('
run "no night blue" "night blue is not on A at night"
mutate 's/			float sunLit = behind ? sunLux \* table(LIGHT_FACING, sunHeight) \* smooth(sunHeight \/ 3) : sunDirect;/			float sunLit = sunDirect;/' 'float sunLit = sunDirect;'
run "no golden hour" "the golden hour is not warm"
mutate 's|moon = moonDisc(phase \* MONTH / 64);|moon = moonDisc(MONTH / 2);|' 'moonDisc(MONTH / 2)'
run "the moon always full" "the moon's phase"
# Leave a build of the lab as it is.
./gradlew -q :core:browserTestWar >/dev/null 2>&1
exit $status
