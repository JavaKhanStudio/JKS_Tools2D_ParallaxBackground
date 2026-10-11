class_name PlaxParticles
extends RefCounted
## The PARTICLES layers of a PlaxBackground, which holds one: ParallaxPageReader's particle branch.
##
## A PARTICLES layer instances the Godot scene its page names (particlesGodot, relative to the page's atlas folder, or
## res:// or absolute): a GPUParticles2D or a CPUParticles2D, in screen pixels, y down, unscaled. It sits between the
## layers before and after it, tinted and faded with its page, and runs on Godot's clock, not act()'s. Anchored to the
## LAYER, one instance per tile the view shows sits at the tile's bottom-left corner, its particles carried by the
## tile (local_coords on); each is its own simulation. Anchored to the VIEW, one instance emits from the view's
## bottom-left moved by the layer's decal, and the particles already out drift by the layer's scroll: the node moves
## with the layer and its process material's emission_shape_offset moves back by as much (a CPUParticles2D is converted
## to a GPUParticles2D for that). A layer with no scene draws nothing, and the reader warns once. Round a planet
## (r330), each instance is lowered as one block by the drop at the middle of its layer's box (planet_drop_at).

var _bg: PlaxBackground
# PARTICLES layers already warned about ("<page id>:<layer index>"): a page that draws no particles says so once.
var _warned := {}


func _init(background: PlaxBackground) -> void:
	_bg = background


## The layer's particlesGodot scene, or null after one warning: no file named, none there, or not a particle node.
func scene_of(model: Dictionary, from_page: PlaxPage, from_atlas: PlaxAtlas, index: int) -> PackedScene:
	var file: String = model.particlesGodot
	var why := ""
	var scene: PackedScene = null
	if file.is_empty():
		why = "names no Godot scene"
	else:
		var path := file
		if not file.begins_with("res://") and not file.begins_with("user://") and not file.is_absolute_path():
			path = (from_atlas.get_dir() if from_atlas else "res://").path_join(file)
		if not ResourceLoader.exists(path) and not FileAccess.file_exists(path):
			why = "names %s, which is not there" % path
		else:
			scene = load(path) as PackedScene
			var root := scene.instantiate() if scene else null
			if not (root is GPUParticles2D or root is CPUParticles2D):
				why = "names %s, whose root is not a GPUParticles2D or a CPUParticles2D" % path
				scene = null
			if root:
				root.free()
	if scene == null:
		var key := "%d:%d" % [from_page.get_instance_id(), index]
		if not _warned.has(key):
			_warned[key] = true
			push_warning("PlaxBackground: PARTICLES layer '%s' %s: it draws nothing" % [model.name, why])
	return scene


## Moves every PARTICLES layer's instances where its layer is now, makes the ones a tile needs, and tints them. At a
## zoom, each layer's are placed in its own view (PlaxBackground._draw_layer) and scaled by 1 over its zoom.
func place() -> void:
	if _bg.page == null:
		return
	for l in _bg.layers + _bg.transfer_layers:
		if l.model.kind != "PARTICLES":
			continue
		var holder: Node2D = l.canvas
		var alpha := _bg._alpha_of(l)
		var tint := _bg.tint
		holder.modulate = Color(tint.r, tint.g, tint.b, tint.a * alpha)
		holder.visible = holder.modulate.a > 0
		if l.scene == null:
			continue
		_bg._update_scale(_bg._zoom_of(l))
		_bg._view_w = _bg.world_width * _bg._zoom_of(l)
		if l.model.particlesAnchor == "VIEW":
			_place_view_particles(l)
		else:
			_place_tile_particles(l)


## VIEW: one instance, carried by the layer's scroll since its decal, its emitter moved back to the view's bottom-left
## plus the decal, as ParallaxPageReader places it.
func _place_view_particles(l: Dictionary) -> void:
	var node: Node2D = _instance(l, Vector2i.ZERO)
	var z := _bg._zoom_of(l)
	var ppu := _bg._ppu
	var screen_h := _bg._screen_h
	var anchor := _bg._anchor_of(z)
	node.scale = node.get_meta("plax_scale") / z
	var decal_x: float = l.model.decal_X_Ratio * (_bg.world_width / 100.0)
	var decal_y: float = l.model.decal_Y_Ratio * (_bg._world_height / 100.0)
	# Round a planet, lowered by the drop at the middle of the layer's box from the emitter's place.
	var drop := _bg.planet_drop_at(l, (anchor.x + decal_x + l.width / 2) * ppu)
	node.position = Vector2((anchor.x + l.travel_x) * ppu, screen_h - (anchor.y + l.travel_y) * ppu + drop)
	if node.has_meta("plax_offset"):
		# In the node's own pixels, which its scale makes ppu on screen: those of zoom 1.
		var authored: Vector3 = node.get_meta("plax_offset")
		node.process_material.emission_shape_offset = authored + Vector3(
				-(l.travel_x - decal_x) * ppu * z, (l.travel_y - decal_y) * ppu * z, 0)
	else:
		# A custom process material cannot be moved back: the emitter stays at the decal, and nothing drifts.
		node.position = Vector2((anchor.x + decal_x) * ppu, screen_h - (anchor.y + decal_y) * ppu + drop)


## LAYER: an instance at the bottom-left corner of each tile the view shows, as PlaxBackground._tile places them. On a
## repeated axis a tile counts as shown while its box and a GPUParticles2D's visibility_rect reach into the view; a
## CPUParticles2D has none, so its box grown by its own size each way. On an axis not repeated there is one tile,
## always kept. Round a planet each is lowered by the drop at its box's middle, and a tile counts as shown down to the
## most a tile reaching into the view can be lowered.
func _place_tile_particles(l: Dictionary) -> void:
	var z := _bg._zoom_of(l)
	var ppu := _bg._ppu
	var anchor := _bg._anchor_of(z)
	var width: float = l.width
	var height: float = l.height
	var reach := Rect2(-width, -height, 3 * width, 3 * height)
	var sample: Node = l.pool[0] if not l.pool.is_empty() else l.instances.values()[0]
	if sample is GPUParticles2D:
		reach = Rect2(0, 0, width, height)
		var r: Rect2 = sample.visibility_rect
		# The node's pixels, y down from the corner, into page units, y up: zoom 1's, the node scaled by 1 / z.
		var unit := ppu * z
		reach = reach.merge(Rect2(r.position.x / unit, -r.end.y / unit, r.size.x / unit, r.size.y / unit))
	var view_w := _bg.world_width * z
	var view_h := _bg._world_height * z
	var lowest := _bg.planet_drop_at(l, -maxf(reach.end.x - width / 2, width / 2 - reach.position.x) * ppu) / ppu
	reach = reach.grow_individual(0, lowest, 0, 0)
	var repeat_x := _bg._repeat_x
	var repeat_y := _bg._repeat_y
	var step_x: float = width + l.model.padX
	var step_y: float = height + l.model.padY
	var range_x := _tile_range(anchor.x + l.travel_x, reach.position.x, reach.end.x, step_x, repeat_x, view_w)
	var range_y := _tile_range(anchor.y + l.travel_y, reach.position.y, reach.end.y, step_y, repeat_y, view_h)
	var shown := {}
	for ky in range(range_y.x, range_y.y + 1):
		for kx in range(range_x.x, range_x.y + 1):
			var key := Vector2i(kx, ky)
			shown[key] = true
			var node: Node2D = _instance(l, key)
			var x: float = anchor.x + l.travel_x + (kx * step_x if repeat_x and step_x > 0 else 0.0)
			var y: float = anchor.y + l.travel_y + (ky * step_y if repeat_y and step_y > 0 else 0.0)
			node.position = Vector2(x * ppu, _bg._screen_h - y * ppu + _bg.planet_drop_at(l, (x + width / 2) * ppu))
			node.scale = node.get_meta("plax_scale") / z
	for key in l.instances.keys():
		if not shown.has(key):
			var node: Node = l.instances[key]
			l.instances.erase(key)
			l.canvas.remove_child(node)
			l.pool.append(node)


## The first and last tile index whose reach (from..to, from its corner) meets 0..view, tiles at origin + k * step;
## (0, 0) when the axis does not repeat.
static func _tile_range(origin: float, from: float, to: float, step: float, repeat: bool, view: float) -> Vector2i:
	if not repeat or step <= 0:
		return Vector2i(0, 0)
	return Vector2i(floori((-to - origin) / step) + 1, ceili((view - from - origin) / step) - 1)


## The layer's instance for `key`, from its pool or the scene, in the layer's place.
func _instance(l: Dictionary, key: Vector2i) -> Node2D:
	if l.instances.has(key):
		return l.instances[key]
	var node: Node2D
	if not l.pool.is_empty():
		node = l.pool.pop_back()
		node.restart()
	else:
		node = prepare(l.scene.instantiate(), l.model)
	l.instances[key] = node
	l.canvas.add_child(node)
	return node


## A new instance of the layer's scene, made to be carried by its node; for the VIEW, made so its emitter can move back.
static func prepare(node: Node2D, model: Dictionary) -> Node2D:
	if model.particlesAnchor == "VIEW":
		if node is CPUParticles2D:
			# Only a process material's emission moves back as the node drifts.
			var gpu := GPUParticles2D.new()
			gpu.convert_from_particles(node)
			gpu.name = node.name
			node.free()
			node = gpu
		if node is GPUParticles2D and node.process_material is ParticleProcessMaterial:
			# Its own, its authored offset kept: the drift is added to it.
			node.process_material = node.process_material.duplicate()
			node.set_meta("plax_offset", node.process_material.emission_shape_offset)
	node.local_coords = true
	# Its authored scale, which a zoom divides (place).
	node.set_meta("plax_scale", node.scale)
	return node
