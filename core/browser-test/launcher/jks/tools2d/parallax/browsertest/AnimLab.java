package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.SelectElement;

import jks.tools2d.parallax.GdxLayerEffects;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * The animation lab (r407, after r375): the shader animations r375 proposed for water and clouds, SWELL, GLINT and
 * BILLOW, written here as lab GLSL (GLSL ES 1.0, sines only, as FOG's noise is) and drawn by the real reader in WebGL,
 * so Simon can judge which to ship before any is written three times. Opened with anim-lab.html: tools/anim-lab.sh.
 * <p>
 * The sea: r365's AI beach page (core/test-data/anim), its sea (l1) a SHADER layer drawn four ways: WAVE (what ships,
 * GdxLayerEffects' own), SWELL, GLINT, and SWELL then GLINT in one shader. The clouds: round1's calm page, its clouds
 * on a layer of their own (r409: a generated page will have them so), drawn still and under BILLOW, in the page and
 * alone (the layers in front left out; OneNight's clouds, tried first, sit above its view). Each effect is written in
 * world units over {@code along()}, as WAVE and FOG are, so it runs on across the tiles; its amplitude, wavelength and
 * speed are the layer's shader numbers, as a shipped effect's would be. GLINT's own numbers (strength, glint size,
 * density, twinkle, how closely it keeps to the crests, the sun's place and width) are the lab's {@code u_glint}: in
 * SWELL + GLINT the layer's numbers are the swell's.
 * <p>
 * {@code &clip=1}: nothing moves by itself, {@code window.animLabAct(seconds)} steps every heart at 1/60 s.
 */
public class AnimLab extends ApplicationAdapter implements LabKit.Clip
{
	static final String SEA_PAGE = "anim-lab/beach.jplax", CALM_PAGE = "fog-lab/s02.jplax";
	/** The sea layer's region in the beach page, the clouds' in calm's. */
	static final String SEA = "l1", CLOUDS = "Clouds";
	/** FogLab's world and panels, the panels lower: five rows of sliders fit under them in the 1580x710 window. */
	static final float WORLD_WIDTH = FogLab.WORLD_WIDTH, WORLD_HEIGHT = FogLab.WORLD_HEIGHT;
	static final int PANEL_WIDTH = FogLab.FRAME_WIDTH, PANEL_HEIGHT = 200;

	/** What a panel draws its animated layer through. */
	static final int STILL = 0, WAVE = 1, SWELL = 2, GLINT = 3, BOTH = 4, BILLOW = 5, MIRROR = 6;
	static final String[] EFFECT_NAMES = { "still", "WAVE (ships today)", "SWELL", "GLINT", "SWELL + GLINT", "BILLOW", "SWELL + MIRROR" };

	/** The views the choice picks: four panels, two one over the other, or one, close. Each: its hearts (indices into {@link #hearts}). */
	static final String[] VIEWS = { "sea, all four", "sea: both", "sea: SWELL", "sea: GLINT", "sea: WAVE", "clouds, all four", "clouds: BILLOW",
			"sea: SWELL + MIRROR", "sea: MIRROR or not" };
	static final int[][] VIEW_HEARTS = { { 0, 1, 2, 3 }, { 3 }, { 1 }, { 2 }, { 0 }, { 4, 5, 6, 7 }, { 5 }, { 9 }, { 8, 9 } };
	/** Each heart: its page, its layer, its effect. 0-3 and 8-9 the sea, 4-7 the clouds. */
	static final String[] HEART_PAGES = { SEA_PAGE, SEA_PAGE, SEA_PAGE, SEA_PAGE, CALM_PAGE, CALM_PAGE, CALM_PAGE, CALM_PAGE, SEA_PAGE, SEA_PAGE };
	static final String[] HEART_LAYERS = { SEA, SEA, SEA, SEA, CLOUDS, CLOUDS, CLOUDS, CLOUDS, SEA, SEA };
	/** Whether a heart draws its page without the layers in front of the animated one: calm's clouds alone. */
	static final boolean[] HEART_ALONE = { false, false, false, false, false, false, true, true, false, false };
	static final int[] HEART_EFFECTS = { WAVE, SWELL, GLINT, BOTH, STILL, BILLOW, STILL, BILLOW, SWELL, MIRROR };
	static final String[] HEART_SETS = { "beach", "beach", "beach", "beach", "calm (round1 s02)", "calm (round1 s02)", "calm's clouds alone",
			"calm's clouds alone", "beach", "beach" };
	/** The sea strip's water: its bottom {@link #SEA_HORIZON} of its height (rows 300 to 383 of 384 in beach-sea.png), the rest clear. */
	static final float SEA_HORIZON = 84 / 384f;
	/**
	 * MIRROR's sky (r419, after Simon on REFLECT and SHEEN: "something that affects the whole of the water ... a mirror
	 * texture, a bit like metallic"): the beach page's gradient, its sky at the horizon (bottomHalf, also its fog) and
	 * over the view (topHalf_top), the two ends of what the water mirrors.
	 */
	static final float[] SKY = { 0.81f, 0.89f, 0.95f }, ZENITH = { 0.31f, 0.56f, 0.82f };
	/** World units under the horizon where MIRROR's flat water mirrors the sky over the view (the beach's visible sea is about 3 deep). */
	static final float MIRROR_DEPTH = 3;
	/**
	 * Where each heart's frame is held, as a fraction of the frame's height from its bottom, at the panel's middle: the
	 * beach's sea, calm's clouds; MIRROR's views lower, on the water under the horizon.
	 */
	static final float[] HEART_FOCUS = { 0.3f, 0.3f, 0.3f, 0.3f, 0.82f, 0.82f, 0.82f, 0.82f, 0.24f, 0.24f };

	private final Parallax_Heart[] hearts = new Parallax_Heart[HEART_PAGES.length];
	private final ParallaxLayer[] animated = new ParallaxLayer[HEART_PAGES.length];
	/** Each clouds layer's own drift (its at rest speed), which the drift slider scales. */
	private final float[] drifts = new float[HEART_PAGES.length];
	private final AnimEffects[] effects = new AnimEffects[HEART_PAGES.length];
	private SelectElement view;
	private InputElement zoom, scroll, waveAmplitude, waveWavelength, waveSpeed, swellAmplitude, swellWavelength, swellSpeed, glintStrength,
			glintSize, glintDensity, glintSpeed, glintCrest, sunX, sunWidth, billowAmplitude, billowWavelength, billowSpeed, drift, mirrorStrength,
			mirrorRelief, mirrorShine, mirrorTint;
	private Element readout;
	private final Element[] labels = new Element[4];
	private boolean clip;
	private SpriteBatch batch;
	/**
	 * GLINT's numbers, the lab's own: strength, density, twinkle (pulses a second), crest reach (world units), the sun's
	 * x (0..1 of the view) and width, and its three waves' whole numbers of turns over the effect's periods, across and
	 * up (r408).
	 */
	final float[] glint = new float[12];
	/** MIRROR's numbers: strength, relief, shine, tint; the horizon's height in the sea's tile, the depth. */
	final float[] mirror = new float[6];

	@Override
	public void create()
	{
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		batch = new SpriteBatch();
		for (int i = 0; i < hearts.length; i++)
		{
			WholePage_Model page = Utils_Page_Json.loadPage(HEART_PAGES[i]);
			// The first layer of that region: the reader makes its layers in the page's order, one each (these pages have
			// no EMPTY or PARTICLES layer).
			int index = -1;
			for (int k = page.pageModel.pageList.size() - 1; k >= 0; k--)
				if (HEART_LAYERS[i].equals(page.pageModel.pageList.get(k).regionName))
					index = k;
			if (index < 0)
				throw new IllegalStateException(HEART_PAGES[i] + " has no layer " + HEART_LAYERS[i]);
			Parallax_Model model = page.pageModel.pageList.get(index);
			if (HEART_ALONE[i])
				while (page.pageModel.pageList.size() > index + 1)
					page.pageModel.pageList.remove(index + 1);
			if (HEART_EFFECTS[i] != STILL)
			{
				// What a page would store: the layer a SHADER one, under WAVE until one of these ships. SWELL under FOG's
				// periods (r419): along() runs on only within the effect's period, one wavelength under WAVE, where SWELL's
				// second wave (5 turns in 8 wavelengths across, 6 and 9 in 10 up) jumped every time the layer wrapped. Its
				// port gives SWELL these periods of its own.
				model.kind = Enum_LayerKind.SHADER;
				int e = HEART_EFFECTS[i];
				boolean swells = e == SWELL || e == BOTH || e == GLINT || e == MIRROR;
				model.shaderEffect = swells ? Enum_ShaderEffect.FOG : Enum_ShaderEffect.WAVE;
			}
			OrthographicCamera camera = new OrthographicCamera();
			camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i] = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i].setPage(page);
			animated[i] = hearts[i].parallaxReader.layers.get(index);
			drifts[i] = animated[i].getSpeedAtRest();
			effects[i] = new AnimEffects(this, HEART_EFFECTS[i], animated[i], hearts[i]);
			hearts[i].parallaxReader.setLayerEffects(effects[i]);
		}
		buildControls();
		if (clip)
			LabKit.exportClip("animLab", this);
	}

	private void buildControls()
	{
		Document document = Document.get();
		Element labelGrid = document.getElementById("anim-labels");
		// Each chip's title in a span of its own: the frame rewrites it, the view's choice before it in the first stays.
		for (int i = 0; i < labels.length; i++)
		{
			Element chip = document.createDivElement();
			chip.setClassName("panel-label");
			labels[i] = document.createSpanElement();
			chip.appendChild(labels[i]);
			labelGrid.appendChild(chip);
		}
		view = LabControls.stageChoice(document, "anim-labels", "view", "view (four panels, or one effect close)", VIEWS, 0);
		Element controls = document.getElementById("anim-controls");
		zoom = LabControls.slider(document, controls, "zoom", "zoom (x the page's frame)", 1, 4, 0.25f, 1.5f);
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll (world units/s)", 0, 120, 5, 20);
		drift = LabControls.slider(document, controls, "cloudDrift", "clouds' drift (x their page's at rest speed)", 0, 1, 0.05f, 1);
		waveAmplitude = LabControls.slider(document, controls, "waveAmplitude", "WAVE amplitude (world units)", 0, 0.5f, 0.01f, 0.06f);
		waveWavelength = LabControls.slider(document, controls, "waveWavelength", "WAVE wavelength (world units)", 0.1f, 4, 0.05f, 0.6f);
		waveSpeed = LabControls.slider(document, controls, "waveSpeed", "WAVE speed (world units/s)", 0, 2, 0.05f, 0.3f);
		swellAmplitude = LabControls.slider(document, controls, "swellAmplitude", "SWELL amplitude (world units)", 0, 0.5f, 0.01f, 0.12f);
		swellWavelength = LabControls.slider(document, controls, "swellWavelength", "SWELL wavelength (world units)", 0.25f, 6, 0.05f, 1.25f);
		swellSpeed = LabControls.slider(document, controls, "swellSpeed", "SWELL speed (world units/s)", 0, 2, 0.05f, 0.3f);
		glintStrength = LabControls.slider(document, controls, "glintStrength", "GLINT strength (light added, 0..1.5)", 0, 1.5f, 0.05f, 0.5f);
		glintSize = LabControls.slider(document, controls, "glintSize", "GLINT size (a glint's length, world units)", 0.2f, 3, 0.05f, 0.8f);
		glintDensity = LabControls.slider(document, controls, "glintDensity", "GLINT density (how much of the crests is lit, 0.05..1.5)", 0.05f, 1.5f, 0.05f, 0.6f);
		glintSpeed = LabControls.slider(document, controls, "glintSpeed", "GLINT twinkle (pulses a second: a glint fades in and out in place)", 0, 2, 0.05f, 0.3f);
		glintCrest = LabControls.slider(document, controls, "glintCrest", "GLINT crest reach (world units: a crest is brighter than the water this far above and below; more, thicker lines)", 0.05f, 0.6f, 0.05f, 0.2f);
		sunX = LabControls.slider(document, controls, "sunX", "sun's place (0..1 across the view: the glitter's column)", 0, 1, 0.05f, 0.5f);
		sunWidth = LabControls.slider(document, controls, "sunWidth", "sun's width (of the view: 2 lights it all)", 0.05f, 2, 0.05f, 0.3f);
		billowAmplitude = LabControls.slider(document, controls, "billowAmplitude", "BILLOW amplitude (world units)", 0, 1.5f, 0.02f, 0.3f);
		billowWavelength = LabControls.slider(document, controls, "billowWavelength", "BILLOW wavelength (world units)", 1, 20, 0.25f, 6);
		billowSpeed = LabControls.slider(document, controls, "billowSpeed", "BILLOW speed (world units/s)", 0, 2, 0.05f, 0.4f);
		Element mirrors = LabControls.section(document, controls, "MIRROR (the whole sea a mirror of the sky, like polished metal: views 'sea: SWELL + MIRROR' and 'sea: MIRROR or not')");
		mirrorStrength = LabControls.slider(document, mirrors, "mirrorStrength", "strength (how much of the water is the mirrored sky: 1 all of it)", 0, 1, 0.05f, 0.8f);
		mirrorRelief = LabControls.slider(document, mirrors, "mirrorRelief", "relief (how much the painted waves bend the mirror, beside the swell)", 0, 3, 0.1f, 1);
		mirrorShine = LabControls.slider(document, mirrors, "mirrorShine", "shine (contrast of the mirrored sky: 0 soft, 1 hard bright and dark facets, like polished metal)", 0, 1, 0.05f, 0.8f);
		mirrorTint = LabControls.slider(document, mirrors, "mirrorTint", "tint (how much the water's own colour tints the mirror, as a metal tints what it reflects: 0 a chrome mirror)", 0, 1, 0.05f, 0.4f);
		readout = LabControls.readout(document, controls, "anim-readout");
	}

	/** Puts the sliders into every heart. */
	private void applyControls()
	{
		int v = LabControls.read(view);
		LabControls.read(zoom);
		float sc = LabControls.read(scroll), dr = LabControls.read(drift);
		float wa = LabControls.read(waveAmplitude), ww = LabControls.read(waveWavelength), ws = LabControls.read(waveSpeed);
		float sa = LabControls.read(swellAmplitude), sw = LabControls.read(swellWavelength), ss = LabControls.read(swellSpeed);
		float gs = LabControls.read(glintSize);
		glint[0] = LabControls.read(glintStrength);
		glint[1] = LabControls.read(glintDensity);
		glint[2] = LabControls.read(glintSpeed);
		glint[3] = LabControls.read(glintCrest);
		glint[4] = LabControls.read(sunX);
		glint[5] = LabControls.read(sunWidth);
		// Each wave a whole number of turns over the periods (8 wavelengths across, 10 up), so it runs on across the
		// layer's wrap (r419). Along a crest, the pieces lit come about every 2.5 glint lengths, of uneven lengths (a second
		// wave not a multiple of the first); up the water they shift every 0.9 world units, so the next crest's are
		// elsewhere. The third wave sets when each piece pulses.
		float across = 8 * sw / (2.5f * gs), up = 10 * sw / 0.9f;
		float[] mx = { 1, 1.37f, 0.53f }, my = { 1, -0.71f, 1.3f };
		for (int k = 0; k < 3; k++)
		{
			glint[6 + 2 * k] = Math.max(1, Math.round(mx[k] * across));
			glint[7 + 2 * k] = Math.round(my[k] * up);
		}
		float ba = LabControls.read(billowAmplitude), bw = LabControls.read(billowWavelength), bs = LabControls.read(billowSpeed);
		mirror[0] = LabControls.read(mirrorStrength);
		mirror[1] = LabControls.read(mirrorRelief);
		mirror[2] = LabControls.read(mirrorShine);
		mirror[3] = LabControls.read(mirrorTint);
		mirror[4] = animated[0].getHeight() * SEA_HORIZON;
		mirror[5] = MIRROR_DEPTH;
		for (int i = 0; i < hearts.length; i++)
		{
			ParallaxLayer layer = animated[i];
			int effect = HEART_EFFECTS[i];
			if (effect == WAVE)
				shader(layer, wa, ww, ws);
			else if (effect == SWELL || effect == BOTH || effect == GLINT || effect == MIRROR)
				shader(layer, sa, sw, ss);
			else if (effect == BILLOW)
				shader(layer, ba, bw, bs);
			layer.setSpeedAtRest(drifts[i] * dr);
			hearts[i].screenSpeedConstantX = sc;
		}
		int[] shown = VIEW_HEARTS[v];
		// The labels' grid is two by two: two panels, one over the other, take its left column.
		for (int k = 0; k < labels.length; k++)
		{
			int h = shown.length == 2 ? (k % 2 == 0 ? k / 2 : -1) : k;
			boolean titled = h >= 0 && h < shown.length;
			labels[k].setInnerText(titled ? HEART_SETS[shown[h]] + ": " + EFFECT_NAMES[HEART_EFFECTS[shown[h]]] : "");
			// A chip over no panel is hidden, not a dark dash; it keeps its cell in the grid.
			labels[k].getParentElement().getStyle().setProperty("visibility", titled || k == 0 ? "visible" : "hidden");
		}
		ParallaxLayer sea = animated[0], cloud = animated[4];
		float seaPx = sea.getDrawnRegion().getRegionWidth() / (sea.getWidth() * sea.getPackedWidthRatio());
		float cloudPx = cloud.getDrawnRegion().getRegionWidth() / (cloud.getWidth() * cloud.getPackedWidthRatio());
		readout.setInnerText("sea layer " + round(sea.getWidth()) + " x " + round(sea.getHeight()) + " world units, its image "
				+ round(seaPx) + " px a unit: SWELL " + round(sa * seaPx) + " px, wavelength " + round(sw * seaPx) + " px; GLINT glints "
				+ round(gs * seaPx) + " px long; calm's clouds " + round(cloudPx) + " px a unit: BILLOW " + round(ba * cloudPx) + " px, wavelength "
				+ round(bw * cloudPx) + " px. The world is " + round(WORLD_WIDTH) + " units wide.");
	}

	private static float round(float value)
	{return Math.round(value * 100) / 100f;}

	private static void shader(ParallaxLayer layer, float amplitude, float wavelength, float speed)
	{
		layer.setShaderAmplitude(amplitude);
		layer.setShaderWavelength(wavelength);
		layer.setShaderSpeed(speed);
	}

	@Override
	public void act(float seconds)
	{
		applyControls();
		LabKit.clip(seconds, hearts);
	}

	@Override
	public void render()
	{
		if (!clip)
		{
			applyControls();
			LabKit.act(LabKit.liveDelta(), hearts);
		}
		int height = Gdx.graphics.getBackBufferHeight(), width = Gdx.graphics.getBackBufferWidth();
		int[] shown = VIEW_HEARTS[LabControls.read(view)];
		float z = Float.parseFloat(zoom.getValue());
		LabKit.beginPanels();
		if (shown.length == 1)
			panel(shown[0], 0, height, width, height, z);
		else if (shown.length == 2)
			for (int k = 0; k < 2; k++)
				panel(shown[k], 0, height - k * (height + 4) / 2, width, (height - 4) / 2, z);
		else
			for (int k = 0; k < shown.length; k++)
				panel(shown[k], (k % 2) * (PANEL_WIDTH + 4), k < 2 ? height : height - PANEL_HEIGHT - 4, PANEL_WIDTH, PANEL_HEIGHT, z);
		LabKit.endPanels();
	}

	/**
	 * Draws heart {@code i} into the panel {@code w} by {@code h} whose top-left is at {@code x}, {@code top}: through a
	 * 16:9 frame {@code z} times the panel's width, its middle across at the panel's, its {@link #HEART_FOCUS} at the
	 * panel's middle up, kept over the whole panel.
	 */
	private void panel(int i, int x, int top, int w, int h, float z)
	{
		int frameWidth = Math.round(w * z), frameHeight = Math.round(frameWidth * WORLD_HEIGHT / WORLD_WIDTH);
		int bottom = Math.round(top - h / 2f - HEART_FOCUS[i] * frameHeight);
		bottom = Math.max(top - frameHeight, Math.min(top - h, bottom));
		Gdx.gl.glScissor(x, top - h, w, h);
		Gdx.gl.glViewport(x - (frameWidth - w) / 2, bottom, frameWidth, frameHeight);
		hearts[i].render();
	}

	/**
	 * A heart's effects: its animated layer through the lab's GLSL for its panel's effect (WAVE: GdxLayerEffects' own
	 * source), every other layer through GdxLayerEffects, as FogLab's are.
	 */
	static final class AnimEffects extends LabKit.Effects
	{
		private static ShaderProgram wave, swell, glint, both, billow, mirror;

		private final AnimLab lab;
		private final int effect;
		private final ParallaxLayer target;
		private final Parallax_Heart heart;
		private final GdxLayerEffects shipped = new GdxLayerEffects();
		private boolean byShipped;
		private ShaderProgram previous;

		AnimEffects(AnimLab lab, int effect, ParallaxLayer target, Parallax_Heart heart)
		{
			this.lab = lab;
			this.effect = effect;
			this.target = target;
			this.heart = heart;
		}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{return begin(batch, layer, phase, 0, Color.WHITE);}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase, float fogAmount, Color fogColor)
		{
			byShipped = layer != target || effect == STILL;
			if (byShipped)
				return shipped.begin(batch, layer, phase, fogAmount, fogColor);
			ShaderProgram program = program(effect);
			previous = batch.getShader();
			batch.setShader(program);
			if (!batch.isDrawing())
				program.bind();
			LabKit.setEffect(program, numbers(layer, phase), fogAmount, fogColor.r, fogColor.g, fogColor.b);
			// WAVE is GdxLayerEffects' own source, whose glint (r427) has uniforms of these names, u_glintB a vec4: set
			// there, the lab's numbers lit WAVE's shipped glint and divided along() by a zero u_glintB.zw, a white sea
			// (r419). Its glint stays off, as on a layer whose glintStrength is 0.
			if (program == wave)
				return true;
			float[] g = lab.glint;
			// GLINT's time in pulses, by the reader's clock, wrapped at a whole number: every time term is whole turns.
			float time = (float) (heart.parallaxReader.getEffectTime() * g[2] % 1000.0);
			program.setUniformf(program.fetchUniformLocation("u_glint", false), g[0], g[1], time, g[3]);
			program.setUniformf(program.fetchUniformLocation("u_sun", false), g[4], g[5]);
			program.setUniformf(program.fetchUniformLocation("u_glintA", false), g[6], g[7], g[8], g[9]);
			program.setUniformf(program.fetchUniformLocation("u_glintB", false), g[10], g[11]);
			float[] m = lab.mirror;
			program.setUniformf(program.fetchUniformLocation("u_mirror", false), m[0], m[1], m[2], m[3]);
			program.setUniformf(program.fetchUniformLocation("u_mirrorAt", false), m[4], m[5]);
			program.setUniformf(program.fetchUniformLocation("u_sky", false), SKY[0], SKY[1], SKY[2]);
			program.setUniformf(program.fetchUniformLocation("u_zenith", false), ZENITH[0], ZENITH[1], ZENITH[2]);
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

		static ShaderProgram program(int effect)
		{
			switch (effect)
			{
				case WAVE:
					return wave != null ? wave : (wave = FogLab.PatchHeightEffects.compile(GdxLayerEffects.fragment(Enum_ShaderEffect.WAVE), "WAVE"));
				case SWELL:
					return swell != null ? swell : (swell = compile("#define SWELL\n", "SWELL"));
				case GLINT:
					return glint != null ? glint : (glint = compile("#define GLINT\n", "GLINT"));
				case BOTH:
					return both != null ? both : (both = compile("#define SWELL\n#define GLINT\n", "SWELL + GLINT"));
				case BILLOW:
					return billow != null ? billow : (billow = compile("#define BILLOW\n", "BILLOW"));
				default:
					return mirror != null ? mirror : (mirror = compile("#define SWELL\n#define MIRROR\n", "SWELL + MIRROR"));
			}
		}

		private static ShaderProgram compile(String defines, String name)
		{
			ShaderProgram program = new ShaderProgram(GdxLayerEffects.vertex(), defines + FRAGMENT);
			if (!program.isCompiled())
				throw new IllegalStateException("the lab's " + name + " does not compile: " + program.getLog());
			return program;
		}
	}

	/**
	 * The lab's effects, one source, {@code #define}d: as GdxLayerEffects writes its own (its head: the region, the
	 * layer's size, u_effect = amplitude, wavelength, phase in world units, the haze, along()), the dissolve and the
	 * grade left out. {@code at(l)}: the image at local position l, in world units from the tile's top-left, wrapping
	 * across (the layer tiles), held at its top and bottom rows.
	 * <ul>
	 * <li>SWELL (r375's swell): the water moved by two crossing travelling waves, sideways and up and down: WAVE slides
	 * whole rows, this bends the painted crests. A, L, speed: the layer's numbers. Every term a whole number of turns
	 * over FOG's periods, 8 wavelengths across and 10 up, the layer's effect in the lab (r419: at 1 / 1.7 turns a
	 * wavelength across, the second wave jumped each time the layer wrapped).</li>
	 * <li>GLINT (r408, after Simon on r375 and r407: "a ton of white dots", "not using the shape of the water"): the
	 * painted crests themselves light up, a pixel brighter than the water u_glint.w above and below it, in pieces about a
	 * glint long that fade in and out in place, each at its own moment, within a sun's column across the view. u_glint:
	 * strength, density (how much of the crests is lit), time in pulses, crest reach; u_sun: its x in the view and
	 * width; u_glintA, u_glintB: the three waves' turns over the periods, across and up (two pick the pieces, the third
	 * when each pulses).</li>
	 * <li>BILLOW (r375's billow): the clouds churn in place, their pixels pushed by a slow warp of wavelength L. On a
	 * layer of its own every pixel is cloud: r375's colour mask, made for clouds painted into a back layer, is left
	 * out (r409: clouds always get their own layer).</li>
	 * <li>MIRROR (r419, after REFLECT and SHEEN, Simon: "something that affects the whole of the water ... a mirror
	 * texture, a bit like metallic"): every pixel of the water is a mirror of the sky, as polished metal is of what is
	 * round it. Flat, it mirrors the pale sky at the horizon (u_sky) and, u_mirrorAt.y world units under it, the bluer sky
	 * over the view (u_zenith); tilted, by the swell's slopes and the painting's own relief (its brightness's slope,
	 * times u_mirror.y), it mirrors higher or lower, so the facets go bright and dark as the swell moves. u_mirror: how
	 * much of the water is the mirror, relief, shine (the mirrored sky's contrast: hard facets), tint (the water's colour
	 * over the mirror, as a metal tints what it reflects); u_mirrorAt.x: the horizon's height in the tile. Its only x
	 * terms are the swell's, whole turns over FOG's periods.</li>
	 * </ul>
	 */
	static final String FRAGMENT = "#ifdef GL_ES\n"
			+ "#ifdef GL_FRAGMENT_PRECISION_HIGH\n"
			+ "precision highp float;\n"
			+ "#else\n"
			+ "precision mediump float;\n"
			+ "#endif\n"
			+ "#endif\n"
			+ "varying vec4 v_color;\n"
			+ "varying vec2 v_texCoords;\n"
			+ "varying vec2 v_view;\n"
			+ "uniform sampler2D u_texture;\n"
			+ "uniform vec4 u_region;\n"
			+ "uniform vec2 u_size;\n"
			+ "uniform vec3 u_effect;\n"
			+ "uniform float u_haze;\n"
			+ "uniform vec3 u_fog;\n"
			+ "uniform vec3 u_across;\n"
			+ "uniform vec3 u_up;\n"
			+ "uniform vec4 u_glint;\n"
			+ "uniform vec2 u_sun;\n"
			+ "uniform vec4 u_glintA;\n"
			+ "uniform vec2 u_glintB;\n"
			+ "uniform vec4 u_mirror;\n"
			+ "uniform vec2 u_mirrorAt;\n"
			+ "uniform vec3 u_sky;\n"
			+ "uniform vec3 u_zenith;\n"
			+ "const float TAU = 6.2831853;\n"
			+ "vec4 hazed(vec4 color)\n"
			+ "{\n"
			+ "	return vec4(mix(color.rgb, u_fog, u_haze), color.a);\n"
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
			+ "vec4 at(vec2 l)\n"
			+ "{\n"
			+ "	vec2 f = vec2(fract(l.x / u_size.x), clamp(l.y / u_size.y, 0.0, 1.0));\n"
			+ "	return texture2D(u_texture, vec2(mix(u_region.x, u_region.z, f.x), mix(u_region.w, u_region.y, f.y)));\n"
			+ "}\n"
			+ "void main()\n"
			+ "{\n"
			+ "	vec2 a = along();\n"
			+ "	vec2 l = local();\n"
			+ "	float L = u_effect.y;\n"
			+ "	float t = u_effect.z / L;\n"
			+ "#ifdef SWELL\n"
			+ "	float p1 = TAU * ((a.x + 0.6 * a.y) / L - t);\n"
			+ "	float p2 = TAU * (0.625 * a.x / L - 0.9 * a.y / L + 2.0 * t);\n"
			+ "	l += u_effect.x * vec2(0.6 * sin(p1) + 0.4 * sin(p2), 0.5 * (0.6 * cos(p1) - 0.4 * cos(p2)));\n"
			+ "#endif\n"
			+ "#ifdef MIRROR\n"
			// How steeply the water faces us: the swell's slope up it (its push's own sines, differentiated), and finer
			// ripples, wider than high as water seen low is, each a whole number of turns over FOG's periods and of t.
			+ "	vec2 mq = vec2(a.x / (8.0 * L), a.y / (10.0 * L));\n"
			+ "	float tilt = TAU / L * u_effect.x * (0.18 * cos(p1) - 0.18 * cos(p2))\n"
			+ "		+ u_effect.x * (0.9 * sin(TAU * (13.0 * mq.x + 41.0 * mq.y - 3.0 * t)) + 0.7 * sin(TAU * (-7.0 * mq.x + 57.0 * mq.y - 4.0 * t))\n"
			+ "		+ 0.5 * sin(TAU * (21.0 * mq.x + 83.0 * mq.y - 5.0 * t + 0.5 * sin(TAU * (5.0 * mq.x - 9.0 * mq.y + t)))));\n"
			+ "#endif\n"
			+ "#ifdef BILLOW\n"
			+ "	vec2 p = a / L;\n"
			+ "	float dx = sin(TAU * (p.x + 0.7 * sin(TAU * p.y) - t)) + 0.5 * sin(TAU * (2.0 * p.x - 1.3 * p.y + t + 0.3));\n"
			+ "	float dy = sin(TAU * (2.0 * p.x + p.y - t + 0.6)) + 0.5 * sin(TAU * (p.x - 2.0 * p.y - 2.0 * t));\n"
			+ "	l += u_effect.x / 1.5 * vec2(dx, -dy);\n"
			+ "#endif\n"
			+ "	vec4 image = at(l);\n"
			+ "	vec4 color = v_color * image;\n"
			+ "#ifdef MIRROR\n"
			+ "	const vec3 GREY = vec3(0.299, 0.587, 0.114);\n"
			// The painting's relief: its brightness's slope over a pixel or two, the painted crests' faces.
			+ "	const float D = 0.05;\n"
			+ "	float relief = dot(at(l + vec2(0.0, D)).rgb - at(l - vec2(0.0, D)).rgb, GREY) / (2.0 * D);\n"
			+ "	float n = tilt + 0.5 * u_mirror.y * relief;\n"
			// What this pixel mirrors, 0 the horizon's sky, 1 the sky over the view: lower in the water, higher; a facet
			// tilted toward us, higher still. Past the horizon's sky, a facet mirrors the sun's haze, white; past the sky
			// over the view, the deeper sky behind us, darker. Shine sharpens the ramp into hard facets, and opens those two
			// ends, as polished metal's light and dark.
			+ "	float sky = (u_mirrorAt.x - local().y) / u_mirrorAt.y + 1.2 * n;\n"
			+ "	float hard = 0.5 - 0.4 * u_mirror.z;\n"
			+ "	vec3 mirrored = mix(u_sky, u_zenith, smoothstep(0.5 - hard, 0.5 + hard, sky));\n"
			+ "	mirrored = mix(mirrored, vec3(1.0), u_mirror.z * smoothstep(0.1, -0.3, sky));\n"
			+ "	mirrored *= 1.0 - 0.5 * u_mirror.z * smoothstep(0.9, 1.4, sky);\n"
			// As a metal: its own colour over what it mirrors, the painting's light and dark kept in it.
			+ "	float grey = dot(color.rgb, GREY);\n"
			+ "	vec3 metal = mirrored * mix(vec3(1.0), color.rgb / max(grey, 0.05), u_mirror.w) * (0.6 + 0.6 * grey / max(dot(v_color.rgb, GREY), 0.05));\n"
			+ "	color.rgb = mix(color.rgb, min(metal, 1.0), u_mirror.x);\n"
			+ "#endif\n"
			+ "#ifdef GLINT\n"
			// The glint's shape is the painting's: a crest, brighter than the water u_glint.w above and below it.
			+ "	const vec3 LUMA = vec3(0.299, 0.587, 0.114);\n"
			+ "	vec4 above = at(l - vec2(0.0, u_glint.w)), below = at(l + vec2(0.0, u_glint.w));\n"
			+ "	float ridge = dot(image.rgb, LUMA) - 0.5 * (dot(above.rgb, LUMA) + dot(below.rgb, LUMA));\n"
			+ "	float crest = smoothstep(0.005, 0.035, ridge) * min(above.a, below.a) * image.a;\n"
			+ "	float sunAt = (v_view.x - u_sun.x) / u_sun.y;\n"
			+ "	vec2 q = vec2(a.x / (8.0 * L), a.y / (10.0 * L));\n"
			// Which pieces of the crests are lit: two waves along them, of uneven turns, their sum -1.6..1.6.
			+ "	float n = (cos(TAU * (u_glintA.x * q.x + u_glintA.y * q.y)) + 0.6 * cos(TAU * (u_glintA.z * q.x + u_glintA.w * q.y + 0.3))) / 1.6;\n"
			// Each piece stays where it is and fades in and out at its own moment, set by the third wave.
			+ "	float pulse = 0.5 + 0.5 * cos(TAU * (u_glint.z + 1.5 * sin(TAU * (u_glintB.x * q.x + u_glintB.y * q.y))));\n"
			+ "	float glow = smoothstep(1.0 - u_glint.y, 1.0, n) * pulse * pulse * crest * exp(-sunAt * sunAt);\n"
			+ "	color.rgb = min(color.rgb + u_glint.x * glow * vec3(1.0, 0.96, 0.85), 1.0);\n"
			+ "#endif\n"
			+ "	gl_FragColor = hazed(color);\n"
			+ "}\n";
}
