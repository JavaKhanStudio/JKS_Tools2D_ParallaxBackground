package jks.tools2d.parallax.browsertest;

import java.util.List;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.google.gwt.dom.client.CanvasElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.SelectElement;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Utils_Page_Json;

/**
 * The lurker lab (r369, Simon: "do we have a lab associated with this? I want to test it in action"): r363's demo, a
 * hooded figure behind Hiver's front pines (core/test-data/lurker/make_lab.py), its {@code lurkerCover} baked by
 * LurkerCover. In its first mode the reader runs the figure's sightings (r370): gone at the gap after one it was seen
 * in, there again by its seed's draws, switched only while the cover says a nearer layer hides it whole or it is out of
 * the view. To see what that rule prevents, the second switches it every two seconds wherever it is: two hearts scroll
 * together, the page with the figure (revealed every step, so always there) and the page without it, and the frame
 * draws the one the timer says. The strip under it is the last 20 s: green while the cover hides the figure, amber while
 * it is in the view uncovered (in a gap, or behind soft edges or two layers together, which the cover does not count),
 * grey while it is out of the view; a white tick at each switch.
 * <p>
 * The strip's state is worked out here, apart from the reader, as tools/lurker-cover/LurkerBake's track does: the
 * readout's count of switches made in sight checks the reader's. Opened with effects-lab.html?lab=lurker:
 * tools/effects-lab.sh --lab lurker. {@code &clip=1}: nothing moves by itself, {@code window.effectsLabAct(seconds)}
 * steps it at 1/60 s. {@code &figure=gone}: the figure is never drawn, whatever the switch says: what
 * tools/r369-lurker-cover/lab-check.sh compares the lab's covered frames with.
 */
public class LurkerLab extends ApplicationAdapter implements LabKit.Clip
{
	static final String PAGE = "lurker-lab/lab.jplax", EMPTY = "lurker-lab/empty.jplax";
	static final float WORLD_WIDTH = FogLab.WORLD_WIDTH, WORLD_HEIGHT = FogLab.WORLD_HEIGHT;
	static final int FRAME_WIDTH = FloorLab.FRAME_WIDTH, FRAME_HEIGHT = FloorLab.FRAME_HEIGHT;
	/** What the lab opens on: the film's scroll (60 units a second), the figure's speed, a figure every 50 units. */
	static final float SCROLL = 60, SPEED = 0.032f, PERIOD = 50;
	/** The timer mode's step, seconds. */
	static final float TIMER = 2;
	/** What the strip keeps: 20 s at 60 frames a second. */
	static final int STRIP = 1200;
	static final int OUT = 0, UNCOVERED = 1, COVERED = 2, SWITCHED = 4;
	static final String[] MODES = { "the reader's sightings", "every 2 s, anywhere", "never: always there" };
	/** The figure's layer name in the lab's page, what the lab reveals it by. */
	static final String NAME = "hooded";

	private Parallax_Heart withFigure, without;
	private List<ParallaxLayer> layers;
	private ParallaxLayer figure;
	private int lurker;
	private float[] cover;
	private SpriteBatch batch;
	private InputElement scroll, speed, period;
	private SelectElement mode;
	private Element readout;
	private CanvasElement strip;
	private final int[] samples = new int[STRIP];
	private int next, state = OUT, switches, inSight, lastMode = -1;
	private boolean there = true, clip, gone;
	private float timer;

	@Override
	public void create()
	{
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		gone = "gone".equals(com.google.gwt.user.client.Window.Location.getParameter("figure"));
		batch = new SpriteBatch();
		withFigure = heart(PAGE);
		without = heart(EMPTY);
		layers = withFigure.parallaxReader.layers;
		for (int i = 0; i < layers.size(); i++)
			if (layers.get(i).kind == Enum_LayerKind.LURKER)
				lurker = i;
		figure = layers.get(lurker);
		cover = withFigure.currentPage.pageModel.pageList.get(lurker).lurkerCover;
		if (figure.kind != Enum_LayerKind.LURKER || cover.length == 0)
			throw new IllegalStateException(PAGE + " has no LURKER layer with a cover");
		buildControls();
		applyControls();
		if (clip)
			LabKit.exportClip("effectsLab", this);
	}

	private Parallax_Heart heart(String page)
	{
		OrthographicCamera camera = new OrthographicCamera();
		camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
		Parallax_Heart heart = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
		heart.setPage(Utils_Page_Json.loadPage(page));
		return heart;
	}

	private void buildControls()
	{
		Document document = Document.get();
		document.setTitle("Effects lab: lurker (r369)");
		document.getElementById("effects-title").setInnerText("Effects lab: lurker");
		document.getElementById("effects-lab").getStyle().setProperty("maxWidth", FRAME_WIDTH + "px");
		document.getElementById("effects-labels").getStyle().setProperty("gridTemplateColumns", "1fr");
		LabControls.panelLabels(document, "effects-labels", "A hooded figure behind Hiver's pines, switched only while they hide it");
		Element controls = document.getElementById("effects-controls");
		mode = LabControls.choice(document, controls, "switchWhen", "switch the figure (the reader's sightings: gone at the gap after one it was seen in, back by its seed's draws, 1 in 3, switched only while LurkerCover says a nearer layer hides it whole or out of the view; every 2 s, anywhere: what that rule prevents, the figure popping in a gap; never: always there)", MODES, 0);
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll (units a second; the film's was 60)", -160, 160, 5, SCROLL);
		speed = LabControls.slider(document, controls, "figureSpeed", "figure's speed (ratio; the hills behind it are 0.024, the pines in front 0.038 and 0.041: the cover holds at any speed, it is worked out on the offset between the layers)", 0.01f, 0.036f, 0.001f, SPEED);
		period = LabControls.slider(document, controls, "figurePeriod", "figure every (world units from one figure to the next; the view is 40 wide)", 30, 120, 1, PERIOD);
		strip = document.createCanvasElement();
		strip.setWidth(FRAME_WIDTH);
		strip.setHeight(24);
		strip.setClassName("lab-strip");
		strip.setTitle("the last 20 s: green covered by one nearer layer, amber in the view uncovered, grey out of the view; white: a switch");
		strip.getStyle().setProperty("width", "100%");
		strip.getStyle().setProperty("height", "24px");
		strip.getStyle().setProperty("gridColumn", "1 / -1");
		controls.appendChild(strip);
		readout = LabControls.readout(document, controls, "effects-readout");
	}

	private void applyControls()
	{
		float s = LabControls.read(speed);
		figure.setParallaxSpeedRatioX(s);
		figure.setPadX(LabControls.read(period) - figure.getWidth());
		float sc = LabControls.read(scroll);
		withFigure.screenSpeedConstantX = sc;
		without.screenSpeedConstantX = sc;
	}

	/** One step of {@code delta}: both hearts, then where the figure stands and whether it switches. */
	private void step(float delta)
	{
		LabKit.act(delta, withFigure, without);
		state = state();
		int picked = LabControls.read(mode);
		boolean flip = false;
		if (picked != 0)
			// Its own sightings out of the way: the lab decides.
			withFigure.parallaxReader.reveal(NAME);
		if (picked == 0)
		{
			// The reader's first draw, or its draws taken back from the lab, is no switch.
			flip = lastMode == 0 && figure.isLurkerThere() != there;
			there = figure.isLurkerThere();
		}
		else if (picked == 1)
		{
			timer += delta;
			if (timer >= TIMER)
			{
				timer -= TIMER;
				flip = true;
			}
		}
		else
			there = true;
		if (flip)
		{
			if (picked != 0)
				there = !there;
			switches++;
			if (state == UNCOVERED)
				inSight++;
		}
		lastMode = picked;
		samples[next] = state | (flip ? SWITCHED : 0);
		next = (next + 1) % STRIP;
	}

	/**
	 * {@link #COVERED} when every copy of the figure in the view is in one of its cover's intervals, {@link #UNCOVERED}
	 * when one is not, {@link #OUT} when none is in the view: LurkerBake's track.
	 */
	private int state()
	{
		float p = figure.getTotalWidth(), width = figure.getWidth();
		boolean any = false, all = true;
		for (float x = figure.getCurrentDistanceX() - (float) Math.ceil((figure.getCurrentDistanceX() + width) / p) * p; x < WORLD_WIDTH; x += p)
		{
			if (x + width <= 0)
				continue;
			any = true;
			boolean covered = false;
			for (int k = 0; k < cover.length; k += 3)
			{
				ParallaxLayer near = layers.get(lurker + (int) cover[k]);
				float q = near.getTotalWidth();
				float d = ((x - near.getCurrentDistanceX()) % q + q) % q;
				if (((d - cover[k + 1]) % q + q) % q <= cover[k + 2] - cover[k + 1])
					covered = true;
			}
			all &= covered;
		}
		return !any ? OUT : all ? COVERED : UNCOVERED;
	}

	@Override
	public void act(float seconds)
	{
		applyControls();
		for (int n = LabKit.clipSteps(seconds); n > 0; n--)
			step(1 / 60f);
		showReadout();
	}

	@Override
	public void render()
	{
		if (!clip)
		{
			applyControls();
			step(LabKit.liveDelta());
		}
		LabKit.clear();
		(there && !gone ? withFigure : without).render();
		drawStrip(strip, samples, next);
		showReadout();
	}

	/** What the figure is doing, then the switches and the cover. */
	private void showReadout()
	{
		readout.setInnerText("the figure is " + (there ? "there" : "gone") + ", "
				+ (state == COVERED ? "covered by a nearer layer" : state == UNCOVERED ? "in the view, uncovered" : "out of the view")
				+ "; " + switches + " switches, " + inSight + " of them while uncovered in the view; the cover: "
				+ cover.length / 3 + " intervals (" + Math.round(100 * coveredShare()) + "% of the offsets)");
	}

	/** How much of the offsets the cover's intervals take, each over its own layer's period, summed. */
	private float coveredShare()
	{
		float share = 0;
		for (int k = 0; k < cover.length; k += 3)
			share += (cover[k + 2] - cover[k + 1]) / layers.get(lurker + (int) cover[k]).getTotalWidth();
		return share;
	}

	/** The strip: one column a step, oldest on the left. */
	private static native void drawStrip(CanvasElement canvas, int[] samples, int next) /*-{
		var c = canvas.getContext('2d'), n = samples.length, w = canvas.width / n, h = canvas.height;
		c.fillStyle = '#333';
		c.fillRect(0, 0, canvas.width, h);
		var colours = ['#555', '#e0a020', '#3cb44b'];
		for (var i = 0; i < n; i++) {
			var s = samples[(next + i) % n];
			c.fillStyle = colours[s & 3];
			c.fillRect(i * w, 0, w + 0.5, h);
		}
		c.fillStyle = '#fff';
		for (var i = 0; i < n; i++)
			if (samples[(next + i) % n] & 4)
				c.fillRect(i * w - 1, 0, 2, h);
	}-*/;

	@Override
	public void dispose()
	{batch.dispose();}
}
