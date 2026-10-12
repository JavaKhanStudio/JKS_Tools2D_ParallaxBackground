class_name PlaxPlanet
extends RefCounted
## A page round its planet (r277, r347; docs/depth-tricks.md §3): the Godot port of core's PlanetBatch and
## ParallaxPageReader.planetInView, planetReachOf and planetPlaceAt, line for line. The layer's view is the side of a
## world turning round one centre `radius` under the middle of its bottom: a point (x, y) of the view goes to the angle
## (x - middle) / radius round it, at radius plus its height from it, so what scrolls across turns and what stands up
## leans out along the radius. Each IMAGE, SHADER and SEQUENCE layer is cut in vertical strips fixed in the view, each
## piece drawn as the quad its four corners go to; PlaxBackground._draw_region hands its regions here.

## PlanetBatch.STRIPS: how many strips the view is cut in, across.
const STRIPS := 256


## ParallaxPageReader.planetInView: the radius, in page units, of the planet `radius` percent of `world_width` in a
## layer's view at zoom `z`, the camera at `zoom` (a dollied layer's view is scaled back into the camera's, so every
## layer is wrapped round the same circle on screen); 0 for none.
static func radius_of(radius: float, world_width: float, z: float, zoom: float) -> float:
	var r := radius * (world_width / 100.0)
	return r * z / zoom if r > 0 else 0.0


## ParallaxPageReader.planetReachOf: how far above a view `view_w` by `view_h` its top corners show, round a planet of
## `radius` under its bottom; 0 for none.
static func reach_of(radius: float, view_w: float, view_h: float) -> float:
	if not radius > 0:
		return 0.0
	var far := radius + view_h
	return sqrt(far * far + view_w * view_w / 4) - far


## PlanetBatch.placeX/placeY: where (x, y) of a view from (0, 0), `view_w` wide, goes round a centre `radius` under the
## middle of its bottom, in page units, y up.
static func place(radius: float, view_w: float, x: float, y: float) -> Vector2:
	var a := (x - view_w / 2) / radius
	return Vector2(view_w / 2 + (radius + y) * sin(a), -radius + (radius + y) * cos(a))


## How far what stands at `x` leans round the planet, in Godot's radians (clockwise on screen): right of the middle,
## to the right. ParallaxPageReader.planetAngleAt, which is counterclockwise in degrees.
static func angle_at(radius: float, view_w: float, x: float) -> float:
	return (x - view_w / 2) / radius if radius > 0 else 0.0


## PlanetBatch.draw: `src` of `texture` in the box from (left, bottom), draw_w by draw_h page units (x 0..view_w of
## the layer's view), flipped as `fx`, `fy` say, cut at the strips, each piece the quad its corners go to round a
## centre `radius` under the view. What lies outside the view is not drawn.
static func draw(
		bg: PlaxBackground, texture: Texture2D, src: Rect2, left: float, bottom: float, draw_w: float, draw_h: float,
		fx: bool, fy: bool, radius: float, view_w: float
) -> void:
	var step := view_w / STRIPS
	var right := left + draw_w
	if not draw_w > 0 or not step > 0 or right <= 0 or left >= view_w:
		return
	var first := maxi(0, int(floorf(left / step)))
	var last := mini(STRIPS - 1, int(floorf(right / step)))
	var size := texture.get_size()
	# The image's top at the box's top and its bottom at the box's bottom; flipped on Y, the other way round.
	var v_top := (src.end.y if fy else src.position.y) / size.y
	var v_bottom := (src.position.y if fy else src.end.y) / size.y
	var colors := PackedColorArray([bg._modulate, bg._modulate, bg._modulate, bg._modulate])
	var top := bottom + draw_h
	for strip in range(first, last + 1):
		var p0 := maxf(left, strip * step)
		var p1 := minf(right, (strip + 1) * step)
		if not p1 > p0:
			continue
		# The piece's share of the image, left to right; flipped on X the image runs right to left.
		var t0 := (p0 - left) / draw_w
		var t1 := (p1 - left) / draw_w
		var u0 := (src.position.x + src.size.x * (1 - t0 if fx else t0)) / size.x
		var u1 := (src.position.x + src.size.x * (1 - t1 if fx else t1)) / size.x
		# SpriteBatch's order: bottom left, top left, top right, bottom right.
		var points := PackedVector2Array([
			_pixels(bg, place(radius, view_w, p0, bottom)), _pixels(bg, place(radius, view_w, p0, top)),
			_pixels(bg, place(radius, view_w, p1, top)), _pixels(bg, place(radius, view_w, p1, bottom))])
		var uvs := PackedVector2Array([
			Vector2(u0, v_bottom), Vector2(u0, v_top), Vector2(u1, v_top), Vector2(u1, v_bottom)])
		bg._target.draw_primitive(points, colors, uvs, texture)


## A point of the view, page units y up, in the screen's pixels, y down.
static func _pixels(bg: PlaxBackground, at: Vector2) -> Vector2:
	return Vector2(at.x * bg._ppu, bg._screen_h - at.y * bg._ppu)
