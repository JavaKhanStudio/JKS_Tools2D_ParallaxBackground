extends Node
## Stills of every scene of a lab round, as demo/.../ParallaxLab --shots takes them with libGDX: the same page, the
## same 60 units/s scroll stepped at 1/60 s, grabbed 0, 6 and 12 s in. tools/godot-parallax-shots.sh runs it and
## compares the two sets.
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
		bg.load_page(root.path_join(scene.page), root.path_join(scene.atlasDir))
		bg.speed_constant_x = SPEED
		# The libGDX loop adds a float step to a float time: count its steps the same way, in float32.
		var time := PackedFloat32Array([0.0])
		for at in SHOT_TIMES:
			while time[0] < at:
				bg.act(step)
				time[0] = time[0] + step
			await RenderingServer.frame_post_draw
			await RenderingServer.frame_post_draw
			var image := get_viewport().get_texture().get_image()
			image.save_png(out.path_join("%s-t%d.png" % [scene.id, at]))
		print("shots: ", scene.id)
	get_tree().quit()


static func _f32(value: float) -> float:
	return PackedFloat32Array([value])[0]
