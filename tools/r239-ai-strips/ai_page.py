#!/usr/bin/env python3
"""From a theme to a page (r246): paint the layers with a local ComfyUI, cut them, pack an atlas, write a
.jplax by the parallax-pages skill's rules, lint it and render it.

    tools/ai-page.sh --theme "snowy pine valley at dawn" --layers 4 --style painted build/ai/snow
    tools/ai-page.sh --theme "..." --colours 8a93a8,5d7a8c,1c2e33 --sky 6d8fc4,dfe6ee OUT

Passes, each one r239's (docs/ai-parallax.md): sil.py draws a shape that loops for each depth, back to front
(mountains far, hills between, the front --front: pines or hills); gen.py paints it with --tile x; painted, the front
layer gets --variants more strips that start and end on its columns and becomes a SEQUENCE; layer.py (painted) or
pixel.py (pixel, SDXL + PixelArt_XL) keys the sky out. A strip whose art reaches its top row, or that holds a creature
(creature.py, r261), is painted again on the next seed, up to --tries times.

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

Writes OUT/recipe.json (the command, and every strip's shape, seed, colour and prompt: tools/ai-gallery.sh shows it),
OUT/strips (every strip, scratch), OUT/page (NAME.atlas, NAME.png, NAME.jplax, round.json) and, unless
--no-shots, OUT/shots (tools/parallax-lab-shots.sh: stills at 0, 6 and 12 s, contact.png). Exits 1 when lint
finds a fault, 2 when a strip cannot be cut. Needs ComfyUI with its venv (COMFYUI_DIR, default ~/ComfyUI) holding
dreamshaper_8 (painted) or sdXL_v10VAEFix + PixelArt_XL (pixel), and ~1.5 GB of free VRAM.
"""
import argparse
import json
import math
import os
import re
import shlex
import subprocess
import sys
from pathlib import Path

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
         "pines": "dark pine forest treeline silhouette", "dunes": "sand dunes", "sea": "calm sea, flat horizon",
         "ruins": "ruined city skyline, broken skyscrapers silhouette", "palms": "palm trees silhouette on a sandy shore"}
GROW = {"mountains": 8, "hills": 8, "pines": 40, "dunes": 8, "sea": 4, "ruins": 16, "palms": 24}  # layer.py --grow, px
DEFAULT_COLOURS = ["7a869e", "4f7a5a", "1f3a30"]  # sil.py's kinds, at full contrast: the fog gives the distance


def negative(neg, kind):
    """The negative prompt for a shape: a ruined skyline is buildings."""
    return neg.replace(", buildings", "") if kind == "ruins" else neg


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


DRY_RUN = False  # --dry-run: print each command instead of running it


def run(cmd, log):
    if DRY_RUN:
        print(shlex.join(cmd), ">", log)
        return 0
    with open(log, "w") as f:
        return subprocess.run(cmd, stdout=f, stderr=subprocess.STDOUT, cwd=ROOT, check=False).returncode


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
    return "".join(f"{int(round(min(255, max(0, v)))):02x}" for v in rgb)  # noqa: RUF046 (v may be a numpy float)


def log(s):
    print(s, file=sys.stderr, flush=True)


def strip_of(path):
    """A cut layer's strip: OUT/strips/l3v1_cut.png is l3v1."""
    return os.path.basename(path)[:-len("_cut.png")]


def parse_args():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("out")
    ap.add_argument("--theme", required=True, help="what the page shows, in a few words: it goes in every prompt")
    ap.add_argument("--layers", type=int, default=4, help="2-6 (lint wants 3 or more)")
    ap.add_argument("--style", choices=["painted", "pixel"], default="painted")
    ap.add_argument("--front", choices=sorted(WORDS), default="pines", help="the front layer's shape")
    ap.add_argument("--kinds", help="every depth's shape, back to front, comma separated (sets --layers and --front):"
                    " e.g. mountains,sea,dunes,palms for a beach, mountains,ruins,ruins for a ruined city")
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
    ap.add_argument("--dry-run", action="store_true", help="print every command, back to front, as if each strip took"
                    " on its first seed, and write nothing (r305: the proof that a change runs the same commands)")
    a = ap.parse_args()
    if a.kinds:
        given = a.kinds.split(",")
        if any(k not in WORDS for k in given):
            ap.error(f"--kinds: each one of {', '.join(sorted(WORDS))}")
        a.layers, a.front = len(given), given[-1]
    if not 2 <= a.layers <= 6:
        ap.error("--layers: 2 to 6")
    return a


class Pipeline:
    """One run, step by step: what every step reads (the arguments, the strip size, each depth's shape and colour) and
    what the strips were painted with, for recipe.json."""

    def __init__(self, a):
        self.a = a
        self.out = os.path.abspath(a.out)
        self.strips, self.page_dir = os.path.join(self.out, "strips"), os.path.join(self.out, "page")
        if not a.dry_run:
            os.makedirs(self.strips, exist_ok=True)
            os.makedirs(self.page_dir, exist_ok=True)
        self.name = a.name or re.sub(r"[^a-z0-9]+", "-", a.theme.lower()).strip("-")[:40]
        self.pixel = a.style == "pixel"
        self.ks = a.kinds.split(",") if a.kinds else kinds(a.layers, a.front)
        self.cols = colours(a.colours.split(",") if a.colours else None, a.layers)
        self.w, self.h = (1536, 576) if self.pixel else (1024, 384)
        self.made = {}  # strip prefix -> how it was painted, for recipe.json

    def gen_command(self, k, kind):
        """gen.py's img2img for depth k, its init, seed and out left to add."""
        a, d = self.a, HERE
        depth = "distant" if k == 0 else "foreground" if k == a.layers - 1 else "middle distance"
        if self.pixel:
            return [PY, f"{d}/gen.py", "img2img", "--ckpt", "sdXL_v10VAEFix.safetensors", "--lora",
                    "PixelArt_XL.safetensors", "--lora-strength", "1.0", "--w", str(self.w), "--h", str(self.h),
                    "--denoise", "0.7", "--negative", negative(NEG_XL, kind), "--prompt",
                    (f"pixel art, 2d game parallax background layer, {a.theme}, {depth} "
                     f"{WORDS[kind]}, empty plain flat pale sky, solid background colour")]
        return [PY, f"{d}/gen.py", "img2img", "--denoise", "0.65", "--negative", negative(NEG, kind), "--prompt",
                f"2d game parallax background layer, {a.theme}, {depth} {WORDS[kind]}, painterly, plain pale sky"]

    def paint(self, k, kind, seed, prefix):
        """sil.py, gen.py, then the cut: the cut layer's path, or None when it reaches its top row or holds a
        creature."""
        a, d = self.a, HERE
        gen = self.gen_command(k, kind)
        self.made[os.path.basename(prefix)] = {"prompt": gen[gen.index("--prompt") + 1],
                                               "negative": gen[gen.index("--negative") + 1],
                                               "denoise": float(gen[gen.index("--denoise") + 1])}
        if a.keep and os.path.exists(f"{prefix}_ai.png"):
            return self.cut(prefix, kind)
        if not a.dry_run:
            with open(prefix + "_seed.txt", "w") as f:  # what a kept strip was painted with: its variants' ends
                f.write(str(seed))
        if run(["python3", f"{d}/sil.py", kind, prefix, "--w", str(self.w), "--h", str(self.h), "--seed", str(seed),
                "--colour", self.cols[k], "--quiet-join"], prefix + "_sil.log"):
            sys.exit(f"sil.py failed: {prefix}_sil.log")
        if run(gen + ["--init", f"{prefix}_init.png", "--tile", "x", "--seed", str(seed), "--out", f"{prefix}_ai.png"],
               prefix + "_gen.log"):
            sys.exit(f"gen.py failed (free VRAM? nvidia-smi): {prefix}_gen.log")
        return self.cut(prefix, kind)

    def cut(self, prefix, kind):
        d = HERE
        tool = ["python3", f"{d}/pixel.py", f"{prefix}_ai.png", f"{prefix}_cut.png", "--mask", f"{prefix}_mask.png",
                "--colours", "12"] if self.pixel else ["python3", f"{d}/layer.py", f"{prefix}_ai.png",
                                                       f"{prefix}_cut.png", "--mask", f"{prefix}_mask.png", "--grow",
                                                       str(GROW[kind])]
        code = run(tool, prefix + "_cut.log")
        if self.a.dry_run:
            return f"{prefix}_cut.png"
        with open(prefix + "_cut.log") as f:
            log(f.read().rstrip())
        if code:
            return None
        if kind == "pines" and not self.pixel and roughness(f"{prefix}_cut.png") < TREES:
            log(f"{prefix}: outline roughness {roughness(prefix + '_cut.png'):.1f} < {TREES}: no trees grew")
            return None
        return f"{prefix}_cut.png"

    def paint_depths(self):
        """Every depth back to front, each on the next seed until one is not refused: per depth [its cut path], and
        the seed that painted it."""
        a = self.a
        layers, seeds = [], []
        for k, kind in enumerate(self.ks):
            for t in range(a.tries):
                seed = a.seed * 1000 + k * 10 + t
                got = self.paint(k, kind, seed, os.path.join(self.strips, f"l{k}"))
                if got:
                    break
                log(f"layer {k} ({kind}) seed {seed}: refused, painting again")
            else:
                sys.exit(f"layer {k} ({kind}): {a.tries} strips refused (art on the top row, a creature, or pines"
                         " without trees); another --seed, or more --tries")
            layers.append([got])
            seed_file = os.path.join(self.strips, f"l{k}_seed.txt")
            seeds.append(int(Path(seed_file).read_text()) if os.path.exists(seed_file) else seed)
            log(f"layer {k}: {kind}, seed {seed}, colour {self.cols[k]}")
        return layers, seeds

    def paint_variants(self, layers, seeds):
        """The front's variants: sil.py's shape for the middle, the front strip's ends (run.sh's pines), each added
        to the front's cut paths."""
        for v in range(1, self.a.variants + 1):
            for t in range(self.a.tries):
                if self.paint_variant(layers, seeds, v, t):
                    break
            else:
                log(f"front variant {v}: {self.a.tries} strips refused, left out of the SEQUENCE")

    def paint_variant(self, layers, seeds, v, t):
        """Variant v on try t: True when it is done with (painted, or kept from an earlier run)."""
        a, d, ks, strips = self.a, HERE, self.ks, self.strips
        front, src = len(ks) - 1, os.path.join(strips, f"l{len(ks) - 1}")
        p = os.path.join(strips, f"l{front}v{v}")
        vs = a.seed * 1000 + 500 + v * 10 + t
        vprompt = (f"2d game parallax background layer, {a.theme}, foreground {WORDS[ks[front]]}, "
                   "painterly, plain pale sky")
        self.made[os.path.basename(p)] = {"prompt": vprompt, "negative": negative(NEG, ks[front]), "denoise": 0.65,
                                          "seed": vs, "edgesOf": seeds[front]}
        if a.keep and os.path.exists(f"{p}_ai.png"):
            got = self.cut(p, ks[front])
            if got:
                layers[front].append(got)
            return True
        run(["python3", f"{d}/sil.py", ks[front], p, "--seed", str(vs), "--edges-of", str(seeds[front]),
             "--colour", self.cols[front], "--quiet-join"], p + "_sil.log")
        code = run([PY, f"{d}/gen.py", "variants", "--src", f"{src}_ai.png", "--init", f"{p}_init.png", "--tile",
                    "x", "--n", "1", "--edge", "96", "--denoise", "0.65", "--seed", str(vs), "--negative",
                    negative(NEG, ks[front]), "--prompt", vprompt, "--out", f"{p}_ai.png"], p + "_gen.log")
        if code:
            sys.exit(f"gen.py variants failed: {p}_gen.log")
        if not a.dry_run:
            os.replace(f"{p}_ai_1.png", f"{p}_ai.png")
        got = self.cut(p, ks[front])
        if got:
            layers[front].append(got)
            return True
        return False

    def bottoms(self, layers):
        """Each layer's bottom edge, world units up: the front at 0, each farther one under the solid band of the one
        in front of it, a fifth of it deep, so its bottom edge never shows (skill step 3)."""
        bottoms, hs = [0.0] * len(self.ks), []
        for paths in layers:
            img = Image.open(paths[0])
            hs.append(lab.SCREEN_W * img.height / img.width)  # sizeRatio 1
        for k in range(len(self.ks) - 2, -1, -1):
            band = min(solid(p) for p in layers[k + 1]) * hs[k + 1]
            bottoms[k] = bottoms[k + 1] + 0.8 * band
        return bottoms

    def write_page(self, layers):
        """Packs the atlas and writes NAME.jplax and round.json: the page, its layers' speeds, and its scene."""
        a, ks, name, page_dir = self.a, self.ks, self.name, self.page_dir
        s = speeds(len(ks), a.front_speed)
        files = [(f"l{k}" if len(paths) == 1 else f"l{k}_{i}", p) for k, paths in enumerate(layers)
                 for i, p in enumerate(paths)]
        make_page.pack(name, files, "Nearest,Nearest" if self.pixel else "Linear,Linear", out=page_dir)
        bottoms = self.bottoms(layers)
        page_layers = []
        for k, paths in enumerate(layers):
            dy = round(100 * bottoms[k] / lab.SCREEN_H, 2)
            if len(paths) == 1:
                page_layers.append(lab.layer(f"l{k}", speed=round(s[k], 5), dy=dy))
            else:
                seq = lab.layer("", speed=round(s[k], 5), dy=dy)
                seq.update(kind="SEQUENCE", name=f"l{k}", sequenceSeed=a.seed, sequenceLength=2 * len(paths),
                           sequenceSegments=[{"regionName": f"l{k}_{i}", "regionPosition": 0, "weight": 1}
                                             for i in range(len(paths))])
                page_layers.append(seq)
        horizon = sky_of(os.path.join(self.strips, "l0_ai.png"))
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
        return page, page_layers, s, sky, scene

    def write_recipe(self, layers, page, page_layers, s, sky, problems):
        """OUT/recipe.json: what made the page, strip by strip: the ones the page draws, each with the prompt and seed
        that painted it."""
        a, pixel, name = self.a, self.pixel, self.name
        keep_out = ("--keep", "--no-shots")  # how this run went, not what paints the page
        recipe = {"command": shlex.join(["tools/ai-page.sh"] + [x for x in sys.argv[1:] if x not in keep_out]),
                  "theme": a.theme, "style": a.style, "name": name, "seed": a.seed, "size": [self.w, self.h],
                  "model": "SDXL 1.0 + PixelArt_XL" if pixel else "SD 1.5 dreamshaper_8",
                  "page": f"page/{name}.jplax", "fogStrength": page.get("fogStrength", 0), "sky": list(sky),
                  "lint": problems, "layers": []}
        for k, paths in enumerate(layers):
            strips_k = []
            for path in paths:
                sid = strip_of(path)
                seed_file = os.path.join(self.strips, f"{sid}_seed.txt")
                info = dict(self.made.get(sid, {}), strip=sid)
                info.setdefault("seed", int(Path(seed_file).read_text()) if os.path.exists(seed_file) else None)
                strips_k.append(info)
            recipe["layers"].append({"kind": self.ks[k], "colour": self.cols[k], "speed": round(s[k], 5),
                                     "dy": page_layers[k]["decal_Y_Ratio"],
                                     "sequence": len(paths) > 1, "strips": strips_k})
        with open(os.path.join(self.out, "recipe.json"), "w") as f:
            json.dump(recipe, f, indent=1)

    def shots_command(self):
        return [os.path.join(ROOT, "tools", "parallax-lab-shots.sh"), self.page_dir, os.path.join(self.out, "shots")]


def main():
    global DRY_RUN
    a = parse_args()
    DRY_RUN = a.dry_run
    p = Pipeline(a)
    layers, seeds = p.paint_depths()
    if not p.pixel and a.variants:
        p.paint_variants(layers, seeds)
    if a.dry_run:
        print(f"make_page.pack {p.name}", *(path for paths in layers for path in paths))
        print("(no shots)" if a.no_shots else shlex.join(p.shots_command()))
        return 0
    page, page_layers, s, sky, scene = p.write_page(layers)
    rel = os.path.relpath(p.page_dir, ROOT)
    problems = lab.lint(lab.load(scene["page"]), rel)
    p.write_recipe(layers, page, page_layers, s, sky, problems)
    print(f"{rel}/{p.name}.jplax")
    print(lab.describe(page))
    print("\n".join("  ! " + problem for problem in problems) or "  lint ok")
    if not a.no_shots:
        subprocess.run(p.shots_command(), cwd=ROOT, check=False)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
