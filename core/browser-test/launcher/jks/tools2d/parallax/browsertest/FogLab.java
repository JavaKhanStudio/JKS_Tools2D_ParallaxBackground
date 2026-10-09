package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.SelectElement;

import java.util.ArrayList;

import jks.tools2d.parallax.GdxLayerEffects;
import jks.tools2d.parallax.LayerEffects;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * The FOG lab (r204, doubt d15): the shaders round's s01 page, drawn four times by the real reader in WebGL, its mist
 * band's FOG patches as tall as they are wide (A, what ships), half as tall (B), as tall as the slider says (C, a
 * stored patch height), and without FOG. Opened with fog-lab.html: tools/fog-lab.sh. The sliders move
 * the mist's amplitude, wavelength and speed, and the scroll, in every panel at once, and the ripple of s01's two WAVE
 * tree layers (r208: Simon read them as "dancing"): their amplitude times the slider, and the mist's place among the
 * layers. It opens on the settings Simon sent on r208 ({@link #SIMON}: shape A, a lighter, wider mist, no ripple). Depth
 * fog (r217, Simon: "allow the fog to get harder, affecting each layer more intensely"): the page's fog, drawn by the
 * reader itself in panels A to C ({@link jks.tools2d.parallax.ParallaxPageReader#setFog}), each layer mixed toward the
 * fog's colour by 1 - exp(-strength (1 / speed - 1 / front)), its strength and colour on sliders; the readout gives each
 * layer's fog. Other sets (r208, Simon: "add some other parallax set to test"): round1's calm, PurpleFairy
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
	static final String[] TITLES = { "A: ships today (patches 3-4x taller than wide)", "B: half as tall (about round)", "C: patch height = slider", "no FOG, no page fog" };

	/** Simon's settings from r208 (d16 answered A): mist amplitude, wavelength, C's patch height, scroll, trees' ripple. */
	static final float[] SIMON = { 0.25f, 8.25f, 0.45f, 40, 0 };
	/** The fog's strength to begin with: s01's back layer (speed 0.01, front 0.041) 1 - exp(-0.03 x 75.6) = 0.90. */
	static final float FOG = 0.03f;
	/** The fog colours the slider picks from: the mist's white first. */
	static final String[] FOG_COLOR_NAMES = { "mist white", "night blue", "warm grey", "dusk pink" };
	static final Color[] FOG_COLORS = { new Color(WholePage_Model.FOG_R, WholePage_Model.FOG_G, WholePage_Model.FOG_B, 1),
			new Color(0.25f, 0.3f, 0.45f, 1), new Color(0.75f, 0.7f, 0.65f, 1), new Color(0.85f, 0.6f, 0.65f, 1) };

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
	private InputElement amplitude, wavelength, speed, height, scroll, ripple, order, fog, size, rise;
	private SelectElement fogColor, pick;
	private final PatchHeightEffects[] effects = new PatchHeightEffects[4];
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
		LabControls.panelLabels(document, "fog-labels", TITLES);
		Element controls = document.getElementById("fog-controls");
		amplitude = LabControls.slider(document, controls, "mistAmplitude", "amplitude (thinning, 0..1)", 0, 1, 0.05f, a);
		wavelength = LabControls.slider(document, controls, "mistWavelength", "wavelength (patch width, world units)", 0.5f, 20, 0.25f, w);
		speed = LabControls.slider(document, controls, "mistSpeed", "speed (world units/s)", 0, 6, 0.1f, s);
		height = LabControls.slider(document, controls, "patchHeightC", "C's patch height (x wavelength)", 0.05f, 1.5f, 0.05f, heights[2]);
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll", 0, 240, 5, SIMON[3]);
		ripple = LabControls.slider(document, controls, "treesWaveRipple", "trees' WAVE ripple (x the page's)", 0, 1, 0.05f, SIMON[4]);
		int last = hearts[0].parallaxReader.layers.size() - 1;
		order = LabControls.slider(document, controls, "mistLayer", "mist's layer (0 = back, front = the set's last)", 0, last, 1, pageMistIndex);
		fog = LabControls.slider(document, controls, "fogStrength", "page fog strength (1 - exp(-s (1/speed - 1/front)))", 0, 0.2f, 0.0025f, FOG);
		fogColor = LabControls.choice(document, controls, "fogColor", "page fog colour", FOG_COLOR_NAMES, 0);
		pick = LabControls.choice(document, controls, "parallaxSet", "parallax set", SET_NAMES, 0);
		size = LabControls.slider(document, controls, "mistSize", "mist's size (x s01's: wider and taller)", 0.5f, 3, 0.05f, 1);
		rise = LabControls.slider(document, controls, "mistRise", "mist up/down (world units from the set's place)", -12, 12, 0.25f, 0);
		readout = LabControls.readout(document, controls, "fog-readout");
	}




	/** Puts the sliders into every heart. */
	private void applyControls()
	{
		float a = LabControls.read(amplitude), w = LabControls.read(wavelength), s = LabControls.read(speed);
		heights[2] = LabControls.read(height);
		float sc = LabControls.read(scroll), rp = LabControls.read(ripple);
		FogSet next = sets[LabControls.read(pick)];
		if (next != set)
		{
			show(next);
			// The layer slider follows the set: its range, and where this set's mist is.
			order.setAttribute("max", String.valueOf(next.hearts[0].parallaxReader.layers.size() - 1));
			order.setValue(String.valueOf(next.mistIndex));
		}
		int last = hearts[0].parallaxReader.layers.size() - 1;
		int index = Math.min(Math.round(LabControls.read(order)), last);
		if (index != set.mistIndex)
		{
			for (int i = 0; i < hearts.length; i++)
			{
				hearts[i].parallaxReader.layers.remove(mists[i]);
				hearts[i].parallaxReader.layers.add(index, mists[i]);
			}
			set.mistIndex = index;
		}
		float k0 = LabControls.read(size), up = LabControls.read(rise);
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
		float strength = LabControls.read(fog);
		int colour = LabControls.read(fogColor);
		for (int i = 0; i < hearts.length; i++)
		{
			// The last panel: no FOG layer, no fog.
			hearts[i].parallaxReader.setFog(i == 3 ? 0 : strength, FOG_COLORS[colour]);
			mists[i].setShaderAmplitude(a);
			mists[i].setShaderWavelength(w);
			mists[i].setShaderSpeed(s);
			hearts[i].screenSpeedConstantX = sc;
		}
		float band = mists[0].getHeight() * mists[0].getPackedHeightRatio();
		readout.setInnerText(SET_NAMES[set.index] + ": mist band " + Math.round(band * 10) / 10f + " world units tall = "
				+ Math.round(band / w * 100) / 100f + " wavelengths; world " + Math.round(hearts[0].getWorldWidth()) + " wide; mist in front of "
				+ set.mistIndex + " of " + last + " layers; fog back to front:" + fogs());
	}

	/** Each layer's fog in panel A, back to front, as the reader draws it. */
	private String fogs()
	{
		StringBuilder out = new StringBuilder();
		ArrayList<ParallaxLayer> layers = hearts[0].parallaxReader.layers;
		float front = ParallaxPageReader.frontSpeedOf(layers);
		for (ParallaxLayer layer : layers)
			out.append(' ').append(Math.round(100 * ParallaxPageReader.fogOf(hearts[0].parallaxReader.getFogStrength(), front, layer)) / 100f);
		return out.toString();
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
			hearts[i].render();
		}
		Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
		Gdx.gl.glViewport(0, 0, width, height);
	}

	/**
	 * The reader's effects, FOG compiled from GdxLayerEffects' own source with its patches scaled in y: p.y divided by
	 * {@code heights[panel]} wavelengths instead of one. WAVE stays GdxLayerEffects'. The last panel draws FOG plain. An
	 * IMAGE or SEQUENCE layer the fog reaches is drawn through GdxLayerEffects' own.
	 */
	static final class PatchHeightEffects implements LayerEffects
	{
		static final String SHIPPED = "vec2 p = (local() + vec2(u_effect.z, 0.0)) / u_effect.y;";
		static final String SCALED = "vec2 p = (local() + vec2(u_effect.z, 0.0)) / (u_effect.y * vec2(1.0, u_height));";

		private final float[] heights;
		private final int panel;
		private ShaderProgram wave;
		/** The layer begun is drawn by {@link #shipped}. */
		private boolean byShipped;
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
		{return begin(batch, layer, phase, 0, FOG_COLORS[0]);}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase, float fogAmount, Color fogColor)
		{
			byShipped = panel == 3 || layer.getKind() != Enum_LayerKind.SHADER;
			if (byShipped)
				return (layer.getKind() != Enum_LayerKind.SHADER || layer.getShaderEffect() != Enum_ShaderEffect.FOG)
						&& shipped.begin(batch, layer, phase, fogAmount, fogColor);
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
			program.setUniformf("u_haze", fogAmount);
			program.setUniformf("u_fog", fogColor.r, fogColor.g, fogColor.b);
			return true;
		}

		@Override
		public void end(Batch batch, ParallaxLayer layer)
		{
			if (byShipped)
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
			fog = compile(source, "FOG");
			return fog;
		}

		private ShaderProgram wave()
		{
			if (wave == null)
				wave = compile(GdxLayerEffects.fragment(Enum_ShaderEffect.WAVE), "WAVE");
			return wave;
		}

		static ShaderProgram compile(String fragment, String name)
		{
			ShaderProgram program = new ShaderProgram(GdxLayerEffects.vertex(), fragment);
			if (!program.isCompiled())
				throw new IllegalStateException("the lab's fogged " + name + " does not compile: " + program.getLog());
			return program;
		}
	}
}
