class_name PlaxAnim
extends RefCounted
## A layer's animated patch (format 16, docs/animated-layers.md), for a PlaxBackground: WholePage_Model.findFrames,
## ParallaxLayer.setAnimTime and drawAnim, line for line. Drawn after each tile through _draw_region, so the dolly,
## the planet, the panorama, the layer's effect and the fog take it as they take the tile.


## The patch's frames for layer `l` built from `model`: the regions named animRegion in the atlas's order, each
## measured as the layer's region is (PlaxBackground._measure_region). Only an IMAGE or SHADER layer has one. False,
## said in the log, when the atlas has none of that name: libGDX fails the load.
static func build(l: Dictionary, model: Dictionary, atlas: PlaxAtlas, use_original_size: bool) -> bool:
	l.anim = []
	l.anim_frame = 0
	if not model.kind in ["IMAGE", "SHADER"] or str(model.animRegion).is_empty():
		return true
	var frames: Array[Dictionary] = []
	while true:
		var region := atlas.find_region(model.animRegion, frames.size())
		if region.is_empty():
			break
		frames.append(PlaxBackground._measure_region(region, use_original_size))
	if frames.is_empty():
		push_error("PlaxAnim: region '%s' #0 not found in the atlas" % model.animRegion)
		return false
	l.anim = frames
	return true


## ParallaxLayer.setAnimTime: frame floor(`time` × fps) modulo the count, a negative one wrapped, NaN or infinite 0.
static func set_time(l: Dictionary, time: float) -> void:
	var count: int = l.get("anim", []).size()
	if count == 0:
		return
	var f := fmod(floorf(time * float(l.model.animFps)), count)
	if is_nan(f):
		l.anim_frame = 0
		return
	l.anim_frame = int(f) + count if f < 0 else int(f)


## ParallaxLayer.drawAnim: the current frame over the tile at (x, y), its place in the layer's image scaled to the
## drawn size, mirrored with the tile by a flip.
static func draw(bg: PlaxBackground, l: Dictionary, x: float, y: float, fx: bool, fy: bool) -> void:
	if l.get("anim", []).is_empty():
		return
	var width: float = l.width
	var height: float = l.height
	var scale_x: float = width / l.image_w
	var scale_y: float = height / l.image_h
	var frame: Dictionary = l.anim[l.anim_frame]
	var patch_w: float = frame.image_w * scale_x
	var patch_h: float = frame.image_h * scale_y
	var ax: float = l.model.animX * scale_x
	var ay: float = l.model.animY * scale_y
	var left := x + width - (ax + patch_w) if fx else x + ax
	var bottom := y + height - (ay + patch_h) if fy else y + ay
	bg._draw_region(frame, left, bottom, patch_w, patch_h, fx, fy)
