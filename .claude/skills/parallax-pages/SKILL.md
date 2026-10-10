---
name: parallax-pages
description: Design or improve a parallax background page for the JKS parallax library — write the page file (.jplax JSON) from a libGDX atlas without the editor, or generate the art from a theme with a local Stable Diffusion (tools/ai-page.sh), check its numbers, render it and look. Use when asked to "make a parallax", "generate a parallax background", "build a background from these layers", "why does my parallax feel flat", or to review a .plax/.jplax/.plaxpj page.
---

# Parallax pages

**Version 3 (r213, after round 3's grades).** Version 1 (r92) started from the art instead of rules, and round 2
backed it: every page written from the art graded at or above Simon's own (three wins, two ties). Round 2 also
found that deeper speeds grade better than the samples' (step 5), and that shrinking full-frame planes hurts (step 2).
Round 3 ran version 2 on three atlases it had never seen (graded 5, 4 and 4; 4 against Simon's 2 on Printemps), and
found no top to the speed span up to 100x (step 5); round 4 none up to 250x. Rules marked *(open)* are still being graded; the grades and the
layer study behind this: `docs/parallax-design.md` §3, §5, §6, §7, §8.

## What a page is

A page (`WholePage_Model`) is layers stored **back to front**, each one atlas region, plus two gradient squares behind
them. The world is 40 units wide; its height follows the screen (22.5 at 16:9). Per layer:

| Field | Meaning |
|-------|---------|
| `regionName`, `regionPosition` | The image: its name in the .atlas, and its **0-based order among the regions of that name, as listed in the .atlas file**. Not the atlas's `index:` field. |
| `sizeRatio` | Layer width in worlds (1.0 = 40 units = one screen at 16:9). Height follows the image's aspect ratio (its `orig` size when `useOriginalSize` is on). |
| `decal_X_Ratio`, `decal_Y_Ratio` | Start offset in % of the world width / height. Y is where the layer's **bottom edge** sits, 0 = screen bottom. |
| `parallaxScalingSpeedX/Y` | Share of the screen scroll the layer follows. Far = small. |
| `speedXAtRest` | Own movement even when the screen is still: clouds, water. |
| `padX`, `padY`, `flipX`, `flipY` | Gap between repeats (world units), and the image flipped left-right / upside down. |
| `mirror` | Only on a page repeating on ONE axis: repeating on X, a second row stacked on top of the strip, upside down; on Y, a second column to its right, reversed. Nothing on XY or none. A reflection that doubles the band (clouds, water), never a seam fix: on a foreground layer it draws the ground upside down above itself. |
| `kind`, `name` | `IMAGE` (the default; `name` null) or `EMPTY` (format 5): a layer with no image, `regionName` null. It is the world's width x `sizeRatio` by the world's height x `sizeRatio`, keeps its speeds, `speedXAtRest`, decal and pads, ignores `mirror`, and draws only through the hook the game registers under its `name` (`ParallaxPageReader.setLayerHook`, Godot's `set_layer_hook`). `PARTICLES` (format 6): the same imageless box, drawing a particle effect instead of a hook. |
| `particlesLibgdx`, `particlesGodot`, `particlesAnchor` | A `PARTICLES` layer's effect files: a libGDX `.p` beside the atlas, its images packed in the atlas, and a Godot scene; each engine draws its own, jME none. `particlesAnchor`: `LAYER` (default) pins the effect to the box's bottom-left corner on every tile (smoke, spray), `VIEW` emits it once from the view at the layer's decal (snow, rain). |
| `shaderEffect`, `shaderAmplitude`, `shaderWavelength`, `shaderSpeed` | `kind` `SHADER` (format 7): an image layer like any other, `regionName` set, drawn through `WAVE` (rows shifted sideways by `amplitude` world units, `wavelength` apart, running up at `speed`: water, heat) or `FOG` (opacity thinned by up to `amplitude`, 0-1, in patches `wavelength` wide drifting at `speed`: a white band is a moving fog). Wavelength 0 draws no effect; lint says so, and when a FOG amplitude leaves 0-1. libGDX, the browser, Godot and jME draw the same frames. |
| `shaderHaze` | Format 9 only, no longer drawn nor written (r217): read from an old file, the largest becomes the page's `fogStrength`. Write `fogStrength` instead. |
| `sequenceSegments`, `sequenceSeed`, `sequenceLength` | `kind` `SEQUENCE` (format 8): `regionName` empty, `sequenceSegments` a list of `{regionName, regionPosition, weight}` from the atlas, chained side by side in a cycle of `sequenceLength` segments picked from `sequenceSeed` by weight, then tiled as one image (the first segment sets the height; docs/sequence-layers.md). With the atlas, lint names a segment whose region it lacks (core then fails to load the page), and says when there is no segment, a length under 1 (read as 1) or no weight above 0 (each weighs 1). |

Page: `topHalf_top/bottom`, `bottomHalf_top/bottom` (RGBA 0-1), `topHalfSize`/`bottomHalfSize` (share of the screen
left **uncovered**: 0.5 = half), `repeatOnX/Y`, `useOriginalSize` (true for new pages on atlases packed with
whitespace stripped), `pageModel.atlasName` (file name; the atlas sits next to the page), `fogStrength` and `fogColor`
(format 10, r217): a depth fog mixing each image layer toward `fogColor` (RGBA 0-1, the mist's white 0.93, 0.95, 0.97
when absent) by `1 - exp(-fogStrength * (1/speed - 1/front))`, speed its `parallaxScalingSpeedX` and front the page's
fastest: depth for a misty page without repainting its layers. Per unit of `1/speed`, so it follows the page's speeds:
speeds 0.1 down to 0.01, 0.03 fogs the back 93%, 0.06 near-hides it; the front layer is never fogged.
`tools/parallax_lab.py` has `layer()`, `empty()` and `page_of()` helpers that write all of it.

**An EMPTY layer** is a depth the game fills: a sprite, particles, a flock of birds that must pass in front of one
layer and behind the next. Place it in the list where that depth is, give it a speed between its neighbours' (step 5
holds for it too: it is what puts the game's things at that depth), and size it to the tile the hook draws into. Lint
counts its speed and skips its layout: it covers nothing. A render of the page alone shows nothing there until the game
hooks it, so a gap in a still at an EMPTY layer is expected: render it with a scene's `"hooks": {"<name>": {"region":
..., "position": ...}}`, which draws an atlas region over each of its tiles (`tools/r186-empty-page` is a worked page
and round, hooked and not).

**A PARTICLES layer** is snow, rain or smoke at one depth: placed and sped like an EMPTY layer, and like it skipped
by the layout checks. Lint also says when it names no `.p`, or its `.p` is not beside the atlas: libGDX then draws
nothing there. README.md's "Snow, rain and smoke" says how wide a `VIEW` effect's `.p` must spawn;
`core/test-data/particles` is a worked round, `VIEW` (p01) and `LAYER` (p02).

## How to build one

1. **Look at every region first.** `python3 tools/parallax_regions.py path/to.atlas /tmp/regions.png` draws each
   region on a checkerboard at its original size and prints: `pos` (its `regionPosition`), `aspect`, `art` (the rows
   holding art, % from the bottom), `solid` (rows opaque across the whole width, from the bottom), `bottom` (how much
   of the bottom row is opaque), `seam` (left/right edge mismatch: past ~20 the repeat shows a cut) and the colours
   at the top and bottom of the art. Open the image. For each region write down what it shows and how near it is.
2. **Say what kind of atlas it is.** It decides the layout:
   - **Full-frame planes** (1920x1080, or 1080-tall panoramas, art already at its height, farther planes' art
     higher up): every layer at its **natural size**, `sizeRatio = aspect x 9/16` (1.0 for 16:9), and decal Y 0.
     They compose as the painter drew them. Don't rescale or shift them one by one, nor shrink them: at 0.75 as wide
     their bottoms show (round 2: 3 against 4).
   - **Pieces on a full-frame canvas** (each piece starts at the bottom of its canvas, like OneNight's grounds):
     stack them like strips, keeping them 0.8 wide or more.
   - **Strips** (5:1 and wider): stack them; their width is free (round 2: 0.7 as wide graded the same). The front strip at the bottom (decal Y 0 or below), each farther strip
     higher, with its bottom edge under the **solid** band of the one in front. Height of a layer in world units =
     `40 x sizeRatio / aspect`; screen = 40 x 22.5.
3. **Hide every cut edge.** Bottom edges under a nearer layer's solid band or below the screen. A region whose art
   touches its top edge (`art` reaches 100% and it is not solid there: the painter cropped a peak or a tree) needs
   its top edge above the screen: make it bigger or raise it. Nothing may leave an empty band: what no layer covers,
   a gradient covers, in the colour of the art next to it.
4. **Order by the art, not the name.** Palest, lowest contrast, closest to the sky colour = farthest = first in the
   list. Region names lie: in `calm.atlas`, `Trees_close` is the farthest strip.
5. **Speeds from the art.** Speed is proportional to 1/distance, and so is the size the painter drew things at: a
   tree drawn half as big is twice as far and moves half as fast. Set the front layer (0.07-0.1), then each layer
   `front / (how many times smaller its things are)`. Sky and far mountains, with nothing to compare: 1/10 to 1/20
   of the front. With no size to read, a constant ratio x1.6 a layer, up to x2.0: round 2 graded x1.15, x1.33 and
   x1.6 at 2, 3 and 4, round 3 x1.6 above x1.33 (5 against 4) and x2.0 level with x1.6 (the samples' x1.25-x1.33 is
   the low end). Speeds must strictly increase toward the front. The span (front / back) must reach ~3x (under it: 2,
   twice); **err deep**: in round 3 the deeper span won three brackets and tied one (calm 100x over 41x, Pure 43x over
   10x, Boss 25x over 7x), and in round 4 250x won on Pure (5) and tied 100x on calm: no top up to 250x. Past ~40x
   the gain flattens (Pure 100x graded under 43x): 40x-250x do not hurt, deeper does not keep winning. A page that
   scatters its planes can still fail deep (45x scored 2 on Simon's PurpleFairy in round 1): the limit is the page's.
   Lint warns past 250x, the deepest graded.
   Speed Y ~0.6 x speed X *(open)*.
6. **Gradients cover, in the art's colours.** Set them to what they must blend with: the sky behind the farthest
   layer, the ground under the nearest one (Hiver's snow is the bottom gradient in the hills' `e0e8dd`). A layer
   with translucent parts shows the gradient boundary through it: use one gradient for the whole screen
   (`topHalfSize` 0, `bottomHalfSize` 1). Round 1 did not back sky colour as a matter of taste.
7. **`useOriginalSize: true`** when the .atlas has `offset:` lines with non-zero values (packed with whitespace
   stripped): without it the region's art is stretched over the layer and its transparent part is lost.
8. **Things that move by themselves** get `speedXAtRest`: far clouds 25-90, mist -10, near water -12 to -20.
   Starting offsets (`decal_X_Ratio`) may be staggered; round 1 found it makes no difference.

`tools/parallax_lab.py` `fairy_from_art`, `hiver_from_art`, `calm_tree_from_art`, `night_from_art` and
`calm_from_art` are worked examples of each kind, each explaining its choices.

## Check it

```bash
python3 tools/parallax_lab.py lint page.jplax        # the numbers, and the rules they break
```

Lint catches motion faults and, reading the atlas (`--atlas DIR`, else next to the page, its round.json or the sample
folders), the layout faults a render shows: (a) art cut flat at a strip's top edge on screen, (b) a strip's bottom edge
on screen with nothing nearer over it, (c) a band of the screen no layer and no gradient covers (white = the editor's
unset default), (d) stripped regions stretched (useOriginalSize off), (e) a tiled layer with seam > 20 in the rows on screen (sunk below it, a mismatch cannot show). It does not
judge composition: the editor's default layout (`calmLag.plaxpj`) passes lint and looks wrong.
**Always render and look:**

```bash
# a round of one or more scenes: round.json lists {id, page, atlasDir, about}, paths relative to the repo root
tools/parallax-lab-shots.sh <round dir> build/lab/<round>   # stills at 0, 6, 12 s + contact.png
```

Look at every frame, full size, not only the contact sheet. Look for: a bottom or top edge cut flat, an empty band,
a gradient's edge showing through a gap or a translucent layer, a bottom edge or gap showing, a seam at the tile joins, the same shape repeating in view, layers whose
order in the picture contradicts their speed, a sky that does not belong to the art.

## Generating a page (no art yet)

When there is a theme and no atlas, a local Stable Diffusion paints one (r239-r246, `docs/ai-parallax.md`). It needs
`~/ComfyUI` (`COMFYUI_DIR`) with its venv and models, and ~1.5 GB of free VRAM: run `nvidia-smi` first, and never stop
another project's process to get it. Nothing is downloaded: a model that is not there is a question for Simon.

```bash
tools/ai-page.sh --theme "snowy pine valley at dawn" --layers 4 --colours 8f9bb8,6f88a0,1c3036 build/ai/snow
tools/ai-page.sh --theme "red desert mesas at sunset" --layers 3 --style pixel --front hills \
    --colours 9a6a6a,b8664a,5a2e22 --sky b5587a,f2b37a build/ai/desert
```

It paints a looping strip per depth (mountains at the back, hills between, `--front` pines or hills), cuts them,
packs `OUT/page/<name>.atlas`, writes `<name>.jplax` by this skill's rules (front 0.08, x2.0 a layer back, the back one
at most a tenth of the front's; each farther strip's bottom under the solid band of the one in front; one gradient
for the whole screen; the depth fog so the back layer is `--fog` 0.6 of the way to the sky), lints it, and renders
`OUT/shots` (0, 6, 12 s). `--style painted` is SD 1.5 at ~12 s a strip, its front a `SEQUENCE` of `--variants` 3 more
strips; `pixel` is SDXL + PixelArt_XL at ~25 s, one PNG pixel per art pixel, drawn `Nearest`. About 2 min a page.

What is yours to choose, from the theme:
- **`--colours`**, back to front (three are spread over any count). img2img keeps the shape's colour, so this is the
  palette. Keep every layer clearly darker or more coloured than a pale sky: a layer near it keys half clear.
- **`--sky top,horizon`**: SD always paints a pale sky. Sunset, night, a storm: say it here. The fog takes the horizon.
- **`--keep`** lays the page out again from the strips already painted: another `--sky`, `--fog` or `--front-speed`
  costs seconds, not a repaint. Another look needs another `--seed`.

Look at every frame (`OUT/shots/a01-t*.png`) for what lint cannot see: pines that stayed smooth spikes (the tool
repaints a painted one under an outline roughness of 3.5; a borderline one passes), a horizon or clouds painted behind a layer, a
creature (the prompt forbids them, and `creature.py` refuses one inside a layer's mass, not on its edge, r261),
layers whose colours do not belong together, a light rim along a far ridge. Then the strips themselves:
`OUT/strips/l<k>_ai.png` painted, `_cut.png` cut, `_cut.log` what the cut dropped. A strip refused 5 times (art on
its top row, a creature, or no trees) stops the run: another `--seed`.

## Getting it graded

The grading lab is in the editor repository (JKS_Tools2D_ParallaxEditor, beside this checkout): there,
`./gradlew :demo:lab --args="demo/lab/<round>"` shows the scenes blind, scrolling; a human grades 1-5 and the grades
land in `demo/lab/<round>/grades.json`. Put the page next to variants of it that each break one rule
(`tools/parallax_lab.py` has `flat`, `inverted`, `rescale_span`, `linear`, `shrunk`, `white_sky`, `aligned`…): what
the grades separate is what the rule is worth.

## Using the page in a game

Export: the editor reads a `.jplax` (open it, export `.plax`), or load the JSON directly with
`Parallax_Heart.fromJson("page.jplax")` (desktop and browser). README.md "Using the library in a game" has the rest.
