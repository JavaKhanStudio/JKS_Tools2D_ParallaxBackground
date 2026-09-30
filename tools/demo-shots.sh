#!/usr/bin/env bash
# demo-shots.sh [OUT_DIR] — stills of the demo game (r93) as a player sees it, off Simon's screen.
#
#   tools/demo-shots.sh demo/build/demo-shots
#
# Runs ParallaxDemo --shots from the demo's installDist (built first), in demo/assets as ./gradlew :demo:run does, in
# cage's headless display on a nested Xwayland (needs cage and Xwayland). The demo presses its own keys at fixed steps:
# calm-day, calm-fading and calm-sunset (SPACE), winter and spring (ENTER, SPACE), one-night and one-night-tinted
# (ENTER, N), purple-fairy (ENTER). With python3 and Pillow, contact.png puts them on one sheet. ATELIER_NO_OFFSCREEN=1
# runs it in a window instead.
set -u
cd "$(dirname "$0")/.."
OUT="$(realpath -m "${1:-demo/build/demo-shots}")"
mkdir -p "$OUT"
./gradlew -q :demo:installDist >/dev/null || exit 1
RUN="cd demo/assets && ../build/install/demo/bin/demo --shots \"$OUT\" >\"$OUT/demo.log\" 2>&1"
if [ "${ATELIER_NO_OFFSCREEN:-0}" = 1 ]; then
	bash -c "$RUN"
else
	inner="Xwayland :9 -geometry 1280x720 & X=\$!; sleep 2; DISPLAY=:9 bash -c '$RUN'; kill \$X 2>/dev/null"
	WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 300 cage -- bash -c "$inner" >"$OUT/cage.log" 2>&1
fi
ls "$OUT"/purple-fairy.png >/dev/null 2>&1 || { echo "no shots:"; tail -20 "$OUT/demo.log"; exit 1; }
python3 - "$OUT" <<'PY' || echo "no contact sheet (python3 + Pillow)"
import os, sys
from PIL import Image
out = sys.argv[1]
names = ['calm-day', 'calm-fading', 'calm-sunset', 'winter', 'spring', 'one-night', 'one-night-tinted', 'purple-fairy']
w, h = 640, 360
sheet = Image.new('RGB', (w * 2, h * 4), 'black')
for i, n in enumerate(names):
    sheet.paste(Image.open(os.path.join(out, n + '.png')).convert('RGB').resize((w, h)), ((i % 2) * w, (i // 2) * h))
sheet.save(os.path.join(out, 'contact.png'))
print('contact sheet: ' + os.path.join(out, 'contact.png'))
PY
