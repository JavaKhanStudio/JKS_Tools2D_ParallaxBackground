"""Do an ai-page.sh --animate page's round stills show its patch moving (r395)? Against a twin held on frame 0.

    python3 tools/r395-animate/moves.py SHOTS HELD_SHOTS OUT.png

SHOTS: tools/parallax-lab-shots.sh's stills of the page (OUT/shots), HELD_SHOTS: the same page's with the patch's
animFps set to 0 (its frame 0 at every time: moves.sh makes them). Prints, per time, the share of the frame's pixels
that differ by more than 8/255 and their mean step: 0 at 0 s (frame 0 both), more at 6 and 12 s, and a different
place at 6 than at 12 (two frames). Writes OUT.png: each time's still over its difference in red. Exit 1 when the
6 s or 12 s still does not differ from the held one, or both differ in the same pixels.
"""
import sys

import numpy as np
from PIL import Image

shots, held, out = sys.argv[1:4]
rows, masks = [], {}
for t in (0, 6, 12):
    a = np.asarray(Image.open(f"{shots}/a01-t{t}.png").convert("RGB"), np.int16)
    b = np.asarray(Image.open(f"{held}/a01-t{t}.png").convert("RGB"), np.int16)
    diff = np.abs(a - b).max(-1)
    masks[t] = diff > 8
    print(f"t{t}: {masks[t].mean() * 100:.2f} % of the pixels differ, mean step {diff[masks[t]].mean() if masks[t].any() else 0:.1f}")
    show = a.astype(np.uint8).copy()
    show[masks[t]] = [255, 0, 0]
    rows.append(np.concatenate([a.astype(np.uint8), show], 1))
Image.fromarray(np.concatenate(rows, 0)).resize((1280, 1080)).save(out)
apart = (masks[6] ^ masks[12]).mean() * 100
print(f"6 s against 12 s: {apart:.2f} % of the pixels differ in one only")
bad = masks[6].mean() < 0.001 or masks[12].mean() < 0.001 or apart < 0.001
print("FAIL" if bad else "MOVES")
sys.exit(1 if bad else 0)
