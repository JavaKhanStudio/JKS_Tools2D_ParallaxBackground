class_name PlaxEffects
extends RefCounted
## The SHADER layers' effects (Enum_ShaderEffect), as CanvasItem shaders: core's GdxLayerEffects line for line, the same
## numbers (uniforms), the same picture. Change one, change core/src/.../GdxLayerEffects.java and engines/jme's
## ParallaxEffect.frag, and run tools/godot-parallax-shots.sh engines/godot/tests/shaders.
##
## Each effect works on the image's own coordinates, taken from UV: x and y in page units from the image's bottom-left,
## as it is drawn. region: the region's u, v, u2, v2 (v at the image's top); size: the drawn image's width and height in
## page units; effect: amplitude, wavelength, phase; haze: the depth haze, 0 to 1, which hazed() mixes the color toward
## the mist's white by, its alpha kept (GdxLayerEffects.HAZE_R/G/B). TAU is Godot's own (2 pi). "PLAIN" is no effect:
## an IMAGE or SEQUENCE layer behind a FOG layer's depth haze.

## The effects this reader draws: a page naming another fails to load, as in libGDX.
const EFFECTS := ["WAVE", "FOG"]

const _HEAD := """shader_type canvas_item;
uniform vec4 region;
uniform vec2 size;
uniform vec3 effect;
uniform float haze;
const vec3 HAZE = vec3(0.93, 0.95, 0.97);
varying vec4 tint;
void vertex() {
	tint = COLOR;
}
vec2 local(vec2 uv) {
	return vec2((uv.x - region.x) / (region.z - region.x) * size.x, (region.w - uv.y) / (region.w - region.y) * size.y);
}
vec4 hazed(vec4 color) {
	return vec4(mix(color.rgb, HAZE, haze), color.a);
}
"""

## WAVE: each row shifted sideways by amplitude * sin(2 pi (y - phase) / wavelength), kept inside the region.
const _WAVE := _HEAD + """void fragment() {
	float shift = effect.x * sin(TAU * (local(UV).y - effect.z) / effect.y);
	float u = clamp(UV.x + shift / size.x * (region.z - region.x), min(region.x, region.z), max(region.x, region.z));
	COLOR = hazed(tint * texture(TEXTURE, vec2(u, UV.y)));
}
"""

## FOG: the opacity times 1 - amplitude * n, n in 0..1 a sum of three sines whose x frequencies are 1, 2 and 3 per
## wavelength.
const _FOG := _HEAD + """void fragment() {
	vec2 p = (local(UV) + vec2(effect.z, 0.0)) / effect.y;
	float n = (sin(TAU * p.x + 2.0 * sin(0.5 * TAU * p.y))
		+ sin(TAU * (2.0 * p.x - 0.5 * p.y) + 1.3)
		+ sin(TAU * (3.0 * p.x + 0.8 * p.y) + 2.9)) / 6.0 + 0.5;
	vec4 color = tint * texture(TEXTURE, UV);
	color.a *= 1.0 - effect.x * n;
	COLOR = hazed(color);
}
"""

## PLAIN: no effect, the depth haze only.
const _PLAIN := _HEAD + """void fragment() {
	COLOR = hazed(tint * texture(TEXTURE, UV));
}
"""

static var _shaders := {}


## A material of its own for a SHADER layer's canvas, or "PLAIN" for a hazed one: its numbers change every frame.
static func material(effect_name: String) -> ShaderMaterial:
	if not _shaders.has(effect_name):
		var shader := Shader.new()
		shader.code = _FOG if effect_name == "FOG" else _PLAIN if effect_name == "PLAIN" else _WAVE
		_shaders[effect_name] = shader
	var m := ShaderMaterial.new()
	m.shader = _shaders[effect_name]
	return m


## ParallaxLayer.getShaderPhase: speed * seconds wrapped to one wavelength, never negative; 0 without a wavelength.
static func phase(model: Dictionary, seconds: float) -> float:
	var wavelength: float = model.shaderWavelength
	if not wavelength > 0:
		return 0.0
	var p := fmod(seconds * float(model.shaderSpeed), wavelength)
	return p + wavelength if p < 0 else p


## GdxLayerEffects.uniforms: the numbers of a SHADER layer `l` of PlaxBackground, set on its material.
static func apply(m: ShaderMaterial, l: Dictionary, seconds: float) -> void:
	var r: Dictionary = l.region
	var texture_size: Vector2 = r.texture.get_size()
	var model: Dictionary = l.model
	m.set_shader_parameter("region", Vector4(r.x / texture_size.x, r.y / texture_size.y,
			(r.x + r.width) / texture_size.x, (r.y + r.height) / texture_size.y))
	m.set_shader_parameter("size", Vector2(l.width * l.packed_w, l.height * l.packed_h))
	var on: bool = model.shaderWavelength > 0
	var amplitude: float = model.shaderAmplitude
	if model.shaderEffect == "FOG":
		amplitude = clampf(amplitude, 0, 1)
	m.set_shader_parameter("effect", Vector3(amplitude if on else 0.0, model.shaderWavelength if on else 1.0,
			phase(model, seconds) if on else 0.0))


## ParallaxLayer.getHazePerLayer: a FOG layer's depth haze, kept within 0..1; 0 for any other layer.
static func haze_per_layer(model: Dictionary) -> float:
	if model.kind != "SHADER" or model.shaderEffect != "FOG" or not float(model.shaderHaze) > 0:
		return 0.0
	return minf(1.0, model.shaderHaze)


## ParallaxPageReader.hazeOf: 1 minus the product, over every FOG layer in front of layer `index` of `page` (back to
## front, PlaxBackground's layers), of (1 - haze) once per step between them.
static func haze_of(page: Array[Dictionary], index: int) -> float:
	var keep := 1.0
	var step := 1.0
	for i in range(page.size() - 1, index, -1):
		var h := haze_per_layer(page[i].model)
		if h > 0:
			step *= 1.0 - h
		keep *= step
	return 1.0 - keep
