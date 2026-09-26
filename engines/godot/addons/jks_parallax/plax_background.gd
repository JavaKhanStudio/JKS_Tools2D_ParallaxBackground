class_name PlaxBackground
extends CanvasLayer
## A parallax page from the JKS editor, drawn behind the game: the Godot port of the libGDX runtime's
## Parallax_Heart + ParallaxPageReader + ParallaxLayer (core/src/jks/tools2d/parallax). Same numbers, same picture:
## tools/godot-parallax-shots.sh compares its frames with the libGDX ones.
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
##   bg.tint_to(Color(1, 0.6, 0.4), 2.0)
##
## Not ported yet: atlas regions packed rotated.

@export_file("*.jplax", "*.plaxpj") var page_path := ""
## Folder the page's atlas is in; empty: the page's own folder.
@export_dir var atlas_dir := ""
@export var world_width := 40.0
## Scroll speed, as Parallax_Heart.screenSpeedConstantX/Y.
@export var speed_constant_x := 0.0
@export var speed_constant_y := 0.0
## Scroll speed for the next frame only, then reset (e.g. from the player's movement).
var speed_consumable_x := 0.0
var speed_consumable_y := 0.0
## False: nothing moves unless act() is called (to step it yourself, at a fixed rate).
@export var auto_act := true

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
var _top_half_size := 0.5
var _bottom_half_size := 0.5

var _canvas := Node2D.new()
var _world_height := 22.5


func _init() -> void:
	layer = -100
	_canvas.draw.connect(_draw_page)
	add_child(_canvas)


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
	_canvas.queue_redraw()


## Cross-fades into `new_page` over `seconds` (0: at once), as Parallax_Heart.transfertIntoPage. The pages are matched
## from their front layer: an incoming layer takes the distance its counterpart has scrolled, so nothing jumps. The
## gradients' colors fade too; the repeat and the gradients' sizes stay those of the page given to set_page. A fade
## started during another drops the page that was fading in.
func transfert_into(new_page: PlaxPage, new_atlas: PlaxAtlas, seconds: float) -> void:
	if page == null:
		set_page(new_page, new_atlas)
		return
	_reset_transfert()
	var incoming := _build_layers(new_page, new_atlas)
	if not incoming.is_empty():
		transfer_page = new_page
		transfer_atlas = new_atlas
		transfer_layers = incoming
		_sync_transfer_positions()
		if seconds <= 0:
			_finish_transfert()
		else:
			_fade_speed = 1.0 / seconds
	_fade_gradients([new_page.top_half_top, new_page.top_half_bottom, new_page.bottom_half_top, new_page.bottom_half_bottom], seconds)
	_update_filter()
	_canvas.queue_redraw()


## Fades `tint` toward `color` over `seconds` (0: at once), as ParallaxPageReader.addColorTransfert.
func tint_to(color: Color, seconds: float) -> void:
	_tint_from = tint
	_tint_to = color
	_tint_elapsed = 0
	_tint_duration = seconds
	if seconds <= 0:
		tint = color
	_canvas.queue_redraw()


func is_in_transfer() -> bool:
	return not transfer_layers.is_empty()


func _build_layers(from_page: PlaxPage, from_atlas: PlaxAtlas) -> Array[Dictionary]:
	var built: Array[Dictionary] = []
	for model in from_page.layers:
		var region := from_atlas.find_region(model.regionName, model.regionPosition) if from_atlas else {}
		if region.is_empty():
			push_error("PlaxBackground: region '%s' #%d not found in %s" % [model.regionName, model.regionPosition, from_page.atlas_name])
			continue
		if region.rotate:
			push_warning("PlaxBackground: region '%s' is packed rotated, which is not supported: drawn as is" % model.regionName)
		var l := _build_layer(model, region, from_page.use_original_size)
		_reset_position(l)
		built.append(l)
	return built


## ParallaxPageReader.syncTransferPositions: matched from the front, the incoming layer keeps its own decal and takes
## the distance its counterpart has scrolled from its decal.
func _sync_transfer_positions() -> void:
	var total := maxi(layers.size(), transfer_layers.size())
	var old_offset := total - layers.size()
	var new_offset := total - transfer_layers.size()
	for slot in range(maxi(old_offset, new_offset), total):
		var from: Dictionary = layers[slot - old_offset]
		var to: Dictionary = transfer_layers[slot - new_offset]
		to.distance_x = to.model.decal_X_Ratio * (world_width / 100.0) + from.distance_x - from.model.decal_X_Ratio * (world_width / 100.0)
		to.distance_y = to.model.decal_Y_Ratio * (_world_height / 100.0) + from.distance_y - from.model.decal_Y_Ratio * (_world_height / 100.0)


func _finish_transfert() -> void:
	page = transfer_page
	atlas = transfer_atlas
	layers = transfer_layers
	_reset_transfert()
	_update_filter()


func _reset_transfert() -> void:
	transfer_page = null
	transfer_atlas = null
	transfer_layers = []
	_new_alpha = 0
	_old_alpha = 1


## SquareBackground.transfertInto: both colors of both gradients, alpha included, toward the incoming page's.
func _fade_gradients(target: Array[Color], seconds: float) -> void:
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
		_gradient[i] = _gradient_from[i].lerp(_gradient_to[i], progress)
	if progress >= 1:
		_gradient_fading = false


## Mipmapped sampling when a page on screen was packed with mipmaps.
func _update_filter() -> void:
	var mipmaps := false
	for l in layers + transfer_layers:
		mipmaps = mipmaps or l.region.texture.get_meta("plax_mipmaps", false)
	_canvas.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS if mipmaps else CanvasItem.TEXTURE_FILTER_LINEAR


## Puts every layer back at its decal.
func reset_positions() -> void:
	for l in layers:
		_reset_position(l)


func _reset_position(l: Dictionary) -> void:
	l.distance_x = l.model.decal_X_Ratio * (world_width / 100.0)
	l.distance_y = l.model.decal_Y_Ratio * (_world_height / 100.0)


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
	_act_gradients(delta)
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
	_canvas.queue_redraw()


## ParallaxLayer.act.
func _act_layer(l: Dictionary, delta: float, speed_x: float, speed_y: float) -> void:
	var m: Dictionary = l.model
	l.distance_y -= delta * speed_y * m.parallaxScalingSpeedY
	l.distance_x -= delta * (m.speedXAtRest + speed_x) * m.parallaxScalingSpeedX
	# Kept within one tile so the tiling loops stay short and floats stay precise.
	var total_w: float = l.width + m.padX
	if _repeat_x and total_w > 0:
		l.distance_x = fmod(l.distance_x, total_w)
	var total_h: float = l.height + m.padY
	if _repeat_y and total_h > 0:
		l.distance_y = fmod(l.distance_y, total_h)


func _build_layer(model: Dictionary, region: Dictionary, use_original_size: bool) -> Dictionary:
	var l := {"model": model, "region": region, "distance_x": 0.0, "distance_y": 0.0,
		"trim_left": 0.0, "trim_bottom": 0.0, "packed_w": 1.0, "packed_h": 1.0}
	var image_w: float = region.width
	var image_h: float = region.height
	# useOriginalSize: a region packed with its whitespace stripped keeps its original size, and its packed image
	# is drawn at its offset inside it. Off: the packed image is stretched over the whole layer.
	if use_original_size and region.original_width > 0 and region.original_height > 0:
		l.trim_left = region.offset_x / region.original_width
		l.trim_bottom = region.offset_y / region.original_height
		l.packed_w = region.width / region.original_width
		l.packed_h = region.height / region.original_height
		image_w = region.original_width
		image_h = region.original_height
	l.image_w = image_w
	l.image_h = image_h
	_size_layer(l)
	return l


## A layer is sizeRatio worlds wide; its height follows the image.
func _size_layer(l: Dictionary) -> void:
	l.width = world_width * l.model.sizeRatio
	l.height = l.image_h * (world_width / l.image_w) * l.model.sizeRatio


func _update_world() -> void:
	var screen := _screen_size()
	_world_height = world_width * screen.y / screen.x


## The world keeps its width and follows the screen's aspect ratio, as Parallax_Heart.resize does; nothing moves.
func _on_resize() -> void:
	_update_world()
	for l in layers + transfer_layers:
		_size_layer(l)
	_canvas.queue_redraw()


func _screen_size() -> Vector2:
	return get_viewport().get_visible_rect().size if is_inside_tree() else Vector2(1280, 720)


# --- drawing (ParallaxPageReader.draw), in the page's units, y up, converted to pixels at the last moment ----------

var _ppu := 1.0
var _screen_h := 0.0
var _modulate := Color.WHITE


func _draw_page() -> void:
	if page == null:
		return
	var screen := _screen_size()
	_ppu = screen.x / world_width
	_screen_h = screen.y
	_draw_gradients(screen)
	# A layer at alpha 0 is not drawn. During a cross-fade the pages are matched from the front: slot by slot, the
	# outgoing layer then the incoming one.
	if transfer_layers.is_empty():
		if _set_modulate(1):
			for l in layers:
				_draw_layer(l)
	else:
		var total := maxi(layers.size(), transfer_layers.size())
		var old_offset := total - layers.size()
		var new_offset := total - transfer_layers.size()
		for slot in total:
			if slot >= old_offset and _set_modulate(_old_alpha):
				_draw_layer(layers[slot - old_offset])
			if slot >= new_offset and _set_modulate(_new_alpha):
				_draw_layer(transfer_layers[slot - new_offset])


## The tint at that opacity, and whether anything drawn with it would show (ParallaxPageReader.setBatchColor).
func _set_modulate(alpha: float) -> bool:
	_modulate = Color(tint.r, tint.g, tint.b, tint.a * alpha)
	return _modulate.a > 0


func _draw_layer(l: Dictionary) -> void:
	var m: Dictionary = l.model
	var x: float = l.distance_x
	var y: float = l.distance_y
	var view_w := world_width
	var view_h := _world_height
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
		PackedVector2Array([rect.position, Vector2(rect.end.x, rect.position.y), rect.end, Vector2(rect.position.x, rect.end.y)]),
		PackedColorArray([top, top, bottom, bottom]))


## The layer at (x, y) plus every repetition, on the requested axes, that intersects the view (x 0..view_w, y 0..view_h).
func _tile(l: Dictionary, x: float, y: float, on_x: bool, on_y: bool, mirror: bool, view_w: float, view_h: float) -> void:
	var width: float = l.width
	var height: float = l.height
	if width <= 0 or height <= 0:
		return
	# A step <= 0 (a negative padding larger than the image) can't tile: the layer is drawn once.
	var step_x: float = width + l.model.padX
	var step_y: float = height + l.model.padY
	var tile_x := on_x and step_x > 0
	var tile_y := on_y and step_y > 0
	var start_x := x - ceilf((x + width) / step_x) * step_x if tile_x else x
	var start_y := y - ceilf((y + height) / step_y) * step_y if tile_y else y
	var count_x := int(ceilf((view_w - start_x) / step_x)) + 1 if tile_x else 1
	var count_y := int(ceilf((view_h - start_y) / step_y)) + 1 if tile_y else 1
	for row in count_y:
		var draw_y := start_y + row * step_y
		if draw_y + height <= 0 or draw_y >= view_h:
			continue
		for column in count_x:
			var draw_x := start_x + column * step_x
			if draw_x + width <= 0 or draw_x >= view_w:
				continue
			var fx: bool = l.model.flipX
			var fy: bool = l.model.flipY
			if mirror:
				# Flipped vertically when tiling on X, horizontally when tiling on Y.
				if on_x:
					fy = not fy
				else:
					fx = not fx
			_draw_region(l, draw_x, draw_y, fx, fy)


## The packed image in the layer's box at (x, y); a flip mirrors where it sits in the box too.
func _draw_region(l: Dictionary, x: float, y: float, fx: bool, fy: bool) -> void:
	var width: float = l.width
	var height: float = l.height
	var draw_w: float = width * l.packed_w
	var draw_h: float = height * l.packed_h
	var left: float = x + width * ((1 - l.trim_left - l.packed_w) if fx else l.trim_left)
	var bottom: float = y + height * ((1 - l.trim_bottom - l.packed_h) if fy else l.trim_bottom)
	var r: Dictionary = l.region
	var src := Rect2(r.x, r.y, r.width, r.height)
	var px := Vector2(draw_w, draw_h) * _ppu
	var origin := Vector2(left * _ppu, _screen_h - (bottom + draw_h) * _ppu)
	if fx or fy:
		_canvas.draw_set_transform(origin + Vector2(px.x if fx else 0.0, px.y if fy else 0.0), 0, Vector2(-1 if fx else 1, -1 if fy else 1))
		_canvas.draw_texture_rect_region(r.texture, Rect2(Vector2.ZERO, px), src, _modulate)
		_canvas.draw_set_transform(Vector2.ZERO)
	else:
		_canvas.draw_texture_rect_region(r.texture, Rect2(origin, px), src, _modulate)
