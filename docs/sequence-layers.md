# Sequence layers: several images chained on one layer

Design for atelier r147 (2026-09-30), written before any code. Today a layer is one image tiled end to end: a ground
strip repeats the same rock every `width + padX`. A **sequence** layer chains several images (its **segments**) along
one layer: rock, rock, bridge, river, river, rock... with a weight per segment deciding how often each shows up. It shares the
`kind` field of [effect-layers.md](effect-layers.md) (`kind = SEQUENCE`).

**Decided** (Simon, 2026-09-30, on r147): **one weight per segment**, whatever came before it; the page stores the
weights and a **seed**, and each engine **draws the cycle at game start** (a game may pass its own seed); segments keep
**their own widths**. The sections below are written to those choices.

## What a sequence layer is

- One layer: one speed, one decal, one depth, one `padX` between segments. Its **height** is the layer's height (from
  `sizeRatio` and the first segment's image, as an image layer's); each segment is as wide as that height and its own
  image's aspect ratio make it, so segments of different widths chain without stretching.
- The **segments**: a list of atlas regions (name + position, as a layer names its region today).
- The **weights**: one whole number per segment, how often it shows up (B with weight 30 out of a total of 100 is
  picked 30% of the time), whatever segment came before it.
- The **seed** the cycle is drawn from, and the cycle's length N.
- The sequence is **built once**, when the page's layers are built (`WholePage_Model.buildLayer`), as a fixed cycle of
  N segments drawn from the seed and the weights. From then on the layer tiles that cycle exactly as it tiles one image today: the cycle is the tile, its
  width the sum of its segments' widths and pads.

## Why a fixed cycle, and how it stays cheap

Drawing must not allocate or decide anything per frame (`FrameAllocationTest`, the #runtime pack). So:

- At build: the cycle's segment regions go into an array, and a second array holds each segment's left edge in the
  cycle (a prefix sum). Nothing is recomputed afterwards; a resize only rescales the prefix sums.
- At draw: `tile()` already knows the first tile's x. For a sequence it finds the first visible segment by a binary
  search in the prefix sums (log N), then walks right until the view is covered. The number of draws is the number of
  visible segments, never N: `ReaderCases` counts them in every repeat mode, as `tilesJustEnoughToCoverTheView` does.
- The cycle is long enough that its repeat is not seen: N defaults to what covers several screens (the editor shows
  "repeats every X screens"). A cycle too short shows its pattern; one too long costs only two float arrays.
- With one weight per segment any segment may follow any other, so the seam where the cycle repeats needs no fixing.
- Cross-fades match layers by slot: a sequence layer takes its counterpart's scrolled distance like any other, and its
  cycle position follows from it.

## Same picture on every engine

jME runs `core`, so it gets sequences for free, and draws them through `draw(region, x, y, w, h)` like any layer.
Godot has to draw the same cycle from the same seed, so the generator is written twice and must agree bit for bit:

- A 32-bit xorshift on `int`s in Java. GWT keeps Java's `int` overflow in JavaScript, so the browser agrees; GDScript's
  ints are 64-bit, so `plax_page.gd` masks every step with `0xFFFFFFFF`.
- The pick is integer only: `(next >>> 1) % totalWeight`, then a walk over the weights. No float: a `float` is a
  JavaScript double in the browser and a 64-bit float in GDScript, and a product of one rounds differently there.
- A game passes its own seed through the reader (a new ground each run); without one, the page's seed, which is what
  the editor previews.
- The Godot pixel round (`engines/godot/tests/sequence`) proves the two generators agree, and a `ReaderCases` case
  pins the first picks of a known seed, so the browser and the JVM are held to the same list.

## What phase 1 settled (r182)

- Stored in `.plax` format 8: `sequenceSegments` (each `regionName`, `regionPosition`, `weight`), `sequenceSeed`,
  `sequenceLength` (16 when unset, 1 at least). A segment without a weight weighs 1; a weight of 0 or less is never
  picked, and when none is above 0 each weighs 1.
- The generator, `SequenceCycle`: `state = seed` (0, where xorshift sticks, starts from `0x6D2B79F5`); per slot,
  `state ^= state << 13; state ^= state >>> 17; state ^= state << 5`, then `pick = (state >>> 1) % total` walks the
  weights in order. `ReaderCases.sequenceCycleOfAKnownSeedIsPinned` holds its first picks, worked out a second time in
  Python.
- The cycle's edges are kept in layer heights, without the pads: slot i starts at `height * edge[i] + i * padX`. The
  first segment is as wide as the layer (`40 x sizeRatio`), so a one-segment sequence draws as its image layer would.
- A game's seed (`ParallaxPageReader.setSequenceSeed`) draws each layer from `game seed XOR the layer's seed`, so two
  layers stored with different seeds stay different; `clearSequenceSeed` goes back to the stored ones, which are what
  the editor saves.
- Flip X / Y and Mirror flip each segment in its own slot; the slots keep their order.
- A negative padX wider than a segment makes the edges go back: the reader then looks at every slot instead of
  searching. Still only the slots in view are drawn.

## Phases

Raised under r147, each `--after` the one before.

0. r146's phase 0 (r177): the `kind` field in the format.
1. `SEQUENCE` in `core`: the stored fields (segments, weights, seed, N; format bump, Kryo, `Utils_Page_Json`,
   `plax_page.gd`, `PlaxFormatTest`, README), the generator and the cycle builder, a game's seed, `tile()` over the
   prefix sums, `ReaderCases` in all four repeat modes and on a known seed, `FrameAllocationTest`, a stress run.
2. Godot: the same generator in `plax_page.gd`, the cycle drawn in `plax_background.gd`, a pixel round
   (`engines/godot/tests/sequence`), jME through the same round.
3. The editor: a layer's segment list, a weight per segment, the seed and cycle length, "repeats every X screens" and
   its preview. After the repo split (r132), in JKS_Tools2D_ParallaxEditor.
