class_name PlaxPlanet
extends RefCounted
## A page bent round its planet (r277, docs/depth-tricks.md §3): the Godot port of ParallaxPageReader.planetBendOf
## and core's PlanetBatch, line for line. Each IMAGE, SHADER and SEQUENCE layer is lowered by bend dx² at dx from the
## view's middle, bend = d / 2R, d its depth from its speed against its page's front's: the far layers bend more, a
## still one (the sky) not at all. Drawn as vertical strips fixed in the view, each piece of a region lowered by its
## strip's drop; PlaxBackground._draw_region hands its regions here when the layer is bent.

## PlanetBatch.STRIPS: how many strips the view is cut in, across.
const STRIPS := 256


## ParallaxPageReader.planetBendOf: how much layer `model` is bent, its page's planet `radius` percent of
## `world_width` and its page's fastest speed ratio X `front` (PlaxEffects.front_speed_of): front / speed / 2R, as
## magnitudes; 0 for no planet, a page that does not scroll and a still layer.
static func bend_of(radius: float, world_width: float, front: float, model: Dictionary) -> float:
	var r := radius * (world_width / 100.0)
	var speed := absf(float(model.parallaxScalingSpeedX))
	if not r > 0 or not front > 0 or not speed > 0:
		return 0.0
	return front / speed / (2 * r)


## PlanetBatch.dropOf: how far strip `strip` of a view `view_w` wide is lowered, bent by `bend`.
static func drop_of(bend: float, view_w: float, strip: int) -> float:
	var dx := (strip + 0.5 - STRIPS / 2.0) * (view_w / STRIPS)
	return bend * dx * dx


## PlanetBatch.draw: `src` of `texture` in the box from (left, bottom), draw_w by draw_h page units (x 0..view_w of
## the layer's view), flipped as `fx`, `fy` say, cut at the strips, each piece lowered by its strip's drop. What lies
## outside the view is not drawn.
static func draw(
		bg: PlaxBackground, texture: Texture2D, src: Rect2, left: float, bottom: float, draw_w: float, draw_h: float,
		fx: bool, fy: bool, bend: float, view_w: float
) -> void:
	var step := view_w / STRIPS
	var right := left + draw_w
	if not draw_w > 0 or not step > 0 or right <= 0 or left >= view_w:
		return
	var first := maxi(0, int(floorf(left / step)))
	var last := mini(STRIPS - 1, int(floorf(right / step)))
	var ppu := bg._ppu
	for strip in range(first, last + 1):
		var p0 := maxf(left, strip * step)
		var p1 := minf(right, (strip + 1) * step)
		if not p1 > p0:
			continue
		# The piece's share of the image, left to right; flipped on X the image runs right to left.
		var t0 := (p0 - left) / draw_w
		var t1 := (p1 - left) / draw_w
		var g0 := 1 - t1 if fx else t0
		var g1 := 1 - t0 if fx else t1
		var piece := Rect2(src.position.x + src.size.x * g0, src.position.y, src.size.x * (g1 - g0), src.size.y)
		var px := Vector2((p1 - p0) * ppu, draw_h * ppu)
		var origin := Vector2(p0 * ppu, bg._screen_h - (bottom - drop_of(bend, view_w, strip) + draw_h) * ppu)
		if fx or fy:
			bg._target.draw_set_transform(
				origin + Vector2(px.x if fx else 0.0, px.y if fy else 0.0), 0, Vector2(-1 if fx else 1, -1 if fy else 1))
			bg._target.draw_texture_rect_region(texture, Rect2(Vector2.ZERO, px), piece, bg._modulate)
			bg._target.draw_set_transform(Vector2.ZERO)
		else:
			bg._target.draw_texture_rect_region(texture, Rect2(origin, px), piece, bg._modulate)
