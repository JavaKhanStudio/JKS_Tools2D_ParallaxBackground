extends Node
## The PARTICLES round (r179): not a pixel comparison, each engine drawing its own particle system. Scrolls each scene
## of a round as shots.gd does (60 units/s, stepped at 1/60 s, a "transfer", "tint", "speedY" and "repeatY" too) and
## checks, 0, 6 and 12 s in, that each PARTICLES layer's Godot scene is instanced, that its node sits between the
## canvases of the layers before and after it, at its page's opacity, and where its layer has scrolled it: the scroll
## is counted here, apart from PlaxBackground's. A scene's "zoom" ([at 0 s, at 12 s], set before each check) and
## "dolly" (r278) place each layer's nodes in its own view, at its own zoom, worked out here too, and scale them by 1
## over it. Round a planet (r347) each node is turned as one block, moved and turned as the middle of its layer's box
## is round one centre under the view, worked out here as core's PlanetBatch.turn. Saves a still of each, and quits
## with 1 on any FAIL.
##   godot --path engines/godot res://tests/particles.tscn -- <repo root> <round dir> <out dir>

const SPEED := 60.0
const SHOT_TIMES := [0, 6, 12]
## Pixels: how far a node may be from where it is expected.
const TOLERANCE := 0.5

var failures := 0


func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	var root: String = args[0]
	var out: String = args[2]
	DirAccess.make_dir_recursive_absolute(out)
	var round = JSON.parse_string(FileAccess.get_file_as_string(root.path_join(args[1]).path_join("round.json")))
	var bg := PlaxBackground.new()
	bg.auto_act = false
	add_child(bg)
	var step := 1.0 / 60.0
	for scene in round.scenes:
		var page := PlaxPage.load_page(root.path_join(scene.page))
		page.repeat_on_y = scene.get("repeatY", page.repeat_on_y)
		var warned: int = bg._particles._warned.size()
		bg.set_page(page, PlaxAtlas.load_atlas(root.path_join(scene.atlasDir).path_join(page.atlas_name)))
		bg.tint_to(Color.WHITE, 0)
		bg.dolly = scene.get("dolly", false)
		var zoom: Array = scene.get("zoom", [1.0, 1.0])
		bg.speed_constant_x = SPEED
		bg.speed_constant_y = scene.get("speedY", 0.0)
		_check(scene.id, "warnings", bg._particles._warned.size() - warned,
				0 if _particle_layers(bg.layers).all(func(l): return not l.model.particlesGodot.is_empty()) else 1)
		var transfer: Dictionary = scene.get("transfer", {})
		var tint: Dictionary = scene.get("tint", {})
		# The scroll as this round counts it, per layer of the page set: where each should be. Layers are Dictionaries,
		# which hash by content: tracked by position, matched with is_same. Counted in doubles: a Vector2 is float32.
		var tracked: Array = bg.layers.duplicate()
		var expected_x := PackedFloat64Array()
		var expected_y := PackedFloat64Array()
		for l in tracked:
			expected_x.append(l.model.decal_X_Ratio * bg.world_width / 100.0)
			expected_y.append(l.model.decal_Y_Ratio * bg._world_height / 100.0)
		var steps := 0
		for at in SHOT_TIMES:
			while steps * step < at - step / 2:
				if not transfer.is_empty() and steps * step >= transfer.at:
					var incoming := PlaxPage.load_page(root.path_join(transfer.page))
					bg.transfert_into(incoming, PlaxAtlas.load_atlas(
							root.path_join(scene.atlasDir).path_join(incoming.atlas_name)), transfer.seconds)
					transfer = {}
				if not tint.is_empty() and steps * step >= tint.at:
					var c: Array = tint.color
					bg.tint_to(Color(c[0], c[1], c[2], c[3]), tint.seconds)
					tint = {}
				bg.act(step)
				steps += 1
				for i in tracked.size():
					var m: Dictionary = tracked[i].model
					expected_x[i] += -step * (m.speedXAtRest + SPEED) * m.parallaxScalingSpeedX
					expected_y[i] += -step * bg.speed_constant_y * m.parallaxScalingSpeedY
			bg.zoom = zoom[0] + (zoom[1] - zoom[0]) * (at / 12.0)
			var shot := "%s t%d" % [scene.id, at]
			_check_order(bg, shot)
			for l in _particle_layers(bg.layers + bg.transfer_layers):
				# Counted here for the page set; an incoming page's layers start where the fade synced them.
				var travel := Vector2(l.travel_x, l.travel_y)
				for i in tracked.size():
					if is_same(tracked[i], l):
						travel = Vector2(expected_x[i], expected_y[i])
				_check_layer(bg, l, travel, shot)
			await RenderingServer.frame_post_draw
			await RenderingServer.frame_post_draw
			get_viewport().get_texture().get_image().save_png(out.path_join("%s-t%d.png" % [scene.id, at]))
		print("particles: ", scene.id)
	print("particles: %s, %d failed" % ["PASS" if failures == 0 else "FAIL", failures])
	get_tree().quit(1 if failures > 0 else 0)


static func _particle_layers(of: Array) -> Array:
	return of.filter(func(l): return l.model.kind == "PARTICLES")


## Every layer's canvas under the gradients' one, in draw order: during a fade, slot by slot from the front, the
## outgoing layer then the incoming one.
func _check_order(bg: PlaxBackground, shot: String) -> void:
	var order := []
	var total := maxi(bg.layers.size(), bg.transfer_layers.size())
	for slot in total:
		var old := slot - (total - bg.layers.size())
		var new := slot - (total - bg.transfer_layers.size())
		if old >= 0:
			order.append(bg.layers[old].canvas)
		if new >= 0:
			order.append(bg.transfer_layers[new].canvas)
	_check(shot, "canvases in draw order", bg._canvas.get_children(), order)


func _check_layer(bg: PlaxBackground, l: Dictionary, travel: Vector2, shot: String) -> void:
	var what := "%s layer '%s'" % [l.model.particlesAnchor, l.model.name]
	var holder: Node2D = l.canvas
	var alpha: float = 1.0 if bg.transfer_layers.is_empty() else (bg._new_alpha if l.incoming else bg._old_alpha)
	_check(shot, what + " opacity", holder.modulate.a, bg.tint.a * alpha)
	_check(shot, what + " tint", Color(holder.modulate, 1), Color(bg.tint, 1))
	var nodes := holder.get_children()
	if l.model.particlesGodot.is_empty():
		_check(shot, what + " instances", nodes.size(), 0)
		return
	# The layer's zoom, worked out here: ParallaxPageReader.dollyZoom, by its speed against its page's fastest.
	var z := bg.zoom
	var front := 0.0
	for other in (bg.transfer_layers if l.incoming else bg.layers):
		front = maxf(front, absf(float(other.model.parallaxScalingSpeedX)))
	if bg.dolly and front > 0:
		z = 1.0 - minf(1.0, absf(float(l.model.parallaxScalingSpeedX)) / front) * (1.0 - bg.zoom)
	# Placed in its own view, world_width * z wide: from its corner, or dollied from the view at zoom 1's.
	var anchor := Vector2(bg.world_width * (z - 1), bg._world_height * (z - 1)) / 2 if bg.dolly else Vector2.ZERO
	var ppu := bg._screen_size().x / (bg.world_width * z)
	var screen_h := bg._screen_h
	# Round its page's planet, R in its view (the camera's R scaled as its view is): a point `at` of an effect whose box's
	# middle is `mid` (page units, y up) turned as `mid` is round a centre R under the middle of the view's bottom, in
	# screen pixels; and the angle of the turn, clockwise.
	var from_page: PlaxPage = bg.transfer_page if l.incoming else bg.page
	var r: float = from_page.planet_radius * bg.world_width / 100.0 * z / bg.zoom
	var half := bg.world_width * z / 2
	var turn := func(mid: Vector2) -> float: return (mid.x - half) / r if r > 0 else 0.0
	var turned := func(at: Vector2, mid: Vector2) -> Vector2:
		var to := mid
		if r > 0:
			var a: float = (mid.x - half) / r
			to = Vector2(half + (r + mid.y) * sin(a), -r + (r + mid.y) * cos(a))
		return Vector2(to.x * ppu, screen_h - to.y * ppu) + Vector2((at.x - mid.x) * ppu, -(at.y - mid.y) * ppu).rotated(
				turn.call(mid))
	_check(shot, what + " scroll", Vector2(l.travel_x, l.travel_y), travel)
	travel += anchor
	for node in nodes:
		_check(shot, what + " scaled by 1 over its zoom", (node as Node2D).scale, node.get_meta("plax_scale") / z)
	if l.model.particlesAnchor == "VIEW":
		_check(shot, what + " instances", nodes.size(), 1)
		if nodes.size() != 1:
			return
		var node: GPUParticles2D = nodes[0] as GPUParticles2D
		_check(shot, what + " is a GPUParticles2D", node != null, true)
		if node == null:
			return
		var decal := anchor + Vector2(l.model.decal_X_Ratio * bg.world_width / 100.0,
				l.model.decal_Y_Ratio * bg._world_height / 100.0)
		var mid := decal + Vector2(l.width, l.height) / 2
		_check(shot, what + " node, carried by the scroll", node.position, turned.call(travel, mid))
		_check(shot, what + " node, turned", node.rotation, turn.call(mid))
		# The emitter stays: the node's position plus its offset, in its scale and turn, is the authored offset from the
		# decal's place.
		var authored: Vector3 = node.get_meta("plax_offset")
		var offset: Vector3 = node.process_material.emission_shape_offset
		_check(shot, what + " emitter, kept in the view",
				node.position + (Vector2(offset.x, offset.y) / z).rotated(node.rotation),
				turned.call(decal, mid) + (Vector2(authored.x, authored.y) / z).rotated(turn.call(mid)))
		return
	# LAYER: a node at the bottom-left corner of every tile whose box, grown by its size each way (a CPUParticles2D),
	# meets the view: tiles at travel + k * step on a repeated axis, the one at travel on the other.
	var corners := []
	for y in _tiles(travel.y, l.height, l.height + l.model.padY, bg._repeat_y, bg._world_height * z):
		for x in _tiles(travel.x, l.width, l.width + l.model.padX, bg._repeat_x, bg.world_width * z):
			corners.append(turned.call(Vector2(x, y), Vector2(x + l.width / 2, y + l.height / 2)))
	var positions := nodes.map(func(n): return n.position)
	_check(shot, what + " one node per tile", positions.size(), corners.size())
	for corner in corners:
		_check(shot, what + " a node at the tile corner %s" % corner,
				positions.any(func(p): return p.distance_to(corner) <= TOLERANCE), true)


static func _tiles(origin: float, size: float, step: float, repeat: bool, view: float) -> Array:
	if not repeat:
		return [origin]
	var found := []
	var k := floori((-2 * size - origin) / step) - 1
	while origin + k * step - size < view:
		var at := origin + k * step
		if at + 2 * size > 0:
			found.append(at)
		k += 1
	return found


func _check(shot: String, what: String, got, want) -> void:
	var ok: bool
	if got is float or want is float:
		ok = absf(float(got) - float(want)) <= 1e-4
	elif got is Vector2:
		ok = got.distance_to(want) <= (TOLERANCE if what.contains("node") or what.contains("emitter") else 1e-4)
	elif got is Color:
		ok = got.is_equal_approx(want)
	else:
		ok = got == want
	if not ok:
		failures += 1
		print("FAIL %s: %s: %s, wanted %s" % [shot, what, got, want])
