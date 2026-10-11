# Depth tricks: a 2.5D or "round" feel from flat layers (r266)

The question (Simon, r266): can a page of flat images feel 3D, or be used "round", as Shantae's towers are? Yes, in
four ways the reader could draw, all from the art pages already have, and a fifth that needs new art. None is built
yet. This page says what each looks like, how it would be built here, and what it costs.

## How it was judged

`tools/r266-depth/mock.py OUT` draws five 4-second clips from Hiver's four regions (trees, mountain, far peaks, the sky
band). It is a **mock**: a small 3D scene in numpy, each screen pixel cast back onto the surface it hits, not the
reader. It shows what each trick looks like moving before any is built. The clips and `contact.png` are attached to r266.

| Clip | What moves | What it shows |
|------|------------|---------------|
| `flat` | the camera pans 6 units | today's parallax: four billboards at depths 1.5, 3, 6 and 24. The control |
| `floor` | the same pan | a ground plane drawn row by row under the same billboards: every row at its own depth |
| `planet` | the same pan | the billboards bent down by their distance along a planet of radius 12: the far ones bend more |
| `dolly` | the camera moves in one unit, then back | near layers grow faster than far ones |
| `tower` | the camera turns 135 degrees round a brick cylinder | Hiver's layers as panoramas on bigger cylinders round it: the near ones turn faster, every edge curves |

## The tricks

Every trick but the last draws a layer as **strips**: thin slices of its image, each placed or sized on its own. A
strip is still `batch.draw(region, x, y, width, height)`, the one call `JmeBatch` implements, and Godot's
`draw_texture_rect_region`. So no shader is written three times, the strips of a layer share its texture (one flush,
as now), and they cover the pixels the layer already covers: the runtime is fill-rate bound, and strips add no fill.
The slices' `TextureRegion`s are made when the page loads, never in `act()` or `draw()` (`FrameAllocationTest`).

### 1. Floor: a ground layer whose rows run at their own depth

SNES fighting games' floors, Mode 7. A layer marked as a ground is drawn as horizontal strips; the top row scrolls at
the speed of the layer behind it, the bottom row at the front speed, the rows between at the speed of their depth
(1 / distance, not a straight line), and each row is stretched by its depth too. The clip is the strongest 3D of the
five: the ground's stones run apart under the camera while the billboards stand on it.

- Built: a layer setting (a far speed for its top row, or "ground"), a format bump, the three readers
  (`Utils_Page_Json`, `plax_page.gd`), `buildFromPage`, README's table. Strips in `tile()`, each row tiled on its own.
- The art: any layer can be a ground, but an image drawn as seen from above (a floor texture) reads best. Hiver's
  sky band would be a lake.
- Limit: row strips are an affine approximation of perspective; at one strip per screen row it is exact enough.
- Built (r275), after Simon felt it in the floor lab (`tools/effects-lab.sh --lab floor`, `FloorLab`): the layer kind
  `FLOOR` (format 11: `floorMapping` `MODE7` or `LINE_SCROLL`, `floorTopSpeed`, `floorRows`, `floorHeight`: the box,
  so the lab's 40%-high ground over a square tile of stones is a page setting), its rows straight in
  speed from the layer's own at the bottom to the top speed (the lab's sliders, not the layer behind's). The lab showed
  the layers in front sliding on the ground at their own speeds; the reader plants them, each at the speed of the row
  under its foot (`ParallaxPageReader.plant`). Drawn by `ParallaxLayer.drawFloor`, `plax_floor.gd`'s
  `draw`; round `engines/godot/tests/floor`.

### 2. Tower: a layer wrapped round a cylinder

The "round" feel: Shantae's and Nebulus's towers. The level turns round a tower; the tower's wall is drawn on a
cylinder (its bricks narrow toward the sides and darken as they turn away), and what is behind turns at rates set by
how far it is. In the clip only the wall is new: the panoramas behind it move almost as a flat parallax does, curving a
little at their edges.

- Built: a layer setting (a radius), its image drawn as vertical strips: strip at angle a placed at R sin(a) and
  R cos(a) wide, shaded by its facing. A format bump, the three readers, as above.
- The camera's X is an angle there: a game on a tower scrolls its page by the angle times the radius, which it can
  already do.
- Conflict to settle: a strip's shade is the obvious batch colour, but the page fog packs each layer's fog in the batch
  colour's red channel (`LayerEffects.beginPageFog`). The shade must ride elsewhere (a darker slice drawn over, or
  the shade baked into the fog's packed value).
- Built (r276): format 12's `towerRadius` (percent of the world width, 0 flat) and `towerShade` (0.65, the mock's,
  when unset) on an `IMAGE` layer. The tower stands in the view's middle; the image runs round it at its own scale, its
  front showing what the flat layer would there, so the layer's scroll turns it. 128 strips of equal angle, one
  `batch.draw` each. The conflict settled in the packed value: the page fog's shader reads the batch colour's free
  blue as darkness (`rgb * (1 - b)` before the fog's mix), 0 for every other layer; elsewhere the strip's rgb is
  multiplied. Drawn by `ParallaxLayer.drawTower`, `plax_tower.gd`'s `draw`; round `engines/godot/tests/tower`. Not
  built: the panoramas behind curving at their edges (a flat parallax behind reads well enough), a shader version.

### 3. Planet: layers bent by their distance

Animal Crossing's rolling world: the farther a thing along the ground, the lower it sinks. On screen a layer at depth d
drops by `dx² · d / 2R` at `dx` from the middle, so the far layers bend more and the sky does not. Cheap, a strong style;
it does not read as depth as much as the floor does.

- Built: a page setting (the planet's radius; depth from each layer's speed against the front's), vertical strips each
  lowered, a format bump, the three readers.

### 4. Dolly: a zoom that scales each layer by its depth

Today the reader draws every layer in world units, so a camera zoom scales every layer alike: the page zooms as one
picture. With the dolly, a layer's scale follows its depth (from its speed against the front's): the trees grow, the sky
barely moves. Only games whose camera zooms gain anything.

- Built: in the reader only, a game's switch (no stored field, no format bump, nothing in Godot's or jME's file
  readers, but their draw code copies it). Tiling must read the scaled view (`tilesJustEnoughToCoverTheView`).
- Built (r278): `ParallaxPageReader.setDolly`. A camera moving in by `dz` grows a layer at distance `d` by
  `d / (d - dz)`; a parallax speed is `1 / d`, so with `k` a layer's speed over the front's, the front layer at the
  camera's zoom `z` puts every layer at zoom `1 - k (1 - z)` (`dollyZoom`): the front as the camera, a still layer at 1.
  Each layer is tiled across its own view, at its zoom about the camera's middle, placed from the corner of the view
  at zoom 1 (so the zoom scales it about the middle, where without the dolly the page zooms about the view's corner),
  and drawn through `DollyBatch`, which scales its quads (and its particles' vertices) into the camera's view; the
  effects keep the game's batch and are laid on the layer's view, FOG's noise from where its layers' corner is
  (`LayerEffects.setView(width, height, left, floor)`). Godot draws each layer in its own view at as many pixels a
  unit (`PlaxBackground._zoom_of`, `_anchor_of`); jME runs core's. Round `engines/godot/tests/dolly`,
  `tools/r278-dolly/mutate.sh`. A FLOOR's rows are not dollied one by one: the floor takes its layer's zoom.

### 5. From one painting: depth estimated, split into layers

The literal "a 2D image": a single painting has no layers. A depth estimator (Depth Anything, MiDaS, run locally beside
`tools/ai-page.sh`) gives each pixel a depth; cutting the painting into depth bands, with the holes behind each band
inpainted, makes an ordinary page. The pipeline is assets work beside r239's strips, not the reader. A per-pixel
displacement shader on one image and its depth map is the other way, and the costliest: a second texture per layer
(a format bump), a shader three times, and holes wherever the camera looks behind a near thing.

- Built (r279): `tools/depth-page.sh PAINTING OUT` (`tools/r279-depth-page/depth_page.py`), Depth Anything V2 Small
  in the ComfyUI venv, the holes repainted by `gen.py inpaint` with `--tile x` so every layer loops, by
  dreamshaper_8's inpainting model, which sees the mask and the painting round it (r315: an ordinary checkpoint left a
  soft band at a repaired join, birds and brighter patches; one with a noise mask, a pale veil). Each step that
  looks across the painting (the depth, the islands, the fill) runs on it laid three times side by side, or a band's
  edge steps at the join. How to use it and what to look for: the parallax-pages skill, "From one painting".

### Not here

- Shantae's planes (jumping into the background, Risky's Revenge): the game's own levels, drawn as layers; the page
  already scales and scrolls them.
- Normal-mapped lighting: new art per layer, a shader three times; a look, not depth.

## What I would build first

The floor: the biggest gain for a side-scroller, any page can use it, and its row strips are the mechanism the tower and
the planet then reuse turned on their side. Then the tower, which is what "round" asks for. The dolly is small and
independent; the planet is a style and comes free after the tower's column strips. The depth split belongs with r239.
