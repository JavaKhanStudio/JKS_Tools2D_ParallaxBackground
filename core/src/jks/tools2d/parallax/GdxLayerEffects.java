package jks.tools2d.parallax;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.Disposable;

import jks.tools2d.parallax.pages.Enum_ShaderEffect;

/**
 * The SHADER layers' effects in libGDX: a batch shader per {@link Enum_ShaderEffect}, GLSL ES 1.0, so WebGL in a
 * browser game too. Compiled the first time a layer needs it; a shader that does not compile is said once in the log,
 * and its layers are drawn without it. Setting the shader flushes the batch: a SHADER layer costs a flush before and
 * after its tiles.
 * <p>
 * Each effect works on the image's own coordinates, taken from the texture coordinates: x and y in world units from the
 * image's bottom-left, as it is drawn. engines/godot/addons/jks_parallax/plax_effects.gd and engines/jme's
 * ParallaxEffect.frag are the same lines: change one, change them, and run tools/godot-parallax-shots.sh and
 * tools/jme-parallax-shots.sh (engines/godot/tests/shaders).
 */
public class GdxLayerEffects implements LayerEffects, Disposable
{
	/** SpriteBatch's own vertex shader. */
	static final String VERTEX = "attribute vec4 " + ShaderProgram.POSITION_ATTRIBUTE + ";\n"
			+ "attribute vec4 " + ShaderProgram.COLOR_ATTRIBUTE + ";\n"
			+ "attribute vec2 " + ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n"
			+ "uniform mat4 u_projTrans;\n"
			+ "varying vec4 v_color;\n"
			+ "varying vec2 v_texCoords;\n"
			+ "void main()\n"
			+ "{\n"
			+ "	v_color = " + ShaderProgram.COLOR_ATTRIBUTE + ";\n"
			+ "	v_color.a = v_color.a * (255.0 / 254.0);\n"
			+ "	v_texCoords = " + ShaderProgram.TEXCOORD_ATTRIBUTE + "0;\n"
			+ "	gl_Position = u_projTrans * " + ShaderProgram.POSITION_ATTRIBUTE + ";\n"
			+ "}\n";

	/**
	 * u_region: the region's u, v, u2, v2 (v at the image's top). u_size: the image's width and height in world units.
	 * u_effect: amplitude, wavelength, phase. local(): where the fragment is in the image, in world units, y up.
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
			+ "const float TAU = 6.2831853;\n"
			+ "vec2 local()\n"
			+ "{\n"
			+ "	return vec2((v_texCoords.x - u_region.x) / (u_region.z - u_region.x) * u_size.x,\n"
			+ "		(u_region.w - v_texCoords.y) / (u_region.w - u_region.y) * u_size.y);\n"
			+ "}\n";

	/** WAVE: each row shifted sideways by amplitude * sin(2 pi (y - phase) / wavelength), kept inside the region. */
	static final String WAVE = FRAGMENT_HEAD
			+ "void main()\n"
			+ "{\n"
			+ "	float shift = u_effect.x * sin(TAU * (local().y - u_effect.z) / u_effect.y);\n"
			+ "	float u = clamp(v_texCoords.x + shift / u_size.x * (u_region.z - u_region.x), min(u_region.x, u_region.z), max(u_region.x, u_region.z));\n"
			+ "	gl_FragColor = v_color * texture2D(u_texture, vec2(u, v_texCoords.y));\n"
			+ "}\n";

	/**
	 * FOG: the opacity times 1 - amplitude * n, n in 0..1 a sum of three sines (no hash: the same on every GPU), whose
	 * x frequencies are 1, 2 and 3 per wavelength, so that the phase wraps at one wavelength without a jump.
	 */
	static final String FOG = FRAGMENT_HEAD
			+ "void main()\n"
			+ "{\n"
			+ "	vec2 p = (local() + vec2(u_effect.z, 0.0)) / u_effect.y;\n"
			+ "	float n = (sin(TAU * p.x + 2.0 * sin(0.5 * TAU * p.y))\n"
			+ "		+ sin(TAU * (2.0 * p.x - 0.5 * p.y) + 1.3)\n"
			+ "		+ sin(TAU * (3.0 * p.x + 0.8 * p.y) + 2.9)) / 6.0 + 0.5;\n"
			+ "	vec4 color = v_color * texture2D(u_texture, v_texCoords);\n"
			+ "	color.a *= 1.0 - u_effect.x * n;\n"
			+ "	gl_FragColor = color;\n"
			+ "}\n";

	private static final Enum_ShaderEffect[] EFFECTS = Enum_ShaderEffect.values();

	private final ShaderProgram[] programs = new ShaderProgram[EFFECTS.length];
	private final boolean[] failed = new boolean[EFFECTS.length];
	private final int[] region = new int[EFFECTS.length], size = new int[EFFECTS.length], effect = new int[EFFECTS.length];
	private ShaderProgram previous;
	private final float[] numbers = new float[9];

	/** The vertex shader of every effect: SpriteBatch's own. */
	public static String vertex()
	{return VERTEX;}

	/** The fragment shader of an effect, as compiled here. */
	public static String fragment(Enum_ShaderEffect effect)
	{
		switch (effect)
		{
			case FOG:
				return FOG;
			case WAVE:
			default:
				return WAVE;
		}
	}

	@Override
	public boolean begin(Batch batch, ParallaxLayer layer, float phase)
	{
		int index = layer.getShaderEffect().ordinal();
		ShaderProgram program = program(index);
		if (program == null)
			return false;

		previous = batch.getShader();
		batch.setShader(program);
		// Bound by setShader while the batch draws; bound here when it does not, for the uniforms.
		if (!batch.isDrawing())
			program.bind();
		uniforms(layer, phase, numbers);
		program.setUniformf(region[index], numbers[0], numbers[1], numbers[2], numbers[3]);
		program.setUniformf(size[index], numbers[4], numbers[5]);
		program.setUniformf(effect[index], numbers[6], numbers[7], numbers[8]);
		return true;
	}

	@Override
	public void end(Batch batch, ParallaxLayer layer)
	{
		batch.setShader(previous);
		previous = null;
	}

	/**
	 * The numbers a SHADER layer's effect is drawn with, the same in every engine, into {@code out}: the region's u, v,
	 * u2, v2; the image's width and height in world units; amplitude, wavelength and phase. A wavelength not positive
	 * draws no effect: amplitude 0, wavelength 1. FOG's amplitude is kept within 0..1.
	 */
	public static float[] uniforms(ParallaxLayer layer, float phase, float[] out)
	{
		TextureRegion image = layer.getRegion();
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

	private ShaderProgram program(int index)
	{
		if (programs[index] != null || failed[index])
			return programs[index];

		ShaderProgram program = new ShaderProgram(VERTEX, fragment(EFFECTS[index]));
		if (!program.isCompiled())
		{
			failed[index] = true;
			Gdx.app.error("Parallax", "The " + EFFECTS[index] + " shader does not compile here, its layers are drawn without it: " + program.getLog());
			program.dispose();
			return null;
		}
		region[index] = program.fetchUniformLocation("u_region", false);
		size[index] = program.fetchUniformLocation("u_size", false);
		effect[index] = program.fetchUniformLocation("u_effect", false);
		programs[index] = program;
		return program;
	}

	@Override
	public void dispose()
	{
		for (int i = 0; i < programs.length; i++)
		{
			if (programs[i] != null)
				programs[i].dispose();
			programs[i] = null;
		}
	}
}
