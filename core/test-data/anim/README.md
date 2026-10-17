# The animation lab's beach (r407, r478)

`beach.jplax` is an AI page ("tropical beach, palm trees, warm sunny afternoon", `tools/ai-page.sh`, painted, seed 11)
in two paintings. Its sea, `l1`, is r365's (dreamshaper_8), the strip r375's `tools/r375-animated-layer/shaders.py`
animated and MIRROR's horizon is measured on (84 of its 384 rows, r455): `beach-sea.png`, never repainted. Its
mountains, dunes and palms (`l0`, `l2`, `l3_0`-`l3_3`) are r478's, painted by DreamShaperXL Turbo with the
Storybook LoRA (parallax-ai R3's "story" recipe, Simon on r455: mountains with detail, palms whole):

    tools/ai-page.sh --theme 'tropical beach, palm trees, warm sunny afternoon' --name beach --style painted \
        --kinds mountains,sea,dunes,palms --colours 8aa3b8,3fb0b0,e0c890,2a3a25 --sky 4f8fd0,cfe4f2 --seed 11 \
        --ckpt DreamShaperXL_Turbo_v2_1.safetensors --steps 8 --cfg 2 --sampler dpmpp_sde \
        --lora StorybookRedmondV2_XL.safetensors --lora-strength 0.9 OUT

then r365's `strips/l1_*` put in place of that run's and the page laid out again with `--keep` (the same flags), so
the layout is measured on the sea that ships. `beach.jplax` is that page as written. `beach.atlas`, `beach.png` and
`beach-sea.png` are its atlas, repacked by `make_lab.py` under the push check's 1 MB (the generated one was 6 MB): each
strip at 1024x384, the sea on a page of its own. The lab makes its sea a `SHADER` layer when it loads it. Never
re-export it: a lab judged on it is judged on these pixels.
