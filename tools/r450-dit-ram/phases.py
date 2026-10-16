#!/usr/bin/env python3
"""r450: a DiT's anonymous RSS after each of gen.py's load steps (unet, text encoder, VAE, encode, free), in the
ComfyUI venv, on gen.py's own loaders. Run it capped:

    systemd-run --user --scope -p MemoryMax=10G -p MemorySwapMax=0 ~/ComfyUI/venv/bin/python tools/r450-dit-ram/phases.py
"""
import gc
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "r239-ai-strips"))
import gen


def anon(what):
    with open("/proc/self/status") as f:
        kb = next(int(line.split()[1]) for line in f if line.startswith("RssAnon:"))
    print(f"{kb / 2**20:6.2f} GB  {what}", flush=True)


with gen.torch.inference_mode():
    anon("imports")
    clip = gen.nodes.CLIPLoader().load_clip("qwen_3_4b_fp8_mixed.safetensors", "lumina2")[0]
    anon("text encoder loaded")
    pos = gen.encode(clip, "a misty pine forest")
    anon("encoded")
    del clip
    gen.comfy.model_management.unload_all_models()
    gen.comfy.model_management.cleanup_models()
    gc.collect()
    gen.comfy.model_management.soft_empty_cache()
    anon("text encoder freed")
    model = gen.gguf_nodes().UnetLoaderGGUF().load_unet("z-image-turbo-Q5_K_M.gguf")[0]
    anon("unet loaded")
    vae = gen.nodes.VAELoader().load_vae("z_image_ae.safetensors")[0]
    anon("vae loaded")
