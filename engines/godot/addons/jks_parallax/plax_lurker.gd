class_name PlaxLurker
extends RefCounted
## A LURKER layer's sightings, for a PlaxBackground (ParallaxPageReader.sightLurkers, r371; docs/lurker-layers.md):
## the figure appears or vanishes only while nothing of it is on screen. Gone at the sighting after one where it was
## seen, else there by its seed's draws (PlaxPage.sequence_next, 32-bit masked: the JVM's picks), or when the game says
## (reveal). Its state is on the layer: lurker_state, lurker_started, lurker_there, lurker_seen, lurker_in_sight,
## lurker_forced. Run every act() step against the view of the last draw, and at each draw against its own.


## Whether layer `l`'s figure is drawn now (ParallaxLayer.isLurkerThere): true for one no sighting has run yet.
static func is_there(l: Dictionary) -> bool:
	return not l.get("lurker_started", false) or l.lurker_there


## Runs the sightings of every LURKER layer on screen, of both pages during a cross-fade, at the camera zoom `zoom` of
## the last draw (NAN before the first: nothing is in the view).
static func sight(bg: PlaxBackground, zoom: float) -> void:
	_sight_page(bg, bg.layers, false, zoom)
	_sight_page(bg, bg.transfer_layers, true, zoom)


static func _sight_page(bg: PlaxBackground, page: Array[Dictionary], incoming: bool, zoom: float) -> void:
	for i in page.size():
		var figure: Dictionary = page[i]
		if figure.model.kind != "LURKER":
			continue
		if not figure.get("lurker_started", false):
			_start(figure)
		var in_sight := _in_sight(bg, page, i, incoming, zoom)
		if in_sight and figure.lurker_there:
			figure.lurker_seen = true
		elif not in_sight and figure.lurker_in_sight:
			_hide(figure)
		figure.lurker_in_sight = in_sight


## The figure's first draw, from its seed (0: PlaxPage.ZERO_SEED), as each later one: rule 2.
static func _start(figure: Dictionary) -> void:
	var seed: int = int(figure.model.lurkerSeed) & 0xFFFFFFFF
	figure.lurker_state = PlaxPage.sequence_next(seed if seed != 0 else PlaxPage.ZERO_SEED)
	figure.lurker_there = _draws_there(figure)
	figure.lurker_seen = false
	figure.lurker_in_sight = false
	figure.lurker_forced = false
	figure.lurker_started = true


## A sighting has ended: whether the figure is there at the next one. Forced by reveal; else gone after it was seen;
## else the state steps once and it is there by 1 chance in its odds. Integers only.
static func _hide(figure: Dictionary) -> void:
	if figure.lurker_forced:
		figure.lurker_there = true
	elif figure.lurker_seen:
		figure.lurker_there = false
	else:
		figure.lurker_state = PlaxPage.sequence_next(figure.lurker_state)
		figure.lurker_there = _draws_there(figure)
	figure.lurker_forced = false
	figure.lurker_seen = false


## (state >>> 1) % lurkerOdds == 0, the state an unsigned 32-bit int.
static func _draws_there(figure: Dictionary) -> bool:
	return (figure.lurker_state >> 1) % maxi(1, int(figure.model.lurkerOdds)) == 0


## ParallaxPageReader.lurkerInSight: whether a copy of the figure `index` of `page` is in the view (its tile's box, in
## its own view when dollied; the view's bottom left corner at 0, 0) and not wholly covered by one layer in front of it.
## The cover counts only where it was worked out for: a page repeating on X, the figure's own row, no planet, no
## panorama, the dolly at zoom 1, no cross-fade, the tint at full opacity, and the layers' Y apart as saved.
static func _in_sight(bg: PlaxBackground, page: Array[Dictionary], index: int, incoming: bool, zoom: float) -> bool:
	var figure: Dictionary = page[index]
	var width: float = figure.width
	var height: float = figure.height
	if not width > 0 or not height > 0 or is_nan(zoom):
		return false
	var z := PlaxEffects.dolly_zoom(zoom, PlaxEffects.front_speed_of(page), figure.model) if bg.dolly else zoom
	var vw := bg.world_width * z
	var vh := bg._world_height * z
	var anchor := bg._anchor_of(z)
	var origin_x: float = anchor.x + figure.distance_x
	var origin_y: float = anchor.y + figure.distance_y
	var from_page := bg.transfer_page if incoming else bg.page
	var covers: bool = (bg._repeat_x and not figure.model.lurkerCover.is_empty() and not from_page.planet_radius > 0
			and PlaxPanorama.near_tower_of(page).is_empty() and (not bg.dolly or zoom == 1)
			and bg.transfer_layers.is_empty() and bg.tint.a >= 1)
	var step_x: float = width + figure.model.padX
	var step_y: float = height + figure.model.padY
	var tile_x := bg._repeat_x and step_x > 0
	var tile_y := bg._repeat_y and step_y > 0
	var first_column := -int(ceilf((origin_x + width) / step_x)) if tile_x else 0
	var row := -int(ceilf((origin_y + height) / step_y)) if tile_y else 0
	var first_row := row
	while true:
		var y := origin_y + row * step_y
		if row > first_row and (not tile_y or y >= vh):
			break
		if y + height > 0 and y < vh:
			var column := first_column
			while true:
				var x := origin_x + column * step_x
				if column > first_column and (not tile_x or x >= vw):
					break
				if x + width > 0 and x < vw:
					if not (covers and row == 0 and _covered(bg, page, index, x - anchor.x)):
						return true
				column += 1
		row += 1
	return false


## ParallaxPageReader.lurkerCovered: whether a layer in front covers the copy of figure `index` whose tile corner is
## `x` from the anchor: its offset against that layer's tiles, modulo their period, in one of the cover's intervals.
static func _covered(bg: PlaxBackground, page: Array[Dictionary], index: int, x: float) -> bool:
	var figure: Dictionary = page[index]
	var cover: Array = figure.model.lurkerCover
	var k := 0
	while k + 2 < cover.size():
		var near_at := index + int(cover[k])
		var from: float = cover[k + 1]
		var to: float = cover[k + 2]
		k += 3
		if near_at <= index or near_at >= page.size():
			continue
		var near: Dictionary = page[near_at]
		var period: float = near.width + near.model.padX
		if not period > 0:
			continue
		# Their Y apart as saved, which the sweep took them at.
		var rest_apart: float = (figure.model.decal_Y_Ratio - near.model.decal_Y_Ratio) * bg._world_height / 100.0
		if absf(figure.distance_y - near.distance_y - rest_apart) > 1e-3 * figure.height:
			continue
		var d := fmod(fmod(x - near.distance_x, period) + period, period)
		if fmod(fmod(d - from, period) + period, period) <= to - from:
			return true
	return false


## ParallaxPageReader.reveal: the next sighting of every LURKER layer named `layer_name` in `page` shows its figure,
## at once when it is not in sight now, else from the end of this sighting.
static func reveal(page: Array[Dictionary], layer_name: String) -> void:
	for figure in page:
		if figure.model.kind != "LURKER" or figure.model.name != layer_name:
			continue
		if not figure.get("lurker_started", false):
			_start(figure)
		if figure.lurker_in_sight:
			figure.lurker_forced = true
		else:
			figure.lurker_there = true
