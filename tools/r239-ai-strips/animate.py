"""ai_page.py --animate's steps (r395, docs/animated-layers.md phase 3): a cut layer's animated patch.

r375's tools paint and check the frames (tools/r375-animated-layer: anim.py with Wan 2.1 VACE, loopcut.py, rekey.py,
loopcheck.py); this module gives them the layer's moving mask and turns what passed into a patch: the frames cropped
to the box where they differ from the still, the still's alpha there lowered to the frames' least (a frond that sways
away must not leave the still's frond under the patch), and a frame rate the round's stills read off a frame change.
"""
import glob
import json
import os

import numpy as np
from PIL import Image, ImageFilter

ANIM = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "r375-animated-layer")
FREE = 0.6  # r375's second run: 2.47 a frame on the palm crowns against 2.13 held, loopcheck passing (time seam 1.06)
FPS = 16  # Wan 2.1's own rate
CHANGED = 2  # of 255, premultiplied: a frame pixel further than that from the still's is in the patch
SHOTS, STEP = (0, 6, 12), 1 / 60  # tools/parallax-lab-shots.sh's stills and ParallaxShots' step


def moving_mask(cut, path, rows=None):
    """White where the layer may move: its alpha, grown 4 px and softened (r375's crowns), only rows top..bottom
    (from the strip's top) when given."""
    a = np.asarray(Image.open(cut).convert("RGBA"))[..., 3] > 128
    m = np.asarray(Image.fromarray((a * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(9))).copy()
    if rows:
        m[:rows[0]] = 0
        m[rows[1]:] = 0
    Image.fromarray(m).filter(ImageFilter.GaussianBlur(3)).save(path)


def commands(py, d, src, cut, prompt, seed, free):
    """anim.py, loopcut.py, rekey.py and loopcheck.py on one layer, in d (d/raw: Wan's clip, d/f*.png: the loop),
    each with the name of its log beside d."""
    return [
        ([py, f"{ANIM}/anim.py", d, "--src", src, "--mask", f"{d}/moving.png", "--prompt", prompt,
          "--free", str(free), "--seed", str(seed)], f"{d}_paint.log"),
        (["python3", f"{ANIM}/loopcut.py", f"{d}/raw", d, "--mask", f"{d}/mask.png"], f"{d}_loopcut.log"),
        (["python3", f"{ANIM}/rekey.py", d, "--src", src, "--cut", cut, "--mask", f"{d}/mask.png"], f"{d}_rekey.log"),
        (["python3", f"{ANIM}/loopcheck.py", d, "--alpha", cut, "--json", f"{d}/loop.json"], f"{d}_loopcheck.log"),
    ]


def premultiplied(rgba):
    return np.concatenate([rgba[..., :3] * rgba[..., 3:] / 255, rgba[..., 3:]], -1)


def patch(d, cut, still_out):
    """The loop in d made a patch: its frames cropped to where any differs from the still (d/patch/f*.png), the
    still with its alpha there lowered to the frames' least (still_out). The box (x0, y0, x1, y1) from the strip's
    top-left, and the frame paths."""
    frames = np.stack([np.asarray(Image.open(f).convert("RGBA"), np.float32)
                       for f in sorted(glob.glob(os.path.join(d, "f*.png")))])
    still = np.asarray(Image.open(cut).convert("RGBA"), np.float32).copy()
    # anim.py moves only its mask's pixels (d/mask.png): outside it the cut's own colours may differ from the painted
    # strip's that the frames keep, and are no motion
    moves = np.asarray(Image.open(os.path.join(d, "mask.png")).convert("L")) > 5
    differs = (np.abs(premultiplied(frames) - premultiplied(still)[None]) > CHANGED).any(axis=(0, 3)) & moves
    ys, xs = np.nonzero(differs)
    x0, y0, x1, y1 = int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1
    still[y0:y1, x0:x1, 3] = np.minimum(still[y0:y1, x0:x1, 3], frames[:, y0:y1, x0:x1, 3].min(axis=0))
    Image.fromarray((still + 0.5).astype(np.uint8), "RGBA").save(still_out)
    out = os.path.join(d, "patch")
    os.makedirs(out, exist_ok=True)
    paths = []
    for i, f in enumerate(frames):
        paths.append(os.path.join(out, f"f{i:03d}.png"))
        Image.fromarray((f[y0:y1, x0:x1] + 0.5).astype(np.uint8), "RGBA").save(paths[-1])
    return (x0, y0, x1, y1), paths


def still_times():
    """The stills' times on the reader's effect clock: ParallaxShots' float32 loop, one scene (make_round.py's)."""
    step, effect, t, times = np.float32(STEP), 0.0, np.float32(0), []
    for at in SHOTS:
        while t < np.float32(at):
            effect += float(step)
            t = np.float32(t + step)
        times.append(effect)
    return times


def fps_for_stills(n, want=FPS):
    """The rate within a frame a second of want whose stills show n-frame loop's frames all different, each still
    as far from a frame change as can be (docs/animated-layers.md: two engines' clocks may round apart). At 16 fps a
    24-frame loop shows frame 0 at 0, 6 and 12 s."""
    times = still_times()

    def margin(fps):
        return min(min(t * fps % 1, 1 - t * fps % 1) for t in times[1:])  # 0 s is frame 0 in every engine

    rates = [round(want - 1 + i / 100, 2) for i in range(201)]
    apart = [r for r in rates if len({int(t * r) % n for t in times}) == len(times)] or rates
    return max(apart, key=lambda r: (margin(r), -abs(r - want)))


def verdict(d):
    """loopcheck.py's numbers, or None when it wrote none."""
    path = os.path.join(d, "loop.json")
    if not os.path.exists(path):
        return None
    with open(path) as f:
        return json.load(f)
