package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;

import jks.tools2d.parallax.LayerEffects;
import jks.tools2d.parallax.ParallaxLayer;

/** {@link ShadowLab}'s shaders (r539): the GLSL of its stencil, shadows, occluders, rays and relief, and the batch's own effect. */
final class ShadowShaders
{
	private ShadowShaders()
	{}

	/** A flank turned from the light: a blue black, as the shade of a bright sky's light. */
	static final Color SHADOW_COLOR = new Color(0.04f, 0.06f, 0.12f, 1);

	/** The stencil's and the shadows' effects: none, so their layers draw through the batch's shader, the lab's. */
	static final LayerEffects BATCH_SHADER = new LayerEffects()
	{
		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{return false;}

		@Override
		public void end(Batch batch, ParallaxLayer layer)
		{}

		@Override
		public boolean beginPageFog(Batch batch, Color tint, Color fog, Color incoming)
		{return false;}
	};

	static final String HEAD = "#ifdef GL_ES\n"
			+ "precision mediump float;\n"
			+ "#endif\n"
			+ "varying vec4 v_color;\n"
			+ "varying vec2 v_texCoords;\n"
			+ "uniform sampler2D u_texture;\n";

	/** A receiver into the stencil: only where it is opaque, so the sky behind it takes no shadow. */
	static final String MASK_FRAGMENT = HEAD
			+ "void main()\n"
			+ "{\n"
			+ "	if (texture2D(u_texture, v_texCoords).a < 0.5)\n"
			+ "		discard;\n"
			+ "	gl_FragColor = vec4(1.0);\n"
			+ "}\n";

	/** A layer in front of the suns into {@link #light}: black, through its alpha. */
	static final String OCCLUDE_FRAGMENT = HEAD
			+ "void main()\n"
			+ "{\n"
			+ "	gl_FragColor = vec4(0.0, 0.0, 0.0, texture2D(u_texture, v_texCoords).a);\n"
			+ "}\n";

	/**
	 * {@link #light} blurred toward the sun, u_sun in the view's 0 to 1: 48 samples along the line from the pixel to it,
	 * each weaker than the one before, over u_rays.y of the way; times u_rays.x, in u_color (the sun's), added to what is
	 * drawn.
	 */
	static final String RAYS_FRAGMENT = HEAD
			+ "uniform vec2 u_sun;\n"
			+ "uniform vec2 u_rays;\n"
			+ "uniform vec3 u_color;\n"
			+ "void main()\n"
			+ "{\n"
			+ "	vec2 step = (v_texCoords - u_sun) * u_rays.y / 48.0;\n"
			+ "	vec2 at = v_texCoords;\n"
			+ "	float weight = 1.0;\n"
			+ "	vec3 sum = vec3(0.0);\n"
			+ "	for (int i = 0; i < 48; i++)\n"
			+ "	{\n"
			+ "		at -= step;\n"
			+ "		sum += texture2D(u_texture, at).rgb * weight;\n"
			+ "		weight *= 0.985;\n"
			+ "	}\n"
			+ "	gl_FragColor = vec4(sum / 48.0 * u_rays.x * u_color, 1.0);\n"
			+ "}\n";

	/**
	 * A layer's faces in the sun's light, over it, as a hill shade: the layer is a mass under its ridge line, and a face
	 * leans the way that line climbs over it. Above the pixel and u_soft.x to each side (the page's texture), the ridge
	 * is found by walking up its column until the layer is clear (kept in the strip, as the shadow's); its slope is how
	 * much higher it stands on one side than the other. The face leans against it, and up a little; u_sun the way to
	 * the sun (z toward you). A face turned to it more than one facing you is lit, toward u_lit, one turned less in
	 * u_shade, both by up to u_shade.a, fading over u_soft.y (the ridge's own height in v) under the ridge.
	 */
	static final String RELIEF_FRAGMENT = HEAD
			+ "uniform vec2 u_soft;\n"
			+ "uniform vec2 u_strips;\n"
			+ "uniform vec3 u_sun;\n"
			+ "uniform vec4 u_shade;\n"
			+ "uniform vec4 u_lit;\n"
			+ "float at(vec2 uv, float top, float bottom)\n"
			+ "{\n"
			+ "	return texture2D(u_texture, vec2(uv.x, clamp(uv.y, top, bottom))).a;\n"
			+ "}\n"
			+ "void main()\n"
			+ "{\n"
			+ "	float strip = floor(v_texCoords.y * u_strips.x + 0.001);\n"
			+ "	float top = strip / u_strips.x + u_strips.y, bottom = (strip + 1.0) / u_strips.x - 3.0 * u_strips.y;\n"
			+ "	float here = at(v_texCoords, top, bottom) * v_color.a;\n"
			+ "	if (here < 0.004)\n"
			+ "		discard;\n"
			// Four rings of twelve, out to u_soft: the way into the mass, the farther rings weighing as much as the near.
			+ "	vec2 into = vec2(0.0);\n"
			+ "	for (int r = 1; r <= 4; r++)\n"
			+ "		for (int i = 0; i < 12; i++)\n"
			+ "		{\n"
			+ "			float angle = 0.5235988 * (float(i) + 0.5 * float(r));\n"
			+ "			vec2 ring = vec2(cos(angle), sin(angle));\n"
			+ "			into += ring * at(v_texCoords + ring * u_soft * float(r) / 4.0, top, bottom);\n"
			+ "		}\n"
			// Across a straight edge the 48 add up to about 48 / pi: 1 there, less deeper in. A texture's v runs down.
			+ "	vec2 away = -vec2(into.x, -into.y) / 15.3;\n"
			+ "	vec3 face = normalize(vec3(1.5 * away, 1.0)), facing = vec3(0.0, 0.0, 1.0);\n"
			+ "	float turn = dot(face, u_sun) - dot(facing, u_sun);\n"
			+ "	float amount = clamp(1.5 * abs(turn), 0.0, 1.0) * u_shade.a * here;\n"
			+ "	gl_FragColor = turn > 0.0 ? vec4(u_lit.rgb, 0.6 * amount) : vec4(u_shade.rgb, amount);\n"
			+ "}\n";

	/**
	 * The batch's vertex shader, its corners where the caster's shadow lands ({@link #shadowOf}): u_shift.xy, the shift;
	 * u_shift.w, how much wider about the view's middle (u_shift.z, world x) the shadow is than the caster. With
	 * u_ground.w 1, the sun behind you's ({@link #groundOf}): u_ground.xy its kx and kz, u_ground.z the caster's foot,
	 * u_horizon the horizon's y and the view's middle; the vertex's w is the ground's, so the image is laid in perspective.
	 */
	static final String SHIFT_VERTEX = "attribute vec4 a_position;\n"
			+ "attribute vec4 a_color;\n"
			+ "attribute vec2 a_texCoord0;\n"
			+ "uniform mat4 u_projTrans;\n"
			+ "uniform vec4 u_shift;\n"
			+ "uniform vec4 u_ground;\n"
			+ "uniform vec2 u_horizon;\n"

			+ "varying vec4 v_color;\n"
			+ "varying vec2 v_texCoords;\n"
			+ "void main()\n"
			+ "{\n"
			+ "	v_color = a_color;\n"
			+ "	v_color.a = v_color.a * (255.0 / 254.0);\n"
			+ "	v_texCoords = a_texCoord0;\n"
			+ "	if (u_ground.w > 0.5)\n"
			+ "	{\n"
			+ "		float up = a_position.y - u_ground.z, w = 1.0 + u_ground.y * up;\n"
			+ "		vec2 g = vec2(u_horizon.y * w + (a_position.x - u_horizon.y) + u_ground.x * up, u_horizon.x * w - (u_horizon.x - u_ground.z));\n"
			+ "		gl_Position = u_projTrans * vec4(g, 0.0, w);\n"
			+ "		return;\n"
			+ "	}\n"
			+ "	vec2 p = vec2(u_shift.z + (a_position.x - u_shift.z) * u_shift.w + u_shift.x, a_position.y + u_shift.y);\n"
			+ "	gl_Position = u_projTrans * vec4(p, 0.0, 1.0);\n"
			+ "}\n";

	/**
	 * The caster as a shadow: its alpha, averaged over a disc of u_soft (in the page's texture), times u_shadow.a, from
	 * nothing to u_shadow.rgb (what the shadow leaves of the light, per channel), discarded where it is all but clear. u_strips: the strips a page height holds, and half a texel: the samples stay in their strip.
	 */
	static final String SHADOW_FRAGMENT = HEAD
			+ "uniform vec2 u_soft;\n"
			+ "uniform vec2 u_strips;\n"
			+ "uniform vec4 u_shadow;\n"
			+ "float at(vec2 uv, float top, float bottom)\n"
			+ "{\n"
			+ "	return texture2D(u_texture, vec2(uv.x, clamp(uv.y, top, bottom))).a;\n"
			+ "}\n"
			+ "void main()\n"
			+ "{\n"
			+ "	float strip = floor(v_texCoords.y * u_strips.x + 0.001);\n"
			+ "	float top = strip / u_strips.x + u_strips.y, bottom = (strip + 1.0) / u_strips.x - 3.0 * u_strips.y;\n"
			+ "	float a = at(v_texCoords, top, bottom);\n"
			// Two rings of eight round the middle, the outer one at the radius.
			+ "	for (int i = 0; i < 8; i++)\n"
			+ "	{\n"
			+ "		float angle = 0.7853982 * float(i);\n"
			+ "		vec2 ring = vec2(cos(angle), sin(angle)) * u_soft;\n"
			+ "		a += at(v_texCoords + ring, top, bottom) + at(v_texCoords + 0.5 * vec2(-ring.y, ring.x), top, bottom);\n"
			+ "	}\n"
			+ "	float alpha = u_shadow.a * a / 17.0 * v_color.a;\n"
			// Nothing drawn sets no stencil: the mass under the caster still goes there.
			+ "	if (alpha < 0.004)\n"
			+ "		discard;\n"
			// Drawn as a product (GL_ZERO, GL_SRC_COLOR): what is there times what the shadow leaves of it.
			+ "	gl_FragColor = vec4(mix(vec3(1.0), u_shadow.rgb, alpha), 1.0);\n"
			+ "}\n";
}
