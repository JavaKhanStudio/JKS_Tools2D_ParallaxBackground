extends Node
## The Godot twin of the editor repository's demo/ (ParallaxDemo): Hiver and Printemps cross-faded on demand, through the reader.
## SPACE winter/spring, N night tint, LEFT / RIGHT scroll, R reset. A page given after "--" is shown alone:
##   godot --path engines/godot -- /abs/path/page.jplax [/abs/path/atlas_dir]

const TRANSFER_SECONDS := 3.0
const MANUAL_SPEED := 400.0
const NIGHT_TINT := Color(0.45, 0.5, 0.85)

var bg := PlaxBackground.new()
var hud := Label.new()
var winter: PlaxPage
var spring: PlaxPage
var winter_atlas: PlaxAtlas
var spring_atlas: PlaxAtlas
var showing_winter := true
var night := false


func _ready() -> void:
	var args := OS.get_cmdline_user_args()
	bg.speed_constant_x = 60
	if args.size() > 0 and args[0].get_extension() in ["jplax", "plaxpj"]:
		bg.load_page(args[0], args[1] if args.size() > 1 else "")
	else:
		var assets := ProjectSettings.globalize_path("res://").path_join("../../core/test-data/samples")
		winter = PlaxPage.load_page(assets.path_join("hiver/Hiver.plaxpj"))
		spring = PlaxPage.load_page(assets.path_join("printemps/Printemps.plaxpj"))
		winter_atlas = PlaxAtlas.load_atlas(assets.path_join(winter.atlas_name))
		spring_atlas = PlaxAtlas.load_atlas(assets.path_join(spring.atlas_name))
		bg.set_page(winter, winter_atlas)
	add_child(bg)
	hud.position = Vector2(10, 6)
	add_child(hud)


func _unhandled_key_input(event: InputEvent) -> void:
	if not event.is_pressed() or event.is_echo() or winter == null:
		return
	match event.keycode:
		KEY_SPACE:
			showing_winter = not showing_winter
			bg.transfert_into(winter if showing_winter else spring,
				winter_atlas if showing_winter else spring_atlas, TRANSFER_SECONDS)
		KEY_N:
			night = not night
			bg.tint_to(NIGHT_TINT if night else Color.WHITE, TRANSFER_SECONDS)
		KEY_R:
			bg.reset_positions()


func _process(_delta: float) -> void:
	if Input.is_key_pressed(KEY_LEFT):
		bg.speed_consumable_x = -MANUAL_SPEED
	elif Input.is_key_pressed(KEY_RIGHT):
		bg.speed_consumable_x = MANUAL_SPEED
	hud.text = "SPACE: winter/spring   N: night tint   LEFT/RIGHT: scroll   R: reset   %d fps" \
		% Engine.get_frames_per_second()
