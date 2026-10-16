package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.SelectElement;

import jks.tools2d.parallax.LayerEffects;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Parallax_Model;
import jks.tools2d.parallax.pages.Utils_Page_Json;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * The shadow lab (r425, Simon on r392: "Should the trees cast shadows on the layer behind? Yes [...] considering where
 * the light source is (and are) [...] Prepare a lab for it"): a nearer layer casts its shadow on the layer just behind
 * it only, never on the sky nor further back, from one light or two. r365's AI forest page (the hills lab's, its hills
 * as they ship), drawn three times by the real reader, side by side: A with distance, B the pines (l3) casting on the
 * hills (l2) as before, the shadow moving with the pines, C as painted.
 * <p>
 * A (r425, Simon on B and on a shadow moving with the hills: "they should move partly with the hill"; "the main problem
 * currently is the lack of feeling of distance ... the forest is, as a cardboard cutout, in front of the mountain ...
 * the mountain doesn't cast shadows"): a shadow lies on ground that runs away from its tree, so the farther along it,
 * the farther away: its foot moves with the pines, its far end {@code withHills} of the way to the hills' speed (the
 * shadows fan out from the view's middle, as from a vanishing point, {@link #WARP_VERTEX}); it blurs wider and fades the
 * farther it lies from the foot (a penumbra grows with the distance from what casts it); and the hills cast theirs on
 * the far range (l1) the same way.
 * <p>
 * B is drawn in passes, each by a heart holding the whole page with the layers it does not draw pushed far under the
 * view (so the page's front, and its fog, stay the page's): the sky and the layers up to the hills; the hills again,
 * into the stencil only, where they are opaque; the pines once per light, laid on the ground away from it, dark,
 * softened, where the stencil is set; the pines themselves on top. B's shadow is the pines' own layer, scrolled
 * with them; C's is at the hills' speed (a second heart), as the world would have it.
 * <p>
 * Each light is a sun (r425, Simon: "it acts as if there was a wall behind the trees and the sun was angled at 60
 * degrees"; the first lab moved the shadow by a free 2D angle, its default from below on the left, which no sun is):
 * a side, round the viewer (0 behind you, 90 on your right, 180 ahead in the picture), and a height over the horizon.
 * A shadow starts at the tree's foot and lies on the ground away from the sun, as long as the tree is tall over the
 * tangent of the sun's height ({@link #shear}): a point h over the pines' foot line goes sideways by h times the sun's
 * sideways run, and up the screen by h times its run into the picture, times {@code rise}, how far up the screen the
 * ground climbs for a unit it runs into the picture (low for flat ground seen low, more where the hills rise behind).
 * The moved silhouette the first lab drew (r425's b52a23a) was a shadow on a wall standing right behind the trees. A
 * sun behind you lays the shadows up into the picture, onto the hills, long when it is low; a sun ahead lays them
 * toward you, onto nothing behind the pines. A lab's passes, not an effect: what ships, if anything, waits for Simon's tests (no
 * Enum_ShaderEffect, no stored field). Opened with shadow-lab.html: tools/shadow-lab.sh.
 * <p>
 * {@code &clip=1}: nothing moves by itself, {@code window.shadowLabAct(seconds)} steps every heart at 1/60 s.
 */
public class ShadowLab extends ApplicationAdapter implements LabKit.Clip
{
	static final String PAGE = "hills-lab/forest.jplax";
	/** The hills as they ship (the hills lab's "today", the page's own seed): l2's region. */
	static final String HILLS_REGION = "h_today_7020";
	/** The receiver, the layer just behind the caster, and the caster: indices in the page's list. */
	static final int HILLS = 2, PINES = 3;
	static final float WORLD_WIDTH = FogLab.WORLD_WIDTH, WORLD_HEIGHT = FogLab.WORLD_HEIGHT;
	/** Three panels side by side, each the round's whole 16:9 frame. */
	static final int FRAME_WIDTH = FogLab.FRAME_WIDTH, FRAME_HEIGHT = FogLab.FRAME_HEIGHT;
	/**
	 * The pines' atlas page, forest-pines.png: four strips of 384 px, one above the next with a 1 px gap. The shadow's
	 * blur keeps its samples in the strip it is in, or a strip's opaque foot shades the next one's sky.
	 */
	static final float PINES_PAGE_HEIGHT = 1539, PINES_STRIP = 385;
	/** Where a hidden layer goes: this far under the view, in percent of the world's height. */
	static final float HIDDEN = -10000;
	/** The range the hills cast on in A. */
	static final int RANGE = 1;
	static final String[] VIEWS = { "the page", "without the pines" };
	static final String[] OFF_ON = { "off", "on" };

	/**
	 * The page as painted (C); B's passes: the back, the stencil, the shadows, the front (the pines); A's: the back
	 * without the hills, the range into the stencil, the hills' shadows, the hills, then as B (the stencil and the front
	 * are B's, the pines' shadows their own heart).
	 */
	static final int PAINTED = 0, BACK = 1, MASK = 2, SHADOW = 3, FAR = 4, RANGE_MASK = 5, HILLS_SHADOW = 6, HILLS_ONLY = 7,
			FRONT = 8;
	private final Parallax_Heart[] hearts = new Parallax_Heart[9];
	private OrthographicCamera camera;
	private SpriteBatch batch;
	/** The shadows' batch: cuts each draw in strips, so the warp's fan bends every strip (StripBatch). */
	private StripBatch strips;
	private ShaderProgram mask, shadow;
	private SelectElement view, second, hillsCast;
	private InputElement scroll, side1, height1, side2, height2, rise, softness, darkness, withHills, softFar, fadeFar, rangeRise;
	/** The camera the shadows are tiled by: wider, so a shadow laid far sideways is all drawn. */
	private OrthographicCamera wide, wideHills;
	/** How far a shadow may run sideways, world units: the wide camera's margin, at least. */
	static final float REACH = 60;
	private Element readout;
	private boolean clip;
	/** What the pines' layer in each heart is: what the shadow pass moves, what the view hides. */
	private final ParallaxLayer[] pines = new ParallaxLayer[hearts.length];
	private int showing = -1;
	/** The page B's back draws: its fog, which the shadow on its hills takes. */
	private WholePage_Model backPage;

	@Override
	public void create()
	{
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		batch = new SpriteBatch();
		strips = new StripBatch();
		mask = compile(batch.getShader().getVertexShaderSource(), MASK_FRAGMENT, "mask");
		shadow = compile(WARP_VERTEX, SHADOW_FRAGMENT, "shadow");
		camera = new OrthographicCamera();
		camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
		for (int i = 0; i < hearts.length; i++)
		{
			WholePage_Model page = Utils_Page_Json.loadPage(PAGE);
			page.pageModel.pageList.get(HILLS).regionName = HILLS_REGION;
			for (int k = 0; k < page.pageModel.pageList.size(); k++)
			{
				boolean drawn = i == PAINTED ? true : i == BACK ? k != PINES : i == FAR ? k < HILLS : i == RANGE_MASK ? k == RANGE
						: i == MASK || i == HILLS_SHADOW || i == HILLS_ONLY ? k == HILLS : k == PINES;
				if (!drawn)
					hide(page.pageModel.pageList.get(k));
			}
			hearts[i] = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i].setPage(page);
			if (i == BACK)
				backPage = page;
			pines[i] = hearts[i].parallaxReader.layers.get(PINES);
			// The stencil's and the shadows' layers draw through the lab's shader, the batch's: no effect of the reader's.
			if (i == MASK || i == SHADOW || i == RANGE_MASK || i == HILLS_SHADOW)
				hearts[i].parallaxReader.setLayerEffects(BATCH_SHADER);
		}
		wide = wideFor(pines[SHADOW]);
		wideHills = wideFor(hearts[HILLS_SHADOW].parallaxReader.layers.get(HILLS));
		buildControls();
		if (clip)
			LabKit.exportClip("shadowLab", this);
	}

	/**
	 * A camera wider than the view by whole tiles of {@code caster} on each side, at least {@link #REACH}: the reader
	 * tiles a layer from its view's left edge, so its tiles land where the camera's own view puts them.
	 */
	private static OrthographicCamera wideFor(ParallaxLayer caster)
	{
		float tile = caster.getTotalWidth();
		return new OrthographicCamera(WORLD_WIDTH + 2 * tile * (float) Math.ceil(REACH / tile), WORLD_HEIGHT);
	}

	/** Pushes a layer far under the view: the heart keeps it (its speed is the page's front), nothing of it shows. */
	private static void hide(Parallax_Model layer)
	{layer.decal_Y_Ratio = HIDDEN;}

	private static ShaderProgram compile(String vertex, String fragment, String name)
	{
		ShaderProgram program = new ShaderProgram(vertex, fragment);
		if (!program.isCompiled())
			throw new IllegalStateException("the shadow lab's " + name + " shader does not compile: " + program.getLog());
		return program;
	}

	private void buildControls()
	{
		Document document = Document.get();
		LabControls.panelLabels(document, "shadow-labels", "A: with distance", "B: as before, moves with the pines", "C: as painted");
		view = LabControls.stageChoice(document, "shadow-labels", "view", "view (the whole page; or without the pines, to see the shadow's shape alone)", VIEWS, 0);
		Element controls = document.getElementById("shadow-controls");
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll (units a second)", -160, 160, 5, 20);
		side1 = LabControls.slider(document, controls, "sun1Side", "sun 1 side (degrees round you: 0 behind you, -90 on your left, 90 on your right, 180 ahead in the picture; or drag it on the dial)", -180, 180, 5, -40);
		height1 = LabControls.slider(document, controls, "sun1Height", "sun 1 height (degrees over the horizon: low, long shadows)", 5, 85, 1, 20);
		second = LabControls.choice(document, controls, "sun2On", "sun 2 (a second light, its own shadow)", OFF_ON, 0);
		side2 = LabControls.slider(document, controls, "sun2Side", "sun 2 side (degrees, as sun 1)", -180, 180, 5, 35);
		height2 = LabControls.slider(document, controls, "sun2Height", "sun 2 height (degrees, as sun 1)", 5, 85, 1, 30);
		rise = LabControls.slider(document, controls, "rise", "rise (how far up the screen the ground climbs for a unit it runs into the picture: low for flat ground seen low, more where the hills rise behind)", 0.05f, 1.5f, 0.05f, 0.5f);
		softness = LabControls.slider(document, controls, "softness", "softness (the blur's radius, world units)", 0, 0.8f, 0.01f, 0.1f);
		darkness = LabControls.slider(document, controls, "darkness", "darkness (0 no shadow, 1 black)", 0, 1, 0.05f, 0.5f);
		Element distance = LabControls.section(document, controls, "distance (A only: the ground behind the trees runs away from them)");
		withHills = LabControls.slider(document, distance, "withHills", "with the hills (how much of the hills' motion a tree's shadow takes at its far end: its foot stays under the tree; 0 as B)", 0, 1, 0.05f, 0.5f);
		softFar = LabControls.slider(document, distance, "softFar", "soft far (the blur added at a shadow's far end, world units: sharp at the foot)", 0, 0.8f, 0.01f, 0.3f);
		fadeFar = LabControls.slider(document, distance, "fadeFar", "fade far (how much lighter a shadow is at its far end)", 0, 1, 0.05f, 0.5f);
		hillsCast = LabControls.choice(document, distance, "hillsCast", "hills cast (the hills' own shadow on the far range)", OFF_ON, 1);
		rangeRise = LabControls.slider(document, distance, "rangeRise", "range rise (as rise, on the far range: steeper, so higher up the screen)", 0.05f, 1.5f, 0.05f, 0.8f);
		readout = LabControls.readout(document, controls, "shadow-readout");
	}

	private void applyControls()
	{
		int v = LabControls.read(view);
		if (v != showing)
		{
			// Without the pines: neither A's nor B's front draws them; B's shadow pass still does.
			float y = v == 1 ? HIDDEN : 0;
			pines[PAINTED].setDecalPercentY(y);
			pines[FRONT].setDecalPercentY(y);
			showing = v;
		}
		float sc = LabControls.read(scroll);
		for (Parallax_Heart heart : hearts)
			heart.screenSpeedConstantX = sc;
		// Read even while off: a slider shows its value when read.
		float s2 = LabControls.read(side2), h2 = LabControls.read(height2);
		LabControls.read(softness);
		LabControls.read(darkness);
		LabControls.read(withHills);
		LabControls.read(softFar);
		LabControls.read(fadeFar);
		LabControls.read(rangeRise);
		LabControls.read(hillsCast);
		float r = LabControls.read(rise);
		float[] k = shear(LabControls.read(side1), LabControls.read(height1), r);
		readout.setInnerText("the pines (l3) cast on the hills (l2) only, and in A the hills on the far range (l1). Sun 1: a tree's shadow " + describe(k)
				+ (LabControls.read(second) == 1 ? "; sun 2: " + describe(shear(s2, h2, r)) : "")
				+ ". The world is " + round(WORLD_WIDTH) + " units wide.");
	}

	/**
	 * A sun's shadow on the ground, {x, y}: where the shadow of a point a unit over a tree's foot lands, from that foot,
	 * in world units on screen. The sun at {@code side} degrees round the viewer (0 behind, 90 on the right), {@code
	 * height} over the horizon: its rays run down a unit for every cot(height) along the ground, away from it, of which
	 * -sin(side) sideways and cos(side) into the picture, up the screen by {@code rise}. A sun ahead (cos(side) under 0)
	 * lays it toward the viewer: y under 0, nothing on the hills.
	 */
	static float[] shear(float side, float height, float rise)
	{
		double a = Math.toRadians(side), run = 1 / Math.tan(Math.toRadians(height));
		return new float[] { (float) (-Math.sin(a) * run), (float) (Math.cos(a) * run * rise) };
	}

	private static String describe(float[] k)
	{
		if (k[1] <= 0)
			return "falls toward you, on nothing behind the pines";
		return "runs " + round(Math.abs(k[0])) + " units " + (k[0] < 0 ? "left" : "right") + " and " + round(k[1])
				+ " up the screen for every unit of the tree's height" + (k[1] > 1 ? " (above the treetops)" : " (under the treetops)");
	}

	private static float round(float value)
	{return Math.round(value * 100) / 100f;}

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
		int height = Gdx.graphics.getBackBufferHeight();
		LabKit.beginPanels();
		if (LabKit.panel(0, height, FRAME_WIDTH, FRAME_HEIGHT, FRAME_HEIGHT, 0))
			distant();
		if (LabKit.panel(FRAME_WIDTH + 4, height, FRAME_WIDTH, FRAME_HEIGHT, FRAME_HEIGHT, 0))
		{
			hearts[BACK].render();
			cast(MASK, SHADOW, HILLS, PINES, LabControls.read(rise), false);
			drawLayers(FRONT, camera);
		}
		if (LabKit.panel(2 * (FRAME_WIDTH + 4), height, FRAME_WIDTH, FRAME_HEIGHT, FRAME_HEIGHT, 0))
			hearts[PAINTED].render();
		LabKit.endPanels();
	}

	/** A: the back without the hills, the hills' shadows on the range, the hills, the pines' shadows on them, the pines. */
	private void distant()
	{
		hearts[FAR].render();
		if (LabControls.read(hillsCast) == 1)
			cast(RANGE_MASK, HILLS_SHADOW, RANGE, HILLS, LabControls.read(rangeRise), true);
		drawLayers(HILLS_ONLY, camera);
		cast(MASK, SHADOW, HILLS, PINES, LabControls.read(rise), true);
		drawLayers(FRONT, camera);
	}

	/**
	 * Heart {@code into}'s layer into the stencil, then heart {@code from}'s caster's shadow per light where it is set:
	 * {@code receiver}'s fog over it, the ground climbing {@code rise} up the screen a unit into the picture; {@code far},
	 * A's distance (the fan, the blur and the fade along it), or B's flat shadow.
	 */
	private void cast(int into, int from, int receiver, int caster, float rise, boolean far)
	{
		Gdx.gl.glClearStencil(0);
		Gdx.gl.glClear(GL20.GL_STENCIL_BUFFER_BIT);
		Gdx.gl.glEnable(GL20.GL_STENCIL_TEST);
		Gdx.gl.glStencilFunc(GL20.GL_ALWAYS, 1, 0xff);
		Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE);
		Gdx.gl.glColorMask(false, false, false, false);
		batch.setShader(mask);
		drawLayers(into, camera);
		batch.setShader(null);
		Gdx.gl.glColorMask(true, true, true, true);
		Gdx.gl.glStencilFunc(GL20.GL_EQUAL, 1, 0xff);
		Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_KEEP);
		shade(from, receiver, caster, shear(LabControls.read(side1), LabControls.read(height1), rise), far);
		if (LabControls.read(second) == 1)
			shade(from, receiver, caster, shear(LabControls.read(side2), LabControls.read(height2), rise), far);
		Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
	}

	/**
	 * One sun's shadow: heart {@code i}'s caster warped about its foot line by {@link #WARP_VERTEX}, a point h over it
	 * moved by h times {@code k} ({@link #shear}), and, far, sideways by h over the caster's height times
	 * {@code withHills} of what the receiver's slower speed leaves behind, from the view's middle; tiled across the
	 * wide camera.
	 */
	private void shade(int i, int receiver, int caster, float[] k, boolean far)
	{
		if (k[1] <= 0)
			return;
		ParallaxLayer from = hearts[i].parallaxReader.layers.get(caster), onto = hearts[BACK].parallaxReader.layers.get(receiver);
		float foot = camera.position.y - WORLD_HEIGHT / 2 + from.getCurrentDistanceY(), tall = from.getHeight();
		float fan = far ? LabControls.read(withHills) * (onto.getParallaxSpeedRatioX() / from.getParallaxSpeedRatioX() - 1) : 0;
		strips.setShader(shadow);
		shadow.bind();
		shadow.setUniformf(shadow.fetchUniformLocation("u_warp", false), foot, k[0], k[1], fan);
		shadow.setUniformf(shadow.fetchUniformLocation("u_middle", false), camera.position.x, tall);
		TextureRegion region = from.getDrawnRegion();
		float texels = region.getRegionWidth() / (from.getWidth() * from.getPackedWidthRatio());
		float width = region.getTexture().getWidth(), height = region.getTexture().getHeight();
		float soft = LabControls.read(softness) * texels, grow = far ? LabControls.read(softFar) * texels : 0;
		shadow.setUniformf(shadow.fetchUniformLocation("u_soft", false), soft / width, soft / height, grow / width, grow / height);
		// The pines' page holds four strips, one over the next: the blur stays in its own. Any other caster: its page.
		shadow.setUniformf(shadow.fetchUniformLocation("u_strips", false), caster == PINES ? PINES_PAGE_HEIGHT / PINES_STRIP : 1, 0.5f / height);
		// The shadow lies on the receiver: as far into the page's fog as it is.
		float fog = ParallaxPageReader.fogOf(backPage.getFogStrength(), ParallaxPageReader.frontSpeedOf(hearts[BACK].parallaxReader.layers), onto);
		Color fogColor = backPage.fogColor;
		shadow.setUniformf(shadow.fetchUniformLocation("u_shadow", false), SHADOW_COLOR.r * (1 - fog) + fogColor.r * fog,
				SHADOW_COLOR.g * (1 - fog) + fogColor.g * fog, SHADOW_COLOR.b * (1 - fog) + fogColor.b * fog, LabControls.read(darkness));
		shadow.setUniformf(shadow.fetchUniformLocation("u_fade", false), far ? LabControls.read(fadeFar) : 0);
		camera.update();
		OrthographicCamera tiled = caster == PINES ? wide : wideHills;
		tiled.position.set(camera.position);
		tiled.update();
		strips.setProjectionMatrix(camera.combined);
		strips.enableBlending();
		strips.begin();
		hearts[i].parallaxReader.draw(tiled, strips);
		strips.end();
		strips.setShader(null);
	}

	/** Heart {@code i}'s layers alone, without its gradients, tiled across {@code view}, drawn through the camera. */
	private void drawLayers(int i, OrthographicCamera view)
	{
		camera.update();
		view.position.set(camera.position);
		view.update();
		batch.setProjectionMatrix(camera.combined);
		batch.enableBlending();
		batch.begin();
		hearts[i].parallaxReader.draw(view, batch);
		batch.end();
	}

	@Override
	public void dispose()
	{
		batch.dispose();
		strips.dispose();
		mask.dispose();
		shadow.dispose();
	}

	/** The shadow's colour out of the fog: a blue black, as the shade of a bright sky's light. */
	static final Color SHADOW_COLOR = new Color(0.04f, 0.06f, 0.12f, 1);

	/** The stencil's and the shadows' effects: none, so their layers draw through the batch's shader, the lab's. */
	static final LayerEffects BATCH_SHADER = new LayerEffects()
	{
		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{return false;}

		@Override
		public void end(Batch batch, ParallaxLayer layer)
		{}

		@Override
		public boolean beginPageFog(Batch batch, Color tint, Color fog, Color incoming)
		{return false;}
	};

	static final String HEAD = "#ifdef GL_ES\n"
			+ "precision mediump float;\n"
			+ "#endif\n"
			+ "varying vec4 v_color;\n"
			+ "varying vec2 v_texCoords;\n"
			+ "uniform sampler2D u_texture;\n";

	/** The hills into the stencil: only where they are opaque, so the sky behind them takes no shadow. */
	static final String MASK_FRAGMENT = HEAD
			+ "void main()\n"
			+ "{\n"
			+ "	if (texture2D(u_texture, v_texCoords).a < 0.5)\n"
			+ "		discard;\n"
			+ "	gl_FragColor = vec4(1.0);\n"
			+ "}\n";

	/**
	 * The batch's vertex shader, its corners warped as a shadow on the ground (r425): u_warp: the caster's foot line (world
	 * y), the sun's sideways and up runs a unit of height ({@link #shear}), and the fan: at a height of the caster's
	 * (u_middle.y) over its foot, a point moves that much more slowly than the caster, in its distance from the view's
	 * middle (u_middle.x), as its foot would at the receiver's speed. v_far: the height over the foot, in the caster's.
	 */
	static final String WARP_VERTEX = "attribute vec4 a_position;\n"
			+ "attribute vec4 a_color;\n"
			+ "attribute vec2 a_texCoord0;\n"
			+ "uniform mat4 u_projTrans;\n"
			+ "uniform vec4 u_warp;\n"
			+ "uniform vec2 u_middle;\n"
			+ "varying vec4 v_color;\n"
			+ "varying vec2 v_texCoords;\n"
			+ "varying float v_far;\n"
			+ "void main()\n"
			+ "{\n"
			+ "	v_color = a_color;\n"
			+ "	v_color.a = v_color.a * (255.0 / 254.0);\n"
			+ "	v_texCoords = a_texCoord0;\n"
			+ "	float h = a_position.y - u_warp.x;\n"
			+ "	v_far = clamp(h / u_middle.y, 0.0, 1.0);\n"
			+ "	vec2 p = vec2(a_position.x + u_warp.y * h + u_warp.w * h / u_middle.y * (a_position.x - u_middle.x), u_warp.x + u_warp.z * h);\n"
			+ "	gl_Position = u_projTrans * vec4(p, 0.0, 1.0);\n"
			+ "}\n";

	/**
	 * The caster as a shadow: its alpha, averaged over a disc of u_soft.xy, plus u_soft.zw at its far end (in the page's
	 * texture), in u_shadow's colour at u_shadow.a, u_fade lighter at its far end. u_strips: the strips a page height
	 * holds, and half a texel: the samples stay in their strip.
	 */
	static final String SHADOW_FRAGMENT = HEAD
			+ "varying float v_far;\n"
			+ "uniform vec4 u_soft;\n"
			+ "uniform vec2 u_strips;\n"
			+ "uniform vec4 u_shadow;\n"
			+ "uniform float u_fade;\n"
			+ "float at(vec2 uv, float top, float bottom)\n"
			+ "{\n"
			+ "	return texture2D(u_texture, vec2(uv.x, clamp(uv.y, top, bottom))).a;\n"
			+ "}\n"
			+ "void main()\n"
			+ "{\n"
			+ "	float strip = floor(v_texCoords.y * u_strips.x);\n"
			+ "	float top = strip / u_strips.x + u_strips.y, bottom = (strip + 1.0) / u_strips.x - 3.0 * u_strips.y;\n"
			+ "	float a = at(v_texCoords, top, bottom);\n"
			+ "	vec2 soft = u_soft.xy + v_far * u_soft.zw;\n"
			// Two rings of eight round the middle, the outer one at the radius.
			+ "	for (int i = 0; i < 8; i++)\n"
			+ "	{\n"
			+ "		float angle = 0.7853982 * float(i);\n"
			+ "		vec2 ring = vec2(cos(angle), sin(angle)) * soft;\n"
			+ "		a += at(v_texCoords + ring, top, bottom) + at(v_texCoords + 0.5 * vec2(-ring.y, ring.x), top, bottom);\n"
			+ "	}\n"
			+ "	gl_FragColor = vec4(u_shadow.rgb, u_shadow.a * (1.0 - u_fade * v_far) * a / 17.0 * v_color.a);\n"
			+ "}\n";
}
