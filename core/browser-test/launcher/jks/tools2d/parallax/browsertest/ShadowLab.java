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
 * shades most of the receiver. As the camera scrolls, a
 * shadow lies on its receiver and moves with it ({@code withReceiver} 1: the caster drawn wider about the view's
 * middle by the receiver's speed over its own, as a ridge twice as far casts a shadow twice its apparent width).
 * <p>
 * The light is an hour's (r425, Simon at 11:08: "higher when there is no sun, way lower when the sun is at the zenith
 * [...] not in a linear fashion [...] color [...] one sun and one moon cycle"): the sun and the moon on their paths,
 * the light's lux, colour and direct share from the measured clear-sky day ({@link Light}, docs/daylight.md). In A, every
 * layer is tinted by it and the sky and the fog lit by it; each cast shadow keeps what is left without its body's
 * direct light.
 * <p>
 * Each receiver's opaque part goes into the stencil, then the caster's shadow is drawn where it is set, as a product,
 * blurred, the receiver's fog kept; each pass is a heart holding the whole page with the layers it does not draw
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
	/**
	 * A day of 24 hours (r425, Simon at 16:48: "a button to do a whole day"; at 11:08: "We will need one sun and one moon
	 * cycle, and both produce light that affects the world"): the sun rises at the view's left edge at 6, is
	 * {@code noonHeight} up over its middle at 12 and sets at its right edge at 18, its height {@code noonHeight}
	 * sin(angle), its side {@code -DAY_SIDE} cos(angle), the angle 0 at 6; under the ground at night, as low at midnight.
	 * The moon goes the same way, its angle behind the sun's by its age over {@link #MONTH}: full, it rises at 18.
	 */
	static final float DAY_SIDE = 40, MONTH = 29.53f;
	/**
	 * The sun behind you (r425, Simon at 19:52: "a button to test without the sun visible (as if it was behind the
	 * camera). It should also affect the way the shadows are cast"): not drawn, and its shadows go away from you, laid on
	 * the ground in perspective ({@link #groundOf}): each layer takes the shadows of every layer in front of it. Simon at
	 * 20:26 ("Sun back does not feel as if it works"): the lab before cast upright copies of each ridge, shifted 10 units
	 * sideways, on the layer just behind; they read as streaks, pointing nowhere. The moon too, when on.
	 */
	private boolean behind;

	/** The sky (the page's gradients alone); each layer alone, drawn; into the stencil; as a shadow. */
	static final int SKY = 0, DRAWN = 1, MASK = DRAWN + LAYERS, CAST = MASK + LAYERS;
	private final Parallax_Heart[] hearts = new Parallax_Heart[CAST + LAYERS];
	private final WholePage_Model[] pages = new WholePage_Model[hearts.length];
	/** The page's own sky and fog, as painted: the light is laid over them each frame (A) and taken off (B). */
	private final Color skyTop = new Color(), skyBottom = new Color(), fog = new Color();
	private OrthographicCamera camera, wide;
	private SpriteBatch batch;
	private ShaderProgram mask, shadow, occlude, rays, relief;
	/**
	 * The light the sun sends past the layers in front of it, A's view at half size: the suns, then the far peaks, the range, the hills
	 * and the pines in black over them ({@link #light}); {@link #RAYS_FRAGMENT} blurs it toward the sun.
	 */
	private FrameBuffer light;
	private Texture sun, moon, haze;
	/** The phase {@link #moon} was drawn for, in 64ths of a month; -1 none yet. */
	private int moonDrawn = -1;
	private SelectElement view;
	private InputElement scroll, hour, noonHeight, moonAge, side1, height1, depth, withReceiver, softness, darkness, nightDark, curve, fullFrom, warmth, nightBlue, rayStrength, rayLength, dayLength, reliefStrength, reliefWidth;
	private Element dayButton, behindButton;
	/** While a day plays: the hours left of it. */
	private boolean day;
	private float dayLeft;
	/**
	 * The clock as drawn: the day's own, unrounded, while one plays, else its sliders; sun 1 likewise, and the hour's
	 * own place for it until its sliders or a drag move it off its path. Read through the sliders, a day moved it in
	 * their 1 and 0.5 degree steps, about 7 px at a time, standing still between (Simon at 17:19: "Why is it shaking so
	 * much?").
	 */
	private float hourAt, ageAt, side1At, height1At;
	/** Sun 1 where the hour puts it (true), or where its sliders or a drag put it. */
	private boolean onPath = true;
	/** The sliders' text when the lab last wrote or read them: a change is someone's. */
	private String hourSeen, noonSeen, ageSeen, sideSeen, heightSeen;
	private final Light lit = new Light();
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
		haze = hazeTexture();
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
		skyTop.set(hearts[SKY].topSquare.topColor);
		skyBottom.set(hearts[SKY].topSquare.bottomColor);
		fog.set(hearts[DRAWN].parallaxReader.getFogColor());
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

	/** A soft round glow, no disc: 1 in the middle to nothing at the edge, as (1 - r)^2. */
	private static Texture hazeTexture()
	{
		int size = 64;
		Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
		pixmap.setBlending(Pixmap.Blending.None);
		for (int y = 0; y < size; y++)
			for (int x = 0; x < size; x++)
			{
				float r = Math.min(1, (float) Math.hypot(x + 0.5f - size / 2f, y + 0.5f - size / 2f) / (size / 2f));
				pixmap.setColor(1, 1, 1, (1 - r) * (1 - r));
				pixmap.drawPixel(x, y);
			}
		Texture texture = new Texture(pixmap);
		texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
		pixmap.dispose();
		return texture;
	}

	/**
	 * The moon {@code age} days old, as the sun's disc ({@link #SUN} across in a square three times that): its lit part
	 * a pale grey-white, the rest a faint earthshine, the terminator an ellipse; lit on the right while it waxes (the sun
	 * is to its right, further along the same path), on the left while it wanes. A faint glow round it, as its light's
	 * own in the air.
	 */
	private static Texture moonDisc(float age)
	{
		int size = 96;
		Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
		pixmap.setBlending(Pixmap.Blending.None);
		float disc = size / 6f;
		double phase = 2 * Math.PI * age / MONTH;
		// The terminator: x = k sqrt(1 - y^2) in the disc's radii, k from 1 (new) through 0 (half) to -1 (full).
		float k = (float) Math.cos(phase);
		boolean waxing = age < MONTH / 2;
		float full = (1 - k) / 2;
		for (int y = 0; y < size; y++)
			for (int x = 0; x < size; x++)
			{
				float dx = (x + 0.5f - size / 2f) / disc, dy = (y + 0.5f - size / 2f) / disc;
				float r = (float) Math.hypot(dx, dy);
				float edge = Math.min(1, Math.max(0, (1 - r) * disc + 0.5f));
				float side = waxing ? dx : -dx, line = k * (float) Math.sqrt(Math.max(0, 1 - dy * dy));
				float on = Math.min(1, Math.max(0, (side - line) * disc + 0.5f));
				// Grey seas: a few broad patches, darker.
				float sea = 0.9f - 0.12f * (float) Math.max(0, Math.cos(3.1 * dx + 1.3) * Math.cos(2.7 * dy - 0.4));
				float glow = r > 1 ? Math.max(0, 1 - (r - 1) / 2f) : 0;
				float lum = on * sea + (1 - on) * 0.07f;
				float a = Math.max(edge * Math.max(on, 0.28f), 0.22f * full * glow * glow);
				pixmap.setColor(r <= 1 ? lum : 0.85f, r <= 1 ? lum : 0.88f, r <= 1 ? lum * 0.97f + 0.02f : 0.95f, Math.min(1, a));
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
		LabControls.panelLabels(document, "shadow-labels", "A: shadows and light", "B: as painted");
		view = LabControls.stageChoice(document, "shadow-labels", "view", "view (the whole page; or without the pines, to see the hills' shadows whole)", VIEWS, 0);
		Element controls = document.getElementById("shadow-controls");
		// One grid, its rows in groups (a section's heading would push the last slider under a 710 px window): the
		// clock and the sun's place in the first two, the light in the next two, the shadows after. The sun and the moon
		// move with the hour; a drag in the sky takes the sun off its path.
		Element time = controls;
		hour = LabControls.slider(document, time, "hour", "hour (0 to 24: the sun rises at 6 at the view's left, is highest at 12, sets at 18 at its right)", 0, 24, 0.05f, 8);
		noonHeight = LabControls.slider(document, time, "noonHeight", "noon height (degrees the sun is up at 12; over about 34 it is above the picture)", 10, 80, 1, 45);
		moonAge = LabControls.slider(document, time, "moonAge", "moon age (days since a new moon: 7.4 first quarter, 14.8 full, 22.1 last quarter; a half moon gives a tenth of a full one's light)", 0, 29.5f, 0.1f, 14.8f);
		dayLength = LabControls.slider(document, time, "dayLength", "day length (seconds the button's 24 hours last; the moon ages a day)", 10, 120, 1, 40);
		side1 = LabControls.slider(document, time, "sun1Side", "sun side (degrees from straight ahead: under 0 left, over 0 right; set by the hour, or drag the sun in the sky)", -40, 40, 1, -35);
		height1 = LabControls.slider(document, time, "sun1Height", "sun height (degrees over the horizon; set by the hour, or drag the sun in the sky)", -60, 80, 0.5f, 22.5f);
		dayButton = button(document, time, "shadow-day", false);
		behindButton = button(document, time, "shadow-behind", true);
		showDay();
		showBehind();
		// The light, from the measured day (docs/daylight.md, Simon at 11:08: "higher when there is no sun, way lower
		// when the sun is at the zenith [...] not in a linear fashion").
		Element world = controls;
		nightDark = LabControls.slider(document, world, "nightDark", "darkest night (how dark a moonless night is: 1 black)", 0, 1, 0.01f, 0.9f);
		curve = LabControls.slider(document, world, "curve", "dusk curve (brightness follows the light's lux on a log scale, raised to this: 1 even, higher keeps dusk darker longer)", 0.5f, 4, 0.05f, 1.3f);
		fullFrom = LabControls.slider(document, world, "fullFrom", "full light from (sun height, degrees, from which the world is as painted)", 2, 60, 0.5f, 20);
		warmth = LabControls.slider(document, world, "warmth", "warmth (how much of the measured colour of the light: 0 white light at every hour)", 0, 1.5f, 0.05f, 1);
		nightBlue = LabControls.slider(document, world, "nightBlue", "night blue (eyes in the dark see blue and grey: 0 none, 1 as measured)", 0, 1, 0.05f, 0.8f);
		scroll = LabControls.slider(document, world, "cameraScroll", "camera scroll (units a second)", -160, 160, 5, 20);
		// A shadow lacks the sun's or the moon's direct light and keeps the sky's.
		Element shadows = controls;
		darkness = LabControls.slider(document, shadows, "darkness", "darkness (1: a shadow lacks all the direct light, as in the world; the sky's share is measured: about 10% at noon, all of it at sunset)", 0, 1, 0.05f, 0.6f);
		depth = LabControls.slider(document, shadows, "depth", "depth (world units between a layer and the one in front: the farther, the lower its shadow lands)", 0, 40, 0.5f, 12);
		softness = LabControls.slider(document, shadows, "softness", "softness (the blur's radius, world units)", 0, 0.8f, 0.01f, 0.8f);
		withReceiver = LabControls.slider(document, shadows, "withReceiver", "with the layer under (how the shadow moves as the camera scrolls; 1: a shadow moves with the layer it lies on, as in the world; 0: with the layer casting it)", 0, 1, 0.05f, 1);
		// The sun coming out from behind the range, and going back (r425, Simon at 20:26: "it should have some effect").
		rayStrength = LabControls.slider(document, shadows, "rays", "rays (the light the sun sends past the ridges in front of it, in A: rays round a ridge it is behind, a glare when it is clear; 0 none)", 0, 2, 0.05f, 0.8f);
		rayLength = LabControls.slider(document, shadows, "rayLength", "ray length (how far across the view the rays reach from the sun)", 0.1f, 1, 0.05f, 0.7f);
		// The sun behind you lights each layer's own faces (r425, Simon at 23:35: "Sun behind is still very bad"): its cast
		// shadows fall behind what casts them, out of sight; what shows where it is, is which flanks it lights.
		reliefStrength = LabControls.slider(document, shadows, "relief", "relief (with the sun behind you: each layer's flanks facing the sun lit, the others in the shade; 0 none)", 0, 1, 0.05f, 0.6f);
		reliefWidth = LabControls.slider(document, shadows, "reliefWidth", "relief width (world units in from a layer's edge its flanks reach: small, a rim; large, whole peaks)", 0.1f, 4, 0.05f, 2.5f);
		readout = LabControls.readout(document, controls, "shadow-readout");
		hourAt = LabControls.read(hour);
		ageAt = LabControls.read(moonAge);
		hourSeen = hour.getValue();
		noonSeen = noonHeight.getValue();
		ageSeen = moonAge.getValue();
		place();
	}

	private Element button(Document document, Element into, String id, boolean behind)
	{
		Element button = document.createButtonElement();
		button.setId(id);
		button.setClassName("lab-copy shadow-day");
		into.appendChild(button);
		onClick(button, this, behind);
		return button;
	}

	private static native void onClick(Element element, ShadowLab lab, boolean behind) /*-{
		element.addEventListener('click', $entry(function() {
			if (behind)
				lab.@jks.tools2d.parallax.browsertest.ShadowLab::toggleBehind()();
			else
				lab.@jks.tools2d.parallax.browsertest.ShadowLab::toggleDay()();
		}));
	}-*/;

	/** The sun and the moon behind you, or back in the sky. */
	void toggleBehind()
	{
		behind = !behind;
		showBehind();
	}

	/** The button says what a press does; window.shadowBehind tells the drag and Copy settings where the sun is. */
	private void showBehind()
	{
		behindButton.setInnerText(behind ? "☀ sun back in the sky" : "☀ sun behind you");
		publishBehind(behind);
	}

	private static native void publishBehind(boolean behind) /*-{
		$wnd.shadowBehind = behind;
	}-*/;

	/** The day's button: 24 hours from the hour shown, or the one playing stopped where it is. */
	void toggleDay()
	{
		day = !day;
		if (day)
		{
			dayLeft = 24;
			onPath = true;
		}
		showDay();
	}

	private void showDay()
	{dayButton.setInnerText(day ? "■ stop the day" : "▶ play 24 hours");}

	/** A playing day moved on by {@code seconds}: the hour and the moon's age, their sliders set to show them. */
	private void stepDay(float seconds)
	{
		if (!day)
			return;
		// An hour or an age someone set while it plays: the day goes on from there.
		if (!hour.getValue().equals(hourSeen) || !moonAge.getValue().equals(ageSeen))
		{
			hourAt = LabControls.read(hour);
			ageAt = LabControls.read(moonAge);
			hourSeen = hour.getValue();
			ageSeen = moonAge.getValue();
		}
		float hours = Math.min(dayLeft, 24 * seconds / LabControls.read(dayLength));
		dayLeft -= hours;
		hourAt = (hourAt + hours) % 24;
		ageAt = (ageAt + hours / 24) % MONTH;
		hour.setValue(LabControls.text(hourAt));
		moonAge.setValue(LabControls.text(ageAt));
		hourSeen = hour.getValue();
		ageSeen = moonAge.getValue();
		if (dayLeft <= 0)
		{
			day = false;
			showDay();
		}
	}

	/** Where the clock puts sun 1, unless it was taken off its path; the sliders show it. */
	private void place()
	{
		if (!hour.getValue().equals(hourSeen) || !noonHeight.getValue().equals(noonSeen) || !moonAge.getValue().equals(ageSeen))
		{
			// Someone moved the clock: the sun back on its path, at the slider's hour.
			hourAt = LabControls.read(hour);
			ageAt = LabControls.read(moonAge);
			hourSeen = hour.getValue();
			noonSeen = noonHeight.getValue();
			ageSeen = moonAge.getValue();
			onPath = true;
		}
		else if (!side1.getValue().equals(sideSeen) || !height1.getValue().equals(heightSeen))
			onPath = false;
		if (onPath)
		{
			float noon = LabControls.read(noonHeight);
			side1At = sideAt(hourAt, 0);
			height1At = heightAt(hourAt, 0, noon);
			side1.setValue(LabControls.text(side1At));
			height1.setValue(LabControls.text(height1At));
		}
		else
		{
			side1At = LabControls.read(side1);
			height1At = LabControls.read(height1);
		}
		sideSeen = side1.getValue();
		heightSeen = height1.getValue();
	}

	/** The angle round a body's path at {@code hour}, {@code behind} hours late: 0 rising, pi/2 highest, pi setting. */
	static double pathAngle(float hour, float behind)
	{return 2 * Math.PI * (hour - 6 - behind) / 24;}

	/** A body's side at {@code hour}, {@code behind} hours late: {@code -DAY_SIDE} rising to {@code DAY_SIDE} setting. */
	static float sideAt(float hour, float behind)
	{return (float) (-DAY_SIDE * Math.cos(pathAngle(hour, behind)));}

	/** A body's height at {@code hour}, {@code behind} hours late, {@code noon} up at its highest. */
	static float heightAt(float hour, float behind, float noon)
	{return (float) (noon * Math.sin(pathAngle(hour, behind)));}

	/** The hours the moon is late on the sun, {@code age} days old: a full moon rises as the sun sets. */
	static float moonLate(float age)
	{return 24 * age / MONTH;}

	private void applyControls()
	{
		float sc = LabControls.read(scroll);
		place();
		for (Parallax_Heart heart : hearts)
			heart.screenSpeedConstantX = sc;
		// Read even while off: a slider shows its value when read.
		LabControls.read(view);
		LabControls.read(withReceiver);
		LabControls.read(softness);
		LabControls.read(dayLength);
		LabControls.read(rayStrength);
		LabControls.read(rayLength);
		LabControls.read(reliefStrength);
		LabControls.read(reliefWidth);
		float noon = LabControls.read(noonHeight);
		float moonSide = sideAt(hourAt, moonLate(ageAt)), moonHeight = heightAt(hourAt, moonLate(ageAt), noon);
		lit.set(height1At, moonHeight, ageAt, LabControls.read(nightDark), LabControls.read(curve), LabControls.read(fullFrom),
				LabControls.read(warmth), LabControls.read(nightBlue), LabControls.read(darkness), behind);
		lit.sunSide = side1At;
		lit.moonSide = moonSide;
		float d = LabControls.read(depth);
		readout.setInnerText(clock(hourAt) + ", moon " + Light.phaseName(ageAt) + ", " + (moonHeight > 0 ? round(moonHeight) + "\u00b0 up" : "down")
				+ ". Sun " + describe(side1At, height1At, d) + ". " + Light.luxText(lit.lux) + " lux: the world "
				+ Math.round(100 * lit.bright) + "% bright, a sun shadow " + Math.round(100 * lit.sunCut) + "% darker, a moon shadow "
				+ Math.round(100 * lit.moonCut) + "%.");
	}

	private static String clock(float hour)
	{
		int minutes = Math.round(hour * 60) % (24 * 60);
		int m = minutes % 60;
		return (minutes / 60) + ":" + (m < 10 ? "0" : "") + m;
	}

	/**
	 * Where a sun at {@code side} degrees from straight ahead (over 0 on the right) and {@code height} over the horizon
	 * lays a layer's shadow on the one {@code depth} units in front of it, {x, y}: the ray over the caster's ridge has
	 * moved away from the sun's side by depth tan(side), and dropped depth tan(height) / cos(side), y under 0.
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

	private String describe(float side, float height, float depth)
	{
		if (height <= 0)
			return "under the horizon";
		if (behind)
		{
			// The top of a pine 13 units over its foot at the view's bottom.
			float[] g = groundOf(side, height);
			float w = 1 + g[1] * 13;
			return round(Math.abs(side)) + "\u00b0 " + (side < 0 ? "left" : "right") + " behind you, " + round(height)
					+ "\u00b0 up: shadows toward the horizon " + round(Math.abs(FOCAL * (float) Math.tan(Math.toRadians(side)))) + " " + (side < 0 ? "right" : "left")
					+ "; a 13 tall pine's " + round(Math.abs(13 * g[0] / w)) + " across, " + round(HORIZON - HORIZON / w) + " up";
		}
		float[] k = shadowOf(side, height, depth);
		return round(Math.abs(side)) + "\u00b0 " + (side < 0 ? "left" : "right") + ", " + round(height)
				+ "\u00b0 up: a ridge's shadow " + round(Math.abs(k[1])) + " under it, " + round(Math.abs(k[0])) + " " + (k[0] < 0 ? "left" : "right");
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
		boolean glare = !behind && LabControls.read(rayStrength) > 0;
		if (glare)
			light(pines);
		LabKit.beginPanels();
		if (LabKit.panel(0, height, FRAME_WIDTH, FRAME_HEIGHT, FRAME_HEIGHT, 0))
		{
			lightUp(true);
			hearts[SKY].render();
			glow();
			suns();
			for (int k = 0; k < LAYERS; k++)
				if (k != PINES || pines)
				{
					drawLayers(DRAWN + k);
					relief(k);
					cast(k);
				}
			if (glare)
				rays();
			lightUp(false);
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
	 * The hour's light on the page (A), or off it (B): every layer's tint the light's colour at its brightness, the sky's
	 * two colours and the page fog the painted ones times the sky's light ({@link Light}).
	 */
	private void lightUp(boolean on)
	{
		for (int k = 0; k < LAYERS; k++)
		{
			ParallaxPageReader reader = hearts[DRAWN + k].parallaxReader;
			reader.addColorTransfert(on ? tint.set(lit.tint[0], lit.tint[1], lit.tint[2], 1) : Color.WHITE, 0);
			reader.getFogColor().set(fog);
			if (on)
				reader.getFogColor().mul(lit.haze[0], lit.haze[1], lit.haze[2], 1);
		}
		hearts[SKY].topSquare.topColor.set(skyTop);
		hearts[SKY].topSquare.bottomColor.set(skyBottom);
		if (on)
		{
			hearts[SKY].topSquare.topColor.mul(lit.zenith[0], lit.zenith[1], lit.zenith[2], 1);
			hearts[SKY].topSquare.bottomColor.mul(lit.horizon[0], lit.horizon[1], lit.horizon[2], 1);
		}
	}

	private final Color tint = new Color();

	/**
	 * A low sun's glow over the sky, behind every layer (docs/daylight.md: the air near the horizon lit red-gold round
	 * the sun at sunrise and sunset): a band along the horizon, and a halo round the sun brighter still. Behind you,
	 * the band alone, pink, a little higher: the Belt of Venus over the Earth's shadow, opposite a setting sun.
	 */
	private void glow()
	{
		if (lit.glow < 0.01f)
			return;
		camera.update();
		batch.setProjectionMatrix(camera.combined);
		batch.enableBlending();
		batch.begin();
		float bottom = camera.position.y - WORLD_HEIGHT / 2, x = camera.position.x;
		// Up to the far peaks' tops (about 8 units over the horizon) and past them.
		batch.setColor(lit.glowColor[0], lit.glowColor[1], lit.glowColor[2], 0.9f * lit.glow);
		float middle = bottom + HORIZON + (behind ? 6 : 4);
		batch.draw(haze, x - 1.5f * WORLD_WIDTH, middle - 10, 3 * WORLD_WIDTH, 20);
		if (!behind)
		{
			double a = Math.toRadians(side1At);
			float sx = x + FOCAL * (float) Math.tan(a), sy = bottom + HORIZON + FOCAL * (float) (Math.tan(Math.toRadians(Math.max(height1At, -4))) / Math.cos(a));
			batch.setColor(lit.glowColor[0], lit.glowColor[1], lit.glowColor[2], lit.glow);
			batch.draw(haze, sx - 16, sy - 9, 32, 18);
		}
		batch.setColor(Color.WHITE);
		batch.end();
	}

	/**
	 * The sun and the moon, over the sky and behind every layer: the farthest peaks (l0) hide a low one first (Simon at
	 * 23:35: "you did not show the sun behind the last layer but the one before that"; bac21de had put it over l0, whose
	 * peaks are hazed almost to the sky's colour, for the bites they cut in it). The sun in its colour (red low down),
	 * the moon paler by day.
	 */
	private void suns()
	{
		if (behind)
			return;
		camera.update();
		batch.setProjectionMatrix(camera.combined);
		batch.enableBlending();
		batch.begin();
		float noon = LabControls.read(noonHeight), late = moonLate(ageAt);
		drawMoon(sideAt(hourAt, late), heightAt(hourAt, late, noon));
		batch.setColor(lit.sunDisc[0], lit.sunDisc[1], lit.sunDisc[2], 1);
		drawBody(sun, side1At, height1At);
		batch.setColor(Color.WHITE);
		batch.end();
	}

	/** The moon at its phase, drawn anew when its age has moved a 64th of a month. */
	private void drawMoon(float side, float height)
	{
		int phase = Math.round(ageAt / MONTH * 64) % 64;
		if (phase != moonDrawn)
		{
			if (moon != null)
			{
				batch.flush();
				moon.dispose();
			}
			moon = moonDisc(phase * MONTH / 64);
			moonDrawn = phase;
		}
		batch.setColor(lit.moonDisc[0], lit.moonDisc[1], lit.moonDisc[2], lit.moonDisc[3]);
		drawBody(moon, side, height);
	}

	/**
	 * Into {@link #light}: the sun in its colour on black, then every layer in front of it (the far peaks, the range, the
	 * hills, the pines when shown) in black over it, through its alpha: what light reaches the eye from the sun, at half
	 * size.
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
	 * Over A, added to it: {@link #light} blurred toward the sun, so what of it shows streams out from it, darker where a
	 * ridge is between: rays round a ridge the sun is just behind, a glare round it when it is clear, nothing once it is
	 * deep behind the range. In the sun's colour.
	 */
	private void rays()
	{
		double a = Math.toRadians(side1At);
		float x = WORLD_WIDTH / 2 + FOCAL * (float) Math.tan(a);
		float y = HORIZON + FOCAL * (float) (Math.tan(Math.toRadians(height1At)) / Math.cos(a));
		camera.update();
		batch.setProjectionMatrix(camera.combined);
		batch.setShader(rays);
		batch.enableBlending();
		batch.setBlendFunction(GL20.GL_ONE, GL20.GL_ONE);
		batch.begin();
		rays.setUniformf(rays.fetchUniformLocation("u_sun", false), x / WORLD_WIDTH, y / WORLD_HEIGHT);
		rays.setUniformf(rays.fetchUniformLocation("u_rays", false), LabControls.read(rayStrength), LabControls.read(rayLength));
		rays.setUniformf(rays.fetchUniformLocation("u_color", false), lit.sunDisc[0], lit.sunDisc[1] * 0.92f, lit.sunDisc[2] * 0.75f);
		float left = camera.position.x - WORLD_WIDTH / 2, bottom = camera.position.y - WORLD_HEIGHT / 2;
		batch.draw(light.getColorBufferTexture(), left, bottom, WORLD_WIDTH, WORLD_HEIGHT, 0, 0, 1, 1);
		batch.end();
		batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		batch.setShader(null);
	}

	/**
	 * The sun's light into {@link #light}: its disc, and the sky's glow round it, {@link #GLOW} across and dim, which the
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
		drawBody(sun, side, height);
	}

	/** A disc of the sky ({@link #sunDisc}, {@link #moonDisc}) where a body at {@code side}, {@code height} is seen. */
	private void drawBody(Texture disc, float side, float height)
	{
		double a = Math.toRadians(side);
		float x = camera.position.x + FOCAL * (float) Math.tan(a);
		float y = camera.position.y - WORLD_HEIGHT / 2 + HORIZON + FOCAL * (float) (Math.tan(Math.toRadians(height)) / Math.cos(a));
		float size = 3 * SUN;
		batch.draw(disc, x - size / 2, y - size / 2, size, size);
	}

	/**
	 * Behind you, layer {@code k}'s own faces in the light that casts the most (the sun's, or at night the moon's): a
	 * flank turned toward it lit, in its colour, one turned away in the shade, each in the layer's fog, as strong as the
	 * light's direct share. The flanks are read from the layer's ridge line ({@link #RELIEF_FRAGMENT}): a face leans as
	 * the ridge over it climbs, so a peak's flank on the light's side is lit, the other in the shade, down to its foot.
	 */
	private void relief(int k)
	{
		boolean byMoon = lit.moonCut > lit.sunCut;
		float strength = LabControls.read(reliefStrength) * Math.min(1, 1.5f * (byMoon ? lit.moonShare : lit.sunShare));
		float h = byMoon ? heightAt(hourAt, moonLate(ageAt), LabControls.read(noonHeight)) : height1At;
		float s = byMoon ? lit.moonSide : side1At;
		if (!behind || strength <= 0 || h <= 0)
			return;
		double a = Math.toRadians(s), up = Math.toRadians(h);
		ParallaxLayer layer = hearts[CAST + k].parallaxReader.layers.get(k);
		WholePage_Model page = pages[DRAWN + k];
		float fogged = ParallaxPageReader.fogOf(page.getFogStrength(), ParallaxPageReader.frontSpeedOf(hearts[DRAWN + k].parallaxReader.layers), hearts[DRAWN + k].parallaxReader.layers.get(k));
		Color f = hearts[DRAWN + k].parallaxReader.getFogColor();
		float[] from = byMoon ? lit.moonDisc : lit.sunDisc;
		float b = lit.bright;
		camera.update();
		batch.setProjectionMatrix(camera.combined);
		batch.enableBlending();
		batch.setShader(relief);
		relief.bind();
		relief.setUniformf(relief.fetchUniformLocation("u_sun", false), (float) (Math.sin(a) * Math.cos(up)), (float) Math.sin(up), (float) (Math.cos(a) * Math.cos(up)));
		relief.setUniformf(relief.fetchUniformLocation("u_shade", false), SHADOW_COLOR.r * (1 - fogged) + f.r * fogged, SHADOW_COLOR.g * (1 - fogged) + f.g * fogged,
				SHADOW_COLOR.b * (1 - fogged) + f.b * fogged, strength);
		relief.setUniformf(relief.fetchUniformLocation("u_lit", false), b * from[0] * (1 - fogged) + f.r * fogged, b * from[1] * (1 - fogged) + f.g * fogged,
				b * from[2] * 0.85f * (1 - fogged) + f.b * fogged, 1);
		draw(relief, hearts[CAST + k], layer, LabControls.read(reliefWidth), camera);
		batch.setShader(null);
	}

	/**
	 * Layer {@code receiver} into the stencil, where it is opaque; then, where it is set, the shadow the layer behind it
	 * casts from the sun and from the moon, each as dark as its direct share of the light ({@link Light#sunCut}).
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
		float d = LabControls.read(depth), noon = LabControls.read(noonHeight), late = moonLate(ageAt);
		if (height1At > 0 && lit.sunCut > 0.002f)
			sun(receiver, side1At, height1At, d, 2, lit.shadeOf(false));
		float mh = heightAt(hourAt, late, noon);
		if (mh > 0 && lit.moonCut > 0.002f)
			sun(receiver, sideAt(hourAt, late), mh, d, 4, lit.shadeOf(true));
		Gdx.gl.glStencilMask(0xff);
		Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
	}

	/**
	 * One light's shadows on {@code receiver}: ahead, the layer behind it's ({@link #shadowOf}); behind you, every layer
	 * in front of it laid on the ground ({@link #groundOf}), each pixel once ({@code drawn}).
	 */
	private void sun(int receiver, float side, float height, float depth, int drawn, float[] shade)
	{
		if (!behind)
		{
			shade(receiver, receiver - 1, shadowOf(side, height, depth), drawn, shade);
			return;
		}
		float[] g = groundOf(side, height);
		for (int caster = LAYERS - 1; caster > receiver; caster--)
			shade(receiver, caster, g, drawn, shade);
	}

	/**
	 * One light's shadow on {@code receiver} from {@code caster}, a sun ahead's: the layer behind it shifted by {@code k}
	 * ({@link #shadowOf}), wider about the view's middle by {@code withReceiver} of the receiver's speed over its own,
	 * blurred; and under it, to the view's bottom, its mass: a ridge's shadow is all that is under it too. Drawn as a
	 * product (GL_ZERO, GL_SRC_COLOR): what is drawn there times {@code shade}, the light that is left once the direct
	 * light is gone (the sky's, blue), in the receiver's fog (which the shadow does not darken). Each pixel once a light:
	 * the stencil's bit {@code drawn}, set where it is drawn, keeps the mass (laid over the caster's foot, so no seam
	 * shows between them) and the wide camera's tiles off what it already shades.
	 */
	private void shade(int receiver, int caster, float[] k, int drawn, float[] shade)
	{
		if (caster < 0 || caster >= LAYERS || caster == PINES && LabControls.read(view) != 0)
			return;
		Gdx.gl.glStencilFunc(GL20.GL_EQUAL, 1, 1 | drawn);
		Gdx.gl.glStencilMask(drawn);
		Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_INVERT);
		ParallaxLayer onto = hearts[DRAWN + receiver].parallaxReader.layers.get(receiver);
		WholePage_Model page = pages[DRAWN + receiver];
		float fogged = ParallaxPageReader.fogOf(page.getFogStrength(), ParallaxPageReader.frontSpeedOf(hearts[DRAWN + receiver].parallaxReader.layers), onto);

		batch.setShader(shadow);
		shadow.bind();
		shadow.setUniformf(shadow.fetchUniformLocation("u_shadow", false), shade[0] + (1 - shade[0]) * fogged, shade[1] + (1 - shade[1]) * fogged,
				shade[2] + (1 - shade[2]) * fogged, 1);
		shadow.setUniformf(shadow.fetchUniformLocation("u_ground", false), 0, 0, 0, 0);
		camera.update();
		wide.position.set(camera.position);
		wide.update();
		batch.setProjectionMatrix(camera.combined);
		batch.enableBlending();
		batch.setBlendFunction(GL20.GL_ZERO, GL20.GL_SRC_COLOR);
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
		}
		else
		{
			float wider = 1 + LabControls.read(withReceiver) * (onto.getParallaxSpeedRatioX() / from.getParallaxSpeedRatioX() - 1);
			shadow.setUniformf(shadow.fetchUniformLocation("u_shift", false), k[0], k[1], camera.position.x, wider);
			draw(hearts[CAST + caster], from, LabControls.read(softness), wide);
			// The mass under the caster's foot, from inside its opaque foot (FOOT): it goes on where the layer stops.
			// Placed as the caster is: the shader shifts it with it.
			float top = bottom + hearts[CAST + caster].parallaxReader.getDrawingHeight() + from.getCurrentDistanceY() + FOOT;
			float under = bottom - 1 - Math.abs(k[1]);
			shadow.setUniformf(shadow.fetchUniformLocation("u_soft", false), 0, 0);
			batch.begin();
			batch.setColor(1, 1, 1, 1);
			batch.draw(sun, camera.position.x - wide.viewportWidth / 2, under, wide.viewportWidth, top - under, 0.5f, 0.5f, 0.5f, 0.5f);
			batch.end();
		}
		batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
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
		haze.dispose();
		if (moon != null)
			moon.dispose();
	}

	/**
	 * The light of an hour, from the measured clear-sky day (docs/daylight.md; the tables are
	 * tools/r425-daylight/daylight.py --java's). Lux: the sun's (its own and its sky's) by its height, the moon's 0.27 lux
	 * full and high times its phase's share (Krisciunas and Schaefer) and its height's, and the night's 0.001. The
	 * world's brightness is the lux on a log scale between a moonless night and the sun {@code fullFrom} degrees up,
	 * raised to {@code curve}, from {@code 1 - nightDark} to 1 (Simon at 11:08: "higher when there is no sun, way lower
	 * when the sun is at the zenith [...] not in a linear fashion"). Its colour: the sun's direct light (red low down),
	 * the moon's (the sun's, a little redder), the sky's (blue), each by its share, white-balanced to a high sun; at low
	 * light, toward a grey blue, as eyes see in the dark (Purkinje). A cast shadow lacks a light's direct share, times
	 * {@code darkness}: what is left is the sky's.
	 */
	static final class Light
	{
		static final float[] LIGHT_HEIGHTS = { -18f, -15f, -12f, -9f, -6f, -4f, -2f, -1f, 0f, 1f, 2f, 3f, 5f, 7.5f, 10f, 15f, 20f, 30f, 45f, 60f, 90f };
		static final float[] LIGHT_LOG_LUX = { -2.80f, -2.50f, -2.05f, -0.78f, 0.53f, 1.27f, 2.01f, 2.38f, 2.75f, 2.89f, 3.22f, 3.43f, 3.71f, 3.94f, 4.11f, 4.34f, 4.49f, 4.70f, 4.88f, 4.99f, 5.06f };
		static final float[] LIGHT_DIRECT = { 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.13f, 0.22f, 0.30f, 0.45f, 0.56f, 0.64f, 0.73f, 0.79f, 0.84f, 0.88f, 0.90f, 0.91f };
		static final float[] LIGHT_FACING = { 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.00f, 0.93f, 0.93f, 0.93f, 0.93f, 0.93f, 0.93f, 0.92f, 0.91f, 0.89f, 0.85f, 0.78f, 0.00f };
		static final float[] LIGHT_SUN = { 1.000f, 0.252f, 0.000f, 1.000f, 0.252f, 0.000f, 1.000f, 0.252f, 0.000f, 1.000f, 0.252f, 0.000f, 1.000f, 0.252f, 0.000f, 1.000f, 0.252f, 0.000f, 1.000f, 0.252f, 0.000f, 1.000f, 0.252f, 0.000f, 1.000f, 0.252f, 0.000f, 1.000f, 0.438f, 0.000f, 1.000f, 0.560f, 0.000f, 1.000f, 0.645f, 0.000f, 1.000f, 0.753f, 0.323f, 1.000f, 0.828f, 0.496f, 1.000f, 0.873f, 0.608f, 1.000f, 0.923f, 0.746f, 1.000f, 0.949f, 0.825f, 1.000f, 0.976f, 0.912f, 1.000f, 0.992f, 0.972f, 1.000f, 1.000f, 1.000f, 0.983f, 0.988f, 1.000f };
		static final float[] LIGHT_SKY = { 0.140f, 0.504f, 1.000f, 0.142f, 0.508f, 1.000f, 0.146f, 0.513f, 1.000f, 0.152f, 0.523f, 1.000f, 0.164f, 0.539f, 1.000f, 0.276f, 0.590f, 1.000f, 0.393f, 0.655f, 1.000f, 0.460f, 0.694f, 1.000f, 0.536f, 0.740f, 1.000f, 0.536f, 0.740f, 1.000f, 0.536f, 0.740f, 1.000f, 0.536f, 0.740f, 1.000f, 0.505f, 0.704f, 1.000f, 0.508f, 0.692f, 1.000f, 0.510f, 0.685f, 1.000f, 0.512f, 0.678f, 1.000f, 0.513f, 0.674f, 1.000f, 0.514f, 0.670f, 1.000f, 0.514f, 0.668f, 1.000f, 0.514f, 0.667f, 1.000f, 0.514f, 0.666f, 1.000f };
		/** A moonless night's light, lux: stars and airglow. A full moon high up: lux. */
		static final float NIGHT_LUX = 0.001f, FULL_MOON_LUX = 0.27f;
		/** The moon's light is the sun's, a little redder (its soil reflects red more than blue). */
		static final float[] MOON_TINT = { 1, 0.93f, 0.84f };
		/** What eyes see in the dark: a grey blue (encoded), and the light's log lux over which they turn to it. */
		static final float[] NIGHT_SEEN = { 0.62f, 0.74f, 1 };
		static final float SEEN_DAY = 1, SEEN_NIGHT = -2;
		/** A moonless night's and the noon sun's log lux. */
		static final float LOG_NIGHT = -3;

		/** The whole light, lux; the world's brightness, 0 to 1; the shares of it that come straight from the sun, the moon. */
		float lux, bright, sunShare, moonShare;
		/** How much darker a sun's, a moon's cast shadow is, 0 to 1 (its encoded luminance's loss), for the readout. */
		float sunCut, moonCut;
		float sunSide, moonSide;
		/** The layers' tint (encoded, the brightness in it); the sky's at its top and at the horizon, over the painted. */
		final float[] tint = new float[3], zenith = new float[3], horizon = new float[3], haze = new float[3];
		/** The glow of a low sun over the horizon (behind you, the pink band opposite it): its colour and amount. */
		final float[] glowColor = new float[3];
		float glow;
		/** What a sun's, a moon's shadow leaves of what is drawn there, per channel (encoded). */
		final float[] sunShade = new float[3], moonShade = new float[3];
		/** The sun's disc colour; the moon's, and its opacity (paler by day). */
		final float[] sunDisc = new float[3], moonDisc = new float[4];
		private final float[] a = new float[3], b = new float[3], c = new float[3], litLinear = new float[3], noonLinear = new float[3];

		float[] shadeOf(boolean moon)
		{return moon ? moonShade : sunShade;}

		void set(float sunHeight, float moonHeight, float moonAge, float nightDark, float curve, float fullFrom, float warmth, float nightBlue, float darkness, boolean behind)
		{
			float sunLux = sunLux(sunHeight), moonLux = moonLux(moonHeight, moonAge);
			lux = sunLux + moonLux + NIGHT_LUX;
			float sunDirect = sunLux * table(LIGHT_DIRECT, sunHeight), moonDirect = moonLux * table(LIGHT_DIRECT, moonHeight);
			sunShare = sunDirect / lux;
			moonShare = moonDirect / lux;
			// The light's colour, linear: each part by its lux, its colour of luminance 1. Ahead of you, the faces you see
			// take the sun's light as level ground does; behind you, they are turned to it, and take far more of it low
			// down (LIGHT_FACING: the golden hour), eased in over its first 3 degrees, as the disc clears the horizon's haze.
			float sunLit = behind ? sunLux * table(LIGHT_FACING, sunHeight) * smooth(sunHeight / 3) : sunDirect;
			float moonLit = behind ? moonLux * table(LIGHT_FACING, moonHeight) * smooth(moonHeight / 3) : moonDirect;
			lightLinear(litLinear, sunHeight, sunLit, sunLux - sunLit, moonHeight, moonLit, moonLux - moonLit, NIGHT_LUX, warmth);
			lightLinear(noonLinear, 60, 0.9f, 0.1f, 0, 0, 0, 0, warmth);
			float s = clamp((log10(lux) - LOG_NIGHT) / (LIGHT_LOG_LUX_AT(fullFrom) - LOG_NIGHT));
			bright = 1 - nightDark + nightDark * (float) Math.pow(s, curve);
			// Night eyes: from SEEN_DAY down to SEEN_NIGHT log lux, the colour turns grey blue.
			float seen = nightBlue * smooth((SEEN_DAY - log10(lux)) / (SEEN_DAY - SEEN_NIGHT));
			for (int i = 0; i < 3; i++)
				c[i] = litLinear[i] / noonLinear[i];
			normalise(c);
			encode(c);
			float grey = 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2];
			for (int i = 0; i < 3; i++)
				tint[i] = bright * (c[i] + (grey * NIGHT_SEEN[i] - c[i]) * seen);
			// A cast shadow: the light without (darkness of) that body's direct part.
			cut(sunShade, sunHeight, darkness * sunDirect, false, moonHeight, warmth);
			cut(moonShade, moonHeight, darkness * moonDirect, true, sunHeight, warmth);
			sunCut = 1 - luminance(sunShade);
			moonCut = 1 - luminance(moonShade);
			// The sky: its colour at this sun's height over its colour at noon (the page's painted sky), then night eyes;
			// at the horizon, the glow of a low sun (red-gold) where it is, ahead; behind you, the pink band opposite it.
			tableColor(LIGHT_SKY, sunHeight, a);
			tableColor(LIGHT_SKY, 60, b);
			for (int i = 0; i < 3; i++)
			{
				a[i] = (float) Math.pow(a[i], 2.2) / (float) Math.pow(b[i], 2.2);
				a[i] = 1 + (a[i] - 1) * Math.min(1, warmth);
			}
			normalise(a);
			encode(a);
			grey = 0.2126f * a[0] + 0.7152f * a[1] + 0.0722f * a[2];
			for (int i = 0; i < 3; i++)
				zenith[i] = bright * (a[i] + (grey * NIGHT_SEEN[i] - a[i]) * seen);
			float glow = Math.min(1, warmth) * (float) Math.exp(-sunHeight * sunHeight / 30f) * (behind ? 0.4f : 0.8f);
			tableColor(LIGHT_SUN, Math.max(0, sunHeight), b);
			if (behind)
			{
				b[0] = 1;
				b[1] = 0.72f;
				b[2] = 0.78f;
			}
			for (int i = 0; i < 3; i++)
			{
				horizon[i] = zenith[i] + (bright * b[i] - zenith[i]) * glow;
				// The air between: lit as the sky, the glow faint in it (the far peaks are mostly fog).
				haze[i] = zenith[i] + (bright * b[i] - zenith[i]) * glow * 0.2f;
				glowColor[i] = bright * b[i];
			}
			this.glow = glow;
			// The discs: the sun in its own colour; the moon reddened as low, paler by day.
			tableColor(LIGHT_SUN, Math.max(0, sunHeight), sunDisc);
			tableColor(LIGHT_SUN, Math.max(0, moonHeight), b);
			for (int i = 0; i < 3; i++)
				moonDisc[i] = 0.35f + 0.65f * b[i];
			moonDisc[3] = 1 - 0.65f * clamp((log10(sunLux + 1e-6f) - 0.5f) / 2.5f);
		}

		/** Into {@code out}, linear: each light's lux times its colour of luminance 1. */
		private void lightLinear(float[] out, float sunHeight, float sunDirect, float sunSky, float moonHeight, float moonDirect, float moonSky, float night, float warmth)
		{
			out[0] = out[1] = out[2] = 0;
			add(out, LIGHT_SUN, Math.max(0, sunHeight), sunDirect, null, warmth);
			add(out, LIGHT_SKY, sunHeight, sunSky, null, warmth);
			add(out, LIGHT_SUN, Math.max(0, moonHeight), moonDirect, MOON_TINT, warmth);
			add(out, LIGHT_SKY, 30, moonSky + night, null, warmth);
		}

		private void add(float[] out, float[] colours, float height, float lux, float[] filter, float warmth)
		{
			if (lux <= 0)
				return;
			tableColor(colours, height, a);
			for (int i = 0; i < 3; i++)
			{
				a[i] = (float) Math.pow(a[i], 2.2) * (filter == null ? 1 : filter[i]);
				// warmth 0: white light; 1: as measured; over 1, more so.
				a[i] = (float) Math.pow(Math.max(1e-4f, a[i]), warmth);
			}
			float l = 0.2126f * a[0] + 0.7152f * a[1] + 0.0722f * a[2];
			for (int i = 0; i < 3; i++)
				out[i] += lux * a[i] / l;
		}

		/** What is left, per channel (encoded), once {@code direct} lux of one body's light is taken off the whole. */
		private void cut(float[] out, float height, float direct, boolean moon, float otherHeight, float warmth)
		{
			if (direct <= 0)
			{
				out[0] = out[1] = out[2] = 1;
				return;
			}
			c[0] = c[1] = c[2] = 0;
			add(c, LIGHT_SUN, Math.max(0, height), direct, moon ? MOON_TINT : null, warmth);
			for (int i = 0; i < 3; i++)
				out[i] = (float) Math.pow(clamp(1 - c[i] / litLinear[i]), 1 / 2.2);
		}

		static float sunLux(float height)
		{return height < -24 ? 0 : Math.max(0, (float) Math.pow(10, table(LIGHT_LOG_LUX, height)) - NIGHT_LUX);}

		/** The moon's lux at that height and age: a full high one's times its phase's share and its height's. */
		static float moonLux(float height, float age)
		{return height <= 0 ? 0 : FULL_MOON_LUX * phaseLight(age) * sunLux(height) / sunLux(90);}

		/** The share of a full moon's light a moon {@code age} days old gives (Krisciunas and Schaefer 1991). */
		static float phaseLight(float age)
		{
			float angle = Math.abs(180 - 360 * (age % MONTH) / MONTH);
			return (float) Math.pow(10, -0.4 * (0.026 * angle + 4e-9 * Math.pow(angle, 4)));
		}

		static String phaseName(float age)
		{
			float f = (age % MONTH) / MONTH;
			String[] names = { "new", "waxing crescent", "first quarter", "waxing gibbous", "full", "waning gibbous", "last quarter", "waning crescent" };
			return names[Math.round(f * 8) % 8] + " (" + Math.round(100 * phaseLight(age)) + "% of a full one's light)";
		}

		static String luxText(float lux)
		{
			if (lux >= 100)
				return String.valueOf(Math.round(lux / 10) * 10);
			if (lux >= 1)
				return String.valueOf(Math.round(lux * 10) / 10f);
			// Two significant digits.
			int places = (int) Math.ceil(-log10(lux)) + 1;
			double k = Math.pow(10, places);
			return String.valueOf(Math.round(lux * k) / k);
		}

		static float LIGHT_LOG_LUX_AT(float height)
		{return table(LIGHT_LOG_LUX, height);}

		static float table(float[] values, float height)
		{
			if (height <= LIGHT_HEIGHTS[0])
				return values[0];
			for (int i = 1; i < LIGHT_HEIGHTS.length; i++)
				if (height <= LIGHT_HEIGHTS[i])
				{
					float t = (height - LIGHT_HEIGHTS[i - 1]) / (LIGHT_HEIGHTS[i] - LIGHT_HEIGHTS[i - 1]);
					return values[i - 1] + (values[i] - values[i - 1]) * t;
				}
			return values[values.length - 1];
		}

		static void tableColor(float[] values, float height, float[] out)
		{
			int last = LIGHT_HEIGHTS.length - 1;
			int i = 1;
			while (i < last && height > LIGHT_HEIGHTS[i])
				i++;
			float t = clamp((height - LIGHT_HEIGHTS[i - 1]) / (LIGHT_HEIGHTS[i] - LIGHT_HEIGHTS[i - 1]));
			for (int k = 0; k < 3; k++)
				out[k] = values[3 * (i - 1) + k] + (values[3 * i + k] - values[3 * (i - 1) + k]) * t;
		}

		private static void normalise(float[] c)
		{
			float max = Math.max(c[0], Math.max(c[1], c[2]));
			for (int i = 0; i < 3; i++)
				c[i] /= max;
		}

		private static void encode(float[] c)
		{
			for (int i = 0; i < 3; i++)
				c[i] = (float) Math.pow(Math.max(0, c[i]), 1 / 2.2);
		}

		private static float luminance(float[] c)
		{return 0.2126f * c[0] + 0.7152f * c[1] + 0.0722f * c[2];}

		static float log10(float x)
		{return (float) (Math.log(x) / Math.log(10));}

		static float clamp(float x)
		{return Math.max(0, Math.min(1, x));}

		static float smooth(float x)
		{
			float t = clamp(x);
			return t * t * (3 - 2 * t);
		}
	}

	/** A flank turned from the light: a blue black, as the shade of a bright sky's light. */
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

	/** A layer in front of the suns into {@link #light}: black, through its alpha. */
	static final String OCCLUDE_FRAGMENT = HEAD
			+ "void main()\n"
			+ "{\n"
			+ "	gl_FragColor = vec4(0.0, 0.0, 0.0, texture2D(u_texture, v_texCoords).a);\n"
			+ "}\n";

	/**
	 * {@link #light} blurred toward the sun, u_sun in the view's 0 to 1: 48 samples along the line from the pixel to it,
	 * each weaker than the one before, over u_rays.y of the way; times u_rays.x, in u_color (the sun's), added to what is
	 * drawn.
	 */
	static final String RAYS_FRAGMENT = HEAD
			+ "uniform vec2 u_sun;\n"
			+ "uniform vec2 u_rays;\n"
			+ "uniform vec3 u_color;\n"
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
			+ "	gl_FragColor = vec4(sum / 48.0 * u_rays.x * u_color, 1.0);\n"
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
	 * The caster as a shadow: its alpha, averaged over a disc of u_soft (in the page's texture), times u_shadow.a, from
	 * nothing to u_shadow.rgb (what the shadow leaves of the light, per channel), discarded where it is all but clear. u_strips: the strips a page height holds, and half a texel: the samples stay in their strip.
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
			// Drawn as a product (GL_ZERO, GL_SRC_COLOR): what is there times what the shadow leaves of it.
			+ "	gl_FragColor = vec4(mix(vec3(1.0), u_shadow.rgb, alpha), 1.0);\n"
			+ "}\n";
}
