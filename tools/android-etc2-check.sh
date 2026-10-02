#!/usr/bin/env bash
# android-etc2-check.sh [OUT] [EXTRA_ASSETS_DIR] — an ETC2 atlas on Android (r140): builds tools/android-etc2-check, runs
# it on the connected device or, with none, on the atelier-phone emulator booted in cage (tools/offscreen.sh: the GPU is
# the host's, translated; Simon took the emulator as the proof, r140), and pulls into OUT (default build/r140):
# results.txt (GL version, each page's textures, every GL error while loading and drawing, PASS/FAIL), <page>-png.png and
# <page>-etc2.png (the same frame from the PNG atlas and the ETC2 one), and r140-png-vs-etc2.png (both and their
# difference x4, with the PSNR printed). EXTRA_ASSETS_DIR adds exports to City's (core/test-data/etc2): the editor's
# Hiver (editor/build/r125/out there, hiver.* only: a second city.* clashes).
# Needs ANDROID_HOME (default ~/Android/Sdk) with platforms;android-36 and build-tools;35.0.0, and Pillow.
# An emulator proves the page loads and draws and that no GL error is left; it does not prove the memory: its
# translator hands ETC2 to a desktop driver, which unpacks it to RGBA. Exit 1 on FAIL.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=${1:-build/r140}
EXTRA=${2:-}
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
ADB=$ANDROID_HOME/platform-tools/adb
PKG=jks.tools2d.parallax.etc2check
mkdir -p "$OUT"

./gradlew -q -p tools/android-etc2-check assembleDebug ${EXTRA:+-PextraAssets=$(realpath "$EXTRA")}

EMU=
if ! "$ADB" get-state >/dev/null 2>&1; then
	# -no-window alone finds no display for the host renderer, and swiftshader_indirect crashes at boot here (r140).
	tools/offscreen.sh "$ANDROID_HOME/emulator/emulator" -avd "${AVD:-atelier-phone}" -no-window -no-audio -no-boot-anim \
		-no-snapshot -gpu host >"$OUT/emulator.log" 2>&1 &
	EMU=$!
	trap '[ -n "$EMU" ] && "$ADB" emu kill >/dev/null 2>&1; wait "$EMU" 2>/dev/null || true' EXIT
	timeout 300 "$ADB" wait-for-device
	until [ "$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ]; do sleep 3; done
fi

"$ADB" install -r tools/android-etc2-check/build/outputs/apk/debug/android-etc2-check-debug.apk >/dev/null
"$ADB" logcat -c
"$ADB" shell am start -W -n $PKG/.Etc2CheckLauncher >/dev/null
for _ in $(seq 120); do
	"$ADB" logcat -d -s ETC2:I AndroidRuntime:E | grep -q 'ETC2 DONE\|FATAL' && break
	sleep 1
done
"$ADB" logcat -d -s AndroidRuntime:E | grep -A20 FATAL && exit 1
"$ADB" exec-out run-as $PKG cat files/results.txt >"$OUT/results.txt"
cat "$OUT/results.txt"
for page in $("$ADB" exec-out run-as $PKG ls -1 files | tr -d '\r' | sed -n 's/-png\.png$//p'); do
	for mode in png etc2; do
		"$ADB" exec-out run-as $PKG cat "files/$page-$mode.png" >"$OUT/$page-$mode.png"
	done
done
python3 - "$OUT" <<'PY'
import glob, math, os, sys
from PIL import Image, ImageChops, ImageDraw, ImageStat
out = sys.argv[1]
rows = []
for png in sorted(glob.glob(os.path.join(out, '*-png.png'))):
    page = os.path.basename(png)[:-len('-png.png')]
    a = Image.open(png).convert('RGB')
    b = Image.open(os.path.join(out, page + '-etc2.png')).convert('RGB')
    d = ImageChops.difference(a, b)
    mse = sum(x * x for x in ImageStat.Stat(d).rms) / 3
    psnr = 10 * math.log10(255 ** 2 / mse) if mse else float('inf')
    print(f'{page}: PNG vs ETC2 frame, PSNR {psnr:.1f} dB')
    w, h = int(a.width * 0.4), int(a.height * 0.4)
    row = Image.new('RGB', (w * 3, h + 24))
    for i, im in enumerate([a, b, d.point(lambda v: min(255, v * 4))]):
        row.paste(im.resize((w, h)), (i * w, 24))
    ImageDraw.Draw(row).text((6, 6), f'{page}: PNG atlas | ETC2 atlas | difference x4, PSNR {psnr:.1f} dB', fill='white')
    rows.append(row)
if not rows:
    sys.exit('no frames pulled from the device')
sheet = Image.new('RGB', (rows[0].width, sum(r.height for r in rows)))
y = 0
for r in rows:
    sheet.paste(r, (0, y))
    y += r.height
sheet.save(os.path.join(out, 'r140-png-vs-etc2.png'))
PY
grep -q '^ETC2 PASS' "$OUT/results.txt"
