# Lurker layers: a figure the nearer layers hide, seen once and gone

Design for atelier r363 (2026-10-14), written before any code. A **lurker** is one figure (a hooded man, a creature's
eyes) in a layer of its own, between two layers of a page, at a speed between theirs. The nearer layers hide it and
their gaps show it. What makes it a scare and not scenery: the player sees it in a gap, a trunk passes over it, and in
the next gap, where they expect it, **it is gone**. It comes back later by chance, or when the game says.

The demo that shows the idea with today's files is `tools/r363-reveal/` (film.sh: v01 the figure behind Hiver's pines,
v02 the same at the size the parallax-ai R1 accident had, a giant; vanish.py: v03, seen once and gone). Its films are
attached to r363.

**Decided** (Simon, 2026-10-14, on r363): a **layer kind of its own** (`LURKER`, format 15), not a hook in game code,
so the editor shows it and shows it vanish. Whether it is there is drawn from a **seed, by chance, plus
`reveal(name)`** for the game to force it. **The seed and the odds are edited in the editor**, which says plainly how
often the figure will be seen. The sections below are written to those choices.

## The one rule

**The figure appears or vanishes only while nothing of it is on screen**: wholly covered by the layers in front of it,
or outside the view. Never in sight. vanish.py asserts it on its switch frame (0 figure pixels), and every reader's
round asserts it on every frame.

## What a lurker layer is

- An IMAGE layer in every other way: one atlas region (the figure, transparent around it), its speed, size, decal and
  `padX`. Its tile is the figure plus `padX`: the figure comes by once every `width + padX` of its own scroll.
- Its **height comes from its depth**: a figure drawn as tall at speed 0.032 as a pine at 0.04 reads as a giant (R1's
  accident, v02). Its height is its real size times its speed over the speed of the layer it is measured against. The
  editor shows that number beside the size (phase 5); nothing enforces it.
- Its feet do not skate if it stands on what scrolls at its own speed: on a layer of the same speed, or on a FLOOR,
  whose planting moves it at the speed of the row under its foot (it is a layer in front of the floor).
- Three stored fields (format 15):
  - `lurkerSeed` (int): where the draws start, as `sequenceSeed` is (0 starts from `SequenceCycle.ZERO_SEED`).
  - `lurkerOdds` (int, at least 1, default 3): after it has gone, each later **sighting** has 1 chance in `lurkerOdds`
    of showing it. 1 shows it at every sighting but the one after a show.
  - `lurkerCover` (float[]): where the nearer layers cover it wholly, worked out by the editor when it saves (below).
    Empty: the figure switches only outside the view.

## Sightings, and what the seed draws

A **sighting** is a stretch of time when the figure's place is on screen and not wholly covered: a gap passing over it.
It ends when it is covered again or leaves the view; that moment is a **hide**, and at each hide the reader decides
whether the figure is there at the next sighting:

1. If the figure was **seen** in the sighting that just ended, it is **gone** at the next one. Always: that is the
   scare. (Seen means at least one of its pixels was drawn uncovered; a sighting where it was absent does not count.)
2. Otherwise the state steps once (`SequenceCycle.next`, integers only, so GWT and Godot draw the same) and it is
   there if `(state >>> 1) % lurkerOdds == 0`.
3. `reveal(name)` (the layer's name, as an EMPTY layer's hook key) makes the next sighting show it, whatever 1 and 2
   say, and consumes itself. A game calls it for its sure first scare.

The first sighting of a page is drawn as in 2. Same seed, same camera path: the same sightings show it, in every
engine; that is what the frame checks compare.

**How often it is seen**, the number the editor shows: in the long run, the figure is seen in about 1 sighting in
`lurkerOdds + 1` (one gone after each show, then 1 in `lurkerOdds` per try). The editor turns it into time at the page's
preview scroll ("seen in about 1 gap in 4, about every 40 s at this speed") and lists the seed's first draws (seen,
gone, gone, seen, ...), so a designer picks a seed whose first sighting shows it, or calls `reveal`.

## Where it is covered: `lurkerCover`

Readers cannot read a texture's alpha every frame (three engines; the GPU holds it). So the editor works it out once,
on the CPU, when it saves, from the atlas images it already has:

- For each nearer layer that repeats on X, the figure's place against that layer's tile depends only on their
  relative offset `d`, modulo that layer's period P (`width + padX`): both scroll, at different speeds. `d` is the
  figure's tile corner minus the nearer layer's (`currentDistanceX`, plus the tiles' steps for the copy in view);
  their Y apart is taken at rest, from their decals.
- The editor sweeps `d` over one period and keeps the intervals where that layer's opaque pixels (alpha at least 250)
  cover the figure's whole box, grown by a margin (4% of the figure's height) for the camera's Y and zoom.
- `lurkerCover` holds triplets: (how many layers in front of the lurker that layer is, from `d`, to `d`), from in
  [0, P), to at least from and possibly past P: the figure is covered at `d` when `((d - from) mod P + P) mod P <=
  to - from`; (n, 0, P) when that layer covers it at every offset. World units of the world the sweep was given (the
  editor's, 40 wide; `tools/lurker-cover.sh` takes 40 x 22.5 by default): `padX` is not scaled with the world, so a
  reader in a world of another width cannot rescale it exactly. Only IMAGE and SEQUENCE layers cover (not towers). A SEQUENCE's
  slots hold what its seed picks, and a game may give it another seed: it covers only where every segment it can pick
  (weight above 0) would, swept in one slot and laid at each slot of the cycle (r385); its segments must be as wide as
  each other, or the slots move with the seed and it covers nothing. A nearer
  layer's mirrored copy counts with it, and the figure's box is its pixels of alpha above 0. A layer moved, resized or re-ordered in the editor is worked out again at the next
  save; a page saved without them (or with a nearer layer's image missing) has none, and the figure switches only
  outside the view.
- Covered by two layers together (a trunk of one, a branch of the other) is not counted: only one layer covering it
  whole. The figure switches less often, never in sight.

The sweep lives in `core/src-jvm` (`LurkerCover`, phase 2, r369: it reads an atlas page's alpha, from `Pixmap`s or
any image), used by the editor and by the rounds' tools (`tools/lurker-cover.sh` bakes a `.jplax`), and tested in
`core/test` (`LurkerCoverTest`, a bar with a gap, to the texel).

On r363's demo (v01, Hiver's pines, `tools/r369-lurker-cover/agree.py` against film.sh's frames) the film shows no
figure pixel from 21.0 to 23.6 s, and the cover finds 21.17 to 21.62 s of it: no frame inside an interval shows a
pixel. The rest is hidden by the pines' soft edges, or two layers together: at alpha 230 it finds 29 of those 63
frames, at 128 the figure shows inside an interval (13 frames, up to 8 px).

On r366's ai-page.sh page (dark pine forest, its front a SEQUENCE of four pine strips, 8 slots;
`tools/r385-sequence-cover/check.sh`) it finds 3 intervals a slot, 24 in all: 33 frames of the film inside one, none
of them differing from the page without its figure by a single pixel value.

## Per frame (no allocation)

Each LURKER layer holds its sightings (`ParallaxLayer`'s package fields: the state, and started, there, seen, in sight,
forced), run by its reader (r370). Each `act()`, every step whether drawn or not (the shots draw only their stills),
against the view of the last `draw()`, and again in `draw()` against its own: compute `d` for each `lurkerCover` entry
from the layers' scrolled distances, and whether a copy of the figure's tile box is in the view. In sight = a copy in
the view and in no cover interval. On in-sight to hidden: apply the rules above. The figure's tile draws as an IMAGE
layer's when `there`, and not at all when not. `FrameAllocationTest.aLurkerFrameAllocatesNothing` covers it.

The cover counts only where it was worked out for; out of it the figure is hidden only out of the view, so it switches
less, never in sight: a page repeating on X; the figure's own row (on a page repeating on Y, its other rows are not
covered); nothing turning or fading the layers in front (no planet, no tower seen from close by, the dolly at zoom 1, no
cross-fade, the tint at full opacity); and each layer in front at the Y apart from the figure it was saved with (a page
scrolling on Y moves them apart). Before the first `draw()` nothing is in the view. The in-view test takes the tile's
box, not the figure's pixels (the reader cannot read them): a sighting where only the tile's empty margin was in the
view counts as one, seen if the figure was there.

`reveal(name)` shows the figure at once when it is not in sight, else at the end of this sighting: never in sight.

Godot's copy is a `plax_lurker.gd` (the same state, the same draws, 32-bit masked as `plax_page.gd`'s `draw_cycle`);
jME runs core's.

## Phases (queued under r363)

1. Format 15: `LURKER` appended to `Enum_LayerKind`, the three fields (serializer, `Utils_Page_Json`, `plax_page.gd`'s
   `KINDS` and `LAYER_DEFAULTS`, `buildFromPage`, `ParallaxLayer.clone`, `PlaxFormatTest`), README's file formats. Drawn
   as an IMAGE layer, always there.
2. `LurkerCover` (src-jvm): the sweep, its tests, and a tool that bakes it into a `.jplax` for the rounds.
3. The reader: sightings, draws, `reveal(name)`; `LurkerCases` in the browser suite (every repeat mode, the one rule
   judged from where the bar in front was drawn); a round `engines/rounds/lurker` (Hiver's pines, as r363's demo,
   `core/test-data/lurker/make_round.py`) in the jME frame check. Done (r370): the figure is 0.07% of a still, so the
   round gives `pixelsOff` (`tools/compare-parallax-frames.py` fails a still with more than 0.02% of its pixels off by
   over 32), and `BREAK=lurker` (jME reveals the figure every step: always there) must fail it.
   `tools/r370-lurker/Sightings.java` runs a page's sightings on the shots' clock without GL.
4. Godot: `plax_lurker.gd`, the same round in Godot's frame check, and a mutant that switches in sight failing it.
   Done (r371): 0.04/255, 0.000% of pixels off; `tools/r371-lurker/mutate.sh`'s five wrong copies (always there,
   switching in sight, the seen rule left out, the step unmasked to 64 bits, the cover ignored) each fail it.
5. The editor (its own board): the kind in the kind tab, seed and odds with the "how often" line and the seed's first
   draws, the depth-height hint, `lurkerCover` worked out on save, and the preview showing it vanish.
6. README: a "Lurker layers" section (the rule, the depth size, the skating), and AGENTS.md's runtime line.

A page painted by `tools/ai-page.sh` gets one on request (`--lurker`, r366): the figure painted alone, standing on
the layer behind it at that layer's speed, its height its depth's, `lurkerCover` baked by `tools/lurker-cover.sh`.

Not in it: a figure that moves on its own (walks, turns its head), sound, a lurker on a planet or a tower page (drawn
flat, as FLOOR is, until asked).
