package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.ParticleEmitter;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;

import java.util.ArrayList;

import jks.tools2d.parallax.LayerHook;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ParticleAnchor;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * The effects labs (r224, Simon: "one lab per feature ... both should be testable together in one lab, with every other
 * confirmed effect, but they should also both have their own lab, so you can properly make them work and optimize
 * them"). One launcher, three labs, picked by {@code ?lab=}: {@code fog}, the page fog (r217) alone on four parallax
 * sets; {@code snow}, a PARTICLES layer's snow alone; {@code all}, every effect that ships together on one page (the
 * page fog, the FOG mist, the trees' WAVE, a game's towers in an EMPTY layer, the snow). Each draws its page twice by
 * the real reader, Parallax_Heart.render as a game calls it: left with the feature, right without, the rest the same.
 * The readout gives what it costs: the frame time, each panel's draw calls and CPU time, the live flakes. Opened with
 * effects-lab.html: tools/effects-lab.sh; "Copy settings" puts every slider, and what it started at, on the clipboard.
 * <p>
 * {@code &clip=1}: nothing moves by itself, {@code window.effectsLabAct(seconds)} steps both hearts at 1/60 s.
 */
public class EffectsLab extends ApplicationAdapter
{
	static final float WORLD_WIDTH = FogLab.WORLD_WIDTH, WORLD_HEIGHT = FogLab.WORLD_HEIGHT;
	static final int FRAME_WIDTH = FogLab.FRAME_WIDTH, FRAME_HEIGHT = FogLab.FRAME_HEIGHT, PANEL_HEIGHT = FogLab.PANEL_HEIGHT;
	/** The haze lab's page: the shaders round's s01 with towers (EMPTY) and the particles round's snow (PARTICLES). */
	static final String ALL_PAGE = "haze-lab/lab.jplax";
	static final String TOWERS = "towers", MIST = "mist";
	/** The fog lab's sets: s01, then round1's calm, PurpleFairy and OneNight, as the FOG lab has them. */
	static final String[] FOG_PAGES = FogLab.SET_PAGES;
	static final String[] FOG_NAMES = FogLab.SET_NAMES;
	/** What the labs open on: s01's back layer 1 - exp(-0.03 x 75.6) = 0.90 fogged, the mist's white, 40 units/s. */
	static final float FOG = 0.03f, SCROLL = 40;

	enum Lab
	{
		FOG("page fog", "with the page fog", "without"), SNOW("snow", "with the snow", "without"), ALL("every effect", "every effect", "none (the plain images)");

		final String feature, left, right;

		Lab(String feature, String left, String right)
		{
			this.feature = feature;
			this.left = left;
			this.right = right;
		}
	}

	/** One page in two hearts: the left draws the feature, the right does not. */
	final class Set
	{
		final Parallax_Heart[] hearts = new Parallax_Heart[2];
		final int index;
		/** The frame's bottom below the panel's, in px: s01 sits in the upper world, round1's pages at the bottom. */
		final int frameDrop;

		Set(int index, String page, boolean upper)
		{
			this.index = index;
			frameDrop = upper ? FRAME_HEIGHT - PANEL_HEIGHT : 0;
			for (int i = 0; i < hearts.length; i++)
			{
				OrthographicCamera camera = new OrthographicCamera();
				camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
				hearts[i] = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
				hearts[i].setPage(Utils_Page_Json.loadPage(page));
				hearts[i].parallaxReader.setLayerHook(TOWERS, towers);
				// The page's own fog (s01's haze lab page stores a format 9 haze, read as a strength): the sliders' instead.
				hearts[i].parallaxReader.setFog(0, null);
				keepOnly(hearts[i].parallaxReader.layers, i == 0);
			}
		}

		/** Takes out of {@code layers} what this lab does not show; {@code left}: the panel with the feature. */
		private void keepOnly(ArrayList<ParallaxLayer> layers, boolean left)
		{
			for (int j = layers.size() - 1; j >= 0; j--)
			{
				ParallaxLayer layer = layers.get(j);
				boolean snow = layer.getKind() == Enum_LayerKind.PARTICLES, mist = MIST.equals(layer.getName());
				boolean hook = layer.getKind() == Enum_LayerKind.EMPTY;
				boolean wave = layer.getKind() == Enum_LayerKind.SHADER && layer.getShaderEffect() == Enum_ShaderEffect.WAVE;
				boolean keep;
				if (lab == Lab.SNOW)
					keep = !mist && !hook && (!snow || left);
				else if (lab == Lab.FOG)
					keep = !mist && !hook && !snow;
				else
					keep = left || !(snow || mist || hook);
				if (!keep)
					layers.remove(j);
				else if (wave && (lab != Lab.ALL || !left))
					// A WAVE layer with no amplitude draws its image as an IMAGE layer would.
					layer.setShaderAmplitude(0);
				else if (wave)
					waves.add(layer);
			}
		}
	}

	private Lab lab;
	private boolean clip;
	private SpriteBatch batch;
	private Texture pixel;
	private TextureRegion block;
	private LayerHook towers;
	private Set[] sets;
	private Set set;
	private final ArrayList<ParallaxLayer> waves = new ArrayList<>();
	private final ArrayList<Float> waveAmplitudes = new ArrayList<>();
	private final Color fogColor = new Color();
	private InputElement fog, fogR, fogG, fogB, scroll, pick, density, size, wind, snowLayer, snowSpeed, anchor, mist, ripple;
	private Element readout;
	/** The snow's emitters as the .p has them: emission high min/max, max count, X scale high min/max. */
	private float[] snowBase;
	private float appliedDensity = -1, appliedSize = -1, appliedWind = Float.NaN;
	/** Frame time and per-panel cost, averaged over the last second. */
	private float frameSeconds, frames;
	private final float[] panelMillis = new float[2];
	private final int[] panelCalls = new int[2];
	private String costLine = "";

	@Override
	public void create()
	{
		String which = com.google.gwt.user.client.Window.Location.getParameter("lab");
		lab = "snow".equals(which) ? Lab.SNOW : "all".equals(which) ? Lab.ALL : Lab.FOG;
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		batch = new SpriteBatch();
		Pixmap white = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		white.setColor(Color.WHITE);
		white.fill();
		pixel = new Texture(white);
		white.dispose();
		block = new TextureRegion(pixel);
		towers = new LayerHook()
		{
			/** Each tower: left and width in the tile's width, height in its height; dark, as the haze lab's. */
			final float[] shape = { 0.05f, 0.12f, 0.9f, 0.25f, 0.08f, 0.55f, 0.45f, 0.18f, 1.0f, 0.75f, 0.1f, 0.7f };

			@Override
			public void draw(Batch batch, ParallaxLayer layer, float x, float y, float width, float height)
			{
				Color c = batch.getColor();
				batch.setColor(c.r * 0.15f, c.g * 0.135f, c.b * 0.195f, c.a);
				for (int i = 0; i < shape.length; i += 3)
					batch.draw(block, x + shape[i] * width, y, shape[i + 1] * width, shape[i + 2] * height);
			}
		};
		if (lab == Lab.FOG)
		{
			sets = new Set[FOG_PAGES.length];
			for (int k = 0; k < sets.length; k++)
				sets[k] = new Set(k, FOG_PAGES[k], k == 0);
		}
		else
			sets = new Set[] { new Set(0, ALL_PAGE, true) };
		set = sets[0];
		for (ParallaxLayer wave : waves)
			waveAmplitudes.add(wave.getShaderAmplitude());
		snowBase = snowBase();
		buildControls();
		if (clip)
			exportAct(this);
	}

	/** The first snow emitter's numbers as loaded, or null when the lab has no snow. */
	private float[] snowBase()
	{
		ParallaxLayer snow = snow(set.hearts[0]);
		if (snow == null || snow.getParticles() == null)
			return null;
		ParticleEmitter e = snow.getParticles().getEmitters().first();
		return new float[] { e.getEmission().getHighMin(), e.getEmission().getHighMax(), e.getMaxParticleCount(), e.getXScale().getHighMin(),
				e.getXScale().getHighMax() };
	}

	private static ParallaxLayer snow(Parallax_Heart heart)
	{
		for (ParallaxLayer layer : heart.parallaxReader.layers)
			if (layer.getKind() == Enum_LayerKind.PARTICLES)
				return layer;
		return null;
	}

	private void buildControls()
	{
		Document document = Document.get();
		document.setTitle("Effects lab: " + lab.feature + " (r224)");
		document.getElementById("effects-title").setInnerText("Effects lab: " + lab.feature);
		LabControls.panelLabels(document, "effects-labels", "A: " + lab.left, "B: " + lab.right);
		Element controls = document.getElementById("effects-controls");
		if (lab != Lab.SNOW)
		{
			fog = LabControls.slider(document, controls, "fogStrength", "page fog strength (1 - exp(-s (1/speed - 1/front)))", 0, 0.2f, 0.0025f, FOG);
			fogR = LabControls.slider(document, controls, "fogRed", "fog colour: red", 0, 1, 0.01f, WholePage_Model.FOG_R);
			fogG = LabControls.slider(document, controls, "fogGreen", "fog colour: green", 0, 1, 0.01f, WholePage_Model.FOG_G);
			fogB = LabControls.slider(document, controls, "fogBlue", "fog colour: blue", 0, 1, 0.01f, WholePage_Model.FOG_B);
		}
		if (lab != Lab.FOG)
		{
			density = LabControls.slider(document, controls, "snowDensity", "snow density (x the .p's flakes a second)", 0, 4, 0.05f, 1);
			size = LabControls.slider(document, controls, "flakeSize", "flake size (x the .p's)", 0.25f, 4, 0.05f, 1);
			wind = LabControls.slider(document, controls, "snowWind", "wind (world units/s, + = right)", -4, 4, 0.1f, 0);
		}
		if (lab == Lab.SNOW)
		{
			ArrayList<ParallaxLayer> layers = set.hearts[0].parallaxReader.layers;
			ParallaxLayer snow = snow(set.hearts[0]);
			snowLayer = LabControls.slider(document, controls, "snowLayer", "snow's layer (0 = back, front = the last)", 0, layers.size() - 1, 1, layers.indexOf(snow));
			snowSpeed = LabControls.slider(document, controls, "snowSpeedX", "snow's speed ratio X (its drift with the scroll)", 0, 0.1f, 0.001f, snow.getParallaxSpeedRatioX());
			anchor = LabControls.slider(document, controls, "snowAnchor", "anchor (0 = VIEW: snow that never runs out, 1 = LAYER: tiled with the box)", 0, 1, 1,
					snow.getAnchor() == Enum_ParticleAnchor.LAYER ? 1 : 0);
		}
		if (lab == Lab.ALL)
		{
			mist = LabControls.slider(document, controls, "mistAmplitude", "the FOG mist's amplitude (thinning, 0..1)", 0, 1, 0.05f, mistOf(set.hearts[0]).getShaderAmplitude());
			ripple = LabControls.slider(document, controls, "treesWaveRipple", "trees' WAVE ripple (x the page's)", 0, 1, 0.05f, 1);
		}
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll", 0, 240, 5, SCROLL);
		if (sets.length > 1)
			pick = LabControls.slider(document, controls, "parallaxSet", "parallax set", 0, sets.length - 1, 1, 0);
		readout = LabControls.readout(document, controls, "effects-readout");
	}

	private static ParallaxLayer mistOf(Parallax_Heart heart)
	{
		for (ParallaxLayer layer : heart.parallaxReader.layers)
			if (MIST.equals(layer.getName()))
				return layer;
		throw new IllegalStateException(ALL_PAGE + " has no layer named " + MIST);
	}




	/** Puts the sliders into both hearts. */
	private void applyControls()
	{
		if (pick != null)
		{
			set = sets[Math.round(LabControls.read(pick))];
			((Element) pick.getNextSibling()).setInnerText(FOG_NAMES[set.index]);
		}
		float sc = LabControls.read(scroll);
		for (Parallax_Heart heart : set.hearts)
			heart.screenSpeedConstantX = sc;
		StringBuilder line = new StringBuilder();
		if (fog != null)
		{
			fogColor.set(LabControls.read(fogR), LabControls.read(fogG), LabControls.read(fogB), 1);
			float strength = LabControls.read(fog);
			set.hearts[0].parallaxReader.setFog(strength, fogColor);
			// "all" draws none of the effects on the right; "fog" none of the fog.
			set.hearts[1].parallaxReader.setFog(0, fogColor);
			line.append("fog back to front:").append(fogs(set.hearts[0].parallaxReader, strength)).append("; ");
		}
		if (density != null)
			applySnow(line);
		if (mist != null)
		{
			mistOf(set.hearts[0]).setShaderAmplitude(LabControls.read(mist));
			float rp = LabControls.read(ripple);
			for (int i = 0; i < waves.size(); i++)
				waves.get(i).setShaderAmplitude(waveAmplitudes.get(i) * rp);
		}
		readout.setInnerText(line + costLine);
	}

	/** Each layer's fog, back to front, as the reader draws it. */
	private static String fogs(ParallaxPageReader reader, float strength)
	{
		StringBuilder out = new StringBuilder();
		float front = ParallaxPageReader.frontSpeedOf(reader.layers);
		for (ParallaxLayer layer : reader.layers)
			out.append(' ').append(Math.round(100 * ParallaxPageReader.fogOf(strength, front, layer)) / 100f);
		return out.toString();
	}

	private void applySnow(StringBuilder line)
	{
		float d = LabControls.read(density), s = LabControls.read(size), w = LabControls.read(wind);
		ParallaxLayer snow = snow(set.hearts[0]);
		if (snow == null || snowBase == null)
			return;
		if (snowLayer != null)
		{
			ArrayList<ParallaxLayer> layers = set.hearts[0].parallaxReader.layers;
			int index = Math.min(Math.round(LabControls.read(snowLayer)), layers.size() - 1);
			if (layers.indexOf(snow) != index)
			{
				layers.remove(snow);
				layers.add(index, snow);
			}
			snow.setParallaxSpeedRatioX(LabControls.read(snowSpeed));
			boolean layerAnchor = Math.round(LabControls.read(anchor)) == 1;
			((Element) anchor.getNextSibling()).setInnerText(layerAnchor ? "LAYER" : "VIEW");
			snow.setAnchor(layerAnchor ? Enum_ParticleAnchor.LAYER : Enum_ParticleAnchor.VIEW);
		}
		Array<ParticleEmitter> emitters = snow.getParticles().getEmitters();
		if (d != appliedDensity || s != appliedSize || w != appliedWind)
		{
			for (ParticleEmitter e : emitters)
			{
				e.getEmission().setHigh(snowBase[0] * d, snowBase[1] * d);
				int count = Math.max(1, Math.round(snowBase[2] * d));
				if (count != e.getMaxParticleCount())
					e.setMaxParticleCount(count);
				e.getXScale().setHigh(snowBase[3] * s, snowBase[4] * s);
				e.getWind().setActive(w != 0);
				e.getWind().setHigh(w);
			}
			appliedDensity = d;
			appliedSize = s;
			appliedWind = w;
		}
		int alive = 0;
		for (ParticleEmitter e : emitters)
			alive += e.getActiveCount();
		line.append("snow: ").append(alive).append(" flakes alive in A; ");
	}

	/** Steps both hearts by {@code seconds}, at 1/60 s: the clip's clock. */
	void act(float seconds)
	{
		applyControls();
		for (float t = 0; t < seconds - 1e-4f; t += 1 / 60f)
			for (Parallax_Heart heart : set.hearts)
				heart.act(1 / 60f);
		applyControls();
	}

	private static native void exportAct(EffectsLab lab) /*-{
		$wnd.effectsLabAct = $entry(function(seconds) {
			lab.@jks.tools2d.parallax.browsertest.EffectsLab::act(F)(seconds);
		});
		$wnd.effectsLabReady = true;
	}-*/;

	@Override
	public void render()
	{
		if (!clip)
		{
			applyControls();
			float delta = Math.min(Gdx.graphics.getDeltaTime(), 1 / 15f);
			for (Parallax_Heart heart : set.hearts)
				heart.act(delta);
		}
		int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
		Gdx.gl.glViewport(0, 0, width, height);
		Gdx.gl.glClearColor(0, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
		for (int i = 0; i < 2; i++)
		{
			// A on the left, B on the right, a 4 px gap.
			int x = i * (FRAME_WIDTH + 4);
			Gdx.gl.glScissor(x, height - PANEL_HEIGHT, FRAME_WIDTH, PANEL_HEIGHT);
			Gdx.gl.glViewport(x, height - PANEL_HEIGHT - set.frameDrop, FRAME_WIDTH, FRAME_HEIGHT);
			double start = now();
			set.hearts[i].render();
			panelMillis[i] += (float) (now() - start);
			panelCalls[i] = batch.renderCalls;
		}
		Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
		Gdx.gl.glViewport(0, 0, width, height);
		measure();
	}

	/** The cost line, once a second of frames: frame time, and each panel's draw calls and CPU time to submit. */
	private void measure()
	{
		frames++;
		frameSeconds += Gdx.graphics.getDeltaTime();
		if (frameSeconds < 1 && !(clip && frames >= 1))
			return;
		costLine = (clip ? "cost (stepped, not timed): " : "frame " + LabControls.text(1000 * frameSeconds / frames) + " ms; ")
				+ "A " + panelCalls[0] + " draw calls, " + LabControls.text(panelMillis[0] / frames) + " ms CPU; B " + panelCalls[1]
				+ " draw calls, " + LabControls.text(panelMillis[1] / frames) + " ms CPU";
		frames = frameSeconds = 0;
		panelMillis[0] = panelMillis[1] = 0;
	}

	private static native double now() /*-{
		return $wnd.performance.now();
	}-*/;

	@Override
	public void dispose()
	{
		batch.dispose();
		pixel.dispose();
	}
}
