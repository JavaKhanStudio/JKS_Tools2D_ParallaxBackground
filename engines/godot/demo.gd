extends Node
## Shows a page scrolling. LEFT / RIGHT scroll by hand; the page is the first argument after "--", or Hiver:
##   godot --path engines/godot -- /abs/path/page.jplax [/abs/path/atlas_dir]

func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	var bg := PlaxBackground.new()
	bg.speed_constant_x = 60
	if args.size() > 0:
		bg.load_page(args[0], args[1] if args.size() > 1 else "")
	else:
		bg.load_page(ProjectSettings.globalize_path("res://").path_join("../../demo/lab/round1/s03.jplax"),
			ProjectSettings.globalize_path("res://").path_join("../../demo/assets"))
	add_child(bg)
	set_meta("bg", bg)


func _process(_delta: float) -> void:
	var bg: PlaxBackground = get_meta("bg")
	if Input.is_key_pressed(KEY_LEFT):
		bg.speed_consumable_x = -400
	elif Input.is_key_pressed(KEY_RIGHT):
		bg.speed_consumable_x = 400
