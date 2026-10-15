package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
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
 * as they ship), drawn twice by the real reader: A as painted, B with the pines (l3) casting on the hills (l2).
 * <p>
 * B is drawn in passes, each by a heart holding the whole page with the layers it does not draw pushed far under the
 * view (so the page's front, and its fog, stay the page's): the sky and the layers up to the hills; the hills again,
 * into the stencil only, where they are opaque; the pines once per light, moved away from it by the shadow's length,
 * dark, softened, where the stencil is set; the pines themselves on top. The shadow is the pines' own layer, so it
 * scrolls with them. A lab's passes, not an effect: what ships, if anything, waits for Simon's tests (no
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

	/** A: the page as painted. B's passes: the back, the stencil, the shadows, the front. */
	static final int PAINTED = 0, BACK = 1, MASK = 2, SHADOW = 3, FRONT = 4;
	private final Parallax_Heart[] hearts = new Parallax_Heart[5];
	private OrthographicCamera camera;
	private SpriteBatch batch;
	private ShaderProgram mask, shadow;
	private SelectElement view, second;
	private InputElement scroll, from1, from2, length, softness, darkness;
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
			if (i == MASK || i == SHADOW)
				hearts[i].parallaxReader.setLayerEffects(BATCH_SHADER);
		}
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
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll (units a second: the shadow moves with the pines)", -160, 160, 5, 20);
		from1 = LabControls.slider(document, controls, "light1", "light 1 comes from (degrees around the view: 0 the right, 90 above, 180 the left, 270 below)", 0, 355, 5, 215);
		second = LabControls.choice(document, controls, "light2On", "light 2 (a second light, its own shadow)", OFF_ON, 0);
		from2 = LabControls.slider(document, controls, "light2", "light 2 comes from (degrees, as light 1)", 0, 355, 5, 325);
		length = LabControls.slider(document, controls, "length", "shadow length (world units: a lower light casts it further)", 0, 4, 0.05f, 1);
		softness = LabControls.slider(document, controls, "softness", "softness (the blur's radius, world units)", 0, 0.8f, 0.01f, 0.12f);
		darkness = LabControls.slider(document, controls, "darkness", "darkness (0 no shadow, 1 black)", 0, 1, 0.05f, 0.45f);
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
		// Read even while off: a slider shows its value when read.
		float second = LabControls.read(from2);
		for (Parallax_Heart heart : hearts)
			heart.screenSpeedConstantX = sc;
		ParallaxLayer hills = hearts[BACK].parallaxReader.layers.get(HILLS);
		float px = hills.getDrawnRegion().getRegionWidth() / (hills.getWidth() * hills.getPackedWidthRatio());
		readout.setInnerText("the pines (l3) cast on the hills (l2) only: shadow " + round(LabControls.read(length)) + " units long ("
				+ Math.round(LabControls.read(length) * px) + " px of the hills' image), blur " + Math.round(LabControls.read(softness) * px)
				+ " px; light 1 from " + Math.round(LabControls.read(from1)) + " degrees"
				+ (LabControls.read(this.second) == 1 ? ", light 2 from " + Math.round(second) + " degrees" : "")
				+ ". The world is " + round(WORLD_WIDTH) + " units wide.");
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
		shade(LabControls.read(from1));
		if (LabControls.read(second) == 1)
			shade(LabControls.read(from2));
		Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
		drawLayers(FRONT);
	}

	/** One light's shadow: the shadow heart's pines moved away from the light by the shadow's length, and back. */
	private void shade(float from)
	{
		float away = (float) Math.toRadians(from + 180), d = LabControls.read(length);
		float dx = d * (float) Math.cos(away) * 100 / WORLD_WIDTH, dy = d * (float) Math.sin(away) * 100 / WORLD_HEIGHT;
		ParallaxLayer caster = pines[SHADOW];
		float x = caster.getDecalPercentX(), y = caster.getDecalPercentY();
		caster.setDecalPercentX(x + dx);
		caster.setDecalPercentY(y + dy);
		draw(SHADOW, shadow);
		caster.setDecalPercentX(x);
		caster.setDecalPercentY(y);
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
		drawLayers(i);
		batch.setShader(null);
	}

	/** Heart {@code i}'s layers alone, without its gradients. */
	private void drawLayers(int i)
	{
		camera.update();
		batch.setProjectionMatrix(camera.combined);
		batch.enableBlending();
		batch.begin();
		hearts[i].parallaxReader.draw(camera, batch);
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
