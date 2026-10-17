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
 * The animation lab (r407, after r375): the shader animations r375 proposed for water, SWELL, GLINT and MIRROR,
 * written here as lab GLSL (GLSL ES 1.0, sines only, as FOG's noise is) and drawn by the real reader in WebGL, so Simon
 * can judge which to ship before any is written three times. Opened with anim-lab.html: tools/anim-lab.sh. The clouds'
 * BILLOW has a lab of its own since r451, {@link CloudsLab}.
 * <p>
 * The sea: the AI beach page (core/test-data/anim: r365's sea, r478's backdrop), its sea (l1) a SHADER layer drawn four ways: WAVE (what ships,
 * GdxLayerEffects' own), SWELL, GLINT, and SWELL then GLINT in one shader. SWELL ships since r431: it, and SWELL + GLINT,
 * are GdxLayerEffects' own SWELL program (r435), the lab's GLSL keeps its SWELL for its other MIRROR views. MIRROR by
 * the water's colour + GLINT ships since r455 (Enum_ShaderEffect.MIRROR): it, and the detail levels' 'high', draw
 * through GdxLayerEffects' MIRROR program, with the sliders' MIRROR and GLINT numbers in its uniforms; the lab's GLSL
 * keeps MIRROR as tuned and by the water's colour without GLINT, as a record. Each effect is written in
 * world units over {@code along()}, as WAVE and FOG are, so it runs on across the tiles; its amplitude, wavelength and
 * speed are the layer's shader numbers, as a shipped effect's would be. GLINT's own numbers (strength, glint size,
 * density, twinkle, how closely it keeps to the crests, the sun's place and width) are the lab's {@code u_glint}: in
 * SWELL + GLINT the layer's numbers are the swell's.
 * <p>
 * {@code &clip=1}: nothing moves by itself, {@code window.animLabAct(seconds)} steps every heart at 1/60 s.
 */
public class AnimLab extends ApplicationAdapter implements LabKit.Clip
{
	static final String SEA_PAGE = "anim-lab/beach.jplax";
	/** The sea layer's region in the beach page. */
	static final String SEA = "l1";
	/** FogLab's world and panels, the panels lower: five rows of sliders fit under them in the 1580x710 window. */
	static final float WORLD_WIDTH = FogLab.WORLD_WIDTH, WORLD_HEIGHT = FogLab.WORLD_HEIGHT;
	static final int PANEL_WIDTH = FogLab.FRAME_WIDTH, PANEL_HEIGHT = 200;

	/** What a panel draws its animated layer through. */
	static final int WAVE = 0, SWELL = 1, GLINT = 2, BOTH = 3, MIRROR = 4, MIRROR_OWN = 5, MIRROR_GLINT = 6, NONE = 7;
	static final String[] EFFECT_NAMES = { "WAVE (ships today)", "SWELL", "GLINT", "SWELL + GLINT", "SWELL + MIRROR as you tuned it (fixed)",
			"SWELL + MIRROR by the water's colour", "SWELL + MIRROR by the water's colour + GLINT", "nothing" };

	/**
	 * The views the choice picks: four panels, three, two one over the other, or one, close. Each: its hearts (indices
	 * into {@link #hearts}). The first (r462, Simon: "should display what is getting exported, aka Nothing, Swell + mirror
	 * + glint, and swell + glint ... (detail level) so someone using the parallax can hook up different quality level"):
	 * the sea at each detail level a game may pick, at the sliders' numbers, which start at his (r419, 16 Oct 15:02).
	 */
	static final String[] VIEWS = { "detail levels", "sea, all four", "sea: both", "sea: SWELL", "sea: GLINT", "sea: WAVE", "sea: SWELL + MIRROR", "sea: MIRROR or not" };
	static final int[][] VIEW_HEARTS = { { 8, 9, 10 }, { 0, 1, 2, 3 }, { 3 }, { 1 }, { 2 }, { 0 }, { 7 }, { 4, 5, 6, 7 } };
	/** Each heart's effect, on the beach's sea. */
	static final int[] HEART_EFFECTS = { WAVE, SWELL, GLINT, BOTH, SWELL, MIRROR, MIRROR_OWN, MIRROR_GLINT, MIRROR_GLINT, BOTH, NONE };
	/** The detail levels' hearts, from {@link #FIRST_LEVEL}: their titles, the most a game draws first. */
	static final int FIRST_LEVEL = 8;
	static final String[] LEVEL_NAMES = { "high: SWELL + MIRROR + GLINT", "medium: SWELL + GLINT", "off: nothing" };
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
	/** MIRROR as Simon tuned it on r419 (16 Oct 13:15: strength, relief, shine, tint), its panel's numbers, fixed: the sliders move the others. */
	static final float[] MIRROR_TUNED = { 0.15f, 3, 1, 1 };
	/**
	 * Where each heart's frame is held, as a fraction of the frame's height from its bottom, at the panel's middle: the
	 * beach's sea; MIRROR's views lower, on the water under the horizon.
	 */
	static final float[] HEART_FOCUS = { 0.3f, 0.3f, 0.3f, 0.3f, 0.24f, 0.24f, 0.24f, 0.24f, 0.24f, 0.24f, 0.24f };

	private final Parallax_Heart[] hearts = new Parallax_Heart[HEART_EFFECTS.length];
	private final ParallaxLayer[] animated = new ParallaxLayer[HEART_EFFECTS.length];
	private final AnimEffects[] effects = new AnimEffects[HEART_EFFECTS.length];
	private SelectElement view;
	private InputElement zoom, scroll, waveAmplitude, waveWavelength, waveSpeed, swellAmplitude, swellWavelength, swellSpeed, glintStrength,
			glintSize, glintDensity, glintSpeed, glintCrest, sunX, sunWidth, mirrorStrength, mirrorRelief, mirrorShine, mirrorTint;
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
			WholePage_Model page = Utils_Page_Json.loadPage(SEA_PAGE);
			// The first layer of that region: the reader makes its layers in the page's order, one each (these pages have
			// no EMPTY or PARTICLES layer).
			int index = -1;
			for (int k = page.pageModel.pageList.size() - 1; k >= 0; k--)
				if (SEA.equals(page.pageModel.pageList.get(k).regionName))
					index = k;
			if (index < 0)
				throw new IllegalStateException(SEA_PAGE + " has no layer " + SEA);
			Parallax_Model model = page.pageModel.pageList.get(index);
			// What a page would store: the layer a SHADER one, under WAVE or, since r431, SWELL, whose periods (8
			// wavelengths across, 10 up) the lab's GLINT and MIRROR are whole turns over too (r419).
			model.kind = HEART_EFFECTS[i] == NONE ? Enum_LayerKind.IMAGE : Enum_LayerKind.SHADER;
			model.shaderEffect = HEART_EFFECTS[i] == MIRROR_GLINT ? Enum_ShaderEffect.MIRROR
					: HEART_EFFECTS[i] != WAVE ? Enum_ShaderEffect.SWELL : Enum_ShaderEffect.WAVE;
			// The shipped MIRROR's water (r455): its horizon in the strip and its depth, as a page stores them.
			model.mirrorHorizon = SEA_HORIZON;
			model.mirrorDepth = MIRROR_DEPTH;
			OrthographicCamera camera = new OrthographicCamera();
			camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i] = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i].setPage(page);
			animated[i] = hearts[i].parallaxReader.layers.get(index);
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
		waveAmplitude = LabControls.slider(document, controls, "waveAmplitude", "WAVE amplitude (world units)", 0, 0.5f, 0.01f, 0.06f);
		waveWavelength = LabControls.slider(document, controls, "waveWavelength", "WAVE wavelength (world units)", 0.1f, 4, 0.05f, 0.6f);
		waveSpeed = LabControls.slider(document, controls, "waveSpeed", "WAVE speed (world units/s)", 0, 2, 0.05f, 0.3f);
		swellAmplitude = LabControls.slider(document, controls, "swellAmplitude", "SWELL amplitude (world units)", 0, 0.5f, 0.01f, 0.12f);
		swellWavelength = LabControls.slider(document, controls, "swellWavelength", "SWELL wavelength (world units)", 0.25f, 6, 0.05f, 1.25f);
		swellSpeed = LabControls.slider(document, controls, "swellSpeed", "SWELL speed (world units/s)", 0, 2, 0.05f, 0.35f);
		glintStrength = LabControls.slider(document, controls, "glintStrength", "GLINT strength (light added, 0..1.5)", 0, 1.5f, 0.05f, 0.5f);
		glintSize = LabControls.slider(document, controls, "glintSize", "GLINT size (a glint's length, world units)", 0.2f, 3, 0.05f, 0.8f);
		glintDensity = LabControls.slider(document, controls, "glintDensity", "GLINT density (how much of the crests is lit, 0.05..1.5)", 0.05f, 1.5f, 0.05f, 0.6f);
		glintSpeed = LabControls.slider(document, controls, "glintSpeed", "GLINT twinkle (pulses a second: a glint fades in and out in place)", 0, 2, 0.05f, 0.3f);
		glintCrest = LabControls.slider(document, controls, "glintCrest", "GLINT crest reach (world units: a crest is brighter than the water this far above and below; more, thicker lines)", 0.05f, 0.6f, 0.05f, 0.2f);
		sunX = LabControls.slider(document, controls, "sunX", "sun's place (0..1 across the view: the glitter's column)", 0, 1, 0.05f, 0.25f);
		sunWidth = LabControls.slider(document, controls, "sunWidth", "sun's width (of the view: 2 lights it all)", 0.05f, 2, 0.05f, 0.45f);
		Element mirrors = LabControls.section(document, controls, "MIRROR (the whole sea a mirror of the sky, like polished metal: views 'sea: SWELL + MIRROR' and 'sea: MIRROR or not')");
		mirrorStrength = LabControls.slider(document, mirrors, "mirrorStrength", "strength (how much of the water is the mirrored sky: 1 all of it)", 0, 1, 0.05f, 0.15f);
		mirrorRelief = LabControls.slider(document, mirrors, "mirrorRelief", "relief (how much the painted waves bend the mirror, beside the swell)", 0, 3, 0.1f, 3);
		mirrorShine = LabControls.slider(document, mirrors, "mirrorShine", "shine (contrast of the mirrored sky: 0 soft, 1 hard bright and dark facets, like polished metal)", 0, 1, 0.05f, 1);
		mirrorTint = LabControls.slider(document, mirrors, "mirrorTint", "tint (how much the water's own colour tints the mirror, as a metal tints what it reflects: 0 a chrome mirror)", 0, 1, 0.05f, 1);
		readout = LabControls.readout(document, controls, "anim-readout");
	}

	/** Puts the sliders into every heart. */
	private void applyControls()
	{
		int v = LabControls.read(view);
		LabControls.read(zoom);
		float sc = LabControls.read(scroll);
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
			else
				shader(layer, sa, sw, ss);
			hearts[i].screenSpeedConstantX = sc;
		}
		int[] shown = VIEW_HEARTS[v];
		// The labels' grid is two by two: two panels, one over the other, take its left column.
		for (int k = 0; k < labels.length; k++)
		{
			int h = shown.length == 2 ? (k % 2 == 0 ? k / 2 : -1) : k;
			boolean titled = h >= 0 && h < shown.length;
			labels[k].setInnerText(!titled ? "" : shown[h] >= FIRST_LEVEL ? LEVEL_NAMES[shown[h] - FIRST_LEVEL]
					: "beach: " + EFFECT_NAMES[HEART_EFFECTS[shown[h]]]);
			// A chip over no panel is hidden, not a dark dash; it keeps its cell in the grid.
			labels[k].getParentElement().getStyle().setProperty("visibility", titled || k == 0 ? "visible" : "hidden");
		}
		ParallaxLayer sea = animated[0];
		float seaPx = sea.getDrawnRegion().getRegionWidth() / (sea.getWidth() * sea.getPackedWidthRatio());
		readout.setInnerText("sea layer " + round(sea.getWidth()) + " x " + round(sea.getHeight()) + " world units, its image "
				+ round(seaPx) + " px a unit: SWELL " + round(sa * seaPx) + " px, wavelength " + round(sw * seaPx) + " px; GLINT glints "
				+ round(gs * seaPx) + " px long. The world is " + round(WORLD_WIDTH) + " units wide.");
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
		if (LabKit.frame(x, top - h, w, h, x - (frameWidth - w) / 2, bottom, frameWidth, frameHeight))
			hearts[i].render();
	}

	/**
	 * A heart's effects: its sea through the lab's GLSL for its panel's effect (WAVE: GdxLayerEffects' own source), every
	 * other layer through GdxLayerEffects, as FogLab's are. SWELL, and SWELL + GLINT, draw through GdxLayerEffects'
	 * own SWELL program, as a game does (r435): the latter with the lab's glint knobs in the shipped glint's uniforms,
	 * where a game has its strength alone and GdxLayerEffects' GLINT_ numbers. MIRROR + GLINT draws through its own
	 * MIRROR program (r455), the lab's mirror knobs in u_mirror, where a game has GdxLayerEffects' MIRROR_ numbers.
	 */
	static final class AnimEffects extends LabKit.Effects
	{
		private static ShaderProgram wave, glint, mirror, mirrorOwn;

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
		public void setView(float width, float height, float left, float floor)
		{
			super.setView(width, height, left, floor);
			shipped.setView(width, height, left, floor);
		}

		@Override
		public void setRepeat(boolean x, boolean y)
		{shipped.setRepeat(x, y);}

		/**
		 * Puts back what GdxLayerEffects left bound, as a game's does: without it (before r465) a layer the reader draws
		 * plain, the beach's palms in front (no fog), drew through the program the layer behind it left, with that
		 * layer's fog (about 0.1 of the fog's colour).
		 */
		@Override
		public void release(Batch batch)
		{shipped.release(batch);}

		@Override
		public boolean beginPageFog(Batch batch, Color tint, Color fog, Color incomingFog)
		{return shipped.beginPageFog(batch, tint, fog, incomingFog);}

		@Override
		public void endPageFog(Batch batch)
		{shipped.endPageFog(batch);}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase, float fogAmount, Color fogColor)
		{
			// Nothing: the sea an IMAGE layer, drawn as the page draws it.
			byShipped = layer != target || effect == NONE || effect == SWELL || effect == BOTH || effect == MIRROR_GLINT;
			if (byShipped)
			{
				boolean begun = shipped.begin(batch, layer, phase, fogAmount, fogColor);
				if (!begun || layer != target || (effect != BOTH && effect != MIRROR_GLINT))
					return begun;
				// The lab's glint over the shipped one's: its strength, density, twinkle, crest reach, sun, and its waves'
				// turns over the periods, which are SWELL's (8 wavelengths across, 10 up).
				float[] g = lab.glint;
				ShaderProgram program = batch.getShader();
				float time = (float) (heart.parallaxReader.getEffectTime() * g[2] % 1000.0);
				program.setUniformf(program.fetchUniformLocation("u_glint", false), g[0], g[1], time, g[3]);
				program.setUniformf(program.fetchUniformLocation("u_sun", false), g[4], g[5]);
				program.setUniformf(program.fetchUniformLocation("u_glintA", false), g[6], g[7], g[8], g[9]);
				program.setUniformf(program.fetchUniformLocation("u_glintB", false), g[10], g[11], layer.getShaderPeriod(), layer.getShaderPeriodY());
				float[] m = lab.mirror;
				if (effect == MIRROR_GLINT)
					program.setUniformf(program.fetchUniformLocation("u_mirror", false), m[0], m[1], m[2], m[3]);
				return true;
			}
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
			float[] m = lab.mirror, own = effect == MIRROR ? MIRROR_TUNED : m;
			program.setUniformf(program.fetchUniformLocation("u_mirror", false), own[0], own[1], own[2], own[3]);
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
				case GLINT:
					return glint != null ? glint : (glint = compile("#define GLINT\n", "GLINT"));
				case MIRROR_OWN:
					return mirrorOwn != null ? mirrorOwn : (mirrorOwn = compile("#define SWELL\n#define MIRROR\n#define OWN\n", "SWELL + MIRROR by the water's colour"));
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
	 * <li>MIRROR (r419, after REFLECT and SHEEN, Simon: "something that affects the whole of the water ... a mirror
	 * texture, a bit like metallic"): every pixel of the water is a mirror of the sky, as polished metal is of what is
	 * round it. Flat, it mirrors the pale sky at the horizon (u_sky) and, u_mirrorAt.y world units under it, the bluer sky
	 * over the view (u_zenith); tilted, by the swell's slopes and the painting's own relief (its brightness's slope,
	 * times u_mirror.y), it mirrors higher or lower, so the facets go bright and dark as the swell moves. u_mirror: how
	 * much of the water is the mirror, relief, shine (the mirrored sky's contrast: hard facets), tint (the water's colour
	 * over the mirror, as a metal tints what it reflects); u_mirrorAt.x: the horizon's height in the tile. Its only x
	 * terms are the swell's, whole turns over FOG's periods. With OWN (r419, Simon at 13:15: "accounting for every colour
	 * the same", the darker water washed), the painting keeps its colour and a facet only bends it, by how much more or
	 * less light it mirrors than flat water there; tint then keeps the water's hue in that bend. GLINT runs over it as
	 * it does over SWELL.</li>
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
			// What the water mirrors at sky (0 the horizon's sky, 1 the sky over the view). Past the horizon's sky, a facet
			// mirrors the sun's haze, white; past the sky over the view, the deeper sky behind us, darker. Shine sharpens the
			// ramp into hard facets, and opens those two ends, as polished metal's light and dark.
			+ "vec3 mirrorOf(float sky)\n"
			+ "{\n"
			+ "	float hard = 0.5 - 0.4 * u_mirror.z;\n"
			+ "	vec3 mirrored = mix(u_sky, u_zenith, smoothstep(0.5 - hard, 0.5 + hard, sky));\n"
			+ "	mirrored = mix(mirrored, vec3(1.0), u_mirror.z * (1.0 - smoothstep(-0.3, 0.1, sky)));\n"
			+ "	return mirrored * (1.0 - 0.5 * u_mirror.z * smoothstep(0.9, 1.4, sky));\n"
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
			+ "	vec4 image = at(l);\n"
			+ "	vec4 color = v_color * image;\n"
			+ "#ifdef MIRROR\n"
			+ "	const vec3 GREY = vec3(0.299, 0.587, 0.114);\n"
			// The painting's relief: its brightness's slope over a pixel or two, the painted crests' faces.
			+ "	const float D = 0.05;\n"
			+ "	float relief = dot(at(l + vec2(0.0, D)).rgb - at(l - vec2(0.0, D)).rgb, GREY) / (2.0 * D);\n"
			+ "	float facet = tilt + 0.5 * u_mirror.y * relief;\n"
			// What this pixel mirrors: lower in the water, higher in the sky; a facet tilted toward us, higher still.
			+ "	float level = (u_mirrorAt.x - local().y) / u_mirrorAt.y;\n"
			+ "	vec3 mirrored = mirrorOf(level + 1.2 * facet);\n"
			+ "#ifdef OWN\n"
			// By the water's colour (Simon on r419: "accounting for every colour the same", its darker parts washed): a
			// facet bends the painting's own colour by how much more or less light it mirrors than flat water there does, so
			// dark and pale water move alike, each from its own colour. Tint: that bend in grey (the water's hue kept) or
			// in colour (the sky's hue over it).
			+ "	vec3 still = mirrorOf(level);\n"
			+ "	vec3 bend = mirrored / max(still, 0.05);\n"
			+ "	bend = mix(bend, vec3(dot(mirrored, GREY) / max(dot(still, GREY), 0.05)), u_mirror.w);\n"
			+ "	color.rgb = mix(color.rgb, min(color.rgb * bend, 1.0), u_mirror.x);\n"
			+ "#else\n"
			// As a metal: its own colour over what it mirrors, the painting's light and dark kept in it.
			+ "	float grey = dot(color.rgb, GREY);\n"
			+ "	vec3 metal = mirrored * mix(vec3(1.0), color.rgb / max(grey, 0.05), u_mirror.w) * (0.6 + 0.6 * grey / max(dot(v_color.rgb, GREY), 0.05));\n"
			+ "	color.rgb = mix(color.rgb, min(metal, 1.0), u_mirror.x);\n"
			+ "#endif\n"
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
