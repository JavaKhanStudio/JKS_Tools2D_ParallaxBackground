package jks.tools2d.parallax.browsertest;

import java.util.List;

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
 * work for both day and night"): cloud shaders as lab GLSL (GLSL ES 1.0), drawn by the real reader in WebGL, in the
 * three settings Ficelle needs first: a Mediterranean day (the animation lab's beach), a night in a cold forest (the
 * hills lab's forest, tinted) and a medieval fantasy evening (round1's calm, which Ficelle's saga wears). Each panel is
 * a page's gradients, the hour's sun or moon, the clouds, then the page's own layers.
 * <ul>
 * <li>CUMULUS ({@link #CUMULUS_FRAGMENT}, round 3: Simon refused rounds 1 and 2 whole, "It's so bad [...] Try again"):
 * cumulus made in the shader, nothing painted, worn as a whole sky of three layers ({@link #SKY_LAYERS}: a far bank at
 * the horizon, a middle, the near clouds), in a panel as tall as it is wide so the sky is what shows. One shader, three
 * looks by its numbers ({@link #LOOKS}): painted, cut in flat tones, soft.</li>
 * <li>the earlier rounds, kept in the last view: WISPS ({@link #WISPS_FRAGMENT}, streaks as value noise), a page's
 * painted clouds under r375's BILLOW and relit ({@link #PAINTED}: calm's bank and OneNight's cumulus), and the
 * painting still under the page's tint, which is what a page does today.</li>
 * </ul>
 * The sliders scale each layer's own numbers, so one move shows in every setting. Opened with clouds-lab.html:
 * tools/clouds-lab.sh.
 * <p>
 * {@code &clip=1}: nothing moves by itself, {@code window.cloudsLabAct(seconds)} steps every heart at 1/60 s.
 */
public class CloudsLab extends ApplicationAdapter implements LabKit.Clip
{
	static final String CLOUDS_PAGE = "fog-lab/s02.jplax", CLOUDS_REGION = "Clouds";
	/** OneNight's page and its painted cumulus (the first of its two "clouds" regions). */
	static final String ONENIGHT_PAGE = "fog-lab/s06.jplax", ONENIGHT_REGION = "clouds";
	static final float WORLD_WIDTH = FogLab.WORLD_WIDTH, WORLD_HEIGHT = FogLab.WORLD_HEIGHT;
	/**
	 * Three panels a row. A sky's view is one row of them, each as tall as two: a view nearly as tall as it is wide, as
	 * a phone held upright sees a page, the land at its foot. The earlier rounds' are two rows, each panel nearly the
	 * page's whole 16:9 frame, cut at its foot.
	 */
	static final int COLUMNS = 3, PANEL_WIDTH = FogLab.FRAME_WIDTH, PANEL_HEIGHT = 376, SKY_HEIGHT = 2 * PANEL_HEIGHT + 4;

	static final String[] SETTINGS = { "Mediterranean day", "cold forest night", "medieval fantasy" };
	static final String[] PAGES = { "anim-lab/beach.jplax", "hills-lab/forest.jplax", CLOUDS_PAGE };
	/** The hills lab's atlas holds the forest's hills layer once per candidate: the page's own strip. */
	static final String FOREST_HILLS = "l2", FOREST_HILLS_STRIP = "h_today_7020";
	/** What a panel wears. The first {@link #OLD_KINDS} are one clouds layer each; the last three one CUMULUS sky, by its look. */
	static final int WISPS = 0, RELIT = 1, RELIT_CUMULUS = 2, STILL = 3, CUMULUS = 4, CUT = 5, SOFT = 6, OLD_KINDS = 4;
	static final String[] KINDS = { "WISPS (round 1)", "painted bank, relit (round 2)", "painted cumulus, relit (round 2)", "the painting, still (today)",
			"CUMULUS", "CUMULUS, cut in flat tones", "CUMULUS, soft" };
	/** The shader an old kind is drawn through ({@link CloudEffects}): WISPS, the painted relit; none for the still painting. */
	static final int[] SHADER = { 0, 1, 1, -1 };
	/** BILLOW's wavelength (world units) and speed (world units a second), as r451 left them. */
	static final float BILLOW_WAVELENGTH = 6, BILLOW_SPEED = 0.4f;
	/** Each setting's sky, top and horizon; its layers' tint; its clouds' lit and shaded colours; its sun's or moon's. */
	static final float[][] SKY_TOP = { { 0.16f, 0.45f, 0.86f }, { 0.015f, 0.03f, 0.085f }, { 0.27f, 0.33f, 0.64f } };
	static final float[][] SKY_LOW = { { 0.74f, 0.88f, 0.97f }, { 0.09f, 0.15f, 0.27f }, { 0.99f, 0.74f, 0.50f } };
	static final float[][] TINT = { { 1, 1, 1 }, { 0.26f, 0.34f, 0.56f }, { 1, 0.84f, 0.70f } };
	static final float[][] LIT = { { 1, 1, 1 }, { 0.50f, 0.60f, 0.80f }, { 1, 0.90f, 0.68f } };
	static final float[][] SHADE = { { 0.50f, 0.64f, 0.86f }, { 0.045f, 0.07f, 0.15f }, { 0.56f, 0.38f, 0.56f } };
	static final float[][] DISC = { { 1, 0.98f, 0.88f }, { 0.92f, 0.94f, 1 }, { 1, 0.86f, 0.55f } };
	/**
	 * Where each setting's sun or moon stands: degrees round an arc from its right end, and its radius, world units. The
	 * arc in a page's 16:9 frame, and in a sky's tall view: its foot and its rise, world units. The fantasy's sun is low,
	 * under the near clouds: their bellies are the lit side.
	 */
	static final float[] SUN_ANGLE = { 118, 60, 20 }, SUN_RADIUS = { 1.5f, 1.1f, 2 };
	static final float SUN_FOOT = 12, SUN_RISE = 8.5f, SKY_SUN_FOOT = 10, SKY_SUN_RISE = 26;
	/**
	 * A CUMULUS sky's layers, back to front: the far bank on the horizon, the middle, the near clouds. Each: its size (x
	 * the world's width), its foot (% of the world's height), the lobes across it (a whole number: it tiles), how many
	 * lobes long a cloud's slot is, how tall a cloud may stand (lobes), its base (how far up the layer), its coverage
	 * (0 to 1), its haze toward the horizon's colour, its pace (x the drift and the scroll), its seed.
	 */
	static final float[][] SKY_LAYERS = { { 1.5f, 35.5f, 40, 5, 1.6f, 0.12f, 0.75f, 0.55f, 0.35f, 1 },
			{ 1.8f, 55.5f, 22, 4, 2.0f, 0.10f, 0.60f, 0.25f, 0.6f, 5 }, { 3.5f, 89, 22, 4, 2.4f, 0.08f, 0.60f, 0, 1, 11 } };
	/**
	 * CUMULUS's looks, by kind from {@link #CUMULUS}: how flat its shade is cut (0 smooth, 1 three flat tones), its
	 * edge's softness (lobes), how frayed its edge is, its silver lining.
	 */
	static final float[][] LOOKS = { { 0.8f, 0.03f, 0, 0.6f }, { 1, 0.012f, 0, 0.5f }, { 0.5f, 0.08f, 0.3f, 0.8f } };
	/** CUMULUS's churn: turns a second of its large lobes round their places (the middle ones twice, the small thrice). */
	static final float CHURN = 0.03f;
	/**
	 * WISPS' numbers, per setting: coverage (0 to 1), a cell's height (world units), how many times longer than tall,
	 * the edge's softness, the base (how far up the layer), how flat it is, the silver lining, the opacity, the churn
	 * (cells a second).
	 */
	static final float[][] WISPS_STYLE = { { 0.52f, 2.2f, 6, 0.30f, 0.45f, 0.6f, 0.2f, 0.75f, 0.04f }, { 0.60f, 2, 7, 0.35f, 0.45f, 0.5f, 0.9f, 0.7f, 0.03f },
			{ 0.56f, 2.4f, 6, 0.30f, 0.45f, 0.6f, 0.6f, 0.8f, 0.04f } };
	/** An old kind's clouds layer, made or painted: its size (x the world's width) and its foot (% of the world's height). */
	static final float MADE_SIZE = 1.6f, MADE_FOOT = 50, PAINTED_SIZE = 1.1f, PAINTED_FOOT = 60, ONENIGHT_SIZE = 0.9f, ONENIGHT_FOOT = 13;
	/**
	 * How the painted cumulus is read: OneNight's painter put the shapes in the opacity (5% at a cloud's thin foot to 55%
	 * at its lit crown, the colour nearly one white), so the relit copy takes that opacity as its shade, lowest to
	 * highest, by this weight, and is made this solid past a faint edge. Calm's bank is one flat fill: none of it.
	 */
	static final float[] ONENIGHT_PAINT = { 0.05f, 0.55f, 0.75f, 1 }, BANK_PAINT = { 0, 1, 0, 0 };
	/** The views the choice picks: CUMULUS in each setting, a setting under CUMULUS's three looks, the earlier rounds. Each panel: setting, kind. */
	static final String[] VIEWS = { "CUMULUS", SETTINGS[0], SETTINGS[1], SETTINGS[2], "earlier rounds" };
	static final int[][][] VIEW_PANELS = { { { 0, CUMULUS }, { 1, CUMULUS }, { 2, CUMULUS } }, { { 0, CUMULUS }, { 0, CUT }, { 0, SOFT } },
			{ { 1, CUMULUS }, { 1, CUT }, { 1, SOFT } }, { { 2, CUMULUS }, { 2, CUT }, { 2, SOFT } },
			{ { 0, WISPS }, { 1, WISPS }, { 2, WISPS }, { 2, RELIT }, { 2, RELIT_CUMULUS }, { 2, STILL } } };
	/** Where a frame is held, as a fraction of its height from its bottom, at the panel's middle: the sky. A page's frame, a sky's view. */
	static final float FOCUS = 0.75f, SKY_FOCUS = 0.6f;

	private final Parallax_Heart[] lands = new Parallax_Heart[SETTINGS.length];
	private final Parallax_Heart[][] skies = new Parallax_Heart[SETTINGS.length][OLD_KINDS];
	private final ParallaxLayer[][] clouds = new ParallaxLayer[SETTINGS.length][OLD_KINDS];
	private final CloudEffects[][] effects = new CloudEffects[SETTINGS.length][OLD_KINDS];
	/** Each setting's CUMULUS sky: one page of {@link #SKY_LAYERS}, drawn through one {@link SkyEffects}. */
	private final Parallax_Heart[] cumulus = new Parallax_Heart[SETTINGS.length];
	private final SkyEffects[] cumulusEffects = new SkyEffects[SETTINGS.length];
	private final Parallax_Heart[] hearts = new Parallax_Heart[SETTINGS.length * (2 + OLD_KINDS)];
	/** Each setting's sun or moon, degrees round its arc; the clouds layer's own drift (its at rest speed), which the drift slider scales. */
	private final float[] sunAngle = new float[SETTINGS.length];
	private float ownDrift;
	private SelectElement view;
	private InputElement zoom, scroll, drift, coverage, size, tall, softness, cut, churn, light, amplitude;
	private Element readout;
	private final Element[] labels = new Element[2 * COLUMNS];
	private boolean clip;
	private SpriteBatch batch;
	private Texture disc;

	@Override
	public void create()
	{
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		// CUMULUS and WISPS never read the layer's image, so the GPU drops u_texture, which SpriteBatch sets by name: a
		// shipped one must keep it alive or be set another way (r290).
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
			for (int kind = 0; kind < OLD_KINDS; kind++)
			{
				boolean oneNight = kind == RELIT_CUMULUS, made = kind == WISPS;
				WholePage_Model sky = cloudsPage(oneNight ? ONENIGHT_PAGE : CLOUDS_PAGE, oneNight ? ONENIGHT_REGION : CLOUDS_REGION, 1);
				Parallax_Model model = sky.pageModel.pageList.get(0);
				model.sizeRatio = made ? MADE_SIZE : oneNight ? ONENIGHT_SIZE : PAINTED_SIZE;
				model.decal_Y_Ratio = made ? MADE_FOOT : oneNight ? ONENIGHT_FOOT : PAINTED_FOOT;
				if (kind != STILL)
					shaderLayer(model);
				skies[s][kind] = hearts[n++] = heart(sky);
				clouds[s][kind] = skies[s][kind].parallaxReader.layers.get(0);
				// Every kind drifts as calm's clouds do: OneNight's own stand still at rest.
				if (!oneNight)
					ownDrift = clouds[s][kind].getSpeedAtRest();
				if (kind == STILL)
					skies[s][kind].parallaxReader.addColorTransfert(color(TINT[s]), 0);
				else
					skies[s][kind].parallaxReader.setLayerEffects(
							effects[s][kind] = new CloudEffects(SHADER[kind], LIT[s], SHADE[s], oneNight ? ONENIGHT_PAINT : BANK_PAINT));
			}
			// The CUMULUS sky: calm's clouds layer three times over, for its quad alone (the shader never reads its image).
			WholePage_Model sky = cloudsPage(CLOUDS_PAGE, CLOUDS_REGION, SKY_LAYERS.length);
			for (int i = 0; i < SKY_LAYERS.length; i++)
			{
				Parallax_Model model = sky.pageModel.pageList.get(i);
				model.sizeRatio = SKY_LAYERS[i][0];
				model.decal_Y_Ratio = SKY_LAYERS[i][1];
				model.parallaxScalingSpeedX *= SKY_LAYERS[i][8];
				shaderLayer(model);
			}
			cumulus[s] = hearts[n++] = heart(sky);
			cumulus[s].parallaxReader.setLayerEffects(cumulusEffects[s] = new SkyEffects(cumulus[s].parallaxReader.layers, LIT[s], SHADE[s], SKY_LOW[s]));
		}
		buildControls();
		if (clip)
			LabKit.exportClip("cloudsLab", this);
	}

	/** A page of {@code page}'s layer {@code region} alone, {@code times} over. */
	private static WholePage_Model cloudsPage(String page, String region, int times)
	{
		WholePage_Model sky = null;
		for (int i = 0; i < times; i++)
		{
			WholePage_Model loaded = Utils_Page_Json.loadPage(page);
			Parallax_Model model = null;
			for (Parallax_Model each : loaded.pageModel.pageList)
				if (model == null && region.equals(each.regionName))
					model = each;
			if (model == null)
				throw new IllegalStateException(page + " has no layer " + region);
			if (sky == null)
			{
				sky = loaded;
				sky.pageModel.pageList.clear();
			}
			sky.pageModel.pageList.add(model);
		}
		return sky;
	}

	/** What a page would store: a SHADER layer, under FOG's periods until a cloud effect ships (the lab draws its own). */
	private static void shaderLayer(Parallax_Model model)
	{
		model.kind = Enum_LayerKind.SHADER;
		model.shaderEffect = Enum_ShaderEffect.FOG;
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
		view = LabControls.stageChoice(document, "clouds-labels", "view", "view (CUMULUS in the three settings, one setting under CUMULUS's three looks, or the earlier rounds: WISPS, the painted clouds relit, the painting today)", VIEWS, 0);
		Element controls = document.getElementById("clouds-controls");
		zoom = LabControls.slider(document, controls, "zoom", "zoom (x the panel's view)", 1, 4, 0.25f, 1);
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll (world units/s)", 0, 120, 5, 20);
		drift = LabControls.slider(document, controls, "cloudDrift", "clouds' drift (x calm's own at rest speed: 0 to judge the churn alone)", 0, 3, 0.05f, 1);
		// With the first three, not in a section of its own: a heading costs a row, and the window has none to spare (r232).
		amplitude = LabControls.slider(document, controls, "billowAmplitude", "painted churn (the earlier rounds' painted clouds alone: BILLOW's amplitude, world units, how far their pixels are pushed; its wavelength "
				+ LabControls.text(BILLOW_WAVELENGTH) + " and speed " + LabControls.text(BILLOW_SPEED) + " are r451's)", 0, 1.5f, 0.02f, 0.3f);
		Element made = LabControls.section(document, controls, "CUMULUS (made in the shader, no painting: each slider scales every layer's own number)");
		coverage = LabControls.slider(document, made, "cloudCoverage", "coverage (x the layer's: how much of the sky is cloud)", 0, 1.6f, 0.05f, 1);
		size = LabControls.slider(document, made, "cloudSize", "size (x the layer's: how large a lobe is)", 0.5f, 2, 0.05f, 1);
		tall = LabControls.slider(document, made, "cloudHeight", "height (x the layer's: how tall a cloud may stand)", 0.3f, 1.2f, 0.05f, 1);
		softness = LabControls.slider(document, made, "cloudSoftness", "softness (x the look's: 0 a cut-out edge)", 0, 4, 0.1f, 1);
		cut = LabControls.slider(document, made, "shadeCut", "shade cut (x the look's: 0 a smooth shade, up to three flat tones)", 0, 1.25f, 0.05f, 1);
		churn = LabControls.slider(document, made, "cloudChurn", "churn (x " + LabControls.text(CHURN) + " turns a second: how fast the lobes move)", 0, 6, 0.25f, 1);
		light = LabControls.slider(document, made, "lightAngle", "light (degrees added to where the setting's sun or moon stands; the clouds' lit side follows, the earlier rounds' too)", -60, 60, 2, 0);
		readout = LabControls.readout(document, controls, "clouds-readout");
	}

	/** Puts the sliders into every heart. */
	private void applyControls()
	{
		int v = LabControls.read(view);
		LabControls.read(zoom);
		float sc = LabControls.read(scroll), dr = LabControls.read(drift), li = LabControls.read(light);
		float co = LabControls.read(coverage), si = LabControls.read(size), he = LabControls.read(tall), so = LabControls.read(softness);
		float cu = LabControls.read(cut), ch = LabControls.read(churn);
		float ba = LabControls.read(amplitude);
		for (int s = 0; s < SETTINGS.length; s++)
		{
			sunAngle[s] = SUN_ANGLE[s] + li;
			lands[s].screenSpeedConstantX = sc;
			cumulus[s].screenSpeedConstantX = sc;
			SkyEffects sky = cumulusEffects[s];
			sky.softness = so;
			sky.cut = cu;
			sky.churn = CHURN * ch;
			for (int i = 0; i < SKY_LAYERS.length; i++)
			{
				float[] layer = SKY_LAYERS[i];
				cumulus[s].parallaxReader.layers.get(i).setSpeedAtRest(ownDrift * dr * layer[8]);
				// Whole lobes across the layer: the clouds run on across its tiles.
				sky.lobes[i] = Math.max(4, Math.round(layer[2] / si));
				// A cloud keeps its slot's and its height's world size when its lobes change theirs.
				sky.slot[i] = layer[3] * sky.lobes[i] / layer[2];
				sky.tall[i] = layer[4] * he * sky.lobes[i] / layer[2];
				sky.coverage[i] = Math.min(1, layer[6] * co);
			}
			for (int kind = 0; kind < OLD_KINDS; kind++)
			{
				ParallaxLayer layer = clouds[s][kind];
				layer.setSpeedAtRest(ownDrift * dr);
				skies[s][kind].screenSpeedConstantX = sc;
				if (kind == STILL)
					continue;
				layer.setShaderAmplitude(ba);
				layer.setShaderWavelength(BILLOW_WAVELENGTH);
				layer.setShaderSpeed(BILLOW_SPEED);
				if (kind != WISPS)
					continue;
				CloudEffects e = effects[s][kind];
				float[] style = WISPS_STYLE[s];
				float width = layer.getWidth() * layer.getPackedWidthRatio();
				e.coverage = style[0];
				e.cellHeight = style[1];
				// Whole cells across the layer: the clouds run on across its tiles.
				e.cells = Math.max(1, Math.round(width / (style[1] * style[2])));
				e.softness = style[3];
				e.base = style[4];
				e.flat = style[5];
				e.rim = style[6];
				e.opacity = style[7];
				e.churn = style[8];
			}
		}
		int[][] shown = VIEW_PANELS[v];
		for (int k = 0; k < labels.length; k++)
		{
			boolean titled = k < shown.length;
			// The first view's name is its kind, each setting's its setting: a title says the rest, short enough for a narrow panel.
			labels[k].setInnerText(!titled ? "" : (char) ('A' + k) + ": " + (v == 0 ? SETTINGS[shown[k][0]] : v > SETTINGS.length ? SETTINGS[shown[k][0]] + ", " + KINDS[shown[k][1]] : KINDS[shown[k][1]]));
			// A chip over no panel is hidden, not a dark dash; it keeps its cell in the grid.
			labels[k].getParentElement().getStyle().setProperty("visibility", titled ? "visible" : "hidden");
		}
		int s = shown[0][0], kind = shown[0][1];
		if (kind < CUMULUS)
		{
			readout.setInnerText("A (" + SETTINGS[s] + ", " + KINDS[kind] + "): the earlier rounds' clouds, at their own numbers; of the sliders, only the "
					+ "painted churn (" + round(ba) + " world units), the drift, the scroll and the light move them. The world is " + round(WORLD_WIDTH) + " units wide.");
			return;
		}
		SkyEffects a = cumulusEffects[s];
		float[] look = LOOKS[kind - CUMULUS];
		int near = SKY_LAYERS.length - 1;
		float width = cumulus[s].parallaxReader.layers.get(near).getWidth();
		readout.setInnerText("A (" + SETTINGS[s] + ", " + KINDS[kind] + "): three layers of one shader; the near clouds' lobes " + round(width / a.lobes[near])
				+ " world units (" + Math.round(a.lobes[near]) + " across the layer's " + round(width) + "), a cloud up to " + round(a.tall[near]) + " lobes tall, coverage "
				+ round(a.coverage[near]) + "; shade cut " + round(Math.min(1, look[0] * cu)) + ", softness " + round(look[1] * so) + ", churn " + round(a.churn)
				+ " turns/s. The world is " + round(WORLD_WIDTH) + " units wide.");
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
			if (shown[k][1] >= CUMULUS)
				panel(shown[k][0], shown[k][1], k * (PANEL_WIDTH + 4), height, PANEL_WIDTH, SKY_HEIGHT, z);
			else
				panel(shown[k][0], shown[k][1], (k % COLUMNS) * (PANEL_WIDTH + 4), k < COLUMNS ? height : height - PANEL_HEIGHT - 4, PANEL_WIDTH, PANEL_HEIGHT, z);
		LabKit.endPanels();
	}

	/**
	 * Draws setting {@code s} under the clouds of {@code kind} into the panel {@code w} by {@code h} whose top-left is
	 * at {@code x}, {@code top}. An earlier round's: through the page's 16:9 frame. A CUMULUS sky's: through a view as
	 * tall as the panel, the page's world at its foot and that much more sky over it. Either {@code z} times the
	 * panel's width, its middle across at the panel's, its focus at the panel's middle up, kept over the whole panel.
	 * The sky's gradient, the sun or moon, the clouds, then the page's layers.
	 */
	private void panel(int s, int kind, int x, int top, int w, int h, float z)
	{
		boolean made = kind >= CUMULUS;
		float viewHeight = made ? WORLD_WIDTH * h / w : WORLD_HEIGHT;
		int frameWidth = Math.round(w * z), frameHeight = Math.round(frameWidth * viewHeight / WORLD_WIDTH);
		int bottom = Math.round(top - h / 2f - (made ? SKY_FOCUS : FOCUS) * frameHeight);
		bottom = Math.max(top - frameHeight, Math.min(top - h, bottom));
		if (!LabKit.frame(x, top - h, w, h, x - (frameWidth - w) / 2, bottom, frameWidth, frameHeight))
			return;
		Parallax_Heart land = lands[s], sky = made ? cumulus[s] : skies[s][kind];
		see(land.worldCamera, viewHeight);
		see(sky.worldCamera, viewHeight);
		double angle = Math.toRadians(sunAngle[s]);
		float sunX = WORLD_WIDTH / 2 + 16 * (float) Math.cos(angle);
		float sunY = made ? SKY_SUN_FOOT + SKY_SUN_RISE * (float) Math.sin(angle) : SUN_FOOT + SUN_RISE * (float) Math.sin(angle);
		if (made)
			cumulusEffects[s].see(LOOKS[kind - CUMULUS], sunX / WORLD_WIDTH, sunY / viewHeight, WORLD_WIDTH / viewHeight);
		else if (kind != STILL)
		{
			effects[s][kind].sunX = sunX / WORLD_WIDTH;
			effects[s][kind].sunY = sunY / viewHeight;
		}
		land.drawBackGround();
		batch.setProjectionMatrix(land.worldCamera.combined);
		batch.enableBlending();
		batch.begin();
		float r = SUN_RADIUS[s] / 0.3f;
		batch.setColor(DISC[s][0], DISC[s][1], DISC[s][2], 1);
		batch.draw(disc, sunX - r, sunY - r, 2 * r, 2 * r);
		batch.setColor(Color.WHITE);
		sky.parallaxReader.draw(sky.worldCamera, batch);
		land.parallaxReader.draw(land.worldCamera, batch);
		batch.end();
	}

	/** {@code camera} sees the world's width and {@code height} world units up from the world's floor. */
	private static void see(OrthographicCamera camera, float height)
	{
		camera.viewportHeight = height;
		camera.position.y = height / 2;
		camera.update();
	}

	@Override
	public void dispose()
	{
		batch.dispose();
		disc.dispose();
	}

	/** A CUMULUS sky's effects: each of its layers through {@link #CUMULUS_FRAGMENT}, at its own numbers. */
	static final class SkyEffects extends LabKit.Effects
	{
		private final List<ParallaxLayer> layers;
		private final float[] lit, shade, horizon;
		final float[] coverage = new float[SKY_LAYERS.length], lobes = new float[SKY_LAYERS.length], slot = new float[SKY_LAYERS.length],
				tall = new float[SKY_LAYERS.length];
		/** The sliders: x the look's softness, x its cut; the churn, turns a second. */
		float softness = 1, cut = 1, churn;
		private float[] look = LOOKS[0];
		private float sunX, sunY, aspect = 1;
		private ShaderProgram previous;

		SkyEffects(List<ParallaxLayer> layers, float[] lit, float[] shade, float[] horizon)
		{
			this.layers = layers;
			this.lit = lit;
			this.shade = shade;
			this.horizon = horizon;
		}

		/** What the next draw wears and sees: its look, the sun or moon in the view (0 to 1), the view's width over its height. */
		void see(float[] look, float sunX, float sunY, float aspect)
		{
			this.look = look;
			this.sunX = sunX;
			this.sunY = sunY;
			this.aspect = aspect;
		}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{return begin(batch, layer, phase, 0, Color.WHITE);}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase, float fogAmount, Color fogColor)
		{
			int i = Math.max(0, layers.indexOf(layer));
			float[] numbers = SKY_LAYERS[i];
			ShaderProgram program = CloudEffects.program(2);
			previous = batch.getShader();
			batch.setShader(program);
			if (!batch.isDrawing())
				program.bind();
			LabKit.setEffect(program, numbers(layer, phase), numbers[7], horizon[0], horizon[1], horizon[2]);
			program.setUniformf(program.fetchUniformLocation("u_sun", false), sunX, sunY, aspect);
			program.setUniformf(program.fetchUniformLocation("u_lit", false), lit[0], lit[1], lit[2]);
			program.setUniformf(program.fetchUniformLocation("u_shade", false), shade[0], shade[1], shade[2]);
			program.setUniformf(program.fetchUniformLocation("u_cloud", false), coverage[i], lobes[i], slot[i], look[1] * softness);
			program.setUniformf(program.fetchUniformLocation("u_shape", false), numbers[5], tall[i], (float) (layer.getEffectTime() * churn % 100.0), numbers[9]);
			program.setUniformf(program.fetchUniformLocation("u_tone", false), Math.min(1, look[0] * cut), look[3], 1, look[2]);
			return true;
		}

		@Override
		public void end(Batch batch, ParallaxLayer layer)
		{
			batch.setShader(previous);
			previous = null;
		}
	}

	/** An earlier round's clouds layer's effects: through {@link #WISPS_FRAGMENT}, or {@link #PAINTED} for the painted clouds relit. */
	static final class CloudEffects extends LabKit.Effects
	{
		private static final ShaderProgram[] PROGRAMS = new ShaderProgram[3];
		private static final String[] FRAGMENTS = { WISPS_FRAGMENT, PAINTED, CUMULUS_FRAGMENT };
		private static final String[] NAMES = { "WISPS", "painted clouds", "CUMULUS" };

		/** 0 WISPS, 1 the painted clouds relit. */
		private final int shader;
		private final float[] lit, shade, paint;
		float sunX, sunY, coverage, cellHeight, cells = 1, softness, base, flat, rim, opacity = 1, churn;
		private ShaderProgram previous;

		CloudEffects(int shader, float[] lit, float[] shade, float[] paint)
		{
			this.shader = shader;
			this.lit = lit;
			this.shade = shade;
			this.paint = paint;
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
			program.setUniformf(program.fetchUniformLocation("u_tone", false), 0, rim, opacity);
			program.setUniformf(program.fetchUniformLocation("u_paint", false), paint[0], paint[1], paint[2], paint[3]);
			program.setUniformf(program.fetchUniformLocation("u_wrap", false), 1 - paint[3]);
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
				PROGRAMS[shader] = compiled(FRAGMENTS[shader], NAMES[shader]);
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

	/** What every shader here opens with, as GdxLayerEffects writes its own effects' head, and the light they share. */
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
			+ "}\n";

	/** WISPS' and the painted clouds' two colours by how lit: smooth, or {@code u_tone.x} flat bands; y the silver lining, z the opacity. */
	static final String TONED = "uniform vec3 u_tone;\n"
			// The two colours by how lit: smooth, or u_tone.x flat bands.
			+ "vec3 toned(float lit)\n"
			+ "{\n"
			+ "	lit = clamp(lit, 0.0, 1.0);\n"
			+ "	if (u_tone.x > 0.5)\n"
			+ "		lit = floor(lit * u_tone.x + 0.5) / u_tone.x;\n"
			+ "	return mix(u_shade, u_lit, lit);\n"
			+ "}\n";

	/**
	 * CUMULUS (r556, round 3): cumulus made in the shader, the layer's image never read. Lengths are in lobes: the
	 * layer holds {@code u_cloud.y} of them across (a whole number, so it tiles), from the base ({@code u_shape.x} up
	 * the layer).
	 * <ul>
	 * <li>Where a cloud stands and how tall ({@code crown}): two octaves of value noise along the layer, a slot
	 * {@code u_cloud.z} lobes long, against the coverage {@code u_cloud.x}; up to {@code u_shape.y} lobes, never past
	 * the layer's top.</li>
	 * <li>Its outline ({@code cloud}): the crown, heaped with three sizes of round domes ({@code billow}: one a cell,
	 * its place, size and slow orbit hashed; the highest wins, so the outline is scallops, as cumulus is drawn), on a
	 * nearly flat foot. {@code u_tone.w} frays it with noise, for a soft cloud.</li>
	 * <li>Its light: no second read. The surface turns as a mound does (the crown up, the belly down, the flanks out),
	 * each large dome a ball on it, the middle ones a little; that against the sun or moon where it stands in the view,
	 * so a noon sun whitens the crowns over shaded bellies, and a low one lights the bellies under shaded crowns. Cut
	 * into three tones by {@code u_tone.x} (0 smooth, 1 flat), between {@code u_shade} and {@code u_lit}; a silver
	 * lining ({@code u_tone.y}) on thin edges near the sun.</li>
	 * </ul>
	 * {@code u_cloud.w} is the edge's softness, {@code u_tone.z} the opacity, {@code u_shape.w} the seed (a layer's
	 * own), {@code u_shape.z} the churn's clock in turns (wrapped at 100: every motion a whole number of turns of it).
	 * The hash is a permutation polynomial on whole numbers, exact in a float's 24 bits: every GPU draws the same sky.
	 * tools/r556-clouds/probe.py draws this very text alone, in seconds: tune it there.
	 */
	static final String CUMULUS_FRAGMENT = HEAD
			+ "uniform vec4 u_cloud;\n"
			+ "uniform vec4 u_shape;\n"
			+ "uniform vec4 u_tone;\n"
			+ "float permute(float x)\n"
			+ "{\n"
			+ "	return mod((34.0 * x + 1.0) * x, 289.0);\n"
			+ "}\n"
			+ "float hash(float x, float y, float k)\n"
			+ "{\n"
			+ "	return permute(permute(permute(mod(x, 289.0)) + mod(y, 289.0)) + k) / 289.0;\n"
			+ "}\n"
			// Round domes, one a cell, the nine round the pixel. x: the highest at the pixel, 1 at a dome's middle (its creases
			// sharp: an outline's scallops). zw: where the pixel is on its dome, from the middle to 1 at its rim, the domes melted
			// into one another by their height (what the light reads: each dome a ball). period: whole cells across.
			+ "vec4 billow(vec2 p, float period, float k, float t)\n"
			+ "{\n"
			+ "	vec2 i = floor(p);\n"
			+ "	vec2 f = p - i;\n"
			+ "	float best = -1.0;\n"
			+ "	float sum = 0.0;\n"
			+ "	vec2 on = vec2(0.0);\n"
			+ "	for (int oy = -1; oy <= 1; oy++)\n"
			+ "		for (int ox = -1; ox <= 1; ox++)\n"
			+ "		{\n"
			+ "			vec2 o = vec2(float(ox), float(oy));\n"
			+ "			float hx = mod(i.x + o.x, period);\n"
			+ "			float hy = i.y + o.y;\n"
			+ "			float a = hash(hx, hy, k);\n"
			+ "			float b = hash(hx, hy, k + 1.0);\n"
			+ "			float r = hash(hx, hy, k + 2.0);\n"
			+ "			vec2 at = o + 0.2 + 0.6 * vec2(a, b) + 0.1 * vec2(sin(TAU * (t + a)), cos(TAU * (t + b)));\n"
			+ "			vec2 q = (f - at) / (0.64 + 0.38 * r + 0.07 * sin(TAU * (t + r)));\n"
			+ "			float v = 1.0 - dot(q, q);\n"
			+ "			best = max(best, v);\n"
			+ "			float w = exp(8.0 * max(v, -2.0));\n"
			+ "			sum += w;\n"
			+ "			on += w * q;\n"
			+ "		}\n"
			+ "	return vec4(best, 0.0, on / sum);\n"
			+ "}\n"
			// Where the clouds stand: 0 to 1 along the layer, whole slots across.
			+ "float mass(float x, float period, float k)\n"
			+ "{\n"
			+ "	float i = floor(x);\n"
			+ "	float f = x - i;\n"
			+ "	f = f * f * (3.0 - 2.0 * f);\n"
			+ "	return mix(hash(mod(i, period), 3.0, k), hash(mod(i + 1.0, period), 3.0, k), f);\n"
			+ "}\n"
			// Value noise, 0 to 1, whole cells across: what frays a soft cloud's edge.
			+ "float grain(vec2 p, float period, float k)\n"
			+ "{\n"
			+ "	vec2 i = floor(p);\n"
			+ "	vec2 f = p - i;\n"
			+ "	f = f * f * (3.0 - 2.0 * f);\n"
			+ "	float x0 = mod(i.x, period);\n"
			+ "	float x1 = mod(i.x + 1.0, period);\n"
			+ "	return mix(mix(hash(x0, i.y, k), hash(x1, i.y, k), f.x), mix(hash(x0, i.y + 1.0, k), hash(x1, i.y + 1.0, k), f.x), f.y);\n"
			+ "}\n"
			// A cloud's top over its base at x, in lobes: under 0 where none stands.
			+ "float crown(float x)\n"
			+ "{\n"
			+ "	float slots = max(1.0, floor(u_cloud.y / u_cloud.z + 0.5));\n"
			+ "	float s = x * slots / u_cloud.y;\n"
			+ "	float m = 0.55 * mass(s, slots, u_shape.w) + 0.3 * mass(2.0 * s + 0.5, 2.0 * slots, u_shape.w + 9.0)\n"
			+ "		+ 0.15 * mass(4.0 * s + 0.25, 4.0 * slots, u_shape.w + 10.0);\n"
			+ "	float none = (1.0 - u_cloud.x) * 0.8;\n"
			// Never past the layer's top, where its quad would cut it.
			+ "	float room = (1.0 - u_shape.x) * u_size.y * u_cloud.y / u_size.x - 0.6;\n"
			+ "	return min(u_shape.y * (m - none) / (0.85 - none), room);\n"
			+ "}\n"
			// The cloud at p (lobes, y from the base): x over 0 inside its top, y over 0 above its foot. facing: which way its
			// surface turns there, as a painter shades one: the whole heap a mound (its crown up, its belly down, its flanks out),
			// each large lobe a ball on it, the middle ones a little; the small bumps are the outline's alone.
			+ "vec2 cloud(vec2 p, float t, out vec2 facing)\n"
			+ "{\n"
			+ "	float top = crown(p.x);\n"
			+ "	vec4 b1 = billow(p, u_cloud.y, u_shape.w + 20.0, t);\n"
			// The finer domes: about 2.3 and 5.3 a lobe, a whole number of them across the layer, or its tiles show a seam.
			+ "	float across2 = floor(2.3 * u_cloud.y + 0.5);\n"
			+ "	float across5 = floor(5.3 * u_cloud.y + 0.5);\n"
			+ "	vec4 b2 = billow(across2 / u_cloud.y * p + 7.0, across2, u_shape.w + 30.0, 2.0 * t);\n"
			+ "	float b3 = billow(across5 / u_cloud.y * p + 3.0, across5, u_shape.w + 40.0, 3.0 * t).x;\n"
			// Steeper over the crown than under it: a dome's tip alone, over the crown, would float as a ball.
			+ "	float rise = top - p.y;\n"
			+ "	float tall = (rise < 0.0 ? 1.5 : 0.8) * rise - 0.67 + 0.75 * b1.x + 0.22 * b2.x + 0.08 * b3;\n"
			+ "	if (u_tone.w > 0.0)\n"
			+ "		tall += u_tone.w * (0.3 * grain(3.0 * p + 0.6 * vec2(sin(TAU * t), cos(TAU * t)), 3.0 * u_cloud.y, u_shape.w + 50.0)\n"
			+ "			+ 0.2 * grain(7.0 * p - 0.6 * vec2(cos(TAU * 2.0 * t), sin(TAU * 2.0 * t)), 7.0 * u_cloud.y, u_shape.w + 51.0) - 0.3);\n"
			+ "	float foot = 3.0 * p.y + 0.35 * (b2.x - 0.5) + 0.15;\n"
			+ "	float lean = (crown(p.x + 0.25) - crown(p.x - 0.25)) / 0.5;\n"
			+ "	vec2 mound = vec2(-0.5 * lean, 1.6 * p.y / max(top, 0.4) - 0.75);\n"
			// The lobes show under the crown and fade toward the belly, which a painter leaves broad.
			+ "	float show = 1.0 - 0.65 * smoothstep(0.5, 1.9, top - p.y);\n"
			+ "	facing = 0.45 * clamp(mound, -1.0, 1.0) + show * (0.5 * b1.zw + 0.22 * b2.zw);\n"
			+ "	return vec2(tall, foot);\n"
			+ "}\n"
			+ "void main()\n"
			+ "{\n"
			+ "	vec2 l = local();\n"
			+ "	float lobe = u_size.x / u_cloud.y;\n"
			+ "	vec2 p = vec2(l.x, l.y - u_shape.x * u_size.y) / lobe;\n"
			+ "	float t = u_shape.z;\n"
			+ "	vec2 turned;\n"
			+ "	vec2 c = cloud(p, t, turned);\n"
			+ "	float d = min(c.x, c.y);\n"
			+ "	float soft = max(u_cloud.w, 0.004);\n"
			// A cloud's thin end, where its top meets its foot, melts away: cut, it is a hairline.
			+ "	float alpha = smoothstep(0.0, soft, c.x) * smoothstep(0.0, soft + 0.3 * u_tone.w, c.y) * smoothstep(0.1, 0.45, c.x + c.y);\n"
			+ "	vec2 s = sunward();\n"
			+ "	vec3 normal = vec3(turned, sqrt(max(0.04, 1.0 - dot(turned, turned))));\n"
			+ "	float lit = 0.5 + 0.5 * dot(normalize(normal), normalize(vec3(s, 0.35)));\n"
			+ "	lit = clamp(lit, 0.0, 1.0);\n"
			+ "	float w = mix(0.5, 0.03, u_tone.x);\n"
			+ "	float cut = 0.5 * smoothstep(0.42 - w, 0.42 + w, lit) + 0.5 * smoothstep(0.66 - w, 0.66 + w, lit);\n"
			+ "	vec3 color = mix(u_shade, u_lit, mix(lit, cut, min(1.0, 1.5 * u_tone.x)));\n"
			+ "	float thin = 1.0 - smoothstep(0.0, 0.2 + soft, d);\n"
			+ "	float near = sunNear();\n"
			+ "	color = mix(color, min(vec3(1.0), 1.25 * u_lit), u_tone.y * thin * near * near);\n"
			+ "	gl_FragColor = hazed(v_color * vec4(color, alpha * u_tone.z));\n"
			+ "}\n";

	/**
	 * What WISPS picks its shapes with (CUMULUS holds its own copy): whole numbers in, a 289th out, by a permutation
	 * polynomial exact in a float's 24 bits, so every GPU draws the same sky (a sine hash does not: round 1's numpy
	 * model drew other clouds in float64 than in float32).
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
	 * WISPS (r556): streaks made in the shader, the layer's image never read. Value noise over a lattice of
	 * {@code u_cloud.z} whole cells across the layer (so it tiles), {@code u_cloud.w} world units tall, four octaves,
	 * each finer one sliding and rising on the churn's clock ({@code u_shape.z}) so the shapes change as they drift.
	 * A cloud is where the noise passes a threshold the coverage sets, less what its height costs: {@code u_shape.y}
	 * under the base, slowly above, and all of it toward the layer's foot and top. Lit by how the noise falls toward
	 * the sun (a second, three-octave read); a silver lining on thin edges near the sun.
	 */
	static final String WISPS_FRAGMENT = HEAD + TONED + HASH
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
	 * tile's top-left, held at its top and bottom rows, and across either wrapping (calm's bank, which tiles: {@code u_wrap}
	 * 1) or held a hair inside its sides (OneNight's cumulus, a region packed to its paint with sky between its tiles:
	 * wrapped, a pushed pixel showed the cloud's other end as a speck). A cloud pushed past its packed quad is cut
	 * there: a shipped one needs the quad's margin. {@code u_paint}: a painting
	 * whose shapes are in its opacity (x lowest, y highest) gives it as its shade, by the weight z, and is drawn solid
	 * past a faint edge, by w ({@link #CUMULUS_PAINT}).
	 */
	static final String PAINTED = HEAD + TONED
			+ "uniform vec4 u_paint;\n"
			+ "uniform float u_wrap;\n"
			+ "vec2 along()\n"
			+ "{\n"
			+ "	return vec2(u_across.z * (v_view.x * u_across.x - u_across.y), u_up.z * (v_view.y * u_up.x - u_up.y));\n"
			+ "}\n"
			+ "vec4 at(vec2 l)\n"
			+ "{\n"
			+ "	vec2 f = vec2(clamp(mix(l.x / u_size.x, fract(l.x / u_size.x), u_wrap), 0.002, 0.998), clamp(l.y / u_size.y, 0.0, 1.0));\n"
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
			+ "	float drawn = (painted.a - u_paint.x) / (u_paint.y - u_paint.x);\n"
			+ "	float solid = mix(painted.a, smoothstep(0.0, 0.08, painted.a), u_paint.w);\n"
			+ "	float edge = mix(painted.a - toward, smoothstep(0.0, 0.08, painted.a) - smoothstep(0.0, 0.08, toward), u_paint.w);\n"
			+ "	vec3 color = toned(mix(0.25 + 0.6 * up, 0.1 + drawn, u_paint.z) + mix(1.5, 0.35, u_paint.z) * edge);\n"
			+ "	color = mix(color, min(vec3(1.0), 1.15 * u_lit), u_tone.y * (1.0 - solid) * sunNear());\n"
			+ "	gl_FragColor = hazed(v_color * vec4(color, solid * u_tone.z));\n"
			+ "}\n";
}
