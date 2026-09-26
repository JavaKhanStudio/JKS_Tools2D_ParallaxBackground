# Parallax pages in other engines

Can a page made with the editor run in Godot, Unity or Unreal? (atelier r87, 2026-09-26.) **Yes, without changing the
file format.** Godot is done and checked frame by frame against the libGDX runtime. Unity and Unreal need the same
three pieces, in C# and C++.

## What an engine has to read

| File | What it is | Readable outside the JVM? |
|------|------------|---------------------------|
| `.plax` | Kryo binary. Its class ids are the registration order in `GVars_Serialization.prepareKryo`, and references are on. | **No**, in practice: you would have to re-implement Kryo 5's wire format and the id table. Keep it for libGDX. |
| `.jplax` | JSON, every stored field by name (`WholePage_Model` → `pageModel` → `pageList` of `Parallax_Model`). | **Yes**: any JSON parser. Export with *JSON (.jplax)* ticked. |
| `.plaxpj` | The project: the same page under `saving`, plus `inside` (false = a loose image, not exported). | Yes, same fields; a reader skips the layers flagged `inside: false`, as `Utils_Page_Json` does. |
| `.atlas` + `.png` | The libGDX texture atlas the page names **by file name**. A layer names a region by `regionName` and its position among the regions of that name. | Yes: a small text format. About 100 lines to parse. |

So the interchange format already exists: `.jplax` plus its atlas. Nothing had to change in the editor or in `core`
for Godot.

## What an engine has to do

A reader is three pieces, and the third is where the work is:

1. **The page:** read the JSON by hand, with the same defaults as `core/src/.../pages/Utils_Page_Json` (a missing field
   keeps its default; `sizeRatio` defaults to 1).
2. **The atlas:** parse the libGDX `.atlas`: both layouts (`xy`/`size`/`orig`/`offset` before libGDX 1.9.13,
   `bounds`/`offsets` after). Keep libGDX's region order: file order, then sorted by `index` when any region has one,
   a region without one last. `regionPosition` counts in that order, so a reader that orders differently draws the
   wrong image and raises no error.
3. **The drawing:** port `ParallaxLayer.act`, `ParallaxPageReader.tile`/`drawRegion` and `SquareBackground`:
   - The world is `world_width` units wide (40, `Parallax_Heart`'s default) and as high as the screen's aspect ratio
     makes it. Speeds are in world units per second, so a different world width changes how fast a page scrolls.
   - A layer is `world_width × sizeRatio` wide, and as high as its image's aspect ratio makes it. With the page's
     `useOriginalSize`, the image is the region's *original* size, with the packed pixels drawn at their offset in it.
   - It starts at its decal (percent of the world), moves by `delta × (speed + speedXAtRest) × parallaxScalingSpeed`,
     and is tiled from the bottom left of the view (y up) with `padX`/`padY` between copies. `mirror` stacks a flipped
     strip above an X-tiled layer, or to the right of a Y-tiled one.
   - The two gradients are in screen fractions (`topHalfSize` is the part left *uncovered*), drawn opaque (libGDX
     draws them without blending, so their alpha is ignored), the bottom one over the top one.
   - The page is anchored to the screen, not to the game camera.
   - A cross-fade matches the two pages from their FRONT layer: each incoming layer takes the distance its outgoing
     counterpart has scrolled from its decal; an incoming page with more layers has back layers with no counterpart,
     one with fewer leaves the outgoing back layers fading out alone. Slot by slot, back to front, the outgoing layer is
     drawn at `1 - t`, then the incoming one at `t`. The repeat and the gradients' sizes stay those of the first page;
     the gradients' colours fade. A tint lerps from the current one and is multiplied into every layer, not the
     gradients.

## Godot 4: done

`engines/godot/` is a Godot 4.6 project. `addons/jks_parallax/` is the reader a game copies:

- `plax_atlas.gd` (`PlaxAtlas`): the `.atlas` parser, loading the PNG as an imported resource when the project has one
  and straight from disk otherwise.
- `plax_page.gd` (`PlaxPage`): `.jplax` and `.plaxpj`.
- `plax_background.gd` (`PlaxBackground`): a `CanvasLayer` (layer −100) that scrolls and draws the page. It mirrors
  `Parallax_Heart`: `speed_constant_x/y`, `speed_consumable_x/y`, `act(delta)`, `reset_positions()`,
  `transfert_into(page, atlas, seconds)` (`transfertIntoPage`) and `tint_to(color, seconds)` (`addColorTransfert`).

Usage is in the README (*In Godot 4*). `godot --path engines/godot` scrolls Hiver.

**How it is checked.** `tools/godot-parallax-shots.sh` renders the same pages in libGDX (the grading lab's `--shots`)
and in Godot (`engines/godot/tests/shots.gd`), with the same 60 units/s scroll stepped at 1/60 s, 0, 6 and 12 s in,
off screen, and compares them pixel by pixel. It runs three rounds: the lab's `demo/lab/round1` (18 pages from four
atlases), `engines/godot/tests/conformance` (7 pages for what round 1 lacks: trimmed regions with `useOriginalSize`,
X+Y tiling with padding, Y tiling with mirrors, X tiling with mirrors and negative padding, no tiling, overlapping
gradients with a translucent colour, and a `.plaxpj` with loose layers; three of them also scroll on Y at ±30 units/s,
and two resize the window 3 s in, to 720x1280 and to 1280x1000) and `engines/godot/tests/transfer` (6 scenes that
cross-fade or tint 4 s in over 4 s, so t6 is mid-fade: into a page with more layers and another atlas, into one with
fewer, into the page on screen while tinting, with no repeat into other gradients, a translucent tint alone, and a
second cross-fade started 1 s into the first, which drops the page fading in and brings the outgoing one back to full
opacity, as libGDX does). A scene's `speedY`, `resize`, `transfer` and `tint` entries in `round.json` drive both
sides.

On 2026-09-26 (Godot 4.6.3, Compatibility renderer): worst mean difference **0.28 / 255**, and 0.03 % of pixels off by
more than 32, on the edges of clouds in an atlas filtered `MipMap` (Godot and the GL driver build mipmaps
differently). Comparing a Godot frame with the libGDX frame 12 s later scores 6.5 to 25, so the 2 / 255 threshold
catches a real error. The transfer round: worst **0.69 / 255**; with the scroll sync left out it scores 24.6, with the
tint left out 73.8, with the interrupted fade keeping its opacities 27.0. The conformance round with Y scrolling and
resizes: worst 0.25; with the Y scroll reversed it scores 31.5, with the world height left as it was before a resize
41.8. CI runs the three rounds on every push (`.github/workflows/ci.yml`, job `godot-frames`: Xvfb and Mesa llvmpipe,
no GPU), where round 1, the conformance round and the transfer round scored 0.18, 0.17 and 0.55.

**Not ported yet:** atlas regions packed rotated (the editor's packer does not rotate; a TexturePacker atlas may).
**Not checked:** an exported Godot game (only the editor/runner has been tried: `res://` pages load, with the PNG
imported).

## Unity and Unreal: what they would take

The same three pieces. What changes is how each engine draws a 2D screen-space background:

| | Unity | Unreal |
|---|---|---|
| Page | `JsonUtility` maps the public fields directly (the Java names are the JSON names). | `FJsonObjectConverter` or `FJsonSerializer`, by hand. |
| Atlas | Parse the text, build `Sprite`s with `Sprite.Create(texture, rect, pivot, ppu)`, y flipped (Unity textures start bottom left). | Parse the text, UVs into the texture (Paper2D sprites are optional). |
| Draw | A dedicated background camera, or a mesh of quads rebuilt each frame; a `SpriteRenderer` per tile works but costs GameObjects. | A `UCanvas` draw in a HUD, or a Slate/UMG widget: screen space, no world actors. |
| Watch | **Colour space**: a Linear-space project blends and interpolates gradients differently from libGDX. Mark the textures sRGB and expect small differences, or compare in Gamma space. | **Tone mapping and post-process** alter every colour of the scene: draw after them (UI), or the page will not match. |

For each, the Godot check carries over: a runner that renders the lab rounds' pages at 0, 6 and 12 s and compares with
the libGDX stills. That comparison is what makes a port trustworthy, much more than the reader code.

## Would the format need to change?

Not to read a page. Two changes would make the other ports easier, and both are a format change (a new stored field is
a `.plax` version bump, `Utils_Page_Json`, `PlaxFormatTest` and the Godot `plax_page.gd`; see AGENTS.md):

- **The page's world width in the file.** Speeds and sizes are relative to a 40-unit world only by convention of the
  heart; a port has to know that number.
- **The regions inside the page** (their rectangles, trims and order), so an engine needs no libGDX atlas parser, or the
  editor exporting an engine's own atlas format (Godot `AtlasTexture`s, a Unity sprite sheet).

Neither is needed today: the Godot parser is 100 lines and the world width is a documented default.
