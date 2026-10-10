extends Node
## The Godot twin of the editor repository's demo/ (ParallaxDemo): Hiver and Printemps cross-faded on demand, through the reader.
## SPACE winter/spring, T transfert style (depth stagger, fade, dissolve, through white, fog creep), N night tint,
## LEFT / RIGHT scroll, R reset. A page given after "--" is shown alone:
##   godot --path engines/godot -- /abs/path/page.jplax [/abs/path/atlas_dir]

const TRANSFER_SECONDS := 3.0
const MANUAL_SPEED := 400.0
const NIGHT_TINT := Color(0.45, 0.5, 0.85)
## What SPACE switches through, T picks (PlaxTransfertStyle), at the transfert lab's opening values: the back layers
## change first, then the front ones; every layer at once; in patches; through white; under a mist.
const STYLE_NAMES := ["depth stagger", "fade", "dissolve", "through white", "fog creep"]

var bg := PlaxBackground.new()
var hud := Label.new()
var winter: PlaxPage
var spring: PlaxPage
var winter_atlas: PlaxAtlas
var spring_atlas: PlaxAtlas
var showing_winter := true
var night := false
var styles: Array[PlaxTransfertStyle] = [PlaxTransfertStyle.depth_stagger(1), PlaxTransfertStyle.fade(),
	PlaxTransfertStyle.dissolve(5, 0.08, 0.5), PlaxTransfertStyle.through_color(Color.WHITE, 0.5),
	PlaxTransfertStyle.fog_creep(Color(0.93, 0.95, 0.97), 0.5)]
var style_index := 0


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
	# Outlined: a transfert through white or into a mist turns the whole screen near white.
	hud.add_theme_color_override("font_outline_color", Color.BLACK)
	hud.add_theme_constant_override("outline_size", 4)
	add_child(hud)


func _unhandled_key_input(event: InputEvent) -> void:
	if not event.is_pressed() or event.is_echo() or winter == null:
		return
	match event.keycode:
		KEY_SPACE:
			showing_winter = not showing_winter
			bg.transfert_into(winter if showing_winter else spring,
				winter_atlas if showing_winter else spring_atlas, TRANSFER_SECONDS, styles[style_index])
		KEY_T:
			style_index = (style_index + 1) % styles.size()
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
	hud.text = "SPACE: winter/spring   T: style (%s)   N: night tint   LEFT/RIGHT: scroll   R: reset   %d fps" \
		% [STYLE_NAMES[style_index], Engine.get_frames_per_second()]
