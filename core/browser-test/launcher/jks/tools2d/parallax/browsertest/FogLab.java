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
 * layers. It opens on the settings Simon sent on r208 ({@link #SIMON}: shape A, a lighter, wider mist, no ripple). "Copy settings"
 * (fog-lab.html) puts every slider, and what it started at, on the clipboard as JSON, for Simon to paste into a card.
 * <p>
 * {@code &clip=1}: nothing moves by itself, {@code window.fogLabAct(seconds)} steps every heart at 1/60 s, so that
 * tools/fog-lab.sh --clip records it at game speed however slow the browser draws.
 */
public class FogLab extends ApplicationAdapter
{
	static final String PAGE = "fog-lab/s01.jplax";
	/** The round's world: 40 wide, 16:9, as tools/parallax-lab-shots.sh draws s01. */
	static final float WORLD_WIDTH = 40, WORLD_HEIGHT = 22.5f;
	/** Each panel: the round's 16:9 frame, cut below the page (s01 leaves its lower 42% to the white gradient). */
	static final int FRAME_WIDTH = 760, FRAME_HEIGHT = 428, PANEL_HEIGHT = 250;
	static final String[] TITLES = { "A: ships today (patches 3-4x taller than wide)", "B: half as tall (about round)", "C: patch height = slider", "no FOG" };

	/** Simon's settings from r208 (d16 answered A): mist amplitude, wavelength, C's patch height, scroll, trees' ripple. */
	static final float[] SIMON = { 0.25f, 8.25f, 0.45f, 40, 0 };

	/** The FOG patch height per panel, in wavelengths; C's comes from its slider, the last panel draws plain. */
	private final float[] heights = { 1, 0.5f, 0.25f, 1 };
	private final Parallax_Heart[] hearts = new Parallax_Heart[4];
	private final ParallaxLayer[] mists = new ParallaxLayer[4];
	/** s01's WAVE layers in every heart, and the amplitude each was saved with. */
	private final ParallaxLayer[] waves = new ParallaxLayer[8];
	private final float[] waveAmplitudes = new float[8];
	private int waveCount;
	private InputElement amplitude, wavelength, speed, height, scroll, ripple, order;
	/** Where the mist sits in every heart's layers, 0 = at the back: the page's place to begin with. */
	private int mistIndex, pageMistIndex;
	private Element readout;
	private boolean clip;
	private SpriteBatch batch;

	@Override
	public void create()
	{
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		for (int i = 0; i < hearts.length; i++)
		{
			OrthographicCamera camera = new OrthographicCamera();
			camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
			if (batch == null)
				batch = new SpriteBatch();
			hearts[i] = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i].setPage(Utils_Page_Json.loadPage(PAGE));
			hearts[i].parallaxReader.setLayerEffects(new PatchHeightEffects(heights, i));
			for (ParallaxLayer layer : hearts[i].parallaxReader.layers)
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
		pageMistIndex = mistIndex = hearts[0].parallaxReader.layers.indexOf(mists[0]);
		ParallaxLayer mist = mists[0];
		heights[2] = SIMON[2];
		buildControls(SIMON[0], SIMON[1], mist.getShaderSpeed());
		if (clip)
			exportAct(this);
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
		order = slider(document, controls, "mistLayer", "mist's layer (0 = back, " + last + " = front; page: " + pageMistIndex + ")", 0, last, 1, pageMistIndex);
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
		int index = Math.round(read(order));
		if (index != mistIndex)
		{
			for (int i = 0; i < hearts.length; i++)
			{
				hearts[i].parallaxReader.layers.remove(mists[i]);
				hearts[i].parallaxReader.layers.add(index, mists[i]);
			}
			mistIndex = index;
		}
		for (int i = 0; i < waveCount; i++)
			waves[i].setShaderAmplitude(waveAmplitudes[i] * rp);
		for (int i = 0; i < hearts.length; i++)
		{
			mists[i].setShaderAmplitude(a);
			mists[i].setShaderWavelength(w);
			mists[i].setShaderSpeed(s);
			hearts[i].screenSpeedConstantX = sc;
		}
		float band = mists[0].getHeight() * mists[0].getPackedHeightRatio();
		readout.setInnerText("mist band: " + Math.round(band * 10) / 10f + " world units tall = "
				+ Math.round(band / w * 100) / 100f + " wavelengths; world " + Math.round(hearts[0].getWorldWidth()) + " wide; mist in front of "
				+ mistIndex + " of " + (hearts[0].parallaxReader.layers.size() - 1) + " layers");
	}

	/** Steps every heart by {@code seconds}, at 1/60 s: the clip's clock. */
	void act(float seconds)
	{
		applyControls();
		for (float t = 0; t < seconds - 1e-4f; t += 1 / 60f)
			for (Parallax_Heart heart : hearts)
				heart.act(1 / 60f);
	}

	private static native void exportAct(FogLab lab) /*-{
		$wnd.fogLabAct = $entry(function(seconds) {
			lab.@jks.tools2d.parallax.browsertest.FogLab::act(F)(seconds);
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
			Gdx.gl.glViewport(x, top - FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);
			hearts[i].render();
		}
		Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
		Gdx.gl.glViewport(0, 0, width, height);
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
			if (layer.getShaderEffect() != Enum_ShaderEffect.FOG)
				return shipped.begin(batch, layer, phase);
			if (panel == 3)
				return false;
			ShaderProgram program = fog();
			previous = batch.getShader();
			batch.setShader(program);
			if (!batch.isDrawing())
				program.bind();
			GdxLayerEffects.uniforms(layer, phase, numbers);
			program.setUniformf("u_region", numbers[0], numbers[1], numbers[2], numbers[3]);
			program.setUniformf("u_size", numbers[4], numbers[5]);
			program.setUniformf("u_effect", numbers[6], numbers[7], numbers[8]);
			program.setUniformf("u_height", heights[panel]);
			return true;
		}

		@Override
		public void end(Batch batch, ParallaxLayer layer)
		{
			if (layer.getShaderEffect() != Enum_ShaderEffect.FOG)
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
			fog = new ShaderProgram(GdxLayerEffects.vertex(), source);
			if (!fog.isCompiled())
				throw new IllegalStateException("the lab's FOG does not compile: " + fog.getLog());
			return fog;
		}
	}
}
