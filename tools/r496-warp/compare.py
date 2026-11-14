"""r496: ruins strips painted and cut from a plain and a warped init, same seeds, through ai_page's own Pipeline.paint,
so its prompts and its cut are a real run's. One try per strip: a refusal is counted, not repainted. Resumes: a strip
already painted is only cut again.

    python3 tools/r496-warp/compare.py OUT [--seeds 10] [--amount 0.0243]    GPU, about an hour
    python3 tools/r496-warp/compare.py OUT --only-report                                       no GPU

OUT/{plain,warp}/s<seed>/strips/l<k>_*   the strips
OUT/counts.json, OUT/counts.txt          refusals, towers keyed away, seam, sky colour, per strip and per arm
OUT/sheet-l<k>.png                       the cut strips over R3's sky, plain left, warped right, a row per seed
OUT/refused-blobs.png, OUT/worst-joins.png   what creature.py refused; the highest seam ratios' joins at 4x
"""
import argparse
import json
import os
import re
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
STRIPS = os.path.join(os.path.dirname(HERE), "r239-ai-strips")
sys.path.insert(0, STRIPS)
import ai_page  # noqa: E402

# R3's postapo-d70 (../JKS_Tools2D_ParallaxAI/build/rounds/r3/postapo-d70/recipe.json), which r493 tried it on.
R3 = ["--theme", "post-apocalyptic ruined city, dusty orange haze, desolate", "--name", "postapo", "--style", "painted",
      "--kinds", "mountains,ruins,ruins,ruins", "--colours", "a08a78,7a6458,4a3c38,231c1c", "--sky", "8a6a58,e0b48a",
      "--ckpt", "DreamShaperXL_Turbo_v2_1.safetensors", "--steps", "8", "--cfg", "2", "--sampler", "dpmpp_sde",
      "--lora", "StorybookRedmondV2_XL.safetensors", "--lora-strength", "0.9", "--denoise", "0.7", "--keep"]
SKY_TOP, SKY_FOOT = np.array([0x8a, 0x6a, 0x58], float), np.array([0xe0, 0xb4, 0x8a], float)
DEPTHS = (1, 2, 3)
ARMS = ("plain", "warp")
MIN_TOWER = 0.10  # of the strip's height, over the mask's median outline: what counts as a tower
KEYED = 0.5  # a tower with under this share of its upper half opaque in the cut is keyed away


def pipeline(out, seed, arm, amount):
    sys.argv = ["ai_page", f"{out}/{arm}/s{seed}", *R3, "--seed", str(seed)]
    sys.argv += ["--warp", str(amount if arm == "warp" else 0)]  # plain is --warp 0 since ruins warp by default
    return ai_page.Pipeline(ai_page.parse_args())


def paint(out, seeds, amount):
    for seed in seeds:
        for arm in ARMS:
            p = pipeline(out, seed, arm, amount)
            for k in DEPTHS:
                p.paint(k, "ruins", seed * 1000 + k * 10, os.path.join(p.strips, f"l{k}"))


def towers(mask, alpha):
    """How many of the mask's towers the cut lost, of how many: a tower is a run of columns standing MIN_TOWER over the
    median outline, looked at in the upper half of what it stands above that line."""
    h, w = mask.shape
    top = np.argmax(mask > 0.5, axis=0)
    line = np.median(top)
    tall = top < line - MIN_TOWER * h
    start = int(np.argmin(tall)) if tall.any() and not tall.all() else 0  # from a low column: a run across the join is one
    runs, x, lost = [], 0, 0
    while x < w:
        c = (start + x) % w
        if tall[c]:
            r = []
            while x < w and tall[(start + x) % w]:
                r.append((start + x) % w)
                x += 1
            runs.append(r)
        x += 1
    for r in runs:
        crest = int(top[r].min())
        rows = np.arange(crest, int((crest + line) / 2) + 1)
        inside = mask[np.ix_(rows, r)] > 0.5
        if inside.sum() and (alpha[np.ix_(rows, r)][inside] > 128).mean() < KEYED:
            lost += 1
    return lost, len(runs)


def measure(prefix):
    code = None
    log = Path(prefix + "_cut.log").read_text()
    if "a creature?" in log:
        code = "creature"
    elif "cut flat at the top edge" in log:
        code = "top row"
    rgb = np.asarray(Image.open(prefix + "_ai.png").convert("RGB"), float)
    cut = np.asarray(Image.open(prefix + "_cut.png").convert("RGBA"), float)
    mask = np.asarray(Image.open(prefix + "_mask.png").convert("L"), float) / 255
    lost, n = towers(mask, cut[..., 3])
    seam = subprocess.run([sys.executable, os.path.join(STRIPS, "seam.py"), prefix + "_cut.png"], capture_output=True,
                          text=True, check=True).stdout
    sky = rgb[:24].reshape(-1, 3).mean(0)
    far = re.search(r"far range: (\d+) px", log)
    return {"refused": code, "far_range_px": int(far.group(1)) if far else 0, "towers": n, "keyed": lost, "seam": float(re.search(r"seam ratio ([\d.]+)", seam).group(1)),
            "alpha_step": float(re.search(r"alpha step ([\d.]+)", seam).group(1)), "sky": [round(x, 1) for x in sky],
            "sky_blue_minus_green": round(float(sky[2] - sky[1]), 1), "opaque": round(float((cut[..., 3] > 128).mean()), 4),
            "half_clear": round(float(((cut[..., 3] > 16) & (cut[..., 3] < 240)).mean()), 4),
            "roughness": round(ai_page.roughness(prefix + "_cut.png"), 2)}


def over(cut):
    h = cut.shape[0]
    t = np.linspace(0, 1, h)[:, None, None]
    sky = SKY_TOP * (1 - t) + SKY_FOOT * t
    a = cut[..., 3:] / 255
    return (cut[..., :3] * a + sky * (1 - a)).clip(0, 255).astype(np.uint8)


def sheet(out, seeds, k, rows):
    w, h = 768, 288
    top = 40  # the strips' first rows are empty sky
    pad, title = 6, 18
    im = Image.new("RGB", (2 * w + pad, (h - top + title) * len(seeds)), "white")
    d = ImageDraw.Draw(im)
    for r, seed in enumerate(seeds):
        for c, arm in enumerate(ARMS):
            cut = np.asarray(Image.open(f"{out}/{arm}/s{seed}/strips/l{k}_cut.png").convert("RGBA"), float)
            pic = Image.fromarray(over(cut)).resize((w, h), Image.LANCZOS).crop((0, top, w, h))
            x, y = c * (w + pad), r * (h - top + title)
            m = rows[arm, seed, k]
            note = f"{arm} l{k} seed {seed * 1000 + k * 10}: seam {m['seam']:.2f}, towers keyed {m['keyed']}/{m['towers']}"
            d.text((x + 4, y + 3), note + (f"  REFUSED: {m['refused']}" if m["refused"] else ""),
                   fill="red" if m["refused"] else "black")
            im.paste(pic, (x, y + title))
    im.save(f"{out}/sheet-l{k}.png")


def blobs(out, seeds):
    """Every blob creature.py refused, cropped from its painting, to see whether it is a figure."""
    found = []
    for arm in ARMS:
        for seed in seeds:
            for k in DEPTHS:
                log = Path(f"{out}/{arm}/s{seed}/strips/l{k}_cut.log").read_text()
                for m in re.finditer(r"a creature\? (\d+) px blob at x=(\d+) y=(\d+)", log):
                    found.append((arm, seed, k, *map(int, m.groups())))
    size, cols = 200, 8
    im = Image.new("RGB", (cols * (size + 4), (len(found) + cols - 1) // cols * (size + 18)), "white")
    d = ImageDraw.Draw(im)
    for i, (arm, seed, k, n, x, y) in enumerate(found):
        strip = np.asarray(Image.open(f"{out}/{arm}/s{seed}/strips/l{k}_ai.png").convert("RGB"))
        y0 = max(0, min(strip.shape[0] - 100, y - 50))
        crop = np.take(strip[y0:y0 + 100], range(x - 50, x + 50), axis=1, mode="wrap")
        px, py = (i % cols) * (size + 4), (i // cols) * (size + 18)
        d.text((px + 2, py + 2), f"{arm} s{seed} l{k} {n}px", fill="red" if arm == "warp" else "black")
        im.paste(Image.fromarray(crop).resize((size, size), Image.LANCZOS), (px, py + 18))
    im.save(f"{out}/refused-blobs.png")


def joins(out, rows, n=4):
    """The n highest seam ratios' loop joins at 4x over the page's sky, the join in each panel's middle."""
    worst = sorted(rows, key=lambda r: -rows[r]["seam"])[:n]
    im = Image.new("RGB", (n * 490, 978), "white")
    d = ImageDraw.Draw(im)
    for i, (arm, seed, k) in enumerate(worst):
        cut = Image.open(f"{out}/{arm}/s{seed}/strips/l{k}_cut.png").convert("RGBA")
        w, h = cut.size
        pair = Image.new("RGBA", (120, 240), (224, 180, 138, 255))
        pair.alpha_composite(cut.crop((w - 60, h - 240, w, h)), (0, 0))
        pair.alpha_composite(cut.crop((0, h - 240, 60, h)), (60, 0))
        d.text((i * 490 + 2, 2), f"{arm} s{seed} l{k}: seam {rows[arm, seed, k]['seam']:.2f}", fill="black")
        im.paste(pair.convert("RGB").resize((480, 960), Image.NEAREST), (i * 490, 18))
    im.save(f"{out}/worst-joins.png")


def report(out, seeds):
    rows = {(arm, seed, k): measure(f"{out}/{arm}/s{seed}/strips/l{k}") for arm in ARMS for seed in seeds for k in DEPTHS}
    lines = []
    for arm in ARMS:
        ms = [m for (a, _, _), m in rows.items() if a == arm]
        lines.append(f"{arm}: {len(ms)} strips, refused creature {sum(m['refused'] == 'creature' for m in ms)}, "
                     f"top row {sum(m['refused'] == 'top row' for m in ms)}; towers keyed away {sum(m['keyed'] for m in ms)} of "
                     f"{sum(m['towers'] for m in ms)}; seam mean {np.mean([m['seam'] for m in ms]):.2f} max "
                     f"{max(m['seam'] for m in ms):.2f}; alpha step max {max(m['alpha_step'] for m in ms):.1f}; "
                     f"sky blue-green mean {np.mean([m['sky_blue_minus_green'] for m in ms]):.1f}; opaque mean "
                     f"{np.mean([m['opaque'] for m in ms]):.3f}; half clear mean {np.mean([m['half_clear'] for m in ms]):.3f}; "
                     f"far range kept half clear on {sum(m['far_range_px'] > 0 for m in ms)} strips, mean "
                     f"{np.mean([m['far_range_px'] for m in ms]):.0f} px; "
                     f"outline roughness mean {np.mean([m['roughness'] for m in ms]):.2f}")
    text = "\n".join(lines)
    print(text)
    with open(f"{out}/counts.txt", "w") as f:
        f.write(text + "\n")
    with open(f"{out}/counts.json", "w") as f:
        json.dump({f"{a}/s{s}/l{k}": m for (a, s, k), m in rows.items()}, f, indent=1)
    for k in DEPTHS:
        sheet(out, seeds, k, rows)
    blobs(out, seeds)
    joins(out, rows)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--seeds", type=int, default=10)
    ap.add_argument("--amount", type=float, default=0.0243, help="r493's 14 px of 576")
    ap.add_argument("--only-report", action="store_true")
    a = ap.parse_args()
    out = os.path.abspath(a.out)
    seeds = list(range(13, 13 + a.seeds))
    if not a.only_report:
        paint(out, seeds, a.amount)
    report(out, seeds)


if __name__ == "__main__":
    main()
