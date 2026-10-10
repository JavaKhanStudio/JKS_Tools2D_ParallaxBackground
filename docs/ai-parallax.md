# AI-made parallax layers that loop

Research for atelier r239 (2026-10-10). Can an AI make parallax layers that loop pixel-perfectly, and loops whose
pieces are not all the same? Yes, with a local Stable Diffusion and four passes. Everything below was run on this
machine. `tools/r239-ai-strips/run.sh` reproduces it bit for bit, and `tools/r239-ai-strips/page` is the page it makes,
drawn by `core` (`tools/parallax-lab-shots.sh tools/r239-ai-strips/page build/lab/r239`).

**From a theme to a page, in one command (r246):** `tools/ai-page.sh --theme "snowy pine valley at dawn" --colours
8f9bb8,6f88a0,1c3036 OUT` runs the four passes below for each depth, packs the atlas, writes the `.jplax` by the
`parallax-pages` skill's rules (its "Generating a page" section), lints it and renders it. `--help` has the rest.

**Examples (r239, 2026-10-10):** a forest, a beach and a post-apocalyptic city, each painted and pixel:
`tools/r239-ai-strips/examples.sh OUT` paints the six pages and films them (`tools/ai-film.sh`). The beach and the
city needed shapes of their own in `sil.py` (`dunes`, `sea`, `palms`, `ruins`, chosen with `--kinds`); a theme that
names what another layer draws ("turquoise sea") gets it painted faintly into every layer's sky.

## What works

| Pass | Tool | What it does |
|------|------|--------------|
| 1. Shape | `sil.py` | Draws the layer's silhouette (ridge, hills, treeline) as a sum of sines of whole frequencies over the width, with trees placed on a cylinder. It loops by construction, like an SVG would, and it stays editable: a seed and a few numbers. |
| 2. Paint | `gen.py img2img --tile x` | SD 1.5 (`dreamshaper_8`) repaints the flat silhouette at denoise 0.65, with every convolution of the UNet and the VAE padded **circularly along X** (circular padding). The model paints on a cylinder, so the strip's right edge runs on into its left one. Spikes come out as real pines. |
| 3. Vary | `sil.py --edges-of` + `gen.py variants` | More strips, **edge-locked variants**, that start and end on the first one's columns: a new silhouette whose ends are the first one's, painted only in the middle (noise mask), then the first strip's outer 8 px laid back exactly. **Any variant can follow any other**, so one `SEQUENCE` layer chains them in a different order every cycle. |
| 4. Cut | `layer.py` | Keys the flat sky out to alpha, takes edge colours from solid neighbours (no light rim), cuts the alpha islands apart from the layer's mass (around the loop; `--keep-islands` for clouds), their faint edge down to the alpha around it, so a speck in half-keyed haze leaves the haze, not a square hole (r264), and fails on art cut at the top row. `--pixel 4 --colours 10`: box-downscales, quantises to one palette (`--palette`: shared by all segments of a sequence) and cuts alpha hard, for a page drawn `Nearest`. |

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
- **ControlNet.** Not needed for the loop, and not kept for the shape either (r244): img2img at 0.65 stays. `gen.py
  --control MASK --control-edge` feeds `control_v11p_sd15_lineart` the silhouette's outline (its convolutions padded
  circularly too; seams 0.84 to 1.16, as good as img2img's). At denoise 0.8 and 0.9 it holds the outline *literally*:
  silhouette IoU hills 0.996 against img2img's 0.984, pines 0.990 at 0.8 against 0.928. That is the trouble. The pines
  stay `sil.py`'s sine spikes with no tree in them, where img2img's lower IoU is SD growing real pines past the crude
  shape. And the higher denoise paints the sky: grey bands and clouds the key keeps (both strips at 0.9, pines at 0.8,
  art up to the top row), and a light rim along the line on the hills. `tools/r239-ai-strips/r244-control.sh` paints
  the ways; its `compare.png` is on r244 and r257. A weaker hint (strength 0.5, first 60% of the steps, denoise 0.8:
  r257) does not beat img2img 0.65 either: the pines are still the sine spikes, no tree in them (IoU 0.952), and the
  freed sky gets painted all the same, a grey cloud band and a blue mountain range behind the pines, a grey haze and
  clouds over the hills (IoU 0.991), so `layer.py` refuses both (art cut flat at the top edge, 228 and 318 columns).
  Seams 0.56 hills, 1.16 pines. Weak or full, the hint at 0.8 paints no trees: img2img 0.65 stays, `run.sh` unchanged.
- **Several passes.** Yes, these four. Each one can be checked on its own (`seam.py`, `layer.py`'s top-row check,
  lint), and a page is made from their output.
- **Pixel-perfect.** Two meanings, two answers. *Loops to the pixel*: yes, see the numbers. *Pixel art*: the SD 1.5
  downscale (`--pixel`) looks like a painting made small. SDXL with the `PixelArt_XL` LoRA (also on disk) paints real
  pixel art (16 px cells, clean outlines) and loops the same way. r243 cuts it to true pixel art: see below.

## True pixel art (r243)

`tools/r239-ai-strips/run-xl.sh build/r243` paints mountains, hills and pines with SDXL (`sdXL_v10VAEFix`) and the
`PixelArt_XL` LoRA at strength 1.0, img2img at denoise 0.7 over `sil.py`'s silhouettes drawn at 1536x576, circular X.
`pixel.py` then cuts each one to **one PNG pixel per art pixel**, and `make_page.py --xl` packs them as
`page/pixel-xl.atlas` (`Nearest`), the round's scene `g03`.

- **The grid is read from the picture.** Colour steps between neighbour columns pile up on the cell borders: folded
  at the right period, the step profile has one tall peak (8 to 10 times its mean). All three strips: 16 px cells,
  phase 15, 96x35 art pixels. The smallest period within 85% of the best score is taken, or twice the cell (half the
  borders, the same peak) would win. A cell becomes the median of its inner half, since the VAE blurs the borders.
  The count across is rounded to divide the width, and sampling wraps, so the small strip loops too.
- **The sky is keyed, then gated by the silhouette.** SDXL paints a sky in two tones, a haze range and clouds, whatever
  the prompt asks (it was asked for a flat sky and given "clouds" as a negative). A colour key alone can't tell a pale
  ridge from that sky. `--mask` makes a cell opaque only if it keys AND lies within one cell of `sil.py`'s silhouette,
  so nothing SD paints in the open sky survives: the hills strip's clouds are gone with no prompt work.
- **One palette per layer, 12 colours.** A palette shared by three layers of different hues starved each of them: with
  16 shared colours the mountains were left 6, and the pines lost their trunks and highlights. The segments of *one*
  `SEQUENCE` layer still share one (`--palette`).
- **Loops:** `seam.py` alpha step at the join is 0 (inside 0) for mountains and hills, 14.6 (inside 21.9) for pines.
  Lint passes the page: its (e) once flagged the pines (seam 58 > 20, a fixed limit below the strip's own inner
  column steps, 27 to 75) until r253 held a seam to the art's own steps.
- **Time**, 28 steps at 1536x576 on the RTX 5060 laptop (8 GB): with ollama holding about 4.1 GB, **79 s, 77 s and
  43 s** (mountains, hills, pines; the sampling alone is 63 s at 2.3 s an iteration). r239's 363 s, under
  the same ollama load, is not explained (not measured again). With the GPU free (r255, 119 MiB
  in use before mountains and hills, 564 MiB before pines): **28.0 s, 25.1 s and 23.5 s**, the sampler at 2.07
  iterations a second. Ollama's 4 GB costs SDXL about three times its time. The same seeds on a free GPU paint a
  different strip: a quarter of the cut atlas's pixels (2608 of 10272) differ from the one made under ollama, so a
  strip is reproducible only under the same GPU load.

Still wrong: a 1-cell speck can float in the sky next to the silhouette (a cell inside the mask's one-cell slack that
keys as art): `pixel.py`'s island cut removes it since r242. And SDXL drifts off the silhouette: it painted the low crests between
the mountains as sky, so the far range reads as a flat band. SD 1.5 lineart held an outline but painted no detail in
it (r244); an SDXL ControlNet is untried.

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
- **The seed grows the trees, not the prompt** (r246). Over one pine shape, seed 13 paints real pines and seed 130 leaves
  `sil.py`'s smooth spikes, with or without the theme in the prompt. `ai_page.py` measures the cut's outline (mean
  second difference of its top edge: trees 4.4 to 5.1, spikes 1.9 to 2.3, the bare shape 1.1) and repaints a pine
  strip under 3.5 on the next seed.
- **Snow under a pale sky keys half clear** (r246): the colour key reads it as sky, and the layer shows holes. And at
  0.65 SD paints a horizon behind a hill strip (a snowy plain, lakes), which the key keeps. `layer.py --mask --grow`
  takes `sil.py`'s shape too: opaque 8 px inside it (SD moves an outline by a few px: less lets sky in at the peaks),
  keyed by colour within `--grow` px of it (8, the pines 40: they grow past it), clear beyond.
- **The foot of a gap between trees** (r263): SD paints a gap deeper than the silhouette, and its foot, sky or far
  haze, stood opaque inside the shape: a pale spot in a dark treeline (snow `l3v2` at 451,338: 156 such px within 12 px, 0 now), and sky shards along the
  desert mesas and snowy peaks. `layer.py --gap 0.6` (the default) places each pixel on the line from the sky (0) to
  the mass's median colour (1); the pixels under 0.6, and not whiter than the sky (snow), that reach the clear sky
  through each other are keyed along it, so the foot fades clear and the edge takes the trees' colour.
  `tools/r263-gap/pale.py` counts what stays pale and opaque near a point.
- **A join on a tree's flank** (r246): the strip loops, but its join fell where the outline steps steeply, above three
  quarters of the strip's own column steps, and a `SEQUENCE` repeats that join at every segment: lint (e) at 21 and 22
  against 20. `sil.py --quiet-join` turns the shape so the join falls on its flattest 16 px; variants turn by their
  kept seed's, so they still fit.
- **The sky colour is the prompt's**, always pale: a sunset or a night needs `--sky` (and so the fog colour, the sky's
  horizon). `--keep` lays the page out again from the strips already painted.

## Still wrong in the frames

- r242's "bird" in the mountain strip was not painted by SD: `layer.py` drew black a pale ridge too far from any solid
  pixel to take an edge colour (0/0). Fixed. `run.sh`'s negative prompt names birds, animals and creatures: a creature
  would stand still in a scrolling background. Since r261, `layer.py` and `pixel.py` exit 4 on one SD paints *inside* a
  layer's mass (`creature.py`), and `ai-page.sh` paints the strip again: a blob far in colour from the mass around it,
  50 to 3000 px in a 1024 px strip (six art pixels at least in pixel art), not touching the mass's edge, and alone:
  no other blob of its colour, shape and size, as snow on pines or trees on a slope come in crowds.
  `tools/r261-creature/sweep.sh` passes every strip on disk (r239, r242, r243, r246: 27) and flags the 8 creatures it
  plants (`plant.py`: birds and deer on mountains, hills, pines, snowy pines, among painted trees, in pixel art); 15
  strips it was not tuned on (r244, r239-run) pass too. `mutate.sh` takes each rule out in turn and the sweep fails.
  Missed by design: a creature on the silhouette's edge, and one the colour, shape and size of the layer's own flecks
  (a white bird among snow flecks).
- Each layer is painted on its own: nothing makes their colours agree but `--colours`, which the init image carries.
  `ai-page.sh` paints them at full contrast and gives the distance with the page's depth fog (`fogStrength`, r217).

## Plan

Tasks under r239, tagged `#assets`:

1. r242 (done): the bird was `layer.py`'s own black fill; fixed, and `layer.py`/`pixel.py` drop the alpha islands.
2. r243 (done): pixel art through SDXL + `PixelArt_XL`, see "True pixel art".
3. r244 (done): ControlNet lineart over the silhouette at 0.8 to 0.9 holds the outline and loses the trees: img2img 0.65
   stays (see "What each idea from the card came to"). r257 (done): a weak hint (0.5, first 60%) at 0.8
   does no better: spikes, and a painted sky.
4. r245 (`#runtime`): lint (`tools/parallax_lab.py`) skips `SEQUENCE` layers in its layout checks, so it neither
   counts them as covering (a false (b) on this page) nor checks their joins.
5. r246 (done): from a theme to a page, `tools/ai-page.sh`, and the `parallax-pages` skill's "Generating a page".
6. r261 (done): `creature.py`, a strip with a creature in its mass is refused (see "Still wrong in the frames").
