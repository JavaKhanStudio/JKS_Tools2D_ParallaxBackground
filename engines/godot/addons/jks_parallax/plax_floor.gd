class_name PlaxFloor
extends RefCounted
## A FLOOR layer's rows, measured when the layer is built or resized, never in a frame: PlaxBackground draws them
## (_draw_floor) and plants the layers in front on them (_plant).

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


## ParallaxLayer.along: how far along the ground the row at `t` is from the bottom row, in image heights.
static func _along(bottom: float, top: float, t: float, near_in_heights: float) -> float:
	var speed := bottom + (top - bottom) * t
	return (bottom / speed - 1) * near_in_heights


static func _f32(value: float) -> float:
	return PackedFloat32Array([value])[0]
