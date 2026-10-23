#!/usr/bin/env python3
"""compare-parallax-frames.py ROUND_DIR OUT_DIR THRESHOLD ENGINE — another engine's stills against libGDX's, pixel by pixel.

Reads OUT_DIR/gdx/<scene>-t0/6/12.png (tools/parallax-lab-shots.sh) and OUT_DIR/<engine>/ the same names, writes
OUT_DIR/compare.png (libGDX | engine | difference x4, per still) and OUT_DIR/report.txt, and exits 1 when a still's
mean absolute difference per channel (0-255) is over THRESHOLD, or, when round.json gives a "pixelsOff" (percent), when
more of its pixels than that differ by over 32: what a small thing drawn wrong moves (r370's lurker, 0.07% of a still).
SKIP_SCENES="s12 s13 ..." (and SKIP_WHY, the task that will draw them) leaves scenes the engine does not draw yet out
of the verdict and the sheet, each named SKIPPED in report.txt (engines/threejs/tools/frame-check.sh, r39).
engines/godot/tools/frame-check.sh (ENGINE godot) and
engines/jme/tools/frame-check.sh (ENGINE jme) run it. Needs Pillow.
"""
import json
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageStat

round_dir, out, threshold, engine = sys.argv[1], sys.argv[2], float(sys.argv[3]), sys.argv[4]
label_of = {'godot': 'Godot', 'jme': 'jME'}.get(engine, engine)
with open(os.path.join(round_dir, 'round.json')) as f:
    round_json = json.load(f)
scenes, pixels_off = round_json['scenes'], round_json.get('pixelsOff')
skipped = set(os.environ.get('SKIP_SCENES', '').split())
skip_why = os.environ.get('SKIP_WHY', '')
worst_big = 0.0
w, h, label = 426, 240, 22
sheet = Image.new('RGB', (w * 3, (h + label) * max(1, sum(s['id'] not in skipped for s in scenes)) * 3), 'black')
draw = ImageDraw.Draw(sheet)
lines, worst, row = [], 0.0, 0
for s in scenes:
    if s['id'] in skipped:
        lines.append(f"{s['id']}  SKIPPED: not drawn by {label_of} yet" + (f' ({skip_why})' if skip_why else ''))
        continue
    for t in (0, 6, 12):
        name = f"{s['id']}-t{t}.png"
        gdx_path, other_path = os.path.join(out, 'gdx', name), os.path.join(out, engine, name)
        if not os.path.exists(other_path):
            lines.append(f'{name}  MISSING from {label_of}'); worst = 999; continue
        gdx, other = Image.open(gdx_path).convert('RGB'), Image.open(other_path).convert('RGB')
        if gdx.size != other.size:
            lines.append(f'{name}  size {gdx.size} vs {other.size}'); worst = 999; continue
        diff = ImageChops.difference(gdx, other)
        mean = sum(ImageStat.Stat(diff).mean) / 3
        big = sum(diff.convert('L').histogram()[33:]) / (gdx.width * gdx.height) * 100
        worst = max(worst, mean)
        worst_big = max(worst_big, big)
        lines.append(f"{name}  mean diff {mean:.2f} / 255   pixels off by >32: {big:.2f}%"
                     f"   {s.get('name', s.get('about', ''))}")
        y = row * (h + label)
        caption = f"{name}  {s.get('name', '')}   mean diff {mean:.2f}   (libGDX | {label_of} | difference x4)"
        draw.text((6, y + 5), caption, fill='white')
        # A still shot after a resize (portrait) keeps its aspect ratio in its cell.
        for col, im in enumerate((gdx, other, diff.point(lambda v: min(255, v * 4)))):
            im = im.copy(); im.thumbnail((w, h))
            sheet.paste(im, (col * w + (w - im.width) // 2, y + label + (h - im.height) // 2))
        row += 1
sheet.save(os.path.join(out, 'compare.png'))
verdict = 'PASS' if worst <= threshold and (pixels_off is None or worst_big <= pixels_off) else 'FAIL'
lines.append(f'{verdict}: worst mean diff {worst:.2f} (threshold {threshold:.1f})'
             + ('' if pixels_off is None else f', worst pixels off by >32 {worst_big:.3f}% (at most {pixels_off}%)'))
with open(os.path.join(out, 'report.txt'), 'w') as f:
    f.write('\n'.join(lines) + '\n')
print('\n'.join(lines))
print('compare sheet: ' + os.path.join(out, 'compare.png'))
sys.exit(0 if verdict == 'PASS' else 1)
