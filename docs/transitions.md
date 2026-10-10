# Transitions: the transfert, and effects that could carry it

Research note for atelier r220 (2026-10-09). Simon: "I remember the parallax to be able to do transitions, is that
still working? If so can you try to make a demo of it? Also, let's inject that concept in the shader / effect
research: transitions using effect, color grading + shaders to creep an effect in, etc." Nothing here is decided: it
is what exists, what the lab shows, and what each way would cost.

## What exists, and that it works

- **The transfert** (`Parallax_Heart.transfertIntoPage(page, seconds)`, `ParallaxPageReader.addLayersTransfert`): the
  two pages are stacked back to front and matched from the front; each slot fades from the old page's layer (1 to 0)
  into the new page's (0 to 1), both keep scrolling, a matched new layer starts where the old one had scrolled
  (`syncTransferPositions`), and the gradients fade too (`SquareBackground.transfertInto`). A transfert into the page on
  screen clones its layers; one interrupted by another releases the page it was going to.
- **The tint transfer** (`ParallaxPageReader.addColorTransfert(color, seconds)`): every layer's colour multiplied by a
  tint that moves toward `color`.
- Both are ported line for line to Godot (`plax_background.gd`, r94) and run as is in jME. `engines/godot/tests/transfer`
  is their frame round: on 2026-10-09 it passes in Godot (worst 0.69/255) and jME (0.23/255), with `:core:test`
  (`ReaderCases`' cross-fade cases, all four repeat modes).

## The lab

`tools/transfert-lab.sh` (Labs screen: transfert-lab) runs the same transfert in four panels, back and forth between
two of round1's pages, drawn four ways:

| Panel | What | In the library today? |
|---|---|---|
| A | the transfert as it ships | yes |
| B | the transfert through a colour grade: the tint goes to the grade colour over the first half, back to white over the second | yes: a game can call both today. The tint multiplies, so it darkens and colours, never lightens: no fade through white |
| C | fog creep: the mist's white rolls in in patches, far layers first, the page is swapped under the full fog, the fog clears near layers first | no: the lab's own shader |
| D | dissolve: in each slot the new page's layer eats the old one in patches, back slots first | yes: `TransfertStyle.dissolve` (r250) |

C is GdxLayerEffects' PLAIN shader with a mask taken from FOG's noise (r216's sum of sines, so the same on every GPU), in
screen pixels; D the same mask in the library's shaders (`dissolved()`, over the camera view), in all three engines. Sliders: the pages, the transfert's length, the hold, the scroll, B's grade colour, and for C and
D the depth stagger, the patch size and the edge softness. "Copy settings" hands them back as JSON.

## Ways to carry a transfert, and what each costs

Every way below has to draw the same in libGDX (and WebGL), Godot and jME, as the transfert does today: anything in the
page is written three times and checked by a frame round.

1. **Depth-staggered fade** (no shader). Each slot fades over its own window, back slots first (or front first). Only
   the alphas change: `ParallaxPageReader.draw` already sets one per slot. Cheapest, no flush, no new shader; ported in
   `plax_background.gd` as a few lines; a frame round in `tests/transfer`. One number (the stagger) on the call or the
   page.
2. **Fade through a colour** (colour grade). B shows what a tint can do; a real grade (fade to white, lift, desaturate)
   needs the layers drawn through a shader, as the depth haze already does with PLAIN (`u_haze` mixes toward a colour):
   a grade colour and amount are the haze's mix with another colour, so it costs the haze's flush per layer and its
   three copies. Fade through white is the haze at 1.
3. **Fog creep** (C). The depth haze plus a noise mask: the shaders already have FOG's noise and `hazed()`. Costs a
   flush per layer during the transfert only, a mask uniform in the three shaders, and the swap at full fog (no
   cross-fade needed under it).
4. **Dissolve** (D). Each slot's two layers drawn with complementary masks: the same mask as C, alpha instead of
   colour. Same costs as C; two layers per slot are already drawn during a transfert, so no extra fill.
5. **An effect that creeps in.** A page's own SHADER layers ramp their numbers during the transfert: a WAVE whose
   amplitude rises to blur the old page (a heat shimmer), a FOG whose amplitude rises to thin it out. Needs the reader
   to drive a layer's amplitude from the transfert's progress; no new shader. It only works for pages that hold those
   layers.
6. **A game's own cover** (no library change). An `EMPTY` layer's hook or a `PARTICLES` layer (a snow burst, a flock)
   drawn over the swap. Already possible; a recipe in README, not code.

What is not settled, and is Simon's to choose: which of these the library should ship, and whether a transfert's style
is chosen by the game's call (`transfertIntoPage(page, seconds, style)`, no format change) or stored in the page (a format
bump, three readers, the editor). The lab is there to choose by.
