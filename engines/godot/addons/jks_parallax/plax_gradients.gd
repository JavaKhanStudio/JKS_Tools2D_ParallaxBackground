class_name PlaxGradients
extends RefCounted
## A PlaxBackground's two gradients (SquareBackground), their cross-fade and their drawing (r527: out of
## plax_background.gd).

## Their colors top_top, top_bottom, bottom_top, bottom_bottom and the part of the screen each leaves uncovered, set by
## set_page. A cross-fade fades the colors; the sizes stay.
var colors: Array[Color] = [Color.WHITE, Color.WHITE, Color.WHITE, Color.WHITE]
var top_half_size := 0.5
var bottom_half_size := 0.5
var _from: Array[Color] = []
var _to: Array[Color] = []
var _duration := 0.0
var _elapsed := 0.0
var _fading := false
## How the gradients fade (TransfertStyle.gradient): null straight, as a plain fade.
var _style: PlaxTransfertStyle = null


## The page's gradients, at once.
func set_page(page: PlaxPage) -> void:
	colors = [page.top_half_top, page.top_half_bottom, page.bottom_half_top, page.bottom_half_bottom]
	_fading = false
	top_half_size = page.top_half_size
	bottom_half_size = page.bottom_half_size


## SquareBackground.transfertInto: both colors of both gradients, alpha included, toward `page`'s, as `style` goes
## (null: straight).
func fade_to(page: PlaxPage, seconds: float, style: PlaxTransfertStyle = null) -> void:
	_style = style
	_from = colors.duplicate()
	_to = [page.top_half_top, page.top_half_bottom, page.bottom_half_top, page.bottom_half_bottom]
	_elapsed = 0
	_duration = seconds
	_fading = true
	if seconds <= 0:
		act(0)


func act(delta: float) -> void:
	if not _fading:
		return
	_elapsed += delta
	var progress := minf(1, _elapsed / _duration) if _duration > 0 else 1.0
	for i in 4:
		colors[i] = (_style.gradient(_from[i], _to[i], progress) if _style
				else _from[i].lerp(_to[i], progress))
	if progress >= 1:
		_fading = false


## The two gradients on `canvas`, in screen pixels and opaque: the libGDX heart draws them without blending.
func draw(canvas: CanvasItem, screen: Vector2) -> void:
	var top_h := screen.y * (1.0 - top_half_size)
	if top_h > 0:
		_draw_one(canvas, Rect2(0, 0, screen.x, top_h), colors[0], colors[1])
	var bottom_h := screen.y * (1.0 - bottom_half_size)
	if bottom_h > 0:
		_draw_one(canvas, Rect2(0, screen.y - bottom_h, screen.x, bottom_h), colors[2], colors[3])


## Opaque, each channel cut to the byte libGDX's ShapeRenderer packs it to (Color.toFloatBits: (int)(255 * c)).
static func _draw_one(canvas: CanvasItem, rect: Rect2, top: Color, bottom: Color) -> void:
	top = Color(floorf(255 * top.r) / 255, floorf(255 * top.g) / 255, floorf(255 * top.b) / 255)
	bottom = Color(floorf(255 * bottom.r) / 255, floorf(255 * bottom.g) / 255, floorf(255 * bottom.b) / 255)
	canvas.draw_polygon(PackedVector2Array(
			[rect.position, Vector2(rect.end.x, rect.position.y), rect.end, Vector2(rect.position.x, rect.end.y)]),
		PackedColorArray([top, top, bottom, bottom]))
