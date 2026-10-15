"""Does an animated layer loop? Measures a clip of frames the way seam.py measures a strip (r375).

    python3 tools/r375-animated-layer/loopcheck.py FRAMES_DIR [--alpha cut.png] [--json out.json]

FRAMES_DIR holds f000.png ... in play order; the frame after the last is the first again.

time seam  = mean |colour step| from the last frame back to the first / median |step| between neighbour frames
             inside the clip. About 1: the wrap is as smooth as the motion itself. Above 2: a jump shows.
motion     = that median step, in 0..255 per pixel of the moving part. Below 0.3: the clip barely moves.
x seam     = seam.py's ratio of every frame's right edge into its left one (a layer strip tiles in X): the worst
             frame. Its references: the still strip's own ratio, and the same ratio between neighbour columns inside
             the frames (x seam inner, the worst of 8 places a frame): sparse bright specks (a glint) spike it
             wherever they fall, so a join only fails above what the clip's own columns reach.
moving box = the box round every pixel that ever changes, and its share of the strip: what a layer would store
             frame by frame if only that part were animated (partial animation).
Only pixels opaque in --alpha (the layer's cut) count, when given.
"""
import argparse
import glob
import json
import os
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "r239-ai-strips"))
import seam


def load_frames(path):
    files = sorted(glob.glob(os.path.join(path, "f*.png")))
    if len(files) < 3:
        raise SystemExit(f"{path}: fewer than 3 frames")
    return np.stack([premultiplied(Image.open(f)) for f in files])


def premultiplied(img):
    """RGB, times the frame's own alpha when it has one (rekey.py): a pop of the alpha is a pop too."""
    rgba = np.asarray(img.convert("RGBA")).astype(np.float32)
    return rgba[..., :3] * (rgba[..., 3:] / 255 if img.mode == "RGBA" else 1)


def measure(frames, opaque=None, changed=2.0):
    n, h, w, _ = frames.shape
    keep = np.ones((h, w), bool) if opaque is None else opaque
    moved = (np.abs(frames - frames[:1]).max(axis=(0, 3)) > changed) & keep
    region = moved if moved.any() else keep

    def step(a, b):
        return float(np.abs(a[region] - b[region]).mean())

    steps = [step(frames[i], frames[i + 1]) for i in range(n - 1)]
    wrap = step(frames[-1], frames[0])
    inner = float(np.median(steps)) or 1e-6
    alpha = (keep * 255).astype(np.float32)[..., None]
    xs = [seam.step(np.concatenate([f[:, -1], alpha[:, -1]], 1), np.concatenate([f[:, 0], alpha[:, 0]], 1))
          / seam.inner(np.concatenate([f, alpha], 2)) for f in frames]
    places = [w * k // 9 for k in range(1, 9)]
    xin = [seam.step(np.concatenate([f[:, c], alpha[:, c]], 1), np.concatenate([f[:, c + 1], alpha[:, c + 1]], 1))
           / seam.inner(np.concatenate([f, alpha], 2)) for f in frames for c in places]
    ys, xs_ = np.nonzero(moved)
    box = [int(xs_.min()), int(ys.min()), int(xs_.max()) + 1, int(ys.max()) + 1] if moved.any() else None
    share = ((box[2] - box[0]) * (box[3] - box[1]) / (w * h)) if box else 0.0
    return {"frames": n, "motion": round(inner, 3), "max_step": round(max(steps), 3), "wrap_step": round(wrap, 3),
            "time_seam": round(wrap / inner, 2), "x_seam_worst": round(max(xs), 2),
            "x_seam_inner": round(max(xin), 2), "moving_box": box,
            "moving_share": round(share, 3)}


def verdict(m, still_x_seam=None):
    bad = []
    if m["motion"] < 0.3:
        bad.append("barely moves")
    if m["time_seam"] > 2.0:
        bad.append(f"jumps at the wrap ({m['time_seam']}x the motion)")
    if still_x_seam is not None and m["x_seam_worst"] > max(2.0, 1.5 * still_x_seam, m.get("x_seam_inner", 0)):
        bad.append(f"stops tiling in X (worst frame {m['x_seam_worst']}, still {still_x_seam:.2f}, "
                   f"inside the frames {m.get('x_seam_inner')})")
    return "PASS" if not bad else "FAIL: " + "; ".join(bad)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("frames")
    ap.add_argument("--alpha", help="the layer's cut (RGBA): only its opaque pixels count")
    ap.add_argument("--json")
    a = ap.parse_args()
    frames = load_frames(a.frames)
    opaque, still = None, None
    if a.alpha:
        cut = seam.load(a.alpha)
        opaque = cut[..., 3] > 128
        still = seam.step(cut[:, -1], cut[:, 0]) / seam.inner(cut)
    m = measure(frames, opaque)
    m["x_seam_still"] = None if still is None else round(still, 2)
    m["verdict"] = verdict(m, still)
    print(json.dumps(m))
    if a.json:
        with open(a.json, "w") as f:
            json.dump(m, f, indent=1)
    sys.exit(0 if m["verdict"] == "PASS" else 1)


if __name__ == "__main__":
    main()
