package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
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
 * as they ship), drawn twice by the real reader: A as painted, B with the pines (l3) casting on the hills (l2).
 * <p>
 * B is drawn in passes, each by a heart holding the whole page with the layers it does not draw pushed far under the
 * view (so the page's front, and its fog, stay the page's): the sky and the layers up to the hills; the hills again,
 * into the stencil only, where they are opaque; the pines once per light, laid on the ground away from it, dark,
 * softened, where the stencil is set; the pines themselves on top. The shadow is the pines' own layer, scrolled
 * with them, or at the hills' speed (a second heart), as the world would have it.
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
	/** Two panels side by side, each the round's whole 16:9 frame. */
	static final int FRAME_WIDTH = FogLab.FRAME_WIDTH, FRAME_HEIGHT = FogLab.FRAME_HEIGHT;
	/**
	 * The pines' atlas page, forest-pines.png: four strips of 384 px, one above the next with a 1 px gap. The shadow's
	 * blur keeps its samples in the strip it is in, or a strip's opaque foot shades the next one's sky.
	 */
	static final float PINES_PAGE_HEIGHT = 1539, PINES_STRIP = 385;
	/** Where a hidden layer goes: this far under the view, in percent of the world's height. */
	static final float HIDDEN = -10000;
	static final String[] VIEWS = { "the page", "without the pines" };
	static final String[] OFF_ON = { "off", "on" };

	static final String[] FOLLOWS = { "the pines", "the hills" };

	/** A: the page as painted. B's passes: the back, the stencil, the shadows (at the pines' speed or the hills'), the front. */
	static final int PAINTED = 0, BACK = 1, MASK = 2, SHADOW = 3, SHADOW_ON_HILLS = 4, FRONT = 5;
	private final Parallax_Heart[] hearts = new Parallax_Heart[6];
	private OrthographicCamera camera;
	private SpriteBatch batch;
	private ShaderProgram mask, shadow;
	private SelectElement view, second, follows;
	private InputElement scroll, side1, height1, side2, height2, rise, softness, darkness;
	/** The shadow pass's shear, and the camera it tiles the pines by: wider, so a shadow laid far sideways is all drawn. */
	private final Matrix4 sheared = new Matrix4(), straight = new Matrix4();
	private OrthographicCamera wide;
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
		mask = compile(batch, MASK_FRAGMENT, "mask");
		shadow = compile(batch, SHADOW_FRAGMENT, "shadow");
		camera = new OrthographicCamera();
		camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
		for (int i = 0; i < hearts.length; i++)
		{
			WholePage_Model page = Utils_Page_Json.loadPage(PAGE);
			page.pageModel.pageList.get(HILLS).regionName = HILLS_REGION;
			for (int k = 0; k < page.pageModel.pageList.size(); k++)
			{
				boolean drawn = i == PAINTED || i == BACK ? k != PINES : i == MASK ? k == HILLS : k == PINES;
				// On the hills, the shadow scrolls as they do: the pines' image at the hills' speed.
				if (i == SHADOW_ON_HILLS && k == PINES)
				{
					page.pageModel.pageList.get(k).parallaxScalingSpeedX = page.pageModel.pageList.get(HILLS).parallaxScalingSpeedX;
					page.pageModel.pageList.get(k).parallaxScalingSpeedY = page.pageModel.pageList.get(HILLS).parallaxScalingSpeedY;
				}
				if (i == PAINTED)
					drawn = true;
				if (!drawn)
					hide(page.pageModel.pageList.get(k));
			}
			hearts[i] = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i].setPage(page);
			if (i == BACK)
				backPage = page;
			pines[i] = hearts[i].parallaxReader.layers.get(PINES);
			// The stencil's and the shadows' layers draw through the lab's shader, the batch's: no effect of the reader's.
			if (i == MASK || i == SHADOW || i == SHADOW_ON_HILLS)
				hearts[i].parallaxReader.setLayerEffects(BATCH_SHADER);
		}
		// The reader tiles a layer from its view's left edge: widened by whole tiles on each side, the pines' tiles land
		// where the camera's own view puts them.
		float tile = pines[SHADOW].getTotalWidth();
		wide = new OrthographicCamera(WORLD_WIDTH + 2 * tile * (float) Math.ceil(REACH / tile), WORLD_HEIGHT);
		buildControls();
		if (clip)
			LabKit.exportClip("shadowLab", this);
	}

	/** Pushes a layer far under the view: the heart keeps it (its speed is the page's front), nothing of it shows. */
	private static void hide(Parallax_Model layer)
	{layer.decal_Y_Ratio = HIDDEN;}

	/** {@code fragment} behind the batch's own vertex shader. */
	private static ShaderProgram compile(SpriteBatch batch, String fragment, String name)
	{
		ShaderProgram program = new ShaderProgram(batch.getShader().getVertexShaderSource(), fragment);
		if (!program.isCompiled())
			throw new IllegalStateException("the shadow lab's " + name + " shader does not compile: " + program.getLog());
		return program;
	}

	private void buildControls()
	{
		Document document = Document.get();
		LabControls.panelLabels(document, "shadow-labels", "A: as painted", "B: the pines cast on the hills");
		view = LabControls.stageChoice(document, "shadow-labels", "view", "view (the whole page; or without the pines, to see the shadow's shape alone)", VIEWS, 0);
		Element controls = document.getElementById("shadow-controls");
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll (units a second)", -160, 160, 5, 20);
		side1 = LabControls.slider(document, controls, "sun1Side", "sun 1 side (degrees round you: 0 behind you, -90 on your left, 90 on your right, 180 ahead in the picture; or drag it on the dial)", -180, 180, 5, -40);
		height1 = LabControls.slider(document, controls, "sun1Height", "sun 1 height (degrees over the horizon: low, long shadows)", 5, 85, 1, 20);
		second = LabControls.choice(document, controls, "sun2On", "sun 2 (a second light, its own shadow)", OFF_ON, 0);
		side2 = LabControls.slider(document, controls, "sun2Side", "sun 2 side (degrees, as sun 1)", -180, 180, 5, 35);
		height2 = LabControls.slider(document, controls, "sun2Height", "sun 2 height (degrees, as sun 1)", 5, 85, 1, 30);
		rise = LabControls.slider(document, controls, "rise", "rise (how far up the screen the ground climbs for a unit it runs into the picture: low for flat ground seen low, more where the hills rise behind)", 0.05f, 1.5f, 0.05f, 0.5f);
		follows = LabControls.choice(document, controls, "follows", "shadow moves with (as the camera scrolls: the pines keep it under their trees; the hills, as the world would, so the trees slide off it)", FOLLOWS, 0);
		softness = LabControls.slider(document, controls, "softness", "softness (the blur's radius, world units)", 0, 0.8f, 0.01f, 0.1f);
		darkness = LabControls.slider(document, controls, "darkness", "darkness (0 no shadow, 1 black)", 0, 1, 0.05f, 0.5f);
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
		float r = LabControls.read(rise);
		float[] k = shear(LabControls.read(side1), LabControls.read(height1), r);
		readout.setInnerText("the pines (l3) cast on the hills (l2) only. Sun 1: a tree's shadow " + describe(k)
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
		LabKit.panel(0, height, FRAME_WIDTH, FRAME_HEIGHT, FRAME_HEIGHT, 0);
		hearts[PAINTED].render();
		LabKit.panel(FRAME_WIDTH + 4, height, FRAME_WIDTH, FRAME_HEIGHT, FRAME_HEIGHT, 0);
		cast();
		LabKit.endPanels();
	}

	/** B: the back, the hills into the stencil, a shadow per light where it is set, the front. */
	private void cast()
	{
		hearts[BACK].render();
		Gdx.gl.glClearStencil(0);
		Gdx.gl.glClear(GL20.GL_STENCIL_BUFFER_BIT);
		Gdx.gl.glEnable(GL20.GL_STENCIL_TEST);
		Gdx.gl.glStencilFunc(GL20.GL_ALWAYS, 1, 0xff);
		Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE);
		Gdx.gl.glColorMask(false, false, false, false);
		draw(MASK, mask);
		Gdx.gl.glColorMask(true, true, true, true);
		Gdx.gl.glStencilFunc(GL20.GL_EQUAL, 1, 0xff);
		Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_KEEP);
		shade(LabControls.read(side1), LabControls.read(height1));
		if (LabControls.read(second) == 1)
			shade(LabControls.read(side2), LabControls.read(height2));
		Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
		drawLayers(FRONT, camera);
	}

	/**
	 * One sun's shadow: the shadow heart's pines sheared about their foot line, a point h over it moved by h times
	 * {@link #shear}, through the batch's transform; tiled across the wide camera.
	 */
	private void shade(float side, float height)
	{
		float[] k = shear(side, height, LabControls.read(rise));
		if (k[1] <= 0)
			return;
		int i = LabControls.read(follows) == 1 ? SHADOW_ON_HILLS : SHADOW;
		float foot = camera.position.y - WORLD_HEIGHT / 2 + pines[i].getCurrentDistanceY();
		sheared.idt();
		sheared.val[Matrix4.M01] = k[0];
		sheared.val[Matrix4.M03] = -k[0] * foot;
		sheared.val[Matrix4.M11] = k[1];
		sheared.val[Matrix4.M13] = foot - k[1] * foot;
		batch.setTransformMatrix(sheared);
		draw(i, shadow);
		batch.setTransformMatrix(straight);
	}

	/** Heart {@code i}'s layers through {@code program}, set on the batch, the lab's numbers given it. */
	private void draw(int i, ShaderProgram program)
	{
		batch.setShader(program);
		program.bind();
		ParallaxLayer hills = hearts[BACK].parallaxReader.layers.get(HILLS);
		ParallaxLayer caster = pines[SHADOW];
		float texels = caster.getDrawnRegion().getRegionWidth() / (caster.getWidth() * caster.getPackedWidthRatio());
		float soft = LabControls.read(softness) * texels;
		program.setUniformf(program.fetchUniformLocation("u_soft", false), soft / caster.getDrawnRegion().getTexture().getWidth(),
				soft / caster.getDrawnRegion().getTexture().getHeight());
		program.setUniformf(program.fetchUniformLocation("u_strips", false), PINES_PAGE_HEIGHT / PINES_STRIP, 0.5f / PINES_PAGE_HEIGHT);
		// The shadow lies on the hills: as far into the page's fog as they are.
		float fog = ParallaxPageReader.fogOf(backPage.getFogStrength(), ParallaxPageReader.frontSpeedOf(hearts[BACK].parallaxReader.layers), hills);
		Color fogColor = backPage.fogColor;
		program.setUniformf(program.fetchUniformLocation("u_shadow", false), SHADOW_COLOR.r * (1 - fog) + fogColor.r * fog,
				SHADOW_COLOR.g * (1 - fog) + fogColor.g * fog, SHADOW_COLOR.b * (1 - fog) + fogColor.b * fog, LabControls.read(darkness));
		drawLayers(i, i == SHADOW || i == SHADOW_ON_HILLS ? wide : camera);
		batch.setShader(null);
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
	 * The pines as a shadow: their alpha, averaged over a disc of u_soft (in the page's texture), in u_shadow's colour at
	 * u_shadow.a. u_strips: the strips a page height holds, and half a texel: the samples stay in their strip.
	 */
	static final String SHADOW_FRAGMENT = HEAD
			+ "uniform vec2 u_soft;\n"
			+ "uniform vec2 u_strips;\n"
			+ "uniform vec4 u_shadow;\n"
			+ "float at(vec2 uv, float top, float bottom)\n"
			+ "{\n"
			+ "	return texture2D(u_texture, vec2(uv.x, clamp(uv.y, top, bottom))).a;\n"
			+ "}\n"
			+ "void main()\n"
			+ "{\n"
			+ "	float strip = floor(v_texCoords.y * u_strips.x);\n"
			+ "	float top = strip / u_strips.x + u_strips.y, bottom = (strip + 1.0) / u_strips.x - 3.0 * u_strips.y;\n"
			+ "	float a = at(v_texCoords, top, bottom);\n"
			// Two rings of eight round the middle, the outer one at the radius.
			+ "	for (int i = 0; i < 8; i++)\n"
			+ "	{\n"
			+ "		float angle = 0.7853982 * float(i);\n"
			+ "		vec2 ring = vec2(cos(angle), sin(angle)) * u_soft;\n"
			+ "		a += at(v_texCoords + ring, top, bottom) + at(v_texCoords + 0.5 * vec2(-ring.y, ring.x), top, bottom);\n"
			+ "	}\n"
			+ "	gl_FragColor = vec4(u_shadow.rgb, u_shadow.a * a / 17.0 * v_color.a);\n"
			+ "}\n";
}
