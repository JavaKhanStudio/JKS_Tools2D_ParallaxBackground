#!/usr/bin/env python3
"""From one painting to a page (r279): estimate its depth, cut it into depth bands, repaint what each band hid, and
write the atlas and a .jplax whose speeds follow the bands' depths.

    tools/depth-page.sh PAINTING.png OUT [--layers 4] [--theme "green valley, pines"] [--front-speed 0.08]

docs/depth-tricks.md §5. Passes:
  1. the loop: a page's layers tile along X, so the painting must end where it starts. A painting whose join shows
     (r239's seam ratio over 2) has a band round its join repainted (gen.py inpaint, tile x). Paint one that loops
     from the start with gen.py txt2img --tile x (docs/ai-parallax.md);
  2. depth: Depth Anything V2 Small (Hugging Face depth-anything/Depth-Anything-V2-Small-hf, Apache-2.0, ~100 MB in
     ~/.cache/huggingface), run on the painting padded with its own other end, so the depth loops too. Its output is
     relative disparity: larger is nearer;
  3. bands: --layers clusters of that disparity (k-means in one dimension), far to near, small islands merged into
     what surrounds them; a thing a band's edge cuts (SAM 2.1 small's masks: Hugging Face
     facebook/sam2.1-hiera-small, Apache-2.0, ~180 MB) given whole to the band at its foot (one seen only in the depth's
     ramp beside a nearer thing is hidden: to the band most of it is in, r386), and so is a field a
     band's edge cut straight across its grass, which SAM prompted with two points across that cut outlines to its
     shadow's edge, hedge or bank (r374: a field down a slope was cut in slabs); then a colour pass that
     pulls into a nearer band the thin parts of it the model judged far (a branch), its mirror handing back to the
     farther band the sky and ridge seen between a crown's branches (r361), and a small patch standing above
     its band's top edge handed to the farther band round it; last, every other piece of a layer apart from its mass
     joined to it (a leaf beside its crown) or handed to the farther band round it (a speck floating over the layer).
     A layer holds its own band, and every nearer pixel under its band's top edge (a tree's trunk in front of the
     hills): the layer stays solid to its bottom, as a painted strip is. Over columns where a nearer thing hides the
     band, its outline on each side goes on behind it, mirrored and eased from one side's height to the other's
     (r373: a clump behind a trunk no longer ends in a straight edge);
  4. holes: those nearer pixels, grown by --grow px over the soft edge depth leaves, are filled with the layer's own
     colours and repainted by dreamshaper_8's inpainting model (gen.py inpaint, tile x). The camera scrolls past
     every one of them. Before it, RULES are weighed on each layer's hole (tools/parallax_rules.py, r378): a fix
     for one case, applied only where its conditions hold (sky_twigs: the twig tips Depth Anything V2 Small judged
     as far as the sky, kept in a tree page's back layer as birds, join its hole); --rule NAME=on|off forces one.
  Clouds (r409): SAM's masks at the sky's depth, brighter than the sky round them, are the clouds. When there are
  any, the back band comes apart: the sky (its pixels at the sky's depth, the clouds in it) and the far range, cut
  as a nearer band is; the sky is filled where the clouds and every nearer thing stood (fill(), no repaint) and the
  clouds go on a layer of their own between the two, faded where a peak hid them, drifting at rest speed. The page
  then has --layers + 2 layers: sky, clouds, l0 ... (the parallax-pages skill's rule 9).
  Every step that looks across the painting (depth, islands, fill) sees it as the tiled layer shows it, its two
  ends joined: else a band's edge steps at the join, lint fault (e).
  5. the page: every layer the painting's full width at sizeRatio 1, bottom on the screen's bottom, cut 2 rows
     above its first row with art; speeds proportional to each band's median disparity, the front at --front-speed,
     none under a twentieth of it. The back layer is opaque (the painted sky, or the filled sky behind the clouds);
     the gradients take the painting's top and bottom colours.

Writes OUT/recipe.json (the command and each pass, each layer's rules weighed, for tools/ai-gallery.sh), OUT/cut (source.png, depth.png, objects.png (SAM's masks), fields.png (the prompted fields), bands.png, each layer before and after the repaint, sheet.png), OUT/page (NAME.atlas,
NAME.png, NAME.jplax, round.json) and, unless --no-shots, OUT/shots (tools/parallax-lab-shots.sh, cage: stills at 0,
6 and 12 s). Exits 1 when lint finds a fault. Runs in the ComfyUI venv (COMFYUI_DIR, default ~/ComfyUI), which holds
transformers, scipy and torch; needs --ckpt in its models/checkpoints (dreamshaper_8Inpainting.safetensors, Civitai
model version 131004, ~2 GB: a model that sees the mask and the painting round it, r315) and ~2 GB of free VRAM.
"""

import argparse
import json
import os
import re
import shlex
import subprocess
import sys
import tempfile

import numpy as np
from PIL import Image
from scipy import ndimage

HERE = os.path.dirname(os.path.abspath(__file__))
TOOLS = os.path.dirname(HERE)
AI = os.path.join(TOOLS, "r239-ai-strips")
sys.path.insert(0, TOOLS)
sys.path.insert(0, AI)
import gpu_lock  # noqa: E402
import parallax_lab as lab  # noqa: E402
import parallax_rules  # noqa: E402
import seam  # noqa: E402

# r533: each step in a module of its own beside this one (dp_*.py); every name a harness reads as depth_page.X is
# imported back here. Those it patches (gen, log, depth_of, masks_of, fields_of, sky_apart, gpu_lock) are called
# from this file, so a patch still reaches the pipeline.
from dp_bands import (  # noqa: E402, F401
    bands,
    by_colour,
    colour_bands,
    kmeans,
    loose,
    merge_small,
    necks,
    see_through,
    shut_in,
    sky_hued,
    strays,
)
from dp_common import SKY, band_means, fill, filled, grow, islands, looped, runs_of  # noqa: E402, F401
from dp_models import MODEL, SAM, _depth_of, _fields_of, _masks_of, piece_at, slab_cuts  # noqa: E402, F401
from dp_objects import (  # noqa: E402, F401
    FIELD_MOST,
    HALO,
    HIDDEN,
    agreeing,
    foot_of,
    kept_fields,
    mend_join,
    objects,
    outlines,
    with_fields,
)
from dp_outline import (  # noqa: E402, F401
    SLIVER,
    alpha_of,
    behind_start,
    field_colours,
    shown,
    topline,
    unsliver,
)
from dp_page import seam_colour, sheet, speeds_of, write_page  # noqa: E402, F401
from dp_rules import RULES, sky_twigs  # noqa: E402, F401
from dp_sky import (  # noqa: E402, F401
    CLOUD_LIFT,
    CLOUD_REST,
    cloud_layer,
    clouds_of,
    range_top,
    ridge_ends,
    sky_apart,
    sky_layer,
)

ROOT = lab.ROOT
NEG = ("text, watermark, frame, border, people, birds, flying bird, animals, tree in the sky, close-up, "
       "foreground object, blurry")
WORDS = ["far distance", "middle distance", "near", "foreground"]  # the inpaint prompt's depth, back to front
# r426: what no repaint draws: SD carried a track the layer kept on across the hole, from r387's field-coloured start,
# a second track beside the painted one once the layers slid (valley, noloop).
TRACKS = ", path, road, dirt track, trail, tyre tracks"


def log(s):
    print(s, file=sys.stderr, flush=True)


def log_rules(what, rules):
    """Each rule applied, its pixels moved, on stderr."""
    for r in rules:
        if r["applied"]:
            log(f"{what}: rule {r['rule']}: {r['moved']} px")


def gen(args, logfile):
    """gen.py in a subprocess: it loads ComfyUI, whose models and argv parsing stay out of this process."""
    with open(logfile, "w") as f:
        code = subprocess.run([sys.executable, os.path.join(AI, "gen.py")] + args, stdout=f, stderr=subprocess.STDOUT,
                              cwd=ROOT, check=False).returncode
    if code:
        sys.exit(f"gen.py failed (free VRAM? nvidia-smi): {logfile}")


def in_child(step, **arrays):
    """GPU step step (STEPS) run by this file in a child process, on arrays, its result back: torch never frees the CUDA
    context it opens short of its process ending, so run here, this one sat in nvidia-smi with 160-242 MiB through the
    rest of the cut, its repaints and the other lane's gen.py (r396)."""
    with tempfile.TemporaryDirectory(prefix="depth_page-") as tmp:
        inp, out = os.path.join(tmp, "in.npz"), os.path.join(tmp, "out.npz")
        np.savez(inp, **arrays)
        code = subprocess.run([sys.executable, os.path.abspath(__file__), "--gpu-step", step, inp, out], cwd=ROOT,
                              stdout=sys.stderr, check=False).returncode
        if code:
            sys.exit(f"depth_page: the {step} step failed (exit {code})")
        with np.load(out) as got:
            return got["out"]


def depth_of(img):
    return in_child("depth", img=img)


def masks_of(img):
    return list(in_child("masks", img=img))


def fields_of(img, L, n):
    return list(in_child("fields", img=img, L=L, n=np.int64(n)))


def gpu_step(step, inp, out):
    """The child's side of in_child: step on inp's arrays, the result in out (masks stacked, none: an empty stack)."""
    with np.load(inp) as a:
        img = a["img"]
        if step == "depth":
            got = _depth_of(img)
        elif step == "masks":
            got = _masks_of(img)
        else:
            got = _fields_of(img, a["L"], int(a["n"]))
    if step != "depth":
        got = np.array(got, bool).reshape((len(got),) + img.shape[:2])
    np.savez(out, out=got)


def parse_args():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("painting")
    ap.add_argument("out")
    ap.add_argument("--layers", type=int, default=4, help="depth bands, 2-6 (lint wants 3 or more)")
    ap.add_argument("--theme", default="landscape painting", help="what the painting shows: the repaint's prompt")
    ap.add_argument("--front-speed", type=float, default=0.08)
    ap.add_argument("--grow", type=int, default=6, help="px a hole reaches past the nearer band: depth's soft edge")
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--denoise", type=float, default=0.8, help="the repaint's strength over the filled hole")
    ap.add_argument("--ckpt", default="dreamshaper_8Inpainting.safetensors", help="the repaint's checkpoint")
    ap.add_argument("--name", help="the atlas and page name; default the painting's")
    ap.add_argument("--keep", action="store_true", help="keep OUT/cut's repainted layers: only lay the page out again")
    ap.add_argument("--no-shots", action="store_true")
    ap.add_argument("--rule", action="append", metavar="NAME=on|off",
                    help="force a rule on or off, whatever its conditions say: " + ", ".join(r.name for r in RULES))
    a = ap.parse_args()
    if not 2 <= a.layers <= 6:
        ap.error("--layers: 2 to 6")
    try:
        a.forced = parallax_rules.parse_forced(a.rule, RULES)
    except ValueError as e:
        ap.error(str(e))
    return a


def mend_loop(img, cut, a, recipe):
    """1. The loop: a painting whose join shows has a band round it repainted; the painting, its join mended."""
    h, w = img.shape[:2]
    ratio, _ = seam.join(seam.load(os.path.join(cut, "painting.png")), seam.load(os.path.join(cut, "painting.png")))
    log(f"painting {w}x{h}: seam ratio {ratio:.2f}")
    recipe["loop"] = {"before": round(float(ratio), 2), "after": None}
    if ratio <= 2:
        return img
    rolled = np.roll(img, w // 2, axis=1)  # the join in the middle, where the repaint has both sides
    x = np.arange(w)
    m = np.clip(1 - (np.abs(x - w // 2) - 48) / 16, 0, 1)
    mask = np.tile(m, (h, 1))
    known = mask < 0.5  # the ramp keeps the painting under it: filled, it would blend a blur into both ends
    Image.fromarray(fill(rolled, known).round().clip(0, 255).astype(np.uint8)).save(f"{cut}/loop_src.png")
    Image.fromarray((mask * 255).astype(np.uint8)).save(f"{cut}/loop_mask.png")
    gen(["inpaint", "--ckpt", a.ckpt, "--src", f"{cut}/loop_src.png", "--mask", f"{cut}/loop_mask.png", "--tile", "x", "--denoise",
         "0.95", "--seed", str(a.seed), "--w", str(w), "--h", str(h), "--negative", NEG,
         "--prompt", f"{a.theme}, painterly, seamless", "--out", f"{cut}/loop.png"], f"{cut}/loop.log")
    img = np.roll(np.asarray(Image.open(f"{cut}/loop.png").convert("RGB")), -(w // 2), axis=1)
    Image.fromarray(img).save(os.path.join(cut, "painting.png"))
    p = os.path.join(cut, "painting.png")
    recipe["loop"]["after"] = round(float(seam.join(seam.load(p), seam.load(p))[0]), 2)
    recipe["loop"]["prompt"] = f"{a.theme}, painterly, seamless"
    log(f"join repainted: seam ratio {recipe['loop']['after']:.2f}")
    return img


def depth_bands(img, n, cut):
    """2. Depth, 3. bands: (dn, L, centres, ground, masks), the depth normalised to 0..1, each pixel's band, far 0 to
    near n-1, the fields fields_of found (r387: the layers behind one seed their hole with it) and SAM's masks (the
    clouds are read from them, r409)."""
    h, w = img.shape[:2]
    # r391: each GPU step under the lock gen.py and anim.py take, its memory freed before it ends; gen.py (the loop,
    # the repaints) takes it itself, outside these
    with gpu_lock.held("depth_page depth"):
        d = depth_of(img)
    lo, hi = np.percentile(d, (1, 99.5))
    dn = np.clip((d - lo) / (hi - lo), 0, 1)
    Image.fromarray((dn * 255).astype(np.uint8)).save(os.path.join(cut, "depth.png"))
    L, centres = bands(dn, n, min_area=w * h // 150)
    with gpu_lock.held("depth_page masks"):
        masks = masks_of(img)
    L = objects(L, masks, n)
    outlines(img, masks).save(os.path.join(cut, "objects.png"))
    with gpu_lock.held("depth_page fields"):
        fields = fields_of(img, L, n)
    fields = agreeing(L, fields, n)
    L = with_fields(L, fields, n)
    moved = L.copy()
    outlines(img, fields).save(os.path.join(cut, "fields.png"))
    L = by_colour(L, img, n)
    L = see_through(L, dn, centres, img, n)
    L = strays(L, n, w * h // 150)
    L = loose(L, n)
    L = loose(necks(L, dn, n), n)  # the stubs a neck leaves are loose pieces
    L = kept_fields(L, moved, fields)
    colour_bands(L, n).save(os.path.join(cut, "bands.png"))
    log("bands, far to near: " + ", ".join(f"{k}: disparity {centres[k]:.2f}, {(L == k).mean():.0%} of the pixels"
                                          for k in range(n)))
    return dn, L, centres, np.any(fields, axis=0) if fields else np.zeros((h, w), bool), masks


def layer_pixels(k, L, n, grow_px):
    """4. Layer k's pixels and its hole: (back, alpha, hole), back when it is the opaque back layer. With the sky apart
    (r409: L is -1 there), band 0 is cut as a nearer band is, its outline going on behind what hides it."""
    h, w = L.shape
    apart = (L < 0).any()  # the sky apart (r409): band 0 is the far range, cut along range_top
    back = k == 0 and not apart  # the opaque back layer, the painted sky
    if back:
        alpha = np.ones((h, w), bool)
    elif k == 0:
        alpha = (L == 0) | ((np.arange(h)[:, None] >= range_top(L)[None]) & (L > 0))
    else:
        alpha = alpha_of(L, k)
    # The hole: what nearer things hid, and what alpha_of took behind them that is no part of band k (r373).
    hole = alpha & (grow(L > k, grow_px) | (L < k)) if k < n - 1 else np.zeros_like(alpha)
    # The sky between a crown's branches is band k too, tinted by the leaves round it: left as a start for the
    # fill, the repaint grows a small tree there. Islands of the layer's own pixels inside the hole join it.
    if hole.any():
        hole |= looped(lambda m, o: islands(m, w * h // 150, o), alpha & ~hole, hole)
    return back, alpha, hole


def cut_layer(k, img, dn, L, centres, ground, cut, a):
    """4. Layer k and its hole (layer_pixels), repainted: (layer, its recipe entry)."""
    h, w = img.shape[:2]
    n = a.layers
    back, alpha, hole = layer_pixels(k, L, n, a.grow)
    ctx = {"k": k, "n": n, "back": back, "theme": a.theme, "depth_model": MODEL, "img": img, "dn": dn, "alpha": alpha,
           "hole": hole}
    rules = parallax_rules.weigh(RULES, ctx, a.forced)
    hole = ctx["hole"]
    log_rules(f"layer {k}", rules)
    cut_k = os.path.join(cut, f"l{k}")
    src = looped(fill, img, alpha & ~hole)
    if hole.any() and not back:  # the back layer is opaque, and nothing goes on behind its sky
        src = behind_start(src, img.astype(np.float32), alpha & ~hole, L, k)
    # A field moved to a nearer band goes on behind itself: its own colours, blurred, start the repaint there. From the
    # fill alone, river's l2 under its moved fields was repainted as a dark forest, seen at l3's edge as it slid; from
    # its pixels, a track across the field was painted again behind it, a second track once the layers slid (r387).
    field = hole & ground & (L > k)
    if field.any():
        src[field] = field_colours(img, ground & (L > k))[field]
    Image.fromarray(np.dstack([img, alpha * 255]).astype(np.uint8)).save(f"{cut_k}_raw.png")
    done = f"{cut_k}_painted.png"
    word = WORDS[min(3, round(k * 3 / max(1, n - 1)))]
    if hole.any() and not (a.keep and os.path.exists(done)):
        Image.fromarray(src.round().clip(0, 255).astype(np.uint8)).save(f"{cut_k}_src.png")
        soft = ndimage.gaussian_filter(grow(hole, 2).astype(np.float32), 1.5, mode=("nearest", "wrap"))
        Image.fromarray((np.maximum(soft, hole) * 255).astype(np.uint8)).save(f"{cut_k}_mask.png")
        gen(["inpaint", "--ckpt", a.ckpt, "--src", f"{cut_k}_src.png", "--mask", f"{cut_k}_mask.png", "--tile", "x", "--denoise",
             str(a.denoise), "--seed", str(a.seed * 100 + k), "--w", str(w), "--h", str(h), "--negative", NEG + TRACKS,
             "--prompt", f"{a.theme}, {word} scenery, painterly, seamless", "--out", done], f"{cut_k}_gen.log")
        log(f"layer {k}: {hole.mean():.0%} of the painting repainted")
    elif not hole.any():
        Image.fromarray(img).save(done)
    rgb = np.asarray(Image.open(done).convert("RGB"))
    # A 1 px soft edge where the layer gives way to a farther band: the cut's own line would alias.
    soft_a = alpha.astype(np.float32)
    if not back:
        soft_a = np.minimum(soft_a, ndimage.gaussian_filter(soft_a, 0.6, mode=("nearest", "wrap")) * 1.15)
    rgba = np.dstack([rgb, (np.clip(soft_a, 0, 1) * 255).round()]).astype(np.uint8)
    top = max(0, int(np.argmax(alpha.any(axis=1))) - 2)  # 2 clear rows: a pine's tip on the top row reads cut, (a)
    Image.fromarray(rgba[top:]).save(f"{cut_k}.png")
    med = float(np.median(dn[L == k])) if (L == k).any() else centres[k]
    layer = {"name": f"l{k}", "path": f"{cut_k}.png", "disparity": med, "top": top, "hole": float(hole.mean()),
             "raw": f"{cut_k}_raw.png"}
    entry = {"strip": f"l{k}", "disparity": round(med, 3), "top": top,
             "repainted": round(float(hole.mean()), 4), "seed": a.seed * 100 + k,
             "prompt": f"{a.theme}, {word} scenery, painterly, seamless" if hole.any() else None, "rules": rules}
    return layer, entry


def main():
    if sys.argv[1:2] == ["--gpu-step"]:
        return gpu_step(*sys.argv[2:5])
    a = parse_args()
    out = os.path.abspath(a.out)
    cut, page_dir = os.path.join(out, "cut"), os.path.join(out, "page")
    os.makedirs(cut, exist_ok=True)
    os.makedirs(page_dir, exist_ok=True)
    name = a.name or re.sub(r"[^a-z0-9]+", "-", os.path.splitext(os.path.basename(a.painting))[0].lower()).strip("-")
    n = a.layers

    im = Image.open(a.painting).convert("RGB")
    w, h = (im.width // 8) * 8, (im.height // 8) * 8  # the VAE's cell
    im = im.crop((0, 0, w, h))
    im.save(os.path.join(cut, "painting.png"))
    im.save(os.path.join(cut, "source.png"))  # painting.png is the one with its join repainted, when it is
    recipe = {"tool": "depth-page", "command": shlex.join(["tools/depth-page.sh"] + [x for x in sys.argv[1:]
                                                                                    if x not in ("--keep", "--no-shots")]),
              "theme": a.theme, "name": name, "seed": a.seed, "size": [w, h], "denoise": a.denoise,
              "model": f"Depth Anything V2 Small + SAM 2.1 small + SD 1.5 {a.ckpt} (repaints)", "page": f"page/{name}.jplax",
              "negative": NEG, "fogStrength": 0, "layers": []}

    img = mend_loop(np.asarray(im), cut, a, recipe)
    dn, L, centres, ground, masks = depth_bands(img, n, cut)
    layers = []
    # r409: clouds always on a layer of their own, behind the far range and over a sky with none: BILLOW on a back
    # layer holding sky, clouds and range warped the mountains too, and painted clouds could not drift.
    clouds = clouds_of(img, dn, L, masks)
    if clouds.any():
        L = sky_apart(L, dn, clouds, w * h // 150, img)
        clouds &= L < 0
        sky, entry = sky_layer(img, dn, L, clouds, cut, a)
        log_rules("sky", entry["rules"])
        layers.append(sky)
        recipe["layers"].append(entry)
        cloud, entry = cloud_layer(img, np.asarray(Image.open(sky["path"]).convert("RGB")), L, clouds, cut)
        layers.append(cloud)
        recipe["layers"].append(entry)
        log(f"clouds: {clouds.mean():.1%} of the painting, on a layer of their own over the filled sky")
    for k in range(n):
        layer, entry = cut_layer(k, img, dn, L, centres, ground, cut, a)
        layers.append(layer)
        recipe["layers"].append(entry)
    s = speeds_of(layers, a.front_speed)
    scene, rel, sky = write_page(name, layers, s, img, page_dir, a)
    sheet(cut, layers)

    problems = lab.lint(lab.load(scene["page"]), rel)
    for k, ly in enumerate(recipe["layers"]):
        ly["speed"] = s[k]
    recipe.update(lint=problems, sky=list(sky))
    with open(os.path.join(out, "recipe.json"), "w") as f:
        json.dump(recipe, f, indent=1)
    print(f"{rel}/{name}.jplax")
    for k, ly in enumerate(layers):
        print(f"  {ly['name']}: disparity {ly['disparity']:.2f}, speed {s[k]:g}, from row {ly['top']}, "
              f"{ly['hole']:.0%} repainted")
    print("\n".join("  ! " + p for p in problems) or "  lint ok")
    if not a.no_shots:
        subprocess.run([os.path.join(TOOLS, "parallax-lab-shots.sh"), page_dir, os.path.join(out, "shots")],
                       cwd=ROOT, check=False)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
