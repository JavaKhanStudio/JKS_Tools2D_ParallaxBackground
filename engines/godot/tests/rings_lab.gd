extends Control
## The Godot rings lab (parallax-readers r49): a page of the rings round (engines/rounds/rings) as the sky of a small 3D
## world, drawn by PlaxRings over one PlaxBackground, as three.js's (engines/threejs/tests/rings_lab.js): a valley, a
## snowy ridge round it at about the page's foot, a camera flying round the valley. Panel A stands the bands at the
## library's depths (front / speed), panel B compresses them into front..far, as the saga's sky does; click a panel to
## give it the whole stage, again to go back. Sliders for every option of PlaxRings, the world's fog and the flight;
## Copy settings puts them as JSON on the clipboard and in the box under it. engines/godot/tools/rings-lab.sh opens it,
## or shoots it in cage:
##   godot --path engines/godot res://tests/rings_lab.tscn -- <repo root> [--shot DIR] [scene=g02] [view=b] [front=510]
##       [round=engines/threejs/tests/rings-seam]  another round's scenes (its first scene, and its only one shot)
## --shot: DIR/<scene>-<view>-t<s>.png for each scene of scenes= (default g02,g01,g04), view both, at 0, 8 and 20 s of
## the flight, and for a page with a SHADER layer DIR/<scene>-join-t8.png, panel B turned to where its band closes
## its ring; then the last scene cross-fading into the next, half way and done; "rings_lab: PASS" when all are saved.

const ROUND := "engines/rounds/rings"
const VIEWS := [["both", "A | B"], ["a", "A: library depth"], ["b", "B: compressed to far"]]
## key, label, min, max, step, section. Their start values: DEFAULTS (Simon's look on r48, 26 Oct: a low flight, the
## bands compressed into 510..3000, 28 degrees of page, the eye melt), the page's fog strength, then the command line's.
const SLIDERS := [
	["front", "front", 50, 800, 5, "Rings"], ["far", "far (B)", 100, 3000, 10, "Rings"],
	["foot", "foot", 0, 120, 1, "Rings"], ["lift", "lift", -12, 12, 0.5, "Rings"],
	["scale", "scale", 0.2, 2, 0.05, "Rings"], ["pageDegrees", "page degrees", 10, 90, 1, "Rings"],
	["fogStrength", "fog strength", 0, 0.2, 0.002, "Fog"], ["melt", "melt", 0, 0.6, 0.01, "Fog"],
	["eyeMelt", "eye melt", 0, 0.8, 0.01, "Fog"], ["horizon", "horizon", 0, 0.5, 0.01, "Fog"],
	["worldFog", "world fog far", 200, 3000, 10, "Fog"],
	["dusk", "dusk tint", 0, 1, 0.01, "World"], ["tintSky", "tint sky (0/1)", 0, 1, 1, "World"],
	["orbit", "flight radius", 20, 240, 5, "World"], ["height", "flight height", 5, 120, 1, "World"],
	["speed", "flight speed", 0, 3, 0.05, "World"], ["look", "look (deg)", -180, 180, 1, "World"],
]
const DEFAULTS := {"front": 510.0, "far": 3000.0, "foot": 51.0, "lift": -2.0, "scale": 1.0, "pageDegrees": 28.0,
	"fogStrength": 0.0, "melt": 0.0, "eyeMelt": 0.3, "horizon": 0.5, "worldFog": 200.0, "dusk": 0.0, "tintSky": 0.0,
	"orbit": 60.0, "height": 10.0, "speed": 0.4, "look": 0.0}
const NIGHT := Color(0x30 / 255.0, 0x38 / 255.0, 0x6e / 255.0)
const CONTROLS_W := 380

var root := ""
var scenes: Array = []
var current: Dictionary = {}
var knobs := DEFAULTS.duplicate()
var opened := DEFAULTS.duplicate()
var view := "b"
var zoomed := ""
var flight := 0.0
var playing := true
var bg := PlaxBackground.new()
# Per panel ("a", "b"): its container, viewport, camera, environment, rings and tag.
var panels := {}
var given := {}

var _stage := Control.new()
var _sliders := VBoxContainer.new()
var _views := HBoxContainer.new()
var _scenes := HFlowContainer.new()
var _about := Label.new()
var _numbers := Label.new()
var _settings := TextEdit.new()
var _play := Button.new()
var _copy := Button.new()


func _exit_tree() -> void:
	# Out of the tree, the background is freed by no one else.
	for p in panels.values():
		p.rings.clear()
	bg.free()


func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	root = args[0] if not args.is_empty() else ProjectSettings.globalize_path("res://").path_join("../..")
	var shot_dir := ""
	var i := 1
	while i < args.size():
		if args[i] == "--shot":
			shot_dir = args[i + 1]
			i += 2
			continue
		var kv: PackedStringArray = args[i].split("=", true, 1)
		if kv.size() == 2:
			given[kv[0]] = kv[1]
		i += 1
	view = given.get("view", "b")
	bg.auto_act = false
	# round=: another round's folder, from the repository root (engines/threejs/tests/rings-seam, r58's join fixture)
	var round_dir: String = given.get("round", ROUND)
	scenes = JSON.parse_string(FileAccess.get_file_as_string(root.path_join(round_dir).path_join("round.json"))).scenes
	_build_ui()
	for name in ["a", "b"]:
		panels[name] = _panel(name)
	_load_scene(given.get("scene", scenes[0].id))
	_layout()
	resized.connect(_layout)
	if shot_dir != "":
		_shoot.call_deferred(shot_dir)


# --- the world -------------------------------------------------------------------------------------------------------

## One panel: a viewport of its own world (the valley, a sun, the camera, the environment), its rings over the shared
## background.
func _panel(name: String) -> Dictionary:
	var box := SubViewportContainer.new()
	box.stretch = true
	box.mouse_filter = Control.MOUSE_FILTER_PASS
	box.gui_input.connect(_on_panel_input.bind(name))
	var port := SubViewport.new()
	port.own_world_3d = true
	port.msaa_3d = Viewport.MSAA_4X
	box.add_child(port)
	_stage.add_child(box)
	port.add_child(_valley())
	var sun := DirectionalLight3D.new()
	sun.light_color = Color("fff2dd")
	sun.light_energy = 1.4
	port.add_child(sun)
	sun.look_at_from_position(Vector3(-300, 400, -200), Vector3.ZERO)
	var camera := Camera3D.new()
	camera.fov = 60
	camera.near = 0.5
	camera.far = 20000
	port.add_child(camera)
	var environment := Environment.new()
	environment.ambient_light_source = Environment.AMBIENT_SOURCE_COLOR
	environment.ambient_light_color = Color("dfe8ff")
	environment.ambient_light_energy = 0.7
	environment.fog_enabled = true
	environment.fog_mode = Environment.FOG_MODE_DEPTH
	environment.fog_sky_affect = 0.0
	var world := WorldEnvironment.new()
	world.environment = environment
	port.add_child(world)
	var rings := PlaxRings.new(bg)
	rings.auto_update = false
	port.add_child(rings)
	rings.apply_sky(environment)
	var tag := Label.new()
	tag.add_theme_color_override("font_color", Color.WHITE)
	tag.add_theme_color_override("font_shadow_color", Color.BLACK)
	tag.position = Vector2(10, 8)
	box.add_child(tag)
	return {"box": box, "port": port, "camera": camera, "environment": environment, "rings": rings, "tag": tag}


## A valley 300 units across, its floor rolling, a ridge round it peaking near 50 (the rings' foot), snow on top
## (three.js's valley(), point for point).
func _valley() -> MeshInstance3D:
	const RINGS := 70
	const AROUND := 256
	const R := 300.0
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	for i in RINGS + 1:
		var r := R * i / RINGS
		for j in AROUND + 1:
			var a := TAU * j / AROUND
			var noise := sin(a * 7 + 1.3) * 0.5 + sin(a * 13 + r * 0.02) * 0.3 + sin(a * 29 + 2.1) * 0.2
			var ridge := smoothstep(150.0, 265.0, r) * (40 + 12 * noise)
			var ground := 2.5 * sin(r * 0.05 + a * 3) * cos(a * 5 - r * 0.03)
			var y := maxf(-1, ridge + ground)
			st.set_color(Color(0.92, 0.94, 0.97) if y > 42 else Color(0.45, 0.43, 0.4) if y > 22 else Color(0.32, 0.45, 0.22))
			st.add_vertex(Vector3(r * sin(a), y, -r * cos(a)))
	for i in RINGS:
		for j in AROUND:
			var a := i * (AROUND + 1) + j
			var c := a + AROUND + 1
			for k in [a, c, a + 1, a + 1, c, c + 1]:
				st.add_index(k)
	st.generate_normals()
	var material := StandardMaterial3D.new()
	material.vertex_color_use_as_albedo = true
	material.cull_mode = BaseMaterial3D.CULL_DISABLED
	var mesh := MeshInstance3D.new()
	mesh.mesh = st.commit()
	mesh.material_override = material
	return mesh


func _load_scene(id: String) -> void:
	current = scenes[0]
	for s in scenes:
		if s.id == id:
			current = s
	bg.load_page(root.path_join(current.page), root.path_join(current.atlasDir))
	knobs = DEFAULTS.duplicate()
	knobs.fogStrength = bg.page.fog_strength
	for key in knobs:
		if given.has(key):
			knobs[key] = float(given[key])
	opened = knobs.duplicate()
	for p in panels.values():
		p.rings.clear()
	_about.text = "%s %s: %s" % [current.id, current.name, current.about]
	_build_sliders()
	_build_chips()
	_apply()


func _apply() -> void:
	var common := {"front": knobs.front, "foot": knobs.foot, "lift": knobs.lift, "scale": knobs.scale,
		"pageDegrees": knobs.pageDegrees, "fogStrength": knobs.fogStrength, "melt": knobs.melt,
		"eyeMelt": knobs.eyeMelt, "horizon": knobs.horizon, "tintSky": knobs.tintSky > 0}
	var a := common.duplicate()
	a.far = 0.0
	var b := common.duplicate()
	b.far = knobs.far
	panels.a.rings.set_options(a)
	panels.b.rings.set_options(b)
	bg.tint_to(Color.WHITE.lerp(NIGHT, knobs.dusk), 0)
	for p in panels.values():
		p.rings.update(0)
		# the world's fog in the rings' fog colour, tinted with tint sky
		var fog: Vector3 = p.rings._fog_color(bg.page)
		p.environment.fog_light_color = Color(fog.x, fog.y, fog.z)
		p.environment.fog_depth_begin = knobs.front * 0.3
		p.environment.fog_depth_end = knobs.worldFog
	_describe()


## The bands' radii, per panel, under the controls.
func _describe() -> void:
	var lines := []
	for name in ["a", "b"]:
		var found := PlaxRings.bands(bg.layers, bg.world_width, panels[name].rings.ring_options_of(bg.page))
		var radii := found.map(func(band): return "sky" if band.radius == INF else str(roundi(band.radius)))
		lines.append("%s: %d bands, radii %s" % [name.to_upper(), found.size(), " ".join(radii)])
	_numbers.text = "\n".join(lines)


## The camera round the valley, looking ahead and out toward the ridge and the sky, turned by look degrees.
func _fly(t: float) -> void:
	var a := t * 0.12
	var r: float = knobs.orbit
	var y: float = knobs.height + 6 * sin(t * 0.3)
	var at := Vector3(r * sin(a), y, -r * cos(a))
	var heading := a + PI / 2 - 0.6 + deg_to_rad(knobs.look)
	for p in panels.values():
		p.camera.look_at_from_position(at, Vector3(at.x + sin(heading) * 100, y + 14, at.z - cos(heading) * 100))


func _process(delta: float) -> void:
	if playing:
		var dt := minf(0.1, delta)
		flight += dt * knobs.speed
		bg.act(dt)
	_draw_now()


func _draw_now() -> void:
	_fly(flight)
	for p in panels.values():
		p.rings.update(0)
	panels.a.tag.text = "A  library depth (front / speed)"
	panels.b.tag.text = "B  compressed into %d..%d" % [knobs.front, knobs.far]


# --- the controls ----------------------------------------------------------------------------------------------------

func _build_ui() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	var back := ColorRect.new()
	back.color = Color(0.1, 0.1, 0.12)
	back.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(back)
	add_child(_stage)
	var scroll := ScrollContainer.new()
	scroll.name = "Controls"
	scroll.horizontal_scroll_mode = ScrollContainer.SCROLL_MODE_DISABLED
	add_child(scroll)
	var column := VBoxContainer.new()
	column.custom_minimum_size.x = CONTROLS_W - 20
	scroll.add_child(column)
	# The view pick first, top left of the controls.
	column.add_child(_views)
	column.add_child(_scenes)
	var buttons := HBoxContainer.new()
	_play.text = "Pause"
	_play.pressed.connect(func():
		playing = not playing
		_play.text = "Pause" if playing else "Play")
	var fade := Button.new()
	fade.text = "Fade to next"
	fade.pressed.connect(func(): _fade_to_next(4.0))
	_copy.text = "Copy settings"
	_copy.pressed.connect(_copy_settings)
	for b in [_play, fade, _copy]:
		buttons.add_child(b)
	column.add_child(buttons)
	_settings.visible = false
	_settings.custom_minimum_size.y = 160
	column.add_child(_settings)
	column.add_child(_sliders)
	_about.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_about.add_theme_font_size_override("font_size", 12)
	column.add_child(_about)
	_numbers.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
	_numbers.add_theme_font_size_override("font_size", 12)
	column.add_child(_numbers)


func _layout() -> void:
	# The controls on the left, the view pick their first row; the stage right of them.
	var w := size.x - CONTROLS_W
	var controls: Control = get_node("Controls")
	controls.position = Vector2(8, 6)
	controls.size = Vector2(CONTROLS_W - 14, size.y - 12)
	_stage.position = Vector2(CONTROLS_W, 0)
	_stage.size = Vector2(w, size.y)
	var shown: Array = [zoomed] if zoomed != "" else (["a", "b"] if view == "both" else [view])
	for name in ["a", "b"]:
		var p: Dictionary = panels[name]
		var at := shown.find(name)
		p.box.visible = at >= 0
		if at < 0:
			continue
		p.box.position = Vector2(at * w / shown.size(), 0)
		p.box.size = Vector2(w / shown.size(), size.y)


func _on_panel_input(event: InputEvent, name: String) -> void:
	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		if zoomed != "":
			zoomed = ""
		elif view == "both":
			zoomed = name
		_layout()


func _chip(text: String, on: bool, pick: Callable) -> Button:
	var b := Button.new()
	b.text = text
	b.toggle_mode = true
	b.button_pressed = on
	b.pressed.connect(pick)
	return b


func _build_chips() -> void:
	for c in _views.get_children() + _scenes.get_children():
		c.queue_free()
	for v in VIEWS:
		_views.add_child(_chip(v[1], v[0] == view, func():
			view = v[0]
			zoomed = ""
			_build_chips()
			_layout()))
	for s in scenes:
		_scenes.add_child(_chip("%s %s" % [s.id, s.name], s.id == current.id, func(): _load_scene(s.id)))


func _build_sliders() -> void:
	for c in _sliders.get_children():
		c.queue_free()
	var section := ""
	var grid: GridContainer
	for spec in SLIDERS:
		if spec[5] != section:
			section = spec[5]
			var h := Label.new()
			h.text = section
			h.add_theme_font_size_override("font_size", 13)
			h.add_theme_color_override("font_color", Color(0.75, 0.8, 0.9))
			_sliders.add_child(h)
			grid = GridContainer.new()
			grid.columns = 3
			_sliders.add_child(grid)
		var key: String = spec[0]
		var label := Label.new()
		label.text = spec[1]
		label.custom_minimum_size.x = 110
		var slider := HSlider.new()
		slider.min_value = spec[2]
		slider.max_value = spec[3]
		slider.step = spec[4]
		slider.value = knobs[key]
		slider.custom_minimum_size.x = 170
		slider.size_flags_vertical = Control.SIZE_SHRINK_CENTER
		var value := Label.new()
		value.text = str(knobs[key])
		slider.value_changed.connect(func(v: float):
			knobs[key] = v
			value.text = str(v)
			_apply())
		for c in [label, slider, value]:
			grid.add_child(c)


## Every knob's value, what it opened at with this scene, and the ones moved, as JSON: on the clipboard, and selected in
## the box under the button.
func _copy_settings() -> void:
	var moved := {}
	for k in knobs:
		if knobs[k] != opened[k]:
			moved[k] = [opened[k], knobs[k]]
	var text := JSON.stringify({"scene": current.id, "view": view, "values": knobs, "opened": opened, "moved": moved},
			" ")
	DisplayServer.clipboard_set(text)
	_settings.text = text
	_settings.visible = true
	_settings.select_all()
	_copy.text = "Copied"
	get_tree().create_timer(1.5).timeout.connect(func(): _copy.text = "Copy settings")


## Cross-fades the rings into the next scene's page over `seconds` (PlaxBackground.transfert_into): each band at its
## layer's fade alpha; the sliders stay the scene's that opened.
func _fade_to_next(seconds: float) -> void:
	var next: Dictionary = scenes[(scenes.find(current) + 1) % scenes.size()]
	var page := PlaxPage.load_page(root.path_join(next.page))
	bg.transfert_into(page, PlaxAtlas.load_atlas(root.path_join(next.atlasDir).path_join(page.atlas_name)), seconds)
	current = next
	_about.text = "fading into %s %s: %s" % [next.id, next.name, next.about]
	_build_chips()


# --- the stills ------------------------------------------------------------------------------------------------------

## The flight at `t` seconds, the page's drift and fades `t` seconds in, drawn now.
func _at(t: float) -> void:
	playing = false
	bg.reset_positions()
	bg.act(t)
	flight = t * knobs.speed
	_draw_now()


## The look (degrees) that turns the camera, now, to where panel B's band of the first SHADER layer closes its ring
## (its tile repeat-1 meeting tile 0, at the holder's angle 0), as three.js's ringsLab.lookAtJoin (r58); NAN
## without one.
func _look_at_join() -> float:
	var rings: PlaxRings = panels.b.rings
	for l in bg.layers:
		if l.model.kind != "SHADER" or not rings._built.has(l.get("ring_id", -1)):
			continue
		var built: Dictionary = rings._built[l.ring_id]
		var turned: float = -built.holder.rotation.y
		var big_r: float = built.band.radius
		var a := flight * 0.12
		var r: float = knobs.orbit
		var dx := big_r * sin(turned) - r * sin(a)
		var dz := -big_r * cos(turned) + r * cos(a)
		var look := rad_to_deg(atan2(dx, -dz) - (a + PI / 2 - 0.6))
		return roundf(fposmod(look + 180, 360) - 180)
	return NAN


func _save(path: String) -> void:
	await RenderingServer.frame_post_draw
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png(path)
	print("rings_lab: ", path.get_file())


func _shoot(dir: String) -> void:
	DirAccess.make_dir_recursive_absolute(dir)
	view = given.get("view", "both")
	_layout()
	var first: String = "g02,g01,g04" if not given.has("round") else scenes[0].id
	var ids: PackedStringArray = given.get("scenes", first).split(",")
	for id in ids:
		_load_scene(id)
		for t in [0, 8, 20]:
			_at(t)
			await _save(dir.path_join("%s-%s-t%d.png" % [id, view, t]))
		# A page with a SHADER layer: panel B turned to where its band closes the ring (r58), at 8 s.
		_at(8)
		var look := _look_at_join()
		if not is_nan(look):
			knobs.look = look
			print("rings_lab: look at the join %d" % look)
			_build_sliders()
			_at(8)
			await _save(dir.path_join("%s-join-t8.png" % id))
			knobs.look = 0.0
			_build_sliders()
	_fade_to_next(4.0)
	for half in [2.0, 2.0]:
		bg.act(half)
		_draw_now()
		await _save(dir.path_join("fade-%s-%s.png" % [current.id, "half" if bg.is_in_transfer() else "done"]))
	print("rings_lab: PASS")
	get_tree().quit()
