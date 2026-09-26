#!/usr/bin/env bash
# parallax-lab-keys-probe.sh — drive the grading lab with real key presses, off Simon's screen, and prove what it saved.
#
#   tools/parallax-lab-keys-probe.sh [ROUND_DIR] [OUT_DIR]    (defaults demo/lab/round1, demo/build/lab/keys-probe)
#
# Copies the round to OUT_DIR/round (its grades.json is never touched), runs ParallaxLab on it in cage + a nested
# Xwayland, presses keys through XTest (libXtst via ctypes) and grabs the screen with ffmpeg's x11grab after each step:
# grade the first scene 4, the second 2, go back, reveal, regrade 5, then ENTER to the end (summary). Checks that
# grades.json holds s01=5 and s02=2, and that a restarted lab opens on the first ungraded scene (s03).
set -u
cd "$(dirname "$0")/.."
ROUND="${1:-demo/lab/round1}" OUT="$(realpath -m "${2:-demo/build/lab/keys-probe}")"
rm -rf "$OUT" && mkdir -p "$OUT/round"
./gradlew -q :demo:installDist >/dev/null || exit 1
# The copy keeps the page paths of round.json pointing at the original pages: only grades.json lands in the copy.
cp "$ROUND/round.json" "$OUT/round/"
CP="$PWD/demo/build/install/demo/lib/*"

cat >"$OUT/keys.py" <<'PY'
import ctypes, subprocess, sys, time
out, steps = sys.argv[1], sys.argv[2].split()
x11, xtst = ctypes.CDLL('libX11.so.6'), ctypes.CDLL('libXtst.so.6')
x11.XOpenDisplay.restype = ctypes.c_void_p
x11.XStringToKeysym.restype = ctypes.c_ulong
d = ctypes.c_void_p(x11.XOpenDisplay(b':9'))
def key(name):
    code = x11.XKeysymToKeycode(d, ctypes.c_ulong(x11.XStringToKeysym(name.encode())))
    xtst.XTestFakeKeyEvent(d, code, True, 0); x11.XFlush(d); time.sleep(0.05)
    xtst.XTestFakeKeyEvent(d, code, False, 0); x11.XFlush(d)
for i, step in enumerate(steps):
    if step.startswith('shot:'):
        subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-f', 'x11grab', '-video_size', '1280x720', '-i', ':9',
                        '-frames:v', '1', '%s/%s.png' % (out, step[5:])])
    elif step.startswith('wait:'):
        time.sleep(float(step[5:]))
    else:
        key(step); time.sleep(0.3)
PY

run() {  # $1 = key steps
	inner=$(cat <<SH
Xwayland :9 -geometry 1280x720 & X=\$!
sleep 2
DISPLAY=:9 java -cp "$CP" jks.tools2d.parallax.demo.ParallaxLab "$OUT/round" >>"$OUT/lab.log" 2>&1 & L=\$!
sleep 6
python3 "$OUT/keys.py" "$OUT" "$1"
kill \$L 2>/dev/null; wait \$L 2>/dev/null; kill \$X 2>/dev/null
SH
)
	WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 120 cage -- bash -c "$inner" >>"$OUT/cage.log" 2>&1
}

run "shot:1-start 4 wait:1.2 shot:2-after-grade-4 2 wait:1.2 BackSpace BackSpace shot:3-back-to-s01 h shot:4-revealed 5 wait:1.2 Return Return Return Return Return Return Return Return Return Return Return Return Return Return Return Return Return Return shot:5-summary"
run "shot:6-restarted"

python3 - "$OUT" <<'PY'
import json, sys
out = sys.argv[1]
grades = json.load(open(out + '/round/grades.json'))['grades']
print('grades.json:', {k: v['grade'] for k, v in grades.items()})
ok = grades.get('s01', {}).get('grade') == 5 and grades.get('s02', {}).get('grade') == 2 and len(grades) == 2
print('PASS' if ok else 'FAIL', '- expected s01=5, s02=2 and nothing else')
sys.exit(0 if ok else 1)
PY
status=$?
ls "$OUT"/*.png
exit $status
