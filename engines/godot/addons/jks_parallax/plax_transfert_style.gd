class_name PlaxTransfertStyle
extends RefCounted
## How a cross-fade into another page is drawn (PlaxBackground.transfert_into's `style`): the Godot copy of core's
## TransfertStyle, slot_ramp line for line. The game's call, never stored in a page.
##
##   bg.transfert_into(page, atlas, 3.0, PlaxTransfertStyle.depth_stagger(0.5))
##
## FADE (or no style): every layer slot fades at once. DEPTH_STAGGER: each slot over its own window, the back ones first.
## DISSOLVE: in each slot the incoming layer eats the outgoing one in patches (PlaxEffects' dissolved()), over the same
## windows. THROUGH_COLOR: in each slot the outgoing layer goes to a colour, then the incoming one comes out of it
## (PlaxEffects' grade), the gradients too, over the same windows.

## TransfertStyle.MAX_STAGGER.
const MAX_STAGGER := 2.0
## TransfertStyle.MIN_PATCHES, MAX_PATCHES, MIN_SOFTNESS, MAX_SOFTNESS, DISSOLVE_DRIFT.
const MIN_PATCHES := 0.5
const MAX_PATCHES := 64.0
const MIN_SOFTNESS := 0.01
const MAX_SOFTNESS := 0.5
const DISSOLVE_DRIFT := 0.15

## "FADE", "DEPTH_STAGGER", "DISSOLVE" or "THROUGH_COLOR", as TransfertStyle.Kind.
var kind := "FADE"
## How far apart the slots' windows are, 0 to MAX_STAGGER; 0 for FADE.
var stagger := 0.0
## A dissolve's patches across the view, and how soft their edges are; 0 for every other style.
var patches := 0.0
var softness := 0.0
## THROUGH_COLOR's colour, opaque; black for every other style.
var color := Color.BLACK


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


## TransfertStyle.dissolve: about `patches` patches across the view, their edges `softness` soft (0.01 hard, 0.5 a
## blur), the slots over depth_stagger's windows. Clamped as there.
static func dissolve(patch_count: float, soft: float, value: float) -> PlaxTransfertStyle:
	var style := PlaxTransfertStyle.new()
	style.kind = "DISSOLVE"
	style.stagger = clampf(value, 0, MAX_STAGGER)
	style.patches = clampf(patch_count, MIN_PATCHES, MAX_PATCHES)
	style.softness = clampf(soft, MIN_SOFTNESS, MAX_SOFTNESS)
	return style


## TransfertStyle.throughColor: each slot's outgoing layer mixed toward `through` (its alpha ignored, clamped to 0..1)
## until it is all that colour halfway through the slot's window, then the incoming one out of it; the slots over
## depth_stagger's windows.
static func through_color(through: Color, value: float) -> PlaxTransfertStyle:
	var style := PlaxTransfertStyle.new()
	style.kind = "THROUGH_COLOR"
	style.stagger = clampf(value, 0, MAX_STAGGER)
	style.color = Color(clampf(through.r, 0, 1), clampf(through.g, 0, 1), clampf(through.b, 0, 1), 1)
	return style


## TransfertStyle.gradeOf: how much of the colour a layer of a slot at `ramp` is mixed toward, 0 to 1, peaking at 1
## halfway.
static func grade_of(ramp: float) -> float:
	return 2 * ramp if ramp < 0.5 else 2 - 2 * ramp


## TransfertStyle.incomingShows: whether a slot at `ramp` draws its incoming layer rather than its outgoing one.
static func incoming_shows(ramp: float) -> bool:
	return ramp >= 0.5


## TransfertStyle.gradient: a gradient's colour `progress` into a fade from `from` to `to`; THROUGH_COLOR's goes
## through its colour at the back slot's pace.
func gradient(from: Color, to: Color, progress: float) -> Color:
	if kind != "THROUGH_COLOR":
		return from.lerp(to, progress)
	var ramp := slot_ramp(progress, 0, 1)
	return (to if incoming_shows(ramp) else from).lerp(color, grade_of(ramp))


## TransfertStyle.cellsUp: the dissolve's noise cells up a view `view_w` by `view_h`, stretched 2.5 times.
func cells_up(view_w: float, view_h: float) -> float:
	return patches * view_h / view_w * 2.5 if view_w > 0 else 0.0


## TransfertStyle.drift: how far the dissolve's noise has drifted on x at `seconds` of the clock, in cells, wrapped at 8.
static func drift(seconds: float) -> float:
	var d := fmod(seconds * DISSOLVE_DRIFT, 8.0)
	return d + 8.0 if d < 0 else d


## TransfertStyle.slotRamp: how far along slot `slot` of `total` (0 at the back) is, 0 to 1, when the fade is at
## `progress`: the incoming layer's opacity, the outgoing one's 1 minus it.
func slot_ramp(progress: float, slot: int, total: int) -> float:
	var k := slot / float(total - 1) if total > 1 else 0.0
	return clampf(progress * (1 + stagger) - stagger * k, 0, 1)
