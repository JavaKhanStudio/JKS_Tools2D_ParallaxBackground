class_name PlaxPage
extends RefCounted
## A saved page: the JSON export (.jplax) or the editor project (.plaxpj), read field by field as
## core/src/.../pages/Utils_Page_Json does. A field missing from the file keeps its default. The Kryo export (.plax)
## is not read here: its class ids only mean something to the JVM library.

var top_half_top := Color.WHITE
var top_half_bottom := Color.WHITE
var bottom_half_top := Color.WHITE
var bottom_half_bottom := Color.WHITE
## The part of the screen each gradient leaves UNCOVERED: 0 fills the screen, 0.5 half of it, 1 nothing.
var top_half_size := 0.5
var bottom_half_size := 0.5
var repeat_on_x := true
var repeat_on_y := false
var use_original_size := false
var atlas_name := ""
## Back to front. Each is a Dictionary with the keys of Parallax_Model (regionName, regionPosition, flipX, ...).
var layers: Array[Dictionary] = []

const LAYER_DEFAULTS := {
	"regionName": "", "regionPosition": 0, "flipX": false, "flipY": false,
	"parallaxScalingSpeedX": 0.0, "parallaxScalingSpeedY": 0.0, "speedXAtRest": 0.0, "sizeRatio": 1.0,
	"decal_X_Ratio": 0.0, "decal_Y_Ratio": 0.0, "padX": 0.0, "padXFactor": 0.0, "padY": 0.0, "padYFactor": 0.0,
	"mirror": false,
}


static func load_page(path: String) -> PlaxPage:
	var json = JSON.parse_string(FileAccess.get_file_as_string(path))
	if typeof(json) != TYPE_DICTIONARY:
		push_error("PlaxPage: not a page: " + path)
		return null
	var page := PlaxPage.new()
	# A project holds its page under "saving", with a flag per layer telling whether it comes from the atlas.
	if json.has("saving"):
		page._read(json.saving, json.saving.get("inside"))
	else:
		page._read(json, null)
	return page


func _read(json: Dictionary, inside) -> void:
	top_half_top = _color(json.get("topHalf_top"), top_half_top)
	top_half_bottom = _color(json.get("topHalf_bottom"), top_half_bottom)
	bottom_half_top = _color(json.get("bottomHalf_top"), bottom_half_top)
	bottom_half_bottom = _color(json.get("bottomHalf_bottom"), bottom_half_bottom)
	top_half_size = float(json.get("topHalfSize", top_half_size))
	bottom_half_size = float(json.get("bottomHalfSize", bottom_half_size))
	repeat_on_x = bool(json.get("repeatOnX", repeat_on_x))
	repeat_on_y = bool(json.get("repeatOnY", repeat_on_y))
	use_original_size = bool(json.get("useOriginalSize", use_original_size))
	var page_model = json.get("pageModel")
	if typeof(page_model) != TYPE_DICTIONARY:
		return
	atlas_name = str(page_model.get("atlasName", atlas_name))
	var list = page_model.get("pageList")
	if typeof(list) != TYPE_ARRAY:
		return
	for i in list.size():
		# A project layer without a flag comes from the atlas, as the editor reads it.
		if typeof(inside) == TYPE_ARRAY and i < inside.size() and not inside[i]:
			continue
		var layer := LAYER_DEFAULTS.duplicate()
		for key in LAYER_DEFAULTS:
			if list[i].has(key) and list[i][key] != null:
				layer[key] = list[i][key]
		layer.regionPosition = int(layer.regionPosition)
		layers.append(layer)


static func _color(json, fallback: Color) -> Color:
	if typeof(json) != TYPE_DICTIONARY:
		return fallback
	return Color(json.get("r", 0.0), json.get("g", 0.0), json.get("b", 0.0), json.get("a", 0.0))
