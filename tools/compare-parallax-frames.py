#!/usr/bin/env python3
"""compare-parallax-frames.py ROUND_DIR OUT_DIR THRESHOLD ENGINE — another engine's stills against libGDX's, pixel by pixel.

Reads OUT_DIR/gdx/<scene>-t0/6/12.png (tools/parallax-lab-shots.sh) and OUT_DIR/<engine>/ the same names, writes
OUT_DIR/compare.png (libGDX | engine | difference x4, per still) and OUT_DIR/report.txt, and exits 1 when a still's
mean absolute difference per channel (0-255) is over THRESHOLD. tools/godot-parallax-shots.sh (ENGINE godot) and
tools/jme-parallax-shots.sh (ENGINE jme) run it. Needs Pillow.
"""
import json
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageStat

round_dir, out, threshold, engine = sys.argv[1], sys.argv[2], float(sys.argv[3]), sys.argv[4]
label_of = {'godot': 'Godot', 'jme': 'jME'}.get(engine, engine)
with open(os.path.join(round_dir, 'round.json')) as f:
    scenes = json.load(f)['scenes']
w, h, label = 426, 240, 22
sheet = Image.new('RGB', (w * 3, (h + label) * len(scenes) * 3), 'black')
draw = ImageDraw.Draw(sheet)
lines, worst, row = [], 0.0, 0
for s in scenes:
    for t in (0, 6, 12):
        name = '%s-t%d.png' % (s['id'], t)
        gdx_path, other_path = os.path.join(out, 'gdx', name), os.path.join(out, engine, name)
        if not os.path.exists(other_path):
            lines.append('%s  MISSING from %s' % (name, label_of)); worst = 999; continue
        gdx, other = Image.open(gdx_path).convert('RGB'), Image.open(other_path).convert('RGB')
        if gdx.size != other.size:
            lines.append('%s  size %s vs %s' % (name, gdx.size, other.size)); worst = 999; continue
        diff = ImageChops.difference(gdx, other)
        mean = sum(ImageStat.Stat(diff).mean) / 3
        big = sum(1 for p in diff.convert('L').getdata() if p > 32) / (gdx.width * gdx.height) * 100
        worst = max(worst, mean)
        lines.append('%s  mean diff %.2f / 255   pixels off by >32: %.2f%%   %s' % (name, mean, big, s.get('name', s.get('about', ''))))
        y = row * (h + label)
        draw.text((6, y + 5), '%s  %s   mean diff %.2f   (libGDX | %s | difference x4)' % (name, s.get('name', ''), mean, label_of), fill='white')
        # A still shot after a resize (portrait) keeps its aspect ratio in its cell.
        for col, im in enumerate((gdx, other, diff.point(lambda v: min(255, v * 4)))):
            im = im.copy(); im.thumbnail((w, h))
            sheet.paste(im, (col * w + (w - im.width) // 2, y + label + (h - im.height) // 2))
        row += 1
sheet.save(os.path.join(out, 'compare.png'))
verdict = 'PASS' if worst <= threshold else 'FAIL'
lines.append('%s: worst mean diff %.2f (threshold %.1f)' % (verdict, worst, threshold))
with open(os.path.join(out, 'report.txt'), 'w') as f:
    f.write('\n'.join(lines) + '\n')
print('\n'.join(lines))
print('compare sheet: ' + os.path.join(out, 'compare.png'))
sys.exit(0 if verdict == 'PASS' else 1)
