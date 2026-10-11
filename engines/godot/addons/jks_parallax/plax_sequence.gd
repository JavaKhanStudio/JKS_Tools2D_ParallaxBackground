class_name PlaxSequence
extends RefCounted
## A SEQUENCE layer's cycle (ParallaxLayer's sequence half), for a PlaxBackground: its segments measured when the layer
## is built, its slots laid out when its seed changes, never in a frame, and drawn through the background's
## _draw_region (r324: out of plax_background.gd).


## A SEQUENCE layer (WholePage_Model.buildLayer, ParallaxLayer.measureSegments): its first segment sizes it as an image
## layer's region does, every segment is as wide as the layer's height and its own image make it, the first as wide as
## the layer. Empty when a segment's region is missing. Its cycle is not laid out yet (lay_out).
static func build(bg: PlaxBackground, model: Dictionary, from_page: PlaxPage, from_atlas: PlaxAtlas) -> Dictionary:
	if model.sequenceSegments.is_empty():
		push_error("PlaxBackground: the sequence layer '%s' has no segments" % model.name)
		return {}
	var segments: Array[Dictionary] = []
	var weights := PackedInt32Array()
	for named in model.sequenceSegments:
		var region := from_atlas.find_region(named.regionName, named.regionPosition) if from_atlas else {}
		if region.is_empty():
			push_error("PlaxBackground: region '%s' #%d of the sequence layer '%s' not found in %s"
					% [named.regionName, named.regionPosition, model.name, from_page.atlas_name])
			return {}
		if region.rotate:
			push_warning("PlaxBackground: region '%s' is packed rotated, which is not supported: drawn as is" % named.regionName)
		segments.append(PlaxBackground._measure_region(region, from_page.use_original_size))
		weights.append(named.weight)
	# The first segment sizes the layer, as an image layer's region does.
	var l := bg._build_layer(model, segments[0].region, from_page.use_original_size)
	l.segments = segments
	l.weights = weights
	l.narrowest = INF
	for segment in segments:
		l.narrowest = minf(l.narrowest, segment.aspect)
	return l


## The layer's cycle from `seed`, and where each slot starts, without the pads, in layer heights: slot i at
## height * edges[i] + i * padX. One edge more than the slots.
static func lay_out(l: Dictionary, seed: int) -> void:
	l.cycle = PlaxPage.draw_cycle(seed, l.weights, l.model.sequenceLength)
	var edges := PackedFloat64Array()
	edges.resize(l.cycle.size() + 1)
	var edge := 0.0
	for slot in l.cycle.size():
		edges[slot] = edge
		edge += l.segments[l.cycle[slot]].aspect
	edges[l.cycle.size()] = edge
	l.edges = edges


## ParallaxLayer.getCycleLeft: where a SEQUENCE layer's leftmost slot starts, from the tile's corner; below 0 only when
## a negative padX wider than a segment takes slots back past it.
static func cycle_left(l: Dictionary) -> float:
	if ordered(l):
		return 0.0
	var left := 0.0
	for slot in l.cycle.size():
		left = minf(left, l.height * l.edges[slot] + slot * l.model.padX)
	return left


## ParallaxLayer.getCycleRight: where its rightmost slot ends; the layer's width unless slots go back.
static func cycle_right(l: Dictionary) -> float:
	if ordered(l):
		return l.width
	var right := -INF
	for slot in l.cycle.size():
		right = maxf(right, l.height * (l.edges[slot] + l.segments[l.cycle[slot]].aspect) + slot * l.model.padX)
	return right


## Every slot starts right of the one before: no negative padX outweighs the narrowest segment.
static func ordered(l: Dictionary) -> bool:
	return l.height * l.narrowest + l.model.padX > 0


## ParallaxLayer.drawCycle: the slots of a SEQUENCE layer's cycle, starting at (x, y), that reach between `from_x` and
## `to_x`, each drawn by `bg`'s _draw_region. A binary search finds the first, then the walk stops past `to_x`, so a
## long cycle costs only what shows. A flip mirrors each segment in its own slot; the slots keep their order.
static func draw(
		bg: PlaxBackground, l: Dictionary, x: float, y: float, from_x: float, to_x: float, fx: bool, fy: bool
) -> void:
	var height: float = l.height
	var pad: float = l.model.padX
	var edges: PackedFloat64Array = l.edges
	var cycle: PackedInt32Array = l.cycle
	var slots := cycle.size()
	# A negative padX wider than a segment makes the edges go back: then every slot is looked at.
	var in_order := ordered(l)
	var first := 0
	if in_order:
		var last := slots
		while first < last:
			var middle := (first + last) >> 1
			if x + height * edges[middle + 1] + middle * pad > from_x:
				last = middle
			else:
				first = middle + 1
	for slot in range(first, slots):
		var left: float = x + height * edges[slot] + slot * pad
		if left >= to_x:
			if in_order:
				break
			continue
		var segment: Dictionary = l.segments[cycle[slot]]
		var width: float = height * segment.aspect
		if left + width <= from_x:
			continue
		bg._draw_region(segment, left, y, width, height, fx, fy)
