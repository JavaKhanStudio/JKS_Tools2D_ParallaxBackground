"""Generate parallax layer strips that loop, with the models of a local ComfyUI (r239).

Runs ComfyUI's nodes in-process, without its server, and leaves the install untouched:

    ~/ComfyUI/venv/bin/python tools/r239-ai-strips/gen.py txt2img --prompt "..." --out a.png --tile x
    ~/ComfyUI/venv/bin/python tools/r239-ai-strips/gen.py img2img --init sil.png --denoise 0.75 --tile x ...
    ~/ComfyUI/venv/bin/python tools/r239-ai-strips/gen.py variants --src a.png --n 3 --edge 96 --tile x ...
    ... img2img ... --control mask.png --control-edge --control-strength 1.0   (r244)
    ~/ComfyUI/venv/bin/python tools/r239-ai-strips/gen.py inpaint --src a.png --mask hole.png --tile x ...   (r279)

--tile x pads every convolution of the UNet and the VAE circularly along X (zeros along Y): the
model then draws on a cylinder, so the strip's right edge continues into its left one.
`variants` repaints the middle of a looping strip and keeps its edge columns: every variant
starts and ends on the same pixels, so a SEQUENCE layer can put any of them after any other.
`inpaint` repaints where --mask is white (grey: partly) and keeps every other pixel of --src exactly: what a
layer cut from one painting (r279) shows where a nearer thing stood. With an inpainting checkpoint (9 input
channels: --ckpt dreamshaper_8Inpainting.safetensors, r315) the model is shown the mask and the source round it
(InpaintModelConditioning); with any other it only starts from the source under the mask (SetLatentNoiseMask), so
fill the hole with plausible colour first either way: below denoise 1 the start still shows.
--control guides the sampling with a ControlNet (SD 1.5 lineart by default) fed that image; --control-edge
turns a silhouette mask into its outline first (white lines on black, wrapping along X). With --tile x the
ControlNet's convolutions are padded circularly too: its hint feeds every block of the UNet, and a hint
padded with zeros at the ends would pull the strip's two ends apart.

A --ckpt ending in .gguf is a DiT (r356; parallax-ai r23 proved it with tools/dit/zloop.py): Z-Image-Turbo by
default, its transformer through ComfyUI-GGUF's UnetLoaderGGUF, --dit-clip (lumina2) and --dit-vae beside it, 9 steps
at cfg 1 (res_multistep, simple) unless --steps and the rest say otherwise, the negative zeroed out (cfg 1 never reads
it), a 16-channel latent. A transformer has no convolution to pad: with --tile x a unet wrapper (--dit-loop) makes
its prediction periodic at every step, and only the VAE is padded. No ControlNet with a DiT.
"""
import argparse
import gc
import importlib.util
import math
import os
import sys

COMFY = os.environ.get("COMFYUI_DIR", os.path.expanduser("~/ComfyUI"))
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import gpu_lock  # noqa: E402

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ram  # noqa: E402

# r391: a run is one GPU step, comfy's imports too (they open a CUDA context): the GPU is taken before them and
# dropped when the process exits, so another lane's run waits instead of running out of memory beside this one.
GPU = None if {"-h", "--help"} & set(sys.argv) else gpu_lock.take("gen.py " + (sys.argv[1:2] or ["?"])[0])
sys.path.insert(0, COMFY)
sys.argv_saved, sys.argv = sys.argv, [sys.argv[0]]  # comfy parses argv on import

import comfy.options  # noqa: E402
import numpy as np  # noqa: E402
import torch  # noqa: E402
import torch.nn.functional as F  # noqa: E402
from PIL import Image  # noqa: E402

comfy.options.enable_args_parsing(False)
from comfy.cli_args import args  # noqa: E402

# bf16 halves the VAE's memory, so it decodes a whole strip at once on a GPU shared with other
# work. COMFY_CPU_VAE=1 decodes on the CPU instead (minutes a strip).
args.bf16_vae = True
args.cpu_vae = os.environ.get("COMFY_CPU_VAE") == "1"
import comfy.model_management  # noqa: E402
import folder_paths  # noqa: E402,F401
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


def is_dit(ckpt):
    return ckpt.endswith(".gguf")


def refuse_tiled_vae(vae):
    def refuse(*_, **__):
        # Out of memory, comfy retries in tiles, and a tile's outer edge is padded with zeros
        # and normalised on its own: the strip's two ends then no longer meet.
        raise RuntimeError("VAE ran out of memory: a tiled decode or encode would break the loop. "
                           "Free the GPU, or COMFY_CPU_VAE=1")

    vae.decode_tiled_ = vae.encode_tiled_ = refuse


def load(ckpt, lora=None, lora_strength=0.8, tile=None):
    model, clip, vae = nodes.CheckpointLoaderSimple().load_checkpoint(ckpt)[:3]
    if lora:
        model, clip = nodes.LoraLoader().load_lora(model, clip, lora, lora_strength, lora_strength)
    if tile == "x":
        n = tile_x(model.model.diffusion_model) + tile_x(vae.first_stage_model)
        refuse_tiled_vae(vae)
        print(f"tile x: {n} convolutions padded circularly", flush=True)
    return model, clip, vae


def gguf_nodes():
    """ComfyUI-GGUF's nodes, imported from COMFY/custom_nodes as a package (its own name clashes with comfy's nodes)."""
    path = os.path.join(COMFY, "custom_nodes", "ComfyUI-GGUF")
    spec = importlib.util.spec_from_file_location("comfyui_gguf", os.path.join(path, "__init__.py"),
                                                  submodule_search_locations=[path])
    mod = importlib.util.module_from_spec(spec)
    sys.modules["comfyui_gguf"] = mod
    spec.loader.exec_module(mod)
    return sys.modules["comfyui_gguf.nodes"]


def loop_wrapper(mode, patch=2):
    """A unet function wrapper making a transformer's prediction periodic along X (parallax-ai r23's zloop.py).
    roll: the latent rolled by a fresh offset (whole patches) before the model and back after, so the join lands
    somewhere new each step (1 model call a step). blend: the model sees the latent as it is and rolled half a width,
    the two predictions mixed column by column, sin^2 of the distance to each copy's own join, so no column is taken
    from a copy whose edge it sits on (2 model calls a step)."""
    state = {"step": 0}

    def wrap(apply_model, args):
        x, t, c = args["input"], args["timestep"], args["c"]
        w = x.shape[-1]
        if mode == "roll":
            g = torch.Generator().manual_seed(1000 + state["step"])
            s = int(torch.randint(0, w // patch, (1,), generator=g)) * patch
            state["step"] += 1
            return torch.roll(apply_model(torch.roll(x, s, -1), t, **c), -s, -1)
        s = (w // 2) // patch * patch
        a = apply_model(x, t, **c)
        b = torch.roll(apply_model(torch.roll(x, s, -1), t, **c), -s, -1)
        col = (torch.arange(w, device=x.device, dtype=a.dtype) + 0.5) / w
        wa = torch.sin(math.pi * col) ** 2  # 0 at this copy's join (columns 0 and w-1), 1 in its middle
        return a * wa + b * (1 - wa)

    return wrap


# r450: a Z-Image strip's anonymous RSS peaks at 6.91 GB from gen.py's start (1536x576 img2img, the text encoder freed
# before the transformer, tools/r450-dit-ram/peak.py), half a GB over it for a larger strip's decode.
DIT_PEAK = int(7.4 * ram.GB)
RAM_REFUSED = 3  # gen.py's exit code when a DiT would not fit in RAM: ai_page.py prints the log's last line


def refuse_short_ram(peak):
    """Exits RAM_REFUSED, saying how much is missing, when the run cannot take `peak` bytes of RAM before the kernel
    (or its scope's MemoryMax) kills it halfway, as it did at 12.2 GB on 2026-10-16 (r450)."""
    short = ram.shortfall(peak)
    if short:
        print(f"gen.py: not enough RAM for a DiT: it peaks at {peak / ram.GB:.1f} GB, "
              f"{(peak - short) / ram.GB:.1f} GB can be taken (MemAvailable, or a cgroup's MemoryMax): "
              f"free {short / ram.GB:.1f} GB", file=sys.stderr, flush=True)
        sys.exit(RAM_REFUSED)


def encode_dit(prompt, clip_name, clip_type):
    """A DiT's prompt, encoded and its text encoder freed before the transformer loads (r450): the encoder takes
    ~5 GB of RAM while it encodes, and kept beside the sampling it took the run to 12 GB, which the kernel killed."""
    clip = nodes.CLIPLoader().load_clip(clip_name, clip_type)[0]
    pos = encode(clip, prompt)
    del clip
    comfy.model_management.unload_all_models()
    comfy.model_management.cleanup_models()
    gc.collect()
    comfy.model_management.soft_empty_cache()
    return pos


def load_dit(unet, vae_name, shift, tile=None, loop="blend"):
    from comfy_extras.nodes_model_advanced import ModelSamplingAuraFlow

    model = gguf_nodes().UnetLoaderGGUF().load_unet(unet)[0]
    model = ModelSamplingAuraFlow().patch_aura(model, shift)[0]
    vae = nodes.VAELoader().load_vae(vae_name)[0]
    if tile == "x":
        if loop != "none":
            model = model.clone()
            model.set_model_unet_function_wrapper(loop_wrapper(loop))
        n = tile_x(vae.first_stage_model)
        refuse_tiled_vae(vae)
        print(f"tile x: DiT looped by {loop}, {n} VAE convolutions padded circularly", flush=True)
    return model, vae


def control_image(path, w, h, edge):
    """[1,H,W,3] 0..1. edge: the outline of a mask, where it changes from one pixel to the next."""
    im = Image.open(path).convert("L").resize((w, h), Image.LANCZOS)
    a = np.asarray(im).astype(np.float32) / 255.0
    if edge:
        m = a > 0.5
        e = (m != np.roll(m, 1, axis=1)) | (m != np.roll(m, -1, axis=1))
        e[1:] |= m[1:] != m[:-1]
        e[:-1] |= m[:-1] != m[1:]
        a = e.astype(np.float32)
    return torch.from_numpy(np.repeat(a[..., None], 3, axis=2))[None]


def apply_control(pos, neg, a):
    cnet = nodes.ControlNetLoader().load_controlnet(a.control_model)[0]
    if a.tile == "x":
        print(f"tile x: {tile_x(cnet.control_model)} ControlNet convolutions padded circularly", flush=True)
    hint = control_image(a.control, a.w, a.h, a.control_edge)
    return nodes.ControlNetApplyAdvanced().apply_controlnet(pos, neg, cnet, hint, a.control_strength, 0.0, a.control_end)


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


def parse_args():
    ap = argparse.ArgumentParser()
    ap.add_argument("mode", choices=list(MODES))
    ap.add_argument("--ckpt", default="dreamshaper_8.safetensors")
    ap.add_argument("--lora")
    ap.add_argument("--lora-strength", type=float, default=0.8)
    ap.add_argument("--prompt", required=True)
    ap.add_argument("--negative", default="text, watermark, signature, frame, border, blurry")
    ap.add_argument("--w", type=int, default=1024)
    ap.add_argument("--h", type=int, default=384)
    ap.add_argument("--seed", type=int, default=1)
    ap.add_argument("--steps", type=int, help="default 28, a DiT 9")
    ap.add_argument("--cfg", type=float, help="default 7.0, a DiT 1.0")
    ap.add_argument("--sampler", help="default dpmpp_2m, a DiT res_multistep")
    ap.add_argument("--scheduler", help="default karras, a DiT simple")
    ap.add_argument("--dit-clip", default="qwen_3_4b_fp8_mixed.safetensors", help="a DiT's text encoder")
    ap.add_argument("--dit-clip-type", default="lumina2")
    ap.add_argument("--dit-vae", default="z_image_ae.safetensors")
    ap.add_argument("--dit-shift", type=float, default=3.0, help="ModelSamplingAuraFlow's shift")
    ap.add_argument("--dit-loop", choices=["blend", "roll", "none"], default="blend",
                    help="with --tile x, how a DiT's prediction is made to loop (none: the VAE alone wraps)")
    ap.add_argument("--denoise", type=float, default=1.0)
    ap.add_argument("--tile", choices=["x", "none"], default="none")
    ap.add_argument("--init", help="img2img: start image; variants: the new middle's start image")
    ap.add_argument("--src", help="variants: the looping strip whose edges are kept; inpaint: the image to repaint")
    ap.add_argument("--mask", help="inpaint: white where to repaint")
    ap.add_argument("--noise-mask", action="store_true", help="inpaint, inpainting model: sample inside the mask only "
                    "(r315: leaves a pale veil)")
    ap.add_argument("--n", type=int, default=3)
    ap.add_argument("--edge", type=int, default=96, help="variants: kept columns at each end, px")
    ap.add_argument("--control", help="ControlNet hint image (a silhouette mask with --control-edge)")
    ap.add_argument("--control-model", default="control_v11p_sd15_lineart_fp16.safetensors")
    ap.add_argument("--control-strength", type=float, default=1.0)
    ap.add_argument("--control-end", type=float, default=1.0, help="fraction of the steps the hint guides")
    ap.add_argument("--control-edge", action="store_true", help="feed the hint's outline, not the image")
    ap.add_argument("--out", required=True)
    a = ap.parse_args()
    for key, value in (DIT_SAMPLING if is_dit(a.ckpt) else SAMPLING).items():
        if getattr(a, key) is None:
            setattr(a, key, value)
    if is_dit(a.ckpt) and (a.control or a.lora):
        ap.error("--control and --lora: not with a DiT")
    return a


SAMPLING = {"steps": 28, "cfg": 7.0, "sampler": "dpmpp_2m", "scheduler": "karras"}
DIT_SAMPLING = {"steps": 9, "cfg": 1.0, "sampler": "res_multistep", "scheduler": "simple"}  # Z-Image-Turbo's


def txt2img(model, pos, neg, vae, a, common):
    if is_dit(a.ckpt):  # EmptySD3LatentImage: 16 channels
        latent = {"samples": torch.zeros(1, 16, a.h // 8, a.w // 8), "downscale_ratio_spacial": 8}
    else:
        latent = nodes.EmptyLatentImage().generate(a.w, a.h, 1)[0]
    out = sample(model, pos, neg, latent, a.seed, denoise=1.0, **common)
    to_image(vae, out)[0].save(a.out)


def img2img(model, pos, neg, vae, a, common):
    latent = to_latent(vae, from_image(a.init, a.w, a.h))
    out = sample(model, pos, neg, latent, a.seed, denoise=a.denoise, **common)
    to_image(vae, out)[0].save(a.out)


def inpaint(model, pos, neg, vae, a, common):
    src = from_image(a.src)
    m = np.asarray(Image.open(a.mask).convert("L")).astype(np.float32) / 255.0
    mask = torch.from_numpy(m)[None]
    blocks = getattr(model.model.diffusion_model, "input_blocks", None)  # a DiT has none
    if blocks is not None and blocks[0][0].in_channels == 9:
        # An inpainting model (dreamshaper_8Inpainting, r315) is fed the source with the hole greyed out beside
        # the noise: it sees the mask and what surrounds it, and matches its light and style. No noise mask: it
        # paints the whole image and only the mask's pixels are kept below. With one (--noise-mask), the kept latent
        # is laid back at every step, and the repaint comes out under a pale veil with a hard edge.
        pos, neg, latent = nodes.InpaintModelConditioning().encode(pos, neg, src, vae, mask, a.noise_mask)
    else:
        latent = nodes.SetLatentNoiseMask().set_mask(to_latent(vae, src), mask)[0]
    out = sample(model, pos, neg, latent, a.seed, denoise=a.denoise, **common)
    img = np.asarray(to_image(vae, out)[0]).astype(np.float32)
    # The decoder shifts every pixel by a level or two: only the mask's own pixels are taken from it.
    img = src[0].cpu().numpy() * 255 * (1 - m[..., None]) + img * m[..., None]
    Image.fromarray(img.round().clip(0, 255).astype(np.uint8)).save(a.out)


def variants(model, pos, neg, vae, a, common):
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


MODES = {"txt2img": txt2img, "img2img": img2img, "variants": variants, "inpaint": inpaint}


def main():
    a = parse_args()
    if is_dit(a.ckpt):
        refuse_short_ram(DIT_PEAK)
        pos = encode_dit(a.prompt, a.dit_clip, a.dit_clip_type)
        model, vae = load_dit(a.ckpt, a.dit_vae, a.dit_shift, a.tile, a.dit_loop)
        neg = nodes.ConditioningZeroOut().zero_out(pos)[0]
    else:
        model, clip, vae = load(a.ckpt, a.lora, a.lora_strength, a.tile)
        pos, neg = encode(clip, a.prompt), encode(clip, a.negative)
    if a.control:
        pos, neg = apply_control(pos, neg, a)
    common = {"steps": a.steps, "cfg": a.cfg, "sampler": a.sampler, "scheduler": a.scheduler}
    MODES[a.mode](model, pos, neg, vae, a, common)


if __name__ == "__main__":
    # comfy's server runs every node under inference mode; called directly, autograd would keep
    # every activation and the VAE would run out of memory.
    with torch.inference_mode():
        main()
