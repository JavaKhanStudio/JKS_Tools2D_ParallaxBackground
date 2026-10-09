# AI-made parallax layers that loop

Research for atelier r239 (2026-10-10). Can an AI make parallax layers that loop pixel-perfectly, and loops whose
pieces are not all the same? Yes, with a local Stable Diffusion and four passes. Everything below was run on this
machine. `tools/r239-ai-strips/run.sh` reproduces it bit for bit, and `tools/r239-ai-strips/page` is the page it makes,
drawn by `core` (`tools/parallax-lab-shots.sh tools/r239-ai-strips/page build/lab/r239`).

## What works

| Pass | Tool | What it does |
|------|------|--------------|
| 1. Shape | `sil.py` | Draws the layer's silhouette (ridge, hills, treeline) as a sum of sines of whole frequencies over the width, with trees placed on a cylinder. It loops by construction, like an SVG would, and it stays editable: a seed and a few numbers. |
| 2. Paint | `gen.py img2img --tile x` | SD 1.5 (`dreamshaper_8`) repaints the flat silhouette at denoise 0.65, with every convolution of the UNet and the VAE padded **circularly along X** (circular padding). The model paints on a cylinder, so the strip's right edge runs on into its left one. Spikes come out as real pines. |
| 3. Vary | `sil.py --edges-of` + `gen.py variants` | More strips, **edge-locked variants**, that start and end on the first one's columns: a new silhouette whose ends are the first one's, painted only in the middle (noise mask), then the first strip's outer 8 px laid back exactly. **Any variant can follow any other**, so one `SEQUENCE` layer chains them in a different order every cycle. |
| 4. Cut | `layer.py` | Keys the flat sky out to alpha, takes edge colours from solid neighbours (no light rim), and fails on art cut at the top row. `--pixel 4 --colours 10`: box-downscales, quantises to one palette (`--palette`: shared by all segments of a sequence) and cuts alpha hard, for a page drawn `Nearest`. |

Measured on the cut layers (`seam.py`: the colour step at the join divided by the median step inside the picture;
1 means the join is as smooth as the picture itself):

- a plain txt2img strip drawn twice: **44.4**, a cliff cut in half;
- the same seed, circular X: **1.28**;
- painted layers after pass 4: **0.46 to 0.74**. Pine variants chained 0, 2, 1, 3: **1.10 to 1.13**, and the alpha
  steps at the joins (1.6 to 1.7) are below the median inside (1.9 to 2.4).

Cost: about 14 s a strip at 1024x384 (SD 1.5, 28 steps) on the RTX 5060 laptop GPU, with ollama holding 3.7 GB of its
8 GB. Nothing was downloaded: `~/ComfyUI` already had the models. `gen.py` runs ComfyUI's nodes in-process, with no
server, and leaves the install as it is.

## What each idea from the card came to

- **SVG.** It is pass 1: a shape that loops exactly. It doesn't replace the painting, it feeds it. `sil.py` writes
  pixels, but an SVG path drawn the same way, through whole-frequency curves, would do the same job.
- **Stable Diffusion detail.** Kept, in pass 2. Circular padding is what makes it loop, not a prompt or a mask. It costs
  nothing: the same model and the same speed.
- **ControlNet.** Not needed for the loop. It might hold a silhouette at a higher denoise than img2img's 0.65 (more
  detail, same shape). `control_v11p_sd15_lineart` is on disk. Untested, queued.
- **Several passes.** Yes, these four. Each one can be checked on its own (`seam.py`, `layer.py`'s top-row check,
  lint), and a page is made from their output.
- **Pixel-perfect.** Two meanings, two answers. *Loops to the pixel*: yes, see the numbers. *Pixel art*: the SD 1.5
  downscale (`--pixel`) looks like a painting made small. SDXL with the `PixelArt_XL` LoRA (also on disk) paints real
  pixel art (16 px cells, clean outlines) and loops the same way, but it took **363 s** a strip with ollama loaded
  (SDXL gets offloaded in parts). It also painted clouds into the sky, so the sky can't be keyed out yet. Queued.

## Traps (each one cost a wrong result first)

- **Inference mode.** ComfyUI's server runs nodes under `torch.inference_mode()`. Called directly, they don't, so
  autograd keeps every activation and the VAE runs out of memory.
- **Tiled VAE fallback.** When ComfyUI runs out of memory, it decodes in tiles. Each tile is padded with zeros and
  normalised on its own, so the strip's two ends differ by 3 to 4 levels: a seam no picture shows at first glance.
  `gen.py` decodes in bf16 and *refuses* the fallback (it raises), so a seam can't ship silently. Gluing the strip's
  other end on before decoding does not fix it: a circular VAE then wraps at the wrong period.
- **Fixed key thresholds.** A pixel half sky, half hill counts as solid, and draws a 1 px light rim on screen. The ramp
  ends at 0.85 times the layer's own distance from the sky.
- **Variants.** Repainting the first strip's middle at denoise 0.9 drifts the style (grass, cloud ghosts). A 96 px soft
  noise mask leaves ghost trees, half old and half new. What works: a new silhouette at the same 0.65, with a 16 px
  ramp.
- **One palette per sequence.** Pixel-art segments quantised one by one change colour at every join (ratio 4 to 7).
- **SD grows pines** past their silhouette, up to the strip's top row, where they are cut flat. Leave headroom.
- **seam.py on pixel art.** The ratio means nothing there: the median step inside a cell is 0. Look at the alpha step,
  and with your eyes.

## Still wrong in the frames

- SD adds objects nobody asked for: a black bird in the mountain strip, keyed in as part of the layer.
- Each layer is painted on its own: nothing makes their colours agree. The page's depth fog (`fogStrength`, r217) can
  give the distance, so layers could be painted at full contrast.

## Plan

Tasks under r239, tagged `#assets`:

1. r242: `layer.py` drops the alpha islands that don't touch the layer's mass (the bird).
2. r243: pixel art through SDXL + `PixelArt_XL`: a sky that can be keyed out, the cell grid found and sampled one art
   pixel per cell, and the time measured on a free GPU.
3. r244: ControlNet lineart over the silhouette at denoise 0.8 to 0.9, against img2img at 0.65: more detail, same shape?
4. r245 (`#runtime`): lint (`tools/parallax_lab.py`) skips `SEQUENCE` layers in its layout checks, so it neither
   counts them as covering (a false (b) on this page) nor checks their joins.
5. r246, after r242 to r244: from a prompt to a page. `run.sh` as one tool (theme, layer count, size, pixel or
   painted), and a section in the `parallax-pages` skill, once 1 to 3 have settled what goes in it.
