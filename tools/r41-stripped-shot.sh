#!/usr/bin/env bash
# r41-stripped-shot.sh — before/after of atlas regions packed with their whitespace stripped (Hiver, Printemps).
#
#   ./gradlew :demo:installDist && tools/r41-stripped-shot.sh [out-dir]
#
# Renders a saved page with ParallaxStress --plax, once as its file says (useOriginalSize false: every sample predates
# .plax format 4, so the packed image is stretched over the layer) and once with --original-size true (the region keeps
# its original size and offset, what the editor now does for new pages). Headless in cage, on a nested Xwayland; needs
# cage and Xwayland. Writes <page>-stretched.png and <page>-original.png, 1280x720.
set -u
cd "$(dirname "$0")/.."
OUT="$(realpath -m "${1:-demo/build/stress/r41}")"
mkdir -p "$OUT"
CP="$PWD/demo/build/install/demo/lib/*"
for page in hiver/Hiver printemps/Printemps; do
	name=$(basename "$page")
	for mode in stretched:false original:true; do
		inner=$(cat <<SH
Xwayland :9 -geometry 1280x720 & X=\$!
sleep 2
cd demo/assets && DISPLAY=:9 java -cp "$CP" jks.tools2d.parallax.demo.ParallaxStress --plax $page.plax --seconds 0.5 --original-size ${mode#*:} --shot "$OUT/$name-${mode%%:*}.png" >"$OUT/$name-${mode%%:*}.log" 2>&1
kill \$X 2>/dev/null
SH
)
		WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 60 cage -- bash -c "$inner" >"$OUT/cage.log" 2>&1
		ls "$OUT/$name-${mode%%:*}.png" 2>/dev/null || { echo "no shot for $name ${mode%%:*}:"; tail -20 "$OUT/$name-${mode%%:*}.log"; }
	done
done
