"""Cut a loop out of a clip that does not loop: the two frames most alike, far enough apart, and a cross-fade (r375).

    python3 tools/r375-animated-layer/loopcut.py RAW_DIR OUT_DIR [--skip 5] [--min 24] [--fade 6] [--mask mask.png]

Wan's VAE encodes a clip's first frame alone, so the first frames pop: --skip drops them. Among the frames left,
the pair (i, j), j - i >= --min, whose --fade frames before each are most alike (mean |step| over --mask's moving
part) is the loop: frames i .. j-1, the last --fade of them cross-faded into the --fade frames before i, so the
frame after the last is i again with no jump. Writes OUT_DIR/f000.png ... and OUT_DIR/cut.json (i, j, the cost).
"""
import argparse
import glob
import json
import os

import numpy as np
from PIL import Image


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("raw")
    ap.add_argument("out")
    ap.add_argument("--skip", type=int, default=5)
    ap.add_argument("--min", type=int, default=24)
    ap.add_argument("--fade", type=int, default=6)
    ap.add_argument("--mask")
    a = ap.parse_args()
    files = sorted(glob.glob(os.path.join(a.raw, "f*.png")))
    frames = np.stack([np.asarray(Image.open(f).convert("RGB"), np.float32) for f in files])
    n = len(frames)
    region = np.ones(frames.shape[1:3], bool) if not a.mask else np.asarray(Image.open(a.mask).convert("L")) > 5
    flat = frames[:, region]
    best = None
    for i in range(a.skip + a.fade, n):
        for j in range(i + a.min, n + 1):
            # the frames that the fade will lay over each other: j-fade..j-1 against i-fade..i-1, and j against i
            pairs = [(j - k, i - k) for k in range(1, a.fade + 1)] + ([(j, i)] if j < n else [])
            cost = float(np.mean([np.abs(flat[p] - flat[q]).mean() for p, q in pairs]))
            if best is None or cost < best[0]:
                best = (cost, i, j)
    cost, i, j = best
    loop = frames[i:j].copy()
    for k in range(1, a.fade + 1):
        w = 1 - k / (a.fade + 1)  # the last frame leans most on the frame just before i
        loop[-k] = frames[j - k] * (1 - w) + frames[i - k] * w
    os.makedirs(a.out, exist_ok=True)
    for f in glob.glob(os.path.join(a.out, "f*.png")):
        os.remove(f)
    for k, f in enumerate(loop):
        Image.fromarray((np.clip(f, 0, 255) + 0.5).astype(np.uint8)).save(os.path.join(a.out, f"f{k:03d}.png"))
    with open(os.path.join(a.out, "cut.json"), "w") as f:
        json.dump({"raw": a.raw, "frames": n, "from": i, "to": j, "cost": round(cost, 3), "fade": a.fade}, f)
    print(f"loop {i}..{j - 1} of {n} ({j - i} frames), cost {cost:.2f}")


if __name__ == "__main__":
    main()
