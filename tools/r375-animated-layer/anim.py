"""Animate a layer strip in place with Wan 2.1 VACE 1.3B, through a local ComfyUI's nodes, in-process (r375).

    ~/ComfyUI/venv/bin/python tools/r375-animated-layer/anim.py --src l3_ai.png --prompt "fronds swaying" \
        [--mask moving.png] [--edge 64] [--frames 49] [--seed 1] OUT
    python3 tools/r375-animated-layer/loopcut.py OUT/raw OUT --mask OUT/mask.png     (the loop)
    python3 tools/r375-animated-layer/loopcheck.py OUT --alpha l3_cut.png            (does it loop?)

VACE is given the strip as every frame of a control video, and a mask per frame: white where the model may paint.
The first frame's mask is black, so the clip starts on the strip and stays near it. --mask (white: may move) keeps
the rest of the strip still, which is what makes an animation partial: outside it every frame is the strip's own
pixels, pasted back after the decode, and the model is only given the rows the mask can reach (--context more round
them). --edge keeps that many columns at each end still (feathered), so every frame tiles in X as the strip did.
The clip does not loop: loopcut.py cuts one out of it. (--hold-end 4 holds the last latent to the strip too, a loop
by construction, but Wan's VAE draws a clip's first frame apart from the rest, and the wrap pops: r375's palms-v1.)
Models (~11 GB, Comfy-Org/Wan_2.1_ComfyUI_repackaged): diffusion_models/wan2.1_vace_1.3B_fp16.safetensors,
text_encoders/umt5_xxl_fp8_e4m3fn_scaled.safetensors, vae/wan_2.1_vae.safetensors. The text encoder runs on the
CPU, the rest fits 8 GB (5.4 GB at 1024x240, 33 frames).
Writes OUT/raw/f000.png ... (every frame), OUT/mask.png and OUT/anim.log.
"""
import argparse
import json
import os
import sys
import time

COMFY = os.environ.get("COMFYUI_DIR", os.path.expanduser("~/ComfyUI"))
sys.path.insert(0, COMFY)
sys.argv_saved, sys.argv = sys.argv, [sys.argv[0]]  # comfy parses argv on import

import comfy.options  # noqa: E402
import numpy as np  # noqa: E402
import torch  # noqa: E402
from PIL import Image, ImageFilter  # noqa: E402

comfy.options.enable_args_parsing(False)
import comfy.model_management  # noqa: E402
import folder_paths  # noqa: E402,F401
import nodes  # noqa: E402

sys.argv = sys.argv_saved

NEGATIVE = ("static, still image, frozen, blurry, low quality, jpeg artifacts, text, watermark, camera motion, "
            "zoom, pan, moving camera, people, birds")


def wan_nodes():
    from comfy_extras.nodes_model_advanced import ModelSamplingSD3
    from comfy_extras.nodes_wan import TrimVideoLatent, WanVaceToVideo
    return ModelSamplingSD3, TrimVideoLatent, WanVaceToVideo


def moving_mask(w, h, path, edge):
    m = np.ones((h, w), np.float32) if not path else \
        np.asarray(Image.open(path).convert("L").resize((w, h)), np.float32) / 255
    if edge:
        ramp = np.clip((np.minimum(np.arange(w), w - 1 - np.arange(w)) - edge / 2) / (edge / 2), 0, 1)
        m = m * ramp[None, :]
    return m


def match_colours(video, still, region):
    """One lookup table per channel taking the clip's colours in region, all frames pooled, to the still's there.
    Wan repaints what it moves in its own colours (r375: palm crowns 61 -> 23 in red); one table for every frame
    brings them back without flicker."""
    out = video.copy()
    q = np.linspace(0, 1, 257)
    for c in range(3):
        mine = np.quantile(video[:, region, c], q)
        theirs = np.quantile(still[region, c], q)
        out[..., c] = np.interp(video[..., c], mine, theirs)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("--src", required=True, help="the layer's painted strip (RGB)")
    ap.add_argument("--prompt", required=True)
    ap.add_argument("--mask", help="white where the strip may move (grey: partly); none: everywhere")
    ap.add_argument("--edge", type=int, default=64, help="columns kept still at each end, so frames tile in X")
    ap.add_argument("--context", type=int, default=32, help="rows round the moving part the model also sees")
    ap.add_argument("--frames", type=int, default=49, help="4k+1")
    ap.add_argument("--steps", type=int, default=25)
    ap.add_argument("--cfg", type=float, default=6.0)
    ap.add_argument("--shift", type=float, default=8.0)
    ap.add_argument("--weights", default="default", help="fp8_e4m3fn halves the model's 4 GB")
    ap.add_argument("--no-match", action="store_true", help="keep the model's colours (see match_colours)")
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--hold-end", type=int, default=0,
                    help="frames at the end held to the strip too (4: one latent): a loop by construction, but it pops")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    t0 = time.time()

    src = Image.open(a.src).convert("RGB")
    w, h = src.size
    assert w % 16 == 0 and h % 16 == 0, "strip size must be a multiple of 16"
    still = np.asarray(src, np.float32) / 255
    soft = moving_mask(w, h, a.mask, a.edge)
    Image.fromarray((soft * 255).astype(np.uint8)).save(os.path.join(a.out, "mask.png"))
    # what the model may paint: the moving part grown a little, so its edge is painted, then blended back
    grow = Image.fromarray((soft > 0.02).astype(np.uint8) * 255).filter(ImageFilter.MaxFilter(17))
    paint = np.asarray(grow, np.float32) / 255

    # the model sees only the rows the mask can move, and --context rows round them: a partial animation costs
    # the GPU (and the atlas) its moving band, not the whole strip
    rows = np.nonzero(paint.any(1))[0]
    y0 = max(0, (rows.min() - a.context) // 16 * 16)
    y1 = min(h, -(-(rows.max() + 1 + a.context) // 16) * 16)
    band = still[y0:y1]
    n = a.frames
    masks = np.repeat(paint[None, y0:y1], n, 0)
    masks[:1] = 0
    if a.hold_end:
        masks[n - a.hold_end:] = 0
    control = torch.from_numpy(np.repeat(band[None], n, 0))
    control_masks = torch.from_numpy(masks)

    ModelSamplingSD3, TrimVideoLatent, WanVaceToVideo = wan_nodes()
    model = nodes.UNETLoader().load_unet("wan2.1_vace_1.3B_fp16.safetensors", a.weights)[0]
    model = ModelSamplingSD3().patch(model, a.shift)[0]
    clip = nodes.CLIPLoader().load_clip("umt5_xxl_fp8_e4m3fn_scaled.safetensors", "wan", "cpu")[0]
    vae = nodes.VAELoader().load_vae("wan_2.1_vae.safetensors")[0]
    pos = nodes.CLIPTextEncode().encode(clip, a.prompt)[0]
    neg = nodes.CLIPTextEncode().encode(clip, NEGATIVE)[0]
    del clip
    pos, neg, latent, trim = WanVaceToVideo.execute(pos, neg, vae, w, y1 - y0, n, 1, 1.0, control_video=control,
                                                    control_masks=control_masks)
    latent = nodes.KSampler().sample(model, a.seed, a.steps, a.cfg, "uni_pc", "simple", pos, neg, latent)[0]
    if trim:
        latent = TrimVideoLatent.execute(latent, trim)[0]
    video = nodes.VAEDecode().decode(vae, latent)[0].cpu().numpy()  # [N,H,W,3]
    if video.ndim == 5:
        video = video.reshape(-1, *video.shape[-3:])
    video = video[:n]
    if not a.no_match:
        video = match_colours(video, band, paint[y0:y1] > 0.5)
    out = np.repeat(still[None], n, 0)
    out[:, y0:y1] = video
    out = out * soft[None, ..., None] + still[None] * (1 - soft[None, ..., None])
    raw = os.path.join(a.out, "raw")
    os.makedirs(raw, exist_ok=True)
    for i, f in enumerate(out):
        Image.fromarray((np.clip(f, 0, 1) * 255 + 0.5).astype(np.uint8)).save(os.path.join(raw, f"f{i:03d}.png"))
    with open(os.path.join(a.out, "anim.log"), "w") as f:
        json.dump({**vars(a), "seconds": round(time.time() - t0), "rows": [int(y0), int(y1)]}, f, indent=1)
    print(f"{len(out)} frames in {time.time() - t0:.0f} s -> {raw}", flush=True)


if __name__ == "__main__":
    with torch.inference_mode():  # ComfyUI's server runs nodes so; without it every block's activations are kept
        main()
