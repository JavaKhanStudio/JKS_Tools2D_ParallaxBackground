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
	static final int STILL = 0, WAVE = 1, SWELL = 2, GLINT = 3, BOTH = 4, BILLOW = 5, REFLECT = 6;
	static final String[] EFFECT_NAMES = { "still", "WAVE (ships today)", "SWELL", "GLINT", "SWELL + GLINT", "BILLOW", "SWELL + REFLECT" };

	/** The views the choice picks: four panels, two one over the other, or one, close. Each: its hearts (indices into {@link #hearts}). */
	static final String[] VIEWS = { "sea, all four", "sea: both", "sea: SWELL", "sea: GLINT", "sea: WAVE", "clouds, all four", "clouds: BILLOW",
			"sea: SWELL + REFLECT", "sea: REFLECT or not" };
	static final int[][] VIEW_HEARTS = { { 0, 1, 2, 3 }, { 3 }, { 1 }, { 2 }, { 0 }, { 4, 5, 6, 7 }, { 5 }, { 8 }, { 9, 8 } };
	/** Each heart: its page, its layer, its effect. 0-3, 8 and 9 the sea, 4-7 the clouds. */
	static final String[] HEART_PAGES = { SEA_PAGE, SEA_PAGE, SEA_PAGE, SEA_PAGE, CALM_PAGE, CALM_PAGE, CALM_PAGE, CALM_PAGE, SEA_PAGE, SEA_PAGE };
	static final String[] HEART_LAYERS = { SEA, SEA, SEA, SEA, CLOUDS, CLOUDS, CLOUDS, CLOUDS, SEA, SEA };
	/** Whether a heart draws its page without the layers in front of the animated one: calm's clouds alone. */
	static final boolean[] HEART_ALONE = { false, false, false, false, false, false, true, true, false, false };
	static final int[] HEART_EFFECTS = { WAVE, SWELL, GLINT, BOTH, STILL, BILLOW, STILL, BILLOW, REFLECT, SWELL };
	static final String[] HEART_SETS = { "beach", "beach", "beach", "beach", "calm (round1 s02)", "calm (round1 s02)", "calm's clouds alone",
			"calm's clouds alone", "beach", "beach" };
	/**
	 * REFLECT (r419, after Simon: "more feeling of reflexion"): the beach's far range, {@link #RANGE}, drawn again as a
	 * layer right over the sea, flipped on Y, at the range's own speeds, so the mirrored range scrolls with the range.
	 * Its mirror axis is the sea's top line: the sea strip's top {@link #SEA_HORIZON} of its height is water, the rest
	 * clear (rows 300 to 383 of 384 in beach-sea.png).
	 */
	static final String RANGE = "l0";
	static final float SEA_HORIZON = 84 / 384f;
	/** The beach sea's mean colour (beach-sea.png's water), what REFLECT tints the mirrored range toward. */
	static final float[] WATER = { 0.37f, 0.71f, 0.79f };
	/** The beach page's sky at its top (topHalf_top), what REFLECT mirrors deep under the horizon; its haze colour nearer. */
	static final float[] SKY = { 0.31f, 0.56f, 0.82f };
	/**
	 * Where each heart's frame is held, as a fraction of the frame's height from its bottom, at the panel's middle: the
	 * beach's sea, calm's clouds; REFLECT's views lower, on the water under the horizon.
	 */
	static final float[] HEART_FOCUS = { 0.3f, 0.3f, 0.3f, 0.3f, 0.82f, 0.82f, 0.82f, 0.82f, 0.24f, 0.24f };

	private final Parallax_Heart[] hearts = new Parallax_Heart[HEART_PAGES.length];
	private final ParallaxLayer[] animated = new ParallaxLayer[HEART_PAGES.length];
	/** REFLECT's mirrored range, in the hearts that draw one; null in the others. */
	private final ParallaxLayer[] mirrored = new ParallaxLayer[HEART_PAGES.length];
	/** Each clouds layer's own drift (its at rest speed), which the drift slider scales. */
	private final float[] drifts = new float[HEART_PAGES.length];
	private final AnimEffects[] effects = new AnimEffects[HEART_PAGES.length];
	private SelectElement view;
	private InputElement zoom, scroll, waveAmplitude, waveWavelength, waveSpeed, swellAmplitude, swellWavelength, swellSpeed, glintStrength,
			glintSize, glintDensity, glintSpeed, glintCrest, sunX, sunWidth, billowAmplitude, billowWavelength, billowSpeed, drift, reflectStrength,
			reflectDepth, reflectRipple, reflectBand, reflectTint, reflectSquash;
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
	/** REFLECT's numbers: where the range's foot is under the horizon (world units), how deep it fades, strength, band height, tint, squash. */
	final float[] reflect = new float[6];

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
				boolean swells = HEART_EFFECTS[i] == SWELL || HEART_EFFECTS[i] == BOTH || HEART_EFFECTS[i] == GLINT || HEART_EFFECTS[i] == REFLECT;
				model.shaderEffect = swells ? Enum_ShaderEffect.FOG : Enum_ShaderEffect.WAVE;
			}
			int rangeIndex = -1;
			if (HEART_EFFECTS[i] == REFLECT)
			{
				// The range again, right over the sea: flipped, its foot hidden under the horizon as the range's is, so the
				// mirror's axis is the sea's top line. Every bottom here is the layer's currentDistanceY: the lab's camera
				// never moves up or down, which would move the range and the sea apart (the mirror then off its axis).
				Parallax_Model range = null;
				for (Parallax_Model m : page.pageModel.pageList)
					if (RANGE.equals(m.regionName))
						range = m;
				float height = WORLD_WIDTH * 384 / 1024f, percent = WORLD_HEIGHT / 100;
				float horizon = model.decal_Y_Ratio * percent + height * SEA_HORIZON, foot = horizon - range.decal_Y_Ratio * percent;
				Parallax_Model mirror = new Parallax_Model();
				mirror.regionName = RANGE;
				mirror.flipY = true;
				mirror.parallaxScalingSpeedX = range.parallaxScalingSpeedX;
				mirror.parallaxScalingSpeedY = range.parallaxScalingSpeedY;
				mirror.decal_X_Ratio = range.decal_X_Ratio;
				// Flipped, the image's bottom row is at the box's top: local height foot goes on the horizon.
				mirror.decal_Y_Ratio = (horizon + foot - height) / percent;
				mirror.kind = Enum_LayerKind.SHADER;
				mirror.shaderEffect = Enum_ShaderEffect.FOG;
				rangeIndex = index + 1;
				page.pageModel.pageList.add(rangeIndex, mirror);
				reflect[0] = foot;
			}
			OrthographicCamera camera = new OrthographicCamera();
			camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i] = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i].setPage(page);
			animated[i] = hearts[i].parallaxReader.layers.get(index);
			drifts[i] = animated[i].getSpeedAtRest();
			if (rangeIndex >= 0)
				mirrored[i] = hearts[i].parallaxReader.layers.get(rangeIndex);
			effects[i] = new AnimEffects(this, HEART_EFFECTS[i], animated[i], mirrored[i], hearts[i]);
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
		for (int i = 0; i < labels.length; i++)
		{
			labels[i] = document.createDivElement();
			labels[i].setClassName("panel-label");
			labelGrid.appendChild(labels[i]);
		}
		Element controls = document.getElementById("anim-controls");
		view = LabControls.choice(document, controls, "view", "view (four panels, or one effect close)", VIEWS, 0);
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
		reflectStrength = LabControls.slider(document, controls, "reflectStrength", "REFLECT strength (the mirrored range's opacity at the horizon)", 0, 1, 0.05f, 0.75f);
		reflectDepth = LabControls.slider(document, controls, "reflectDepth", "REFLECT depth (world units under the horizon where it has faded out)", 0.5f, 6, 0.1f, 4.5f);
		reflectRipple = LabControls.slider(document, controls, "reflectRipple", "REFLECT ripple (sideways shift of its bands, world units, at the horizon; twice that at its depth)", 0, 0.4f, 0.01f, 0.05f);
		reflectBand = LabControls.slider(document, controls, "reflectBand", "REFLECT bands (their height, world units: thinner, a more broken reflection)", 0.03f, 0.6f, 0.01f, 0.1f);
		reflectTint = LabControls.slider(document, controls, "reflectTint", "REFLECT tint (how much of the water's colour it takes)", 0, 1, 0.05f, 0.7f);
		reflectSquash = LabControls.slider(document, controls, "reflectSquash", "REFLECT squash (the range's height over the reflection's: 1 a true mirror, which on this thin sea shows only the range's foot)", 1, 5, 0.1f, 2.5f);
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
		reflect[1] = LabControls.read(reflectDepth);
		reflect[2] = LabControls.read(reflectStrength);
		reflect[3] = LabControls.read(reflectBand);
		reflect[4] = LabControls.read(reflectTint);
		reflect[5] = LabControls.read(reflectSquash);
		float rr = LabControls.read(reflectRipple);
		for (int i = 0; i < hearts.length; i++)
		{
			ParallaxLayer layer = animated[i];
			int effect = HEART_EFFECTS[i];
			if (effect == WAVE)
				shader(layer, wa, ww, ws);
			else if (effect == SWELL || effect == BOTH || effect == GLINT || effect == REFLECT)
				shader(layer, sa, sw, ss);
			// Its ripple on the swell's wavelength and speed, so its time and its x run on the swell's whole turns.
			if (mirrored[i] != null)
				shader(mirrored[i], rr, sw, ss);
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
			labels[k].setInnerText(h >= 0 && h < shown.length ? HEART_SETS[shown[h]] + ": " + EFFECT_NAMES[HEART_EFFECTS[shown[h]]] : "");
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
		private static ShaderProgram wave, swell, glint, both, billow, reflect;

		private final AnimLab lab;
		private final int effect;
		private final ParallaxLayer target, mirrored;
		private final Parallax_Heart heart;
		private final GdxLayerEffects shipped = new GdxLayerEffects();
		private boolean byShipped;
		private ShaderProgram previous;

		AnimEffects(AnimLab lab, int effect, ParallaxLayer target, ParallaxLayer mirrored, Parallax_Heart heart)
		{
			this.lab = lab;
			this.effect = effect;
			this.target = target;
			this.mirrored = mirrored;
			this.heart = heart;
		}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{return begin(batch, layer, phase, 0, Color.WHITE);}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase, float fogAmount, Color fogColor)
		{
			byShipped = (layer != target || effect == STILL) && (layer != mirrored || mirrored == null);
			if (byShipped)
				return shipped.begin(batch, layer, phase, fogAmount, fogColor);
			ShaderProgram program = program(layer == mirrored ? -1 : effect == REFLECT ? SWELL : effect);
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
			float[] r = lab.reflect;
			program.setUniformf(program.fetchUniformLocation("u_reflect", false), r[0], r[1], r[2], r[3]);
			program.setUniformf(program.fetchUniformLocation("u_water", false), WATER[0], WATER[1], WATER[2], r[4]);
			program.setUniformf(program.fetchUniformLocation("u_sky", false), SKY[0], SKY[1], SKY[2]);
			program.setUniformf(program.fetchUniformLocation("u_squash", false), r[5]);
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
					return reflect != null ? reflect : (reflect = compile("#define REFLECT\n", "REFLECT"));
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
	 * <li>REFLECT (r419, after Simon: "more feeling of reflexion"): drawn on the beach's far range flipped over the sea
	 * ({@link #RANGE}), not on the sea: below the horizon, the range squashed by u_squash (a true mirror of a range taller
	 * than the sea is deep shows only its foot), the sky mirrored where the range is clear (u_fog at the horizon, u_sky
	 * deeper), the range's mirror darkened by the water's colour (u_water, its a how much) so its silhouette reads in the
	 * sheen, cut in bands u_reflect.w high that slide sideways by the layer's amplitude and thin out, faded out
	 * u_reflect.y under the horizon, at most u_reflect.z opaque. u_reflect.x: how far under the horizon the range's
	 * foot is. Its x terms are whole turns over FOG's periods, its time terms whole multiples of t.</li>
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
			+ "uniform vec4 u_reflect;\n"
			+ "uniform vec4 u_water;\n"
			+ "uniform vec3 u_sky;\n"
			+ "uniform float u_squash;\n"
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
			+ "#ifdef BILLOW\n"
			+ "	vec2 p = a / L;\n"
			+ "	float dx = sin(TAU * (p.x + 0.7 * sin(TAU * p.y) - t)) + 0.5 * sin(TAU * (2.0 * p.x - 1.3 * p.y + t + 0.3));\n"
			+ "	float dy = sin(TAU * (2.0 * p.x + p.y - t + 0.6)) + 0.5 * sin(TAU * (p.x - 2.0 * p.y - 2.0 * t));\n"
			+ "	l += u_effect.x / 1.5 * vec2(dx, -dy);\n"
			+ "#endif\n"
			+ "#ifdef REFLECT\n"
			// e: how far under the horizon this pixel of the mirrored range is, the height over the horizon it mirrors.
			+ "	float e = l.y - u_reflect.x;\n"
			+ "	float deep = clamp(e / u_reflect.y, 0.0, 1.0);\n"
			// Bands u_reflect.w high, each pushed sideways on its own beat, more the deeper (the water nearer, its waves
			// seen steeper); across, the swell's whole turns over FOG's periods, so it runs on across the wrap.
			+ "	float b = e / u_reflect.w;\n"
			+ "	float qx = a.x / (8.0 * L);\n"
			+ "	float shift = 0.65 * sin(TAU * (b * 0.37 - t)) + 0.35 * sin(TAU * (3.0 * qx + b * 0.91 + 2.0 * t));\n"
			+ "	l.x += u_effect.x * (1.0 + deep) * shift;\n"
			// Squashed: a true mirror (1) of a range taller than the sea is deep only shows its foot.
			+ "	l.y = u_reflect.x + e * u_squash;\n"
			// Some bands break: their reflection thins where the water's crest catches the sky instead.
			+ "	float gap = 0.5 + 0.5 * sin(TAU * (5.0 * qx + b * 0.53 - t + 0.25 * sin(TAU * (2.0 * qx - b * 0.29))));\n"
			+ "	float keep = u_reflect.z * (1.0 - deep * deep * (3.0 - 2.0 * deep)) * smoothstep(0.0, 0.08, e) * (1.0 - 0.6 * gap * deep - 0.25 * gap);\n"
			+ "#endif\n"
			+ "	vec4 image = at(l);\n"
			+ "	vec4 color = v_color * image;\n"
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
			+ "#ifdef REFLECT\n"
			// Where the range is clear, the sky it stands in, mirrored too, as the sky's light on the water: the haze's
			// colour at the horizon, the sky's top colour (u_sky) deeper. The range's mirror takes the water's colour over
			// it, as water darkens what it mirrors: on a hazed page the range is barely off the sky, and its silhouette
			// reads only so, dark in the sheen.
			+ "	vec3 sky = mix(u_fog, u_sky, smoothstep(0.0, 1.0, 1.4 * deep));\n"
			+ "	vec3 range = mix(color.rgb, color.rgb * u_water.rgb * 1.25, u_water.a);\n"
			+ "	color = vec4(mix(sky, range, image.a), v_color.a * keep);\n"
			+ "#endif\n"
			+ "	gl_FragColor = hazed(color);\n"
			+ "}\n";
}
