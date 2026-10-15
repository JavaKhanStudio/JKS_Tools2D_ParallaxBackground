"""A film of a page's strips scrolling, with animated layers playing their loops: a mock in Python, not the reader (r375).

    python3 tools/r375-animated-layer/film.py STRIPS_DIR OUT.mp4 --layers l0,l1,l2,l3 --speeds 0.008,0.02,0.04,0.08 \
        [--anim l1=build/r375/sea] [--fps 30] [--anim-fps 16] [--seconds 8]

Each layer is its <name>_cut.png, tiled along X and stacked in strip coordinates, --lifts px higher.
An --anim layer draws OUT/f*.png in turn, at --anim-fps, under their own alpha (rekey.py) or the cut's. The front layer moves 4 px a frame;
the others in proportion to their speed. Writes OUT.mp4 (ffmpeg) and OUT-moments.png (four stills).
"""
import argparse
import glob
import os
import subprocess
import tempfile

import numpy as np
from PIL import Image


def own_alpha(img, cut):
    """A frame with its own alpha (rekey.py) keeps it; an RGB one takes the still cut's."""
    if img.mode == "RGBA":
        return np.asarray(img, np.float32) / 255
    return np.concatenate([np.asarray(img.convert("RGB"), np.float32) / 255, cut[..., 3:]], 2)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("strips")
    ap.add_argument("out")
    ap.add_argument("--layers", default="l0,l1,l2,l3")
    ap.add_argument("--speeds", default="0.008,0.02,0.04,0.08")
    ap.add_argument("--lifts", default="0,0,0,0", help="px each layer is drawn higher (the page's decal Y)")
    ap.add_argument("--anim", action="append", default=[], help="NAME=FRAMES_DIR")
    ap.add_argument("--sky", default="4f8fd0,cfe4f2")
    ap.add_argument("--fps", type=int, default=30)
    ap.add_argument("--anim-fps", type=float, default=16)
    ap.add_argument("--seconds", type=float, default=8)
    a = ap.parse_args()
    names = a.layers.split(",")
    speeds = [float(s) for s in a.speeds.split(",")]
    lifts = [int(s) for s in a.lifts.split(",")]
    anims = dict(x.split("=", 1) for x in a.anim)
    layers = []
    for n in names:
        cut = np.asarray(Image.open(os.path.join(a.strips, f"{n}_cut.png")).convert("RGBA"), np.float32) / 255
        frames = [cut]
        if n in anims:
            frames = [own_alpha(Image.open(f), cut) for f in sorted(glob.glob(os.path.join(anims[n], "f*.png")))]
        layers.append(frames)
    h, w = layers[0][0].shape[:2]
    top, bottom = (np.array([int(c[i:i + 2], 16) for i in (0, 2, 4)], np.float32) / 255 for c in a.sky.split(","))
    sky = (top[None, :] * (1 - np.linspace(0, 1, h))[:, None] + bottom[None, :] * np.linspace(0, 1, h)[:, None])
    sky = np.repeat(sky[:, None], w, 1)
    count = int(a.seconds * a.fps)
    stills = []
    with tempfile.TemporaryDirectory() as tmp:
        for k in range(count):
            t = k / a.fps
            img = sky.copy()
            for frames, speed, lift in zip(layers, speeds, lifts):
                f = frames[int(t * a.anim_fps) % len(frames)]
                shift = round(k * 4 * speed / max(speeds)) % w
                f = np.roll(f, -shift, 1)
                if lift:
                    f = np.concatenate([f[lift:], np.zeros_like(f[:lift])])
                img = img * (1 - f[..., 3:]) + f[..., :3] * f[..., 3:]
            frame = Image.fromarray((img * 255 + 0.5).astype(np.uint8))
            frame.save(os.path.join(tmp, f"{k:05d}.png"))
            if k in (0, count // 4, count // 2, 3 * count // 4):
                stills.append(frame)
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-framerate", str(a.fps), "-i",
                        os.path.join(tmp, "%05d.png"), "-pix_fmt", "yuv420p", "-crf", "18", a.out], check=True)
    sheet = Image.new("RGB", (w, h * len(stills)))
    for i, s in enumerate(stills):
        sheet.paste(s, (0, h * i))
    sheet.save(os.path.splitext(a.out)[0] + "-moments.png")


if __name__ == "__main__":
    main()
