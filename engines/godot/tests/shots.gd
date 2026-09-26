extends Node
## Stills of every scene of a lab round, as demo/.../ParallaxLab --shots takes them with libGDX: the same page, the
## same 60 units/s scroll stepped at 1/60 s, grabbed 0, 6 and 12 s in. tools/godot-parallax-shots.sh runs it and
## compares the two sets. A scene's "transfer" (one, or a list) and "tint" start a cross-fade or a tint at the same step as the lab,
## its "speedY" scrolls it up too, and its "resize" resizes the window mid-scroll.
##   godot --path engines/godot res://tests/shots.tscn -- <repo root> <round dir> <out dir>

const SPEED := 60.0
const SHOT_TIMES := [0, 6, 12]


func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	var root: String = args[0]
	var round_dir: String = root.path_join(args[1])
	var out: String = args[2]
	DirAccess.make_dir_recursive_absolute(out)
	var round = JSON.parse_string(FileAccess.get_file_as_string(round_dir.path_join("round.json")))
	var bg := PlaxBackground.new()
	bg.auto_act = false
	add_child(bg)
	var step := _f32(1.0 / 60.0)
	for scene in round.scenes:
		await _resize_window(Vector2i(1280, 720))
		bg.load_page(root.path_join(scene.page), root.path_join(scene.atlasDir))
		bg.tint_to(Color.WHITE, 0)
		bg.speed_constant_x = SPEED
		bg.speed_constant_y = scene.get("speedY", 0.0)
		var resize: Dictionary = scene.get("resize", {})
		# One transfer, or a list of them started one after the other.
		var transfers = scene.get("transfer", [])
		transfers = [transfers] if transfers is Dictionary else transfers.duplicate()
		var tint: Dictionary = scene.get("tint", {})
		# The libGDX loop adds a float step to a float time: count its steps the same way, in float32.
		var time := PackedFloat32Array([0.0])
		for at in SHOT_TIMES:
			while time[0] < at:
				while not transfers.is_empty() and time[0] >= transfers[0].at:
					_transfer(bg, root, scene, transfers.pop_front())
				if not resize.is_empty() and time[0] >= resize.at:
					await _resize_window(Vector2i(resize.size[0], resize.size[1]))
					resize = {}
				if not tint.is_empty() and time[0] >= tint.at:
					var c: Array = tint.color
					bg.tint_to(Color(c[0], c[1], c[2], c[3]), tint.seconds)
					tint = {}
				bg.act(step)
				time[0] = time[0] + step
			await RenderingServer.frame_post_draw
			await RenderingServer.frame_post_draw
			var image := get_viewport().get_texture().get_image()
			image.save_png(out.path_join("%s-t%d.png" % [scene.id, at]))
		print("shots: ", scene.id)
	get_tree().quit()


## Resizes the window, and waits until the viewport has the new size (PlaxBackground follows it on size_changed).
func _resize_window(size: Vector2i) -> void:
	if get_window().size == size:
		return
	get_window().size = size
	for i in 300:
		await RenderingServer.frame_post_draw
		if Vector2i(get_viewport().get_visible_rect().size) == size:
			return
	push_error("shots: the window stayed %s, not %s" % [get_viewport().get_visible_rect().size, size])


## Into the scene's transfer page, or into the page on screen when it names none.
static func _transfer(bg: PlaxBackground, root: String, scene: Dictionary, transfer: Dictionary) -> void:
	if not transfer.has("page"):
		bg.transfert_into(bg.page, bg.atlas, transfer.seconds)
		return
	var page := PlaxPage.load_page(root.path_join(transfer.page))
	var atlas_dir: String = root.path_join(transfer.get("atlasDir", scene.atlasDir))
	bg.transfert_into(page, PlaxAtlas.load_atlas(atlas_dir.path_join(page.atlas_name)), transfer.seconds)


static func _f32(value: float) -> float:
	return PackedFloat32Array([value])[0]
