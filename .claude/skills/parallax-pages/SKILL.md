---
name: parallax-pages
description: Design or improve a parallax background page for the JKS parallax library — write the page file (.jplax JSON) from a libGDX atlas without the editor, check its numbers, render it and look. Use when asked to "make a parallax", "build a background from these layers", "why does my parallax feel flat", or to review a .plax/.jplax/.plaxpj page.
---

# Parallax pages

**Version 0 (r73, round 1).** The rules below come from the physics of motion parallax and from measuring Simon's own
pages; the grading lab's rounds are testing them. A rule marked *(hypothesis)* is not backed by grades yet. Research
notes and the numbers behind every rule: `docs/parallax-design.md`.

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
| `padX`, `padY`, `flipX`, `flipY`, `mirror` | Gap between repeats, mirrored image, flipped copy beside each repeat. |

Page: `topHalf_top/bottom`, `bottomHalf_top/bottom` (RGBA 0-1), `topHalfSize`/`bottomHalfSize` (share of the screen
left **uncovered**: 0.5 = half), `repeatOnX/Y`, `useOriginalSize` (true for new pages on atlases packed with
whitespace stripped), `pageModel.atlasName` (file name; the atlas sits next to the page).
`tools/parallax_lab.py` has `layer()` and `page_of()` helpers that write all of it.

## How to build one

1. **Look at every region before placing it.** Render or crop each region (Pillow on the atlas PNG, using the
   `xy`/`size` lines of the .atlas). Write down for each: what it shows, how hazy/pale it is, whether it is a full-width
   strip or a single object, and whether its left and right edges join (a hard cut shows a seam at every repeat).
2. **Order by haze, not by name.** Palest, lowest contrast, closest to the sky colour = farthest = first in the list.
   Region names lie: in `calm.atlas`, `Trees_close` is the farthest strip.
3. **Speeds: a constant ratio.** Choose the back speed (samples: 0.01-0.018) and a ratio of x1.25-x1.4, and give layer
   *i* `back * ratio^i`. Speeds must strictly increase toward the front. Keep front/back between ~3x and ~15x
   *(hypothesis for the limits)*. Speed Y ~0.6 x speed X.
4. **Heights: stack so nothing's bottom edge shows.** For each layer compute its height in world units
   (`40 * sizeRatio * imageHeight / imageWidth`) and set `decal_Y_Ratio` so the layer in front (or the screen bottom)
   hides its bottom edge. Farther layers sit higher. The front layer usually sits at 0 or just below.
5. **Width and stagger.** A tiled layer at least ~0.8 worlds wide *(hypothesis)*. Stagger `decal_X_Ratio` (0, 10, 25,
   40…) so no two layers start their repeat together; the same image reused at two depths gets a different size, a
   different offset and often `flipX`.
6. **Sky and ground.** Set the top gradient to the light of the art: its bottom colour close to the farthest layer's
   haze, its top colour deeper. A night scene gets a night sky. Leave the lower part white only when the game draws
   its own ground there (Hiver, Printemps).
7. **Things that move by themselves** get `speedXAtRest`: far clouds 25-90, near water -12 to -20.

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

Look for: a bottom edge or gap showing, a seam at the tile joins, the same shape repeating in view, layers whose
order in the picture contradicts their speed, a sky that does not belong to the art.

## Getting it graded

`./gradlew :demo:lab --args="demo/lab/<round>"` shows the scenes blind, scrolling; a human grades 1-5 and the grades
land in `demo/lab/<round>/grades.json`. Put the page next to variants of it that each break one rule
(`tools/parallax_lab.py` has `flat`, `inverted`, `rescale_span`, `linear`, `shrunk`, `white_sky`, `aligned`…): what
the grades separate is what the rule is worth.

## Using the page in a game

Export: the editor reads a `.jplax` (open it, export `.plax`), or load the JSON directly with
`Parallax_Heart.fromJson("page.jplax")` (desktop and browser). README.md "Using the library in a game" has the rest.
