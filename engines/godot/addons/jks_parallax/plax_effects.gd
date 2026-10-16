class_name PlaxEffects
extends RefCounted
## The SHADER layers' effects (Enum_ShaderEffect), as CanvasItem shaders: core's GdxLayerEffects line for line, the same
## numbers (uniforms), the same picture. Change one, change core/src/.../GdxLayerEffects.java and engines/jme's
## ParallaxEffect.frag, and run engines/godot/tools/frame-check.sh engines/rounds/shaders.
##
## Each effect works on the image's own coordinates, taken from UV: x and y in page units from the image's bottom-left,
## as it is drawn. region: the region's u, v, u2, v2 (v at the image's top); size: the drawn image's width and height in
## page units; effect: amplitude, wavelength, phase; haze: the depth fog at the layer, 0 to 1, which hazed() mixes the
## color toward fog_color by, then toward grade's rgb by its a (a transfert through a colour, set_grade), its alpha
## times dissolved() (fog_of). dissolve, cells: a transfert's dissolve (side 0 none, 1 outgoing, 2 incoming; ramp,
## softness, drift; the noise's cells across and up the view), which dissolved() reads at SCREEN_UV, y turned up as
## libGDX's view. across: the view's width in page units, where the layer's noise starts from its left (effect_start_x),
## 1 or -1 (flipped on X); along() reads it at SCREEN_UV: where the fragment is along the layer, local() carried on
## across its tiles, so FOG has no seam at a tile edge (r229); up: the same up the view, from its bottom
## (effect_start_y), -1 flipped on Y (r262), which WAVE's ripple reads too (r273). TAU is Godot's own (2 pi). "PLAIN" is
## no effect: an IMAGE or SEQUENCE layer the page's depth fog reaches, or any a dissolve masks or a transfert through a
## colour grades. glinted(): the sun on a SHADER layer's painted crests after its effect (GdxLayerEffects.glintUniforms,
## r408), its numbers set by apply.

## The effects this reader draws: a page naming another fails to load, as in libGDX.
const EFFECTS := ["WAVE", "FOG", "SWELL"]
## Enum_ShaderEffect.FOG_PERIOD: how many wavelengths FOG's patches run before they repeat, SWELL's waves too.
const FOG_PERIOD := 8
## Enum_ShaderEffect.FOG_PERIOD_Y: how many wavelengths they run up before they repeat (r262).
const FOG_PERIOD_Y := 10
## GdxLayerEffects' GLINT_*: what a page does not store of the glint, as the animation lab drew it (r408).
const GLINT_SIZE := 0.8
const GLINT_CREST := 0.2
const GLINT_DENSITY := 0.6
const GLINT_TWINKLE := 0.3
const GLINT_SUN_X := 0.5
const GLINT_SUN_WIDTH := 0.3
const GLINT_ACROSS := [1.0, 1.37, 0.53]
const GLINT_UP := [1.0, -0.71, 1.3]

const _HEAD := """shader_type canvas_item;
uniform vec4 region;
uniform vec2 size;
uniform vec3 effect;
uniform float haze;
uniform vec3 fog_color = vec3(0.93, 0.95, 0.97);
uniform vec4 dissolve;
uniform vec2 cells;
uniform vec4 grade;
uniform vec3 across = vec3(0.0, 0.0, 1.0);
uniform vec3 up = vec3(0.0, 0.0, 1.0);
uniform vec4 glint;
uniform vec2 sun = vec2(0.5, 0.3);
uniform vec4 glint_a;
uniform vec4 glint_b = vec4(0.0, 0.0, 1.0, 1.0);
varying vec4 tint;
void vertex() {
	tint = COLOR;
}
vec2 local(vec2 uv) {
	return vec2((uv.x - region.x) / (region.z - region.x) * size.x, (region.w - uv.y) / (region.w - region.y) * size.y);
}
vec2 along(vec2 screen_uv) {
	return vec2(across.z * (screen_uv.x * across.x - across.y), up.z * ((1.0 - screen_uv.y) * up.x - up.y));
}
float dissolved(vec2 screen_uv) {
	if (dissolve.x < 0.5)
		return 1.0;
	vec2 p = vec2(screen_uv.x, 1.0 - screen_uv.y) * cells + vec2(dissolve.w, 0.0);
	float n = (sin(TAU * 0.875 * p.x + 2.0 * sin(0.5 * TAU * p.y))
		+ sin(TAU * (2.125 * p.x - 0.5 * p.y) + 1.3)
		+ sin(TAU * (2.875 * p.x + 0.8 * p.y) + 2.9)) / 6.0 + 0.5;
	float e = dissolve.z;
	float m = smoothstep(n - e, n + e, dissolve.y * (1.0 + 2.0 * e) - e);
	return dissolve.x < 1.5 ? 1.0 - m : m;
}
vec4 hazed(vec4 color, vec2 screen_uv) {
	return vec4(mix(mix(color.rgb, fog_color, haze), grade.rgb, grade.a), color.a * dissolved(screen_uv));
}
vec4 glinted(sampler2D tex, vec4 color, vec4 image, vec2 uv, vec2 screen_uv) {
	if (glint.x <= 0.0)
		return color;
	const vec3 LUMA = vec3(0.299, 0.587, 0.114);
	float dv = glint.w / size.y * (region.w - region.y);
	float lo = min(region.y, region.w), hi = max(region.y, region.w);
	vec4 above = texture(tex, vec2(uv.x, clamp(uv.y - dv, lo, hi)));
	vec4 below = texture(tex, vec2(uv.x, clamp(uv.y + dv, lo, hi)));
	float ridge = dot(image.rgb, LUMA) - 0.5 * (dot(above.rgb, LUMA) + dot(below.rgb, LUMA));
	float crest = smoothstep(0.005, 0.035, ridge) * min(above.a, below.a) * image.a;
	float sun_at = (screen_uv.x - sun.x) / sun.y;
	vec2 q = along(screen_uv) / glint_b.zw;
	float n = (cos(TAU * (glint_a.x * q.x + glint_a.y * q.y))
		+ 0.6 * cos(TAU * (glint_a.z * q.x + glint_a.w * q.y + 0.3))) / 1.6;
	float pulse = 0.5 + 0.5 * cos(TAU * (glint.z + 1.5 * sin(TAU * (glint_b.x * q.x + glint_b.y * q.y))));
	float glow = smoothstep(1.0 - glint.y, 1.0, n) * pulse * pulse * crest * exp(-sun_at * sun_at);
	return vec4(min(color.rgb + glint.x * glow * vec3(1.0, 0.96, 0.85), 1.0), color.a);
}
"""

## WAVE: each row shifted sideways by amplitude * sin(2 pi (y - phase) / wavelength), kept inside the region; y is
## along().y, running on up across the tiles (r273).
const _WAVE := _HEAD + """void fragment() {
	float shift = effect.x * sin(TAU * (along(SCREEN_UV).y - effect.z) / effect.y);
	float u = clamp(UV.x + shift / size.x * (region.z - region.x), min(region.x, region.z), max(region.x, region.z));
	vec2 uv = vec2(u, UV.y);
	vec4 image = texture(TEXTURE, uv);
	COLOR = hazed(glinted(TEXTURE, tint * image, image, uv, SCREEN_UV), SCREEN_UV);
}
"""

## FOG: the opacity times 1 - amplitude * n, n in 0..1 a sum of three sines whose x frequencies are 7, 17 and 23 per
## 8 wavelengths (FOG_PERIOD).
const _FOG := _HEAD + """void fragment() {
	vec2 p = (along(SCREEN_UV) + vec2(effect.z, 0.0)) / effect.y;
	float n = (sin(TAU * 0.875 * p.x + 2.0 * sin(0.5 * TAU * p.y))
		+ sin(TAU * (2.125 * p.x - 0.5 * p.y) + 1.3)
		+ sin(TAU * (2.875 * p.x + 0.8 * p.y) + 2.9)) / 6.0 + 0.5;
	vec4 image = texture(TEXTURE, UV);
	vec4 color = tint * image;
	color.a *= 1.0 - effect.x * n;
	COLOR = hazed(glinted(TEXTURE, color, image, UV, SCREEN_UV), SCREEN_UV);
}
"""

## SWELL (r431): each pixel read from where two crossing travelling waves move it, up to amplitude sideways and half
## that up and down, the image wrapped on the axes its page repeats on (wrap: 1 wraps, 0 holds the edge rows, r434)
## and held on the others; whole turns over FOG_PERIOD wavelengths across and FOG_PERIOD_Y up.
const _SWELL := _HEAD + """uniform vec2 wrap = vec2(1.0, 0.0);
void fragment() {
	vec2 a = along(SCREEN_UV);
	float L = effect.y;
	float t = effect.z / L;
	float p1 = TAU * ((a.x + 0.6 * a.y) / L - t);
	float p2 = TAU * (0.625 * a.x / L - 0.9 * a.y / L + 2.0 * t);
	vec2 l = local(UV) + effect.x * vec2(0.6 * sin(p1) + 0.4 * sin(p2), 0.5 * (0.6 * cos(p1) - 0.4 * cos(p2)));
	vec2 f = mix(clamp(l / size, 0.0, 1.0), fract(l / size), wrap);
	vec2 uv = vec2(mix(region.x, region.z, f.x), mix(region.w, region.y, f.y));
	vec4 image = texture(TEXTURE, uv);
	COLOR = hazed(glinted(TEXTURE, tint * image, image, uv, SCREEN_UV), SCREEN_UV);
}
"""

## PLAIN: no effect, the depth fog only.
const _PLAIN := _HEAD + """void fragment() {
	COLOR = hazed(tint * texture(TEXTURE, UV), SCREEN_UV);
}
"""

static var _shaders := {}


## A material of its own for a SHADER layer's canvas, or "PLAIN" for a fogged one: its numbers change every frame.
static func material(effect_name: String) -> ShaderMaterial:
	if not _shaders.has(effect_name):
		var shader := Shader.new()
		shader.code = {"FOG": _FOG, "SWELL": _SWELL, "PLAIN": _PLAIN}.get(effect_name, _WAVE)
		_shaders[effect_name] = shader
	var m := ShaderMaterial.new()
	m.shader = _shaders[effect_name]
	return m


## ParallaxLayer.getShaderPhase: speed * seconds wrapped to where the effect repeats (one wavelength, FOG_PERIOD for
## FOG and SWELL), never negative; 0 without a wavelength.
static func phase(model: Dictionary, seconds: float) -> float:
	var wavelength: float = model.shaderWavelength
	if not wavelength > 0:
		return 0.0
	var period := wavelength * _across(model)
	var p := fmod(seconds * float(model.shaderSpeed), period)
	return p + period if p < 0 else p


## ParallaxLayer.getShaderPeriod: where the effect repeats, one wavelength, FOG_PERIOD for FOG and SWELL, at least
## FOG's for a glinting layer; 0 without a wavelength.
static func period(model: Dictionary) -> float:
	var wavelength: float = model.get("shaderWavelength", 0.0)
	if not wavelength > 0:
		return 0.0
	return wavelength * _across(model)


## ParallaxLayer.glints: a SHADER layer with a positive glint strength and wavelength.
static func glints(model: Dictionary) -> bool:
	return (model.get("kind", "") == "SHADER" and float(model.get("glintStrength", 0.0)) > 0
			and float(model.get("shaderWavelength", 0.0)) > 0)


## ParallaxLayer.periodAcross: the wavelengths the effect runs across before it repeats, FOG's with it when glinting.
static func _across(model: Dictionary) -> int:
	var own := FOG_PERIOD if model.get("shaderEffect", "") in ["FOG", "SWELL"] else 1
	return _least_multiple(own, FOG_PERIOD) if glints(model) else own


## ParallaxLayer.periodUp: the same up.
static func _up(model: Dictionary) -> int:
	var own := FOG_PERIOD_Y if model.get("shaderEffect", "") in ["FOG", "SWELL"] else 1
	return _least_multiple(own, FOG_PERIOD_Y) if glints(model) else own


static func _least_multiple(a: int, b: int) -> int:
	var x := a
	var y := b
	while y != 0:
		var r := x % y
		x = y
		y = r
	return a / x * b


## ParallaxLayer.getEffectStartX: where FOG's noise starts on X, in page units from the view's left, wrapped to the
## period: the image's left edge in the tile at distance_x (its right edge flipped on X), carried back by what
## _act_layer wrapped.
static func effect_start_x(l: Dictionary) -> float:
	var trim: float = (1 - l.trim_left) if l.model.flipX else l.trim_left
	var start: float = l.distance_x + l.wrapped_x + l.width * trim
	var p := period(l.model)
	return fmod(start, p) if p > 0 else start


## ParallaxLayer.getShaderPeriodY: where the effect repeats up, one wavelength, FOG_PERIOD_Y for FOG and SWELL, at
## least FOG's for a glinting layer; 0 without one.
static func period_y(model: Dictionary) -> float:
	var wavelength: float = model.get("shaderWavelength", 0.0)
	if not wavelength > 0:
		return 0.0
	return wavelength * _up(model)


## ParallaxLayer.getEffectStartY: where FOG's noise starts on Y, in page units from the view's bottom, wrapped to
## period_y: the image's bottom edge in the tile at distance_y (its top edge flipped on Y), carried back by what
## _act_layer wrapped (r262).
static func effect_start_y(l: Dictionary) -> float:
	var trim: float = (1 - l.trim_bottom) if l.model.flipY else l.trim_bottom
	var start: float = l.distance_y + l.wrapped_y + l.height * trim
	var p := period_y(l.model)
	return fmod(start, p) if p > 0 else start


## What is drawn of an atlas region `r`, in texels: half a texel in on every side, as ParallaxLayer.insetByHalfATexel
## (r218). A linear sample at a tile's edge then never reads the atlas pixel past it, transparent in an atlas packed
## without duplicatePadding.
static func drawn_rect(r: Dictionary) -> Rect2:
	var rect := Rect2(r.x, r.y, r.width, r.height)
	return rect.grow(-0.5) if r.width >= 2 and r.height >= 2 else rect


## GdxLayerEffects.uniforms: the numbers of a SHADER layer `l` of PlaxBackground, set on its material; `view_w` and
## `view_h` the view's size in page units; `wrap` 1 on an axis the page repeats on, 0 on one it does not (SWELL's,
## GdxLayerEffects.setRepeat, r434).
static func apply(
		m: ShaderMaterial, l: Dictionary, seconds: float, view_w: float, view_h: float, left := 0.0, floor_y := 0.0,
		wrap := Vector2(1.0, 0.0)
) -> void:
	var r: Dictionary = l.region
	var texture_size: Vector2 = r.texture.get_size()
	var model: Dictionary = l.model
	var drawn := drawn_rect(r)
	m.set_shader_parameter("region", Vector4(drawn.position.x / texture_size.x, drawn.position.y / texture_size.y,
			drawn.end.x / texture_size.x, drawn.end.y / texture_size.y))
	m.set_shader_parameter("size", Vector2(l.width * l.packed_w, l.height * l.packed_h))
	var on: bool = model.shaderWavelength > 0
	var amplitude: float = model.shaderAmplitude
	if model.shaderEffect == "FOG":
		amplitude = clampf(amplitude, 0, 1)
	m.set_shader_parameter("effect", Vector3(amplitude if on else 0.0, model.shaderWavelength if on else 1.0,
			phase(model, seconds) if on else 0.0))
	m.set_shader_parameter("across", Vector3(view_w, left + effect_start_x(l), -1.0 if model.flipX else 1.0))
	m.set_shader_parameter("up", Vector3(view_h, floor_y + effect_start_y(l), -1.0 if model.flipY else 1.0))
	m.set_shader_parameter("wrap", wrap)
	_apply_glint(m, model, seconds)


## GdxLayerEffects.glintUniforms: the glint's numbers, its strength 0 for a layer that does not glint; each wave a whole
## number of turns over the effect's period.
static func _apply_glint(m: ShaderMaterial, model: Dictionary, seconds: float) -> void:
	var on := glints(model)
	var period_x := period(model) if on else 1.0
	var period_up := period_y(model) if on else 1.0
	m.set_shader_parameter("glint", Vector4(float(model.get("glintStrength", 0.0)) if on else 0.0, GLINT_DENSITY,
			fmod(seconds * GLINT_TWINKLE, 1000.0), GLINT_CREST))
	m.set_shader_parameter("sun", Vector2(GLINT_SUN_X, GLINT_SUN_WIDTH))
	var across := period_x / (2.5 * GLINT_SIZE)
	var up_turns := period_up / 0.9
	var t: Array[float] = []
	for k in 3:
		t.append(maxf(1.0, floorf(GLINT_ACROSS[k] * across + 0.5)))
		t.append(floorf(GLINT_UP[k] * up_turns + 0.5))
	m.set_shader_parameter("glint_a", Vector4(t[0], t[1], t[2], t[3]))
	m.set_shader_parameter("glint_b", Vector4(t[4], t[5], period_x, period_up))


## LayerEffects.setDissolve on a layer's material: `side` 0 (none), 1 (outgoing) or 2 (incoming), at the slot's `ramp`,
## the noise `drift` cells along, `cells_x` by `cells_y` cells over the view.
static func set_dissolve(m: ShaderMaterial, side: int, ramp: float, softness: float, drift: float, cells_x: float,
		cells_y: float) -> void:
	m.set_shader_parameter("dissolve", Vector4(side, ramp, softness, drift))
	m.set_shader_parameter("cells", Vector2(cells_x, cells_y))


## LayerEffects.setGrade on a layer's material: mixed toward `color` by `amount`, 0 to 1, after the fog.
static func set_grade(m: ShaderMaterial, color: Color, amount: float) -> void:
	m.set_shader_parameter("grade", Vector4(color.r, color.g, color.b, clampf(amount, 0, 1)))


## ParallaxPageReader.fogOf: how much of the fog's colour layer `model` is mixed toward, 0 to 1:
## 1 - exp(-strength * (1 / speed - 1 / front)), speed its speed ratio X and front the page's fastest (front_speed_of),
## as magnitudes. 0 for the front layer, for no fog and for a page that does not scroll; 1 for a still layer.
static func fog_of(strength: float, front: float, model: Dictionary) -> float:
	if not strength > 0 or not front > 0:
		return 0.0
	var speed := absf(float(model.parallaxScalingSpeedX))
	if not speed > 0:
		return 1.0
	var depth := 1.0 / speed - 1.0 / front
	return 1.0 - exp(-strength * depth) if depth > 0 else 0.0


## ParallaxPageReader.dollyZoom: the zoom a dollied layer is drawn at, the camera's being `zoom` and its page's front
## `front`: 1 - k (1 - zoom), k its speed ratio X over the front's, as magnitudes; the camera's for a still page.
static func dolly_zoom(zoom: float, front: float, model: Dictionary) -> float:
	if not front > 0:
		return zoom
	var k := minf(1.0, absf(float(model.parallaxScalingSpeedX)) / front)
	return 1.0 - k * (1.0 - zoom)


## ParallaxPageReader.frontSpeedOf: the fastest speed ratio X of `page` (PlaxBackground's layers), as a magnitude.
static func front_speed_of(page: Array[Dictionary]) -> float:
	var front := 0.0
	for l in page:
		front = maxf(front, absf(float(l.model.parallaxScalingSpeedX)))
	return front
