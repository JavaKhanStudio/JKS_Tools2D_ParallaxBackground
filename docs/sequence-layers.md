# Sequence layers: several images chained on one layer

Design for atelier r147 (2026-09-30), written before any code. Today a layer is one image tiled end to end: a ground
strip repeats the same rock every `width + padX`. A **sequence** layer chains several images (its **segments**) along
one layer: rock, rock, bridge, river, river, rock... with odds deciding which segment follows which. It shares the
`kind` field of [effect-layers.md](effect-layers.md) (`kind = SEQUENCE`).

## What a sequence layer is

- One layer: one speed, one decal, one depth, one `padX` between segments. Its **height** is the layer's height (from
  `sizeRatio` and the first segment's image, as an image layer's); each segment is as wide as that height and its own
  image's aspect ratio make it, so segments of different widths chain without stretching.
- The **segments**: a list of atlas regions (name + position, as a layer names its region today).
- The **odds**: how likely each segment is to follow the one before (the choice asked on r147, below).
- The sequence is **built once**, when the page's layers are built (`WholePage_Model.buildLayer`), as a fixed cycle of
  N segments. From then on the layer tiles that cycle exactly as it tiles one image today: the cycle is the tile, its
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
- The cycle's last segment must be allowed to precede its first, or the seam shows a transition the odds forbid: the
  builder closes the cycle on an allowed pair (it retries the last picks; a table with no closing path is refused in
  the editor).
- Cross-fades match layers by slot: a sequence layer takes its counterpart's scrolled distance like any other, and its
  cycle position follows from it.

## Same picture on every engine

jME runs `core`, so it gets sequences for free, and draws them through `draw(region, x, y, w, h)` like any layer. Godot
has to build the same cycle. Two ways (asked on r147):

- **Stored:** the editor draws the cycle when the page is exported and stores it (`sequence: [3, 0, 0, 2, ...]`). Every
  engine reads it; no random numbers at run time; every launch shows the same ground.
- **Seeded:** the page stores the odds and a seed; each engine runs the same generator (a 32-bit xorshift, written
  with masks in GDScript whose ints are 64-bit), and a game may pass its own seed for a new ground each run. The Godot
  pixel round proves the two generators agree.

## Phases

0. r146's phase 0: the `kind` field in the format.
1. `SEQUENCE` in `core`: the stored fields (format bump, Kryo, `Utils_Page_Json`, `PlaxFormatTest`, README), the cycle
   builder, `tile()` over the prefix sums, `ReaderCases` in all four repeat modes, `FrameAllocationTest`, a stress run.
2. Godot: `plax_page.gd` and `plax_background.gd`, a pixel round (`engines/godot/tests/sequence`), jME through the
   same round.
3. The editor: a layer's segment list, the odds, the cycle length and its preview.
