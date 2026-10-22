"""r485: does a stronger negative keep the small figures out of post-apo ruins strips? (no check, Simon's answer)

    python3 tools/r485-figures/probe.py OUT [--seeds 4] [--only d70:l2] [--neg old,new]

Repaints parallax-ai R3's post-apo ruins strips (l1-l3 at denoise 0.70 and 0.60) from their saved inits, the round's
own gen.py command (DreamShaperXL Turbo + Storybook 0.9, 8 steps, cfg 2, dpmpp_sde), seeds strip seed + 0..N-1, once
per way (--neg: old, new, empty, nopost, abandoned). OUT/<d>-<l>-<seed>-<neg>.png; OUT/sheet-<d>-<l>.png puts each
seed's ways side by side, the strips' lower half where the figures stood, for counting by eye. Reads ../JKS_Tools2D_ParallaxAI, never writes there.
"""
import argparse
import os
import subprocess
import sys

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
STRIPS = os.path.join(ROOT, "tools", "r239-ai-strips")
sys.path.insert(0, STRIPS)
import ai_page  # noqa: E402

R3 = os.path.join(os.path.dirname(ROOT), "JKS_Tools2D_ParallaxAI", "build", "rounds", "r3")
PROMPT = ("2d game parallax background layer, post-apocalyptic ruined city, dusty orange haze, desolate, "
          "middle distance ruined city skyline, broken skyscrapers silhouette, painterly, plain pale sky")
# What r485 adds: R3's figures stood under a negative that already said "people".
FIGURES = ", person, human figure, man, woman, silhouette of a person, pedestrian, survivor, crowd"
NEGS = {"old": ai_page.negative(ai_page.NEG, "ruins"), "new": ai_page.negative(ai_page.NEG, "ruins") + FIGURES}
# Ways tried beyond the negative, by name: the prompt they paint with (r443 struck a word from the prompt).
PROMPTS = {"old": PROMPT, "new": PROMPT,
           "empty": PROMPT.replace("desolate,", "desolate, deserted, empty, uninhabited, no one,"),
           "nopost": PROMPT.replace("post-apocalyptic ruined city", "ruined city"),
           "abandoned": PROMPT.replace("post-apocalyptic ruined city", "abandoned ruined city")}
NEGS["empty"] = NEGS["nopost"] = NEGS["abandoned"] = NEGS["new"]
MODEL = ["--ckpt", "DreamShaperXL_Turbo_v2_1.safetensors", "--lora", "StorybookRedmondV2_XL.safetensors",
         "--lora-strength", "0.9", "--w", "1536", "--h", "576", "--steps", "8", "--cfg", "2", "--sampler",
         "dpmpp_sde"]


def paint(d, layer, seed, neg, out):
    if os.path.exists(out):
        return
    init = os.path.join(R3, f"postapo-{d}", "strips", f"{layer}_init.png")
    cmd = [ai_page.PY, os.path.join(STRIPS, "gen.py"), "img2img", *MODEL, "--denoise", "0." + d[1:],
           "--negative", NEGS[neg], "--prompt", PROMPTS[neg], "--init", init, "--tile", "x", "--seed", str(seed),
           "--out", out]
    with open(out + ".log", "w") as log:
        if subprocess.run(cmd, stdout=log, stderr=subprocess.STDOUT, check=False).returncode:
            sys.exit(f"gen.py failed: {out}.log")


def sheet(out, d, layer, seeds, negs):
    """Each seed a row, each negative a column: the strip's lower half at 1x (figures are 10-50 px tall)."""
    tiles = [[Image.open(f"{out}/{d}-{layer}-{s}-{n}.png").convert("RGB") for n in negs] for s in seeds]
    w, h = tiles[0][0].size
    crop = (0, h // 2, w, h)
    ch = crop[3] - crop[1]
    im = Image.new("RGB", (w * len(negs) + 8 * (len(negs) - 1), (ch + 20) * len(seeds)), "white")
    dr = ImageDraw.Draw(im)
    for i, row in enumerate(tiles):
        for j, t in enumerate(row):
            x, y = j * (w + 8), i * (ch + 20)
            dr.text((x + 4, y + 4), f"{d} {layer} seed {seeds[i]} negative {negs[j]}", fill="black")
            im.paste(t.crop(crop), (x, y + 20))
    im.save(f"{out}/sheet-{d}-{layer}.png")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--seeds", type=int, default=4)
    ap.add_argument("--only", help="d70:l2 and the like, comma separated")
    ap.add_argument("--neg", default="old,new")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    negs = a.neg.split(",")
    jobs = [(d, f"l{k}") for d in ("d70", "d60") for k in (1, 2, 3)]
    if a.only:
        jobs = [tuple(j.split(":")) for j in a.only.split(",")]
    for d, layer in jobs:
        with open(os.path.join(R3, f"postapo-{d}", "strips", f"{layer}_seed.txt")) as f:
            first = int(f.read())
        seeds = [first + i for i in range(a.seeds)]
        for s in seeds:
            for n in negs:
                paint(d, layer, s, n, f"{a.out}/{d}-{layer}-{s}-{n}.png")
                print(f"{d} {layer} {s} {n}", flush=True)
        sheet(a.out, d, layer, seeds, negs)


if __name__ == "__main__":
    main()
