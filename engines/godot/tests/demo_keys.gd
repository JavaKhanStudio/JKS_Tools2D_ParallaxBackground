extends Node
## Presses SPACE and N in the demo (demo.gd) and saves a frame once both fades are over: Printemps, tinted night.
## With "mid" after the file, presses SPACE alone and saves the frame halfway through the transfert, in the demo's style.
##   godot --path engines/godot res://tests/demo_keys.tscn -- /abs/out.png [mid]   (tools/start-demo-check.sh)

func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	var mid := args.size() > 1 and args[1] == "mid"
	var demo: Node = load("res://demo.gd").new()
	add_child(demo)
	await get_tree().create_timer(0.5).timeout
	for key in [KEY_SPACE] if mid else [KEY_SPACE, KEY_N]:
		var ev := InputEventKey.new()
		ev.keycode = key
		ev.pressed = true
		Input.parse_input_event(ev)
	await get_tree().create_timer(1.5 if mid else 3.5).timeout
	await RenderingServer.frame_post_draw
	get_viewport().get_texture().get_image().save_png(args[0])
	get_tree().quit()
