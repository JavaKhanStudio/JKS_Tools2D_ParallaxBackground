"""The baseline an AI sea has to beat: a SHADER layer's WAVE (Enum_ShaderEffect), already in the library, as frames (r375).

    python3 tools/r375-animated-layer/wave.py STRIP_cut.png OUT [--amplitude 3] [--wavelength 24] [--frames 32]

Each row is shifted sideways by amplitude * sin(2 pi (y - speed t) / wavelength), y from the image's bottom, as
GdxLayerEffects' WAVE does (sampled here with linear filtering, wrapping in X as the layer tiles). The speed is one
wavelength per clip, so the frames loop. Writes OUT/f000.png ... in RGB, for loopcheck.py and film.py.
"""
import argparse
import os

import numpy as np
from PIL import Image


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("cut")
    ap.add_argument("out")
    ap.add_argument("--amplitude", type=float, default=3)
    ap.add_argument("--wavelength", type=float, default=24)
    ap.add_argument("--frames", type=int, default=32)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    img = np.asarray(Image.open(a.cut).convert("RGB"), np.float32)
    h, w = img.shape[:2]
    x = np.arange(w)
    for i in range(a.frames):
        t = i / a.frames
        out = np.empty_like(img)
        for row in range(h):
            y = h - 1 - row
            sx = x + a.amplitude * np.sin(2 * np.pi * (y / a.wavelength - t))
            x0 = np.floor(sx).astype(int)
            f = (sx - x0)[:, None]
            out[row] = img[row, x0 % w] * (1 - f) + img[row, (x0 + 1) % w] * f
        Image.fromarray((out + 0.5).astype(np.uint8)).save(os.path.join(a.out, f"f{i:03d}.png"))


if __name__ == "__main__":
    main()
