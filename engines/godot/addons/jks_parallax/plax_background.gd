class_name PlaxBackground
extends CanvasLayer
## A parallax page from the JKS editor, drawn behind the game: the Godot port of the libGDX runtime's
## Parallax_Heart + ParallaxPageReader + ParallaxLayer (core/src/jks/tools2d/parallax). Same numbers, same picture:
## engines/godot/tools/frame-check.sh compares its frames with the libGDX ones.
##
##   var bg := PlaxBackground.new()
##   bg.load_page("res://backgrounds/calm.jplax")   # its .atlas and .png next to it
##   bg.speed_constant_x = 60                        # optional auto-scroll, in the page's units per second
##   add_child(bg)
##
## Screen-anchored like the libGDX one: it sits on its own canvas layer (layer -100 by default), so a Camera2D does
## not drag it. The world is `world_width` units wide (40, the libGDX heart's default) and as high as the screen's
## aspect ratio makes it; a layer's decal is in percent of that world.
##
## Cross-fading into another page and tinting, as Parallax_Heart.transfertIntoPage and
## ParallaxPageReader.addColorTransfert:
##
##   bg.transfert_into(PlaxPage.load_page(path), PlaxAtlas.load_atlas(atlas_path), 2.0)
##   bg.transfert_into(page, atlas, 3.0, PlaxTransfertStyle.depth_stagger(0.5))   # back layers first
##   bg.tint_to(Color(1, 0.6, 0.4), 2.0)
##
## An EMPTY layer (kind "EMPTY") is drawn by the game, as ParallaxPageReader.setLayerHook:
##
##   bg.set_layer_hook("birds", func(canvas: CanvasItem, rect: Rect2, modulate: Color, l: Dictionary):
##       canvas.draw_texture_rect(bird, rect, false, modulate))
##
## called in the layer's place in the draw order, once per tile of it the view shows, `rect` in screen pixels and
## `modulate` the page's tint at the layer's opacity. A layer with no hook draws nothing. Round a planet the hook is
## not bent: it draws what stands at a point where planet_place_at(l, point) puts it, turned by planet_angle_at(l, x)
## (r347):
##
##   canvas.draw_set_transform(bg.planet_place_at(l, foot), bg.planet_angle_at(l, foot.x))
##   canvas.draw_texture_rect(tower, Rect2(-w / 2, -h, w, h), false, modulate)
##
## A PARTICLES layer instances the Godot scene its page names, between the layers before and after it: PlaxParticles.
##
## A SHADER layer is drawn as an image layer, through its effect (PlaxEffects, the shaders of core's GdxLayerEffects),
## which moves on act()'s clock. The page's depth fog (PlaxPage.fog_strength, fog_color) mixes each IMAGE, SEQUENCE and
## SHADER layer toward the fog's colour, more the slower it scrolls (PlaxEffects.fog_of), through the same shaders, each
## page of a cross-fade by its own fog; an EMPTY or PARTICLES layer is drawn as is.
##
## A SEQUENCE layer chains its segments (atlas regions) in a cycle drawn once, when the page is set (PlaxSequence), by
## PlaxPage.draw_cycle from the page's seed: the same picks as core's SequenceCycle. The cycle is the layer's tile. A
## game draws a new ground each run with its own seed, XORed with each layer's stored one, as
## ParallaxPageReader.setSequenceSeed:
##
##   bg.set_sequence_seed(randi())
##
## A LURKER layer's figure appears or vanishes only while nothing of it is on screen (PlaxLurker): gone at the sighting
## after one where it was seen, there by chance from its seed, or when the game says:
##
##   bg.reveal_lurker("hooded")
##
## An IMAGE layer with a tower radius is wrapped round a cylinder in the view's middle, its scroll turning it
## (PlaxTower, r276).
##
## A page with a planet (PlaxPage.planet_radius, r277, r347) is the side of a world turning round one centre under the
## view: its IMAGE, SHADER and SEQUENCE layers are wrapped round it alike, each turning at its own speed, and what
## stands up leans out from it (PlaxPlanet).
##
## Each layer is drawn by its own child of the gradients' canvas, in draw order, so that a particle node can sit between
## two of them. Not ported yet: atlas regions packed rotated.

@export_file("*.jplax", "*.plaxpj") var page_path := ""

## Folder the page's atlas is in; empty: the page's own folder.
@export_dir var atlas_dir := ""
@export var world_width := 40.0
## Scroll speed, as Parallax_Heart.screenSpeedConstantX/Y.
@export var speed_constant_x := 0.0
@export var speed_constant_y := 0.0
## False: nothing moves unless act() is called (to step it yourself, at a fixed rate).
@export var auto_act := true
## The camera's zoom, as libGDX's OrthographicCamera.zoom: below 1 zooms in, above 1 out. Without the dolly the page
## zooms as one picture, about the view's bottom left corner, where its layers are placed from (ParallaxPageReader).
@export var zoom := 1.0:
	set(value):
		zoom = value
		_rescale()
## ParallaxPageReader.setDolly (r278): the zoom scales each layer by its depth (PlaxEffects.dolly_zoom), the near ones
## more, a still one not at all, about the view's middle.
@export var dolly := false:
	set(value):
		dolly = value
		_rescale()
## Scroll speed for the next frame only, then reset (e.g. from the player's movement).
var speed_consumable_x := 0.0
var speed_consumable_y := 0.0

## The page on screen; during a cross-fade, the one fading out. It becomes the incoming page when the fade ends.
var page: PlaxPage
var atlas: PlaxAtlas
## One per page layer, back to front: the ParallaxLayer state.
var layers: Array[Dictionary] = []
## The page fading in, and its layers; null and empty outside a cross-fade.
var transfer_page: PlaxPage
var transfer_atlas: PlaxAtlas
var transfer_layers: Array[Dictionary] = []
## Multiplied into every layer (not the gradients); tint_to fades it.
var tint := Color.WHITE

# As ParallaxPageReader: the repeat of the page set with set_page, kept by the pages it fades into.
var _repeat_x := true
var _repeat_y := false
# Opacity of the incoming page (0 -> 1) and of the outgoing one (1 -> 0), and how much each moves per second.
var _new_alpha := 0.0
var _old_alpha := 1.0
var _fade_speed := 0.0
# How the cross-fade under way is drawn (ParallaxPageReader.transfertStyle): null outside one, and for a plain fade.
var _style: PlaxTransfertStyle = null
var _tint_from := Color.WHITE
var _tint_to := Color.WHITE
var _tint_duration := 0.0
var _tint_elapsed := 0.0
# The two gradients (SquareBackground): their colors top_top, top_bottom, bottom_top, bottom_bottom and the part of
# the screen each leaves uncovered, set by set_page. A cross-fade fades the colors; the sizes stay.
var _gradient: Array[Color] = [Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE]
var _gradient_from: Array[Color] = []
var _gradient_to: Array[Color] = []
var _gradient_duration := 0.0
var _gradient_elapsed := 0.0
var _gradient_fading := false
# How the gradients fade (TransfertStyle.gradient): null straight, as a plain fade.
var _gradient_style: PlaxTransfertStyle = null
var _top_half_size := 0.5
var _bottom_half_size := 0.5

## Draws the gradients; its children draw the layers, one each, in draw order.
var _canvas := Node2D.new()
# The layer canvas being drawn: _tile and _draw_region draw on it.
var _target: CanvasItem
## What _tile draws of a tile: 0 the image and its patch, 1 the image only, 2 the patch only
## (ParallaxLayer.setDrawPart).
var _part := 0
var _world_height := 22.5
# Set by _draw_page each frame: pixels per page unit, the screen height, the color the next layer is drawn with.
var _ppu := 1.0
var _screen_h := 0.0
var _modulate := Color.WHITE
# Set by _draw_layer: the radius of the planet the layer drawn is wrapped round, in its view (PlaxPlanet.radius_of; 0
# flat), how far over its view's top a tile still shows round it (PlaxPlanet.reach_of), and the width of its view.
var _planet := 0.0
var _reach := 0.0
var _view_w := 0.0
# Set by _draw_layer: the panorama the layer drawn is wrapped in round its page's near tower (PlaxPanorama: rho, q,
# the strips' edges and scales), empty when flat; and the eye it is scaled about.
var _panorama: Array = []
var _eye_y := 0.0
# What draws each EMPTY layer, by layer name.
var _hooks := {}
# Seconds acted: the SHADER layers' clock, shared by both pages of a cross-fade (ParallaxPageReader.effectTime).
var _effect_time := 0.0
# The game's seed for the SEQUENCE layers, when _has_sequence_seed (set_sequence_seed).
var _sequence_seed := 0
var _has_sequence_seed := false
# Places the PARTICLES layers' nodes.
var _particles: PlaxParticles
# The camera zoom of the last draw of a LURKER layer, which act()'s sightings run against; NAN before it (PlaxLurker).
var _lurker_zoom := NAN


func _init() -> void:
	layer = -100
	_particles = PlaxParticles.new(self)
	_canvas.draw.connect(_draw_page)
	add_child(_canvas)


func _notification(what: int) -> void:
	if what == NOTIFICATION_PREDELETE:
		# The pooled particle nodes are out of the tree: freed with their layer.
		_drop(layers)
		_drop(transfer_layers)


func _ready() -> void:
	get_viewport().size_changed.connect(_on_resize)
	if page == null and not page_path.is_empty():
		load_page(page_path, atlas_dir)
	_on_resize()


## Reads a .jplax or .plaxpj, and its atlas from `from_dir` (the page's folder when empty).
func load_page(path: String, from_dir := "") -> void:
	var p := PlaxPage.load_page(path)
	if p == null:
		return
	var dir := from_dir if not from_dir.is_empty() else path.get_base_dir()
	set_page(p, PlaxAtlas.load_atlas(dir.path_join(p.atlas_name)))


func set_page(new_page: PlaxPage, new_atlas: PlaxAtlas) -> void:
	page = new_page
	atlas = new_atlas
	_drop(layers)
	_drop(transfer_layers)
	_reset_transfert()
	_update_world()
	layers = _build_layers(page, atlas)
	_repeat_x = page.repeat_on_x
	_repeat_y = page.repeat_on_y
	_gradient = [page.top_half_top, page.top_half_bottom, page.bottom_half_top, page.bottom_half_bottom]
	_gradient_fading = false
	_top_half_size = page.top_half_size
	_bottom_half_size = page.bottom_half_size
	_update_filter()
	reset_positions()
	_order_canvases()
	_redraw()


## Cross-fades into `new_page` over `seconds` (0: at once), as Parallax_Heart.transfertIntoPage. The pages are matched
## from their front layer: an incoming layer takes the distance its counterpart has scrolled, so nothing jumps. The
## gradients' colors fade too; the repeat and the gradients' sizes stay those of the page given to set_page. A fade
## started during another drops the page that was fading in. `style` (null: every layer slot at once) is how the layers
## fade, as Parallax_Heart.transfertIntoPage(page, seconds, style): PlaxTransfertStyle.depth_stagger(s) fades each slot
## in its own window, the back ones first; PlaxTransfertStyle.dissolve(patches, softness, s) has each slot's incoming
## layer eat the outgoing one in patches; PlaxTransfertStyle.through_color(color, s) goes through a colour, white
## possible, the gradients too; PlaxTransfertStyle.fog_creep(mist, s) sinks the page into a mist and brings the new one
## out of it.
func transfert_into(new_page: PlaxPage, new_atlas: PlaxAtlas, seconds: float, style: PlaxTransfertStyle = null) -> void:
	if page == null:
		set_page(new_page, new_atlas)
		return
	_drop(transfer_layers)
	_unmask(layers)
	_reset_transfert()
	var incoming := _build_layers(new_page, new_atlas)
	if not incoming.is_empty():
		transfer_page = new_page
		transfer_atlas = new_atlas
		transfer_layers = incoming
		for l in incoming:
			l.incoming = true
		_sync_transfer_positions()
		if seconds <= 0:
			_finish_transfert()
		else:
			_fade_speed = 1.0 / seconds
			_style = style
	_fade_gradients(
		[new_page.top_half_top, new_page.top_half_bottom, new_page.bottom_half_top, new_page.bottom_half_bottom],
		seconds, style)
	_update_filter()
	_order_canvases()
	_redraw()


## Fades `tint` toward `color` over `seconds` (0: at once), as ParallaxPageReader.addColorTransfert.
func tint_to(color: Color, seconds: float) -> void:
	_tint_from = tint
	_tint_to = color
	_tint_elapsed = 0
	_tint_duration = seconds
	if seconds <= 0:
		tint = color
	_particles.place()
	_redraw()


## Makes `hook` draw every EMPTY layer named `layer_name`; an empty Callable removes it. See the class doc.
func set_layer_hook(layer_name: String, hook: Callable) -> void:
	if hook.is_valid():
		_hooks[layer_name] = hook
	else:
		_hooks.erase(layer_name)
	_redraw()


## ParallaxPageReader.reveal: the next sighting of every LURKER layer named `layer_name` shows its figure, at once when
## it is not in sight now, else from the end of this sighting: a game's sure first scare (PlaxLurker).
func reveal_lurker(layer_name: String) -> void:
	PlaxLurker.reveal(layers, layer_name)
	PlaxLurker.reveal(transfer_layers, layer_name)
	_redraw()


## ParallaxPageReader.planetPlaceAt (r347): where the point `at` of layer `l` is drawn round its page's planet, both
## in the screen pixels a hook draws in: the view is the side of a world turning round one centre under the middle of
## its bottom, x along the ground's arc and y along the radius. For a hook to place what it draws, turned by
## planet_angle_at; `at` for no planet. Called from a hook, the view is the layer's own (dolly).
func planet_place_at(l: Dictionary, at: Vector2) -> Vector2:
	var radius := _planet_of(l)
	if not radius > 0:
		return at
	var to := PlaxPlanet.place(radius, _view_w, at.x / _ppu, (_screen_h - at.y) / _ppu)
	return Vector2(to.x * _ppu, _screen_h - to.y * _ppu)


## ParallaxPageReader.planetAngleAt: how far what stands at `x` (screen pixels) in layer `l` leans round its page's
## planet, in radians clockwise, as Node2D.rotation and draw_set_transform take it: out along the radius. 0 for no
## planet.
func planet_angle_at(l: Dictionary, x: float) -> float:
	return PlaxPlanet.angle_at(_planet_of(l), _view_w, x / _ppu)


## How far the ground under `x` (screen pixels) is lowered round layer `l`'s planet, in pixels: what PlaxParticles
## culls by. Deprecated for a hook (r347: round a planet its drawing leans too; use planet_place_at and
## planet_angle_at).
func planet_drop_at(l: Dictionary, x: float) -> float:
	return planet_place_at(l, Vector2(x, _screen_h)).y - _screen_h


## The radius of the planet layer `l`'s page runs round, in its view (PlaxPlanet.radius_of); 0 for none.
func _planet_of(l: Dictionary) -> float:
	var from_page := transfer_page if l.incoming else page
	return PlaxPlanet.radius_of(from_page.planet_radius, world_width, _zoom_of(l), zoom)


## Draws every SEQUENCE layer's cycle, on screen and fading in, from `seed` XOR the seed its page stores rather than
## from the stored one alone: a new ground each run (ParallaxPageReader.setSequenceSeed). `seed` is Java's int: 32 bits.
func set_sequence_seed(seed: int) -> void:
	_sequence_seed = seed
	_has_sequence_seed = true
	_draw_cycles()


## Back to the seeds the pages store: what the editor previews.
func clear_sequence_seed() -> void:
	_has_sequence_seed = false
	_draw_cycles()


func _draw_cycles() -> void:
	for l in layers + transfer_layers:
		_draw_cycle_of(l)
	_redraw()


## Draws a SEQUENCE layer's cycle from the seed this background gives it, when it was drawn from another.
func _draw_cycle_of(l: Dictionary) -> void:
	if l.model.kind != "SEQUENCE":
		return
	var seed: int = (_sequence_seed ^ l.model.sequenceSeed if _has_sequence_seed else l.model.sequenceSeed) & 0xFFFFFFFF
	if l.has("drawn_seed") and l.drawn_seed == seed:
		return
	l.drawn_seed = seed
	PlaxSequence.lay_out(l, seed)
	_size_layer(l)


func is_in_transfer() -> bool:
	return not transfer_layers.is_empty()


func _build_layers(from_page: PlaxPage, from_atlas: PlaxAtlas) -> Array[Dictionary]:
	var built: Array[Dictionary] = []
	for i in from_page.layers.size():
		var model: Dictionary = from_page.layers[i]
		if model.kind == "SEQUENCE":
			var sequence := PlaxSequence.build(self, model, from_page, from_atlas)
			if sequence.is_empty():
				continue
			_draw_cycle_of(sequence)
			_reset_position(sequence)
			_add_canvas(sequence)
			built.append(sequence)
			continue
		if not model.kind in ["IMAGE", "SHADER", "FLOOR", "LURKER"]:
			var e := {"model": model, "region": {}, "distance_x": 0.0, "distance_y": 0.0}
			if model.kind == "PARTICLES":
				e.scene = _particles.scene_of(model, from_page, from_atlas, i)
				e.instances = {}
				# One made now: what the scene is (its visibility_rect) is known before the first tile is placed.
				e.pool = [PlaxParticles.prepare(e.scene.instantiate(), model)] if e.scene else []
			_size_layer(e)
			_reset_position(e)
			_add_canvas(e)
			built.append(e)
			continue
		var region := from_atlas.find_region(model.regionName, model.regionPosition) if from_atlas else {}
		if region.is_empty():
			push_error("PlaxBackground: region '%s' #%d not found in %s"
					% [model.regionName, model.regionPosition, from_page.atlas_name])
			continue
		if region.rotate:
			push_warning("PlaxBackground: region '%s' is packed rotated, which is not supported: drawn as is" % model.regionName)
		var l := _build_layer(model, region, from_page.use_original_size)
		if not PlaxAnim.build(l, model, from_atlas, from_page.use_original_size):
			continue
		if model.kind == "FLOOR":
			PlaxFloor.measure(l)
		_reset_position(l)
		_add_canvas(l)
		built.append(l)
	# The depth fog of each layer, by its speed against the page's front: what ParallaxPageReader.drawImageLayer reckons.
	var front := PlaxEffects.front_speed_of(built)
	for l in built:
		l.haze = 0.0 if l.model.kind == "EMPTY" or l.model.kind == "PARTICLES" \
				else PlaxEffects.fog_of(from_page.fog_strength, front, l.model)
		l.fog_color = Vector3(from_page.fog_color.r, from_page.fog_color.g, from_page.fog_color.b)
		if l.haze > 0 and l.model.kind in ["IMAGE", "SEQUENCE", "FLOOR", "LURKER"]:
			l.canvas.material = PlaxEffects.material("PLAIN")
	return built


## A region's box: where its packed image sits in it, the image's size, and its width in heights. useOriginalSize: a
## region packed with its whitespace stripped keeps its original size, and its packed image is drawn at its offset
## inside it. Off: the packed image is stretched over the whole box.
static func _measure_region(region: Dictionary, use_original_size: bool) -> Dictionary:
	var m := {"region": region, "trim_left": 0.0, "trim_bottom": 0.0, "packed_w": 1.0, "packed_h": 1.0}
	var image_w: float = region.width
	var image_h: float = region.height
	if use_original_size and region.original_width > 0 and region.original_height > 0:
		m.trim_left = region.offset_x / region.original_width
		m.trim_bottom = region.offset_y / region.original_height
		m.packed_w = region.width / region.original_width
		m.packed_h = region.height / region.original_height
		image_w = region.original_width
		image_h = region.original_height
	m.image_w = image_w
	m.image_h = image_h
	m.aspect = image_w / image_h
	return m


## What draws the layer: a canvas calling _draw_layer, or for a PARTICLES layer the node holding its instances.
func _add_canvas(l: Dictionary) -> void:
	l.incoming = false
	l.slot = 0
	l.canvas = Node2D.new()
	if l.model.kind == "SHADER":
		l.canvas.material = PlaxEffects.material(l.model.shaderEffect)
		if not l.anim.is_empty():
			# Its patches after its tiles, on a canvas of their own: their effect is given the frame's region, which
			# one canvas's material cannot change between draws (ParallaxPageReader.drawImageLayer, r397).
			l.anim_canvas = Node2D.new()
			l.anim_canvas.material = PlaxEffects.material(l.model.shaderEffect)
			l.anim_canvas.draw.connect(_draw_layer.bind(l, true))
			l.canvas.add_child(l.anim_canvas)
	if l.model.kind != "PARTICLES":
		l.canvas.draw.connect(_draw_layer.bind(l))


## The layers' canvases under the gradients' one, in draw order: during a cross-fade, slot by slot from the front, the
## outgoing layer then the incoming one, as _draw_page did.
func _order_canvases() -> void:
	var order: Array[Dictionary] = []
	var total := maxi(layers.size(), transfer_layers.size())
	var old_offset := total - layers.size()
	var new_offset := total - transfer_layers.size()
	for slot in total:
		# Its slot, for a depth stagger (_alpha_of).
		if slot >= old_offset:
			layers[slot - old_offset].slot = slot
			order.append(layers[slot - old_offset])
		if slot >= new_offset:
			transfer_layers[slot - new_offset].slot = slot
			order.append(transfer_layers[slot - new_offset])
	for i in order.size():
		var canvas: Node2D = order[i].canvas
		if canvas.get_parent() == null:
			_canvas.add_child(canvas)
		_canvas.move_child(canvas, i)
	_particles.place()


## Frees the layers' canvases, and their pooled particle nodes, which are out of the tree.
func _drop(dropped: Array[Dictionary]) -> void:
	for l in dropped:
		if not l.has("canvas") or not is_instance_valid(l.canvas):
			continue
		for node in l.get("pool", []):
			node.free()
		if l.canvas.get_parent() != null:
			l.canvas.get_parent().remove_child(l.canvas)
		l.canvas.queue_free()


func _redraw() -> void:
	_canvas.queue_redraw()
	for l in layers + transfer_layers:
		l.canvas.queue_redraw()
		if l.has("anim_canvas"):
			l.anim_canvas.queue_redraw()


## A new zoom or dolly: the particles placed again, the layers drawn again.
func _rescale() -> void:
	if _particles != null and page != null:
		_particles.place()
	_redraw()


## ParallaxPageReader.syncTransferPositions: matched from the front, the incoming layer keeps its own decal and takes
## the distance its counterpart has scrolled from its decal.
func _sync_transfer_positions() -> void:
	var total := maxi(layers.size(), transfer_layers.size())
	var old_offset := total - layers.size()
	var new_offset := total - transfer_layers.size()
	for slot in range(maxi(old_offset, new_offset), total):
		var from: Dictionary = layers[slot - old_offset]
		var to: Dictionary = transfer_layers[slot - new_offset]
		to.distance_x = (to.model.decal_X_Ratio * (world_width / 100.0) + from.distance_x
				- from.model.decal_X_Ratio * (world_width / 100.0))
		to.distance_y = (to.model.decal_Y_Ratio * (_world_height / 100.0) + from.distance_y
				- from.model.decal_Y_Ratio * (_world_height / 100.0))
		to.travel_x = to.distance_x
		to.travel_y = to.distance_y
		# Its own noise, from its own tile: what the outgoing layer wrapped is not its.
		to.wrapped_x = 0.0
		to.wrapped_y = 0.0


func _finish_transfert() -> void:
	page = transfer_page
	atlas = transfer_atlas
	_drop(layers)
	_unmask(transfer_layers)
	layers = transfer_layers
	for l in layers:
		l.incoming = false
	_reset_transfert()
	_update_filter()
	_order_canvases()


func _reset_transfert() -> void:
	transfer_page = null
	transfer_atlas = null
	transfer_layers = []
	_style = null
	_new_alpha = 0
	_old_alpha = 1


## SquareBackground.transfertInto: both colors of both gradients, alpha included, toward the incoming page's, as
## `style` goes (null: straight).
func _fade_gradients(target: Array[Color], seconds: float, style: PlaxTransfertStyle = null) -> void:
	_gradient_style = style
	_gradient_from = _gradient.duplicate()
	_gradient_to = target
	_gradient_elapsed = 0
	_gradient_duration = seconds
	_gradient_fading = true
	if seconds <= 0:
		_act_gradients(0)


func _act_gradients(delta: float) -> void:
	if not _gradient_fading:
		return
	_gradient_elapsed += delta
	var progress := minf(1, _gradient_elapsed / _gradient_duration) if _gradient_duration > 0 else 1.0
	for i in 4:
		_gradient[i] = (_gradient_style.gradient(_gradient_from[i], _gradient_to[i], progress) if _gradient_style
				else _gradient_from[i].lerp(_gradient_to[i], progress))
	if progress >= 1:
		_gradient_fading = false


## Mipmapped sampling when a page on screen was packed with mipmaps. A Nearest page is not concerned: its texture is a
## CanvasTexture with its own filter (PlaxAtlas._load_texture), so it stays sharp mid-fade with a smooth page.
func _update_filter() -> void:
	var mipmaps := false
	for l in layers + transfer_layers:
		if l.region.is_empty():
			continue
		mipmaps = mipmaps or l.region.texture.get_meta("plax_mipmaps", false)
	_canvas.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS if mipmaps else CanvasItem.TEXTURE_FILTER_LINEAR


## Puts every layer back at its decal.
func reset_positions() -> void:
	for l in layers:
		_reset_position(l)


func _reset_position(l: Dictionary) -> void:
	l.distance_x = l.model.decal_X_Ratio * (world_width / 100.0)
	l.distance_y = l.model.decal_Y_Ratio * (_world_height / 100.0)
	# The same, never wrapped: which tile a PARTICLES instance belongs to, and how far a VIEW one's particles drifted.
	l.travel_x = l.distance_x
	l.travel_y = l.distance_y
	l.wrapped_x = 0.0
	l.wrapped_y = 0.0
	if l.has("row_distance"):
		l.row_distance.fill(l.distance_x)


func _process(delta: float) -> void:
	if auto_act:
		act(delta)


## Scrolls the layers and moves the fades by one step of `delta` seconds (Parallax_Heart.act).
func act(delta: float) -> void:
	var speed_x := speed_constant_x + speed_consumable_x
	var speed_y := speed_constant_y + speed_consumable_y
	speed_consumable_x = 0
	speed_consumable_y = 0
	if page == null:
		return
	_effect_time += delta
	_act_gradients(delta)
	PlaxFloor.plant(layers, _world_height)
	PlaxFloor.plant(transfer_layers, _world_height)
	for l in layers:
		_act_layer(l, delta, speed_x, speed_y)
	if not transfer_layers.is_empty():
		for l in transfer_layers:
			_act_layer(l, delta, speed_x, speed_y)
		_new_alpha = minf(1, _new_alpha + delta * _fade_speed)
		_old_alpha = maxf(0, _old_alpha - delta * _fade_speed)
		if _new_alpha >= 1:
			_finish_transfert()
	if _tint_elapsed < _tint_duration:
		_tint_elapsed = minf(_tint_duration, _tint_elapsed + delta)
		tint = _tint_from.lerp(_tint_to, _tint_elapsed / _tint_duration)
	# Every step, drawn or not: a sighting between two draws counts. Against the last draw's view.
	PlaxLurker.sight(self, _lurker_zoom)
	_particles.place()
	_redraw()


## ParallaxLayer.act.
func _act_layer(l: Dictionary, delta: float, speed_x: float, speed_y: float) -> void:
	var m: Dictionary = l.model
	var move_y: float = -delta * speed_y * m.parallaxScalingSpeedY
	var planted: float = l.get("planted", NAN)
	var move_x: float = -delta * (m.speedXAtRest + speed_x) * (m.parallaxScalingSpeedX if is_nan(planted) else planted)
	l.distance_y += move_y
	l.distance_x += move_x
	l.travel_y += move_y
	l.travel_x += move_x
	# Kept within one tile so the tiling loops stay short and floats stay precise.
	var total_w: float = l.width + m.padX
	if _repeat_x and total_w > 0:
		var before: float = l.distance_x
		l.distance_x = fmod(l.distance_x, total_w)
		# What was wrapped, within the effect's period: FOG's noise runs on across the tiles (ParallaxLayer.wrappedX).
		var period := PlaxEffects.period(m)
		l.wrapped_x = fmod(l.wrapped_x + before - l.distance_x, period) if period > 0 else 0.0
	var total_h: float = l.height + m.padY
	if _repeat_y and total_h > 0:
		var below: float = l.distance_y
		l.distance_y = fmod(l.distance_y, total_h)
		# The same on Y, within its period up (ParallaxLayer.wrappedY, r262).
		var period_y := PlaxEffects.period_y(m)
		l.wrapped_y = fmod(l.wrapped_y + below - l.distance_y, period_y) if period_y > 0 else 0.0
	if l.has("row_distance"):
		PlaxFloor.scroll(l, delta * (m.speedXAtRest + speed_x))


func _build_layer(model: Dictionary, region: Dictionary, use_original_size: bool) -> Dictionary:
	var l := _measure_region(region, use_original_size)
	l.model = model
	l.distance_x = 0.0
	l.distance_y = 0.0
	l.wrapped_x = 0.0
	l.wrapped_y = 0.0
	_size_layer(l)
	return l


## A layer is sizeRatio worlds wide; its height follows the image. An EMPTY or PARTICLES one is sizeRatio worlds high.
## A SEQUENCE one is as high as its first segment makes it, and as wide as its cycle, without the padX after the last
## slot (ParallaxLayer.getWidth).
func _size_layer(l: Dictionary) -> void:
	l.width = world_width * l.model.sizeRatio
	if l.model.kind == "EMPTY" or l.model.kind == "PARTICLES":
		l.height = _world_height * l.model.sizeRatio
		return
	l.height = l.image_h * (world_width / l.image_w) * l.model.sizeRatio
	if l.model.kind == "FLOOR":
		# Its box (ParallaxLayer.getHeight): its own height, or the tile's; mode 7's depths follow it.
		l.floor_h = l.model.floorHeight * (_world_height / 100.0) if l.model.floorHeight > 0 else l.height
		if l.has("row_strips"):
			PlaxFloor.measure(l)
	if l.model.kind == "SEQUENCE" and l.has("edges"):
		l.width = l.height * l.edges[l.cycle.size()] + (l.cycle.size() - 1) * l.model.padX


func _update_world() -> void:
	var screen := _screen_size()
	_world_height = world_width * screen.y / screen.x


## The world keeps its width and follows the screen's aspect ratio, as Parallax_Heart.resize does; nothing moves.
func _on_resize() -> void:
	_update_world()
	for l in layers + transfer_layers:
		_size_layer(l)
	_particles.place()
	_redraw()


func _screen_size() -> Vector2:
	return get_viewport().get_visible_rect().size if is_inside_tree() else Vector2(1280, 720)


# --- drawing (ParallaxPageReader.draw), in the page's units, y up, converted to pixels at the last moment ----------

## The gradients; the layers are drawn by the canvases under it (_order_canvases).
func _draw_page() -> void:
	if page == null:
		return
	_draw_gradients(_screen_size())


## Pixels per page unit and the screen height, as the screen is now, for a layer drawn at `z` (_zoom_of).
func _update_scale(z := 1.0) -> void:
	var screen := _screen_size()
	_ppu = screen.x / (world_width * z)
	_screen_h = screen.y


## The zoom `l` is drawn at: the camera's (zoom), or dollied, by its depth against its page's front
## (ParallaxPageReader.drawLayer).
func _zoom_of(l: Dictionary) -> float:
	if not dolly:
		return zoom
	return PlaxEffects.dolly_zoom(zoom, PlaxEffects.front_speed_of(transfer_layers if l.incoming else layers), l.model)


## Where the layers are placed from in the view at zoom `z`, from its bottom left corner: the corner itself, or dollied,
## the corner of the view at zoom 1, so that a zoom scales them about the middle (ParallaxPageReader.view).
func _anchor_of(z: float) -> Vector2:
	if not dolly:
		return Vector2.ZERO
	return Vector2(world_width * (z - 1), _world_height * (z - 1)) / 2


## The opacity of the page the layer belongs to: during a cross-fade, the incoming page's or the outgoing one's; with a
## depth stagger, its slot's, each slot at its own point of the fade (ParallaxPageReader.draw). In a dissolve a layer
## the mask draws (_masked) is at full opacity while its share of the slot is above 0; an EMPTY or PARTICLES one fades
## to its share (ParallaxPageReader.drawDissolved). Through a colour or in a fog creep only one side of a slot shows,
## the outgoing layer until the slot is all colour, the incoming one after (PlaxTransfertStyle.shows_incoming): at full
## opacity when graded (_graded), else faded out by as much as the grade (ParallaxPageReader.drawGraded).
func _alpha_of(l: Dictionary) -> float:
	if transfer_layers.is_empty():
		return 1.0
	if _style == null or _style.kind == "FADE":
		return _new_alpha if l.incoming else _old_alpha
	if _style.grades():
		var total := maxi(layers.size(), transfer_layers.size())
		if _style.shows_incoming(_new_alpha, l.slot, total) != l.incoming:
			return 0.0
		return 1.0 if _graded(l) else 1.0 - _style.grade_at(_new_alpha, l.slot, total)
	var share := _share_of(l)
	return (1.0 if share > 0 else 0.0) if _masked(l) else share


## The layer's share of its slot, 0 to 1: its slot's ramp, the outgoing layer's 1 minus it.
func _share_of(l: Dictionary) -> float:
	var ramp := _style.slot_ramp(_new_alpha, l.slot, maxi(layers.size(), transfer_layers.size()))
	return ramp if l.incoming else 1.0 - ramp


## Whether a dissolve draws the layer through its mask now: an IMAGE, SEQUENCE, SHADER or FLOOR one, during a dissolve.
func _masked(l: Dictionary) -> bool:
	return (not transfer_layers.is_empty() and _style != null and _style.kind == "DISSOLVE"
			and l.model.kind in ["IMAGE", "SEQUENCE", "SHADER", "FLOOR", "LURKER"])


## Whether a transfert through a colour or a fog creep mixes the layer toward it now: an IMAGE, SEQUENCE, SHADER or
## FLOOR one, during one.
func _graded(l: Dictionary) -> bool:
	return (not transfer_layers.is_empty() and _style != null and _style.grades()
			and l.model.kind in ["IMAGE", "SEQUENCE", "SHADER", "FLOOR", "LURKER"])


## Takes the dissolve's mask and the colour's grade off `page`'s layers: the PLAIN material either gave a layer that had
## none goes.
func _unmask(page: Array[Dictionary]) -> void:
	for l in page:
		if l.get("dissolve_plain", false):
			l.canvas.material = null
			l.dissolve_plain = false
		elif l.has("canvas") and l.canvas.material is ShaderMaterial:
			PlaxEffects.set_dissolve(l.canvas.material, 0, 0, 0, 0, 0, 0)
			PlaxEffects.set_grade(l.canvas.material, Color.BLACK, 0)
			if l.has("anim_canvas"):
				PlaxEffects.set_dissolve(l.anim_canvas.material, 0, 0, 0, 0, 0, 0)
				PlaxEffects.set_grade(l.anim_canvas.material, Color.BLACK, 0)


## The tint at that opacity, and whether anything drawn with it would show (ParallaxPageReader.setBatchColor).
func _set_modulate(alpha: float) -> bool:
	_modulate = Color(tint.r, tint.g, tint.b, tint.a * alpha)
	return _modulate.a > 0


## A layer's canvas draws it; a layer at alpha 0 is not drawn. `patches`: its patches' canvas draws them (_add_canvas).
func _draw_layer(l: Dictionary, patches := false) -> void:
	_part = (2 if patches else 1) if l.has("anim_canvas") else 0
	if page != null and l.model.kind == "LURKER":
		# The lurkers against this view; a figure not there is not drawn (PlaxLurker).
		_lurker_zoom = zoom
		PlaxLurker.sight(self, zoom)
		if not PlaxLurker.is_there(l):
			return
	if page == null or not _set_modulate(_alpha_of(l)):
		return
	# Its own view, at its zoom: tiled across, and drawn into the screen at as many pixels a unit.
	var z := _zoom_of(l)
	_update_scale(z)
	_target = l.anim_canvas if patches else l.canvas
	var m: Dictionary = l.model
	var anchor := _anchor_of(z)
	var x: float = l.distance_x + anchor.x
	var y: float = l.distance_y + anchor.y
	var view_w := world_width * z
	var view_h := _world_height * z
	_view_w = view_w
	_planet = 0.0
	_reach = 0.0
	_panorama = []
	_eye_y = view_h / 2
	# Behind a tower seen from close by: a panorama round its axis (ParallaxPageReader.drawImageLayer); else, round the
	# page's planet.
	var near := PlaxPanorama.near_tower_of(transfer_layers if l.incoming else layers)
	var pano := PlaxPanorama.radius_of(near, l, world_width)
	if pano > 0:
		var camera := PlaxPanorama.camera_of(near, world_width)
		var front: float = camera - near.model.towerRadius * (world_width / 100.0)
		_panorama = [front * pano / (camera + pano), camera / pano, PackedFloat32Array(), PackedFloat32Array()]
		PlaxPanorama.lay_out(_panorama[0], _panorama[1], view_w, _panorama[2], _panorama[3])
	elif m.kind == "IMAGE" or m.kind == "SHADER" or m.kind == "SEQUENCE":
		_planet = _planet_of(l)
		_reach = PlaxPlanet.reach_of(_planet, view_w, view_h)
	if m.kind == "EMPTY":
		# No mirrored copy: the hook draws what it likes in each tile.
		var hook: Callable = _hooks.get(m.name, Callable())
		if hook.is_valid():
			_tile(l, x, y, _repeat_x, _repeat_y, false, view_w, view_h, hook)
		return
	if _masked(l):
		if _target.material == null:
			_target.material = PlaxEffects.material("PLAIN")
			l.dissolve_plain = true
		var ramp := _style.slot_ramp(_new_alpha, l.slot, maxi(layers.size(), transfer_layers.size()))
		PlaxEffects.set_dissolve(_target.material, 2 if l.incoming else 1, ramp, _style.softness,
				PlaxTransfertStyle.drift(_effect_time), _style.patches, _style.cells_up(view_w, view_h))
	if _graded(l):
		if _target.material == null:
			_target.material = PlaxEffects.material("PLAIN")
			l.dissolve_plain = true
		PlaxEffects.set_grade(_target.material, _style.color,
				_style.grade_at(_new_alpha, l.slot, maxi(layers.size(), transfer_layers.size())))
	if m.kind == "SHADER":
		PlaxEffects.apply(_target.material, l, _effect_time, view_w, view_h, anchor.x, anchor.y,
				Vector2(1.0 if _repeat_x else 0.0, 1.0 if _repeat_y else 0.0))
	if _target.material:
		_target.material.set_shader_parameter("haze", l.haze)
		_target.material.set_shader_parameter("fog_color", l.fog_color)
	# The animated patch's frame, on the clock the effects run on: drawn after each tile (PlaxAnim.draw).
	PlaxAnim.set_time(l, _effect_time)
	if patches:
		PlaxAnim.apply(_target.material, l)
	if m.kind == "FLOOR":
		# Its rows across the whole view, whatever the page repeats; no mirrored copy.
		PlaxFloor.draw(self, l, y, view_w, view_h)
		return
	if PlaxTower.is_tower(l):
		_planet = 0.0
		_reach = 0.0
		# Round its tower in the view's middle, tiled up as the page says; no mirrored copy.
		PlaxTower.draw_rows(self, l, x, y, view_w, view_h)
		return
	_tile(l, x, y, _repeat_x, _repeat_y, false, view_w, view_h)
	if m.mirror and _repeat_x != _repeat_y:
		# A mirrored copy is stacked next to the tiled strip: above it when tiling on X, to its right on Y.
		if _repeat_x:
			_tile(l, x, y + m.padY + l.height, true, false, true, view_w, view_h)
		else:
			_tile(l, x + m.padX + l.width, y, false, true, true, view_w, view_h)


## The two gradients, in screen pixels and opaque: the libGDX heart draws them without blending.
func _draw_gradients(screen: Vector2) -> void:
	var top_h := screen.y * (1.0 - _top_half_size)
	if top_h > 0:
		_draw_gradient(Rect2(0, 0, screen.x, top_h), _gradient[0], _gradient[1])
	var bottom_h := screen.y * (1.0 - _bottom_half_size)
	if bottom_h > 0:
		_draw_gradient(Rect2(0, screen.y - bottom_h, screen.x, bottom_h), _gradient[2], _gradient[3])


func _draw_gradient(rect: Rect2, top: Color, bottom: Color) -> void:
	top.a = 1
	bottom.a = 1
	_canvas.draw_polygon(
		PackedVector2Array(
			[rect.position, Vector2(rect.end.x, rect.position.y), rect.end, Vector2(rect.position.x, rect.end.y)]),
		PackedColorArray([top, top, bottom, bottom]))


## The layer at (x, y) plus every repetition, on the requested axes, that intersects the view
## (x 0..view_w, y 0..view_h).
func _tile(
		l: Dictionary, x: float, y: float, on_x: bool, on_y: bool, mirror: bool, view_w: float, view_h: float,
		hook := Callable()
) -> void:
	var width: float = l.width
	var height: float = l.height
	var sequence: bool = l.model.kind == "SEQUENCE"
	# A SEQUENCE layer whose pads outweigh its segments is no wider than 0, its slots still drawn: once, below.
	if (width <= 0 and not sequence) or height <= 0:
		return
	# What is drawn of a tile, from its corner: the box, or a SEQUENCE layer's slots (ParallaxLayer.getCycleLeft/Right).
	var left := PlaxSequence.cycle_left(l) if sequence else 0.0
	var right := PlaxSequence.cycle_right(l) if sequence else width
	# A step <= 0 (a negative padding larger than the image) can't tile: the layer is drawn once.
	var step_x: float = width + l.model.padX
	var step_y: float = height + l.model.padY
	var tile_x := on_x and step_x > 0
	var tile_y := on_y and step_y > 0
	var start_x := x - ceilf((x + right) / step_x) * step_x if tile_x else x
	var start_y := y - ceilf((y + height) / step_y) * step_y if tile_y else y
	var count_x := int(ceilf((view_w - left - start_x) / step_x)) + 1 if tile_x else 1
	# Round a planet the view's top corners show what lies a little over it.
	var top := view_h + _reach
	var count_y := int(ceilf((top - start_y) / step_y)) + 1 if tile_y else 1
	for row in count_y:
		var draw_y := start_y + row * step_y
		if draw_y + height <= 0 or draw_y >= top:
			continue
		for column in count_x:
			var draw_x := start_x + column * step_x
			if draw_x + right <= 0 or draw_x + left >= view_w:
				continue
			if hook.is_valid():
				var rect := Rect2(draw_x * _ppu, _screen_h - (draw_y + height) * _ppu, width * _ppu, height * _ppu)
				hook.call(_target, rect, _modulate, l)
				_target.draw_set_transform(Vector2.ZERO)
				continue
			var fx: bool = l.model.flipX
			var fy: bool = l.model.flipY
			if mirror:
				# Flipped vertically when tiling on X, horizontally when tiling on Y.
				if on_x:
					fy = not fy
				else:
					fx = not fx
			if l.model.kind == "SEQUENCE":
				PlaxSequence.draw(self, l, draw_x, draw_y, 0, view_w, fx, fy)
			else:
				if _part != 2:
					_draw_region(l, draw_x, draw_y, l.width, l.height, fx, fy)
				if _part != 1:
					PlaxAnim.draw(self, l, draw_x, draw_y, fx, fy)


## The packed image of `l` (a layer, or a sequence's segment) in a box at (x, y); a flip mirrors where it sits in the
## box too.
func _draw_region(l: Dictionary, x: float, y: float, width: float, height: float, fx: bool, fy: bool) -> void:
	var draw_w: float = width * l.packed_w
	var draw_h: float = height * l.packed_h
	var left: float = x + width * ((1 - l.trim_left - l.packed_w) if fx else l.trim_left)
	var bottom: float = y + height * ((1 - l.trim_bottom - l.packed_h) if fy else l.trim_bottom)
	var r: Dictionary = l.region
	var src := PlaxEffects.drawn_rect(r)
	if not _panorama.is_empty():
		# Behind a near tower: cut in strips, each placed round the panorama and scaled about the eye.
		PlaxPanorama.draw(self, r.texture, src, left, bottom, draw_w, draw_h, fx, fy, _panorama, _eye_y, _view_w)
		return
	if _planet > 0:
		# Round the page's planet: cut in strips, each wrapped round it (ParallaxPageReader.drawImageLayer).
		PlaxPlanet.draw(self, r.texture, src, left, bottom, draw_w, draw_h, fx, fy, _planet, _view_w)
		return
	var px := Vector2(draw_w, draw_h) * _ppu
	var origin := Vector2(left * _ppu, _screen_h - (bottom + draw_h) * _ppu)
	if fx or fy:
		_target.draw_set_transform(
			origin + Vector2(px.x if fx else 0.0, px.y if fy else 0.0), 0, Vector2(-1 if fx else 1, -1 if fy else 1))
		_target.draw_texture_rect_region(r.texture, Rect2(Vector2.ZERO, px), src, _modulate)
		_target.draw_set_transform(Vector2.ZERO)
	else:
		_target.draw_texture_rect_region(r.texture, Rect2(origin, px), src, _modulate)
