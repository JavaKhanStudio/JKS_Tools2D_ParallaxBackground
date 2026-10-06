package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;

import java.util.ArrayList;

import jks.tools2d.parallax.GdxLayerEffects;
import jks.tools2d.parallax.LayerEffects;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;
import jks.tools2d.parallax.pages.Utils_Page_Json;

/**
 * The FOG lab (r204, doubt d15): the shaders round's s01 page, drawn four times by the real reader in WebGL, its mist
 * band's FOG patches as tall as they are wide (A, what ships), half as tall (B), as tall as the slider says (C, a
 * stored patch height), and without FOG. Opened with fog-lab.html: tools/fog-lab.sh. The sliders move
 * the mist's amplitude, wavelength and speed, and the scroll, in every panel at once, and the ripple of s01's two WAVE
 * tree layers (r208: Simon read them as "dancing"): their amplitude times the slider, and the mist's place among the
 * layers. It opens on the settings Simon sent on r208 ({@link #SIMON}: shape A, a lighter, wider mist, no ripple). Depth haze
 * (r208, Simon: "the thing in the background that the fog completely blocks"): every layer behind the mist mixed toward
 * the mist's white, more the further back it is, in panels A to C; the lab draws the page one layer at a time to do it,
 * the reader is unchanged. Other sets (r208, Simon: "add some other parallax set to test"): round1's calm, PurpleFairy
 * and OneNight pages, s01's mist put into each, picked with a slider; the mist's size and height have sliders too, the
 * mist picture being a band (Simon: "fog seems to still mainly be in the middle"). "Copy settings"
 * (fog-lab.html) puts every slider, and what it started at, on the clipboard as JSON, for Simon to paste into a card.
 * <p>
 * {@code &clip=1}: nothing moves by itself, {@code window.fogLabAct(seconds)} steps every heart at 1/60 s, so that
 * tools/fog-lab.sh --clip records it at game speed however slow the browser draws.
 */
public class FogLab extends ApplicationAdapter
{
	static final String PAGE = "fog-lab/s01.jplax";
	/**
	 * The sets the slider picks from: s01 first, then round1's pages, which get a copy of s01's mist. Each: page, name,
	 * how many of its layers the mist starts in front of (-1: s01's own), where the mist's middle starts (world units
	 * up), how far below the panel the frame's bottom is (px: s01 sits high, round1's pages at the bottom).
	 */
	static final String[] SET_PAGES = { PAGE, "fog-lab/s02.jplax", "fog-lab/s05.jplax", "fog-lab/s06.jplax" };
	static final String[] SET_NAMES = { "Hiver (s01)", "calm (round1 s02)", "PurpleFairy (round1 s05)", "OneNight (round1 s06)" };
	static final int[] SET_MIST_INDEX = { -1, 6, 4, 4 };
	static final float[] SET_MIST_MIDDLE = { 17.125f, 5.5f, 5.5f, 5.5f };
	/** The round's world: 40 wide, 16:9, as tools/parallax-lab-shots.sh draws s01. */
	static final float WORLD_WIDTH = 40, WORLD_HEIGHT = 22.5f;
	/** Each panel: the round's 16:9 frame, cut below the page (s01 leaves its lower 42% to the white gradient). */
	static final int FRAME_WIDTH = 760, FRAME_HEIGHT = 428, PANEL_HEIGHT = 250;
	static final String[] TITLES = { "A: ships today (patches 3-4x taller than wide)", "B: half as tall (about round)", "C: patch height = slider", "no FOG, no haze" };

	/** Simon's settings from r208 (d16 answered A): mist amplitude, wavelength, C's patch height, scroll, trees' ripple. */
	static final float[] SIMON = { 0.25f, 8.25f, 0.45f, 40, 0 };
	/** Depth haze to begin with: the share of the mist's white a layer one step behind it takes. */
	static final float HAZE = 0.25f;
	/** The haze's color: the mist's white. */
	static final float FOG_R = 0.93f, FOG_G = 0.95f, FOG_B = 0.97f;

	/** The FOG patch height per panel, in wavelengths; C's comes from its slider, the last panel draws plain. */
	private final float[] heights = { 1, 0.5f, 0.25f, 1 };
	private final FogSet[] sets = new FogSet[SET_PAGES.length];
	/** The set drawn, and its hearts and mists: one per panel. */
	private FogSet set;
	private Parallax_Heart[] hearts;
	private ParallaxLayer[] mists;
	/** s01's WAVE layers in every heart, and the amplitude each was saved with. */
	private final ParallaxLayer[] waves = new ParallaxLayer[8];
	private final float[] waveAmplitudes = new float[8];
	private int waveCount;
	private InputElement amplitude, wavelength, speed, height, scroll, ripple, order, haze, pick, size, rise;
	/** Every heart's layers back to front, while the lab hands its reader one at a time. */
	private final ArrayList<ParallaxLayer> drawn = new ArrayList<>();
	private final PatchHeightEffects[] effects = new PatchHeightEffects[4];
	private ShaderProgram plainHaze;
	/** Where the mist sits in s01's layers, 0 = at the back: the page's place, the slider's start. */
	private int pageMistIndex;
	/** s01's mist as saved: its size ratio. */
	private float mistSizeRatio;
	private Element readout;
	private boolean clip;
	private SpriteBatch batch;

	@Override
	public void create()
	{
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		batch = new SpriteBatch();
		for (int i = 0; i < effects.length; i++)
			effects[i] = new PatchHeightEffects(heights, i);
		for (int k = 0; k < sets.length; k++)
			sets[k] = new FogSet(k);
		pageMistIndex = sets[0].mistIndex;
		mistSizeRatio = sets[0].mists[0].getSizeRatio();
		show(sets[0]);
		heights[2] = SIMON[2];
		buildControls(SIMON[0], SIMON[1], sets[0].mists[0].getShaderSpeed());
		if (clip)
			exportAct(this);
	}

	/** One set: its page in four hearts, the panels' readers handed the panels' effects, each with a mist. */
	final class FogSet
	{
		final Parallax_Heart[] hearts = new Parallax_Heart[4];
		final ParallaxLayer[] mists = new ParallaxLayer[4];
		final int index;
		int mistIndex;
		/** What the sliders last put here: the mist moves only when they change. */
		float size = -1, rise = Float.NaN;

		FogSet(int index)
		{
			this.index = index;
			for (int i = 0; i < hearts.length; i++)
			{
				OrthographicCamera camera = new OrthographicCamera();
				camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
				hearts[i] = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
				hearts[i].setPage(Utils_Page_Json.loadPage(SET_PAGES[index]));
				hearts[i].parallaxReader.setLayerEffects(effects[i]);
				ArrayList<ParallaxLayer> layers = hearts[i].parallaxReader.layers;
				if (index == 0)
				{
					for (ParallaxLayer layer : layers)
						if ("mist".equals(layer.getName()))
							mists[i] = layer;
						else if (layer.getShaderEffect() == Enum_ShaderEffect.WAVE && layer.getKind() == Enum_LayerKind.SHADER)
						{
							waves[waveCount] = layer;
							waveAmplitudes[waveCount++] = layer.getShaderAmplitude();
						}
					if (mists[i] == null)
						throw new IllegalStateException(PAGE + " has no layer named mist");
				}
				else
				{
					mists[i] = sets[0].mists[i].clone();
					layers.add(Math.min(SET_MIST_INDEX[index], layers.size()), mists[i]);
				}
			}
			mistIndex = hearts[0].parallaxReader.layers.indexOf(mists[0]);
		}

		/** The frame's bottom below the panel's, in px: s01's page sits in the upper world, round1's at the bottom. */
		int frameDrop()
		{return index == 0 ? FRAME_HEIGHT - PANEL_HEIGHT : 0;}
	}

	private void show(FogSet next)
	{
		set = next;
		hearts = next.hearts;
		mists = next.mists;
	}

	private void buildControls(float a, float w, float s)
	{
		Document document = Document.get();
		Element labels = document.getElementById("fog-labels");
		for (int i = 0; i < TITLES.length; i++)
		{
			Element label = document.createDivElement();
			label.setClassName("panel-label");
			label.setInnerText(TITLES[i]);
			labels.appendChild(label);
		}
		Element controls = document.getElementById("fog-controls");
		amplitude = slider(document, controls, "mistAmplitude", "amplitude (thinning, 0..1)", 0, 1, 0.05f, a);
		wavelength = slider(document, controls, "mistWavelength", "wavelength (patch width, world units)", 0.5f, 20, 0.25f, w);
		speed = slider(document, controls, "mistSpeed", "speed (world units/s)", 0, 6, 0.1f, s);
		height = slider(document, controls, "patchHeightC", "C's patch height (x wavelength)", 0.05f, 1.5f, 0.05f, heights[2]);
		scroll = slider(document, controls, "cameraScroll", "camera scroll", 0, 240, 5, SIMON[3]);
		ripple = slider(document, controls, "treesWaveRipple", "trees' WAVE ripple (x the page's)", 0, 1, 0.05f, SIMON[4]);
		int last = hearts[0].parallaxReader.layers.size() - 1;
		order = slider(document, controls, "mistLayer", "mist's layer (0 = back, front = the set's last)", 0, last, 1, pageMistIndex);
		haze = slider(document, controls, "depthHaze", "depth haze (white per layer behind the mist)", 0, 0.6f, 0.05f, HAZE);
		pick = slider(document, controls, "parallaxSet", "parallax set", 0, sets.length - 1, 1, 0);
		size = slider(document, controls, "mistSize", "mist's size (x s01's: wider and taller)", 0.5f, 3, 0.05f, 1);
		rise = slider(document, controls, "mistRise", "mist up/down (world units from the set's place)", -12, 12, 0.25f, 0);
		readout = document.createDivElement();
		readout.setId("fog-readout");
		controls.appendChild(readout);
	}

	private static String text(float value)
	{return String.valueOf(Math.round(value * 100) / 100.0);}

	/** A slider row; {@code key} and the value it starts at go on the input, read by fog-lab.html's "Copy settings". */
	private static InputElement slider(Document document, Element into, String key, String name, float min, float max, float step, float value)
	{
		Element row = document.createLabelElement();
		row.setClassName("row");
		Element text = document.createSpanElement();
		text.setInnerText(name);
		InputElement input = document.createTextInputElement();
		input.setAttribute("type", "range");
		input.setAttribute("min", text(min));
		input.setAttribute("max", text(max));
		input.setAttribute("step", text(step));
		input.setValue(text(value));
		input.setAttribute("data-key", key);
		input.setAttribute("data-start", text(value));
		Element shown = document.createSpanElement();
		shown.setClassName("value");
		row.appendChild(text);
		row.appendChild(input);
		row.appendChild(shown);
		into.appendChild(row);
		return input;
	}

	private static float read(InputElement input)
	{
		float value = Float.parseFloat(input.getValue());
		((Element) input.getNextSibling()).setInnerText(input.getValue());
		return value;
	}

	/** Puts the sliders into every heart. */
	private void applyControls()
	{
		float a = read(amplitude), w = read(wavelength), s = read(speed);
		heights[2] = read(height);
		float sc = read(scroll), rp = read(ripple);
		FogSet next = sets[Math.round(read(pick))];
		((Element) pick.getNextSibling()).setInnerText(SET_NAMES[next.index]);
		if (next != set)
		{
			show(next);
			// The layer slider follows the set: its range, and where this set's mist is.
			order.setAttribute("max", String.valueOf(next.hearts[0].parallaxReader.layers.size() - 1));
			order.setValue(String.valueOf(next.mistIndex));
		}
		int last = hearts[0].parallaxReader.layers.size() - 1;
		int index = Math.min(Math.round(read(order)), last);
		if (index != set.mistIndex)
		{
			for (int i = 0; i < hearts.length; i++)
			{
				hearts[i].parallaxReader.layers.remove(mists[i]);
				hearts[i].parallaxReader.layers.add(index, mists[i]);
			}
			set.mistIndex = index;
		}
		float k0 = read(size), up = read(rise);
		if (k0 != set.size || up != set.rise)
		{
			// Grown about its middle: the band stays where it was, taller and wider.
			for (ParallaxLayer mist : mists)
			{
				mist.setSizeRatio(mistSizeRatio * k0);
				mist.setCurrentDistanceY(SET_MIST_MIDDLE[set.index] + up - mist.getHeight() / 2);
			}
			set.size = k0;
			set.rise = up;
		}
		for (int i = 0; i < waveCount; i++)
			waves[i].setShaderAmplitude(waveAmplitudes[i] * rp);
		float k = read(haze);
		for (PatchHeightEffects e : effects)
			e.hazePerLayer = k;
		for (int i = 0; i < hearts.length; i++)
		{
			mists[i].setShaderAmplitude(a);
			mists[i].setShaderWavelength(w);
			mists[i].setShaderSpeed(s);
			hearts[i].screenSpeedConstantX = sc;
		}
		float band = mists[0].getHeight() * mists[0].getPackedHeightRatio();
		readout.setInnerText(SET_NAMES[set.index] + ": mist band " + Math.round(band * 10) / 10f + " world units tall = "
				+ Math.round(band / w * 100) / 100f + " wavelengths; world " + Math.round(hearts[0].getWorldWidth()) + " wide; mist in front of "
				+ set.mistIndex + " of " + last + " layers");
	}

	/** Steps every heart by {@code seconds}, at 1/60 s: the clip's clock. */
	void act(float seconds)
	{
		applyControls();
		for (float t = 0; t < seconds - 1e-4f; t += 1 / 60f)
			for (Parallax_Heart heart : hearts)
				heart.act(1 / 60f);
	}

	/** Shows set {@code index}, as its slider does: tools/fog-lab.sh --sets. */
	void pick(int index)
	{
		pick.setValue(String.valueOf(index));
		applyControls();
	}

	private static native void exportAct(FogLab lab) /*-{
		$wnd.fogLabAct = $entry(function(seconds) {
			lab.@jks.tools2d.parallax.browsertest.FogLab::act(F)(seconds);
		});
		$wnd.fogLabPick = $entry(function(index) {
			lab.@jks.tools2d.parallax.browsertest.FogLab::pick(I)(index);
		});
		$wnd.fogLabReady = true;
	}-*/;

	@Override
	public void render()
	{
		if (!clip)
		{
			applyControls();
			float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 15f);
			for (Parallax_Heart heart : hearts)
				heart.act(delta);
		}
		int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
		Gdx.gl.glViewport(0, 0, width, height);
		Gdx.gl.glClearColor(0, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
		for (int i = 0; i < hearts.length; i++)
		{
			// A, B on top; C, no FOG below; a 4 px gap. GL's y goes up: a panel's top is its frame's top.
			int x = (i % 2) * (FRAME_WIDTH + 4), top = i < 2 ? height : height - PANEL_HEIGHT - 4;
			Gdx.gl.glScissor(x, top - PANEL_HEIGHT, FRAME_WIDTH, PANEL_HEIGHT);
			Gdx.gl.glViewport(x, top - PANEL_HEIGHT - set.frameDrop(), FRAME_WIDTH, FRAME_HEIGHT);
			renderHazed(hearts[i], effects[i]);
		}
		Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
		Gdx.gl.glViewport(0, 0, width, height);
	}

	/**
	 * Parallax_Heart.render, but the reader is handed one layer at a time, each drawn through a shader that mixes it
	 * toward the mist's white by {@link PatchHeightEffects#haze(int)}; the SHADER layers' own shaders do the same.
	 */
	private void renderHazed(Parallax_Heart heart, PatchHeightEffects effects)
	{
		ArrayList<ParallaxLayer> layers = heart.parallaxReader.layers;
		drawn.clear();
		drawn.addAll(layers);
		heart.worldCamera.update();
		heart.drawBackGround();
		batch.setProjectionMatrix(heart.worldCamera.combined);
		batch.enableBlending();
		batch.begin();
		for (int j = 0; j < drawn.size(); j++)
		{
			ParallaxLayer layer = drawn.get(j);
			effects.layerHaze = effects.haze(set.mistIndex - j);
			batch.setShader(plainHaze());
			batch.flush();
			plainHaze.setUniformf("u_haze", effects.layerHaze);
			plainHaze.setUniformf("u_fog", FOG_R, FOG_G, FOG_B);
			layers.clear();
			layers.add(layer);
			heart.parallaxReader.draw(heart.worldCamera, batch);
		}
		layers.clear();
		layers.addAll(drawn);
		batch.setShader(null);
		batch.end();
	}

	private ShaderProgram plainHaze()
	{
		if (plainHaze != null)
			return plainHaze;
		String source = "#ifdef GL_ES\nprecision mediump float;\n#endif\n"
				+ "varying vec4 v_color;\nvarying vec2 v_texCoords;\nuniform sampler2D u_texture;\n"
				+ "void main()\n{\n	gl_FragColor = v_color * texture2D(u_texture, v_texCoords);\n}\n";
		plainHaze = PatchHeightEffects.compile(PatchHeightEffects.hazed(source), "plain");
		return plainHaze;
	}

	/**
	 * The reader's effects, FOG compiled from GdxLayerEffects' own source with its patches scaled in y: p.y divided by
	 * {@code heights[panel]} wavelengths instead of one. WAVE stays GdxLayerEffects'. The last panel draws FOG plain.
	 */
	static final class PatchHeightEffects implements LayerEffects
	{
		static final String SHIPPED = "vec2 p = (local() + vec2(u_effect.z, 0.0)) / u_effect.y;";
		static final String SCALED = "vec2 p = (local() + vec2(u_effect.z, 0.0)) / (u_effect.y * vec2(1.0, u_height));";

		private final float[] heights;
		private final int panel;
		/** The haze slider, and the haze of the layer being drawn (set by renderHazed). */
		float hazePerLayer, layerHaze;
		private ShaderProgram wave;
		private final GdxLayerEffects shipped = new GdxLayerEffects();
		private final float[] numbers = new float[9];
		private ShaderProgram fog, previous;

		PatchHeightEffects(float[] heights, int panel)
		{
			this.heights = heights;
			this.panel = panel;
		}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{
			if (panel == 3)
				return layer.getShaderEffect() != Enum_ShaderEffect.FOG && shipped.begin(batch, layer, phase);
			ShaderProgram program = layer.getShaderEffect() == Enum_ShaderEffect.FOG ? fog() : wave();
			previous = batch.getShader();
			batch.setShader(program);
			if (!batch.isDrawing())
				program.bind();
			GdxLayerEffects.uniforms(layer, phase, numbers);
			program.setUniformf("u_region", numbers[0], numbers[1], numbers[2], numbers[3]);
			program.setUniformf("u_size", numbers[4], numbers[5]);
			program.setUniformf("u_effect", numbers[6], numbers[7], numbers[8]);
			if (layer.getShaderEffect() == Enum_ShaderEffect.FOG)
				program.setUniformf("u_height", heights[panel]);
			program.setUniformf("u_haze", layerHaze);
			program.setUniformf("u_fog", FOG_R, FOG_G, FOG_B);
			return true;
		}

		@Override
		public void end(Batch batch, ParallaxLayer layer)
		{
			if (panel == 3)
			{
				shipped.end(batch, layer);
				return;
			}
			batch.setShader(previous);
			previous = null;
		}

		private ShaderProgram fog()
		{
			if (fog != null)
				return fog;
			String source = GdxLayerEffects.fragment(Enum_ShaderEffect.FOG);
			if (!source.contains(SHIPPED))
				throw new IllegalStateException("GdxLayerEffects' FOG no longer has the line this lab scales: " + SHIPPED);
			source = source.replace(SHIPPED, SCALED).replace("uniform vec3 u_effect;\n", "uniform vec3 u_effect;\nuniform float u_height;\n");
			fog = compile(hazed(source), "FOG");
			return fog;
		}

		private ShaderProgram wave()
		{
			if (wave == null)
				wave = compile(hazed(GdxLayerEffects.fragment(Enum_ShaderEffect.WAVE)), "WAVE");
			return wave;
		}

		/** The haze a layer {@code behind} layers behind the mist takes: none in front of it or in the plain panel. */
		float haze(int behind)
		{return panel == 3 || behind <= 0 ? 0 : 1 - (float) Math.pow(1 - hazePerLayer, behind);}

		/** A fragment shader whose color is then mixed toward u_fog by u_haze, its alpha kept. */
		static String hazed(String source)
		{
			return source.replace("void main()", "uniform float u_haze;\nuniform vec3 u_fog;\nvec4 fragColor;\nvoid effectMain()")
					.replace("gl_FragColor", "fragColor")
					+ "void main()\n{\n	effectMain();\n	gl_FragColor = vec4(mix(fragColor.rgb, u_fog, u_haze), fragColor.a);\n}\n";
		}

		static ShaderProgram compile(String fragment, String name)
		{
			ShaderProgram program = new ShaderProgram(GdxLayerEffects.vertex(), fragment);
			if (!program.isCompiled())
				throw new IllegalStateException("the lab's hazed " + name + " does not compile: " + program.getLog());
			return program;
		}
	}
}
