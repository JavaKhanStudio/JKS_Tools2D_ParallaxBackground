#!/usr/bin/env bash
# godot-parallax-shots.sh [ROUND_DIR] — the Godot reader (engines/godot) against the libGDX runtime, frame by frame (r87).
#
#   tools/godot-parallax-shots.sh                          demo/lab/round1, engines/godot/tests/conformance and /transfer
#   tools/godot-parallax-shots.sh demo/lab/round1
#
# For each round: renders its scenes with libGDX (tools/parallax-lab-shots.sh, 0, 6 and 12 s into the lab's scroll)
# and with Godot (engines/godot/tests/shots.gd, the same scroll), off screen in cage's headless display on a nested
# Xwayland, then compares them pixel by pixel. The transfer round starts a cross-fade or a tint 4 s in, so t6 is
# mid-fade and t12 after it. Writes demo/build/godot/<round>/: the libGDX stills in gdx/, Godot's in godot/,
# compare.png (libGDX | Godot | difference x4, per still) and report.txt. Fails when a still differs by more than
# THRESHOLD (mean absolute difference per channel, 0-255; default 2). Needs godot 4.x, cage, Xwayland, python3 with
# Pillow.
set -u
cd "$(dirname "$0")/.."
ROOT="$PWD"
THRESHOLD="${THRESHOLD:-2}"
ROUNDS=("$@")
[ ${#ROUNDS[@]} -eq 0 ] && ROUNDS=(demo/lab/round1 engines/godot/tests/conformance engines/godot/tests/transfer)
GODOT="${GODOT:-godot}"
status=0
for ROUND in "${ROUNDS[@]}"; do
	OUT="$ROOT/demo/build/godot/$(basename "$ROUND")"
	rm -rf "$OUT"; mkdir -p "$OUT/godot"
	tools/parallax-lab-shots.sh "$ROUND" "$OUT/gdx" >/dev/null || { echo "libGDX shots failed: $OUT/gdx/lab.log"; exit 1; }
	"$GODOT" --headless --path engines/godot --import >/dev/null 2>&1
	RUN="$GODOT --path \"$ROOT/engines/godot\" --resolution 1280x720 res://tests/shots.tscn -- \"$ROOT\" \"$ROUND\" \"$OUT/godot\" >\"$OUT/godot.log\" 2>&1"
	inner="Xwayland :9 -geometry 1280x720 & X=\$!; sleep 2; DISPLAY=:9 $RUN; kill \$X 2>/dev/null"
	WLR_BACKENDS=headless ALSOFT_DRIVERS=null timeout 300 cage -- bash -c "$inner" >"$OUT/cage.log" 2>&1
	python3 - "$ROUND" "$OUT" "$THRESHOLD" <<'PY' || status=1
import json, os, sys
from PIL import Image, ImageChops, ImageDraw, ImageStat
round_dir, out, threshold = sys.argv[1], sys.argv[2], float(sys.argv[3])
scenes = json.load(open(os.path.join(round_dir, 'round.json')))['scenes']
w, h, label = 426, 240, 22
sheet = Image.new('RGB', (w * 3, (h + label) * len(scenes) * 3), 'black')
draw = ImageDraw.Draw(sheet)
lines, worst, row = [], 0.0, 0
for s in scenes:
    for t in (0, 6, 12):
        name = '%s-t%d.png' % (s['id'], t)
        gdx_path, godot_path = os.path.join(out, 'gdx', name), os.path.join(out, 'godot', name)
        if not os.path.exists(godot_path):
            lines.append('%s  MISSING from Godot' % name); worst = 999; continue
        gdx, godot = Image.open(gdx_path).convert('RGB'), Image.open(godot_path).convert('RGB')
        if gdx.size != godot.size:
            lines.append('%s  size %s vs %s' % (name, gdx.size, godot.size)); worst = 999; continue
        diff = ImageChops.difference(gdx, godot)
        mean = sum(ImageStat.Stat(diff).mean) / 3
        big = sum(1 for p in diff.convert('L').getdata() if p > 32) / (gdx.width * gdx.height) * 100
        worst = max(worst, mean)
        lines.append('%s  mean diff %.2f / 255   pixels off by >32: %.2f%%   %s' % (name, mean, big, s.get('name', s.get('about', ''))))
        y = row * (h + label)
        draw.text((6, y + 5), '%s  %s   mean diff %.2f   (libGDX | Godot | difference x4)' % (name, s.get('name', ''), mean), fill='white')
        sheet.paste(gdx.resize((w, h)), (0, y + label))
        sheet.paste(godot.resize((w, h)), (w, y + label))
        sheet.paste(diff.point(lambda v: min(255, v * 4)).resize((w, h)), (2 * w, y + label))
        row += 1
sheet.save(os.path.join(out, 'compare.png'))
verdict = 'PASS' if worst <= threshold else 'FAIL'
lines.append('%s: worst mean diff %.2f (threshold %.1f)' % (verdict, worst, threshold))
open(os.path.join(out, 'report.txt'), 'w').write('\n'.join(lines) + '\n')
print('\n'.join(lines))
print('compare sheet: ' + os.path.join(out, 'compare.png'))
sys.exit(0 if verdict == 'PASS' else 1)
PY
done
exit $status
