class_name PlaxTransfertStyle
extends RefCounted
## How a cross-fade into another page is drawn (PlaxBackground.transfert_into's `style`): the Godot copy of core's
## TransfertStyle, slot_ramp line for line. The game's call, never stored in a page.
##
##   bg.transfert_into(page, atlas, 3.0, PlaxTransfertStyle.depth_stagger(0.5))
##
## FADE (or no style): every layer slot fades at once. DEPTH_STAGGER: each slot over its own window, the back ones first.

## TransfertStyle.MAX_STAGGER.
const MAX_STAGGER := 2.0

## "FADE" or "DEPTH_STAGGER", as TransfertStyle.Kind.
var kind := "FADE"
## How far apart the slots' windows are, 0 to MAX_STAGGER; 0 but for DEPTH_STAGGER.
var stagger := 0.0


## Every layer slot at once: what a cross-fade with no style does.
static func fade() -> PlaxTransfertStyle:
	return PlaxTransfertStyle.new()


## TransfertStyle.depthStagger: the back slot over the first 1 / (1 + stagger) of the fade, the front one over the
## last; `stagger` clamped to 0..MAX_STAGGER, 0 is fade().
static func depth_stagger(value: float) -> PlaxTransfertStyle:
	var style := PlaxTransfertStyle.new()
	var s := clampf(value, 0, MAX_STAGGER)
	if s > 0:
		style.kind = "DEPTH_STAGGER"
		style.stagger = s
	return style


## TransfertStyle.slotRamp: how far along slot `slot` of `total` (0 at the back) is, 0 to 1, when the fade is at
## `progress`: the incoming layer's opacity, the outgoing one's 1 minus it.
func slot_ramp(progress: float, slot: int, total: int) -> float:
	var k := slot / float(total - 1) if total > 1 else 0.0
	return clampf(progress * (1 + stagger) - stagger * k, 0, 1)
