#!/usr/bin/env bash
# android-webp-check.sh [OUT] [IMAGE] — r523: WebP on Android, through parallax-background-webp's BitmapFactory path,
# with the TwelveMonkeys jars left out as an Android game leaves them. Builds tools/android-webp-check, runs it on the
# connected device or, with none, on the atelier-phone emulator booted in cage (tools/offscreen.sh, as
# tools/android-etc2-check.sh), and pulls into OUT (default build/r523-android): results.txt (the lossless fixture's
# bytes against its PNG, the page's frame from a PNG atlas page against its WebP twin, PASS/FAIL), page-png.png and
# page-webp.png. IMAGE (the page's picture) defaults to parallax-ai's assets/fairy/region3-loop.png beside this
# checkout, else webp/test-data/fairy-crop.png. Needs ANDROID_HOME (default ~/Android/Sdk) and Pillow. Exit 1 on FAIL.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=$(realpath -m "${1:-build/r523-android}")
IMAGE=${2:-../JKS_Tools2D_ParallaxAI/assets/fairy/region3-loop.png}
[ -f "$IMAGE" ] || IMAGE=webp/test-data/fairy-crop.png
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
ADB=$ANDROID_HOME/platform-tools/adb
PKG=jks.tools2d.parallax.webpcheck
ASSETS=$OUT/assets
rm -rf "$ASSETS"
mkdir -p "$ASSETS"
cp webp/test-data/fairy-crop.png webp/test-data/fairy-crop.webp webp/test-data/fairy-crop-lossy.webp "$ASSETS"
python3 tools/r523-webp/make_pages.py "$IMAGE" "$ASSETS"

./gradlew -q -p tools/android-webp-check assembleDebug -PwebpAssets="$ASSETS"

EMU=
if ! "$ADB" get-state >/dev/null 2>&1; then
	# As android-etc2-check.sh: -no-window alone finds no display for the host renderer (r140).
	tools/offscreen.sh "$ANDROID_HOME/emulator/emulator" -avd "${AVD:-atelier-phone}" -no-window -no-audio -no-boot-anim \
		-no-snapshot -gpu host >"$OUT/emulator.log" 2>&1 &
	EMU=$!
	trap '[ -n "$EMU" ] && "$ADB" emu kill >/dev/null 2>&1; wait "$EMU" 2>/dev/null || true' EXIT
	timeout 300 "$ADB" wait-for-device
	until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 3; done
fi

"$ADB" install -r tools/android-webp-check/build/outputs/apk/debug/android-webp-check-debug.apk >/dev/null
"$ADB" logcat -c
"$ADB" shell am start -W -n $PKG/.WebpCheckLauncher >/dev/null
for _ in $(seq 120); do
	"$ADB" logcat -d -s WEBP:I AndroidRuntime:E | grep -q 'WEBP DONE\|FATAL' && break
	sleep 1
done
"$ADB" logcat -d -s AndroidRuntime:E | grep -A20 FATAL && exit 1
"$ADB" exec-out run-as $PKG cat files/results.txt >"$OUT/results.txt"
cat "$OUT/results.txt"
for kind in png webp; do
	"$ADB" exec-out run-as $PKG cat "files/page-$kind.png" >"$OUT/page-$kind.png"
done
grep -q '^WEBP PASS' "$OUT/results.txt"
