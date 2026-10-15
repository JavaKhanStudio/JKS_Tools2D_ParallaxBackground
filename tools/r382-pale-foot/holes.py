"""r382: where a layer.py change bites the mass, per strip: the painted strip and three cuts over magenta, side by side,
cropped round the largest hole in the solid zone (sil.py's mask shrunk 8 px) that THIRD opens where REF was opaque.

    python3 tools/r382-pale-foot/holes.py OUT.png ROUND FIRST SECOND THIRD CELL/STRIP... [--ref first|second]

ROUND holds the painted pages (ROUND/CELL/strips/STRIP_ai.png, _mask.png); FIRST, SECOND and THIRD are cut folders
(tools/r364-ghosts/recut.sh's OUTs, or one of a page's own _cut.png linked as CELL/STRIP.png). r382 ran R1's cut, HEAD's
and the fix's: --ref first shows what the fix still loses against R1, --ref second what it opens that HEAD kept.
"""
import argparse
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "r239-ai-strips"))
import layer


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("round")
    ap.add_argument("cuts", nargs=3)
    ap.add_argument("strips", nargs="+")
    ap.add_argument("--ref", choices=["first", "second"], default="first")
    a = ap.parse_args()
    rows = []
    for cs in a.strips:
        cell, s = cs.split("/")
        cuts = [np.asarray(Image.open(os.path.join(d, cell, s + ".png")).convert("RGBA")).astype(float) for d in a.cuts]
        ai = np.asarray(Image.open(os.path.join(a.round, cell, "strips", s + "_ai.png")).convert("RGB")).astype(float)
        m = np.asarray(Image.open(os.path.join(a.round, cell, "strips", s + "_mask.png")).convert("L")
                       .resize(ai.shape[1::-1])) > 127
        ref = cuts[0 if a.ref == "first" else 1]
        lost = layer.spread(m, -8) & (ref[..., 3] >= 128) & (cuts[2][..., 3] < 128)
        label, sizes = layer.components(lost)
        if sizes:
            ys, xs = np.nonzero(label == int(np.argmax(sizes)) + 1)
            cy, cx = int(ys.mean()), int(xs.mean())
        else:
            cy, cx = ai.shape[0] // 2, ai.shape[1] // 2
        y0, x0 = max(cy - 60, 0), min(max(cx - 160, 0), ai.shape[1] - 320)
        tiles = [ai] + [c[..., :3] * c[..., 3:] / 255 + np.array([255, 0, 255]) * (1 - c[..., 3:] / 255) for c in cuts]
        rows.append(np.concatenate([t[y0:y0 + 120, x0:x0 + 320] for t in tiles], 1))
        print(f"{cs}\tlargest hole {max(sizes, default=0)} px at x={cx} y={cy}")
    im = Image.fromarray(np.concatenate(rows, 0).astype(np.uint8))
    im.resize((im.width * 2, im.height * 2), Image.NEAREST).save(a.out)


if __name__ == "__main__":
    main()
