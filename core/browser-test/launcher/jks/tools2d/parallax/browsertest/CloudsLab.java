package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
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
 * The clouds lab (r451, reworked by r556: "do for clouds what we have done for water: a solid shader [...] it should
 * work for both day and night"): candidate cloud shaders as lab GLSL (GLSL ES 1.0), drawn by the real reader in WebGL,
 * side by side in the three settings Ficelle needs first: a Mediterranean day (the animation lab's beach), a night in a
 * cold forest (the hills lab's forest, tinted) and a medieval fantasy evening (round1's calm, which Ficelle's saga
 * wears). Each panel is a page's gradients, the hour's sun or moon, a clouds layer, then the page's own layers.
 * <ul>
 * <li>PUFFS ({@link #PUFFS_FRAGMENT}) and WISPS ({@link #WISPS_FRAGMENT}) are made in the shader, nothing is painted:
 * cumulus as a smooth union of round puffs on a lattice, streaks as value noise, both whole cells across the layer, so
 * they tile; lit from where the sun or moon stands in the view, in two colours, the lit and the shaded. Day or night
 * is those two colours. PUFFS in 3 flat bands is the same shader, its shade cut in steps.</li>
 * <li>painted, churned and relit: calm's painted clouds under r375's BILLOW ({@link #PAINTED}), their colours
 * replaced by the same two.</li>
 * <li>painted, still: what a page does today, the painting under the page's tint.</li>
 * </ul>
 * The sliders scale each setting's own numbers ({@link #STYLES}), so one move shows in every setting. Opened with
 * clouds-lab.html: tools/clouds-lab.sh.
 * <p>
 * {@code &clip=1}: nothing moves by itself, {@code window.cloudsLabAct(seconds)} steps every heart at 1/60 s.
 */
public class CloudsLab extends ApplicationAdapter implements LabKit.Clip
{
	static final String CLOUDS_PAGE = "fog-lab/s02.jplax", CLOUDS_REGION = "Clouds";
	static final float WORLD_WIDTH = FogLab.WORLD_WIDTH, WORLD_HEIGHT = FogLab.WORLD_HEIGHT;
	/** Three panels a row, two rows: each nearly the page's whole 16:9 frame, cut at its foot. */
	static final int COLUMNS = 3, PANEL_WIDTH = FogLab.FRAME_WIDTH, PANEL_HEIGHT = 376;

	static final String[] SETTINGS = { "Mediterranean day", "cold forest night", "medieval fantasy" };
	static final String[] PAGES = { "anim-lab/beach.jplax", "hills-lab/forest.jplax", CLOUDS_PAGE };
	/** The hills lab's atlas holds the forest's hills layer once per candidate: the page's own strip. */
	static final String FOREST_HILLS = "l2", FOREST_HILLS_STRIP = "h_today_7020";
	static final int PUFFS = 0, WISPS = 1, TOON = 2, RELIT = 3, STILL = 4;
	static final String[] KINDS = { "PUFFS", "WISPS", "PUFFS in 3 flat bands", "painted, churned and relit", "painted, still (today)" };
	/** BILLOW's wavelength (world units) and speed (world units a second), as r451 left them. */
	static final float BILLOW_WAVELENGTH = 6, BILLOW_SPEED = 0.4f;
	/** The flat bands of the TOON panel, when the slider asks for fewer. */
	static final float TOON_STEPS = 3;
	/** Each setting's sky, top and horizon; its layers' tint; its clouds' lit and shaded colours; its sun's or moon's. */
	static final float[][] SKY_TOP = { { 0.16f, 0.45f, 0.86f }, { 0.015f, 0.03f, 0.085f }, { 0.27f, 0.33f, 0.64f } };
	static final float[][] SKY_LOW = { { 0.74f, 0.88f, 0.97f }, { 0.09f, 0.15f, 0.27f }, { 0.99f, 0.74f, 0.50f } };
	static final float[][] TINT = { { 1, 1, 1 }, { 0.26f, 0.34f, 0.56f }, { 1, 0.84f, 0.70f } };
	static final float[][] LIT = { { 1, 1, 1 }, { 0.60f, 0.68f, 0.84f }, { 1, 0.88f, 0.66f } };
	static final float[][] SHADE = { { 0.70f, 0.80f, 0.93f }, { 0.05f, 0.08f, 0.16f }, { 0.62f, 0.42f, 0.56f } };
	static final float[][] DISC = { { 1, 0.98f, 0.88f }, { 0.92f, 0.94f, 1 }, { 1, 0.86f, 0.55f } };
	/** Where each setting's sun or moon stands: degrees round the view's arc from its right end, and its radius, world units. */
	static final float[] SUN_ANGLE = { 118, 62, 35 }, SUN_RADIUS = { 1.5f, 1.1f, 2 };
	/**
	 * The made clouds' numbers, per setting, PUFFS then WISPS: coverage (0 to 1), a puff's size (world units), how many
	 * times longer than tall, the edge's softness, the base (how far up the layer), how flat WISPS' is, the silver
	 * lining, the opacity, the churn (PUFFS: x its breaths' own pace; WISPS: cells a second).
	 */
	static final float[][][] STYLES = {
			{ { 0.40f, 3.4f, 1.3f, 0.035f, 0.24f, 5, 0.3f, 1, 1 }, { 0.52f, 2.2f, 6, 0.30f, 0.45f, 0.6f, 0.2f, 0.75f, 0.04f } },
			{ { 0.44f, 3.6f, 1.5f, 0.08f, 0.24f, 4, 0.9f, 0.9f, 1 }, { 0.60f, 2, 7, 0.35f, 0.45f, 0.5f, 0.9f, 0.7f, 0.03f } },
			{ { 0.48f, 4.2f, 1.2f, 0.03f, 0.22f, 6, 0.6f, 1, 1 }, { 0.56f, 2.4f, 6, 0.30f, 0.45f, 0.6f, 0.6f, 0.8f, 0.04f } } };
	/** The clouds layer of a made kind and of a painted one: its size (x the world's width) and its foot (% of the world's height). */
	static final float MADE_SIZE = 1.6f, MADE_FOOT = 50, PAINTED_SIZE = 1.1f, PAINTED_FOOT = 60;
	/**
	 * The views the choice picks: PUFFS over WISPS in each setting, or a setting under every kind. Each panel: setting,
	 * kind.
	 */
	static final String[] VIEWS = { "the three settings", SETTINGS[0], SETTINGS[1], SETTINGS[2] };
	static final int[][][] VIEW_PANELS = {
			{ { 0, PUFFS }, { 1, PUFFS }, { 2, PUFFS }, { 0, WISPS }, { 1, WISPS }, { 2, WISPS } },
			{ { 0, PUFFS }, { 0, WISPS }, { 0, TOON }, { 0, RELIT }, { 0, STILL } },
			{ { 1, PUFFS }, { 1, WISPS }, { 1, TOON }, { 1, RELIT }, { 1, STILL } },
			{ { 2, PUFFS }, { 2, WISPS }, { 2, TOON }, { 2, RELIT }, { 2, STILL } } };
	/** Where a frame is held, as a fraction of its height from its bottom, at the panel's middle: the sky. */
	static final float FOCUS = 0.75f;

	private final Parallax_Heart[] lands = new Parallax_Heart[SETTINGS.length];
	private final Parallax_Heart[][] skies = new Parallax_Heart[SETTINGS.length][KINDS.length];
	private final ParallaxLayer[][] clouds = new ParallaxLayer[SETTINGS.length][KINDS.length];
	private final CloudEffects[][] effects = new CloudEffects[SETTINGS.length][KINDS.length];
	private final Parallax_Heart[] hearts = new Parallax_Heart[SETTINGS.length * (1 + KINDS.length)];
	/** Each setting's sun or moon, in world units; the clouds layer's own drift (its at rest speed), which the drift slider scales. */
	private final float[] sunX = new float[SETTINGS.length], sunY = new float[SETTINGS.length];
	private float ownDrift;
	private SelectElement view;
	private InputElement zoom, scroll, drift, coverage, size, softness, churn, light, steps, amplitude;
	private Element readout;
	private final Element[] labels = new Element[2 * COLUMNS];
	private boolean clip;
	private SpriteBatch batch;
	private Texture disc;

	@Override
	public void create()
	{
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		// PUFFS and WISPS never read the layer's image, so the GPU drops u_texture, which SpriteBatch sets by name: a shipped
		// one must keep it alive or be set another way (r290).
		ShaderProgram.pedantic = false;
		batch = new SpriteBatch();
		disc = discTexture();
		int n = 0;
		for (int s = 0; s < SETTINGS.length; s++)
		{
			WholePage_Model land = Utils_Page_Json.loadPage(PAGES[s]);
			for (int k = land.pageModel.pageList.size() - 1; k >= 0; k--)
				if (CLOUDS_REGION.equals(land.pageModel.pageList.get(k).regionName))
					land.pageModel.pageList.remove(k);
				else if (s == 1 && FOREST_HILLS.equals(land.pageModel.pageList.get(k).regionName))
					land.pageModel.pageList.get(k).regionName = FOREST_HILLS_STRIP;
			// One gradient down the whole view, the setting's sky; the page's fog toward its horizon.
			land.topHalfSize = 0;
			land.bottomHalfSize = 1;
			land.topHalf_top = color(SKY_TOP[s]);
			land.topHalf_bottom = color(SKY_LOW[s]);
			land.bottomHalf_top = color(SKY_LOW[s]);
			land.bottomHalf_bottom = color(SKY_LOW[s]);
			land.fogColor = color(SKY_LOW[s]);
			lands[s] = hearts[n++] = heart(land);
			lands[s].parallaxReader.addColorTransfert(color(TINT[s]), 0);
			for (int kind = 0; kind < KINDS.length; kind++)
			{
				WholePage_Model sky = Utils_Page_Json.loadPage(CLOUDS_PAGE);
				Parallax_Model model = null;
				for (Parallax_Model each : sky.pageModel.pageList)
					if (CLOUDS_REGION.equals(each.regionName))
						model = each;
				if (model == null)
					throw new IllegalStateException(CLOUDS_PAGE + " has no layer " + CLOUDS_REGION);
				sky.pageModel.pageList.clear();
				sky.pageModel.pageList.add(model);
				boolean made = kind <= TOON;
				model.sizeRatio = made ? MADE_SIZE : PAINTED_SIZE;
				model.decal_Y_Ratio = made ? MADE_FOOT : PAINTED_FOOT;
				if (kind != STILL)
				{
					// What a page would store: a SHADER layer, under FOG's periods until one ships (the lab draws its own).
					model.kind = Enum_LayerKind.SHADER;
					model.shaderEffect = Enum_ShaderEffect.FOG;
				}
				skies[s][kind] = hearts[n++] = heart(sky);
				clouds[s][kind] = skies[s][kind].parallaxReader.layers.get(0);
				ownDrift = clouds[s][kind].getSpeedAtRest();
				if (kind == STILL)
					skies[s][kind].parallaxReader.addColorTransfert(color(TINT[s]), 0);
				else
					skies[s][kind].parallaxReader.setLayerEffects(effects[s][kind] = new CloudEffects(kind == RELIT ? 2 : kind == WISPS ? 1 : 0, LIT[s], SHADE[s]));
			}
		}
		buildControls();
		if (clip)
			LabKit.exportClip("cloudsLab", this);
	}

	private static Color color(float[] rgb)
	{return new Color(rgb[0], rgb[1], rgb[2], 1);}

	private Parallax_Heart heart(WholePage_Model page)
	{
		OrthographicCamera camera = new OrthographicCamera();
		camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
		Parallax_Heart heart = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
		heart.setPage(page);
		return heart;
	}

	/** The sun or the moon: a full disc to 0.3 of the square's half, then a glow fading to nothing at its edge. */
	private static Texture discTexture()
	{
		int size = 128;
		Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
		pixmap.setBlending(Pixmap.Blending.None);
		for (int y = 0; y < size; y++)
			for (int x = 0; x < size; x++)
			{
				float r = Math.min(1, (float) Math.hypot(x + 0.5f - size / 2f, y + 0.5f - size / 2f) / (size / 2f));
				float edge = Math.max(0, Math.min(1, (0.3f - r) * size / 4f));
				pixmap.setColor(1, 1, 1, Math.max(edge, 0.45f * (1 - r) * (1 - r)));
				pixmap.drawPixel(x, y);
			}
		Texture texture = new Texture(pixmap);
		texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
		pixmap.dispose();
		return texture;
	}

	private void buildControls()
	{
		Document document = Document.get();
		Element labelGrid = document.getElementById("clouds-labels");
		// Each chip's title in a span of its own: the frame rewrites it, the view's choice before it in the first stays.
		for (int i = 0; i < labels.length; i++)
		{
			Element chip = document.createDivElement();
			chip.setClassName("panel-label");
			labels[i] = document.createSpanElement();
			chip.appendChild(labels[i]);
			labelGrid.appendChild(chip);
		}
		view = LabControls.stageChoice(document, "clouds-labels", "view", "view (PUFFS over WISPS in each setting, or one setting under every kind of clouds)", VIEWS, 0);
		Element controls = document.getElementById("clouds-controls");
		zoom = LabControls.slider(document, controls, "zoom", "zoom (x the page's frame)", 1, 4, 0.25f, 1);
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll (world units/s)", 0, 120, 5, 20);
		drift = LabControls.slider(document, controls, "cloudDrift", "clouds' drift (x calm's own at rest speed: 0 to judge the churn alone)", 0, 3, 0.05f, 1);
		amplitude = LabControls.slider(document, controls, "billowAmplitude", "painted churn (BILLOW's amplitude, world units: how far the painted clouds' pixels are pushed; its wavelength "
				+ LabControls.text(BILLOW_WAVELENGTH) + " and speed " + LabControls.text(BILLOW_SPEED) + " are r451's)", 0, 1.5f, 0.02f, 0.3f);
		Element made = LabControls.section(document, controls, "PUFFS and WISPS (made in the shader, no painting: each slider scales every setting's own number)");
		coverage = LabControls.slider(document, made, "cloudCoverage", "coverage (x the setting's: how much of the sky is cloud)", 0, 2, 0.05f, 1);
		size = LabControls.slider(document, made, "cloudSize", "size (x the setting's: how large a puff is)", 0.4f, 1.6f, 0.05f, 1);
		softness = LabControls.slider(document, made, "cloudSoftness", "softness (x the setting's: 0 a cut-out edge)", 0, 4, 0.1f, 1);
		churn = LabControls.slider(document, made, "cloudChurn", "churn (x the setting's: how fast the shapes change)", 0, 6, 0.25f, 1);
		light = LabControls.slider(document, made, "lightAngle", "light (degrees added to where the setting's sun or moon stands; the painted, relit clouds follow it too)", -60, 60, 2, 0);
		steps = LabControls.slider(document, made, "toonSteps", "toon steps (0: a smooth shade; more: that many flat bands, lit to shaded)", 0, 5, 1, 0);
		readout = LabControls.readout(document, controls, "clouds-readout");
	}

	/** Puts the sliders into every heart. */
	private void applyControls()
	{
		int v = LabControls.read(view);
		LabControls.read(zoom);
		float sc = LabControls.read(scroll), dr = LabControls.read(drift);
		float co = LabControls.read(coverage), si = LabControls.read(size), so = LabControls.read(softness), ch = LabControls.read(churn);
		float li = LabControls.read(light), st = LabControls.read(steps);
		float ba = LabControls.read(amplitude);
		for (int s = 0; s < SETTINGS.length; s++)
		{
			double angle = Math.toRadians(SUN_ANGLE[s] + li);
			sunX[s] = WORLD_WIDTH / 2 + 16 * (float) Math.cos(angle);
			sunY[s] = 12 + 8.5f * (float) Math.sin(angle);
			lands[s].screenSpeedConstantX = sc;
			for (int kind = 0; kind < KINDS.length; kind++)
			{
				ParallaxLayer layer = clouds[s][kind];
				layer.setSpeedAtRest(ownDrift * dr);
				skies[s][kind].screenSpeedConstantX = sc;
				if (kind == STILL)
					continue;
				layer.setShaderAmplitude(ba);
				layer.setShaderWavelength(BILLOW_WAVELENGTH);
				layer.setShaderSpeed(BILLOW_SPEED);
				CloudEffects e = effects[s][kind];
				e.sunX = sunX[s] / WORLD_WIDTH;
				e.sunY = sunY[s] / WORLD_HEIGHT;
				e.steps = kind == TOON ? Math.max(st, TOON_STEPS) : st;
				if (kind == RELIT)
					continue;
				float[] style = STYLES[s][kind == WISPS ? 1 : 0];
				float width = layer.getWidth() * layer.getPackedWidthRatio();
				e.coverage = Math.min(1, style[0] * co);
				e.cellHeight = style[1] * si;
				// Whole cells across the layer: the clouds run on across its tiles.
				e.cells = Math.max(1, Math.round(width / (style[1] * si * style[2])));
				e.softness = style[3] * so;
				e.base = style[4];
				e.flat = style[5];
				e.rim = style[6];
				e.opacity = style[7];
				e.churn = style[8] * ch;
			}
		}
		int[][] shown = VIEW_PANELS[v];
		for (int k = 0; k < labels.length; k++)
		{
			boolean titled = k < shown.length;
			labels[k].setInnerText(titled ? (char) ('A' + k) + ": " + (v == 0 ? SETTINGS[shown[k][0]] + ", " : "") + KINDS[shown[k][1]] : "");
			// A chip over no panel is hidden, not a dark dash; it keeps its cell in the grid.
			labels[k].getParentElement().getStyle().setProperty("visibility", titled ? "visible" : "hidden");
		}
		CloudEffects a = effects[shown[0][0]][shown[0][1]];
		ParallaxLayer cloud = clouds[shown[0][0]][shown[0][1]];
		readout.setInnerText("A (" + SETTINGS[shown[0][0]] + ", " + KINDS[shown[0][1]] + "): coverage " + round(a.coverage) + ", a puff "
				+ round(cloud.getWidth() / a.cells) + " x " + round(a.cellHeight) + " world units (" + Math.round(a.cells) + " across the layer's "
				+ round(cloud.getWidth()) + "), softness " + round(a.softness) + ", churn " + round(a.churn) + ". The world is "
				+ round(WORLD_WIDTH) + " units wide.");
	}

	private static float round(float value)
	{return Math.round(value * 100) / 100f;}

	@Override
	public void act(float seconds)
	{
		LabKit.clip(seconds, this::applyControls, hearts);
		// Where the painted clouds' churn starts across, and its period: along() jumps a period when the start wraps, and
		// a term that is not whole turns over it jumps there. tools/clouds-lab.sh --before-wrap steps a clip to it (r559).
		int[][] shown = VIEW_PANELS[LabControls.read(view)];
		int s = shown[0][0];
		for (int[] panel : shown)
			if (panel[1] == RELIT)
				s = panel[0];
		ParallaxLayer painted = clouds[s][RELIT];
		readout.setAttribute("data-billow-start", String.valueOf(painted.getEffectStartX()));
		readout.setAttribute("data-billow-period", String.valueOf(painted.getShaderPeriod()));
	}

	@Override
	public void render()
	{
		LabKit.live(clip, this::applyControls, hearts);
		int height = Gdx.graphics.getBackBufferHeight();
		int[][] shown = VIEW_PANELS[LabControls.read(view)];
		float z = Float.parseFloat(zoom.getValue());
		LabKit.beginPanels();
		for (int k = 0; k < shown.length; k++)
			panel(shown[k][0], shown[k][1], (k % COLUMNS) * (PANEL_WIDTH + 4), k < COLUMNS ? height : height - PANEL_HEIGHT - 4, PANEL_WIDTH, PANEL_HEIGHT, z);
		LabKit.endPanels();
	}

	/**
	 * Draws setting {@code s} under the clouds of {@code kind} into the panel {@code w} by {@code h} whose top-left is
	 * at {@code x}, {@code top}: through a 16:9 frame {@code z} times the panel's width, its middle across at the
	 * panel's, its {@link #FOCUS} at the panel's middle up, kept over the whole panel. The sky's gradient, the sun or
	 * moon, the clouds, then the page's layers.
	 */
	private void panel(int s, int kind, int x, int top, int w, int h, float z)
	{
		int frameWidth = Math.round(w * z), frameHeight = Math.round(frameWidth * WORLD_HEIGHT / WORLD_WIDTH);
		int bottom = Math.round(top - h / 2f - FOCUS * frameHeight);
		bottom = Math.max(top - frameHeight, Math.min(top - h, bottom));
		if (!LabKit.frame(x, top - h, w, h, x - (frameWidth - w) / 2, bottom, frameWidth, frameHeight))
			return;
		Parallax_Heart land = lands[s], sky = skies[s][kind];
		land.worldCamera.update();
		land.drawBackGround();
		batch.setProjectionMatrix(land.worldCamera.combined);
		batch.enableBlending();
		batch.begin();
		float r = SUN_RADIUS[s] / 0.3f;
		batch.setColor(DISC[s][0], DISC[s][1], DISC[s][2], 1);
		batch.draw(disc, sunX[s] - r, sunY[s] - r, 2 * r, 2 * r);
		batch.setColor(Color.WHITE);
		sky.parallaxReader.draw(sky.worldCamera, batch);
		land.parallaxReader.draw(land.worldCamera, batch);
		batch.end();
	}

	@Override
	public void dispose()
	{
		batch.dispose();
		disc.dispose();
	}

	/** A clouds layer's effects: through {@link #CLOUDS}, or {@link #PAINTED} for the painted clouds relit. */
	static final class CloudEffects extends LabKit.Effects
	{
		private static final ShaderProgram[] PROGRAMS = new ShaderProgram[3];

		/** 0 PUFFS, 1 WISPS, 2 the painted clouds relit. */
		private final int shader;
		private final float[] lit, shade;
		float sunX, sunY, steps, coverage, cellHeight, cells = 1, softness, base, flat, rim, opacity = 1, churn;
		private ShaderProgram previous;

		CloudEffects(int shader, float[] lit, float[] shade)
		{
			this.shader = shader;
			this.lit = lit;
			this.shade = shade;
		}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{return begin(batch, layer, phase, 0, Color.WHITE);}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase, float fogAmount, Color fogColor)
		{
			ShaderProgram program = program(shader);
			previous = batch.getShader();
			batch.setShader(program);
			if (!batch.isDrawing())
				program.bind();
			LabKit.setEffect(program, numbers(layer, phase), fogAmount, fogColor.r, fogColor.g, fogColor.b);
			program.setUniformf(program.fetchUniformLocation("u_sun", false), sunX, sunY, WORLD_WIDTH / WORLD_HEIGHT);
			program.setUniformf(program.fetchUniformLocation("u_lit", false), lit[0], lit[1], lit[2]);
			program.setUniformf(program.fetchUniformLocation("u_shade", false), shade[0], shade[1], shade[2]);
			program.setUniformf(program.fetchUniformLocation("u_tone", false), steps, rim, opacity);
			program.setUniformf(program.fetchUniformLocation("u_cloud", false), coverage, softness, cells, cellHeight);
			program.setUniformf(program.fetchUniformLocation("u_shape", false), base, flat, (float) (layer.getEffectTime() * churn % 100.0));
			return true;
		}

		@Override
		public void end(Batch batch, ParallaxLayer layer)
		{
			batch.setShader(previous);
			previous = null;
		}

		static ShaderProgram program(int shader)
		{
			if (PROGRAMS[shader] == null)
				PROGRAMS[shader] = compiled(shader == 0 ? PUFFS_FRAGMENT : shader == 1 ? WISPS_FRAGMENT : PAINTED, shader == 0 ? "PUFFS" : shader == 1 ? "WISPS" : "painted clouds");
			return PROGRAMS[shader];
		}

		private static ShaderProgram compiled(String fragment, String name)
		{
			ShaderProgram program = new ShaderProgram(GdxLayerEffects.vertex(), fragment);
			if (!program.isCompiled())
				throw new IllegalStateException("the lab's " + name + " does not compile: " + program.getLog());
			return program;
		}
	}

	/** What both shaders open with, as GdxLayerEffects writes its own effects' head, and the light they share. */
	static final String HEAD = "#ifdef GL_ES\n"
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
			+ "uniform vec3 u_sun;\n"
			+ "uniform vec3 u_lit;\n"
			+ "uniform vec3 u_shade;\n"
			+ "uniform vec3 u_tone;\n"
			+ "const float TAU = 6.2831853;\n"
			+ "vec4 hazed(vec4 color)\n"
			+ "{\n"
			+ "	return vec4(mix(color.rgb, u_fog, u_haze), color.a);\n"
			+ "}\n"
			// Where the pixel is in its tile, world units from its bottom-left.
			+ "vec2 local()\n"
			+ "{\n"
			+ "	return vec2((v_texCoords.x - u_region.x) / (u_region.z - u_region.x) * u_size.x,\n"
			+ "		(u_region.w - v_texCoords.y) / (u_region.w - u_region.y) * u_size.y);\n"
			+ "}\n"
			// Toward the sun or moon from this pixel, in the view, y up; and how near it is, 1 on it to 0 half a view away.
			+ "vec2 sunward()\n"
			+ "{\n"
			+ "	vec2 d = (u_sun.xy - v_view) * vec2(u_sun.z, 1.0);\n"
			+ "	return d / max(length(d), 0.0001);\n"
			+ "}\n"
			+ "float sunNear()\n"
			+ "{\n"
			+ "	return 1.0 - smoothstep(0.0, 0.55, length((u_sun.xy - v_view) * vec2(u_sun.z, 1.0)));\n"
			+ "}\n"
			// The two colours by how lit: smooth, or u_tone.x flat bands.
			+ "vec3 toned(float lit)\n"
			+ "{\n"
			+ "	lit = clamp(lit, 0.0, 1.0);\n"
			+ "	if (u_tone.x > 0.5)\n"
			+ "		lit = floor(lit * u_tone.x + 0.5) / u_tone.x;\n"
			+ "	return mix(u_shade, u_lit, lit);\n"
			+ "}\n";

	/**
	 * What PUFFS and WISPS pick their shapes with: whole numbers in, a 289th out, by a permutation polynomial exact in a
	 * float's 24 bits, so every GPU draws the same sky (a sine hash does not: tools/r556-clouds/model.py drew other
	 * clouds in float64 than in float32).
	 */
	static final String HASH = "uniform vec4 u_cloud;\n"
			+ "uniform vec3 u_shape;\n"
			+ "float permute(float x)\n"
			+ "{\n"
			+ "	return mod((34.0 * x + 1.0) * x, 289.0);\n"
			+ "}\n"
			+ "float hash(float x, float y, float k)\n"
			+ "{\n"
			+ "	return permute(permute(permute(mod(x, 289.0)) + mod(y, 289.0)) + k) / 289.0;\n"
			+ "}\n";

	/**
	 * PUFFS (r556): cumulus made in the shader, the layer's image never read. A lattice of {@code u_cloud.z} whole cells
	 * across the layer (so it tiles), {@code u_cloud.w} world units tall, two rows of them over the base
	 * ({@code u_shape.x} up the layer): each cell holds one round puff, its place, size and breath hashed; which
	 * columns hold a cloud is a slow wave of hashes against the coverage, and the upper row only stands on a full
	 * lower one. The cloud is the smooth union of the nine puffs round the pixel (a log of summed exponentials), cut
	 * flat at its foot, its outline pushed in and out by two crossing waves (the small bumps). Lit by how the union
	 * falls toward the sun (a second read), more toward the top; a silver lining on thin edges near the sun. Every
	 * motion is a sine on the churn's clock ({@code u_shape.z}, wrapped at 100: whole turns).
	 */
	static final String PUFFS_FRAGMENT = HEAD + HASH
			+ "const float UNION = 9.0;\n"
			+ "float body(vec2 p, float stretch, float t)\n"
			+ "{\n"
			+ "	vec2 i = floor(p);\n"
			+ "	float sum = 0.0;\n"
			+ "	for (int oy = -1; oy <= 1; oy++)\n"
			+ "		for (int ox = -1; ox <= 1; ox++)\n"
			+ "		{\n"
			+ "			vec2 c = i + vec2(float(ox), float(oy));\n"
			+ "			float hx = mod(c.x, u_cloud.z);\n"
			+ "			float a = hash(hx, c.y, 1.0);\n"
			+ "			float b = hash(hx, c.y, 2.0);\n"
			+ "			float e = hash(hx, c.y, 3.0);\n"
			+ "			float d = hash(hx, c.y, 4.0);\n"
			+ "			float g = 0.5 * hash(floor(hx / 4.0), 0.0, 5.0) + 0.3 * hash(floor(mod(hx + 2.0, u_cloud.z) / 4.0), 0.0, 7.0)\n"
			+ "				+ 0.2 * hash(hx, 0.0, 6.0);\n"
			+ "			float keep = smoothstep(0.9 - u_cloud.x, 1.1 - u_cloud.x, g);\n"
			+ "			float low = 1.0 - step(0.5, abs(c.y));\n"
			+ "			float high = 1.0 - step(0.5, abs(c.y - 1.0));\n"
			+ "			float r = (0.62 + 0.3 * e) * keep * (low + high * 0.8 * smoothstep(0.45, 0.8, keep * (0.5 + 0.5 * e)))\n"
			+ "				* (1.0 + 0.1 * sin(TAU * (0.07 * t + d)));\n"
			+ "			vec2 centre = c + vec2(0.3 + 0.4 * a + 0.08 * sin(TAU * (0.05 * t + b)), mix(0.05 + 0.3 * b, 0.25 + 0.2 * b, low));\n"
			+ "			vec2 q = (p - centre) * vec2(stretch / 1.25, 1.0);\n"
			+ "			float rr = max(r, 0.001);\n"
			+ "			sum += step(0.02, r) * exp(clamp(UNION * rr * (1.0 - dot(q, q) / (rr * rr)), -40.0, 40.0));\n"
			+ "		}\n"
			+ "	return log(max(sum, 1.0e-12)) / UNION;\n"
			+ "}\n"
			+ "void main()\n"
			+ "{\n"
			+ "	vec2 l = local();\n"
			+ "	vec2 cell = vec2(u_size.x / u_cloud.z, u_cloud.w);\n"
			+ "	float stretch = cell.x / cell.y;\n"
			+ "	vec2 p = vec2(l.x, l.y - u_shape.x * u_size.y) / cell;\n"
			+ "	float t = u_shape.z;\n"
			+ "	float wide = p.x * stretch / 1.25;\n"
			+ "	float bumps = 0.035 * (sin(TAU * (2.3 * wide + 0.9 * p.y + 0.11 * t)) * sin(TAU * (2.1 * p.y - 0.7 * p.x + 0.3 - 0.08 * t))\n"
			+ "		+ sin(TAU * (3.7 * wide - 1.3 * p.y - 0.13 * t)) * sin(TAU * (3.1 * p.y + 1.9 * p.x + 0.09 * t)));\n"
			+ "	float whole = body(p, stretch, t);\n"
			+ "	float d = whole + bumps;\n"
			+ "	float alpha = smoothstep(0.0, 2.0 * max(u_cloud.y, 0.004), d) * smoothstep(0.0, 0.1, p.y - 0.2);\n"
			+ "	vec2 s = sunward();\n"
			+ "	float toward = body(p + 0.12 * s / vec2(stretch, 1.0), stretch, t);\n"
			+ "	float lit = 0.5 + 0.26 * (whole - toward) / 0.12 + 0.35 * ((p.y - 0.2) / 1.6 - 0.4) + 0.5 * bumps;\n"
			+ "	vec3 color = toned(lit);\n"
			+ "	float thin = 1.0 - smoothstep(0.0, 0.12, d);\n"
			+ "	color = mix(color, min(vec3(1.0), 1.15 * u_lit), u_tone.y * thin * sunNear());\n"
			+ "	gl_FragColor = hazed(v_color * vec4(color, alpha * u_tone.z));\n"
			+ "}\n";

	/**
	 * WISPS (r556): streaks made in the shader, the layer's image never read. Value noise over a lattice of
	 * {@code u_cloud.z} whole cells across the layer (so it tiles), {@code u_cloud.w} world units tall, four octaves,
	 * each finer one sliding and rising on the churn's clock ({@code u_shape.z}) so the shapes change as they drift.
	 * A cloud is where the noise passes a threshold the coverage sets, less what its height costs: {@code u_shape.y}
	 * under the base, slowly above, and all of it toward the layer's foot and top. Lit by how the noise falls toward
	 * the sun (a second, three-octave read); a silver lining on thin edges near the sun.
	 */
	static final String WISPS_FRAGMENT = HEAD + HASH
			+ "float noise(vec2 p, float period)\n"
			+ "{\n"
			+ "	vec2 i = floor(p);\n"
			+ "	vec2 f = fract(p);\n"
			+ "	f = f * f * (3.0 - 2.0 * f);\n"
			+ "	float x0 = mod(i.x, period);\n"
			+ "	float x1 = mod(i.x + 1.0, period);\n"
			+ "	return mix(mix(hash(x0, i.y, 0.0), hash(x1, i.y, 0.0), f.x),\n"
			+ "		mix(hash(x0, i.y + 1.0, 0.0), hash(x1, i.y + 1.0, 0.0), f.x), f.y);\n"
			+ "}\n"
			+ "float field(vec2 p, float t)\n"
			+ "{\n"
			+ "	float n = 0.5 * noise(p, u_cloud.z);\n"
			+ "	n += 0.25 * noise(2.0 * p + t * vec2(1.0, -0.5), 2.0 * u_cloud.z);\n"
			+ "	n += 0.125 * noise(4.0 * p + t * vec2(-1.6, -1.1), 4.0 * u_cloud.z);\n"
			+ "	return n;\n"
			+ "}\n"
			+ "void main()\n"
			+ "{\n"
			+ "	vec2 l = local();\n"
			+ "	float up = l.y / u_size.y;\n"
			+ "	vec2 cell = vec2(u_size.x / u_cloud.z, u_cloud.w);\n"
			+ "	vec2 p = vec2(l.x, l.y) / cell;\n"
			+ "	float t = u_shape.z;\n"
			+ "	float n = field(p, t);\n"
			+ "	float fine = 0.0625 * noise(8.0 * p + t * vec2(2.3, -2.0), 8.0 * u_cloud.z);\n"
			+ "	float cost = up < u_shape.x ? (u_shape.x - up) * u_shape.y : (up - u_shape.x) / (1.0 - u_shape.x) * 0.16;\n"
			+ "	cost += 0.5 * (1.0 - smoothstep(0.0, 0.12, up) * smoothstep(0.0, 0.1, 1.0 - up));\n"
			+ "	float d = (n + fine) / 0.9375 - mix(0.72, 0.28, u_cloud.x) - cost;\n"
			+ "	float alpha = smoothstep(0.0, max(u_cloud.y, 0.004), d);\n"
			+ "	vec2 s = sunward();\n"
			+ "	float toward = field(p + 0.4 * s * cell.y / cell, t);\n"
			+ "	float lit = 0.5 + 4.5 * (n - toward) + 0.45 * (up - u_shape.x) - 0.6 * d;\n"
			+ "	vec3 color = toned(lit);\n"
			+ "	float thin = 1.0 - smoothstep(0.0, 3.0 * u_cloud.y + 0.06, d);\n"
			+ "	color = mix(color, min(vec3(1.0), 1.15 * u_lit), u_tone.y * thin * sunNear());\n"
			+ "	gl_FragColor = hazed(v_color * vec4(color, alpha * u_tone.z));\n"
			+ "}\n";

	/**
	 * The painted clouds, churned and relit: r375's BILLOW (the pixels pushed by a slow warp of wavelength L, written
	 * in world units over {@code along()}, as WAVE and FOG are, under FOG's periods, where every term is a whole number
	 * of turns), then the painting's colours replaced by the lit and the shaded: more lit up the layer and where the
	 * painting's opacity falls toward the sun. {@code at(l)}: the image at local position l, in world units from the
	 * tile's top-left, wrapping across (the layer tiles), held at its top and bottom rows.
	 */
	static final String PAINTED = HEAD
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
			+ "	vec2 p = a / L;\n"
			+ "	float dx = sin(TAU * (p.x + 0.7 * sin(TAU * p.y) - t)) + 0.5 * sin(TAU * (2.0 * p.x - 1.3 * p.y + t + 0.3));\n"
			+ "	float dy = sin(TAU * (2.0 * p.x + p.y - t + 0.6)) + 0.5 * sin(TAU * (p.x - 2.0 * p.y - 2.0 * t));\n"
			+ "	l += u_effect.x / 1.5 * vec2(dx, -dy);\n"
			+ "	vec4 painted = at(l);\n"
			+ "	vec2 s = sunward();\n"
			+ "	float toward = at(l + 0.35 * s).a;\n"
			+ "	float up = l.y / u_size.y;\n"
			+ "	vec3 color = toned(0.25 + 0.6 * up + 1.5 * (painted.a - toward));\n"
			+ "	color = mix(color, min(vec3(1.0), 1.15 * u_lit), u_tone.y * (1.0 - painted.a) * sunNear());\n"
			+ "	gl_FragColor = hazed(v_color * vec4(color, painted.a * u_tone.z));\n"
			+ "}\n";
}
