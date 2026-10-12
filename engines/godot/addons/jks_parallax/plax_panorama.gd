class_name PlaxPanorama
extends RefCounted
## The layers behind a tower seen from close by (r350, docs/depth-tricks.md §2): the Godot port of
## ParallaxPageReader.nearTowerOf/panoramaRadiusOf and core's PanoramaBatch, line for line. Each IMAGE, SHADER and
## SEQUENCE layer slower than the tower, and not still, is the far side of a cylinder round the tower's axis; its point
## at angle t from that side's middle is drawn at axis + rho sin t (q + 1) / (q + cos t) and scaled about the eye by
## (q + 1) / (q + cos t). Drawn as vertical strips fixed in the view, each piece placed by its edges and scaled at its
## strip's middle; PlaxBackground._draw_region hands its regions here when the layer is one.

## PanoramaBatch.STRIPS: how many strips the view is cut in, across.
const STRIPS := 128


## ParallaxLayer.isNearTower: a tower whose camera stands more than one radius from its axis.
static func is_near_tower(l: Dictionary) -> bool:
	return PlaxTower.is_tower(l) and l.model.towerCamera > 1


## ParallaxPageReader.nearTowerOf: the page's tower seen from close by, the fastest when there are more; {} for none.
static func near_tower_of(page_layers: Array) -> Dictionary:
	var tower := {}
	for l: Dictionary in page_layers:
		if is_near_tower(l) and (tower.is_empty() or absf(float(l.model.parallaxScalingSpeedX))
				> absf(float(tower.model.parallaxScalingSpeedX))):
			tower = l
	return tower


## ParallaxPageReader.towerCameraOf: how far the camera looking at `tower` stands from its axis, in world units.
static func camera_of(tower: Dictionary, world_width: float) -> float:
	return tower.model.towerRadius * (world_width / 100.0) * tower.model.towerCamera


## ParallaxPageReader.panoramaRadiusOf: the radius of the panorama `l` is wrapped in round `tower`, world units; 0 flat.
## The tower's front f = D - r away, the layer f s_tower / s, R = that - D, at least D.
static func radius_of(tower: Dictionary, l: Dictionary, world_width: float) -> float:
	if tower.is_empty() or is_same(tower, l) or PlaxTower.is_tower(l):
		return 0.0
	var kind: String = l.model.kind
	if kind != "IMAGE" and kind != "SHADER" and kind != "SEQUENCE":
		return 0.0
	var speed := absf(float(l.model.parallaxScalingSpeedX))
	var tower_speed := absf(float(tower.model.parallaxScalingSpeedX))
	if not speed > 0 or not speed < tower_speed:
		return 0.0
	var camera := camera_of(tower, world_width)
	var radius: float = tower.model.towerRadius * (world_width / 100.0)
	return maxf(camera, (camera - radius) * tower_speed / speed - camera)


## PanoramaBatch.screenOf: where the panorama's point at angle t is drawn, from the axis.
static func screen_of(rho: float, q: float, t: float) -> float:
	return rho * sin(t) * (q + 1) / (q + cos(t))


## PanoramaBatch.scaleAt: how much the panorama is scaled about the eye at angle t: 1 at its middle.
static func scale_at(q: float, t: float) -> float:
	return (q + 1) / (q + cos(t))


## PanoramaBatch.angleAt: the angle of the point drawn x from the axis.
static func angle_at(rho: float, q: float, x: float) -> float:
	var c := rho * (q + 1)
	var a := sqrt(c * c + x * x)
	return atan2(x, c) + asin(clampf(x * q / a, -1, 1))


## PanoramaBatch.set: the strips' edges as arcs from the axis (`edges`, STRIPS + 1) and their scales (`scales`,
## STRIPS), for a panorama of `rho` at its own scale, the camera `q` of its radius from the axis in the view's middle.
static func lay_out(rho: float, q: float, view_w: float, edges: PackedFloat32Array, scales: PackedFloat32Array) -> void:
	var step := view_w / STRIPS
	edges.resize(STRIPS + 1)
	scales.resize(STRIPS)
	for i in STRIPS + 1:
		edges[i] = rho * angle_at(rho, q, i * step - view_w / 2)
	for i in STRIPS:
		scales[i] = scale_at(q, angle_at(rho, q, (i + 0.5) * step - view_w / 2))


## PanoramaBatch.draw: `src` of `texture` in the box from (left, bottom), draw_w by draw_h page units (x 0..view_w of
## the layer's view), flipped as `fx`, `fy` say, cut at the strips, each piece placed round the panorama and scaled
## about the eye at `eye_y`. What lies outside the view is not drawn.
static func draw(
		bg: PlaxBackground, texture: Texture2D, src: Rect2, left: float, bottom: float, draw_w: float, draw_h: float,
		fx: bool, fy: bool, layout: Array, eye_y: float, view_w: float
) -> void:
	var rho: float = layout[0]
	var q: float = layout[1]
	var edges: PackedFloat32Array = layout[2]
	var scales: PackedFloat32Array = layout[3]
	var step := view_w / STRIPS
	var axis := view_w / 2
	var a := left - axis
	var b := left + draw_w - axis
	if not b > a or not step > 0 or b <= edges[0] or a >= edges[STRIPS]:
		return
	var lo := 0
	var hi := STRIPS - 1
	while lo < hi:
		var mid := (lo + hi) >> 1
		if edges[mid + 1] <= a:
			lo = mid + 1
		else:
			hi = mid
	var ppu := bg._ppu
	var strip := lo
	while strip < STRIPS and edges[strip] < b:
		var p0 := maxf(a, edges[strip])
		var p1 := minf(b, edges[strip + 1])
		var s := strip
		strip += 1
		if not p1 > p0:
			continue
		# The piece's share of the image, left to right; flipped on X the image runs right to left.
		var t0 := (p0 + axis - left) / draw_w
		var t1 := (p1 + axis - left) / draw_w
		var g0 := 1 - t1 if fx else t0
		var g1 := 1 - t0 if fx else t1
		var piece := Rect2(src.position.x + src.size.x * g0, src.position.y, src.size.x * (g1 - g0), src.size.y)
		var x0 := s * step if p0 == edges[s] else axis + screen_of(rho, q, p0 / rho)
		var x1 := (s + 1) * step if p1 == edges[s + 1] else axis + screen_of(rho, q, p1 / rho)
		var scale: float = scales[s]
		var piece_y := eye_y + (bottom - eye_y) * scale
		var px := Vector2((x1 - x0) * ppu, draw_h * scale * ppu)
		var origin := Vector2(x0 * ppu, bg._screen_h - piece_y * ppu - px.y)
		if fx or fy:
			bg._target.draw_set_transform(
				origin + Vector2(px.x if fx else 0.0, px.y if fy else 0.0), 0, Vector2(-1 if fx else 1, -1 if fy else 1))
			bg._target.draw_texture_rect_region(texture, Rect2(Vector2.ZERO, px), piece, bg._modulate)
			bg._target.draw_set_transform(Vector2.ZERO)
		else:
			bg._target.draw_texture_rect_region(texture, Rect2(origin, px), piece, bg._modulate)
