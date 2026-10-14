class_name PlaxFloor
extends RefCounted
## A FLOOR layer's rows, measured when the layer is built or resized, never in a frame, scrolled, drawn, and the layers
## in front planted on them, for a PlaxBackground (r324: out of plax_background.gd).

## The most rows a FLOOR layer is cut in (ParallaxLayer.MAX_FLOOR_ROWS), and mode 7's tilt (ParallaxLayer.FLOOR_TILT).
const MAX_FLOOR_ROWS := 512
const FLOOR_TILT := 0.6


## ParallaxLayer.measureFloor: a FLOOR layer's rows, bottom to top, from its settings and its image: each row's speed
## (the bottom row's to the top row's, straight, at the row's middle), its stretch across (MODE7: its speed over the
## bottom row's; LINE_SCROLL: 1) and the strip of the image it draws, in the atlas' pixels (MODE7: where the ground is
## at the row's depth, the image's height one tile of depth; LINE_SCROLL: the image's rows i/n to (i+1)/n).
static func measure(l: Dictionary) -> void:
	var m: Dictionary = l.model
	var n := clampi(int(m.floorRows), 2, MAX_FLOOR_ROWS)
	var bottom: float = _f32(m.parallaxScalingSpeedX)
	var top: float = _f32(m.floorTopSpeed)
	var mode7: bool = m.floorMapping == "MODE7"
	var recedes: bool = mode7 and absf(bottom) > absf(top) and bottom * top >= 0 and l.height > 0
	var near_in_heights: float = l.floor_h / l.height * bottom / (FLOOR_TILT * (bottom - top)) if recedes else 0.0
	var drawn := PlaxEffects.drawn_rect(l.region)
	l.row_speed = PackedFloat64Array()
	l.row_stretch = PackedFloat64Array()
	l.row_strips = []
	l.row_speed.resize(n)
	l.row_stretch.resize(n)
	for i in n:
		var t0 := float(i) / n
		var t1 := float(i + 1) / n
		var tc := (i + 0.5) / n
		var speed := bottom + (top - bottom) * tc
		l.row_speed[i] = speed
		l.row_stretch[i] = maxf(0.02, speed / bottom) if mode7 and bottom != 0 else 1.0
		var from := t0
		var to := t1
		if recedes:
			from = _along(bottom, top, t0, near_in_heights)
			to = _along(bottom, top, t1, near_in_heights)
			var start := from - floorf(from)
			to = minf(1.0, start + (to - from))
			from = start
		# The image's rows count down from its top, t up from its bottom.
		l.row_strips.append(Rect2(
				drawn.position.x, drawn.position.y + drawn.size.y * (1 - to), drawn.size.x, drawn.size.y * (to - from)))
	# Allocated once: a resize measures the rows again and keeps where they have scrolled to.
	if not l.has("row_distance") or l.row_distance.size() != n:
		l.row_distance = PackedFloat64Array()
		l.row_distance.resize(n)
		l.row_distance.fill(l.distance_x)



## Each row of a FLOOR layer scrolled by `travel` (the page's speed times the step) times its own speed, kept within one
## of its tiles: a floor is tiled across whatever the page repeats (ParallaxLayer.act).
static func scroll(l: Dictionary, travel: float) -> void:
	for i in l.row_distance.size():
		var step: float = l.width * l.row_stretch[i]
		var distance: float = l.row_distance[i] - travel * l.row_speed[i]
		l.row_distance[i] = fmod(distance, step) if step > 0 else distance


## ParallaxPageReader.plant: every layer in front of the page's last FLOOR layer scrolls at the speed of the floor's row
## under its foot (foot_y); one whose foot is above the floor's top row, and every other layer, at its own.
static func plant(page_layers: Array[Dictionary], world_height: float) -> void:
	var floor := -1
	for i in page_layers.size():
		if page_layers[i].model.kind == "FLOOR":
			floor = i
	for i in page_layers.size():
		page_layers[i].planted = (planted_speed_on(page_layers[floor], page_layers[i], world_height)
				if floor >= 0 and i > floor else NAN)


## ParallaxPageReader.plantedSpeedOn: the speed of `floor`'s row under `l`'s foot, NAN above the floor's top row.
static func planted_speed_on(floor: Dictionary, l: Dictionary, world_height: float) -> float:
	var bottom: float = floor.model.decal_Y_Ratio * (world_height / 100.0)
	var height: float = floor.floor_h
	var t: float = (foot_y(l, world_height) - bottom) / height if height > 0 else 0.0
	if t > 1:
		return NAN
	t = maxf(0.0, t)
	return floor.model.parallaxScalingSpeedX + (floor.model.floorTopSpeed - floor.model.parallaxScalingSpeedX) * t


## ParallaxLayer.getFootY: where a layer stands, its decal, and over it the rows an atlas trimmed off an IMAGE or SHADER
## layer's image's bottom (a LURKER's too: drawn as an IMAGE layer).
static func foot_y(l: Dictionary, world_height: float) -> float:
	var foot: float = l.model.decal_Y_Ratio * (world_height / 100.0)
	if l.model.kind in ["IMAGE", "SHADER", "LURKER"]:
		foot += l.height * ((1 - l.trim_bottom - l.packed_h) if l.model.flipY else l.trim_bottom)
	return foot


## ParallaxLayer.drawFloor: a FLOOR layer's rows in its box, its bottom at `y`, each tiled across the view (x 0 to
## `view_w`) from where it has scrolled, its tiles `width * stretch` wide, stretched about the view's middle; drawn on
## `bg`'s target at its scale and tint.
static func draw(bg: PlaxBackground, l: Dictionary, y: float, view_w: float, view_h: float) -> void:
	var n: int = l.row_strips.size()
	var width: float = l.width
	var row_h: float = l.floor_h / n
	var middle := view_w / 2
	var texture: Texture2D = l.region.texture
	var ppu := bg._ppu
	var screen_h := bg._screen_h
	for i in n:
		var row_y := y + i * row_h
		if row_y + row_h <= 0 or row_y >= view_h:
			continue
		var stretch: float = l.row_stretch[i]
		var across := width * stretch
		if not across > 0:
			continue
		var origin: float = middle + (0 - middle) * stretch + l.row_distance[i]
		var first := origin + across * floorf((0 - origin) / across)
		var count := int(ceilf((view_w - first) / across))
		for k in count:
			var x := first + k * across
			bg._target.draw_texture_rect_region(texture,
					Rect2(x * ppu, screen_h - (row_y + row_h) * ppu, across * ppu, row_h * ppu), l.row_strips[i], bg._modulate)

## ParallaxLayer.along: how far along the ground the row at `t` is from the bottom row, in image heights.
static func _along(bottom: float, top: float, t: float, near_in_heights: float) -> float:
	var speed := bottom + (top - bottom) * t
	return (bottom / speed - 1) * near_in_heights


static func _f32(value: float) -> float:
	return PackedFloat32Array([value])[0]
