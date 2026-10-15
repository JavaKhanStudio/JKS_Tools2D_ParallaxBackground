"""make.py FILM OUT.png — README's lurker size picture (r372): the r363 figure at the height its depth gives it, then
the same layer at the size of parallax-ai R1's accident, a giant.

    tools/r363-reveal/film.sh build/r363
    python3 tools/r372-lurker-size/make.py build/r363/film docs/lurker-size.png

Reads FILM/frames (24 fps from 12 s on): v01 at 28 s (whole, in a gap) and v02 at 23.5 s, each cropped to the band of
the view the pines stand in, labelled, stacked.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

FPS, START = 24, 12.0
BAND = (0, 0, 1280, 440)  # the pines and the figure; the empty white below is left out
WIDTH = 720
# Short labels in a large font: README shows the picture at a phone's 360 px too.
SHOTS = (('v01', 28.0, 'Right: 1.8 units, as its depth makes it'),
         ('v02', 23.5, 'Wrong: 11 units, a giant'))
LABEL_H = 44


def main(film, out):
    tiles = []
    for sid, t, label in SHOTS:
        im = Image.open(os.path.join(film, 'frames', f'{sid}-f{round((t - START) * FPS):04d}.png')).convert('RGB')
        band = im.crop(BAND)
        band = band.resize((WIDTH, round(band.height * WIDTH / band.width)), Image.LANCZOS)
        tile = Image.new('RGB', (WIDTH, band.height + LABEL_H), 'black')
        tile.paste(band, (0, LABEL_H))
        ImageDraw.Draw(tile).text((10, 6), label, fill='white', font=ImageFont.load_default(size=30))
        tiles.append(tile)
    sheet = Image.new('RGB', (WIDTH, sum(t.height for t in tiles)), 'black')
    y = 0
    for tile in tiles:
        sheet.paste(tile, (0, y))
        y += tile.height
    sheet.save(out, optimize=True)


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
