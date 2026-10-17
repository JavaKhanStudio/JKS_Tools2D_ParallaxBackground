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
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
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
 * further back): drawn behind every layer (see suns()), so it sinks out of sight behind the mountains,
 * and dragged there. A sun ahead
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
	/** The sky's glow round a sun in {@link #light}, world units across. */
	static final float GLOW = 16;
	static final String[] VIEWS = { "the page", "without the pines" };
	static final String[] OFF_ON = { "off", "on" };
	/**
	 * A whole day (r425, Simon at 16:48: "a button to do a whole day"): sun 1 rises under the horizon at the view's left
	 * edge, {@link #DAY_LOW} degrees, climbs to {@link #DAY_HIGH} over its middle and sets at its right edge, its height
	 * a half sine over the day, its side straight across.
	 */
	static final float DAY_SIDE = 40, DAY_LOW = -6, DAY_HIGH = 30;
	/**
	 * Degrees over the horizon a sun takes to light the page (r425, Simon at 17:19: "the whole thing lights up as a
	 * bugged shader when it appears"): under 0 every layer is in the shade, and over this its cast shadows alone; between
	 * the two, the one fades into the other, where the lab before swapped them in one frame.
	 */
	static final float TWILIGHT = 8;
	/**
	 * The sun behind you (r425, Simon at 19:52: "a button to test without the sun visible (as if it was behind the
	 * camera). It should also affect the way the shadows are cast"): not drawn, and its shadows go away from you, laid on
	 * the ground in perspective ({@link #groundOf}): each layer takes the shadows of every layer in front of it. Simon at
	 * 20:26 ("Sun back does not feel as if it works"): the lab before cast upright copies of each ridge, shifted 10 units
	 * sideways, on the layer just behind; they read as streaks, pointing nowhere.
	 */
	private boolean behind;

	/** The sky (the page's gradients alone); each layer alone, drawn; into the stencil; as a shadow. */
	static final int SKY = 0, DRAWN = 1, MASK = DRAWN + LAYERS, CAST = MASK + LAYERS;
	private final Parallax_Heart[] hearts = new Parallax_Heart[CAST + LAYERS];
	private final WholePage_Model[] pages = new WholePage_Model[hearts.length];
	private OrthographicCamera camera, wide;
	private SpriteBatch batch;
	private ShaderProgram mask, shadow, occlude, rays, relief;
	/**
	 * The light the sun sends past the layers in front of it, A's view at half size: the suns, then the far peaks, the range, the hills
	 * and the pines in black over them ({@link #light}); {@link #RAYS_FRAGMENT} blurs it toward the sun.
	 */
	private FrameBuffer light;
	private Texture sun;
	private SelectElement view, second;
	private InputElement scroll, side1, height1, side2, height2, depth, withReceiver, softness, darkness, liftFrom, liftHigh, rayStrength, rayLength, dayLength, reliefStrength, reliefWidth;
	private Element dayButton, behindButton;
	/** While a day plays: how far through it, 0 sunrise to 1 sunset. */
	private boolean day;
	private float dayAt;
	/**
	 * Sun 1 as drawn: the day's own, unrounded, while one plays, else its sliders. Read through the sliders, a day moved
	 * it in their 1 and 0.5 degree steps, about 7 px at a time, standing still between (Simon at 17:19: "Why is it shaking
	 * so much?").
	 */
	private float side1At, height1At;
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
		occlude = compile(batch.getShader().getVertexShaderSource(), OCCLUDE_FRAGMENT, "occluder");
		rays = compile(batch.getShader().getVertexShaderSource(), RAYS_FRAGMENT, "rays");
		relief = compile(batch.getShader().getVertexShaderSource(), RELIEF_FRAGMENT, "relief");
		light = new FrameBuffer(Pixmap.Format.RGBA8888, FRAME_WIDTH / 2, FRAME_HEIGHT / 2, false);
		light.getColorBufferTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
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
		// The defaults are Simon's (r425, 16 Oct 16:48: "I think we can start around this").
		side1 = LabControls.slider(document, controls, "sun1Side", "sun 1 side (degrees from straight ahead: under 0 left, over 0 right; or drag the sun in the sky)", -40, 40, 1, -40);
		height1 = LabControls.slider(document, controls, "sun1Height", "sun 1 height (degrees over the horizon: low, the shadows climb; under 0, the sun has set and every layer is in the shade; in the sky, over about 34 it is above the picture)", -8, 60, 0.5f, 22.5f);
		second = LabControls.choice(document, controls, "sun2On", "sun 2 (a second sun, its own shadows)", OFF_ON, 0);
		side2 = LabControls.slider(document, controls, "sun2Side", "sun 2 side (degrees, as sun 1)", -40, 40, 1, 22);
		height2 = LabControls.slider(document, controls, "sun2Height", "sun 2 height (degrees, as sun 1)", -8, 60, 0.5f, 20);
		depth = LabControls.slider(document, controls, "depth", "depth (world units between a layer and the one in front: the farther, the lower its shadow lands)", 0, 40, 0.5f, 12);
		withReceiver = LabControls.slider(document, controls, "withReceiver", "with the layer it falls on (1: a shadow moves with the layer it lies on, as in the world; 0: with the layer casting it)", 0, 1, 0.05f, 1);
		softness = LabControls.slider(document, controls, "softness", "softness (the blur's radius, world units)", 0, 0.8f, 0.01f, 0.8f);
		darkness = LabControls.slider(document, controls, "darkness", "darkness (0 no shadow, 1 black)", 0, 1, 0.05f, 0.2f);
		// A high sun lifts the shade (r425, Simon at 19:52: "When the sun is considered high it should also remove overall
		// darkness"): the cast shadows' darkness eases from its full under liftFrom to none from liftHigh up.
		liftFrom = LabControls.slider(document, controls, "liftFrom", "shade lifts from (degrees: under this sun height the shadows are as dark as darkness says)", 0, 60, 0.5f, 15);
		liftHigh = LabControls.slider(document, controls, "liftHigh", "high sun (degrees: from this sun height up, no shade is left)", 0, 90, 0.5f, 40);
		// The sun coming out from behind the range, and going back (r425, Simon at 20:26: "it should have some effect").
		rayStrength = LabControls.slider(document, controls, "rays", "rays (the light the sun sends past the ridges in front of it, in A: rays round a ridge it is behind, a glare when it is clear; 0 none)", 0, 2, 0.05f, 0.8f);
		rayLength = LabControls.slider(document, controls, "rayLength", "ray length (how far across the view the rays reach from the sun)", 0.1f, 1, 0.05f, 0.7f);
		// The sun behind you lights each layer's own faces (r425, Simon at 23:35: "Sun behind is still very bad"): its cast
		// shadows fall behind what casts them, out of sight; what shows where it is, is which flanks it lights.
		reliefStrength = LabControls.slider(document, controls, "relief", "relief (with the sun behind you: each layer's flanks facing the sun lit, the others in the shade; 0 none)", 0, 1, 0.05f, 0.6f);
		reliefWidth = LabControls.slider(document, controls, "reliefWidth", "relief width (world units in from a layer's edge its flanks reach: small, a rim; large, whole peaks)", 0.1f, 4, 0.05f, 2.5f);
		dayLength = LabControls.slider(document, controls, "dayLength", "day length (seconds the button's day lasts, sunrise to sunset)", 5, 60, 1, 20);
		dayButton = document.createButtonElement();
		dayButton.setId("shadow-day");
		dayButton.setClassName("lab-copy shadow-day");
		controls.appendChild(dayButton);
		onClick(dayButton, this, false);
		showDay();
		behindButton = document.createButtonElement();
		behindButton.setId("shadow-behind");
		behindButton.setClassName("lab-copy shadow-day");
		controls.appendChild(behindButton);
		onClick(behindButton, this, true);
		showBehind();
		readout = LabControls.readout(document, controls, "shadow-readout");
	}

	private static native void onClick(Element element, ShadowLab lab, boolean behind) /*-{
		element.addEventListener('click', $entry(function() {
			if (behind)
				lab.@jks.tools2d.parallax.browsertest.ShadowLab::toggleBehind()();
			else
				lab.@jks.tools2d.parallax.browsertest.ShadowLab::toggleDay()();
		}));
	}-*/;

	/** The sun behind you, or back in the sky. */
	void toggleBehind()
	{
		behind = !behind;
		showBehind();
	}

	/** The button says what a press does; window.shadowBehind tells the drag and Copy settings where the sun is. */
	private void showBehind()
	{
		behindButton.setInnerText(behind ? "\u2600 sun back in the sky" : "\u2600 sun behind you");
		publishBehind(behind);
	}

	private static native void publishBehind(boolean behind) /*-{
		$wnd.shadowBehind = behind;
	}-*/;

	/** The day's button: a day from sunrise, or the one playing stopped where its sun is. */
	void toggleDay()
	{
		day = !day;
		if (day)
			dayAt = 0;
		showDay();
	}

	private void showDay()
	{dayButton.setInnerText(day ? "\u25A0 stop the day" : "\u25B6 play a whole day");}

	/** A playing day moved on by {@code seconds}: sun 1's sliders set where the day has it, so the drag and Copy see it. */
	private void stepDay(float seconds)
	{
		if (!day)
			return;
		dayAt = Math.min(1, dayAt + seconds / LabControls.read(dayLength));
		side1At = -DAY_SIDE + 2 * DAY_SIDE * dayAt;
		height1At = DAY_LOW + (DAY_HIGH - DAY_LOW) * (float) Math.sin(Math.PI * dayAt);
		side1.setValue(LabControls.text(side1At));
		height1.setValue(LabControls.text(height1At));
		if (dayAt >= 1)
		{
			day = false;
			showDay();
		}
	}

	private void applyControls()
	{
		float sc = LabControls.read(scroll);
		if (!day)
		{
			side1At = LabControls.read(side1);
			height1At = LabControls.read(height1);
		}
		for (Parallax_Heart heart : hearts)
			heart.screenSpeedConstantX = sc;
		// Read even while off: a slider shows its value when read.
		LabControls.read(view);
		LabControls.read(withReceiver);
		LabControls.read(softness);
		LabControls.read(darkness);
		LabControls.read(dayLength);
		LabControls.read(liftFrom);
		LabControls.read(liftHigh);
		LabControls.read(rayStrength);
		LabControls.read(rayLength);
		LabControls.read(reliefStrength);
		LabControls.read(reliefWidth);
		float s2 = LabControls.read(side2), h2 = LabControls.read(height2), d = LabControls.read(depth);
		readout.setInnerText((behind
				? "The sun is behind you: the shadows lie on the ground away from you, on every layer behind the one casting them, and each layer's flanks turned toward it are lit, the others in the shade. Sun 1: "
				: "Each layer casts on the layer just in front of it: the far peaks (l0) on the range (l1), the range on the hills (l2), the hills on the pines (l3). Sun 1: ")
				+ ""
				+ describe(side1At, height1At, d)
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

	/**
	 * A sun behind you, {@code side} degrees round from straight behind (under 0 on your left) and {@code height} up: its
	 * light goes away from you, (-sin side cos height, -sin height, cos side cos height), x right, y up, z into the
	 * picture. Each layer stands upright on a flat ground at its foot, the ground meeting the sky at {@link #HORIZON}, the
	 * view {@link #FOCAL} units from the eye. A point of a layer {@code up} units over its foot has its shadow on the ground
	 * where that light takes it: in the picture, (x + {kx} up, H - (H - foot) / w), w = 1 + {kz} up, x from the view's
	 * middle, H the horizon. A plane to a plane: the shadow vertex shader draws it with w as the vertex's own, so the
	 * caster's image lies on the ground in perspective. A tree's shadow runs from its foot toward the point of the
	 * horizon straight away from the sun, {@code FOCAL} tan(-side) right of the middle: a low sun's long, nearly to it;
	 * a high sun's short, behind the tree.
	 */
	static float[] groundOf(float side, float height)
	{
		double a = Math.toRadians(side), h = Math.toRadians(height);
		double ly = Math.sin(h);
		return new float[] { (float) (-Math.sin(a) * Math.cos(h) / ly), (float) (Math.cos(a) * Math.cos(h) / (ly * FOCAL)) };
	}

	/** How far into the night a sun {@code height} degrees up has the page: 1 set, 0 from {@link #TWILIGHT} up. */
	static float dusk(float height)
	{return Math.max(0, Math.min(1, 1 - height / TWILIGHT));}

	/** How much of the cast shadows' darkness a sun {@code height} degrees up leaves: 1 under {@code from}, 0 from {@code high} up, eased between. */
	static float lift(float height, float from, float high)
	{
		if (high <= from)
			return height < high ? 1 : 0;
		float t = Math.max(0, Math.min(1, (height - from) / (high - from)));
		return 1 - t * t * (3 - 2 * t);
	}

	private String describe(float side, float height, float depth)
	{
		if (height <= 0)
			return "under the horizon: set, every layer in the shade";
		if (behind)
		{
			// The top of a pine 13 units over its foot at the view's bottom.
			float[] g = groundOf(side, height);
			float w = 1 + g[1] * 13;
			return round(Math.abs(side)) + " degrees " + (side < 0 ? "left" : "right") + " behind you, " + round(height)
					+ " up: the shadows run toward the horizon " + round(Math.abs(FOCAL * (float) Math.tan(Math.toRadians(side)))) + " units " + (side < 0 ? "right" : "left")
					+ " of the middle; a pine 13 units tall lays one " + round(Math.abs(13 * g[0] / w)) + " across and " + round(HORIZON - HORIZON / w) + " up the picture, "
					+ Math.round(100 * lift(height, LabControls.read(liftFrom), LabControls.read(liftHigh))) + "% dark";
		}
		float[] k = shadowOf(side, height, depth);
		return round(Math.abs(side)) + " degrees " + (side < 0 ? "left" : "right") + ", " + round(height)
				+ " up: a ridge's shadow lands " + round(Math.abs(k[1])) + (k[1] <= 0 ? " units under it and " : " units over it and ") + round(Math.abs(k[0])) + " " + (k[0] < 0 ? "left" : "right")
				+ ", " + Math.round(100 * lift(height, LabControls.read(liftFrom), LabControls.read(liftHigh))) + "% dark";
	}

	private static float round(float value)
	{return Math.round(value * 100) / 100f;}

	@Override
	public void act(float seconds)
	{
		stepDay(seconds);
		applyControls();
		LabKit.clip(seconds, hearts);
	}

	@Override
	public void render()
	{
		if (!clip)
		{
			float delta = LabKit.liveDelta();
			stepDay(delta);
			applyControls();
			LabKit.act(delta, hearts);
		}
		int height = Gdx.graphics.getBackBufferHeight();
		boolean pines = LabControls.read(view) == 0;
		boolean lit = !behind && LabControls.read(rayStrength) > 0;
		if (lit)
			light(pines);
		LabKit.beginPanels();
		if (LabKit.panel(0, height, FRAME_WIDTH, FRAME_HEIGHT, FRAME_HEIGHT, 0))
		{
			hearts[SKY].render();
			suns();
			for (int k = 0; k < LAYERS; k++)
				if (k != PINES || pines)
				{
					drawLayers(DRAWN + k);
					relief(k);
					cast(k);
				}
			if (lit)
				rays();
		}
		if (LabKit.panel(FRAME_WIDTH + 4, height, FRAME_WIDTH, FRAME_HEIGHT, FRAME_HEIGHT, 0))
		{
			hearts[SKY].render();
			suns();
			for (int k = 0; k < LAYERS; k++)
				if (k != PINES || pines)
					drawLayers(DRAWN + k);
		}
		LabKit.endPanels();
	}

	/**
	 * Each sun, over the sky and behind every layer: the farthest peaks (l0) hide a low one first (Simon at 23:35: "you
	 * did not show the sun behind the last layer but the one before that"; bac21de had put it over l0, whose peaks are
	 * hazed almost to the sky's colour, for the bites they cut in it).
	 */
	private void suns()
	{
		if (behind)
			return;
		camera.update();
		batch.setProjectionMatrix(camera.combined);
		batch.enableBlending();
		batch.begin();
		drawSun(side1At, height1At);
		if (LabControls.read(second) == 1)
			drawSun(LabControls.read(side2), LabControls.read(height2));
		batch.end();
	}

	/**
	 * Into {@link #light}: the suns in their colour on black, then every layer in front of them (the far peaks, the range, the hills, the
	 * pines when shown) in black over them, through its alpha: what light reaches the eye from the suns, at half size.
	 */
	private void light(boolean pines)
	{
		light.begin();
		Gdx.gl.glClearColor(0, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		camera.update();
		batch.setProjectionMatrix(camera.combined);
		batch.enableBlending();
		batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
		batch.begin();
		lightSun(side1At, height1At);
		if (LabControls.read(second) == 1)
			lightSun(LabControls.read(side2), LabControls.read(height2));
		batch.end();
		batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		// The stencil's hearts: they draw through the batch's shader, this one (the drawn ones through the page fog's).
		batch.setShader(occlude);
		for (int k = 0; k < LAYERS; k++)
			if (k != PINES || pines)
				drawLayers(MASK + k);
		batch.setShader(null);
		light.end();
	}

	/**
	 * Over A, added to it: {@link #light} blurred toward sun 1 (the higher sun when two are on), so what of the suns
	 * shows streams out from it, darker where a ridge is between: rays round a ridge the sun is just behind, a glare round
	 * it when it is clear, nothing once it is deep behind the range.
	 */
	private void rays()
	{
		boolean two = LabControls.read(second) == 1 && LabControls.read(height2) > height1At;
		float side = two ? LabControls.read(side2) : side1At, high = two ? LabControls.read(height2) : height1At;
		double a = Math.toRadians(side);
		float x = WORLD_WIDTH / 2 + FOCAL * (float) Math.tan(a);
		float y = HORIZON + FOCAL * (float) (Math.tan(Math.toRadians(high)) / Math.cos(a));
		camera.update();
		batch.setProjectionMatrix(camera.combined);
		batch.setShader(rays);
		batch.enableBlending();
		batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE);
		batch.begin();
		rays.setUniformf(rays.fetchUniformLocation("u_sun", false), x / WORLD_WIDTH, y / WORLD_HEIGHT);
		rays.setUniformf(rays.fetchUniformLocation("u_rays", false), LabControls.read(rayStrength), LabControls.read(rayLength));
		float left = camera.position.x - WORLD_WIDTH / 2, bottom = camera.position.y - WORLD_HEIGHT / 2;
		batch.draw(light.getColorBufferTexture(), left, bottom, WORLD_WIDTH, WORLD_HEIGHT, 0, 0, 1, 1);
		batch.end();
		batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		batch.setShader(null);
	}

	/**
	 * A sun's light into {@link #light}: its disc, and the sky's glow round it, {@link #GLOW} across and dim, which the
	 * ridges in front cut into rays while the disc is behind one.
	 */
	private void lightSun(float side, float height)
	{
		double a = Math.toRadians(side);
		float x = camera.position.x + FOCAL * (float) Math.tan(a);
		float y = camera.position.y - WORLD_HEIGHT / 2 + HORIZON + FOCAL * (float) (Math.tan(Math.toRadians(height)) / Math.cos(a));
		batch.setColor(1, 1, 1, 0.45f);
		batch.draw(sun, x - GLOW / 2, y - GLOW / 2, GLOW, GLOW);
		batch.setColor(1, 1, 1, 1);
		drawSun(side, height);
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
	 * With the sun behind you, layer {@code k}'s own faces in its light: a flank turned toward it lit, warm, one turned
	 * away in the shade, each in the layer's fog. The flanks are read from the layer's ridge line ({@link
	 * #RELIEF_FRAGMENT}): a face leans as the ridge over it climbs, so a peak's flank on the sun's side is lit, the other
	 * in the shade, down to its foot.
	 */
	private void relief(int k)
	{
		float strength = LabControls.read(reliefStrength), h = height1At;
		if (!behind || strength <= 0 || h <= 0)
			return;
		double a = Math.toRadians(side1At), up = Math.toRadians(h);
		ParallaxLayer layer = hearts[CAST + k].parallaxReader.layers.get(k);
		WholePage_Model page = pages[DRAWN + k];
		float fog = ParallaxPageReader.fogOf(page.getFogStrength(), ParallaxPageReader.frontSpeedOf(hearts[DRAWN + k].parallaxReader.layers), hearts[DRAWN + k].parallaxReader.layers.get(k));
		Color f = page.fogColor;
		camera.update();
		batch.setProjectionMatrix(camera.combined);
		batch.enableBlending();
		batch.setShader(relief);
		relief.bind();
		relief.setUniformf(relief.fetchUniformLocation("u_sun", false), (float) (Math.sin(a) * Math.cos(up)), (float) Math.sin(up), (float) (Math.cos(a) * Math.cos(up)));
		relief.setUniformf(relief.fetchUniformLocation("u_shade", false), SHADOW_COLOR.r * (1 - fog) + f.r * fog, SHADOW_COLOR.g * (1 - fog) + f.g * fog, SHADOW_COLOR.b * (1 - fog) + f.b * fog, strength * (1 - dusk(h)));
		relief.setUniformf(relief.fetchUniformLocation("u_lit", false), SUNLIT.r * (1 - fog) + f.r * fog, SUNLIT.g * (1 - fog) + f.g * fog, SUNLIT.b * (1 - fog) + f.b * fog, 1);
		draw(relief, hearts[CAST + k], layer, LabControls.read(reliefWidth), camera);
		batch.setShader(null);
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
		float d = LabControls.read(depth), h1 = height1At, h2 = LabControls.read(height2);
		boolean two = LabControls.read(second) == 1;
		// Night: every layer in the shade, as dark as one sun's, while every sun on is set; it fades over TWILIGHT
		// degrees as the higher sun rises, and that sun's cast shadows fade in.
		float night = dusk(two ? Math.max(h1, h2) : h1);
		if (night > 0)
			shade(receiver, -1, null, 0, night);
		float from = LabControls.read(liftFrom), high = LabControls.read(liftHigh);
		if (h1 > 0)
			sun(receiver, side1At, h1, d, 2, (1 - dusk(h1)) * lift(h1, from, high));
		if (two && h2 > 0)
			sun(receiver, LabControls.read(side2), h2, d, 4, (1 - dusk(h2)) * lift(h2, from, high));
		Gdx.gl.glStencilMask(0xff);
		Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
	}

	/**
	 * One sun's shadows on {@code receiver}: ahead, the layer behind it's ({@link #shadowOf}); behind you, every layer in
	 * front of it laid on the ground ({@link #groundOf}), each pixel once ({@code drawn}).
	 */
	private void sun(int receiver, float side, float height, float depth, int drawn, float weight)
	{
		if (!behind)
		{
			shade(receiver, receiver - 1, shadowOf(side, height, depth), drawn, weight);
			return;
		}
		float[] g = groundOf(side, height);
		for (int caster = LAYERS - 1; caster > receiver; caster--)
			shade(receiver, caster, g, drawn, weight);
	}

	/**
	 * One sun's shadow on {@code receiver} from {@code caster}, a sun ahead's: the layer behind it shifted by {@code k} ({@link #shadowOf}), wider about the
	 * view's middle by {@code withReceiver} of the receiver's speed over its own, darkened and blurred, in the
	 * receiver's fog; and under it, to the view's bottom, its mass: a ridge's shadow is all that is under it too. Each
	 * pixel once a sun: the stencil's bit {@code drawn}, set where it is drawn, keeps the mass (laid over the caster's
	 * foot, so no seam shows between them) and the wide camera's tiles off what it already shades. No {@code k}: the night,
	 * the whole receiver in the shade (drawn again in its colour), the farthest layer too. Its darkness times
	 * {@code weight}, the twilight's fade.
	 */
	private void shade(int receiver, int caster, float[] k, int drawn, float weight)
	{
		boolean set = k == null;
		if (!set && (caster < 0 || caster >= LAYERS || caster == PINES && LabControls.read(view) != 0) || weight <= 0)
			return;
		Gdx.gl.glStencilFunc(GL20.GL_EQUAL, 1, 1 | drawn);
		Gdx.gl.glStencilMask(drawn);
		Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_INVERT);
		ParallaxLayer onto = hearts[DRAWN + receiver].parallaxReader.layers.get(receiver);
		WholePage_Model page = pages[DRAWN + receiver];
		float fog = ParallaxPageReader.fogOf(page.getFogStrength(), ParallaxPageReader.frontSpeedOf(hearts[DRAWN + receiver].parallaxReader.layers), onto);
		Color fogColor = page.fogColor;
		float r = SHADOW_COLOR.r * (1 - fog) + fogColor.r * fog, g = SHADOW_COLOR.g * (1 - fog) + fogColor.g * fog,
				b = SHADOW_COLOR.b * (1 - fog) + fogColor.b * fog, dark = weight * LabControls.read(darkness);

		batch.setShader(shadow);
		shadow.bind();
		shadow.setUniformf(shadow.fetchUniformLocation("u_shadow", false), r, g, b, dark);
		shadow.setUniformf(shadow.fetchUniformLocation("u_ground", false), 0, 0, 0, 0);
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
		float bottom = camera.position.y - WORLD_HEIGHT / 2;
		if (behind)
		{
			// Laid on the ground from the caster's foot, its tiles' bottom edge: no mass under it, the caster's own body
			// hides the ground there.
			float foot = bottom + hearts[CAST + caster].parallaxReader.getDrawingHeight() + from.getCurrentDistanceY();
			shadow.setUniformf(shadow.fetchUniformLocation("u_ground", false), k[0], k[1], foot, 1);
			shadow.setUniformf(shadow.fetchUniformLocation("u_horizon", false), bottom + HORIZON, camera.position.x);
			draw(hearts[CAST + caster], from, LabControls.read(softness), wide);
			batch.setShader(null);
			return;
		}
		float wider = 1 + LabControls.read(withReceiver) * (onto.getParallaxSpeedRatioX() / from.getParallaxSpeedRatioX() - 1);
		shadow.setUniformf(shadow.fetchUniformLocation("u_shift", false), k[0], k[1], camera.position.x, wider);
		draw(hearts[CAST + caster], from, LabControls.read(softness), wide);
		// The mass under the caster's foot, from inside its opaque foot (FOOT): it goes on where the layer stops. Placed as
		// the caster is: the shader shifts it with it.
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
	{draw(shadow, heart, layer, softness, view);}

	/** As {@link #draw(Parallax_Heart, ParallaxLayer, float, OrthographicCamera)}, through {@code program}. */
	private void draw(ShaderProgram program, Parallax_Heart heart, ParallaxLayer layer, float softness, OrthographicCamera view)
	{
		TextureRegion region = layer.getDrawnRegion();
		float texels = region.getRegionWidth() / (layer.getWidth() * layer.getPackedWidthRatio());
		float width = region.getTexture().getWidth(), height = region.getTexture().getHeight();
		program.setUniformf(program.fetchUniformLocation("u_soft", false), softness * texels / width, softness * texels / height);
		program.setUniformf(program.fetchUniformLocation("u_strips", false), height / STRIP, 0.5f / height);
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
		occlude.dispose();
		rays.dispose();
		relief.dispose();
		light.dispose();
		sun.dispose();
	}

	/** The shadow's colour out of the fog: a blue black, as the shade of a bright sky's light. */
	static final Color SHADOW_COLOR = new Color(0.04f, 0.06f, 0.12f, 1);

	/** A face in the sun's light: a warm white. */
	static final Color SUNLIT = new Color(1, 0.92f, 0.76f, 1);

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

	/** A layer in front of the suns into {@link #light}: black, through its alpha. */
	static final String OCCLUDE_FRAGMENT = HEAD
			+ "void main()\n"
			+ "{\n"
			+ "	gl_FragColor = vec4(0.0, 0.0, 0.0, texture2D(u_texture, v_texCoords).a);\n"
			+ "}\n";

	/**
	 * {@link #light} blurred toward the sun, u_sun in the view's 0 to 1: 48 samples along the line from the pixel to it,
	 * each weaker than the one before, over u_rays.y of the way; times u_rays.x, warm, added to what is drawn.
	 */
	static final String RAYS_FRAGMENT = HEAD
			+ "uniform vec2 u_sun;\n"
			+ "uniform vec2 u_rays;\n"
			+ "void main()\n"
			+ "{\n"
			+ "	vec2 step = (v_texCoords - u_sun) * u_rays.y / 48.0;\n"
			+ "	vec2 at = v_texCoords;\n"
			+ "	float weight = 1.0;\n"
			+ "	vec3 sum = vec3(0.0);\n"
			+ "	for (int i = 0; i < 48; i++)\n"
			+ "	{\n"
			+ "		at -= step;\n"
			+ "		sum += texture2D(u_texture, at).rgb * weight;\n"
			+ "		weight *= 0.985;\n"
			+ "	}\n"
			+ "	gl_FragColor = vec4(sum / 48.0 * u_rays.x * vec3(1.0, 0.88, 0.66), 1.0);\n"
			+ "}\n";

	/**
	 * A layer's faces in the sun's light, over it, as a hill shade: the layer is a mass under its ridge line, and a face
	 * leans the way that line climbs over it. Above the pixel and u_soft.x to each side (the page's texture), the ridge
	 * is found by walking up its column until the layer is clear (kept in the strip, as the shadow's); its slope is how
	 * much higher it stands on one side than the other. The face leans against it, and up a little; u_sun the way to
	 * the sun (z toward you). A face turned to it more than one facing you is lit, toward u_lit, one turned less in
	 * u_shade, both by up to u_shade.a, fading over u_soft.y (the ridge's own height in v) under the ridge.
	 */
	static final String RELIEF_FRAGMENT = HEAD
			+ "uniform vec2 u_soft;\n"
			+ "uniform vec2 u_strips;\n"
			+ "uniform vec3 u_sun;\n"
			+ "uniform vec4 u_shade;\n"
			+ "uniform vec4 u_lit;\n"
			+ "float at(vec2 uv, float top, float bottom)\n"
			+ "{\n"
			+ "	return texture2D(u_texture, vec2(uv.x, clamp(uv.y, top, bottom))).a;\n"
			+ "}\n"
			+ "void main()\n"
			+ "{\n"
			+ "	float strip = floor(v_texCoords.y * u_strips.x + 0.001);\n"
			+ "	float top = strip / u_strips.x + u_strips.y, bottom = (strip + 1.0) / u_strips.x - 3.0 * u_strips.y;\n"
			+ "	float here = at(v_texCoords, top, bottom) * v_color.a;\n"
			+ "	if (here < 0.004)\n"
			+ "		discard;\n"
			// Four rings of twelve, out to u_soft: the way into the mass, the farther rings weighing as much as the near.
			+ "	vec2 into = vec2(0.0);\n"
			+ "	for (int r = 1; r <= 4; r++)\n"
			+ "		for (int i = 0; i < 12; i++)\n"
			+ "		{\n"
			+ "			float angle = 0.5235988 * (float(i) + 0.5 * float(r));\n"
			+ "			vec2 ring = vec2(cos(angle), sin(angle));\n"
			+ "			into += ring * at(v_texCoords + ring * u_soft * float(r) / 4.0, top, bottom);\n"
			+ "		}\n"
			// Across a straight edge the 48 add up to about 48 / pi: 1 there, less deeper in. A texture's v runs down.
			+ "	vec2 away = -vec2(into.x, -into.y) / 15.3;\n"
			+ "	vec3 face = normalize(vec3(1.5 * away, 1.0)), facing = vec3(0.0, 0.0, 1.0);\n"
			+ "	float turn = dot(face, u_sun) - dot(facing, u_sun);\n"
			+ "	float amount = clamp(1.5 * abs(turn), 0.0, 1.0) * u_shade.a * here;\n"
			+ "	gl_FragColor = turn > 0.0 ? vec4(u_lit.rgb, 0.6 * amount) : vec4(u_shade.rgb, amount);\n"
			+ "}\n";

	/**
	 * The batch's vertex shader, its corners where the caster's shadow lands ({@link #shadowOf}): u_shift.xy, the shift;
	 * u_shift.w, how much wider about the view's middle (u_shift.z, world x) the shadow is than the caster. With
	 * u_ground.w 1, the sun behind you's ({@link #groundOf}): u_ground.xy its kx and kz, u_ground.z the caster's foot,
	 * u_horizon the horizon's y and the view's middle; the vertex's w is the ground's, so the image is laid in perspective.
	 */
	static final String SHIFT_VERTEX = "attribute vec4 a_position;\n"
			+ "attribute vec4 a_color;\n"
			+ "attribute vec2 a_texCoord0;\n"
			+ "uniform mat4 u_projTrans;\n"
			+ "uniform vec4 u_shift;\n"
			+ "uniform vec4 u_ground;\n"
			+ "uniform vec2 u_horizon;\n"

			+ "varying vec4 v_color;\n"
			+ "varying vec2 v_texCoords;\n"
			+ "void main()\n"
			+ "{\n"
			+ "	v_color = a_color;\n"
			+ "	v_color.a = v_color.a * (255.0 / 254.0);\n"
			+ "	v_texCoords = a_texCoord0;\n"
			+ "	if (u_ground.w > 0.5)\n"
			+ "	{\n"
			+ "		float up = a_position.y - u_ground.z, w = 1.0 + u_ground.y * up;\n"
			+ "		vec2 g = vec2(u_horizon.y * w + (a_position.x - u_horizon.y) + u_ground.x * up, u_horizon.x * w - (u_horizon.x - u_ground.z));\n"
			+ "		gl_Position = u_projTrans * vec4(g, 0.0, w);\n"
			+ "		return;\n"
			+ "	}\n"
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
