class_name PlaxPage
extends RefCounted
## A saved page: the JSON export (.jplax) or the editor project (.plaxpj), read field by field as
## core/src/.../pages/Utils_Page_Json does. A field missing from the file keeps its default. The Kryo export (.plax)
## is not read here: its class ids only mean something to the JVM library.

const LAYER_DEFAULTS := {
	"regionName": "", "regionPosition": 0, "flipX": false, "flipY": false,
	"parallaxScalingSpeedX": 0.0, "parallaxScalingSpeedY": 0.0, "speedXAtRest": 0.0, "sizeRatio": 1.0,
	"decal_X_Ratio": 0.0, "decal_Y_Ratio": 0.0, "padX": 0.0, "padXFactor": 0.0, "padY": 0.0, "padYFactor": 0.0,
	"mirror": false, "kind": "IMAGE", "name": "",
	"particlesLibgdx": "", "particlesGodot": "", "particlesAnchor": "LAYER",
	"shaderEffect": "WAVE", "shaderAmplitude": 0.0, "shaderWavelength": 0.0, "shaderSpeed": 0.0, "shaderHaze": 0.0,
	"sequenceSegments": [], "sequenceSeed": 0, "sequenceLength": 16,
}
## The layer kinds this reader knows (Enum_LayerKind): a page naming another fails to load, as in libGDX. A PARTICLES
## layer's particlesGodot scene is instanced by PlaxBackground, a SHADER layer drawn through PlaxEffects, a SEQUENCE
## layer's cycle drawn by draw_cycle.
const KINDS := ["IMAGE", "EMPTY", "PARTICLES", "SHADER", "SEQUENCE"]
## What a seed of 0 starts from (SequenceCycle.ZERO_SEED): xorshift never leaves 0.
const ZERO_SEED := 0x6D2B79F5
const _U32 := 0xFFFFFFFF
## Where a PARTICLES layer's effect sits (Enum_ParticleAnchor).
const ANCHORS := ["LAYER", "VIEW"]

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
## The depth fog (WholePage_Model.getFogStrength, format 10): 0 or more; PlaxEffects.fog_of mixes each layer toward
## fog_color by it, more the slower the layer. Read from a page saved before format 10, it is the largest FOG layer's
## shaderHaze.
var fog_strength := 0.0
var fog_color := Color(0.93, 0.95, 0.97)
var atlas_name := ""
## Back to front. Each is a Dictionary with the keys of Parallax_Model (regionName, regionPosition, flipX, ...).
var layers: Array[Dictionary] = []


static func load_page(path: String) -> PlaxPage:
	var json = JSON.parse_string(FileAccess.get_file_as_string(path))
	if typeof(json) != TYPE_DICTIONARY:
		push_error("PlaxPage: not a page: " + path)
		return null
	var page := PlaxPage.new()
	# A project holds its page under "saving", with a flag per layer telling whether it comes from the atlas.
	var ok := page._read(json.saving, json.saving.get("inside")) if json.has("saving") else page._read(json, null)
	return page if ok else null


## False when a layer is of a kind this reader does not know.
func _read(json: Dictionary, inside) -> bool:
	top_half_top = _color(json.get("topHalf_top"), top_half_top)
	top_half_bottom = _color(json.get("topHalf_bottom"), top_half_bottom)
	bottom_half_top = _color(json.get("bottomHalf_top"), bottom_half_top)
	bottom_half_bottom = _color(json.get("bottomHalf_bottom"), bottom_half_bottom)
	top_half_size = float(json.get("topHalfSize", top_half_size))
	bottom_half_size = float(json.get("bottomHalfSize", bottom_half_size))
	repeat_on_x = bool(json.get("repeatOnX", repeat_on_x))
	repeat_on_y = bool(json.get("repeatOnY", repeat_on_y))
	use_original_size = bool(json.get("useOriginalSize", use_original_size))
	fog_color = _color(json.get("fogColor"), fog_color)
	if json.has("fogStrength") and json.fogStrength != null:
		fog_strength = maxf(0.0, float(json.fogStrength))
	var page_model = json.get("pageModel")
	if typeof(page_model) != TYPE_DICTIONARY:
		return true
	atlas_name = str(page_model.get("atlasName", atlas_name))
	var list = page_model.get("pageList")
	if typeof(list) != TYPE_ARRAY:
		return true
	for i in list.size():
		# A project layer without a flag comes from the atlas, as the editor reads it.
		if typeof(inside) == TYPE_ARRAY and i < inside.size() and not inside[i]:
			continue
		var layer := LAYER_DEFAULTS.duplicate(true)
		for key in LAYER_DEFAULTS:
			if list[i].has(key) and list[i][key] != null:
				layer[key] = list[i][key]
		layer.regionPosition = int(layer.regionPosition)
		# JSON numbers are floats here: a seed is read back to the int Java stored.
		layer.sequenceSeed = int(layer.sequenceSeed)
		layer.sequenceLength = int(layer.sequenceLength)
		for segment in layer.sequenceSegments:
			segment.regionPosition = int(segment.get("regionPosition", 0))
			segment.weight = int(segment.get("weight", 1)) if segment.get("weight") != null else 1
		if not layer.kind in KINDS:
			push_error("PlaxPage: layer kind %s is not known here: the page was written by a newer version" % layer.kind)
			return false
		if not layer.particlesAnchor in ANCHORS:
			push_error("PlaxPage: particle anchor %s is not known here: the page was written by a newer version" % layer.particlesAnchor)
			return false
		if not layer.shaderEffect in PlaxEffects.EFFECTS:
			push_error("PlaxPage: shader effect %s is not known here: the page was written by a newer version" % layer.shaderEffect)
			return false
		layers.append(layer)
	if not json.has("fogStrength"):
		for layer in layers:
			if layer.kind == "SHADER" and layer.shaderEffect == "FOG" and float(layer.shaderHaze) > fog_strength:
				fog_strength = minf(1.0, layer.shaderHaze)
	return true


## SequenceCycle.next: the 32-bit xorshift step (13, 17, 5) of `state`, both unsigned 32-bit ints (0 .. 2^32 - 1).
## GDScript's ints are 64-bit: every left shift is masked back to 32 bits, and a right shift of an unsigned value is
## Java's >>>.
static func sequence_next(state: int) -> int:
	state ^= (state << 13) & _U32
	state ^= state >> 17
	state ^= (state << 5) & _U32
	return state


## SequenceCycle.draw, bit for bit: `length` segment indexes picked from `seed` (Java's int: negative or not) and
## `weights`, one per segment. For each slot the state steps once and (state >>> 1) % total walks the weights; a weight
## of 0 or less is never picked, and when none is above 0 each weighs 1. Integers only: a float would pick otherwise.
static func draw_cycle(seed: int, weights: PackedInt32Array, length: int) -> PackedInt32Array:
	var total := 0
	for weight in weights:
		if weight > 0:
			total += weight
	var state := seed & _U32
	if state == 0:
		state = ZERO_SEED
	var cycle := PackedInt32Array()
	cycle.resize(maxi(1, length))
	for slot in cycle.size():
		state = sequence_next(state)
		var pick := (state >> 1) % (total if total > 0 else weights.size())
		var segment := 0
		while segment < weights.size() - 1:
			var weight := maxi(weights[segment], 0) if total > 0 else 1
			if pick < weight:
				break
			pick -= weight
			segment += 1
		cycle[slot] = segment
	return cycle


static func _color(json, fallback: Color) -> Color:
	if typeof(json) != TYPE_DICTIONARY:
		return fallback
	return Color(json.get("r", 0.0), json.get("g", 0.0), json.get("b", 0.0), json.get("a", 0.0))
