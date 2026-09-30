---
name: parallax-pages
description: Design or improve a parallax background page for the JKS parallax library — write the page file (.jplax JSON) from a libGDX atlas without the editor, check its numbers, render it and look. Use when asked to "make a parallax", "build a background from these layers", "why does my parallax feel flat", or to review a .plax/.jplax/.plaxpj page.
---

# Parallax pages

**Version 1 (r92, after round 1's grades).** Round 1 graded pages placed from rules as "random values": they did not
start from what each region shows. This version starts from the art. Rules marked *(open)* are still being graded;
what round 1 backed and dropped, and the layer study behind this version: `docs/parallax-design.md` §3, §5, §6.

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

Page: `topHalf_top/bottom`, `bottomHalf_top/bottom` (RGBA 0-1), `topHalfSize`/`bottomHalfSize` (share of the screen
left **uncovered**: 0.5 = half), `repeatOnX/Y`, `useOriginalSize` (true for new pages on atlases packed with
whitespace stripped), `pageModel.atlasName` (file name; the atlas sits next to the page).
`tools/parallax_lab.py` has `layer()` and `page_of()` helpers that write all of it.

## How to build one

1. **Look at every region first.** `python3 tools/parallax_regions.py path/to.atlas /tmp/regions.png` draws each
   region on a checkerboard at its original size and prints: `pos` (its `regionPosition`), `aspect`, `art` (the rows
   holding art, % from the bottom), `solid` (rows opaque across the whole width, from the bottom), `bottom` (how much
   of the bottom row is opaque), `seam` (left/right edge mismatch: past ~20 the repeat shows a cut) and the colours
   at the top and bottom of the art. Open the image. For each region write down what it shows and how near it is.
2. **Say what kind of atlas it is.** It decides the layout:
   - **Full-frame planes** (1920x1080, or 1080-tall panoramas, art already at its height, farther planes' art
     higher up): every layer at its **natural size**, `sizeRatio = aspect x 9/16` (1.0 for 16:9), and decal Y 0.
     They compose as the painter drew them. Don't rescale or shift them one by one.
   - **Pieces on a full-frame canvas** (each piece starts at the bottom of its canvas, like OneNight's grounds):
     stack them like strips, keeping them 0.8 wide or more.
   - **Strips** (5:1 and wider): stack them. The front strip at the bottom (decal Y 0 or below), each farther strip
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
   of the front. With no size to read, a constant ratio x1.25-x1.4 a layer *(open: round 2 brackets x1.15/x1.6)*.
   Speeds must strictly increase toward the front, and the front/back span stay between ~3x and ~15x (both backed
   by round 1). Speed Y ~0.6 x speed X *(open)*.
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

Lint catches motion faults, not layout: the editor's default layout (`calmLag.plaxpj`) passes lint and looks wrong.
**Always render and look:**

```bash
# a round of one or more scenes: round.json lists {id, page, atlasDir, about}, paths relative to the repo root
tools/parallax-lab-shots.sh demo/lab/<round> demo/build/lab/<round>   # stills at 0, 6, 12 s + contact.png
```

Look at every frame, full size, not only the contact sheet. Look for: a bottom or top edge cut flat, an empty band,
a gradient's edge showing through a gap or a translucent layer, a bottom edge or gap showing, a seam at the tile joins, the same shape repeating in view, layers whose
order in the picture contradicts their speed, a sky that does not belong to the art.

## Getting it graded

`./gradlew :demo:lab --args="demo/lab/<round>"` shows the scenes blind, scrolling; a human grades 1-5 and the grades
land in `demo/lab/<round>/grades.json`. Put the page next to variants of it that each break one rule
(`tools/parallax_lab.py` has `flat`, `inverted`, `rescale_span`, `linear`, `shrunk`, `white_sky`, `aligned`…): what
the grades separate is what the rule is worth.

## Using the page in a game

Export: the editor reads a `.jplax` (open it, export `.plax`), or load the JSON directly with
`Parallax_Heart.fromJson("page.jplax")` (desktop and browser). README.md "Using the library in a game" has the rest.
