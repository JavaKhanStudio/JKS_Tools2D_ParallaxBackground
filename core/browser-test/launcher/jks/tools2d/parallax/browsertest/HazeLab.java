package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;

import java.util.ArrayList;

import jks.tools2d.parallax.GdxLayerEffects;
import jks.tools2d.parallax.LayerEffects;
import jks.tools2d.parallax.LayerHook;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;
import jks.tools2d.parallax.pages.Utils_Page_Json;

/**
 * The haze lab (r215, doubt d17): the depth haze (r211) leaves EMPTY and PARTICLES layers as they are, and mixes toward
 * an untinted white when the page is tinted; this shows the two other ways side by side. Its page
 * (core/test-data/haze/make_lab.py) is the haze round's h01 with a game's towers (an EMPTY layer, drawn by the lab's hook)
 * and p01's snow (a PARTICLES layer), both behind the mist. A draws it as the reader ships; B hazes the towers and the
 * snow too; C mixes toward the mist's white times the page's tint; D does both. A is Parallax_Heart.render as is; B to D
 * hand the reader one layer at a time, each through the shipped shaders with the mist's white made a uniform, as
 * {@link FogLab} does. Sliders: the mist's haze (stored on it: A moves too), the tint and how strong, the camera's scroll,
 * the towers' brightness. Opened with haze-lab.html: tools/haze-lab.sh.
 * <p>
 * {@code &clip=1}: nothing moves by itself, {@code window.hazeLabAct(seconds)} steps every heart at 1/60 s.
 * {@code &same=1}: B to D draw as A does (no hook haze, untinted white) and the snow is left out (random in each panel),
 * so tools/haze-lab.sh --same can prove the lab's one-layer-at-a-time drawing is the reader's own.
 */
public class HazeLab extends ApplicationAdapter
{
	static final String PAGE = "haze-lab/lab.jplax";
	static final String TOWERS = "towers";
	static final float WORLD_WIDTH = FogLab.WORLD_WIDTH, WORLD_HEIGHT = FogLab.WORLD_HEIGHT;
	static final int FRAME_WIDTH = FogLab.FRAME_WIDTH, FRAME_HEIGHT = FogLab.FRAME_HEIGHT, PANEL_HEIGHT = FogLab.PANEL_HEIGHT;
	/** The s01 page sits in the upper world: the frame's bottom is this far below the panel's. */
	static final int FRAME_DROP = FRAME_HEIGHT - PANEL_HEIGHT;
	static final String[] TITLES = { "A: ships today (towers and snow not hazed, white untinted)", "B: towers and snow hazed too",
			"C: haze white x the page's tint", "D: both (B and C)" };
	/** Per panel: are EMPTY and PARTICLES layers hazed, is the haze's white tinted. */
	static final boolean[] HAZE_HOOKS = { false, true, false, true };
	static final boolean[] TINT_WHITE = { false, false, true, true };
	/** The tints the slider picks from: the haze round's orange (h05), a night blue, a dusk pink, a sick green. */
	static final String[] TINT_NAMES = { "none", "orange (h05)", "night blue", "dusk pink", "green" };
	static final float[][] TINTS = { { 1, 1, 1 }, { 1, 0.55f, 0.3f }, { 0.45f, 0.55f, 1 }, { 1, 0.6f, 0.75f }, { 0.6f, 1, 0.5f } };
	/** What the lab opens on: the round's haze, untinted (a tint multiplies every layer, the snow too: r224), scrolled 40 units/s, dark towers. */
	static final float HAZE = 0.25f, TINT = 0, STRENGTH = 1, SCROLL = 40, TOWERS_LIGHT = 0.15f;

	private final Parallax_Heart[] hearts = new Parallax_Heart[4];
	private final ParallaxLayer[] mists = new ParallaxLayer[4];
	private final LabEffects[] effects = new LabEffects[4];
	private final ArrayList<ParallaxLayer> drawn = new ArrayList<>();
	private final Color tint = new Color(Color.WHITE);
	private final float[] white = new float[3];
	private InputElement haze, tintPick, strength, scroll, light;
	private Element readout;
	private ShaderProgram plain;
	private SpriteBatch batch;
	private Texture pixel;
	private TextureRegion block;
	private float towersLight = TOWERS_LIGHT;
	private boolean clip, same;

	@Override
	public void create()
	{
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		same = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("same"));
		batch = new SpriteBatch();
		Pixmap white = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		white.setColor(Color.WHITE);
		white.fill();
		pixel = new Texture(white);
		white.dispose();
		block = new TextureRegion(pixel);
		LayerHook towers = new LayerHook()
		{
			/** Each tower: left and width in the tile's width, height in its height. */
			final float[] shape = { 0.05f, 0.12f, 0.9f, 0.25f, 0.08f, 0.55f, 0.45f, 0.18f, 1.0f, 0.75f, 0.1f, 0.7f };

			@Override
			public void draw(Batch batch, ParallaxLayer layer, float x, float y, float width, float height)
			{
				// The batch's color is the page's tint at the layer's opacity: multiplied in, as a game would.
				Color c = batch.getColor();
				batch.setColor(c.r * towersLight, c.g * towersLight * 0.9f, c.b * towersLight * 1.3f, c.a);
				for (int i = 0; i < shape.length; i += 3)
					batch.draw(block, x + shape[i] * width, y, shape[i + 1] * width, shape[i + 2] * height);
			}
		};
		for (int i = 0; i < hearts.length; i++)
		{
			OrthographicCamera camera = new OrthographicCamera();
			camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i] = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i].setPage(Utils_Page_Json.loadPage(PAGE));
			hearts[i].parallaxReader.setLayerHook(TOWERS, towers);
			if (i > 0)
				hearts[i].parallaxReader.setLayerEffects(effects[i] = new LabEffects());
			ArrayList<ParallaxLayer> layers = hearts[i].parallaxReader.layers;
			// The snow is random in each panel: drawn the same, it would still differ.
			if (same)
				for (int j = layers.size() - 1; j >= 0; j--)
					if (layers.get(j).getKind() == Enum_LayerKind.PARTICLES)
						layers.remove(j);
			for (ParallaxLayer layer : layers)
				if ("mist".equals(layer.getName()))
					mists[i] = layer;
			if (mists[i] == null)
				throw new IllegalStateException(PAGE + " has no layer named mist");
		}
		buildControls();
		if (clip)
			exportAct(this);
	}

	private void buildControls()
	{
		Document document = Document.get();
		Element labels = document.getElementById("haze-labels");
		for (String title : TITLES)
		{
			Element label = document.createDivElement();
			label.setClassName("panel-label");
			label.setInnerText(same ? title + " (same: drawn as A)" : title);
			labels.appendChild(label);
		}
		Element controls = document.getElementById("haze-controls");
		haze = slider(document, controls, "depthHaze", "the mist's depth haze (white per layer behind it)", 0, 0.6f, 0.05f, HAZE);
		tintPick = slider(document, controls, "pageTint", "page tint", 0, TINTS.length - 1, 1, TINT);
		strength = slider(document, controls, "tintStrength", "tint strength (0 = white)", 0, 1, 0.05f, STRENGTH);
		scroll = slider(document, controls, "cameraScroll", "camera scroll", 0, 240, 5, SCROLL);
		light = slider(document, controls, "towersLight", "towers' brightness (the hook's color)", 0, 1, 0.05f, TOWERS_LIGHT);
		readout = document.createDivElement();
		readout.setId("haze-readout");
		controls.appendChild(readout);
	}

	private static String text(float value)
	{return String.valueOf(Math.round(value * 100) / 100.0);}

	/** A slider row; {@code key} and the value it starts at go on the input, read by haze-lab.html's "Copy settings". */
	private static InputElement slider(Document document, Element into, String key, String name, float min, float max, float step, float value)
	{
		Element row = document.createLabelElement();
		row.setClassName("row");
		Element label = document.createSpanElement();
		label.setInnerText(name);
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
		row.appendChild(label);
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
		float h = read(haze), k = read(strength), sc = read(scroll);
		int pick = Math.round(read(tintPick));
		((Element) tintPick.getNextSibling()).setInnerText(TINT_NAMES[pick]);
		towersLight = read(light);
		float[] t = TINTS[pick];
		tint.set(1 + (t[0] - 1) * k, 1 + (t[1] - 1) * k, 1 + (t[2] - 1) * k, 1);
		for (int i = 0; i < hearts.length; i++)
		{
			mists[i].setShaderHaze(h);
			hearts[i].parallaxReader.addColorTransfert(tint, 0);
			hearts[i].screenSpeedConstantX = sc;
		}
		StringBuilder line = new StringBuilder("haze behind the mist: ");
		ArrayList<ParallaxLayer> layers = hearts[0].parallaxReader.layers;
		for (int j = 0; j < layers.size(); j++)
		{
			ParallaxLayer layer = layers.get(j);
			float of = ParallaxPageReader.hazeOf(layers, j);
			if (of <= 0)
				continue;
			String name = layer.getName() != null ? layer.getName() : layer.getKind() == Enum_LayerKind.SHADER ? "wave" : "image";
			line.append(name).append(' ').append(text(of)).append(hooked(layer) ? " (A and C: none)" : "").append(", ");
		}
		line.append("tint ").append(text(tint.r)).append(' ').append(text(tint.g)).append(' ').append(text(tint.b))
				.append("; haze white in C and D ").append(text(GdxLayerEffects.HAZE_R * tint.r)).append(' ')
				.append(text(GdxLayerEffects.HAZE_G * tint.g)).append(' ').append(text(GdxLayerEffects.HAZE_B * tint.b));
		readout.setInnerText(line.toString());
	}

	private static boolean hooked(ParallaxLayer layer)
	{return layer.getKind() == Enum_LayerKind.EMPTY || layer.getKind() == Enum_LayerKind.PARTICLES;}

	/** Steps every heart by {@code seconds}, at 1/60 s: the clip's clock. */
	void act(float seconds)
	{
		applyControls();
		for (float t = 0; t < seconds - 1e-4f; t += 1 / 60f)
			for (Parallax_Heart heart : hearts)
				heart.act(1 / 60f);
	}

	private static native void exportAct(HazeLab lab) /*-{
		$wnd.hazeLabAct = $entry(function(seconds) {
			lab.@jks.tools2d.parallax.browsertest.HazeLab::act(F)(seconds);
		});
		$wnd.hazeLabReady = true;
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
			// A, B on top; C, D below; a 4 px gap. GL's y goes up: a panel's top is its frame's top.
			int x = (i % 2) * (FRAME_WIDTH + 4), top = i < 2 ? height : height - PANEL_HEIGHT - 4;
			Gdx.gl.glScissor(x, top - PANEL_HEIGHT, FRAME_WIDTH, PANEL_HEIGHT);
			Gdx.gl.glViewport(x, top - PANEL_HEIGHT - FRAME_DROP, FRAME_WIDTH, FRAME_HEIGHT);
			if (i == 0)
				hearts[i].render();
			else
				renderLab(hearts[i], effects[i], !same && HAZE_HOOKS[i], !same && TINT_WHITE[i]);
		}
		Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
		Gdx.gl.glViewport(0, 0, width, height);
	}

	/**
	 * Parallax_Heart.render, the reader handed one layer at a time: each drawn through a plain shader, or its effect's,
	 * hazed by {@link ParallaxPageReader#hazeOf} over the whole page (an EMPTY or PARTICLES layer too when
	 * {@code hazeHooks}), toward the mist's white, times the page's tint when {@code tintWhite}.
	 */
	private void renderLab(Parallax_Heart heart, LabEffects effects, boolean hazeHooks, boolean tintWhite)
	{
		white[0] = GdxLayerEffects.HAZE_R;
		white[1] = GdxLayerEffects.HAZE_G;
		white[2] = GdxLayerEffects.HAZE_B;
		if (tintWhite)
		{
			white[0] *= tint.r;
			white[1] *= tint.g;
			white[2] *= tint.b;
		}
		effects.white = white;
		ArrayList<ParallaxLayer> layers = heart.parallaxReader.layers;
		drawn.clear();
		drawn.addAll(layers);
		heart.worldCamera.update();
		heart.drawBackGround();
		batch.setProjectionMatrix(heart.worldCamera.combined);
		batch.enableBlending();
		batch.begin();
		ShaderProgram program = plain();
		for (int j = 0; j < drawn.size(); j++)
		{
			ParallaxLayer layer = drawn.get(j);
			effects.haze = hooked(layer) && !hazeHooks ? 0 : ParallaxPageReader.hazeOf(drawn, j);
			batch.setShader(program);
			batch.flush();
			program.setUniformf("u_haze", effects.haze);
			program.setUniformf("u_white", white[0], white[1], white[2]);
			layers.clear();
			layers.add(layer);
			heart.parallaxReader.draw(heart.worldCamera, batch);
		}
		layers.clear();
		layers.addAll(drawn);
		batch.setShader(null);
		batch.end();
	}

	private ShaderProgram plain()
	{
		if (plain == null)
			plain = LabEffects.compile(null);
		return plain;
	}

	/**
	 * The reader's effects, compiled from GdxLayerEffects' own sources with the mist's white a uniform, u_white, and the
	 * haze the lab's, not the reader's (it is handed one layer, so it reckons none).
	 */
	static final class LabEffects implements LayerEffects
	{
		static final String SHIPPED = "const vec3 HAZE = vec3(0.93, 0.95, 0.97);\n";
		static final String UNIFORM = "uniform vec3 u_white;\n#define HAZE u_white\n";

		float haze;
		float[] white;
		private final ShaderProgram[] programs = new ShaderProgram[Enum_ShaderEffect.values().length];
		private final float[] numbers = new float[9];
		private ShaderProgram previous;

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{return begin(batch, layer, phase, 0);}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase, float ignored)
		{
			Enum_ShaderEffect effect = layer.getShaderEffect();
			if (effect == null)
				return false;
			ShaderProgram program = programs[effect.ordinal()];
			if (program == null)
				program = programs[effect.ordinal()] = compile(effect);
			previous = batch.getShader();
			batch.setShader(program);
			if (!batch.isDrawing())
				program.bind();
			GdxLayerEffects.uniforms(layer, phase, numbers);
			program.setUniformf("u_region", numbers[0], numbers[1], numbers[2], numbers[3]);
			program.setUniformf("u_size", numbers[4], numbers[5]);
			program.setUniformf("u_effect", numbers[6], numbers[7], numbers[8]);
			program.setUniformf("u_haze", haze);
			program.setUniformf("u_white", white[0], white[1], white[2]);
			return true;
		}

		@Override
		public void end(Batch batch, ParallaxLayer layer)
		{
			batch.setShader(previous);
			previous = null;
		}

		/** GdxLayerEffects' fragment for {@code effect} (null: PLAIN), its white made u_white. */
		static ShaderProgram compile(Enum_ShaderEffect effect)
		{
			String source = GdxLayerEffects.fragment(effect);
			if (!source.contains(SHIPPED))
				throw new IllegalStateException("GdxLayerEffects no longer has the line this lab makes a uniform: " + SHIPPED);
			ShaderProgram program = new ShaderProgram(GdxLayerEffects.vertex(), source.replace(SHIPPED, UNIFORM));
			if (!program.isCompiled())
				throw new IllegalStateException("the haze lab's " + effect + " does not compile: " + program.getLog());
			return program;
		}
	}
}
