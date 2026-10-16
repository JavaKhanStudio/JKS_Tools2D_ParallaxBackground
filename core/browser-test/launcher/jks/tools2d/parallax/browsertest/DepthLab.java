package jks.tools2d.parallax.browsertest;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.SelectElement;

import jks.tools2d.parallax.ParallaxDepth;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Utils_Page_Json;

/**
 * The depth lab (r444): a page turned sideways, each layer a flat card standing as far from the camera as its speed
 * says ({@link ParallaxDepth}), the ground ruled in front distances, the camera's view drawn from its eye. Each card is
 * the real reader drawing that layer alone ({@link ParallaxPageReader#drawLayer(OrthographicCamera, com.badlogic.gdx.graphics.g2d.Batch, int)}),
 * scaled by its distance about the camera's eye: seen from the eye, every card covers what it covers in the game, so the
 * 3D view at 0 is the 2D frame, pixel for pixel, and the slide between the two is one camera moving. The eye is at the
 * view's bottom edge, its lens shifted up: every card stands on one ground, seen edge on from the eye.
 * <p>
 * Drag the canvas to turn round the page (from 2D, it turns into 3D first), the wheel to come closer; the button or the
 * space bar slides between 2D and 3D. {@code &clip=1}: nothing moves by itself, {@code window.depthLabAct(seconds)}
 * steps the heart and the slide at 1/60 s. {@code &flat=1}: the heart's own render instead of the cards, for the check
 * that the 3D view at 0 is the game's frame (tools/depth-lab.sh --seam). {@code &big=1}: the canvas shown at its full
 * 1280 x 720, for a marketing capture.
 */
public class DepthLab extends ApplicationAdapter implements LabKit.Clip
{
	static final float WORLD_WIDTH = FogLab.WORLD_WIDTH, WORLD_HEIGHT = FogLab.WORLD_HEIGHT;
	/** The canvas: 16:9, drawn at 960 wide over the sliders, its back buffer finer. */
	static final int FRAME_WIDTH = 1280, FRAME_HEIGHT = 720, SHOWN_WIDTH = 960;
	static final String[] PAGES = { "AI pines", "AI beach", "Purple fairy", "One night", "Hiver, misty" };
	static final String[] FILES = { "pines-lab/forest.jplax", "anim-lab/beach.jplax", "fog-lab/s05.jplax", "fog-lab/s06.jplax", "fog-lab/s01.jplax" };
	static final String[] SIZES = { "as the camera sees them", "all the front's size" };
	static final String[] AXES = { "true distances", "compressed (square root)", "compressed (log)" };
	/** The units the ground, the tags and the readout speak (r458): metres first, a phone's length, or front distances. */
	static final String[] UNITS = { "metres", "phone lengths", "front distances" };
	static final int METRES = 0, PHONES = 1, FRONTS = 2;
	/** A phone's length and a person's height, in metres: the scale's two references. */
	static final float PHONE = 0.15f, PERSON = 1.8f;
	/** How wide the front layer's view is, in metres, by default: a world unit is a metre on a 40-unit-wide view. */
	static final float ACROSS = 40;
	/** The thinnest a line is drawn, in pixels of the back buffer: narrower, it fades instead (r458). */
	static final float LINE_MIN = 1.6f, LINE_MAX = 5;
	/**
	 * What the lab opens on: 3D, the cards all the front's size on a log axis (the most readable side view of a deep
	 * page), seen from the right of the camera, a little above, the camera's view 60 degrees across.
	 */
	static final int SIZE = 1, AXIS = 2;
	static final float VIEW = 1, YAW = 66, PITCH = 14, CLOSER = 1, FOV = 60, SCROLL = 6;
	/** How long the slide between 2D and 3D takes, in seconds. */
	static final float SLIDE = 1.6f;
	/** Where the sky (the page's gradients and any still layer) stands: this much farther than the farthest layer. */
	static final float SKY = 1.35f;
	/** Each layer's accent, back to front: its card's frame, its foot on the ground and its tag. */
	static final Color[] ACCENTS = { new Color(0.55f, 0.75f, 1f, 1), new Color(0.45f, 0.95f, 0.85f, 1), new Color(1f, 0.82f, 0.4f, 1),
			new Color(1f, 0.55f, 0.7f, 1), new Color(0.75f, 0.6f, 1f, 1), new Color(0.6f, 1f, 0.5f, 1), new Color(1f, 0.65f, 0.4f, 1),
			new Color(0.5f, 0.85f, 1f, 1) };

	private Parallax_Heart heart;
	private SpriteBatch batch;
	private ShapeRenderer shapes;
	private OrthographicCamera flatCamera;
	private SelectElement page, axis, sizes, units;
	private InputElement view, yaw, pitch, closer, fov, scroll, across;
	private Element readout, overlay, labels;
	private boolean clip, flat;
	private int shownPage = -1;
	/** The slide: where it is (0 the 2D frame, 1 the 3D view) and where it goes; -1 when the slider holds it. */
	private float slide = VIEW, goal = -1;

	// Each layer of the page, back to front: how far it is (front distances), where it stands (the axis's), its name.
	private final List<ParallaxLayer> layers = new ArrayList<>();
	private float[] distance = new float[0], placed = new float[0];
	private String[] names = new String[0];
	private float skyPlaced, frontSpeed;

	// The 3D camera.
	private final Matrix4 projection = new Matrix4(), look = new Matrix4(), combined = new Matrix4(), card = new Matrix4(),
			cardCombined = new Matrix4(), toWorld = new Matrix4();
	private final Vector3 eye = new Vector3(), target = new Vector3(), up = new Vector3(0, 1, 0), home = new Vector3(), pivot = new Vector3(),
			projected = new Vector3();
	private float cameraDistance, eased;
	/** Metres a world unit; the side of the page the eye is on (1 its right, -1 its left). */
	private float metresPerUnit = 1, side = 1;
	private final Matrix4 toScreen = new Matrix4();
	private final List<Element> tags = new ArrayList<>();
	private int tagsUsed;
	private String costLine = "";
	private float frameSeconds, frames;

	@Override
	public void create()
	{
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		flat = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("flat"));
		batch = new SpriteBatch();
		shapes = new ShapeRenderer();
		flatCamera = new OrthographicCamera();
		flatCamera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
		heart = new Parallax_Heart(flatCamera, batch, WORLD_WIDTH, WORLD_HEIGHT);
		buildControls();
		applyControls();
		Gdx.input.setInputProcessor(new Orbit());
		if (clip)
			LabKit.exportClip("depthLab", this);
	}

	private void buildControls()
	{
		Document document = Document.get();
		boolean big = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("big"));
		document.getElementById("depth-lab").getStyle().setProperty("maxWidth", (big ? FRAME_WIDTH : SHOWN_WIDTH) + "px");
		LabControls.panelLabels(document, "depth-labels", "");
		page = LabControls.stageChoice(document, "depth-labels", "page", "page (the parallax turned sideways)", PAGES, 0);
		axis = LabControls.choice(document, document.getElementById("depth-controls"), "axis",
				"depth axis (how the ground is ruled: true distances, or compressed so a deep page fits; the cards' sizes and the rules' numbers stay true)", AXES, AXIS);
		Element controls = document.getElementById("depth-controls");
		sizes = LabControls.choice(document, controls, "sizes",
				"cards (as the camera sees them: each scaled by its distance, so from the camera's eye the picture is the game's; or all the front's size, an exploded view, in 3D only)", SIZES, SIZE);
		view = LabControls.slider(document, controls, "view", "2D / 3D (0: the game's frame; 1: the page turned sideways. The button and the space bar slide it)", 0, 1, 0.01f, VIEW);
		yaw = LabControls.slider(document, controls, "yaw", "turn (degrees round the page; drag the canvas)", -85, 85, 1, YAW);
		pitch = LabControls.slider(document, controls, "pitch", "height (degrees above the ground; drag the canvas)", 2, 80, 1, PITCH);
		closer = LabControls.slider(document, controls, "closer", "closer (the 3D camera's distance, 1 frames the page; the wheel)", 0.3f, 2.5f, 0.01f, CLOSER);
		units = LabControls.choice(document, controls, "units",
				"units (of the ground's rules and the tags: metres, a phone's length, or front distances, the camera's distance to the front layer)", UNITS, METRES);
		across = LabControls.slider(document, controls, "across",
				"front's width in metres (how wide the front layer's view is in the world: sets the metres; a person, 1.8 m, stands beside the front)", 4, 200, 1, ACROSS);
		fov = LabControls.slider(document, controls, "fov", "camera's view (degrees across the 2D frame: how far the camera stands from the front layer, the one number the speeds do not give; narrower is farther and deeper)", 20, 110, 1, FOV);
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll (units a second)", -40, 40, 1, SCROLL);
		Element button = document.createButtonElement();
		button.setId("depth-slide");
		button.setClassName("lab-copy depth-slide");
		button.setInnerText("2D ⇄ 3D");
		controls.appendChild(button);
		onClick(button, this);
		readout = LabControls.readout(document, controls, "depth-readout");
		overlay = document.getElementById("depth-tags");
		labels = document.getElementById("depth-labels");
	}

	private static native void onClick(Element element, DepthLab lab) /*-{
		element.addEventListener('click', $entry(function() {
			lab.@jks.tools2d.parallax.browsertest.DepthLab::toggle()();
		}));
	}-*/;

	/** Slides toward 2D from 3D, toward 3D from 2D or from between. */
	void toggle()
	{goal = slide >= 0.999f ? 0 : 1;}

	/** The page as chosen, each layer's distance, and the readout. */
	private void applyControls()
	{
		int chosen = LabControls.read(page);
		if (chosen != shownPage)
		{
			heart.setPage(Utils_Page_Json.loadPage(FILES[chosen]));
			shownPage = chosen;
		}
		heart.screenSpeedConstantX = LabControls.read(scroll);
		if (goal < 0)
			slide = LabControls.read(view);
		LabControls.read(yaw);
		LabControls.read(pitch);
		LabControls.read(closer);
		cameraDistance = ParallaxDepth.cameraDistance(WORLD_WIDTH, LabControls.read(fov));
		metresPerUnit = LabControls.read(across) / WORLD_WIDTH;
		measure();
	}

	/** Each layer's distance from its speed, and where the axis stands it. */
	private void measure()
	{
		layers.clear();
		layers.addAll(heart.parallaxReader.layers);
		int n = layers.size();
		if (distance.length != n)
		{
			distance = new float[n];
			placed = new float[n];
			names = new String[n];
		}
		frontSpeed = ParallaxPageReader.frontSpeedOf(layers);
		float farthest = 1;
		for (int i = 0; i < n; i++)
		{
			distance[i] = ParallaxDepth.distanceOf(frontSpeed, layers.get(i));
			if (distance[i] != Float.POSITIVE_INFINITY)
				farthest = Math.max(farthest, distance[i]);
			String name = layers.get(i).getName();
			TextureRegion region = layers.get(i).getRegion();
			if ((name == null || name.isEmpty()) && region instanceof AtlasRegion)
				name = ((AtlasRegion) region).name + (((AtlasRegion) region).index >= 0 ? "_" + ((AtlasRegion) region).index : "");
			names[i] = name == null || name.isEmpty() ? "layer " + i : name;
		}
		float sky = farthest * SKY;
		skyPlaced = place(sky);
		for (int i = 0; i < n; i++)
			placed[i] = distance[i] == Float.POSITIVE_INFINITY ? skyPlaced : place(distance[i]);
		StringBuilder line = new StringBuilder();
		for (int i = n - 1; i >= 0; i--)
		{
			line.append(names[i]).append(' ').append(times(distance[i]));
			if (LabControls.read(units) != FRONTS && distance[i] != Float.POSITIVE_INFINITY)
				line.append(" (").append(measured(distance[i])).append(')');
			if (i > 0)
				line.append(", ");
		}
		readout.setInnerText("distances from the camera, in front distances (the front layer's, " + two(cameraDistance)
				+ " world units, " + metres(cameraDistance * metresPerUnit) + " at this view and width): " + line + "; " + costLine);
	}

	/** Where a layer {@code d} front distances away stands on the axis chosen: true, or compressed (1 stays 1). */
	private float place(float d)
	{
		switch (LabControls.read(axis))
		{
		case 1:
			return (float) Math.sqrt(d);
		case 2:
			return 1 + (float) Math.log(d) * 1.4427f;
		default:
			return d;
		}
	}

	/** "4.0×", or "∞" for a still layer. */
	private static String times(float d)
	{return d == Float.POSITIVE_INFINITY ? "∞ (still)" : (Math.round(d * 10) / 10.0) + "×";}

	private static String two(float value)
	{return String.valueOf(Math.round(value * 100) / 100.0);}

	/** A length of {@code m} metres: "0.8 m", "35 m", "1.2 km". */
	private static String metres(float m)
	{return m >= 1000 ? Math.round(m / 100) / 10.0 + " km" : m >= 10 ? Math.round(m) + " m" : Math.round(m * 10) / 10.0 + " m";}

	/** How many of the chosen unit one front distance is: the camera's distance to the front, in metres or phones. */
	private float perFront()
	{
		int unit = LabControls.read(units);
		return unit == FRONTS ? 1 : cameraDistance * metresPerUnit / (unit == PHONES ? PHONE : 1);
	}

	/** {@code v} of the chosen unit, written: "35 m", "230 phones", "4.0×". */
	private String unitText(float v)
	{
		switch (LabControls.read(units))
		{
		case PHONES:
			return (v >= 1000 ? Math.round(v / 100) / 10.0 + "k" : v >= 10 ? String.valueOf(Math.round(v)) : String.valueOf(Math.round(v * 10) / 10.0)) + " phones";
		case FRONTS:
			return (Math.round(v * 10) / 10.0) + "×";
		default:
			return metres(v);
		}
	}

	/** A distance of {@code d} front distances in the chosen unit, "∞" for a still layer. */
	private String measured(float d)
	{return d == Float.POSITIVE_INFINITY ? "∞ (still)" : unitText(d * perFront());}

	@Override
	public void act(float seconds)
	{
		applyControls();
		for (int n = LabKit.clipSteps(seconds); n > 0; n--)
		{
			heart.act(1 / 60f);
			stepSlide(1 / 60f);
		}
		applyControls();
	}

	/** Moves the slide toward its goal; there, the slider holds it again. */
	private void stepSlide(float delta)
	{
		if (goal < 0)
			return;
		float step = delta / SLIDE;
		slide = goal > slide ? Math.min(goal, slide + step) : Math.max(goal, slide - step);
		view.setValue(LabControls.text(Math.round(slide * 100) / 100f));
		if (slide == goal)
			goal = -1;
	}

	@Override
	public void render()
	{
		if (!clip)
		{
			applyControls();
			float delta = LabKit.liveDelta();
			heart.act(delta);
			stepSlide(delta);
		}
		LabKit.clear();
		if (flat)
		{
			heart.render();
			hideTags();
			return;
		}
		eased = ease(slide);
		int calls = draw3D(eased);
		frames++;
		frameSeconds += Gdx.graphics.getDeltaTime();
		if (frameSeconds >= 1 || clip)
		{
			costLine = (clip ? "" : "frame " + LabControls.text(1000 * frameSeconds / frames) + " ms; ") + calls + " draw calls";
			frames = frameSeconds = 0;
		}
	}

	private static float ease(float t)
	{return t * t * (3 - 2 * t);}

	/** The camera between its 2D place ({@code e} 0) and the orbit ({@code e} 1), then everything it sees. */
	private int draw3D(float e)
	{
		flatCamera.update();
		float vw = WORLD_WIDTH, vh = WORLD_HEIGHT, cx = vw / 2, d = cameraDistance;
		home.set(cx, 0, d);
		// The orbit turns round the middle of the layers' cards and the camera, a third of their height up, far enough
		// to frame them.
		float deepest = 1, widest = 1;
		for (int i = 0; i < placed.length; i++)
			if (distance[i] != Float.POSITIVE_INFINITY)
			{
				deepest = Math.max(deepest, placed[i]);
				widest = Math.max(widest, scaleOf(placed[i]));
			}
		float zBack = -(deepest - 1) * d;
		pivot.set(cx, vh * widest * 0.35f, (zBack + d) / 2);
		float tanUp = vh / 2 / d, a = vw / vh;
		float yawTo = (float) Math.toRadians(LabControls.read(yaw)), pitchTo = (float) Math.toRadians(LabControls.read(pitch));
		float frame = fit(yawTo, pitchTo, vw, vh, d, tanUp * a, tanUp) / LabControls.read(closer);
		float r0 = home.dst(pivot);
		float pitch0 = (float) Math.asin((home.y - pivot.y) / r0);
		float yaw0 = (float) Math.atan2(home.x - pivot.x, home.z - pivot.z);
		float yawR = yaw0 + (yawTo - yaw0) * e;
		float pitchR = pitch0 + (pitchTo - pitch0) * e;
		float radius = r0 + (frame - r0) * e;
		eye.set(pivot.x + radius * (float) (Math.sin(yawR) * Math.cos(pitchR)), pivot.y + radius * (float) Math.sin(pitchR),
				pivot.z + radius * (float) (Math.cos(yawR) * Math.cos(pitchR)));
		// From 2D, straight ahead of the eye; in 3D, at the pivot.
		target.set(home.x, home.y, home.z - r0).lerp(pivot, e);
		// The lens: at 0 shifted up a whole half view (the eye at the frame's bottom), centred in 3D.
		float shift = 1 - e, near = d * 0.02f, far = (skyPlaced + 4) * d * 40;
		projection.setToProjection(-near * tanUp * a, near * tanUp * a, near * tanUp * (shift - 1), near * tanUp * (shift + 1), near, far);
		look.setToLookAt(eye, target, up);
		combined.set(projection).mul(look);
		side = eye.x >= cx ? 1 : -1;

		int calls = 0;
		tagsUsed = 0;
		studio(e);
		ground(e, vw, d);

		// Back to front as the eye sees them: the parallel cards farthest from it first.
		int n = layers.size();
		Integer[] order = new Integer[n + 1];
		for (int i = 0; i <= n; i++)
			order[i] = i - 1;
		java.util.Arrays.sort(order, (p, q) -> Float.compare(Math.abs(eye.z - planeZ(q, d)), Math.abs(eye.z - planeZ(p, d))));
		for (Integer i : order)
		{
			if (i < 0)
				sky(e, vw, vh, d);
			else
				calls += card(i, e, vw, vh, d);
		}
		person(e, vw, d);
		camera(e, vw, vh, d);
		hideTags();
		spread(overlay, labels);
		return calls;
	}

	/**
	 * Frames the 3D view turned {@code yaw}, {@code pitch}: moves {@link #pivot} and returns the distance from it that
	 * shows every layer's card (the sky's too when the cards are all the front's size) and the eye, centred. Each pass
	 * looks at the corners from the camera it has, then pans to their middle and stands as far as their widest spread
	 * needs.
	 */
	private float fit(float yaw, float pitch, float vw, float vh, float d, float tanAcross, float tanUp)
	{
		Vector3 forward = new Vector3((float) (-Math.sin(yaw) * Math.cos(pitch)), (float) -Math.sin(pitch), (float) (-Math.cos(yaw) * Math.cos(pitch)));
		Vector3 right = new Vector3(forward).crs(up).nor(), above = new Vector3(right).crs(forward).nor();
		Vector3 corner = new Vector3(), from = new Vector3();
		float radius = d * 4, cx = vw / 2;
		boolean sky = LabControls.read(sizes) == 1;
		for (int pass = 0; pass < 4; pass++)
		{
			from.set(forward).scl(-radius).add(pivot);
			float left = Float.MAX_VALUE, rightmost = -Float.MAX_VALUE, low = Float.MAX_VALUE, high = -Float.MAX_VALUE;
			boolean behind = false;
			for (int i = -1; i < placed.length; i++)
			{
				if (i < 0 ? !sky : distance[i] == Float.POSITIVE_INFINITY)
					continue;
				float k = i < 0 ? skyPlaced : placed[i], s = scaleOf(k), z = -(k - 1) * d;
				for (int c = 0; c < 5; c++)
				{
					if (c == 4)
						corner.set(cx, 0, d);
					else
						corner.set((c & 1) == 0 ? cx - vw / 2 * s : cx + vw / 2 * s, (c & 2) == 0 ? 0 : vh * s, z);
					corner.sub(from);
					float along = corner.dot(forward);
					// A corner behind the eye or at it has no place on the screen: stand back first (r458: from behind
					// the camera, low, the pass panned to it and put the eye under the ground).
					if (along < d * 0.05f)
					{
						behind = true;
						continue;
					}
					float x = corner.dot(right) / along / tanAcross, y = corner.dot(above) / along / tanUp;
					left = Math.min(left, x);
					rightmost = Math.max(rightmost, x);
					low = Math.min(low, y);
					high = Math.max(high, y);
				}
			}
			if (behind)
			{
				radius *= 2;
				continue;
			}
			// Pan to the corners' middle at the pivot's distance, then come as close as the larger spread allows; never
			// under the ground.
			pivot.mulAdd(right, (left + rightmost) / 2 * tanAcross * radius).mulAdd(above, (low + high) / 2 * tanUp * radius);
			float spread = Math.max((rightmost - left) / 2, (high - low) / 2) / 0.86f;
			pivot.y = Math.max(0, pivot.y);
			radius *= Math.max(0.25f, Math.min(4, spread));
		}
		return radius;
	}

	/** The z of card {@code i}'s plane (-1: the sky's): the front at 0, the eye at {@code d}. */
	private float planeZ(int i, float d)
	{return -((i < 0 ? skyPlaced : placed[i]) - 1) * d;}

	/**
	 * {@code card} set to stand a plane {@code k} times the front's distance away, scaled by k about the eye's axis; with
	 * the cards all the front's size, by less as the slide goes into 3D ({@link #scaleOf}).
	 */
	private void cardAt(float k, float vw, float d)
	{
		float s = scaleOf(k);
		card.setToTranslationAndScaling(vw / 2 * (1 - s), 0, -(k - 1) * d, s, s, 1);
	}

	/** How much a card {@code k} front distances away is scaled: k, or, the cards all the front's size, toward 1 in 3D. */
	private float scaleOf(float k)
	{return LabControls.read(sizes) == 1 ? k + (1 - k) * eased : k;}

	/** The studio behind the page in 3D: a dark gradient, unseen at 0 behind the sky's card. */
	private void studio(float e)
	{
		if (!(e > 0))
			return;
		shapes.setProjectionMatrix(toWorld.setToOrtho2D(0, 0, 1, 1));
		beginShapes(ShapeType.Filled);
		// The page's own sky, darkened: its silhouettes read against it, and every page gets its own studio.
		Color sky = heart.topSquare != null ? heart.topSquare.bottomColor : Color.GRAY;
		Color top = new Color(sky).mul(0.42f), bottom = new Color(sky).mul(0.12f);
		top.a = bottom.a = e;
		shapes.rect(0, 0, 1, 1, bottom, bottom, top, top);
		shapes.end();
	}

	/**
	 * The ground: a dark floor under the cards, ruled across in the chosen unit (the numbers as tags on the side away
	 * from the eye), the camera's view opening on it from the eye, and each card's foot in its colour, the gap to the
	 * next card written between them on the eye's side.
	 */
	private void ground(float e, float vw, float d)
	{
		if (!(e > 0.01f))
			return;
		float cx = vw / 2, far = skyPlaced, halfFar = vw / 2 * scaleOf(far) * 1.3f;
		float zNear = d * 0.92f, zFar = -(far - 1) * d - d * 0.25f;
		// The ground's plane, y 0: x stays, the drawing's y runs along -z.
		toWorld.idt().rotate(1, 0, 0, -90);
		shapes.setProjectionMatrix(cardCombined.set(combined).mul(toWorld));
		beginShapes(ShapeType.Filled);
		Color nearC = new Color(0.1f, 0.12f, 0.18f, 0.92f * e), farC = new Color(0.06f, 0.07f, 0.11f, 0.0f);
		shapes.rect(cx - halfFar, -zNear, 2 * halfFar, zNear - zFar, nearC, nearC, farC, farC);
		shapes.end();
		beginLines();
		// The view's edges on the ground: from the eye, wider as they go.
		float spread = vw / 2 / d, thin = d * 0.006f;
		line(cx, 0, d, cx - spread * (d - zFar), 0, zFar, thin, 1, 1, 1, 0.22f * e);
		line(cx, 0, d, cx + spread * (d - zFar), 0, zFar, thin, 1, 1, 1, 0.22f * e);
		if (LabControls.read(units) == FRONTS)
			frontRules(e, vw, d, far, thin);
		else
			unitRules(e, vw, d, far, thin);
		// Each layer's foot, and the gap to the one in front of it.
		for (int i = 0; i < layers.size(); i++)
		{
			float k = placed[i], z = -(k - 1) * d, half = vw / 2 * scaleOf(k);
			Color accent = ACCENTS[i % ACCENTS.length];
			line(cx - half, 0, z, cx + half, 0, z, thin * 3, accent.r, accent.g, accent.b, 0.9f * e);
			if (i + 1 < layers.size() && distance[i] != Float.POSITIVE_INFINITY)
			{
				float k2 = placed[i + 1], z2 = -(k2 - 1) * d, x = cx + side * vw / 2 * Math.max(scaleOf(k), scaleOf(k2)) * 1.06f;
				line(x, 0, z, x, 0, z2, thin * 1.6f, accent.r, accent.g, accent.b, 0.55f * e);
				float gap = (distance[i] - distance[i + 1]) * perFront();
				tag(x, 0, (z + z2) / 2, "+" + unitText(gap), side > 0 ? "depth-gap" : "depth-gap depth-left", e, accent, 200 + i, "down");
			}
		}
		shapes.end();
	}

	/** A rule across the ground at {@code k} on the axis, numbered {@code text} on the side away from the eye, or not. */
	private void rule(float e, float vw, float d, float k, float thin, boolean strong, String text, int rank)
	{
		float cx = vw / 2, z = -(k - 1) * d, half = vw / 2 * scaleOf(k) * (strong ? 1.04f : 1);
		line(cx - half, 0, z, cx + half, 0, z, thin * (strong ? 2f : 1f), 1, 1, 1, (strong ? 0.45f : 0.16f) * e);
		if (text != null)
			tag(cx - side * half, 0, z, text, side > 0 ? "depth-rule" : "depth-rule depth-right", e, null, rank, "hide");
	}

	/** The rules in front distances: every whole one, a stronger line at the numbered ones. */
	private void frontRules(float e, float vw, float d, float far, float thin)
	{
		float deepest = 1;
		for (int i = 0; i < placed.length; i++)
			if (distance[i] != Float.POSITIVE_INFINITY)
				deepest = Math.max(deepest, distance[i]);
		int max = (int) Math.ceil(deepest);
		int every = max <= 6 ? 1 : max <= 14 ? 2 : max <= 40 ? 5 : max <= 80 ? 10 : 20;
		// Every whole distance a faint line (every few, past 40), every few a numbered one.
		int minor = every <= 5 ? 1 : every / 5;
		for (int t = 1; t <= max; t = t == 1 && minor > 1 ? minor : t + minor)
		{
			float k = place(t);
			if (k > far)
				break;
			boolean numbered = t == 1 || t % every == 0;
			rule(e, vw, d, k, thin, numbered, numbered ? t + "×" : null, 300 + t);
		}
	}

	/**
	 * The rules in metres or phones (r458): round numbers, 1, 2 and 5 times a power of ten on the log axis, a round step
	 * on the others, from just past the camera to the sky; a faint line halfway between two on the true axis.
	 */
	private void unitRules(float e, float vw, float d, float far, float thin)
	{
		float per = perFront(), low = distanceAt(0.12f) * per, high = distanceAt(far) * per;
		int rank = 300;
		if (LabControls.read(axis) == 2)
		{
			for (double ten = Math.pow(10, Math.floor(Math.log(low) / Math.log(10))); ten <= high; ten *= 10)
				for (int m : new int[] { 1, 2, 5 })
				{
					float v = (float) (m * ten);
					if (v >= low && v <= high)
						rule(e, vw, d, place(v / per), thin, true, unitText(v), rank++);
				}
			return;
		}
		float step = nice(high / 7);
		for (float v = step / 2; v <= high; v += step / 2)
		{
			boolean strong = Math.round(v / step * 2) % 2 == 0;
			if (v >= low && (strong || LabControls.read(axis) == 0))
				rule(e, vw, d, place(v / per), thin, strong, strong ? unitText(v) : null, rank++);
		}
	}

	/** The round number at or above {@code x}: 1, 2 or 5 times a power of ten. */
	private static float nice(float x)
	{
		double ten = Math.pow(10, Math.floor(Math.log(x) / Math.log(10))), f = x / ten;
		return (float) ((f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10) * ten);
	}

	/** The true distance standing at {@code k} on the axis: {@link #place}'s inverse. */
	private float distanceAt(float k)
	{
		switch (LabControls.read(axis))
		{
		case 1:
			return k * k;
		case 2:
			return (float) Math.exp((k - 1) / 1.4427f);
		default:
			return k;
		}
	}

	/** The page's gradients on the sky's card: as the heart draws them in the 2D frame, from its eye. */
	private void sky(float e, float vw, float vh, float d)
	{
		cardAt(skyPlaced, vw, d);
		float w = Gdx.graphics.getWidth(), h = Gdx.graphics.getHeight();
		toWorld.set(card).scale(vw / w, vh / h, 1);
		shapes.setProjectionMatrix(cardCombined.set(combined).mul(toWorld));
		beginShapes(ShapeType.Filled);
		if (heart.topSquare != null)
			heart.topSquare.draw(shapes);
		if (heart.bottomSquare != null)
			heart.bottomSquare.draw(shapes);
		shapes.end();
		frame(-1, e, vw, vh, d, new Color(1, 1, 1, 1));
		cardTag(skyPlaced, vw, vh, d, "∞\nsky", "depth-card depth-sky", e, null, layers.size());
	}

	/** Layer {@code i} on its card: the reader draws it alone through the card's matrix. */
	private int card(int i, float e, float vw, float vh, float d)
	{
		float k = placed[i];
		cardAt(k, vw, d);
		Color accent = ACCENTS[i % ACCENTS.length];
		// The card's view into the stencil, a pane of glass the layer is painted on: its tiles past the view's edges are
		// not drawn.
		Gdx.gl.glClear(GL20.GL_STENCIL_BUFFER_BIT);
		Gdx.gl.glEnable(GL20.GL_STENCIL_TEST);
		Gdx.gl.glStencilFunc(GL20.GL_ALWAYS, 1, 0xff);
		Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_REPLACE);
		shapes.setProjectionMatrix(cardCombined.set(combined).mul(card));
		beginShapes(ShapeType.Filled);
		shapes.setColor(accent.r * 0.5f + 0.5f, accent.g * 0.5f + 0.5f, accent.b * 0.5f + 0.5f, 0.1f * e);
		shapes.rect(0, 0, vw, vh);
		shapes.end();
		Gdx.gl.glStencilFunc(GL20.GL_EQUAL, 1, 0xff);
		Gdx.gl.glStencilOp(GL20.GL_KEEP, GL20.GL_KEEP, GL20.GL_KEEP);
		batch.setProjectionMatrix(combined);
		batch.setTransformMatrix(card);
		batch.begin();
		heart.parallaxReader.drawLayer(flatCamera, batch, i);
		batch.end();
		Gdx.gl.glDisable(GL20.GL_STENCIL_TEST);
		int calls = batch.renderCalls;
		batch.setTransformMatrix(toWorld.idt());
		frame(i, e, vw, vh, d, accent);
		ParallaxLayer layer = layers.get(i);
		// The distance large, the multiplier small under it (d21: visible, but subtle), then the name and speed; past five
		// layers the name and speed only in the readout.
		boolean fronts = LabControls.read(units) == FRONTS;
		String first = measured(distance[i]), more = (fronts ? "" : times(distance[i]) + " · ") + names[i] + " · speed "
				+ LabControls.text(Math.abs(layer.getParallaxSpeedRatioX()));
		String text = layers.size() <= 5 ? first + "\n" + more : fronts ? first : first + "\n" + times(distance[i]);
		cardTag(k, vw, vh, d, text, "depth-card", e, accent, layers.size() - 1 - i);
		return calls;
	}

	/**
	 * A card's tag (r458), over its top edge's corner on the side away from the eye: seen straight on, the nearer cards'
	 * corners stand higher and wider than the farther ones', a stair; seen from the side, the far corners stand clear of
	 * the cards in front. A corner off the screen (a near card seen from close) slides along the edge toward its middle
	 * until it is on.
	 */
	private void cardTag(float k, float vw, float vh, float d, String text, String kind, float e, Color accent, int rank)
	{
		float s = scaleOf(k), y = vh * s, z = -(k - 1) * d;
		for (int step = 0; step <= 8; step++)
		{
			float x = vw / 2 - side * vw / 2 * s * (1 - step / 8f);
			projected.set(x, y, z).prj(combined);
			if (step == 8 || Math.abs(projected.x) < 0.94f && Math.abs(projected.y) < 0.94f && ahead(x, y, z))
			{
				tag(x, y, z, text, kind, e, accent, rank, "up");
				return;
			}
		}
	}

	/** Whether the world point (x, y, z) is in front of the eye. */
	private boolean ahead(float x, float y, float z)
	{return (x - eye.x) * (target.x - eye.x) + (y - eye.y) * (target.y - eye.y) + (z - eye.z) * (target.z - eye.z) > 0;}

	/** A card's outline, in its accent: {@link #card}'s corners, each edge a {@link #line}. */
	private void frame(int i, float e, float vw, float vh, float d, Color accent)
	{
		if (!(e > 0.01f))
			return;
		float[] corners = new float[12];
		for (int c = 0; c < 4; c++)
		{
			projected.set(c == 1 || c == 2 ? vw : 0, c >= 2 ? vh : 0, 0).mul(card);
			corners[c * 3] = projected.x;
			corners[c * 3 + 1] = projected.y;
			corners[c * 3 + 2] = projected.z;
		}
		beginLines();
		for (int c = 0; c < 4; c++)
		{
			int a = c * 3, b = (c + 1) % 4 * 3;
			line(corners[a], corners[a + 1], corners[a + 2], corners[b], corners[b + 1], corners[b + 2], 0.06f * scaleOf(i < 0 ? skyPlaced : placed[i]),
					accent.r, accent.g, accent.b, 0.75f * e);
		}
		shapes.end();
	}

	/** The camera: its eye, and its view opening onto the front card's corners. */
	private void camera(float e, float vw, float vh, float d)
	{
		if (!(e > 0.01f))
			return;
		float cx = vw / 2, thin = d * 0.004f;
		beginLines();
		for (int c = 0; c < 4; c++)
			line(cx, 0, d, (c & 1) == 0 ? 0 : vw, (c & 2) == 0 ? 0 : vh, 0, thin, 1, 0.85f, 0.45f, 0.85f * e);
		shapes.end();
		shapes.setProjectionMatrix(combined);
		beginShapes(ShapeType.Filled);
		shapes.setColor(1, 0.85f, 0.45f, e);
		shapes.box(cx - 0.6f, 0, d + 0.6f, 1.2f, 0.9f, 1.2f);
		shapes.end();
		tag(cx, 0.9f, d, "camera · " + (LabControls.read(units) == FRONTS ? two(cameraDistance) + " units" : unitText(perFront())) + " from the front",
				"depth-camera", e, null, 100, "up");
	}

	/**
	 * The scale's references (r458): a person, {@link #PERSON} tall, standing on the ground beside the front card on the
	 * eye's side, a flat cut-out turned to face the eye so it never shows edge on, holding out a phone, {@link #PHONE}
	 * long, at its true size (d21: small, a second reference for the phone lengths).
	 */
	private void person(float e, float vw, float d)
	{
		if (!(e > 0.01f) || LabControls.read(units) == FRONTS)
			return;
		float h = PERSON / metresPerUnit, x = vw / 2 + side * (vw / 2 + h * 0.6f), z = 0;
		toWorld.setToTranslation(x, 0, z).rotateRad(0, 1, 0, (float) Math.atan2(eye.x - x, eye.z - z));
		shapes.setProjectionMatrix(cardCombined.set(combined).mul(toWorld));
		beginShapes(ShapeType.Filled);
		shapes.setColor(0.92f, 0.94f, 1f, 0.95f * e);
		shapes.rect(-0.13f * h, 0, 0.1f * h, 0.47f * h);
		shapes.rect(0.03f * h, 0, 0.1f * h, 0.47f * h);
		shapes.rect(-0.15f * h, 0.44f * h, 0.3f * h, 0.38f * h);
		shapes.rect(-0.22f * h, 0.47f * h, 0.07f * h, 0.33f * h);
		// The arm holding the phone: down to the elbow, then out.
		shapes.rect(0.15f * h, 0.62f * h, 0.07f * h, 0.18f * h);
		shapes.rect(0.15f * h, 0.6f * h, 0.16f * h, 0.06f * h);
		shapes.circle(0, 0.91f * h, 0.085f * h, 20);
		float ph = PHONE / metresPerUnit, pw = ph / 2, px = 0.29f * h, py = 0.6f * h;
		shapes.setColor(0.08f, 0.09f, 0.12f, e);
		shapes.rect(px, py, pw, ph);
		shapes.setColor(0.5f, 0.85f, 1f, e);
		shapes.rect(px + pw * 0.1f, py + ph * 0.06f, pw * 0.8f, ph * 0.88f);
		shapes.end();
		String size = LabControls.read(units) == PHONES ? metres(PERSON) + " = " + Math.round(PERSON / PHONE) + " phones" : metres(PERSON) + " · its phone " + Math.round(PHONE * 100) + " cm";
		tag(x, h * 1.05f, z, "a person · " + size, "depth-person", e, null, 150, "up");
	}

	/** Shapes begun in the back buffer's pixels, for {@link #line}. */
	private void beginLines()
	{
		shapes.setProjectionMatrix(toScreen.setToOrtho2D(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight()));
		beginShapes(ShapeType.Filled);
	}

	/**
	 * A line from (ax, ay, az) to (bx, by, bz) in the world (r458): cut where it passes behind the eye, then drawn on
	 * the screen as a quad {@code width} world units wide at each end, between {@link #LINE_MIN} and {@link #LINE_MAX} pixels; one that
	 * would be thinner fades instead, so a far rule seen edge on stays a line, not dashes. Between
	 * {@link #beginLines()} and the shapes' end.
	 */
	private void line(float ax, float ay, float az, float bx, float by, float bz, float width, float r, float g, float b, float a)
	{
		float[] m = combined.val;
		float acx = m[0] * ax + m[4] * ay + m[8] * az + m[12], acy = m[1] * ax + m[5] * ay + m[9] * az + m[13], acw = m[3] * ax + m[7] * ay + m[11] * az + m[15];
		float bcx = m[0] * bx + m[4] * by + m[8] * bz + m[12], bcy = m[1] * bx + m[5] * by + m[9] * bz + m[13], bcw = m[3] * bx + m[7] * by + m[11] * bz + m[15];
		float near = cameraDistance * 0.02f;
		if (acw < near && bcw < near)
			return;
		if (acw < near || bcw < near)
		{
			float t = (near - acw) / (bcw - acw);
			if (acw < near)
			{
				acx += (bcx - acx) * t;
				acy += (bcy - acy) * t;
				acw = near;
			}
			else
			{
				bcx = acx + (bcx - acx) * t;
				bcy = acy + (bcy - acy) * t;
				bcw = near;
			}
		}
		float w = Gdx.graphics.getBackBufferWidth() / 2f, h = Gdx.graphics.getBackBufferHeight() / 2f, pixels = projection.val[0] * w;
		float x1 = (acx / acw + 1) * w, y1 = (acy / acw + 1) * h, x2 = (bcx / bcw + 1) * w, y2 = (bcy / bcw + 1) * h;
		float dx = x2 - x1, dy = y2 - y1, length = (float) Math.sqrt(dx * dx + dy * dy);
		if (length < 0.01f)
			return;
		float w1 = width * pixels / acw, w2 = width * pixels / bcw;
		// Thinner than the least width: drawn at it, fainter by what it lacks.
		float faint = Math.min(1, (float) Math.sqrt(Math.max(w1, w2) / LINE_MIN));
		w1 = Math.max(LINE_MIN, Math.min(LINE_MAX, w1)) / 2;
		w2 = Math.max(LINE_MIN, Math.min(LINE_MAX, w2)) / 2;
		float nx = -dy / length, ny = dx / length;
		shapes.setColor(r, g, b, a * faint);
		shapes.triangle(x1 + nx * w1, y1 + ny * w1, x1 - nx * w1, y1 - ny * w1, x2 + nx * w2, y2 + ny * w2);
		shapes.triangle(x1 - nx * w1, y1 - ny * w1, x2 - nx * w2, y2 - ny * w2, x2 + nx * w2, y2 + ny * w2);
	}

	/** The shapes begun, blending: the batch turns blending off when it ends. */
	private void beginShapes(ShapeType type)
	{
		Gdx.gl.glEnable(GL20.GL_BLEND);
		Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
		shapes.begin(type);
	}

	/**
	 * A tag over the canvas at the world point (x, y, z), {@code e} its opacity; hidden behind the eye. {@link #spread}
	 * lays the tags out by {@code rank}, the lowest first, and moves one that would cover another {@code move}: "up",
	 * "down", or "hide".
	 */
	private void tag(float x, float y, float z, String text, String kind, float e, Color accent, int rank, String move)
	{
		if (overlay == null || !(e > 0.02f))
			return;
		projected.set(x, y, z);
		// Behind the eye: not shown.
		Vector3 toPoint = new Vector3(x, y, z).sub(eye), ahead = new Vector3(target).sub(eye);
		if (toPoint.dot(ahead) <= 0)
			return;
		projected.prj(combined);
		if (Math.abs(projected.x) > 1.2f || Math.abs(projected.y) > 1.2f)
			return;
		Element tag;
		if (tagsUsed < tags.size())
			tag = tags.get(tagsUsed);
		else
		{
			tag = Document.get().createDivElement();
			overlay.appendChild(tag);
			tags.add(tag);
		}
		tagsUsed++;
		tag.setClassName("depth-tag " + kind);
		tag.setInnerText(text);
		tag.getStyle().setProperty("left", (projected.x + 1) * 50 + "%");
		tag.getStyle().setProperty("top", (1 - projected.y) * 50 + "%");
		tag.getStyle().setProperty("opacity", String.valueOf(Math.min(1, e * 1.4f)));
		tag.getStyle().setProperty("borderColor", accent == null ? "" : "rgb(" + (int) (accent.r * 255) + "," + (int) (accent.g * 255) + "," + (int) (accent.b * 255) + ")");
		tag.getStyle().setProperty("display", "");
		tag.setAttribute("data-rank", String.valueOf(rank));
		tag.setAttribute("data-move", move);
	}

	/**
	 * No tag over another (r449), readable whichever way the page is turned (r458). The tags are taken by rank, the
	 * front card's first; each stays where {@link #tag} put it unless it covers one already laid or the page's chip, or
	 * the stage cuts it. Then it takes the nearest free place, its way ("up" or "down") cheaper than the other and
	 * sideways, within a few tag heights, else it is hidden; a rule's number ("hide") is hidden at once. A leader line
	 * in its colour runs from what it labels to the tag, every tag but a rule's: the card's, the sky's, the camera's,
	 * the person's at once, a gap's once moved. One measure of every tag, then numbers only.
	 */
	private static native void spread(Element overlay, Element labels) /*-{
		var GAP = 4, laid = [], tags = [];
		var svgNs = 'http://www.w3.org/2000/svg';
		var svg = overlay.querySelector('svg.depth-leaders');
		if (!svg)
		{
			svg = $doc.createElementNS(svgNs, 'svg');
			svg.setAttribute('class', 'depth-leaders');
			overlay.insertBefore(svg, overlay.firstChild);
		}
		while (svg.firstChild)
			svg.removeChild(svg.firstChild);
		var chips = labels.children;
		for (var c = 0; c < chips.length; c++)
			laid.push(chips[c].getBoundingClientRect());
		var kids = overlay.querySelectorAll('.depth-tag');
		for (var i = 0; i < kids.length; i++)
		{
			kids[i].style.marginTop = '';
			kids[i].style.marginLeft = '';
			if (kids[i].style.display !== 'none')
				tags.push(kids[i]);
		}
		tags.sort(function (a, b) { return a.getAttribute('data-rank') - b.getAttribute('data-rank'); });
		var boxes = tags.map(function (t) { return t.getBoundingClientRect(); });
		var stage = overlay.getBoundingClientRect();
		// The places a tag may take, nearest first: on an 8 px grid, up (its way) cheaper than sideways, down dearer.
		var offsets = $wnd.depthTagOffsets;
		if (!offsets)
		{
			offsets = [];
			for (var x = -240; x <= 240; x += 8)
				for (var y = -160; y <= 160; y += 8)
					offsets.push([x, y, Math.sqrt(x * x + (y < 0 ? 0.6 * y * y : 2.2 * y * y))]);
			offsets.sort(function (a, b) { return a[2] - b[2]; });
			$wnd.depthTagOffsets = offsets;
		}
		function fits(b, dx, dy)
		{
			if (b.left + dx < stage.left || b.right + dx > stage.right || b.top + dy < stage.top || b.bottom + dy > stage.bottom)
				return false;
			for (var j = 0; j < laid.length; j++)
			{
				var o = laid[j];
				if (b.left + dx < o.right + GAP && o.left < b.right + dx + GAP && b.top + dy < o.bottom + GAP && o.top < b.bottom + dy + GAP)
					return false;
			}
			return true;
		}
		for (var i = 0; i < tags.length; i++)
		{
			var b = boxes[i], move = tags[i].getAttribute('data-move'), at = null;
			if (fits(b, 0, 0))
				at = [0, 0];
			else if (move !== 'hide')
				for (var c = 1; c < offsets.length && !at; c++)
				{
					var dy = move === 'down' ? -offsets[c][1] : offsets[c][1];
					if (fits(b, offsets[c][0], dy))
						at = [offsets[c][0], dy];
				}
			if (!at)
			{
				tags[i].style.display = 'none';
				continue;
			}
			tags[i].style.marginLeft = at[0] + 'px';
			tags[i].style.marginTop = at[1] + 'px';
			var box = { left: b.left + at[0], right: b.right + at[0], top: b.top + at[1], bottom: b.bottom + at[1] };
			laid.push(box);
			// The leader: from the point the tag labels to the nearest point of its box.
			if (move === 'hide' || (move === 'down' && at[0] === 0 && at[1] === 0))
				continue;
			var ax = stage.width * parseFloat(tags[i].style.left) / 100, ay = stage.height * parseFloat(tags[i].style.top) / 100;
			var tx = Math.max(box.left - stage.left, Math.min(box.right - stage.left, ax)), ty = Math.max(box.top - stage.top, Math.min(box.bottom - stage.top, ay));
			var colour = tags[i].style.borderColor || 'rgba(255, 255, 255, 0.7)', opacity = tags[i].style.opacity;
			var lead = $doc.createElementNS(svgNs, 'line');
			lead.setAttribute('x1', ax);
			lead.setAttribute('y1', ay);
			lead.setAttribute('x2', tx);
			lead.setAttribute('y2', ty);
			lead.setAttribute('stroke', colour);
			lead.setAttribute('stroke-width', '1.5');
			lead.setAttribute('opacity', opacity);
			svg.appendChild(lead);
			var dot = $doc.createElementNS(svgNs, 'circle');
			dot.setAttribute('cx', ax);
			dot.setAttribute('cy', ay);
			dot.setAttribute('r', '2.5');
			dot.setAttribute('fill', colour);
			dot.setAttribute('opacity', opacity);
			svg.appendChild(dot);
		}
	}-*/;

	private void hideTags()
	{
		for (int i = tagsUsed; i < tags.size(); i++)
			tags.get(i).getStyle().setProperty("display", "none");
	}

	/** Drag: turn round the page; the wheel: closer or farther; space: the slide. */
	private final class Orbit extends InputAdapter
	{
		private int lastX, lastY;

		@Override
		public boolean touchDown(int screenX, int screenY, int pointer, int button)
		{
			lastX = screenX;
			lastY = screenY;
			return true;
		}

		@Override
		public boolean touchDragged(int screenX, int screenY, int pointer)
		{
			float dx = screenX - lastX, dy = screenY - lastY;
			lastX = screenX;
			lastY = screenY;
			if (slide < 1 && goal != 1)
				goal = 1;
			set(yaw, LabControls.read(yaw) - dx * 0.25f, -85, 85);
			set(pitch, LabControls.read(pitch) + dy * 0.2f, 2, 80);
			return true;
		}

		@Override
		public boolean scrolled(float amountX, float amountY)
		{
			set(closer, LabControls.read(closer) * (float) Math.pow(1.1, -amountY), 0.3f, 2.5f);
			return true;
		}

		@Override
		public boolean keyDown(int keycode)
		{
			if (keycode != com.badlogic.gdx.Input.Keys.SPACE)
				return false;
			toggle();
			return true;
		}

		private void set(InputElement slider, float value, float min, float max)
		{slider.setValue(LabControls.text(Math.max(min, Math.min(max, value))));}
	}

	@Override
	public void dispose()
	{
		batch.dispose();
		shapes.dispose();
	}
}
