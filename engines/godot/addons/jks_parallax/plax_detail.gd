class_name PlaxDetail
extends RefCounted
## Detail levels, for a PlaxBackground (ParallaxPageReader.setDetail, Enum_Detail, LayerDetail; r464): how much of a
## page a game draws, a player's choice of quality, never stored in a page. At each level a layer is drawn, drawn
## plain or not drawn as its rule says: from which level it is shown, its SHADER effect drawn (below: its image), its
## glint drawn, a MIRROR layer's reflection drawn (below: through SWELL, in SWELL's sun). Its state is on the layer:
## shown, drawn_effect ("" for none) and drawn_glint, set when the page is built and when the game changes a level:
##
##   bg.detail = PlaxDetail.Level.MEDIUM
##   bg.set_layer_detail("birds", {"shown": PlaxDetail.Level.HIGH})   # a flock at HIGH only
##   bg.set_layer_detail("sea", {"mirror": PlaxDetail.Level.MEDIUM})  # a sea that keeps its reflection at MEDIUM

## Enum_Detail, lowest first.
enum Level { OFF, MEDIUM, HIGH }

## LayerDetail.DEFAULT: shown at every level, its effect and glint from MEDIUM, a MIRROR's reflection at HIGH only.
const DEFAULT := {"shown": Level.OFF, "effect": Level.MEDIUM, "glint": Level.MEDIUM, "mirror": Level.HIGH}


## A layer's rule: DEFAULT with `over`'s keys ("shown", "effect", "glint", "mirror") in their place.
static func rule(over: Dictionary) -> Dictionary:
	var r := DEFAULT.duplicate()
	r.merge(over, true)
	return r


## The rule `bg` draws layer `l` by: its name's (PlaxBackground.set_layer_detail), else every layer's.
static func rule_of(bg: PlaxBackground, l: Dictionary) -> Dictionary:
	return bg._details.get(l.model.get("name", ""), bg._default_detail)


## Sets what `bg.detail` draws of each of its layers, both pages of a cross-fade (ParallaxPageReader.setDetail).
static func apply_all(bg: PlaxBackground) -> void:
	for l in bg.layers + bg.transfer_layers:
		apply(l, bg.detail, rule_of(bg, l))
	bg._redraw()


## Sets what `level` draws of layer `l` under rule `r` (ParallaxPageReader.drawLayer, ParallaxLayer
## .getDrawnEffect, drawsGlint): its canvas shown or hidden (it scrolls on), and a SHADER layer's material that of the
## effect it is drawn through, or for none PLAIN when it is fogged.
static func apply(l: Dictionary, level: int, r: Dictionary) -> void:
	var m: Dictionary = l.model
	l.shown = level >= int(r.shown)
	var effect := ""
	if m.kind == "SHADER" and level >= int(r.effect):
		effect = "SWELL" if m.shaderEffect == "MIRROR" and level < int(r.mirror) else String(m.shaderEffect)
	l.drawn_glint = effect != "" and level >= int(r.glint)
	if l.has("canvas") and m.kind != "PARTICLES":
		l.canvas.visible = l.shown
	var before: String = l.get("drawn_effect", m.get("shaderEffect", "") if m.kind == "SHADER" else "")
	l.drawn_effect = effect
	if m.kind != "SHADER" or before == effect:
		return
	l.dissolve_plain = false
	if effect != "":
		l.canvas.material = PlaxEffects.material(effect)
	else:
		l.canvas.material = PlaxEffects.material("PLAIN") if l.get("haze", 0.0) > 0 else null
	if l.has("anim_canvas"):
		# Drawn plain, its patches go with its tiles, as an IMAGE layer's (ParallaxPageReader.drawImageLayer).
		l.anim_canvas.visible = effect != ""
		if effect != "":
			l.anim_canvas.material = PlaxEffects.material(effect)
