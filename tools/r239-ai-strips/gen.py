"""Generate parallax layer strips that loop, with the models of a local ComfyUI (r239).

Runs ComfyUI's nodes in-process, without its server, and leaves the install untouched:

    ~/ComfyUI/venv/bin/python tools/r239-ai-strips/gen.py txt2img --prompt "..." --out a.png --tile x
    ~/ComfyUI/venv/bin/python tools/r239-ai-strips/gen.py img2img --init sil.png --denoise 0.75 --tile x ...
    ~/ComfyUI/venv/bin/python tools/r239-ai-strips/gen.py variants --src a.png --n 3 --edge 96 --tile x ...

--tile x pads every convolution of the UNet and the VAE circularly along X (zeros along Y): the
model then draws on a cylinder, so the strip's right edge continues into its left one.
`variants` repaints the middle of a looping strip and keeps its edge columns: every variant
starts and ends on the same pixels, so a SEQUENCE layer can put any of them after any other.
"""
import argparse
import os
import sys

COMFY = os.environ.get("COMFYUI_DIR", os.path.expanduser("~/ComfyUI"))
sys.path.insert(0, COMFY)
sys.argv_saved, sys.argv = sys.argv, [sys.argv[0]]  # comfy parses argv on import

import numpy as np  # noqa: E402
import torch  # noqa: E402
import torch.nn.functional as F  # noqa: E402
from PIL import Image  # noqa: E402

import comfy.options  # noqa: E402

comfy.options.enable_args_parsing(False)
from comfy.cli_args import args  # noqa: E402

# bf16 halves the VAE's memory, so it decodes a whole strip at once on a GPU shared with other
# work. COMFY_CPU_VAE=1 decodes on the CPU instead (minutes a strip).
args.bf16_vae = True
args.cpu_vae = os.environ.get("COMFY_CPU_VAE") == "1"
import folder_paths  # noqa: E402,F401
import comfy.model_management  # noqa: E402
import nodes  # noqa: E402

sys.argv = sys.argv_saved


def tile_x(module):
    """Circular padding along X, zeros along Y, on every padded Conv2d under module."""
    count = 0
    for m in module.modules():
        if isinstance(m, torch.nn.Conv2d) and m.padding not in ((0, 0), 0, "valid"):
            py, px = m.padding

            def conv(x, weight, bias, m=m, px=px, py=py):
                x = F.pad(x, (px, px, 0, 0), mode="circular")
                x = F.pad(x, (0, 0, py, py), mode="constant")
                return F.conv2d(x, weight, bias, m.stride, 0, m.dilation, m.groups)

            m._conv_forward = conv
            count += 1
    return count


def load(ckpt, lora=None, lora_strength=0.8, tile=None):
    model, clip, vae = nodes.CheckpointLoaderSimple().load_checkpoint(ckpt)[:3]
    if lora:
        model, clip = nodes.LoraLoader().load_lora(model, clip, lora, lora_strength, lora_strength)
    if tile == "x":
        n = tile_x(model.model.diffusion_model) + tile_x(vae.first_stage_model)

        def refuse(*_, **__):
            # Out of memory, comfy retries in tiles, and a tile's outer edge is padded with zeros
            # and normalised on its own: the strip's two ends then no longer meet.
            raise RuntimeError("VAE ran out of memory: a tiled decode or encode would break the loop. "
                               "Free the GPU, or COMFY_CPU_VAE=1")

        vae.decode_tiled_ = vae.encode_tiled_ = refuse
        print(f"tile x: {n} convolutions padded circularly", flush=True)
    return model, clip, vae


def encode(clip, text):
    return nodes.CLIPTextEncode().encode(clip, text)[0]


def to_image(vae, latent):
    comfy.model_management.unload_all_models()  # the UNet's memory, for a decode in one piece
    img = nodes.VAEDecode().decode(vae, latent)[0]  # [B,H,W,C] 0..1
    return [Image.fromarray((i.cpu().numpy() * 255).clip(0, 255).astype(np.uint8)) for i in img]


def from_image(path, w=None, h=None):
    im = Image.open(path).convert("RGB")
    if w and h and im.size != (w, h):
        im = im.resize((w, h), Image.LANCZOS)
    return torch.from_numpy(np.asarray(im).astype(np.float32) / 255.0)[None]


def to_latent(vae, pixels):
    return nodes.VAEEncode().encode(vae, pixels)[0]


def sample(model, pos, neg, latent, seed, steps, cfg, denoise, sampler, scheduler):
    return nodes.common_ksampler(model, seed, steps, cfg, sampler, scheduler, pos, neg, latent, denoise=denoise)[0]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=["txt2img", "img2img", "variants"])
    ap.add_argument("--ckpt", default="dreamshaper_8.safetensors")
    ap.add_argument("--lora")
    ap.add_argument("--lora-strength", type=float, default=0.8)
    ap.add_argument("--prompt", required=True)
    ap.add_argument("--negative", default="text, watermark, signature, frame, border, blurry")
    ap.add_argument("--w", type=int, default=1024)
    ap.add_argument("--h", type=int, default=384)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--steps", type=int, default=28)
    ap.add_argument("--cfg", type=float, default=7.0)
    ap.add_argument("--sampler", default="dpmpp_2m")
    ap.add_argument("--scheduler", default="karras")
    ap.add_argument("--denoise", type=float, default=1.0)
    ap.add_argument("--tile", choices=["x", "none"], default="none")
    ap.add_argument("--init", help="img2img: start image; variants: the new middle's start image")
    ap.add_argument("--src", help="variants: the looping strip whose edges are kept")
    ap.add_argument("--n", type=int, default=3)
    ap.add_argument("--edge", type=int, default=96, help="variants: kept columns at each end, px")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()

    model, clip, vae = load(a.ckpt, a.lora, a.lora_strength, a.tile)
    pos, neg = encode(clip, a.prompt), encode(clip, a.negative)
    common = dict(steps=a.steps, cfg=a.cfg, sampler=a.sampler, scheduler=a.scheduler)

    if a.mode == "txt2img":
        latent = nodes.EmptyLatentImage().generate(a.w, a.h, 1)[0]
        out = sample(model, pos, neg, latent, a.seed, denoise=1.0, **common)
        to_image(vae, out)[0].save(a.out)
    elif a.mode == "img2img":
        latent = to_latent(vae, from_image(a.init, a.w, a.h))
        out = sample(model, pos, neg, latent, a.seed, denoise=a.denoise, **common)
        to_image(vae, out)[0].save(a.out)
    else:
        src = from_image(a.src)
        h, w = src.shape[1:3]
        # 1 = repaint, past `edge` px from each end, with a 16 px ramp. A wider ramp leaves a band
        # half source, half new, at every step: ghosts of trees. The sampler re-noises the kept
        # edges at each step, so the model itself joins the new middle to them.
        x = np.arange(w, dtype=np.float32)
        d = np.minimum(x, w - 1 - x)
        ramp = np.clip((d - a.edge) / 16, 0, 1)
        start = src
        if a.init:
            # A new silhouette for the middle (sil.py --edges-of): painted at img2img strength
            # it keeps the source's style, where a high denoise over the source drifts.
            r = torch.from_numpy(ramp)[None, None, :, None]
            start = src * (1 - r) + from_image(a.init, w, h) * r
        latent = to_latent(vae, start)
        mask = torch.from_numpy(np.tile(ramp, (h, 1)))[None]
        latent = nodes.SetLatentNoiseMask().set_mask(latent, mask)[0]
        base, ext = os.path.splitext(a.out)
        for i in range(a.n):
            out = sample(model, pos, neg, latent, a.seed + i, denoise=a.denoise, **common)
            img = np.asarray(to_image(vae, out)[0]).astype(np.float32)
            # The decoder sees the new middle beside the kept edges and shifts them by a level or
            # two: lay the source's own outer 8 px back, blended over 16 px, so every variant
            # starts and ends on exactly the source's columns. Only there: across the repainted
            # ramp, a cross-fade would leave ghosts of the source's trees.
            r = np.clip((d - 8) / 16, 0, 1)[None, :, None]
            img = src[0].cpu().numpy() * 255 * (1 - r) + img * r
            Image.fromarray(img.round().clip(0, 255).astype(np.uint8)).save(f"{base}_{i + 1}{ext}")
            print(f"{base}_{i + 1}{ext}", flush=True)


if __name__ == "__main__":
    # comfy's server runs every node under inference mode; called directly, autograd would keep
    # every activation and the VAE would run out of memory.
    with torch.inference_mode():
        main()
