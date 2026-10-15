# Animated layers: what moves on its own, and how each kind of motion is made

Design for atelier r375 (2026-10-15), written before any code. A page moves today when the camera moves, plus three
things that move on their own: a layer's **at rest speed** (a cloud layer drifting), a `SHADER` layer's `WAVE` (water,
heat) and `FOG` (drifting mist), and `PARTICLES`. This adds motion **inside** a layer: fronds that sway, a sea that
glitters, clouds that churn. No single method does it all, so there are two, and each kind of motion goes to the
cheaper one that can make it:

| Motion | Method | Costs |
|---|---|---|
| A cloud layer crossing the sky | at rest speed (exists) | nothing; the clouds must be on a layer of their own |
| Water rippling sideways, heat haze | `WAVE` (exists) | nothing |
| Mist drifting | `FOG` (exists) | nothing |
| Water in two crossing swells | `SWELL` shader (proposed, below) | nothing in the atlas |
| Sun glitter on water | `GLINT` shader (proposed) | nothing in the atlas |
| Clouds churning in place, in a painted sky | `BILLOW` shader (proposed) | nothing in the atlas |
| Fronds, leaves, flags, smoke, anything with a shape that changes | **an animated patch** (format 16) | its frames in the atlas |

A shader costs no atlas space and loops by construction, but it can only push the painted pixels around or light
them. A patch shows whatever was painted, so it can move a frond, but each frame is a texture. A layer can have both:
a `SHADER` layer with a patch draws the patch through its effect.

**Decided** (Simon, 2026-10-15, on r375): a **patch on the layer** (frames of its moving box, their place in the strip
and a frame rate), not frames on a whole layer and not frames drawn by the game through a hook; and `ai-page.sh`
animates a layer on request once the format exists, shipping it only when `loopcheck.py` passes. Simon also asked for
the motion to be **stronger** than r375's gentle stir, and for **shaders for water and clouds**: they are the second
method, below, still to be picked one by one.

## The animated patch (format 16)

- Any `IMAGE` or `SHADER` layer may carry one. Not a `FLOOR`, a tower, a `SEQUENCE` (each of its segments would need
  its own) or `PARTICLES`: those draw their image their own way.
- Three stored fields:
  - `animRegion` (String, empty: no patch): the frames' region name in the page's atlas. The frames are the regions
    of that name in their atlas order (TexturePacker's `index`: `palms_anim` 0, 1, 2 ...), and their count is how many
    there are: no count is stored to fall out of step with the atlas.
  - `animX`, `animY` (int): where the patch's bottom-left corner lies in the layer's region, in that region's pixels
    from its bottom-left. The patch scales with the layer: drawn at the tile's corner plus `animX × drawn width /
    region width`, and so on Y.
  - `animFps` (float, default 12): frames a second.
- **The clock** is the reader's own, the time `act()` has summed, the shader effects' too: frame `floor(time × fps)`
  modulo the count. Two engines given the same deltas show the same frame; a round's stills are taken away from a
  frame change (a time a quarter of a frame past one), so a float rounding differently never shows another frame.
- **Drawing**: after each tile of the layer, its patch's current frame, through the same `Batch` call
  (`draw(region, x, y, width, height)`): so it goes through `DollyBatch`, `PlanetBatch` and `JmeBatch` as the tile
  does, and through the layer's shader effect and fog. Godot draws it in `plax_background.gd`'s `_draw_region` path.
  Frames carry their own alpha (`rekey.py`): a frond that sways out of the still's outline shows, and the still under a
  frame is the layer's own pixels, so a frame only needs what changed.
- **The atlas**: the frames are packed with the layer's region; the mipmap padding rule (#assets: edges copied into
  the padding) holds for them as for any region. 24 frames of the r375 palm crowns' box (785x150) are 11 MB RGBA,
  about 3 MB in ETC2, against 38 MB for the whole strip as frames.
- **What makes the frames** is not the reader's business: `tools/r375-animated-layer` (Wan 2.1 VACE, `loopcut.py`,
  `rekey.py`, `loopcheck.py`) for AI frames, or an artist's.
- **Stronger motion** (Simon: r375's first palms only stirred): `anim.py --free` lets the moving part go of the strip
  after the first frame (its control pixels faded to VACE's grey) and the prompt asks for a strong wind. Motion per
  frame, the palm crowns: 2.13 held (r375), 2.47 at `--free 0.6`, 3.20 at `--free 1.0`, each passing `loopcheck.py`
  (time seam 1.06 to 1.10). Half again as much: the fronds toss, the crowns still do not swing wide. Wan 1.3B is the
  limit there; a larger VACE (14B) does not fit the 8 GB GPU.

### Phases

1. `#format`: format 16, the three fields and `animRegion`'s frames found in the atlas; Kryo behind
   `currentVersion(kryo) >= 16`, `Utils_Page_Json`, `plax_page.gd`'s `LAYER_DEFAULTS`, `Utils_Page.buildFromPage`,
   `ParallaxLayer.clone`, README's "File formats". The libGDX reader draws the patch; `FrameAllocationTest` stays at 0.
2. `#runtime`: Godot draws the patch; a reader round `engines/rounds/animated` (the r375 palms, frames at three
   times) in both frame checks, jME running core's.
3. `#assets`: `ai-page.sh --animate l3='fronds swaying'`: the layer's patch painted, cut, re-keyed, checked, packed.
4. The editor (its own board) shows and plays the patch.

## Shader animations (proposed)

`tools/r375-animated-layer/shaders.py` draws each as frames, written as its fragment shader would be (sines only, as
`FOG`'s noise is, GLSL ES 1.0; every time term a whole number of turns over the loop, every x term a whole number of
cycles over the strip, so the frames loop and tile). Each passes `loopcheck.py` (time seam 0.99 to 1.01).
`demo-shaders.sh` makes them all, with the films that compare them (`sea-compare.mp4`: WAVE, swell, glint, both, and
r375's AI sea; `clouds-billow.mp4`; `page-shaders.mp4`, the beach page with its sea swelling and glinting and its
palms at `--free 1.0`).

- `SWELL`: water moved by two crossing travelling waves, sideways and up and down. `WAVE` slides whole rows; this one
  bends the painted wave crests. Amplitude 3 px, wavelength 32 px on the beach sea.
- `GLINT`: sun glitter: short specks of light where three bent waves peak at once, only on the lit part of the water,
  so the dark troughs never sparkle. Strength 0.8, wavelength 24 px.
- `BILLOW`: clouds churn in place: a slow warp of their pixels, only where the pixel reads as cloud (bright, grey or
  bluish, not a sunlit yellow field), read from the image's own colour at five points, so a painted back layer needs
  no mask. Amplitude 6 px, wavelength 120 px on the valley's sky.

Each is a new `Enum_ShaderEffect` (append only), written three times (`GdxLayerEffects`, `plax_effects.gd`,
`ParallaxEffect.frag`) and given scenes in `engines/rounds/shaders` strong enough that leaving it out fails. A layer
has one effect: water with both swell and glint is one effect that does both, or two layers.

## Not in it

- Clouds cut out of a painted sky onto a layer of their own, to drift: keying painted clouds needs depth-page's
  segmentation (r375 tried a colour key: bars and grey ghosts where the clouds were).
- A patch on a planet page is bent with its tile; on a tower layer it is not drawn.
