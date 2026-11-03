"""r493: four ideas from a Substance breakdown (80.lv, Radu Sircu), tried as baked stills on paintings already on disk.
Nothing here is wired into a page: it makes the sheets a yes or no is judged from.

    ~/ComfyUI/venv/bin/python tools/r493-material-ideas/try.py OUT [--warp-paint]

OUT/ao.png         a depth page's painting shaded by occlusion from its depth map, then quantised
OUT/ao-strip.png   the same from a strip's own brightness: an AI strip has no depth map
OUT/wear.png       a cut strip's edge eaten by a wrapped blurred scribble (alpha only, so no colour is invented)
OUT/moss.png       dabs scattered on a strip, kept mostly in its hollows
OUT/warp.png       a silhouette init warped, and with --warp-paint its gen.py repaint (GPU), same seed and prompt
Blurs wrap in x, so a strip's loop stays closed.
"""
import argparse
import os
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont
from scipy import ndimage as ni

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
STRIPS = os.path.join(ROOT, "tools", "r239-ai-strips")
sys.path.insert(0, STRIPS)
import ai_page  # noqa: E402

RIVER = os.path.join(ROOT, "build/ai/depth/river/cut")
R3 = os.path.join(os.path.dirname(ROOT), "JKS_Tools2D_ParallaxAI", "build", "rounds", "r3", "postapo-d70", "strips")
SKY = np.array([224, 180, 138], float)
# PIL's default font sets its letters by whole pixels: at its size they run together ("cut stripas made", r499).
TITLE_FONT = "/usr/share/fonts/open-sans/OpenSans-Semibold.ttf"
TITLE_SIZE = 20
TITLE_BAND = 34
THEME = "post-apocalyptic ruined city, dusty orange haze, desolate"
MODEL = ["--ckpt", "DreamShaperXL_Turbo_v2_1.safetensors", "--lora", "StorybookRedmondV2_XL.safetensors",
         "--lora-strength", "0.9", "--w", "1536", "--h", "576", "--steps", "8", "--cfg", "2", "--sampler",
         "dpmpp_sde"]


def load(p, mode="RGB"):
    return np.asarray(Image.open(p).convert(mode), float)


def blur(a, s):
    return ni.gaussian_filter(a, s, mode=("nearest", "wrap"))


def noise(shape, s, seed):
    """Smooth noise in 0..1, wrapped in x, flat in histogram."""
    n = blur(np.random.default_rng(seed).standard_normal(shape), s)
    return np.argsort(np.argsort(n, axis=None)).reshape(shape) / n.size


def cavity(h, scales=(3, 9, 27)):
    """How far each pixel lies under what surrounds it (0..1): the cheap form of ambient occlusion, several sizes."""
    c = sum(np.maximum(blur(h, s) - h, 0) for s in scales) / len(scales)
    return np.clip(c / max(np.percentile(c, 99), 1e-6), 0, 1)


def over(rgba, sky=SKY):
    a = rgba[..., 3:] / 255
    return rgba[..., :3] * a + sky * (1 - a)


def sheet(path, panels, cols=1, crop=None, scale=1):
    """Titled panels, each the same crop at the same scale, for judging at a glance."""
    ims = []
    for title, a in panels:
        a = np.clip(a, 0, 255).astype(np.uint8)
        im = Image.fromarray(a if a.ndim == 3 else np.stack([a] * 3, -1))
        im = im.crop(crop) if crop else im
        ims.append((title, im.resize((im.width * scale, im.height * scale), Image.LANCZOS if scale == 1 else Image.NEAREST)))
    w, h = ims[0][1].size
    rows = (len(ims) + cols - 1) // cols
    out = Image.new("RGB", (cols * w + 6 * (cols - 1), rows * (h + TITLE_BAND)), "white")
    d = ImageDraw.Draw(out)
    font = ImageFont.truetype(TITLE_FONT, TITLE_SIZE)
    for i, (title, im) in enumerate(ims):
        if d.textlength(title, font=font) > w - 8:
            raise ValueError(f"title wider than its panel ({w} px): {title}")
        x, y = (i % cols) * (w + 6), (i // cols) * (h + TITLE_BAND)
        d.text((x + 6, y + TITLE_BAND // 2), title, fill="black", font=font, anchor="lm")
        out.paste(im, (x, y + TITLE_BAND))
    out.save(path)


def shade(img, c, k):
    return img * (1 - k * c[..., None])


def ao(out):
    img, depth = load(f"{RIVER}/painting.png"), load(f"{RIVER}/depth.png", "L")
    c = cavity(depth)
    q = np.round(c * 3) / 3  # the breakdown's "quantized grayscale": a few flat steps
    sheet(f"{out}/ao.png", [("A: with occlusion from the depth map, 0.5", shade(img, c, 0.5)), ("painting as made", img),
                            ("occlusion, quantised to 4 greys, 0.5", shade(img, q, 0.5)), ("the occlusion mask", 255 * (1 - c))],
          cols=2)


def ao_strip(out):
    rgba = load(f"{R3}/l2_cut.png", "RGBA")
    lum = rgba[..., :3].mean(-1)
    c = cavity(lum, (2, 6, 18)) * (rgba[..., 3] / 255)
    done = rgba.copy()
    done[..., :3] = shade(rgba[..., :3], c, 0.5)
    sheet(f"{out}/ao-strip.png", [("A: shaded from its own brightness, 0.5", over(done)), ("cut strip as made", over(rgba)),
                                  ("the mask", 255 * (1 - c))], crop=(0, 240, 800, 576))


def wear(out):
    rgba = load(f"{R3}/l3_cut.png", "RGBA")
    a = rgba[..., 3] / 255
    h, w = a.shape
    scribble = noise((h, w), 2.0, 1) * 0.6 + noise((h, w), 9.0, 2) * 0.4
    inner = blur(a, 4.0)  # 1 well inside the mass, about 0.5 on its edge
    keep = np.clip((inner - 0.5 - 0.42 * (scribble - 0.5) * 2 * (1 - inner) * 2 - 0.02) / 0.06 + 0.5, 0, 1)
    done = rgba.copy()
    done[..., 3] = 255 * a * np.where(inner > 0.97, 1, keep)
    seam = np.abs(done[:, 0, 3].astype(float) - done[:, -1, 3].astype(float)).mean()
    print(f"wear: alpha lost {100 * (1 - done[..., 3].sum() / rgba[..., 3].sum()):.2f}%, column seam {seam:.1f}/255")
    sheet(f"{out}/wear.png", [("A: edge eaten by a blurred scribble", over(done)), ("cut strip as made", over(rgba))],
          crop=(0, 300, 500, 576), scale=2)


def moss(out):
    rgba = load(f"{R3}/l2_cut.png", "RGBA")
    h, w = rgba.shape[:2]
    c = cavity(rgba[..., :3].mean(-1), (2, 6, 18))
    rng = np.random.default_rng(3)
    dabs = np.zeros((h, w))
    dabs[rng.integers(0, h, w * 3), rng.integers(0, w, w * 3)] = 1
    dabs = np.clip(blur(dabs, 1.6) * 16, 0, 1)
    m = (dabs * np.clip(c * 2.2, 0, 1) * (noise((h, w), 30.0, 4) > 0.35))[..., None]
    done = rgba.copy()
    done[..., :3] = rgba[..., :3] * (1 - 0.75 * m) + np.array([86, 104, 48.0]) * 0.75 * m
    sheet(f"{out}/moss.png", [("A: dabs kept in the hollows", over(done)), ("cut strip as made", over(rgba))],
          crop=(0, 240, 800, 576))


def warp_paint(init, out, seed):
    kinds = ["mountains", "ruins", "ruins", "ruins"]
    prompt = (f"2d game parallax background layer, {ai_page.theme_for(THEME, 'ruins', kinds)[0]}, middle distance "
              f"{ai_page.WORDS['ruins']}, painterly, plain pale sky")
    cmd = [ai_page.PY, os.path.join(STRIPS, "gen.py"), "img2img", *MODEL, "--denoise", "0.70", "--negative",
           ai_page.negative(ai_page.NEG, "ruins"), "--prompt", prompt, "--init", init, "--tile", "x", "--seed", str(seed),
           "--out", out]
    if not os.path.exists(out):
        subprocess.run(cmd, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


def warp(out, paint):
    init = load(f"{R3}/l2_init.png")
    h, w = init.shape[:2]
    dx, dy = ((noise((h, w), 22.0, s) - 0.5) * 2 * 14 for s in (5, 6))
    yy, xx = np.mgrid[:h, :w]
    warped = np.stack([ni.map_coordinates(init[..., i], [np.clip(yy + dy, 0, h - 1), (xx + dx) % w], order=1, mode="grid-wrap")
                       for i in range(3)], -1)
    panels = [("init as made", init), ("init warped 14 px", warped)]
    if paint:
        Image.fromarray(warped.astype(np.uint8)).save(f"{out}/warp-init.png")
        Image.fromarray(init.astype(np.uint8)).save(f"{out}/plain-init.png")
        for seed in (13023, 13024):
            warp_paint(f"{out}/plain-init.png", f"{out}/plain-{seed}.png", seed)
            warp_paint(f"{out}/warp-init.png", f"{out}/warp-{seed}.png", seed)
            panels += [(f"painted from the plain init, seed {seed}", load(f"{out}/plain-{seed}.png")),
                       (f"painted from the warped init, seed {seed}", load(f"{out}/warp-{seed}.png"))]
    sheet(f"{out}/warp.png", panels, cols=2, crop=(0, 150, 1000, 576))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--warp-paint", action="store_true")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    ao(a.out)
    ao_strip(a.out)
    wear(a.out)
    moss(a.out)
    warp(a.out, a.warp_paint)


if __name__ == "__main__":
    main()
