#!/usr/bin/env bash
# android-etc2-memory.sh [OUT] [EXTRA_ASSETS_DIR] [PAGE] — the video memory an ETC2 page saves on a real phone (r206).
# Installs tools/android-etc2-check (built as tools/android-etc2-check.sh builds it, EXTRA_ASSETS_DIR the same:
# hiver.* only) and starts it RUNS times (default 3) in each hold: none (nothing loaded), PAGE png, PAGE etc2 (PAGE
# default hiver). Each start is a fresh process holding that page on screen. The measure is the Adreno driver's own
# count of the bytes it allocated, /sys/class/kgsl/kgsl/page_alloc (system-wide, readable without root): read with
# the app stopped, then once it logs ETC2 HOLDING and has drawn for SETTLE seconds (default 4); the rise is what that
# start cost the GPU. The page's cost is a hold's median rise minus none's (the window surfaces, the batch).
# dumpsys meminfo's GL mtrack is logged too, but Samsung's memtrack leaves textures out of it (0.6 MiB with Hiver's
# 64 MiB of PNG pages held, r206). Writes OUT/memory.txt (default build/r206): every sample, each hold's median, the
# page's cost for PNG and ETC2. A physical Adreno (Qualcomm) phone only: an emulator's host driver unpacks ETC2 (r140),
# and other GPUs have no kgsl. The phone's screen must be on and unlocked, and the phone otherwise idle: page_alloc
# counts every app. Exit 1 on no device, no kgsl, or a GL error.
# headless: draws on the phone's screen, opens no window here.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=${1:-build/r206}
EXTRA=${2:-}
PAGE=${3:-hiver}
RUNS=${RUNS:-3}
SETTLE=${SETTLE:-4}
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}
ADB=$ANDROID_HOME/platform-tools/adb
PKG=jks.tools2d.parallax.etc2check
mkdir -p "$OUT"

"$ADB" get-state >/dev/null 2>&1 || { echo "no device on adb: plug in a GLES 3 phone with USB debugging on" >&2; exit 1; }
[ "$("$ADB" shell getprop ro.kernel.qemu | tr -d '\r')" = 1 ] && { echo "an emulator cannot measure this (r140)" >&2; exit 1; }
kgsl() { "$ADB" shell cat /sys/class/kgsl/kgsl/page_alloc | tr -d '\r'; }
kgsl >/dev/null 2>&1 || { echo "no readable /sys/class/kgsl/kgsl/page_alloc: not an Adreno phone" >&2; exit 1; }
./gradlew -q -p tools/android-etc2-check assembleDebug ${EXTRA:+-PextraAssets=$(realpath "$EXTRA")}
"$ADB" install -r tools/android-etc2-check/build/outputs/apk/debug/android-etc2-check-debug.apk >/dev/null

report=$OUT/memory.txt
{
	echo "device $("$ADB" shell getprop ro.product.model | tr -d '\r'), $("$ADB" shell getprop ro.board.platform | tr -d '\r'), Android $("$ADB" shell getprop ro.build.version.release | tr -d '\r')"
	echo "hold run kgsl_rise_KiB GL_mtrack_KiB EGL_mtrack_KiB total_PSS_KiB"
} >"$report"
for run in $(seq "$RUNS"); do
	for hold in none "$PAGE png" "$PAGE etc2"; do
		"$ADB" shell am force-stop $PKG
		sleep 2
		before=$(kgsl)
		"$ADB" logcat -c
		"$ADB" shell am start -W -n $PKG/.Etc2CheckLauncher --es hold "'$hold'" >/dev/null
		for _ in $(seq 60); do
			"$ADB" logcat -d -s ETC2:I AndroidRuntime:E | grep -q 'ETC2 HOLDING\|FATAL' && break
			sleep 1
		done
		"$ADB" logcat -d -s AndroidRuntime:E | grep -A20 FATAL && exit 1
		"$ADB" logcat -d -s ETC2:I | grep -q 'ETC2 HOLDING.*GL errors none' || { echo "$hold: no clean ETC2 HOLDING" >&2; exit 1; }
		sleep "$SETTLE"
		rise=$((($(kgsl) - before) / 1024))
		mem=$("$ADB" shell dumpsys meminfo $PKG | tr -d '\r')
		gl=$(awk '$1 == "GL" && $2 == "mtrack" {print $3; exit}' <<<"$mem")
		egl=$(awk '$1 == "EGL" && $2 == "mtrack" {print $3; exit}' <<<"$mem")
		pss=$(awk '/TOTAL PSS:/ {print $3; exit}' <<<"$mem")
		echo "${hold// /-} $run $rise ${gl:-0} ${egl:-0} $pss" >>"$report"
	done
done
"$ADB" shell am force-stop $PKG
python3 - "$report" "$PAGE" <<'PY'
import statistics, sys
path, page = sys.argv[1:]
rows = [l.split() for l in open(path).read().splitlines()[2:]]
med = {}
for hold in ('none', page + '-png', page + '-etc2'):
    med[hold] = [statistics.median(int(r[i]) for r in rows if r[0] == hold) for i in (2, 3, 4, 5)]
lines = ['', 'median     kgsl rise   GL mtrack   EGL mtrack   total PSS (KiB)']
for hold, (rise, gl, egl, pss) in med.items():
    lines.append(f'{hold:<12}{rise:>9.0f}{gl:>12.0f}{egl:>12.0f}{pss:>12.0f}')
png = med[page + '-png'][0] - med['none'][0]
etc2 = med[page + '-etc2'][0] - med['none'][0]
lines += ['', f'{page}: GPU memory (kgsl) over none, PNG {png / 1024:.2f} MiB, ETC2 {etc2 / 1024:.2f} MiB, '
          f'ETC2 saves {(png - etc2) / 1024:.2f} MiB ({png / etc2 if etc2 else float("inf"):.2f}x less)']
open(path, 'a').write('\n'.join(lines) + '\n')
PY
cat "$report"
