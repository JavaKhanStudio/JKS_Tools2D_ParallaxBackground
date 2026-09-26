extends Node
## Presses SPACE and N in the demo (demo.gd) and saves a frame once both fades are over: Printemps, tinted night.
##   godot --path engines/godot res://tests/demo_keys.tscn -- /abs/out.png   (tools/start-demo-check.sh)

func _ready() -> void:
	var demo: Node = load("res://demo.gd").new()
	add_child(demo)
	await get_tree().create_timer(0.5).timeout
	for key in [KEY_SPACE, KEY_N]:
		var ev := InputEventKey.new()
		ev.keycode = key
		ev.pressed = true
		Input.parse_input_event(ev)
	await get_tree().create_timer(3.5).timeout
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png(OS.get_cmdline_user_args()[0])
	get_tree().quit()
