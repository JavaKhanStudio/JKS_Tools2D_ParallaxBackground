#!/usr/bin/env python3
"""r450's control: gen.py's DiT img2img in the order it loaded before r450 (transformer, text encoder, VAE, then the
encode), but with the text encoder freed once it has encoded, so the run fits a 10 GB cap. Its pixels against the
pre-r450 strip say whether a difference comes from where the encode ran or from the encoder kept in RAM.

    systemd-run --user --scope -p MemoryMax=10G -p MemorySwapMax=0 ~/ComfyUI/venv/bin/python \
        tools/r450-dit-ram/old_order.py PROMPT INIT SEED OUT
"""
import gc
import os
import sys

prompt, init, seed, out = sys.argv[1:5]
sys.argv = sys.argv[:1] + ["img2img"]
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "r239-ai-strips"))
import gen  # noqa: E402
from comfy_extras.nodes_model_advanced import ModelSamplingAuraFlow  # noqa: E402

nodes, mm = gen.nodes, gen.comfy.model_management
with gen.torch.inference_mode():
    model = gen.gguf_nodes().UnetLoaderGGUF().load_unet("z-image-turbo-Q5_K_M.gguf")[0]
    model = ModelSamplingAuraFlow().patch_aura(model, 3.0)[0]
    clip = nodes.CLIPLoader().load_clip("qwen_3_4b_fp8_mixed.safetensors", "lumina2")[0]
    vae = nodes.VAELoader().load_vae("z_image_ae.safetensors")[0]
    model = model.clone()
    model.set_model_unet_function_wrapper(gen.loop_wrapper("blend"))
    gen.tile_x(vae.first_stage_model)
    gen.refuse_tiled_vae(vae)
    pos = gen.encode(clip, prompt)
    neg = nodes.ConditioningZeroOut().zero_out(pos)[0]
    del clip
    mm.unload_all_models()
    mm.cleanup_models()
    gc.collect()
    mm.soft_empty_cache()
    latent = gen.to_latent(vae, gen.from_image(init, 1536, 576))
    res = gen.sample(model, pos, neg, latent, int(seed), 9, 1.0, 0.75, "res_multistep", "simple")
    gen.to_image(vae, res)[0].save(out)
