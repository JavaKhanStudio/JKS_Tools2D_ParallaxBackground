package jks.tools2d.parallax;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;

/**
 * The SHADER layers' effects in libGDX: a batch shader per {@link Enum_ShaderEffect}, GLSL ES 1.0, so WebGL in a
 * browser game too. Compiled the first time a layer needs it; a shader that does not compile is said once in the log,
 * and its layers are drawn without it. Setting the shader flushes the batch: a SHADER layer costs a flush before and
 * after its tiles, and so does an IMAGE or SEQUENCE layer the page's depth fog reaches, drawn through {@link #PLAIN}.
 * A shader is kept bound from one layer to the next ({@link #release} puts the game's back): layers drawn one after the
 * other through the same shader cost a flush each, not two shader switches.
 * <p>
 * Each effect works on the image's own coordinates, taken from the texture coordinates: x and y in world units from the
 * image's bottom-left, as it is drawn. engines/godot/addons/jks_parallax/plax_effects.gd and engines/jme's
 * ParallaxEffect.frag are the same lines: change one, change them, and run engines/godot/tools/frame-check.sh and
 * engines/jme/tools/frame-check.sh (engines/rounds/shaders).
 */
public class GdxLayerEffects implements LayerEffects, Disposable
{
	/** SpriteBatch's own vertex shader, and v_view: where the vertex is in the camera view, 0 to 1, y up. */
	static final String VERTEX = "attribute vec4 " + ShaderProgram.POSITION_ATTRIBUTE + ";\n"
			+ "attribute vec4 " + ShaderProgram.COLOR_ATTRIBUTE + ";\n"
			+ "attribute vec2 " + ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n"
			+ "uniform mat4 u_projTrans;\n"
			+ "varying vec4 v_color;\n"
			+ "varying vec2 v_texCoords;\n"
			+ "varying vec2 v_view;\n"
			+ "void main()\n"
			+ "{\n"
			+ "	v_color = " + ShaderProgram.COLOR_ATTRIBUTE + ";\n"
			+ "	v_color.a = v_color.a * (255.0 / 254.0);\n"
			+ "	v_texCoords = " + ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n"
			+ "	gl_Position = u_projTrans * " + ShaderProgram.POSITION_ATTRIBUTE + ";\n"
			+ "	v_view = gl_Position.xy / gl_Position.w * 0.5 + 0.5;\n"
			+ "}\n";

	/** The mist's white: the fog's colour when none is given ({@link LayerEffects#begin(Batch, ParallaxLayer, float, float)}). */
	public static final float HAZE_R = 0.93f, HAZE_G = 0.95f, HAZE_B = 0.97f;

	/**
	 * u_region: the region's u, v, u2, v2 (v at the image's top). u_size: the image's width and height in world units.
	 * u_effect: amplitude, wavelength, phase. u_haze: the depth fog, 0 to 1. u_fog: the fog's colour. local(): where the
	 * fragment is in the image, in world units, y up. hazed(): the color mixed toward u_fog by u_haze, then toward
	 * u_grade's rgb by its a (a transfert through a colour, {@link LayerEffects#setGrade}), its alpha times dissolved();
	 * at 0, no grade and no dissolve the color itself. u_dissolve, u_cells: a transfert's dissolve
	 * ({@link LayerEffects#setDissolve}): side (0 none, 1 outgoing, 2 incoming), ramp, softness, drift; the noise's cells
	 * across and up the view. dissolved(): FOG's noise over the view, the layer's share of the slot at the ramp.
	 * u_across: the view's width in world units, where the layer's noise starts from the view's left
	 * ({@link ParallaxLayer#getEffectStartX}), 1 or -1 (flipped on X). along(): where the fragment is along the layer
	 * in world units, local() carried on across its tiles, so FOG's noise has no seam at a tile edge (r229). u_up: the
	 * same up the view, from its bottom ({@link ParallaxLayer#getEffectStartY}), -1 flipped on Y (r262): FOG's noise and
	 * WAVE's ripple run on up across the tiles (r273). glinted(): the sun on the painted crests ({@link #glintUniforms}).
	 */
	private static final String FRAGMENT_HEAD = "#ifdef GL_ES\n"
			+ "#ifdef GL_FRAGMENT_PRECISION_HIGH\n"
			+ "precision highp float;\n"
			+ "#else\n"
			+ "precision mediump float;\n"
			+ "#endif\n"
			+ "#endif\n"
			+ "varying vec4 v_color;\n"
			+ "varying vec2 v_texCoords;\n"
			+ "uniform sampler2D u_texture;\n"
			+ "uniform vec4 u_region;\n"
			+ "uniform vec2 u_size;\n"
			+ "uniform vec3 u_effect;\n"
			+ "uniform float u_haze;\n"
			+ "uniform vec3 u_fog;\n"
			+ "uniform vec4 u_dissolve;\n"
			+ "uniform vec2 u_cells;\n"
			+ "uniform vec4 u_grade;\n"
			+ "uniform vec3 u_across;\n"
			+ "uniform vec3 u_up;\n"
			+ "uniform vec4 u_glint;\n"
			+ "uniform vec2 u_sun;\n"
			+ "uniform vec4 u_glintA;\n"
			+ "uniform vec4 u_glintB;\n"
			+ "varying vec2 v_view;\n"
			+ "const float TAU = 6.2831853;\n"
			+ "float dissolved()\n"
			+ "{\n"
			+ "	if (u_dissolve.x < 0.5)\n"
			+ "		return 1.0;\n"
			+ "	vec2 p = v_view * u_cells + vec2(u_dissolve.w, 0.0);\n"
			+ "	float n = (sin(TAU * 0.875 * p.x + 2.0 * sin(0.5 * TAU * p.y))\n"
			+ "		+ sin(TAU * (2.125 * p.x - 0.5 * p.y) + 1.3)\n"
			+ "		+ sin(TAU * (2.875 * p.x + 0.8 * p.y) + 2.9)) / 6.0 + 0.5;\n"
			+ "	float e = u_dissolve.z;\n"
			+ "	float m = smoothstep(n - e, n + e, u_dissolve.y * (1.0 + 2.0 * e) - e);\n"
			+ "	return u_dissolve.x < 1.5 ? 1.0 - m : m;\n"
			+ "}\n"
			+ "vec4 hazed(vec4 color)\n"
			+ "{\n"
			+ "	return vec4(mix(mix(color.rgb, u_fog, u_haze), u_grade.rgb, u_grade.a), color.a * dissolved());\n"
			+ "}\n"
			+ "vec2 local()\n"
			+ "{\n"
			+ "	return vec2((v_texCoords.x - u_region.x) / (u_region.z - u_region.x) * u_size.x,\n"
			+ "		(u_region.w - v_texCoords.y) / (u_region.w - u_region.y) * u_size.y);\n"
			+ "}\n"
			+ "vec2 along()\n"
			+ "{\n"
			+ "	return vec2(u_across.z * (v_view.x * u_across.x - u_across.y), u_up.z * (v_view.y * u_up.x - u_up.y));\n"
			+ "}\n"
			+ "vec4 glinted(vec4 color, vec4 image, vec2 uv)\n"
			+ "{\n"
			+ "	if (u_glint.x <= 0.0)\n"
			+ "		return color;\n"
			+ "	const vec3 LUMA = vec3(0.299, 0.587, 0.114);\n"
			+ "	float dv = u_glint.w / u_size.y * (u_region.w - u_region.y);\n"
			+ "	float lo = min(u_region.y, u_region.w), hi = max(u_region.y, u_region.w);\n"
			+ "	vec4 above = texture2D(u_texture, vec2(uv.x, clamp(uv.y - dv, lo, hi)));\n"
			+ "	vec4 below = texture2D(u_texture, vec2(uv.x, clamp(uv.y + dv, lo, hi)));\n"
			+ "	float ridge = dot(image.rgb, LUMA) - 0.5 * (dot(above.rgb, LUMA) + dot(below.rgb, LUMA));\n"
			+ "	float crest = smoothstep(0.005, 0.035, ridge) * min(above.a, below.a) * image.a;\n"
			+ "	float sunAt = (v_view.x - u_sun.x) / u_sun.y;\n"
			+ "	vec2 q = along() / u_glintB.zw;\n"
			+ "	float n = (cos(TAU * (u_glintA.x * q.x + u_glintA.y * q.y)) + 0.6 * cos(TAU * (u_glintA.z * q.x + u_glintA.w * q.y + 0.3))) / 1.6;\n"
			+ "	float pulse = 0.5 + 0.5 * cos(TAU * (u_glint.z + 1.5 * sin(TAU * (u_glintB.x * q.x + u_glintB.y * q.y))));\n"
			+ "	float glow = smoothstep(1.0 - u_glint.y, 1.0, n) * pulse * pulse * crest * exp(-sunAt * sunAt);\n"
			+ "	return vec4(min(color.rgb + u_glint.x * glow * vec3(1.0, 0.96, 0.85), 1.0), color.a);\n"
			+ "}\n";

	/**
	 * WAVE: each row shifted sideways by amplitude * sin(2 pi (y - phase) / wavelength), kept inside the region. y is
	 * along().y, not local().y: the ripple runs on up across the tiles, so a wavelength that does not divide the image's
	 * height jumps at no tile edge on Y (r273).
	 */
	static final String WAVE = FRAGMENT_HEAD
			+ "void main()\n"
			+ "{\n"
			+ "	float shift = u_effect.x * sin(TAU * (along().y - u_effect.z) / u_effect.y);\n"
			+ "	float u = clamp(v_texCoords.x + shift / u_size.x * (u_region.z - u_region.x), min(u_region.x, u_region.z), max(u_region.x, u_region.z));\n"
			+ "	vec2 uv = vec2(u, v_texCoords.y);\n"
			+ "	vec4 image = texture2D(u_texture, uv);\n"
			+ "	gl_FragColor = hazed(glinted(v_color * image, image, uv));\n"
			+ "}\n";

	/**
	 * FOG: the opacity times 1 - amplitude * n, n in 0..1 a sum of three sines (no hash: the same on every GPU), whose
	 * x frequencies are 7, 17 and 23 per 8 wavelengths: the patches come back only every 8 wavelengths (r216, at 1, 2
	 * and 3 per wavelength they came back every one), and the phase wraps there without a jump. Across the layer, not
	 * inside one tile: a tile edge is no seam, on X (r229) or Y (r262).
	 */
	static final String FOG = FRAGMENT_HEAD
			+ "void main()\n"
			+ "{\n"
			+ "	vec2 p = (along() + vec2(u_effect.z, 0.0)) / u_effect.y;\n"
			+ "	float n = (sin(TAU * 0.875 * p.x + 2.0 * sin(0.5 * TAU * p.y))\n"
			+ "		+ sin(TAU * (2.125 * p.x - 0.5 * p.y) + 1.3)\n"
			+ "		+ sin(TAU * (2.875 * p.x + 0.8 * p.y) + 2.9)) / 6.0 + 0.5;\n"
			+ "	vec4 image = texture2D(u_texture, v_texCoords);\n"
			+ "	vec4 color = v_color * image;\n"
			+ "	color.a *= 1.0 - u_effect.x * n;\n"
			+ "	gl_FragColor = hazed(glinted(color, image, v_texCoords));\n"
			+ "}\n";

	/**
	 * SWELL (r431, the animation lab's, r375, r419): each pixel read from where two crossing travelling waves move it,
	 * up to amplitude sideways and half that up and down, the image wrapped across (the layer tiles) and held at its top
	 * and bottom rows. t is the phase in wavelengths. Every term is whole turns over 8 wavelengths across (8 and 5) and 10
	 * up (6 and 9), and the phase wraps at 8 (Enum_ShaderEffect.period): the swell runs on across the layer's wrap.
	 */
	static final String SWELL = FRAGMENT_HEAD
			+ "void main()\n"
			+ "{\n"
			+ "	vec2 a = along();\n"
			+ "	float L = u_effect.y;\n"
			+ "	float t = u_effect.z / L;\n"
			+ "	float p1 = TAU * ((a.x + 0.6 * a.y) / L - t);\n"
			+ "	float p2 = TAU * (0.625 * a.x / L - 0.9 * a.y / L + 2.0 * t);\n"
			+ "	vec2 l = local() + u_effect.x * vec2(0.6 * sin(p1) + 0.4 * sin(p2), 0.5 * (0.6 * cos(p1) - 0.4 * cos(p2)));\n"
			+ "	vec2 f = vec2(fract(l.x / u_size.x), clamp(l.y / u_size.y, 0.0, 1.0));\n"
			+ "	vec2 uv = vec2(mix(u_region.x, u_region.z, f.x), mix(u_region.w, u_region.y, f.y));\n"
			+ "	vec4 image = texture2D(u_texture, uv);\n"
			+ "	gl_FragColor = hazed(glinted(v_color * image, image, uv));\n"
			+ "}\n";

	/**
	 * The page fog ({@link LayerEffects#beginPageFog}): the tint a uniform, the batch color's red the layer's fog, its
	 * green which page's fog colour, its blue how much darker a tower's strip is drawn (0 for every other layer:
	 * {@link ParallaxLayer#drawTower}), its alpha the layer's. Mixed as hazed() does, after the darkening.
	 */
	static final String PAGE_FOG = FRAGMENT_HEAD
			+ "uniform vec3 u_tint;\n"
			+ "uniform vec3 u_fogIncoming;\n"
			+ "void main()\n"
			+ "{\n"
			+ "	vec4 color = vec4(u_tint * (1.0 - v_color.b), v_color.a) * texture2D(u_texture, v_texCoords);\n"
			+ "	vec3 fog = v_color.g > 0.5 ? u_fogIncoming : u_fog;\n"
			+ "	gl_FragColor = vec4(mix(color.rgb, fog, v_color.r), color.a);\n"
			+ "}\n";

	/** No effect: an IMAGE or SEQUENCE layer drawn through the depth fog only. */
	static final String PLAIN = FRAGMENT_HEAD
			+ "void main()\n"
			+ "{\n"
			+ "	gl_FragColor = hazed(v_color * texture2D(u_texture, v_texCoords));\n"
			+ "}\n";

	/**
	 * The glint's numbers a page does not store (r408: Simon chose its strength alone, the rest as the animation lab
	 * drew it): a glint's length along a crest and how far a crest's water is looked for above and below it, in world
	 * units; how much of the crests is lit, 0.05 to 1.5; its pulses a second; the sun's column, its middle and width in
	 * fractions of the view.
	 */
	public static final float GLINT_SIZE = 0.8f, GLINT_CREST = 0.2f, GLINT_DENSITY = 0.6f, GLINT_TWINKLE = 0.3f, GLINT_SUN_X = 0.5f,
			GLINT_SUN_WIDTH = 0.3f;
	/** Each glint wave's turns per glint spacing across (2.5 glint lengths) and per 0.9 world units up: uneven, so the pieces lit differ crest to crest. */
	private static final float[] GLINT_ACROSS = { 1, 1.37f, 0.53f }, GLINT_UP = { 1, -0.71f, 1.3f };

	private static final Enum_ShaderEffect[] EFFECTS = Enum_ShaderEffect.values();
	/** The program of a layer drawn without an effect, after the effects' own. */
	private static final int PLAIN_INDEX = EFFECTS.length;

	private final ShaderProgram[] programs = new ShaderProgram[EFFECTS.length + 1];
	private final boolean[] failed = new boolean[EFFECTS.length + 1];
	private final int[] region = new int[EFFECTS.length + 1], size = new int[EFFECTS.length + 1], effect = new int[EFFECTS.length + 1],
			haze = new int[EFFECTS.length + 1], fog = new int[EFFECTS.length + 1], dissolve = new int[EFFECTS.length + 1],
			cells = new int[EFFECTS.length + 1], grade = new int[EFFECTS.length + 1], across = new int[EFFECTS.length + 1], up = new int[EFFECTS.length + 1],
			glint = new int[EFFECTS.length + 1], sun = new int[EFFECTS.length + 1], glintA = new int[EFFECTS.length + 1], glintB = new int[EFFECTS.length + 1];
	/** The view's size in world units, which FOG's noise is laid across, and where the layers' x and y 0 are ({@link #setView}). */
	private float viewWidth, viewHeight, viewLeft, viewFloor;
	/** The dissolve the next layers begun draw through ({@link #setDissolve}): side, ramp, softness, drift; cells. */
	private float dissolveSide, dissolveRamp, dissolveSoftness, dissolveDrift, cellsX, cellsY;
	/** The colour the next layers begun are mixed toward, and by how much ({@link #setGrade}). */
	private float gradeR, gradeG, gradeB, gradeAmount;
	/** The batch's shader before the first begin, and whether one of ours is still bound in its place. */
	private ShaderProgram previous;
	private boolean holding;
	/** The page fog's shader, and whether it is the one {@link #release} comes back to. */
	private ShaderProgram pageFog;
	private boolean pageFogFailed, inPageFog;
	/** Its uniforms' locations: -1 for one the GPU's compiler dropped, which is then not set. */
	private int pageFogTint, pageFogColor, pageFogIncoming;
	private final float[] numbers = new float[15], glints = new float[14];

	/** The vertex shader of every effect: SpriteBatch's own. */
	public static String vertex()
	{return VERTEX;}

	/** The fragment shader of an effect, as compiled here; null: of a layer drawn without one, through the fog only. */
	public static String fragment(Enum_ShaderEffect effect)
	{
		if (effect == null)
			return PLAIN;
		switch (effect)
		{
			case FOG:
				return FOG;
			case SWELL:
				return SWELL;
			case WAVE:
			default:
				return WAVE;
		}
	}

	@Override
	public boolean begin(Batch batch, ParallaxLayer layer, float phase)
	{return begin(batch, layer, phase, 0, HAZE_R, HAZE_G, HAZE_B);}

	@Override
	public boolean begin(Batch batch, ParallaxLayer layer, float phase, float haze)
	{return begin(batch, layer, phase, haze, HAZE_R, HAZE_G, HAZE_B);}

	@Override
	public boolean begin(Batch batch, ParallaxLayer layer, float phase, float fog, Color fogColor)
	{return begin(batch, layer, phase, fog, fogColor.r, fogColor.g, fogColor.b);}

	/** A SHADER layer through its effect, any other through none; both mixed toward the fog's colour by {@code haze}. */
	private boolean begin(Batch batch, ParallaxLayer layer, float phase, float haze, float fogR, float fogG, float fogB)
	{
		int index = layer.getKind() == Enum_LayerKind.SHADER ? layer.getShaderEffect().ordinal() : PLAIN_INDEX;
		ShaderProgram program = program(index);
		if (program == null)
			return false;

		if (!holding)
		{
			previous = batch.getShader();
			holding = true;
		}
		if (batch.getShader() == program)
			// Still bound from the layer before: what it drew goes out with its numbers before they change.
			batch.flush();
		else
			batch.setShader(program);
		// Bound by setShader while the batch draws; bound here when it does not, for the uniforms.
		if (!batch.isDrawing())
			program.bind();
		uniforms(layer, phase, viewWidth, viewHeight, viewLeft, viewFloor, numbers);
		program.setUniformf(region[index], numbers[0], numbers[1], numbers[2], numbers[3]);
		program.setUniformf(size[index], numbers[4], numbers[5]);
		program.setUniformf(effect[index], numbers[6], numbers[7], numbers[8]);
		program.setUniformf(across[index], numbers[9], numbers[10], numbers[11]);
		program.setUniformf(up[index], numbers[12], numbers[13], numbers[14]);
		program.setUniformf(this.haze[index], Math.max(0, Math.min(1, haze)));
		program.setUniformf(fog[index], fogR, fogG, fogB);
		program.setUniformf(dissolve[index], dissolveSide, dissolveRamp, dissolveSoftness, dissolveDrift);
		program.setUniformf(cells[index], cellsX, cellsY);
		program.setUniformf(grade[index], gradeR, gradeG, gradeB, Math.max(0, Math.min(1, gradeAmount)));
		glintUniforms(layer, glints);
		program.setUniformf(glint[index], glints[0], glints[1], glints[2], glints[3]);
		program.setUniformf(sun[index], glints[4], glints[5]);
		program.setUniformf(glintA[index], glints[6], glints[7], glints[8], glints[9]);
		program.setUniformf(glintB[index], glints[10], glints[11], glints[12], glints[13]);
		return true;
	}

	@Override
	public boolean beginPatch(Batch batch, ParallaxLayer layer)
	{
		int index = layer.getKind() == Enum_LayerKind.SHADER ? layer.getShaderEffect().ordinal() : PLAIN_INDEX;
		ShaderProgram program = programs[index];
		if (program == null || batch.getShader() != program || layer.getAnimDrawnRegion() == null)
			return false;
		// The tiles go out with the layer's region, the patches with their frame's.
		batch.flush();
		if (!batch.isDrawing())
			program.bind();
		patchUniforms(layer, numbers);
		program.setUniformf(region[index], numbers[0], numbers[1], numbers[2], numbers[3]);
		program.setUniformf(size[index], numbers[4], numbers[5]);
		return true;
	}

	@Override
	public void setView(float width, float height, float floor)
	{setView(width, height, 0, floor);}

	@Override
	public void setView(float width, float height, float left, float floor)
	{
		viewWidth = width;
		viewHeight = height;
		viewLeft = left;
		viewFloor = floor;
	}

	@Override
	public boolean setGrade(float r, float g, float b, float amount)
	{
		gradeR = r;
		gradeG = g;
		gradeB = b;
		gradeAmount = amount;
		return true;
	}

	@Override
	public boolean setDissolve(int side, float ramp, float softness, float drift, float cellsX, float cellsY)
	{
		dissolveSide = side;
		dissolveRamp = ramp;
		dissolveSoftness = softness;
		dissolveDrift = drift;
		this.cellsX = cellsX;
		this.cellsY = cellsY;
		return true;
	}

	/** Leaves the layer's shader bound for the next layer: {@link #release} puts the game's back. */
	@Override
	public void end(Batch batch, ParallaxLayer layer)
	{}

	@Override
	public void release(Batch batch)
	{
		if (!holding)
			return;
		if (inPageFog)
		{
			if (batch.getShader() != pageFog)
				batch.setShader(pageFog);
			return;
		}
		batch.setShader(previous);
		previous = null;
		holding = false;
	}

	@Override
	public boolean beginPageFog(Batch batch, Color tint, Color fog, Color incomingFog)
	{
		if (pageFog == null && !pageFogFailed)
		{
			pageFog = new ShaderProgram(VERTEX, PAGE_FOG);
			if (!pageFog.isCompiled())
			{
				pageFogFailed = true;
				Gdx.app.error("Parallax", "The page fog's shader does not compile here, its layers are fogged one by one: " + pageFog.getLog());
				pageFog.dispose();
				pageFog = null;
			}
			else
			{
				pageFogTint = pageFog.fetchUniformLocation("u_tint", false);
				pageFogColor = pageFog.fetchUniformLocation("u_fog", false);
				pageFogIncoming = pageFog.fetchUniformLocation("u_fogIncoming", false);
			}
		}
		if (pageFog == null)
			return false;
		if (!holding)
		{
			previous = batch.getShader();
			holding = true;
		}
		inPageFog = true;
		batch.setShader(pageFog);
		if (!batch.isDrawing())
			pageFog.bind();
		pageFog.setUniformf(pageFogTint, tint.r, tint.g, tint.b);
		pageFog.setUniformf(pageFogColor, fog.r, fog.g, fog.b);
		pageFog.setUniformf(pageFogIncoming, incomingFog.r, incomingFog.g, incomingFog.b);
		return true;
	}

	@Override
	public void endPageFog(Batch batch)
	{
		inPageFog = false;
		release(batch);
	}

	/**
	 * The numbers a SHADER layer's effect is drawn with, the same in every engine, into {@code out}: the drawn region's
	 * u, v, u2, v2 (half a texel inside the atlas region, {@link ParallaxLayer#getDrawnRegion}); the image's width and height in world units; amplitude, wavelength and phase. A wavelength not positive
	 * draws no effect: amplitude 0, wavelength 1. FOG's amplitude is kept within 0..1.
	 */
	public static float[] uniforms(ParallaxLayer layer, float phase, float[] out)
	{
		TextureRegion image = layer.getDrawnRegion();
		out[0] = image.getU();
		out[1] = image.getV();
		out[2] = image.getU2();
		out[3] = image.getV2();
		out[4] = layer.getWidth() * layer.getPackedWidthRatio();
		out[5] = layer.getHeight() * layer.getPackedHeightRatio();
		boolean on = layer.getShaderWavelength() > 0;
		float amplitude = layer.getShaderAmplitude();
		if (layer.getShaderEffect() == Enum_ShaderEffect.FOG)
			amplitude = Math.max(0, Math.min(1, amplitude));
		out[6] = on ? amplitude : 0;
		out[7] = on ? layer.getShaderWavelength() : 1;
		out[8] = on ? phase : 0;
		return out;
	}

	/**
	 * {@link #uniforms(ParallaxLayer, float, float[])}, then where FOG's noise lies across the layer, into
	 * {@code out[9..11]}: the view's width in world units, where the noise starts from the view's left
	 * ({@link ParallaxLayer#getEffectStartX}), and 1, or -1 for a layer flipped on X; into {@code out[12..14]} the same up
	 * the view: its height, where the noise starts from its bottom ({@code floor}, the reader's drawing height, plus
	 * {@link ParallaxLayer#getEffectStartY}), and 1, or -1 for a layer flipped on Y (r262).
	 */
	public static float[] uniforms(ParallaxLayer layer, float phase, float viewWidth, float viewHeight, float floor, float[] out)
	{return uniforms(layer, phase, viewWidth, viewHeight, 0, floor, out);}

	/**
	 * The same, the layers' x 0 {@code left} right of the view's left edge, as their y 0 is {@code floor} above its
	 * bottom: a dollied page's (r278, {@link LayerEffects#setView(float, float, float, float)}).
	 */
	public static float[] uniforms(ParallaxLayer layer, float phase, float viewWidth, float viewHeight, float left, float floor, float[] out)
	{
		uniforms(layer, phase, out);
		out[9] = viewWidth;
		out[10] = left + layer.getEffectStartX();
		out[11] = layer.isFlipX() ? -1 : 1;
		out[12] = viewHeight;
		out[13] = floor + layer.getEffectStartY();
		out[14] = layer.isFlipY() ? -1 : 1;
		return out;
	}

	/**
	 * A SHADER layer's glint (r408), the same in every engine, into {@code out[0..13]}: u_glint, its strength (0: none, a
	 * layer that does not {@link ParallaxLayer#glints}), {@link #GLINT_DENSITY}, its pulses on the reader's clock
	 * ({@link #GLINT_TWINKLE} a second, wrapped at a whole number) and {@link #GLINT_CREST}; u_sun, the sun's column;
	 * u_glintA, the two waves that pick which pieces of the crests are lit, their whole turns over the effect's period
	 * across and up; u_glintB, the third's, that sets when each piece pulses, then the periods, in world units. Each wave is
	 * a whole number of turns over the period, so the glint runs on across the layer's wrap.
	 */
	public static float[] glintUniforms(ParallaxLayer layer, float[] out)
	{
		boolean on = layer.glints();
		float periodX = on ? layer.getShaderPeriod() : 1, periodY = on ? layer.getShaderPeriodY() : 1;
		out[0] = on ? layer.getGlintStrength() : 0;
		out[1] = GLINT_DENSITY;
		out[2] = (float) (layer.getEffectTime() * GLINT_TWINKLE % 1000.0);
		out[3] = GLINT_CREST;
		out[4] = GLINT_SUN_X;
		out[5] = GLINT_SUN_WIDTH;
		float across = periodX / (2.5f * GLINT_SIZE), up = periodY / 0.9f;
		for (int k = 0; k < 3; k++)
		{
			out[6 + 2 * k] = Math.max(1, (float) Math.floor(GLINT_ACROSS[k] * across + 0.5f));
			out[7 + 2 * k] = (float) Math.floor(GLINT_UP[k] * up + 0.5f);
		}
		out[12] = periodX;
		out[13] = periodY;
		return out;
	}

	/**
	 * {@link #uniforms(ParallaxLayer, float, float[])}'s {@code out[0..5]} for the layer's animated patch
	 * ({@link LayerEffects#beginPatch}): its current frame's u, v, u2, v2, and its width and height as drawn. The rest are
	 * the layer's.
	 */
	public static float[] patchUniforms(ParallaxLayer layer, float[] out)
	{
		TextureRegion frame = layer.getAnimDrawnRegion();
		out[0] = frame.getU();
		out[1] = frame.getV();
		out[2] = frame.getU2();
		out[3] = frame.getV2();
		out[4] = layer.getAnimDrawnWidth();
		out[5] = layer.getAnimDrawnHeight();
		return out;
	}

	private ShaderProgram program(int index)
	{
		if (programs[index] != null || failed[index])
			return programs[index];

		boolean plain = index == PLAIN_INDEX;
		ShaderProgram program = new ShaderProgram(VERTEX, fragment(plain ? null : EFFECTS[index]));
		if (!program.isCompiled())
		{
			failed[index] = true;
			Gdx.app.error("Parallax", "The " + (plain ? "depth fog" : EFFECTS[index]) + " shader does not compile here, its layers are drawn without it: " + program.getLog());
			program.dispose();
			return null;
		}
		region[index] = program.fetchUniformLocation("u_region", false);
		size[index] = program.fetchUniformLocation("u_size", false);
		effect[index] = program.fetchUniformLocation("u_effect", false);
		haze[index] = program.fetchUniformLocation("u_haze", false);
		fog[index] = program.fetchUniformLocation("u_fog", false);
		dissolve[index] = program.fetchUniformLocation("u_dissolve", false);
		cells[index] = program.fetchUniformLocation("u_cells", false);
		grade[index] = program.fetchUniformLocation("u_grade", false);
		across[index] = program.fetchUniformLocation("u_across", false);
		up[index] = program.fetchUniformLocation("u_up", false);
		glint[index] = program.fetchUniformLocation("u_glint", false);
		sun[index] = program.fetchUniformLocation("u_sun", false);
		glintA[index] = program.fetchUniformLocation("u_glintA", false);
		glintB[index] = program.fetchUniformLocation("u_glintB", false);
		programs[index] = program;
		return program;
	}

	@Override
	public void dispose()
	{
		if (pageFog != null)
			pageFog.dispose();
		pageFog = null;
		for (int i = 0; i < programs.length; i++)
		{
			if (programs[i] != null)
				programs[i].dispose();
			programs[i] = null;
		}
	}
}
