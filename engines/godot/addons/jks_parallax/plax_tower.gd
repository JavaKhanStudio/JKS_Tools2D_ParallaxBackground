class_name PlaxTower
extends RefCounted
## An IMAGE layer wrapped round a tower (r276, docs/depth-tricks.md §2), for a PlaxBackground: ParallaxPageReader's
## towerRows and ParallaxLayer.drawTower, line for line.

## How many strips a tower's front half is cut in, by angle (ParallaxLayer.TOWER_STRIPS).
const TOWER_STRIPS := 128
const _STRIP_ANGLE := PI / TOWER_STRIPS


## ParallaxLayer.isTower: an IMAGE layer with a radius.
static func is_tower(l: Dictionary) -> bool:
	return l.model.kind == "IMAGE" and l.model.towerRadius > 0


## ParallaxPageReader.towerRows: the tower round its axis in the view's middle, its tile at (x, y); once, or on a page
## repeating on Y every repetition up that intersects the view (y 0 to `view_h`).
static func draw_rows(bg: PlaxBackground, l: Dictionary, x: float, y: float, view_w: float, view_h: float) -> void:
	var height: float = l.height
	var step_y: float = height + l.model.padY
	if not height > 0:
		return
	var tile_y: bool = bg._repeat_y and step_y > 0
	var start_y := y - ceilf((y + height) / step_y) * step_y if tile_y else y
	var count_y := int(ceilf((view_h - start_y) / step_y)) + 1 if tile_y else 1
	for row in count_y:
		var draw_y := start_y + row * step_y
		if draw_y + height <= 0 or draw_y >= view_h:
			continue
		draw(bg, l, view_w / 2, x, draw_y, 0.0, view_w)


## ParallaxLayer.drawTower: the layer round a cylinder of its radius (percent of the world width) whose axis is at
## `axis_x`, its box's bottom at `y`. The point an arc s round the wall from its front shows what the flat layer, its
## tile at `x`, shows at axis_x + s, drawn at axis_x + radius sin(s / radius); the front half cut in TOWER_STRIPS strips
## of equal angle and at each image's edges, each piece at 1 - towerShade (1 - cos a) of the tint, at its middle.
static func draw(bg: PlaxBackground, l: Dictionary, axis_x: float, x: float, y: float, view_left: float,
		view_right: float) -> void:
	var m: Dictionary = l.model
	var radius: float = m.towerRadius * (bg.world_width / 100.0)
	var width: float = l.width
	var height: float = l.height
	if not radius > 0 or not width > 0 or not height > 0:
		return
	# What shows of the front half: its arc from the view's left edge to its right one.
	var from := radius * asin(clampf((view_left - axis_x) / radius, -1, 1))
	var to := radius * asin(clampf((view_right - axis_x) / radius, -1, 1))
	if not to > from:
		return
	var fx: bool = m.flipX
	var fy: bool = m.flipY
	# The image in its tile, along the arc, and up the box, as _draw_region places it.
	var image_left: float = width * ((1 - l.trim_left - l.packed_w) if fx else l.trim_left)
	var image_w: float = width * l.packed_w
	var draw_h: float = height * l.packed_h
	var bottom: float = y + height * ((1 - l.trim_bottom - l.packed_h) if fy else l.trim_bottom)
	# Tile k's image starts at an arc of corner + k * step; a step <= 0 can't tile: the image goes round once.
	var corner := x - axis_x + image_left
	var step: float = width + m.padX
	var first := 0
	var last := 0
	if step > 0:
		first = int(floorf((from - corner - image_w) / step))
		last = int(ceilf((to - corner) / step))
	var src := PlaxEffects.drawn_rect(l.region)
	var texture: Texture2D = l.region.texture
	var ppu := bg._ppu
	var top := bg._screen_h - (bottom + draw_h) * ppu
	var tint := bg._modulate
	for k in range(first, last + 1):
		var left := corner + k * step
		var lo := maxf(from, left)
		var hi := minf(to, left + image_w)
		if not hi > lo:
			continue
		var strip := maxi(0, int(floorf((lo / radius + PI / 2) / _STRIP_ANGLE)))
		var last_strip := mini(TOWER_STRIPS - 1, int(floorf((hi / radius + PI / 2) / _STRIP_ANGLE)))
		while strip <= last_strip:
			var p0 := maxf(lo, radius * (strip * _STRIP_ANGLE - PI / 2))
			var p1 := minf(hi, radius * ((strip + 1) * _STRIP_ANGLE - PI / 2))
			strip += 1
			if not p1 > p0:
				continue
			var x0 := axis_x + radius * sin(p0 / radius)
			var x1 := axis_x + radius * sin(p1 / radius)
			var bright: float = 1 - m.towerShade * (1 - cos((p0 + p1) / (2 * radius)))
			# The piece's share of its image, left to right; flipped on X the image runs right to left.
			var f0 := (p0 - left) / image_w
			var f1 := (p1 - left) / image_w
			var g0 := 1 - f1 if fx else f0
			var g1 := 1 - f0 if fx else f1
			var piece := Rect2(src.position.x + src.size.x * g0, src.position.y, src.size.x * (g1 - g0), src.size.y)
			var px := Vector2((x1 - x0) * ppu, draw_h * ppu)
			var shade := Color(tint.r * bright, tint.g * bright, tint.b * bright, tint.a)
			var origin := Vector2(x0 * ppu, top)
			if fx or fy:
				bg._target.draw_set_transform(
					origin + Vector2(px.x if fx else 0.0, px.y if fy else 0.0), 0, Vector2(-1 if fx else 1, -1 if fy else 1))
				bg._target.draw_texture_rect_region(texture, Rect2(Vector2.ZERO, px), piece, shade)
				bg._target.draw_set_transform(Vector2.ZERO)
			else:
				bg._target.draw_texture_rect_region(texture, Rect2(origin, px), piece, shade)
