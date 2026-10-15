"""r397: a05's libGDX stills before the fix (build/r397/before-gdx, from mutate.sh's core mutant) over after
(build/jme/animated/gdx, a frame check's), at 0, 6 and 12 s, cropped to the front hill's flags. Writes build/r397/sheet.png.
Run from the repository root after tools/r397-shader-patch/mutate.sh and engines/jme/tools/frame-check.sh."""
from pathlib import Path

from PIL import Image, ImageDraw

ROWS = [('before r397: the patch through the hill\'s region', Path('build/r397/before-gdx')),
        ('after: through its frame\'s region', Path('build/jme/animated/gdx'))]
BOX = (0, 300, 1280, 720)


def main():
    w, h, label = BOX[2] - BOX[0], BOX[3] - BOX[1], 28
    sheet = Image.new('RGB', (w * 3 // 2, (h // 2 + label) * len(ROWS)), 'white')
    draw = ImageDraw.Draw(sheet)
    for r, (text, folder) in enumerate(ROWS):
        y = r * (h // 2 + label)
        draw.text((8, y + 6), text, fill='black', font_size=20)
        for c, t in enumerate((0, 6, 12)):
            still = Image.open(folder / f'a05-t{t}.png').convert('RGB').crop(BOX).resize((w // 2, h // 2))
            sheet.paste(still, (c * w // 2, y + label))
    sheet.save('build/r397/sheet.png')


if __name__ == '__main__':
    main()
