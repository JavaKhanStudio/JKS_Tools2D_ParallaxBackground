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
 * the light source is (and are) [...] Prepare a lab for it"): r365's AI forest page (the hills lab's, its hills as they
 * ship), drawn twice by the real reader, side by side: A with its layers' cast shadows, B as painted, under the same sun.
 * <p>
 * The sun is in the sky you see (r425, Simon: "only seems to work when the sun is under the X axis... and it would not be
 * visible in the sky"; the lab before cast from a sun behind the viewer, the only one whose shadows land on a layer
 * further back): drawn behind every layer, so it sinks out of sight behind the mountains, and dragged there. A sun ahead
 * lays the shadows toward you, so each layer casts on the layer just in front of it: the far peaks (l0) on the range
 * (l1), the range on the hills (l2), the hills on the pines (l3); the pines, in front of all, on nothing you see. The
 * one in front stands upright, {@code depth} world units nearer: a ray grazing the caster's ridge has dropped {@code
 * depth} tan(height) / cos(side) by the time it reaches it, and moved {@code depth} tan(side) away from the sun's side
 * ({@link #shadowOf}). So the shadow is the caster's whole mass, its ridge and everything under it, lowered and shifted
 * that much: what of the receiver rises above it is lit, the rest is in the shade. A low sun lowers it little and
 * shades most of the receiver; under the horizon it rises over the ridge and shades it all. As the camera scrolls, a
 * shadow lies on its receiver and moves with it ({@code withReceiver} 1: the caster drawn wider about the view's
 * middle by the receiver's speed over its own, as a ridge twice as far casts a shadow twice its apparent width).
 * <p>
 * Each receiver's opaque part goes into the stencil, then the caster's shadow is drawn where it is set, darkened and
 * blurred, the receiver's fog over it; each pass is a heart holding the whole page with the layers it does not draw
 * pushed far under the view (so the page's front, and its fog, stay the page's). A lab's passes, not an effect: what
 * ships, if anything, waits for Simon's tests (no Enum_ShaderEffect, no stored field). Opened with shadow-lab.html:
 * tools/shadow-lab.sh.
 * <p>
 * {@code &clip=1}: nothing moves by itself, {@code window.shadowLabAct(seconds)} steps every heart at 1/60 s.
 */
public class ShadowLab extends ApplicationAdapter implements LabKit.Clip
{
	static final String PAGE = "hills-lab/forest.jplax";
	/** The hills as they ship (the hills lab's "today", the page's own seed): l2's region. */
	static final String HILLS_REGION = "h_today_7020";
	/** The page's layers, back to front: the far peaks, the range, the hills, the pines. */
	static final int LAYERS = 4, PINES = 3;
	static final float WORLD_WIDTH = FogLab.WORLD_WIDTH, WORLD_HEIGHT = FogLab.WORLD_HEIGHT;
	/** Two panels side by side, each the round's whole 16:9 frame. */
	static final int FRAME_WIDTH = FogLab.FRAME_WIDTH, FRAME_HEIGHT = FogLab.FRAME_HEIGHT;
	/**
	 * Every atlas page here holds strips of 384 px, one above the next with a 1 px gap. The shadow's blur keeps its
	 * samples in the strip it is in, or a strip's opaque foot shades the next one's sky.
	 */
	static final float STRIP = 385;
	/** Where a hidden layer goes: this far under the view, in percent of the world's height. */
	static final float HIDDEN = -10000;
	/**
	 * The sky the sun is placed in: the horizon at the far peaks' foot (world units over the view's bottom), and the
	 * view 90 degrees across, so a degree is about as far as it is in the picture: tan(angle) times {@link #FOCAL}.
	 */
	static final float HORIZON = 8.75f, FOCAL = WORLD_WIDTH / 2;
	/** How far up from a caster's bottom its mass is drawn from: inside every layer's opaque foot (4.4 units here). */
	static final float FOOT = 2;
	/** The sun's disc, world units across, its glow three times that. */
	static final float SUN = 1.4f;
	static final String[] VIEWS = { "the page", "without the pines" };
	static final String[] OFF_ON = { "off", "on" };

	/** The sky (the page's gradients alone); each layer alone, drawn; into the stencil; as a shadow. */
	static final int SKY = 0, DRAWN = 1, MASK = DRAWN + LAYERS, CAST = MASK + LAYERS;
	private final Parallax_Heart[] hearts = new Parallax_Heart[CAST + LAYERS];
	private final WholePage_Model[] pages = new WholePage_Model[hearts.length];
	private OrthographicCamera camera, wide;
	private SpriteBatch batch;
	private ShaderProgram mask, shadow;
	private Texture sun;
	private SelectElement view, second;
	private InputElement scroll, side1, height1, side2, height2, depth, withReceiver, softness, darkness;
	/** How far a shadow may run sideways, world units: the wide camera's margin, at least. */
	static final float REACH = 60;
	private Element readout;
	private boolean clip;

	@Override
	public void create()
	{
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		batch = new SpriteBatch();
		mask = compile(batch.getShader().getVertexShaderSource(), MASK_FRAGMENT, "mask");
		shadow = compile(SHIFT_VERTEX, SHADOW_FRAGMENT, "shadow");
		sun = sunDisc();
		camera = new OrthographicCamera();
		camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
		for (int i = 0; i < hearts.length; i++)
		{
			WholePage_Model page = Utils_Page_Json.loadPage(PAGE);
			page.pageModel.pageList.get(2).regionName = HILLS_REGION;
			int only = i == SKY ? -1 : (i - DRAWN) % LAYERS;
			for (int k = 0; k < LAYERS; k++)
				if (k != only)
					hide(page.pageModel.pageList.get(k));
			hearts[i] = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
			hearts[i].setPage(page);
			pages[i] = page;
			// The stencil's and the shadows' layers draw through the lab's shader, the batch's: no effect of the reader's.
			if (i >= MASK)
				hearts[i].parallaxReader.setLayerEffects(BATCH_SHADER);
		}
		float tile = hearts[CAST].parallaxReader.layers.get(0).getTotalWidth();
		wide = new OrthographicCamera(WORLD_WIDTH + 2 * tile * (float) Math.ceil(REACH / tile), WORLD_HEIGHT);
		buildControls();
		publishSky(WORLD_WIDTH, WORLD_HEIGHT, HORIZON, FOCAL);
		if (clip)
			LabKit.exportClip("shadowLab", this);
	}

	/** What shadow-lab.html's drag needs to find the suns and turn a move into degrees. */
	private static native void publishSky(float width, float height, float horizon, float focal) /*-{
		$wnd.shadowSky = { width: width, height: height, horizon: horizon, focal: focal };
	}-*/;

	/** A sun: a white-gold disc in a soft glow fading to nothing, {@link #SUN} across in a square three times that. */
	private static Texture sunDisc()
	{
		int size = 96;
		Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
		pixmap.setBlending(Pixmap.Blending.None);
		float disc = size / 6f;
		for (int y = 0; y < size; y++)
			for (int x = 0; x < size; x++)
			{
				float r = (float) Math.hypot(x + 0.5f - size / 2f, y + 0.5f - size / 2f);
				float glow = Math.max(0, 1 - (r - disc) / (size / 2f - disc));
				float a = r <= disc ? 1 : Math.min(1, Math.max(0, disc + 1 - r)) + 0.55f * glow * glow;
				pixmap.setColor(1, r <= disc + 1 ? 0.97f : 0.9f, r <= disc + 1 ? 0.86f : 0.62f, Math.min(1, a));
				pixmap.drawPixel(x, y);
			}
		Texture texture = new Texture(pixmap);
		texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
		pixmap.dispose();
		return texture;
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
		LabControls.panelLabels(document, "shadow-labels", "A: shadows", "B: as painted");
		view = LabControls.stageChoice(document, "shadow-labels", "view", "view (the whole page; or without the pines, to see the hills' shadows whole)", VIEWS, 0);
		Element controls = document.getElementById("shadow-controls");
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll (units a second)", -160, 160, 5, 20);
		side1 = LabControls.slider(document, controls, "sun1Side", "sun 1 side (degrees from straight ahead: under 0 left, over 0 right; or drag the sun in the sky)", -40, 40, 1, -20);
		height1 = LabControls.slider(document, controls, "sun1Height", "sun 1 height (degrees over the horizon: low, the shadows climb; under 0, the sun has set and every layer is in the shade)", -8, 30, 0.5f, 21);
		second = LabControls.choice(document, controls, "sun2On", "sun 2 (a second sun, its own shadows)", OFF_ON, 0);
		side2 = LabControls.slider(document, controls, "sun2Side", "sun 2 side (degrees, as sun 1)", -40, 40, 1, 22);
		height2 = LabControls.slider(document, controls, "sun2Height", "sun 2 height (degrees, as sun 1)", -8, 30, 0.5f, 20);
		depth = LabControls.slider(document, controls, "depth", "depth (world units between a layer and the one in front: the farther, the lower its shadow lands)", 0, 40, 0.5f, 6);
		withReceiver = LabControls.slider(document, controls, "withReceiver", "with the layer it falls on (1: a shadow moves with the layer it lies on, as in the world; 0: with the layer casting it)", 0, 1, 0.05f, 1);
		softness = LabControls.slider(document, controls, "softness", "softness (the blur's radius, world units)", 0, 0.8f, 0.01f, 0.15f);
		darkness = LabControls.slider(document, controls, "darkness", "darkness (0 no shadow, 1 black)", 0, 1, 0.05f, 0.45f);
		readout = LabControls.readout(document, controls, "shadow-readout");
	}

	private void applyControls()
	{
		float sc = LabControls.read(scroll);
		for (Parallax_Heart heart : hearts)
			heart.screenSpeedConstantX = sc;
		// Read even while off: a slider shows its value when read.
		LabControls.read(view);
		LabControls.read(withReceiver);
		LabControls.read(softness);
		LabControls.read(darkness);
		float s2 = LabControls.read(side2), h2 = LabControls.read(height2), d = LabControls.read(depth);
		readout.setInnerText("Each layer casts on the layer just in front of it: the far peaks (l0) on the range (l1), the range on the hills (l2), the hills on the pines (l3). Sun 1: "
				+ describe(LabControls.read(side1), LabControls.read(height1), d)
				+ (LabControls.read(second) == 1 ? "; sun 2: " + describe(s2, h2, d) : "") + ". The world is " + round(WORLD_WIDTH) + " units wide.");
	}

	/**
	 * Where a sun at {@code side} degrees from straight ahead (over 0 on the right) and {@code height} over the horizon
	 * lays a layer's shadow on the one {@code depth} units in front of it, {x, y}: the ray over the caster's ridge has
	 * moved away from the sun's side by depth tan(side), and dropped depth tan(height) / cos(side), y under 0. A sun under
	 * the horizon: y over 0, the shadow over the ridge.
	 */
	static float[] shadowOf(float side, float height, float depth)
	{
		double a = Math.toRadians(side);
		return new float[] { (float) (-depth * Math.tan(a)), (float) (-depth * Math.tan(Math.toRadians(height)) / Math.cos(a)) };
	}

	private static String describe(float side, float height, float depth)
	{
		if (height <= 0)
			return "under the horizon: set, every layer in the shade";
		float[] k = shadowOf(side, height, depth);
		return round(Math.abs(side)) + " degrees " + (side < 0 ? "left" : "right") + ", " + round(height)
				+ " up: a ridge's shadow lands " + round(-k[1]) + " units under it and " + round(Math.abs(k[0])) + " " + (k[0] < 0 ? "left" : "right");
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
		boolean pines = LabControls.read(view) == 0;
		LabKit.beginPanels();
		if (LabKit.panel(0, height, FRAME_WIDTH, FRAME_HEIGHT, FRAME_HEIGHT, 0))
		{
			sky();
			for (int k = 0; k < LAYERS; k++)
				if (k != PINES || pines)
				{
					drawLayers(DRAWN + k);
					cast(k);
				}
		}
		if (LabKit.panel(FRAME_WIDTH + 4, height, FRAME_WIDTH, FRAME_HEIGHT, FRAME_HEIGHT, 0))
		{
			sky();
			for (int k = 0; k < LAYERS; k++)
				if (k != PINES || pines)
					drawLayers(DRAWN + k);
		}
		LabKit.endPanels();
	}

	/** The page's gradients, then each sun over the horizon: behind every layer, so the mountains hide a low one. */
	private void sky()
	{
		hearts[SKY].render();
		camera.update();
		batch.setProjectionMatrix(camera.combined);
		batch.enableBlending();
		batch.begin();
		drawSun(LabControls.read(side1), LabControls.read(height1));
		if (LabControls.read(second) == 1)
			drawSun(LabControls.read(side2), LabControls.read(height2));
		batch.end();
	}

	private void drawSun(float side, float height)
	{
		double a = Math.toRadians(side);
		float x = camera.position.x + FOCAL * (float) Math.tan(a);
		float y = camera.position.y - WORLD_HEIGHT / 2 + HORIZON + FOCAL * (float) (Math.tan(Math.toRadians(height)) / Math.cos(a));
		float size = 3 * SUN;
		batch.draw(sun, x - size / 2, y - size / 2, size, size);
	}

	/**
	 * Layer {@code receiver} into the stencil, where it is opaque; then, where it is set, the shadow the layer behind it
	 * casts from each sun (the farthest layer: none, unless a sun has set).
	 */
	private void cast(int receiver)
	{
		Gdx.gl.glClearStencil(0);
		Gdx.gl.glClear(GL20.GL_STENCIL_BUFFER_BIT);
		Gdx.gl.glEnable(GL20.GL_STENCIL_TEST);
		Gdx.gl.glStencilFunc(GL20.GL_ALWAYS, 1, 0xff);
		Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE);
		Gdx.gl.glColorMask(false, false, false, false);
		batch.setShader(mask);
		drawLayers(MASK + receiver);
		batch.setShader(null);
		Gdx.gl.glColorMask(true, true, true, true);
		float d = LabControls.read(depth), h1 = LabControls.read(height1), h2 = LabControls.read(height2);
		shade(receiver, shadowOf(LabControls.read(side1), h1, d), 2, h1 <= 0);
		// Both set: one shade, as dark as one sun's.
		if (LabControls.read(second) == 1 && (h1 > 0 || h2 > 0))
			shade(receiver, shadowOf(LabControls.read(side2), h2, d), 4, h2 <= 0);
		Gdx.gl.glStencilMask(0xff);
		Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
	}

	/**
	 * One sun's shadow on {@code receiver}: the layer behind it shifted by {@code k} ({@link #shadowOf}), wider about the
	 * view's middle by {@code withReceiver} of the receiver's speed over its own, darkened and blurred, in the
	 * receiver's fog; and under it, to the view's bottom, its mass: a ridge's shadow is all that is under it too. Each
	 * pixel once a sun: the stencil's bit {@code drawn}, set where it is drawn, keeps the mass (laid over the caster's
	 * foot, so no seam shows between them) and the wide camera's tiles off what it already shades. A sun under the
	 * horizon ({@code set}) lights nothing: the whole receiver is in the shade (drawn again in its colour), the farthest
	 * layer too.
	 */
	private void shade(int receiver, float[] k, int drawn, boolean set)
	{
		if (receiver == 0 && !set)
			return;
		Gdx.gl.glStencilFunc(GL20.GL_EQUAL, 1, 1 | drawn);
		Gdx.gl.glStencilMask(drawn);
		Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_INVERT);
		int caster = receiver - 1;
		ParallaxLayer onto = hearts[DRAWN + receiver].parallaxReader.layers.get(receiver);
		WholePage_Model page = pages[DRAWN + receiver];
		float fog = ParallaxPageReader.fogOf(page.getFogStrength(), ParallaxPageReader.frontSpeedOf(hearts[DRAWN + receiver].parallaxReader.layers), onto);
		Color fogColor = page.fogColor;
		float r = SHADOW_COLOR.r * (1 - fog) + fogColor.r * fog, g = SHADOW_COLOR.g * (1 - fog) + fogColor.g * fog,
				b = SHADOW_COLOR.b * (1 - fog) + fogColor.b * fog, dark = LabControls.read(darkness);

		batch.setShader(shadow);
		shadow.bind();
		shadow.setUniformf(shadow.fetchUniformLocation("u_shadow", false), r, g, b, dark);
		camera.update();
		wide.position.set(camera.position);
		wide.update();
		batch.setProjectionMatrix(camera.combined);
		batch.enableBlending();
		if (set)
		{
			// The receiver itself in the shade's colour, through its own alpha: its soft edges stay soft (the stencil's
			// would cut the hazy far peaks hard), on every pixel of it whatever the other sun did.
			Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
			shadow.setUniformf(shadow.fetchUniformLocation("u_shift", false), 0, 0, camera.position.x, 1);
			draw(hearts[CAST + receiver], onto, 0, camera);
			Gdx.gl.glEnable(GL20.GL_STENCIL_TEST);
			batch.setShader(null);
			return;
		}
		ParallaxLayer from = hearts[CAST + caster].parallaxReader.layers.get(caster);
		float wider = 1 + LabControls.read(withReceiver) * (onto.getParallaxSpeedRatioX() / from.getParallaxSpeedRatioX() - 1);
		shadow.setUniformf(shadow.fetchUniformLocation("u_shift", false), k[0], k[1], camera.position.x, wider);
		draw(hearts[CAST + caster], from, LabControls.read(softness), wide);
		// The mass under the caster's foot, from inside its opaque foot (FOOT): it goes on where the layer stops. Placed as
		// the caster is: the shader shifts it with it.
		float bottom = camera.position.y - WORLD_HEIGHT / 2;
		float top = bottom + hearts[CAST + caster].parallaxReader.getDrawingHeight() + from.getCurrentDistanceY() + FOOT;
		float under = bottom - 1 - Math.abs(k[1]);
		shadow.setUniformf(shadow.fetchUniformLocation("u_soft", false), 0, 0);
		batch.begin();
		batch.setColor(1, 1, 1, 1);
		batch.draw(sun, camera.position.x - wide.viewportWidth / 2, under, wide.viewportWidth, top - under, 0.5f, 0.5f, 0.5f, 0.5f);
		batch.end();
		batch.setShader(null);
	}

	/**
	 * {@code heart}'s layer {@code layer} through the shadow shader, blurred {@code softness} world units, tiled across
	 * {@code view}: the wide camera for a shifted shadow, the camera's own for one in place (a SEQUENCE's picks run from
	 * its view's left edge: tiled wider, the pines are other pines).
	 */
	private void draw(Parallax_Heart heart, ParallaxLayer layer, float softness, OrthographicCamera view)
	{
		TextureRegion region = layer.getDrawnRegion();
		float texels = region.getRegionWidth() / (layer.getWidth() * layer.getPackedWidthRatio());
		float width = region.getTexture().getWidth(), height = region.getTexture().getHeight();
		shadow.setUniformf(shadow.fetchUniformLocation("u_soft", false), softness * texels / width, softness * texels / height);
		shadow.setUniformf(shadow.fetchUniformLocation("u_strips", false), height / STRIP, 0.5f / height);
		batch.begin();
		heart.parallaxReader.draw(view, batch);
		batch.end();
	}

	/** Heart {@code i}'s layers alone, without its gradients, tiled across the view. */
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
		sun.dispose();
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

	/** A receiver into the stencil: only where it is opaque, so the sky behind it takes no shadow. */
	static final String MASK_FRAGMENT = HEAD
			+ "void main()\n"
			+ "{\n"
			+ "	if (texture2D(u_texture, v_texCoords).a < 0.5)\n"
			+ "		discard;\n"
			+ "	gl_FragColor = vec4(1.0);\n"
			+ "}\n";

	/**
	 * The batch's vertex shader, its corners where the caster's shadow lands ({@link #shadowOf}): u_shift.xy, the shift;
	 * u_shift.w, how much wider about the view's middle (u_shift.z, world x) the shadow is than the caster.
	 */
	static final String SHIFT_VERTEX = "attribute vec4 a_position;\n"
			+ "attribute vec4 a_color;\n"
			+ "attribute vec2 a_texCoord0;\n"
			+ "uniform mat4 u_projTrans;\n"
			+ "uniform vec4 u_shift;\n"

			+ "varying vec4 v_color;\n"
			+ "varying vec2 v_texCoords;\n"
			+ "void main()\n"
			+ "{\n"
			+ "	v_color = a_color;\n"
			+ "	v_color.a = v_color.a * (255.0 / 254.0);\n"
			+ "	v_texCoords = a_texCoord0;\n"
			+ "	vec2 p = vec2(u_shift.z + (a_position.x - u_shift.z) * u_shift.w + u_shift.x, a_position.y + u_shift.y);\n"
			+ "	gl_Position = u_projTrans * vec4(p, 0.0, 1.0);\n"
			+ "}\n";

	/**
	 * The caster as a shadow: its alpha, averaged over a disc of u_soft (in the page's texture), in u_shadow's colour at
	 * u_shadow.a, discarded where it is all but clear. u_strips: the strips a page height holds, and half a texel: the samples stay in their strip.
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
			+ "	float strip = floor(v_texCoords.y * u_strips.x + 0.001);\n"
			+ "	float top = strip / u_strips.x + u_strips.y, bottom = (strip + 1.0) / u_strips.x - 3.0 * u_strips.y;\n"
			+ "	float a = at(v_texCoords, top, bottom);\n"
			// Two rings of eight round the middle, the outer one at the radius.
			+ "	for (int i = 0; i < 8; i++)\n"
			+ "	{\n"
			+ "		float angle = 0.7853982 * float(i);\n"
			+ "		vec2 ring = vec2(cos(angle), sin(angle)) * u_soft;\n"
			+ "		a += at(v_texCoords + ring, top, bottom) + at(v_texCoords + 0.5 * vec2(-ring.y, ring.x), top, bottom);\n"
			+ "	}\n"
			+ "	float alpha = u_shadow.a * a / 17.0 * v_color.a;\n"
			// Nothing drawn sets no stencil: the mass under the caster still goes there.
			+ "	if (alpha < 0.004)\n"
			+ "		discard;\n"
			+ "	gl_FragColor = vec4(u_shadow.rgb, alpha);\n"
			+ "}\n";
}
