class_name PlaxRings
extends Node3D
## A page as the sky of a 3D world (parallax-readers r49, docs/page-as-3d-sky.md): each layer that draws an image is an
## open cylinder round the world's centre, its radius from its speed, so a camera flying through the world sees the
## layers slide by perspective alone; the page's gradients are the WorldEnvironment's sky, by elevation. Built from a
## PlaxBackground's state (its layers, scroll, cross-fades and tint), never a second loader:
##
##   var bg := PlaxBackground.new()
##   bg.load_page("res://backgrounds/calm.jplax")      # kept out of the tree: the rings draw it, not the canvas
##   var rings := PlaxRings.new(bg, {"front": 330, "foot": 50})   # or "far": 760, "lift": -2, "scale": 0.7
##   add_child(rings)
##   rings.apply_sky($WorldEnvironment.environment)   # the gradients as its sky
##
## Each frame it acts the background (when the background is not in the tree, as auto_act says) and lays the rings out
## as it is now; a game stepping the background itself sets `auto_update` false and calls update(0).
##
## The statics are core's ParallaxRings line for line, the numbers only (engines/rounds/rings checks them against
## core's: tests/rings.gd, tools/frame-check.sh); the instance draws them, as three.js's PlaxRings (engines/threejs/src/
## plax.js) does. Its colours are libGDX's, as the flat reader's: the shaders mix in the page's colours and hand the
## result over in the colour space the renderer outputs (OUTPUT_IS_SRGB), the tint packed. Each band is one
## MeshInstance3D per texture its images sit on, unshaded, depth test on and write off, ordered by render_priority far
## first (the rings share a centre: no depth sort can order them).

## ParallaxRings.MAX_ELEVATION: no band reaches the zenith, a cylinder there would be infinitely high.
const MAX_ELEVATION := 89.0 * PI / 180.0
## ParallaxLayer.drawsImage: the kinds a ring is drawn for; EMPTY and PARTICLES have none.
const DRAWS_IMAGE := ["IMAGE", "SHADER", "SEQUENCE", "FLOOR", "LURKER"]

# The shaders' last step: the colour as the renderer outputs it, sRGB (Compatibility) or linear.
const SRGB := """
vec3 to_output(vec3 c, bool srgb) {
	c = clamp(c, 0.0, 1.0);
	return srgb ? c : mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
}
"""

# A band, on its texture (FILTER: the page's filter, _ring_material): the page's tint, its depth fog, the melts by the
# band's height (UV2.x) and by elevation from the eye, a transfert's grade.
const RING_SHADER := """shader_type spatial;
render_mode unshaded, blend_mix, depth_draw_never, cull_disabled, fog_disabled, shadows_disabled;
uniform sampler2D tex : FILTER, repeat_disable;
uniform vec4 tint = vec4(1.0);
uniform vec3 fog_color = vec3(0.93, 0.95, 0.97);
uniform float haze;
uniform float melt;
uniform float eye_melt;
uniform vec4 grade;
varying vec3 from_eye;
""" + SRGB + """
void vertex() {
	from_eye = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz - CAMERA_POSITION_WORLD;
}
void fragment() {
	vec4 color = tint * texture(tex, UV);
	float melted = melt > 0.0 ? 1.0 - smoothstep(0.0, melt, UV2.x) : 0.0;
	if (eye_melt > 0.0)
		melted = max(melted, 1.0 - smoothstep(0.0, eye_melt, asin(clamp(normalize(from_eye).y, -1.0, 1.0))));
	float fog = haze + (1.0 - haze) * melted;
	ALBEDO = to_output(mix(mix(color.rgb, fog_color, fog), grade.rgb, grade.a), OUTPUT_IS_SRGB);
	ALPHA = color.a;
}
"""

# A SHADER layer's band (r59): its effect's canvas_item shader (PlaxEffects.code) made spatial by _ring_effect_code,
# as three.js's ringEffectMaterial: the image's page units round the ring (UV2.y across, UV2.x times page_height up)
# stand in for SCREEN_UV (a view 1 by 1), the tint a uniform, hazed() melting the band as RING_SHADER does.
const RING_EFFECT_HEAD := """shader_type spatial;
render_mode unshaded, blend_mix, depth_draw_never, cull_disabled, fog_disabled, shadows_disabled;
uniform sampler2D ring_tex : FILTER, repeat_disable;
uniform float melt;
uniform float eye_melt;
uniform float page_height;
varying vec2 ring_screen;
varying float ring_band;
varying vec3 from_eye;
""" + SRGB
const RING_EFFECT_VERTEX := """void vertex() {
	ring_screen = vec2(UV2.y, 1.0 - UV2.x * page_height);
	ring_band = UV2.x;
	from_eye = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz - CAMERA_POSITION_WORLD;
}"""
const RING_EFFECT_HAZE := """float ring_haze() {
	float melted = melt > 0.0 ? 1.0 - smoothstep(0.0, melt, ring_band) : 0.0;
	if (eye_melt > 0.0)
		melted = max(melted, 1.0 - smoothstep(0.0, eye_melt, asin(clamp(normalize(from_eye).y, -1.0, 1.0))));
	return haze + (1.0 - haze) * melted;
}
"""

# The sky: the page's gradients by elevation (ParallaxRings.gradient), the fog under the horizon.
const SKY_SHADER := """shader_type sky;
uniform vec3 top_from = vec3(1.0);
uniform vec3 top_to = vec3(1.0);
uniform vec3 bottom_from = vec3(1.0);
uniform vec3 bottom_to = vec3(1.0);
uniform vec3 fog_color = vec3(0.93, 0.95, 0.97);
uniform vec2 top = vec2(0.0, 1.0);
uniform vec2 bottom = vec2(0.0, 1.0);
uniform float horizon = 0.12;
// A sky shader has no OUTPUT_IS_SRGB: true under Compatibility (_init).
uniform bool output_srgb;
""" + SRGB + """
float along(float e, vec2 span) {
	return span.y > span.x ? clamp((e - span.x) / (span.y - span.x), 0.0, 1.0) : step(span.x, e);
}
void sky() {
	float e = asin(clamp(EYEDIR.y, -1.0, 1.0));
	vec3 color = mix(top_from, top_to, along(e, top));
	if (e <= bottom.y)
		color = mix(bottom_from, bottom_to, along(e, bottom));
	COLOR = to_output(mix(fog_color, color, smoothstep(-0.02, horizon, e)), output_srgb);
}
"""

static var _next_id := 0
static var _ring_shaders := {}
static var _sky_shader: Shader

## Whether _process acts the background (when it is not in the tree and its auto_act is on) and lays the rings out.
@export var auto_update := true

var background: PlaxBackground
## options()'s, and: fogStrength and fogColor (a Color) left out, the page's own; melt, the share of each band's
## height, from its bottom, that melts into the fog's colour (deux-generaux's 0.22; 0 none); eyeMelt, how high, in
## radians above the camera's own horizon, the bands melt into the fog's colour as the camera sees them (0 none): it
## holds where melt's part of a band is hidden behind the world's own hills; horizon, how high, in radians, the fog
## climbs the sky from the horizon; tintSky, the background's tint (tint_to) laid on the sky's gradients and the fog's
## colour too (off: as the flat reader, which tints the layers only); renderPriority, the first of the render
## priorities the bands take (one per band, far first), below what the game draws transparent. The camera's far plane
## must reach past the farthest band; a still band is drawn round the camera at 0.85 of it.
var options := {}
## The sky's material: apply_sky puts it on an Environment.
var sky_material := ShaderMaterial.new()

# Per layer, by its ring_id (a layer dictionary hashes by its contents, which move every frame): its band's meshes,
# and the band they were built from.
var _built := {}
# Per page side, by its first layer's ring_id: its bands, and what they were computed from.
var _bands_cache := {}
var _version := 0


func _init(bg: PlaxBackground = null, with_options := {}) -> void:
	name = "PlaxRings"
	# After the game's own nodes: the camera has moved when a still band follows it.
	process_priority = 100
	background = bg
	sky_material.shader = _sky_shader_of()
	sky_material.set_shader_parameter("output_srgb",
			RenderingServer.get_current_rendering_method() == "gl_compatibility")
	set_options(with_options)


func _process(delta: float) -> void:
	if not auto_update or background == null:
		return
	var acts := background.auto_act and not background.is_inside_tree()
	update(delta if acts else 0.0)


# --- the numbers (core's ParallaxRings) -------------------------------------------------------------------------

## ParallaxRings.Options, what the 3D scene chooses (the page itself stores none of it), over `o`: front, the front
## band's radius in world units; far, when more than 0 the slowest moving band's radius, the depths compressed into
## front..far (radius_of), 0 the library's depths; foot, the height of the eye the page is seen from, its horizon; lift,
## the page raised (lowered when negative) by this, in page units, before scale; scale, the page squeezed (or
## stretched) about its horizon; pageDegrees, how many degrees of sky the page's height spans at scale 1;
## fogStrength, the page fog's strength (PlaxEffects.fog_of).
static func ring_options(o := {}) -> Dictionary:
	var r := {"front": 100.0, "far": 0.0, "foot": 0.0, "lift": 0.0, "scale": 1.0, "pageDegrees": 45.0,
		"fogStrength": 0.0}
	for k in o:
		r[k] = float(o[k])
	return r


## ParallaxRings.pageHeightOf: the page's height in its units, as a ring sees it: the world's at 16:9.
static func page_height_of(world_width: float) -> float:
	return world_width * 9 / 16


## ParallaxRings.elevationOf: the elevation of `page_y` (page units up from its bottom edge), in radians, kept under
## the zenith.
static func elevation_of(page_y: float, world_width: float, o: Dictionary) -> float:
	var per_unit: float = deg_to_rad(o.pageDegrees) / page_height_of(world_width)
	return minf(MAX_ELEVATION, (page_y + o.lift) * per_unit * o.scale)


## ParallaxRings.radiusOf: the radius of layer `l`'s ring (PlaxBackground's layer state), the page's speeds running
## from `slowest` (the slowest that moves) to `front`, as magnitudes. The library's depth: o.front * front / speed.
## With o.far: o.front * (front / speed)^p, p = ln(far / o.front) / ln(front / slowest), which puts the slowest at far
## (deux-generaux lib/saga/sky.js's law). Still: infinite.
static func radius_of(front: float, slowest: float, l: Dictionary, o: Dictionary) -> float:
	var speed := absf(float(l.model.parallaxScalingSpeedX))
	if not speed > 0:
		return INF
	var depth := _distance_of(front, l.model)
	if o.far > 0 and slowest > 0 and slowest < front:
		var p: float = log(o.far / o.front) / log(front / slowest)
		return o.front * pow(depth, p)
	return o.front * depth


## ParallaxDepth.distanceOf: how far layer `model` is, in front distances, its page's fastest speed ratio X being
## `front` (PlaxEffects.front_speed_of): front / speed, as magnitudes. The front layer is at 1, a still one infinitely
## far; a page that does not scroll is one picture, every layer at 1.
static func _distance_of(front: float, model: Dictionary) -> float:
	if not front > 0:
		return 1.0
	var speed := absf(float(model.parallaxScalingSpeedX))
	return front / speed if speed > 0 else INF


## ParallaxRings.bands: the rings of `page`'s layers (PlaxBackground's layers) that draw an image, in page order. Each:
## layer (its index in `page`), radius (INF for a still layer, drawn round the camera), bottom and top (the elevations
## of its edges, radians, seen from the centre at the foot), y and height (its bottom edge's height in the world and its
## own, a finite ring's only), repeat (how often its tile goes round, at least once), imageShare (the share of a tile
## the image fills, the rest padX), order (0 first: far first, as the rings share a centre and no depth sort can order
## them) and fog (PlaxEffects.fog_of).
static func bands(page: Array[Dictionary], world_width: float, o: Dictionary) -> Array[Dictionary]:
	var front := PlaxEffects.front_speed_of(page)
	var slowest := front
	for l in page:
		var speed := absf(float(l.model.parallaxScalingSpeedX))
		if speed > 0 and speed < slowest:
			slowest = speed
	var page_height := page_height_of(world_width)
	var found: Array[Dictionary] = []
	for i in page.size():
		var l: Dictionary = page[i]
		if not l.model.kind in DRAWS_IMAGE:
			continue
		var band := {"layer": i, "radius": radius_of(front, slowest, l, o), "bottom": 0.0, "top": 0.0, "y": 0.0,
			"height": 0.0, "repeat": 1, "imageShare": 1.0, "order": 0, "fog": 0.0}
		# ParallaxLayer.getDecalPercentY, getRegionHeight, getTotalWidth and getWidth: a SEQUENCE layer's width is its
		# cycle's without the padX after the last slot (PlaxBackground._size_layer), its tile with it.
		var foot: float = l.model.decal_Y_Ratio * page_height / 100
		var high: float = l.height
		band.bottom = elevation_of(foot, world_width, o)
		band.top = elevation_of(foot + high, world_width, o)
		var tile: float = l.width + l.model.padX
		band.imageShare = l.width / tile if tile > 0 else 1.0
		if band.radius != INF:
			band.y = o.foot + band.radius * tan(band.bottom)
			band.height = band.radius * (tan(band.top) - tan(band.bottom))
			# a tile as wide, for the band's height, as the image is for the layer's: the painting keeps its aspect
			if band.height > 0 and high > 0:
				band.repeat = maxi(1, roundi(TAU * band.radius / (tile * band.height / high)))
		elif band.top > band.bottom and high > 0:
			band.repeat = maxi(1, roundi(TAU / (tile * (band.top - band.bottom) / high)))
		band.fog = PlaxEffects.fog_of(o.fogStrength, front, l.model)
		found.append(band)
	# far first; among equals, page order (back to front)
	var sorted := found.duplicate()
	for i in range(1, sorted.size()):
		var j := i
		while j > 0 and sorted[j].radius > sorted[j - 1].radius:
			var swap: Dictionary = sorted[j]
			sorted[j] = sorted[j - 1]
			sorted[j - 1] = swap
			j -= 1
	for i in sorted.size():
		sorted[i].order = i
	return found


## ParallaxRings.gradient: the page's gradients by elevation, in radians, each over the part of the page's height the
## 2D heart gives it (top_half_size and bottom_half_size, the part each leaves uncovered): the top one from topFrom
## (its bottom colour) to topTo (its top colour), the bottom one from bottomFrom (its bottom colour) to bottomTo (its
## top colour), drawn over the top one where they meet.
static func gradient(top_half_size: float, bottom_half_size: float, world_width: float, o: Dictionary) -> Dictionary:
	var h := page_height_of(world_width)
	return {
		"topFrom": elevation_of(h * top_half_size, world_width, o),
		"topTo": elevation_of(h, world_width, o),
		"bottomFrom": elevation_of(0, world_width, o),
		"bottomTo": elevation_of(h * (1 - bottom_half_size), world_width, o),
	}


# --- the drawing ---------------------------------------------------------------------------------------------------

## Changes some options (as the constructor takes them); the bands are rebuilt at the next update().
func set_options(changed: Dictionary) -> void:
	if options.is_empty():
		options = {"melt": 0.22, "eyeMelt": 0.0, "horizon": 0.12, "tintSky": false, "renderPriority": -120}
	options.merge(changed, true)
	_version += 1


## The options core's ParallaxRings takes for `page`: these, the fog the page's when not given.
func ring_options_of(page: PlaxPage) -> Dictionary:
	var o := {}
	for k in ["front", "far", "foot", "lift", "scale", "pageDegrees"]:
		if options.has(k):
			o[k] = options[k]
	o.fogStrength = options.get("fogStrength", page.fog_strength if page else 0.0)
	return ring_options(o)


## Puts the page's gradients on `environment` as its sky (background mode sky, its own ambient light kept).
func apply_sky(environment: Environment) -> void:
	var sky := Sky.new()
	sky.sky_material = sky_material
	sky.process_mode = Sky.PROCESS_MODE_REALTIME
	sky.radiance_size = Sky.RADIANCE_SIZE_32
	environment.sky = sky
	environment.background_mode = Environment.BG_SKY


## Moves the background by `seconds` (its act: speedXAtRest's drift, the fades, the tint; 0 for none, when the game
## acts it itself), then lays the rings out as it is now.
func update(seconds := 0.0) -> void:
	var bg := background
	if seconds:
		bg.act(seconds)
	var live := {}
	var all: Array[Dictionary] = []
	for side in [[bg.layers, bg.page], [bg.transfer_layers, bg.transfer_page]]:
		var layers: Array[Dictionary] = side[0]
		var page: PlaxPage = side[1]
		if layers.is_empty() or page == null:
			continue
		var o := ring_options_of(page)
		for band in _bands_of(layers, o):
			var l: Dictionary = layers[band.layer]
			live[_id_of(l)] = true
			all.append(_sync(l, band, page))
	for id in _built.keys():
		if not live.has(id):
			_drop_band(id)
	# Far first, the outgoing page before the incoming one among equals (a stable sort: two still bands keep their order).
	var order := range(all.size())
	order.sort_custom(func(a: int, c: int) -> bool:
		var ra: float = all[a].band.radius
		var rc: float = all[c].band.radius
		return a < c if ra == rc else ra > rc)
	for i in order.size():
		var priority := clampi(int(options.renderPriority) + 1 + i, -128, 127)
		for mesh in all[order[i]].meshes:
			mesh.material_override.render_priority = priority
	_draw_sky()
	_follow_camera()


## Takes the rings out: their meshes freed, the background left as it is.
func clear() -> void:
	for id in _built.keys():
		_drop_band(id)
	_bands_cache.clear()


# Layer `l`'s id, stamped on it the first time.
static func _id_of(l: Dictionary) -> int:
	if not l.has("ring_id"):
		_next_id += 1
		l.ring_id = _next_id
	return l.ring_id


# The bands of `layers`, computed again when the options, the layers or a sequence's cycle changed.
func _bands_of(layers: Array[Dictionary], o: Dictionary) -> Array[Dictionary]:
	var sizes := []
	for l in layers:
		sizes.append([_id_of(l), l.width, l.height, l.get("cycle", [])])
	var key := [_version, o, sizes]
	var side := _id_of(layers[0])
	var cached: Dictionary = _bands_cache.get(side, {})
	if cached.get("key") == key:
		return cached.bands
	var found := bands(layers, background.world_width, o)
	_bands_cache[side] = {"key": key, "bands": found}
	return found


# The meshes of layer `l`'s band, built when it is new or its numbers changed; then this frame's scroll, colour, fog and
# fade set on them.
func _sync(l: Dictionary, band: Dictionary, page: PlaxPage) -> Dictionary:
	var bg := background
	var id := _id_of(l)
	var built: Dictionary = _built.get(id, {})
	var effect := effect_of(l)
	if built.is_empty() or not is_same(built.band, band) or built.effect != effect:
		if not built.is_empty():
			_drop_band(id)
		built = _build_band(l, band)
		_built[id] = built
	# The 2D reader's scroll: the tile at distance_x, one tile 1 / repeat of the ring.
	var tile: float = l.width + l.model.padX
	built.holder.rotation.y = -l.distance_x / tile * TAU / band.repeat if tile > 0 else 0.0
	# During a cross-fade, the layer's opacity as the flat reader's; a dissolve fades it by its share, it has no mask.
	var fade := bg._share_of(l) if bg._masked(l) else bg._alpha_of(l)
	var alpha := bg.tint.a * fade
	var tint := PlaxEffects.packed_rgb(bg.tint)
	var grade := Color(0, 0, 0, 0)
	if bg._graded(l):
		var total := maxi(bg.layers.size(), bg.transfer_layers.size())
		grade = Color(bg._style.color, clampf(bg._style.grade_at(bg._new_alpha, l.slot, total), 0, 1))
	var fog := _fog_color(page)
	for mesh: MeshInstance3D in built.meshes:
		mesh.visible = alpha > 0
		var m: ShaderMaterial = mesh.material_override
		m.set_shader_parameter("tint", Color(tint, PlaxEffects.packed_alpha(alpha)))
		m.set_shader_parameter("fog_color", fog)
		m.set_shader_parameter("haze", band.fog)
		m.set_shader_parameter("melt", float(options.melt))
		m.set_shader_parameter("eye_melt", float(options.eyeMelt))
		m.set_shader_parameter("grade", grade)
		if effect != "":
			_apply_effect(m, l, band)
	return built


# Layer `l`'s band as meshes, one per texture its images sit on, in a holder turned by the scroll: each image (an
# IMAGE or SHADER layer's, a SEQUENCE layer's slots) laid round the ring once per repeat, curved, its texture linear in
# angle and in elevation. A still band is on a cylinder of radius 1 round the camera, scaled to its far plane.
func _build_band(l: Dictionary, band: Dictionary) -> Dictionary:
	var still: bool = band.radius == INF
	var r: float = 1.0 if still else band.radius
	var foot_y: float = 0.0 if still else float(options.get("foot", 0.0))
	var holder := Node3D.new()
	var m: Dictionary = l.model
	var tile: float = l.width + m.padX
	var turn: float = TAU / band.repeat
	var images := []
	if m.kind == "SEQUENCE" and l.has("edges"):
		for slot in l.cycle.size():
			var segment: Dictionary = l.segments[l.cycle[slot]]
			images.append([segment, l.height * l.edges[slot] + slot * m.padX, l.height * segment.aspect])
	else:
		images.append([l, 0.0, l.width])
	var runs := {}
	const ROWS := 8
	const STEP := TAU / 192
	for k in band.repeat:
		for entry in images:
			var image: Dictionary = entry[0]
			var left: float = entry[1]
			var width: float = entry[2]
			if not width > 0 or not tile > 0:
				continue
			var fx: bool = m.flipX
			var fy: bool = m.flipY
			# As PlaxBackground._draw_region: the packed image in its box, a flip mirroring where it sits in it.
			var x0: float = left + width * ((1 - image.trim_left - image.packed_w) if fx else image.trim_left)
			var w: float = width * image.packed_w
			var b0: float = (1 - image.trim_bottom - image.packed_h) if fy else image.trim_bottom
			var bh: float = image.packed_h
			var texture: Texture2D = image.region.texture
			var src := PlaxEffects.drawn_rect(image.region)
			var size := Vector2(texture.get_width(), texture.get_height())
			var u0 := src.position.x / size.x
			var u1 := src.end.x / size.x
			var v_top := src.position.y / size.y
			var v_bottom := src.end.y / size.y
			if fx:
				var swap := u0
				u0 = u1
				u1 = swap
			if fy:
				var swap := v_top
				v_top = v_bottom
				v_bottom = swap
			if not runs.has(texture):
				runs[texture] = {"position": PackedVector3Array(), "uv": PackedVector2Array(),
					"band": PackedVector2Array(), "index": PackedInt32Array()}
			var run: Dictionary = runs[texture]
			var cols := maxi(1, ceili(w / tile * turn / STEP))
			var first: int = run.position.size()
			for j in ROWS + 1:
				var along: float = b0 + bh * j / ROWS
				var y: float = foot_y + r * tan(band.bottom + along * (band.top - band.bottom))
				for i in cols + 1:
					var a: float = k * turn + (x0 + w * i / cols) / tile * turn
					run.position.append(Vector3(r * sin(a), y, -r * cos(a)))
					run.uv.append(Vector2(u0 + (u1 - u0) * i / cols, v_bottom + (v_top - v_bottom) * j / ROWS))
					# x: the band's height share; y: page units round the ring from tile 0's left, an effect's across
					run.band.append(Vector2(along, k * tile + x0 + w * i / cols))
			for j in ROWS:
				for i in cols:
					var a := first + j * (cols + 1) + i
					var c := a + cols + 1
					run.index.append_array([a, a + 1, c + 1, a, c + 1, c])
	var meshes: Array[MeshInstance3D] = []
	var effect := effect_of(l)
	for texture: Texture2D in runs:
		var run: Dictionary = runs[texture]
		var arrays := []
		arrays.resize(Mesh.ARRAY_MAX)
		arrays[Mesh.ARRAY_VERTEX] = run.position
		arrays[Mesh.ARRAY_TEX_UV] = run.uv
		arrays[Mesh.ARRAY_TEX_UV2] = run.band
		arrays[Mesh.ARRAY_INDEX] = run.index
		var mesh := ArrayMesh.new()
		mesh.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arrays)
		var instance := MeshInstance3D.new()
		instance.mesh = mesh
		instance.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		# Never culled: a still band's box is round the camera, a near one's round the world.
		instance.extra_cull_margin = 16384
		instance.material_override = _ring_material(texture, effect)
		holder.add_child(instance)
		meshes.append(instance)
	add_child(holder)
	return {"band": band, "holder": holder, "meshes": meshes, "still": still, "effect": effect}


## The effect layer `l`'s band is drawn through: its detail level's (drawn_effect, PlaxDetail), MIRROR as SWELL (its
## sky is the flat page's, not the ring's); "" for an image drawn plain.
static func effect_of(l: Dictionary) -> String:
	if l.model.kind != "SHADER" or l.get("drawn_effect", "") == "":
		return ""
	return "SWELL" if l.drawn_effect == "MIRROR" else l.drawn_effect


# The flat reader's numbers (PlaxEffects.apply) on an effect band's material `m`, then along() laid round the ring:
# page units (a view 1 by 1) from the image's edge in its tile, as effect_start_x and effect_start_y take it, without
# the scroll, which turns the ring; no glint (its sun is a place on the screen) and no dissolve's patches. Round the
# ring the effect runs a whole number of its periods (PlaxEffects.period), stretched a little, or it would step where
# the ring closes (three.js r58).
func _apply_effect(m: ShaderMaterial, l: Dictionary, band: Dictionary) -> void:
	var model: Dictionary = l.model
	PlaxEffects.apply(m, l, background._effect_time, 1, 1)
	var ring: float = band.repeat * (l.width + model.padX)
	var period := PlaxEffects.period(model)
	var stretch := maxf(1, roundf(ring / period)) * period / ring if period > 0 and ring > 0 else 1.0
	var trim_x: float = (1 - l.trim_left) if model.flipX else l.trim_left
	var trim_y: float = (1 - l.trim_bottom) if model.flipY else l.trim_bottom
	m.set_shader_parameter("across", Vector3(stretch, l.width * trim_x * stretch, -1.0 if model.flipX else 1.0))
	m.set_shader_parameter("up", Vector3(1, l.height * trim_y, -1.0 if model.flipY else 1.0))
	m.set_shader_parameter("glint", Vector4())
	m.set_shader_parameter("page_height", l.height)


func _drop_band(id: int) -> void:
	var built: Dictionary = _built[id]
	remove_child(built.holder)
	built.holder.free()
	_built.erase(id)


# A still band and nothing else follows the camera: round it, at 0.85 of its far plane.
func _follow_camera() -> void:
	var camera := get_viewport().get_camera_3d() if is_inside_tree() else null
	if camera == null:
		return
	for built: Dictionary in _built.values():
		if built.still:
			built.holder.global_position = camera.global_position
			built.holder.scale = Vector3.ONE * camera.far * 0.85


# The page's gradients on the sky, by elevation, the fog under the horizon.
func _draw_sky() -> void:
	var bg := background
	var page := bg.page
	if page == null:
		return
	var g := gradient(bg._top_half_size, bg._bottom_half_size, bg.world_width, ring_options_of(page))
	var names := ["top_to", "top_from", "bottom_to", "bottom_from"]
	for i in 4:
		var c := PlaxEffects.packed_rgb(_sky_tinted(bg._gradient[i]))
		sky_material.set_shader_parameter(names[i], Vector3(c.r, c.g, c.b))
	sky_material.set_shader_parameter("top", Vector2(g.topFrom, g.topTo))
	sky_material.set_shader_parameter("bottom", Vector2(g.bottomFrom, g.bottomTo))
	sky_material.set_shader_parameter("fog_color", _fog_color(page))
	sky_material.set_shader_parameter("horizon", float(options.horizon))


# The fog's colour (options.fogColor, else the page's), tinted with tintSky, as a Vector3.
func _fog_color(page: PlaxPage) -> Vector3:
	var c := _sky_tinted(options.get("fogColor", page.fog_color))
	return Vector3(c.r, c.g, c.b)


# Colour `c`, times the background's tint with tintSky, opaque.
func _sky_tinted(c: Color) -> Color:
	var t := background.tint
	return Color(c.r * t.r, c.g * t.g, c.b * t.b) if options.tintSky else Color(c.r, c.g, c.b)


# A band's material on `texture`: the page's colours mixed as the flat reader mixes them, then handed over as they are
# (Compatibility, whose output is sRGB) or as linear (Forward+ and Mobile convert it back);
# blended as every layer is, behind the 3D world (it tests its depth and writes none). A Nearest page (a CanvasTexture,
# PlaxAtlas._load_texture) is sampled sharp. With `effect` (effect_of), that effect's shader (_ring_effect_code).
func _ring_material(texture: Texture2D, effect := "") -> ShaderMaterial:
	var nearest: bool = texture.get_meta("plax_nearest", false)
	if texture is CanvasTexture:
		texture = texture.diffuse_texture
	var filter := "filter_nearest" if nearest else "filter_linear_mipmap"
	var key := filter + effect
	if not _ring_shaders.has(key):
		var shader := Shader.new()
		shader.code = _ring_effect_code(effect, filter) if effect != "" else RING_SHADER.replace("FILTER", filter)
		_ring_shaders[key] = shader
	var m := ShaderMaterial.new()
	m.shader = _ring_shaders[key]
	m.set_shader_parameter("ring_tex" if effect != "" else "tex", texture)
	return m


# Effect `effect`'s canvas_item shader (PlaxEffects.code) as a band's spatial one: RING_EFFECT_HEAD for its first line,
# the tint a uniform, RING_EFFECT_VERTEX for its vertex(), ring_screen for SCREEN_UV, ring_tex for TEXTURE, the haze
# melted (RING_EFFECT_HAZE) and COLOR handed over as ALBEDO and ALPHA. A swap that finds nothing is an error: the flat
# shader changed under it.
static func _ring_effect_code(effect: String, filter: String) -> String:
	var code := PlaxEffects.code(effect)
	for swap in [
		["shader_type canvas_item;", RING_EFFECT_HEAD.replace("FILTER", filter)],
		["varying vec4 tint;", "uniform vec4 tint = vec4(1.0);"],
		["void vertex() {\n\ttint = COLOR;\n}", RING_EFFECT_VERTEX],
		["vec4 hazed(", RING_EFFECT_HAZE + "vec4 hazed("],
		["mix(color.rgb, fog_color, haze)", "mix(color.rgb, fog_color, ring_haze())"],
		["SCREEN_UV", "ring_screen"],
		["TEXTURE", "ring_tex"],
		["\tCOLOR = ", "\tvec4 ring_out = "],
	]:
		if not code.contains(swap[0]):
			push_error("PlaxRings: no '%s' in %s's shader" % [swap[0], effect])
		code = code.replace(swap[0], swap[1])
	var end := code.rfind("}")
	return code.substr(0, end) + "\tALBEDO = to_output(ring_out.rgb, OUTPUT_IS_SRGB);\n\tALPHA = ring_out.a;\n}\n"


static func _sky_shader_of() -> Shader:
	if _sky_shader == null:
		_sky_shader = Shader.new()
		_sky_shader.code = SKY_SHADER
	return _sky_shader
