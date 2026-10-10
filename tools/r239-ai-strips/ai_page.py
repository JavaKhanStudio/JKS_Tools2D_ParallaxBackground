#!/usr/bin/env python3
"""From a theme to a page (r246): paint the layers with a local ComfyUI, cut them, pack an atlas, write a
.jplax by the parallax-pages skill's rules, lint it and render it.

    tools/ai-page.sh --theme "snowy pine valley at dawn" --layers 4 --style painted build/ai/snow
    tools/ai-page.sh --theme "..." --colours 8a93a8,5d7a8c,1c2e33 --sky 6d8fc4,dfe6ee OUT

Passes, each one r239's (docs/ai-parallax.md): sil.py draws a shape that loops for each depth, back to front
(mountains far, hills between, the front --front: pines or hills); gen.py paints it with --tile x; painted, the front
layer gets --variants more strips that start and end on its columns and becomes a SEQUENCE; layer.py (painted) or
pixel.py (pixel, SDXL + PixelArt_XL) keys the sky out. A strip whose art reaches its top row is painted again on the
next seed, up to --tries times.

The page, from the skill (.claude/skills/parallax-pages, steps 2, 3, 5, 6) and r217's fog:
  - speeds: the front at --front-speed, each layer behind it half as fast (x2.0, the top of the graded ratios), the
    back one (mountains, nothing to compare) at most a tenth of the front's;
  - placement: the front at the screen's bottom, each farther layer's bottom edge sunk under the solid band of the one
    in front of it, measured on the cut layers;
  - the layers are painted at full contrast and the page's depth fog gives the distance: fogStrength is set so the
    back layer is --fog of the way to the fog colour, the painted sky at the horizon;
  - one gradient for the whole screen (topHalfSize 0): --sky, else the painted sky and a deeper copy of it above.
img2img at 0.65 keeps much of the shape's colour: --colours sets it per depth (far, ..., front; three are spread
over any count), and is the theme's say in the palette. Choose them for the theme.

Writes OUT/strips (every strip, scratch), OUT/page (NAME.atlas, NAME.png, NAME.jplax, round.json) and, unless
--no-shots, OUT/shots (tools/parallax-lab-shots.sh: stills at 0, 6 and 12 s, contact.png). Exits 1 when lint
finds a fault, 2 when a strip cannot be cut. Needs ComfyUI with its venv (COMFYUI_DIR, default ~/ComfyUI) holding
dreamshaper_8 (painted) or sdXL_v10VAEFix + PixelArt_XL (pixel), and ~1.5 GB of free VRAM.
"""
import argparse
import json
import math
import os
import re
import subprocess
import sys

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
sys.path.insert(0, HERE)
import make_page  # noqa: E402
import parallax_lab as lab  # noqa: E402

ROOT = lab.ROOT
PY = os.path.join(os.environ.get("COMFYUI_DIR", os.path.expanduser("~/ComfyUI")), "venv", "bin", "python")
# No creature: a bird or an animal would stand still in a scrolling background (r242).
NEG = "text, watermark, frame, border, people, buildings, foreground grass, birds, animals, creatures"
# SDXL paints clouds and a sun whatever the prompt says (r243): its negative names them.
NEG_XL = "clouds, sun, moon, birds, animals, creatures, gradient, text, watermark, frame, border, people, buildings"
# run.sh's words for each shape: the pines' grow trees past the shape, "forest treeline" alone leaves its spikes.
WORDS = {"mountains": "rocky mountain range", "hills": "rolling hills with small trees",
         "pines": "dark pine forest treeline silhouette"}
GROW = {"mountains": 8, "hills": 8, "pines": 40}  # px the cut may reach past the shape (layer.py --grow)
DEFAULT_COLOURS = ["7a869e", "4f7a5a", "1f3a30"]  # sil.py's kinds, at full contrast: the fog gives the distance


def kinds(n, front):
    """The silhouette of each depth, back to front."""
    back = ["mountains"] + ["mountains"] * ((n - 2) // 2) + ["hills"] * ((n - 1) // 2)
    return back[:n - 1] + [front]


def colours(given, n):
    """n colours back to front: the ones given, or three spread over n."""
    cs = given or DEFAULT_COLOURS
    if len(cs) == n:
        return cs
    rgb = [np.array([int(c[i:i + 2], 16) for i in (0, 2, 4)], float) for c in cs]
    out = []
    for k in range(n):
        t = k / max(1, n - 1) * (len(rgb) - 1)
        i = min(int(t), len(rgb) - 2)
        c = rgb[i] * (1 - (t - i)) + rgb[i + 1] * (t - i)
        out.append("".join(f"{round(v):02x}" for v in c))
    return out


def speeds(n, front):
    """x2.0 a layer back from the front; the back one at most a tenth of the front's (skill step 5)."""
    s = [front / 2 ** (n - 1 - k) for k in range(n)]
    if n > 1:
        s[0] = min(s[0], front / 10, s[1] / 2)
    return s


def roughness(path):
    """Mean second difference of the cut's top outline, px: SD 1.5 grows trees out of sil.py's pine spikes on some
    seeds only, whatever the prompt (r246: 4.4-5.1 with trees, 1.9-2.3 spikes left smooth, 1.1 the bare shape)."""
    a = np.asarray(Image.open(path).convert("RGBA"))[..., 3] > 127
    top = np.argmax(a, axis=0).astype(float)
    return float(np.abs(np.roll(top, 1) - 2 * top + np.roll(top, -1)).mean())


TREES = 3.5  # roughness under which a pine strip has no trees in it


def run(cmd, log):
    with open(log, "w") as f:
        return subprocess.run(cmd, stdout=f, stderr=subprocess.STDOUT, cwd=ROOT).returncode


def solid(path):
    """Height share, from the bottom, of the rows opaque across the whole strip."""
    a = np.asarray(Image.open(path).convert("RGBA"))[..., 3] > 127
    rows = a.mean(axis=1)[::-1] >= lab.SOLID
    return float(np.argmin(rows) if not rows.all() else len(rows)) / len(rows)


def sky_of(path):
    """The painted sky: the median of the strip's top rows, as layer.py reads it."""
    img = np.asarray(Image.open(path).convert("RGB")).astype(float)
    return np.median(img[:8].reshape(-1, 3), axis=0)


def hexc(rgb):
    return "".join(f"{int(round(min(255, max(0, v)))):02x}" for v in rgb)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("out")
    ap.add_argument("--theme", required=True, help="what the page shows, in a few words: it goes in every prompt")
    ap.add_argument("--layers", type=int, default=4, help="2-6 (lint wants 3 or more)")
    ap.add_argument("--style", choices=["painted", "pixel"], default="painted")
    ap.add_argument("--front", choices=["pines", "hills"], default="pines", help="the front layer's shape")
    ap.add_argument("--colours", help="hex colours per depth, back to front, comma separated (3 are spread)")
    ap.add_argument("--sky", help="the sky gradient, top,horizon hex; default the painted sky")
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--variants", type=int, default=3, help="painted: more front strips for its SEQUENCE; 0: none")
    ap.add_argument("--front-speed", type=float, default=0.08)
    ap.add_argument("--fog", type=float, default=0.6, help="how far the back layer goes toward the fog colour, 0-1")
    ap.add_argument("--tries", type=int, default=5, help="seeds a strip may take before the run gives up")
    ap.add_argument("--name", help="the atlas and page name; default from the theme")
    ap.add_argument("--keep", action="store_true", help="keep the strips OUT/strips holds: only cut and lay out again"
                    " (another --sky, --fog or --front-speed, with the same --seed)")
    ap.add_argument("--no-shots", action="store_true")
    a = ap.parse_args()
    if not 2 <= a.layers <= 6:
        ap.error("--layers: 2 to 6")
    out = os.path.abspath(a.out)
    strips, page_dir = os.path.join(out, "strips"), os.path.join(out, "page")
    os.makedirs(strips, exist_ok=True)
    os.makedirs(page_dir, exist_ok=True)
    name = a.name or re.sub(r"[^a-z0-9]+", "-", a.theme.lower()).strip("-")[:40]
    pixel = a.style == "pixel"
    ks = kinds(a.layers, a.front)
    cols = colours(a.colours.split(",") if a.colours else None, a.layers)
    w, h = (1536, 576) if pixel else (1024, 384)
    d = HERE
    log = lambda s: print(s, file=sys.stderr, flush=True)  # noqa: E731

    def paint(k, kind, seed, prefix):
        """sil.py, gen.py, then the cut: the cut layer's path, or None when it reaches its top row."""
        depth = "distant" if k == 0 else "foreground" if k == a.layers - 1 else "middle distance"
        if pixel:
            gen = [PY, f"{d}/gen.py", "img2img", "--ckpt", "sdXL_v10VAEFix.safetensors", "--lora",
                   "PixelArt_XL.safetensors", "--lora-strength", "1.0", "--w", str(w), "--h", str(h), "--denoise", "0.7",
                   "--negative", NEG_XL, "--prompt", f"pixel art, 2d game parallax background layer, {a.theme}, {depth} "
                   f"{WORDS[kind]}, empty plain flat pale sky, solid background colour"]
        else:
            gen = [PY, f"{d}/gen.py", "img2img", "--denoise", "0.65", "--negative", NEG, "--prompt",
                   f"2d game parallax background layer, {a.theme}, {depth} {WORDS[kind]}, painterly, plain pale sky"]
        if a.keep and os.path.exists(f"{prefix}_ai.png"):
            return cut(prefix, kind)
        with open(prefix + "_seed.txt", "w") as f:  # what a kept strip was painted with: its variants' ends
            f.write(str(seed))
        if run(["python3", f"{d}/sil.py", kind, prefix, "--w", str(w), "--h", str(h), "--seed", str(seed), "--colour",
                cols[k], "--quiet-join"], prefix + "_sil.log"):
            sys.exit(f"sil.py failed: {prefix}_sil.log")
        if run(gen + ["--init", f"{prefix}_init.png", "--tile", "x", "--seed", str(seed), "--out", f"{prefix}_ai.png"],
               prefix + "_gen.log"):
            sys.exit(f"gen.py failed (free VRAM? nvidia-smi): {prefix}_gen.log")
        return cut(prefix, kind)

    def cut(prefix, kind):
        tool = ["python3", f"{d}/pixel.py", f"{prefix}_ai.png", f"{prefix}_cut.png", "--mask", f"{prefix}_mask.png",
                "--colours", "12"] if pixel else ["python3", f"{d}/layer.py", f"{prefix}_ai.png", f"{prefix}_cut.png", "--mask",
                                f"{prefix}_mask.png", "--grow", str(GROW[kind])]
        code = run(tool, prefix + "_cut.log")
        with open(prefix + "_cut.log") as f:
            log(f.read().rstrip())
        if code:
            return None
        if kind == "pines" and not pixel and roughness(f"{prefix}_cut.png") < TREES:
            log(f"{prefix}: outline roughness {roughness(prefix + '_cut.png'):.1f} < {TREES}: no trees grew")
            return None
        return f"{prefix}_cut.png"

    layers, seeds = [], []  # per depth: [cut paths], the first the layer's own; the seed that painted it
    for k, kind in enumerate(ks):
        for t in range(a.tries):
            seed = a.seed * 1000 + k * 10 + t
            got = paint(k, kind, seed, os.path.join(strips, f"l{k}"))
            if got:
                break
            log(f"layer {k} ({kind}) seed {seed}: refused, painting again")
        else:
            sys.exit(f"layer {k} ({kind}): {a.tries} strips refused (art on the top row, or pines without trees);"
                     " another --seed, or more --tries")
        layers.append([got])
        seed_file = os.path.join(strips, f"l{k}_seed.txt")
        seeds.append(int(open(seed_file).read()) if os.path.exists(seed_file) else seed)
        log(f"layer {k}: {kind}, seed {seed}, colour {cols[k]}")
    if not pixel and a.variants:
        # The front's variants: sil.py's shape for the middle, the front strip's ends (run.sh's pines).
        front, src = len(ks) - 1, os.path.join(strips, f"l{len(ks) - 1}")
        for v in range(1, a.variants + 1):
            for t in range(a.tries):
                p = os.path.join(strips, f"l{front}v{v}")
                vs = a.seed * 1000 + 500 + v * 10 + t
                if a.keep and os.path.exists(f"{p}_ai.png"):
                    got = cut(p, ks[front])
                    if got:
                        layers[front].append(got)
                    break
                run(["python3", f"{d}/sil.py", ks[front], p, "--seed", str(vs), "--edges-of", str(seeds[front]),
                     "--colour", cols[front], "--quiet-join"], p + "_sil.log")
                code = run([PY, f"{d}/gen.py", "variants", "--src", f"{src}_ai.png", "--init", f"{p}_init.png", "--tile",
                            "x", "--n", "1", "--edge", "96", "--denoise", "0.65", "--seed", str(vs), "--negative", NEG,
                            "--prompt", f"2d game parallax background layer, {a.theme}, foreground {WORDS[ks[front]]}, "
                            "painterly, plain pale sky", "--out", f"{p}_ai.png"], p + "_gen.log")
                if code:
                    sys.exit(f"gen.py variants failed: {p}_gen.log")
                os.replace(f"{p}_ai_1.png", f"{p}_ai.png")
                got = cut(p, ks[front])
                if got:
                    layers[front].append(got)
                    break
            else:
                log(f"front variant {v}: {a.tries} strips refused, left out of the SEQUENCE")

    # The page.
    s = speeds(len(ks), a.front_speed)
    files = [(f"l{k}" if len(paths) == 1 else f"l{k}_{i}", p) for k, paths in enumerate(layers)
             for i, p in enumerate(paths)]
    make_page.pack(name, files, "Nearest,Nearest" if pixel else "Linear,Linear", out=page_dir)
    bottoms, hs = [0.0] * len(ks), []
    for k, paths in enumerate(layers):
        img = Image.open(paths[0])
        hs.append(lab.SCREEN_W * img.height / img.width)  # sizeRatio 1
    for k in range(len(ks) - 2, -1, -1):
        # Under the nearer layer's solid band, a fifth of it deep: its bottom edge never shows (skill step 3).
        band = min(solid(p) for p in layers[k + 1]) * hs[k + 1]
        bottoms[k] = bottoms[k + 1] + 0.8 * band
    page_layers = []
    for k, paths in enumerate(layers):
        dy = round(100 * bottoms[k] / lab.SCREEN_H, 2)
        if len(paths) == 1:
            page_layers.append(lab.layer(f"l{k}", speed=round(s[k], 5), dy=dy))
        else:
            l = lab.layer("", speed=round(s[k], 5), dy=dy)
            l.update(kind="SEQUENCE", name=f"l{k}", sequenceSeed=a.seed, sequenceLength=2 * len(paths),
                     sequenceSegments=[{"regionName": f"l{k}_{i}", "regionPosition": 0, "weight": 1}
                                       for i in range(len(paths))])
            page_layers.append(l)
    horizon = sky_of(os.path.join(strips, "l0_ai.png"))
    sky = a.sky.split(",") if a.sky else (hexc(horizon * 0.72 + np.array([20, 40, 90]) * 0.28), hexc(horizon))
    page = lab.page_of(f"{name}.atlas", page_layers, sky=sky, ground=(sky[1], sky[1]), top_size=0.0,
                       bottom_size=1.0)
    if len(s) > 1 and a.fog > 0:
        # fog of a layer = 1 - exp(-strength (1/speed - 1/front)): the back one at a.fog.
        page["fogStrength"] = round(-math.log(1 - min(a.fog, 0.99)) / (1 / s[0] - 1 / s[-1]), 5)
        page["fogColor"] = lab.color(sky[1])
    with open(os.path.join(page_dir, f"{name}.jplax"), "w") as f:
        json.dump(page, f, indent=1)
    rel = os.path.relpath(page_dir, ROOT)
    scene = {"id": "a01", "page": f"{rel}/{name}.jplax", "atlasDir": rel, "name": name,
             "about": f"r246 ai-page: '{a.theme}', {a.style}, {len(ks)} layers ({', '.join(ks)}), seed {a.seed}"}
    with open(os.path.join(page_dir, "round.json"), "w") as f:
        json.dump({"round": name, "about": scene["about"], "scenes": [scene]}, f, indent=1)

    problems = lab.lint(lab.load(scene["page"]), rel)
    print(f"{rel}/{name}.jplax")
    print(lab.describe(page))
    print("\n".join("  ! " + p for p in problems) or "  lint ok")
    if not a.no_shots:
        subprocess.run([os.path.join(ROOT, "tools", "parallax-lab-shots.sh"), page_dir, os.path.join(out, "shots")],
                       cwd=ROOT, check=False)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
