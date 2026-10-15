"""Shader animations a layer could take, drawn as frames to judge before any is written three times (r375).

    python3 tools/r375-animated-layer/shaders.py EFFECT[,EFFECT] STRIP.png OUT [--frames 48] [--amplitude A] [--wavelength L]

Each effect is written as its fragment shader would be: per pixel, from the pixel's own position, the clip's time t
(0..1, the loop: every time term turns a whole number of times) and the strip itself, with sines only (no hash,
no noise texture: what GdxLayerEffects' FOG does, GLSL ES 1.0), sampled with linear filtering, wrapping in X as the
layer tiles. x periods are whole numbers of cycles across the strip, so every frame still tiles.

  swell   water: the surface moves in two crossing travelling waves, sideways and up and down (WAVE moves whole
          rows sideways only), A px, wavelength L px.
  glint   water: sun glitter, specks of light on the brighter half of the water that come and go: a sharp power of
          three crossing sine waves, added where the water is already lit. A: the specks' strength (0..1).
  billow  clouds: the clouds churn in place, their pixels pushed A px by a slow warp of wavelength L, only where the
          strip is cloud (bright and grey, read from the pixel's colour, so a painted back layer needs no mask).

Two effects (swell,glint) run one after the other, each at its own numbers (--amplitude and --wavelength then
apply to none). Writes OUT/f000.png ... (RGBA when the strip has alpha) for loopcheck.py and film.py, and OUT/mask.png: where the
effect may move a pixel.
"""
import argparse
import os

import numpy as np
from PIL import Image

TAU = 2 * np.pi


def sample(img, sx, sy):
    """img at float positions, bilinear, X wrapping, Y clamped."""
    h, w = img.shape[:2]
    x0, y0 = np.floor(sx).astype(int), np.floor(sy).astype(int)
    fx, fy = (sx - x0)[..., None], (sy - y0)[..., None]
    xa, xb = x0 % w, (x0 + 1) % w
    ya, yb = np.clip(y0, 0, h - 1), np.clip(y0 + 1, 0, h - 1)
    top = img[ya, xa] * (1 - fx) + img[ya, xb] * fx
    bottom = img[yb, xa] * (1 - fx) + img[yb, xb] * fx
    return top * (1 - fy) + bottom * fy


def cycles(w, wavelength):
    """Whole cycles across the strip nearest to its width over the wavelength: the strip keeps tiling."""
    return max(1, round(w / wavelength))


def lit(rgb):
    return rgb @ np.array([0.299, 0.587, 0.114], np.float32)


def cloudiness(rgb):
    """0..1: how much a pixel reads as cloud, bright and grey or bluish (a blue sky, green hills and sunlit yellow
    fields are not)."""
    lum = lit(rgb)
    sat = rgb.max(-1) - rgb.min(-1)
    warm = rgb[..., 0] - rgb[..., 2]  # a field's yellow: red well over blue; a cloud's shade is bluer, not redder
    return (np.clip((lum - 0.6) / 0.15, 0, 1) * np.clip((0.3 - sat) / 0.15, 0, 1)
            * np.clip((0.06 - warm) / 0.06, 0, 1))


def swell(img, t, a):
    h, w = img.shape[:2]
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    k1, k2 = cycles(w, a.wavelength), cycles(w, a.wavelength * 1.7)
    p1 = TAU * (k1 * x / w + (h - y) / a.wavelength * 0.6 - t)
    p2 = TAU * (k2 * x / w - (h - y) / a.wavelength * 0.9 + 2 * t)
    dx = a.amplitude * (0.6 * np.sin(p1) + 0.4 * np.sin(p2))
    dy = a.amplitude * 0.5 * (0.6 * np.cos(p1) - 0.4 * np.cos(p2))
    return sample(img, x + dx, y + dy), np.ones((h, w), bool)


def glint(img, t, a):
    h, w = img.shape[:2]
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    rgb = img[..., :3]
    k = cycles(w, a.wavelength)
    # three waves at odd angles, each bent by a slow one so no grid shows, on a grid squashed 5 to 1 so a speck is
    # a short dash along the swell; a speck only where all three peak at once, on the lit part of the water
    k2, k3 = round(1.5 * k), 2 * k  # each a whole number of cycles across: the strip keeps tiling
    u, py = x / w, y / (a.wavelength / 5)
    bend = 0.8 * np.sin(TAU * (3 * u + py / 7 - t))
    n = (np.sin(TAU * (k * u + 1.1 * py + bend - t)) + np.sin(TAU * (k2 * u - 1.3 * py - 0.7 * bend + 2 * t + 0.4))
         + np.sin(TAU * (k3 * u + 0.9 * py + 1.3 * bend - 3 * t + 1.1)))
    spark = np.clip((n - 2.45) / 0.55, 0, 1) ** 2
    bright = np.clip((lit(rgb) - 0.45) / 0.25, 0, 1)
    out = img.copy()
    out[..., :3] = np.clip(rgb + a.amplitude * (spark * bright)[..., None], 0, 1)
    return out, bright > 0


def billow(img, t, a):
    h, w = img.shape[:2]
    y, x = np.mgrid[0:h, 0:w].astype(np.float32)
    k = cycles(w, a.wavelength)
    px, py = k * x / w, y / a.wavelength
    dx = np.sin(TAU * (px + 0.7 * np.sin(TAU * py) - t)) + 0.5 * np.sin(TAU * (2 * px - 1.3 * py + t + 0.3))
    dy = np.sin(TAU * (px * 2 + py - t + 0.6)) + 0.5 * np.sin(TAU * (px - 2 * py - 2 * t))
    # where it may move: the clouds and a margin round them, read at the pixel and at the warp's reach (a shader's
    # five reads), so a cloud's edge moves out over the sky as well as in
    c = cloudiness(img[..., :3])
    r = 2 * a.amplitude
    for ox, oy in ((r, 0), (-r, 0), (0, r), (0, -r)):
        c = np.maximum(c, cloudiness(sample(img[..., :3], x + ox, y + oy)))
    s = a.amplitude * c / 1.5
    return sample(img, x + s * dx, y + s * dy), c > 0


EFFECTS = {"swell": (swell, 3, 32), "glint": (glint, 0.8, 24), "billow": (billow, 6, 120)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("effect", help=", ".join(EFFECTS) + "; or two, comma-separated")
    ap.add_argument("strip")
    ap.add_argument("out")
    ap.add_argument("--frames", type=int, default=48)
    ap.add_argument("--amplitude", type=float)
    ap.add_argument("--wavelength", type=float)
    a = ap.parse_args()
    names = a.effect.split(",")
    for n in names:
        if n not in EFFECTS:
            ap.error(f"no effect {n}: {', '.join(EFFECTS)}")
    steps = []
    for n in names:
        fn, amp, wl = EFFECTS[n]
        own = len(names) == 1
        steps.append((fn, argparse.Namespace(amplitude=a.amplitude if own and a.amplitude is not None else amp,
                                             wavelength=a.wavelength if own and a.wavelength is not None else wl)))
    os.makedirs(a.out, exist_ok=True)
    src = Image.open(a.strip)
    mode = "RGBA" if src.mode in ("RGBA", "LA", "P") else "RGB"
    img = np.asarray(src.convert(mode), np.float32) / 255
    moving = np.zeros(img.shape[:2], bool)
    for i in range(a.frames):
        out = img
        for fn, numbers in steps:
            out, m = fn(out, i / a.frames, numbers)
            moving |= m
        Image.fromarray((np.clip(out, 0, 1) * 255 + 0.5).astype(np.uint8), mode).save(
            os.path.join(a.out, f"f{i:03d}.png"))
    Image.fromarray((moving * 255).astype(np.uint8)).save(os.path.join(a.out, "mask.png"))


if __name__ == "__main__":
    main()
