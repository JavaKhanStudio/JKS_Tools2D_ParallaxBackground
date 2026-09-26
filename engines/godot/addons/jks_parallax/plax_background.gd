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
## Not ported yet: cross-fading into another page and tinting (ParallaxPageReader.addLayersTransfert /
## addColorTransfert), and atlas regions packed rotated.

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

var page: PlaxPage
var atlas: PlaxAtlas
## One per page layer, back to front: the ParallaxLayer state.
var layers: Array[Dictionary] = []

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
	layers.clear()
	_update_world()
	var mipmaps := false
	for model in page.layers:
		var region := atlas.find_region(model.regionName, model.regionPosition) if atlas else {}
		if region.is_empty():
			push_error("PlaxBackground: region '%s' #%d not found in %s" % [model.regionName, model.regionPosition, page.atlas_name])
			continue
		if region.rotate:
			push_warning("PlaxBackground: region '%s' is packed rotated, which is not supported: drawn as is" % model.regionName)
		mipmaps = mipmaps or region.texture.get_meta("plax_mipmaps", false)
		layers.append(_build_layer(model, region))
	_canvas.texture_filter = CanvasItem.TEXTURE_FILTER_LINEAR_WITH_MIPMAPS if mipmaps else CanvasItem.TEXTURE_FILTER_LINEAR
	reset_positions()
	_canvas.queue_redraw()


## Puts every layer back at its decal.
func reset_positions() -> void:
	for l in layers:
		l.distance_x = l.model.decal_X_Ratio * (world_width / 100.0)
		l.distance_y = l.model.decal_Y_Ratio * (_world_height / 100.0)


func _process(delta: float) -> void:
	if auto_act:
		act(delta)


## Scrolls the layers by one step of `delta` seconds (ParallaxPageReader.act).
func act(delta: float) -> void:
	var speed_x := speed_constant_x + speed_consumable_x
	var speed_y := speed_constant_y + speed_consumable_y
	speed_consumable_x = 0
	speed_consumable_y = 0
	if page == null:
		return
	for l in layers:
		var m: Dictionary = l.model
		l.distance_y -= delta * speed_y * m.parallaxScalingSpeedY
		l.distance_x -= delta * (m.speedXAtRest + speed_x) * m.parallaxScalingSpeedX
		# Kept within one tile so the tiling loops stay short and floats stay precise.
		var total_w: float = l.width + m.padX
		if page.repeat_on_x and total_w > 0:
			l.distance_x = fmod(l.distance_x, total_w)
		var total_h: float = l.height + m.padY
		if page.repeat_on_y and total_h > 0:
			l.distance_y = fmod(l.distance_y, total_h)
	_canvas.queue_redraw()


func _build_layer(model: Dictionary, region: Dictionary) -> Dictionary:
	var l := {"model": model, "region": region, "distance_x": 0.0, "distance_y": 0.0,
		"trim_left": 0.0, "trim_bottom": 0.0, "packed_w": 1.0, "packed_h": 1.0}
	var image_w: float = region.width
	var image_h: float = region.height
	# useOriginalSize: a region packed with its whitespace stripped keeps its original size, and its packed image
	# is drawn at its offset inside it. Off: the packed image is stretched over the whole layer.
	if page.use_original_size and region.original_width > 0 and region.original_height > 0:
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
	for l in layers:
		_size_layer(l)
	_canvas.queue_redraw()


func _screen_size() -> Vector2:
	return get_viewport().get_visible_rect().size if is_inside_tree() else Vector2(1280, 720)


# --- drawing (ParallaxPageReader.draw), in the page's units, y up, converted to pixels at the last moment ----------

var _ppu := 1.0
var _screen_h := 0.0


func _draw_page() -> void:
	if page == null:
		return
	var screen := _screen_size()
	_ppu = screen.x / world_width
	_screen_h = screen.y
	_draw_gradients(screen)
	var view_w := world_width
	var view_h := _world_height
	for l in layers:
		var m: Dictionary = l.model
		var x: float = l.distance_x
		var y: float = l.distance_y
		_tile(l, x, y, page.repeat_on_x, page.repeat_on_y, false, view_w, view_h)
		if m.mirror and page.repeat_on_x != page.repeat_on_y:
			# A mirrored copy is stacked next to the tiled strip: above it when tiling on X, to its right on Y.
			if page.repeat_on_x:
				_tile(l, x, y + m.padY + l.height, true, false, true, view_w, view_h)
			else:
				_tile(l, x + m.padX + l.width, y, false, true, true, view_w, view_h)


## The two gradients, in screen pixels and opaque: the libGDX heart draws them without blending.
func _draw_gradients(screen: Vector2) -> void:
	var top_h := screen.y * (1.0 - page.top_half_size)
	if top_h > 0:
		_gradient(Rect2(0, 0, screen.x, top_h), page.top_half_top, page.top_half_bottom)
	var bottom_h := screen.y * (1.0 - page.bottom_half_size)
	if bottom_h > 0:
		_gradient(Rect2(0, screen.y - bottom_h, screen.x, bottom_h), page.bottom_half_top, page.bottom_half_bottom)


func _gradient(rect: Rect2, top: Color, bottom: Color) -> void:
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
		_canvas.draw_texture_rect_region(r.texture, Rect2(Vector2.ZERO, px), src)
		_canvas.draw_set_transform(Vector2.ZERO)
	else:
		_canvas.draw_texture_rect_region(r.texture, Rect2(origin, px), src)
