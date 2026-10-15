"""r398: where a SEQUENCE's joins fall in a film frame, by matching the pines' outline against its strips'.

    python3 tools/r398-seq-seams/find_join.py STRIPS_DIR FRAME.png OUT.png [LAYER_TOP_PX]

The strips (l3_cut.png, l3v*_cut.png) are 1024 px wide and drawn 1280 px wide here (scale 1.25), their bottom at the
frame's. A column's outline is its first row with alpha > 128 (strip) or luminance under 60 (frame). The shift that
best matches, each side of a join taking the strip that fits it best (the cycle picks a variant a slot), marks each join with a red tick and saves a 4x crop of the first one,
rows H-180..H-60 (the R1 forest's tip on the edge), its tick at the top."""
import glob
import sys

from PIL import Image, ImageDraw

strips = [Image.open(p).convert('RGBA') for p in sorted(glob.glob(sys.argv[1] + '/l3*_cut.png'))]
frame = Image.open(sys.argv[2]).convert('RGB')
W, H = frame.size
scale = 1.25
lum = frame.convert('L').load()
band = range(H - round(strips[0].height * scale), H)
seen = [next((y for y in band if lum[x, y] < 60), H) for x in range(W)]


def outline(img):
    a = img.resize((round(img.width * scale), round(img.height * scale))).getchannel('A').load()
    h = round(img.height * scale)
    return [H - h + next((y for y in range(h) if a[x, y] > 128), h) for x in range(round(img.width * scale))]


outlines = [outline(img) for img in strips]
n = len(outlines[0])


def fit(o, xs, shift):
    return sum(abs(seen[x] - o[(x - shift) % n]) for x in xs)


best = None
for shift in range(0, n, 2):  # a join at frame x = shift: the variant before it on the left, another after
    left, right = range(0, shift, 4), range(shift, W, 4)
    err = min(fit(o, left, shift) for o in outlines) + min(fit(o, right, shift) for o in outlines)
    if best is None or err < best[0]:
        best = (err, shift)
err, shift = best
joins = [x for x in range(shift % n, W, n)] + ([shift % n - n] if shift % n - n >= 0 else [])
print(f'shift {shift}, mean outline error {err / (W / 4):.1f} px, joins at x {joins}')
d = ImageDraw.Draw(frame)
for x in joins:
    d.line([(x, H - 330), (x, H - 300)], fill=(255, 0, 0), width=2)
frame.save(sys.argv[3])
if joins:
    x = joins[0]
    crop = Image.open(sys.argv[2]).convert('RGB').crop((max(0, x - 60), H - 180, min(W, x + 60), H - 60))
    ImageDraw.Draw(crop).line([(x - max(0, x - 60), 0), (x - max(0, x - 60), 4)], fill=(255, 0, 0), width=1)
    crop.resize((crop.width * 4, crop.height * 4), Image.NEAREST).save(sys.argv[3].replace('.png', '-zoom.png'))
