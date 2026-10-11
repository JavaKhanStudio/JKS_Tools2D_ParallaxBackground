package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.SelectElement;

import jks.tools2d.parallax.LayerHook;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Utils_Page_Json;

/**
 * The floor lab (r275, Simon: "gimme a lab so I can test this ... I need to feel it"): r266's floor, a ground drawn as
 * horizontal strips, each row at its own depth, tried before the reader gets it. Its page (core/test-data/floor/
 * make_lab.py) is Hiver's four regions standing on a ground, the ground an EMPTY layer named "floor" whose box (the
 * bottom 40% of the world) this lab's hook draws in. A draws it row by row: the top row scrolls at the top row's speed
 * (by default the mountain's, the layer behind), the bottom row at the bottom row's (nearer than the trees), the rows
 * between at a speed straight in the row's height, which is 1 / its depth in a perspective; B draws the same ground as
 * a page does today, one image at one speed, the mean of the two. Every strip is one
 * {@code batch.draw(region, x, y, width, height)}, the call JmeBatch implements, so what A shows is what the reader
 * would draw.
 * <p>
 * Its choices: the ground's art (stones seen from above, or the same painted in perspective, make_lab.py's painted.png,
 * as a fighting game's floor is drawn) and how the rows
 * map it: shifted only (line scroll: the image as painted, each row at its speed); shifted and stretched by their depth;
 * or mode 7 (stretched, and the image's rows spaced by depth too, so the ground recedes); and the parallax set, Hiver on
 * snow or round1's PurpleFairy on a violet ground (Simon: "let's try it again with another parallax"). Every layer in
 * front of the ground is planted (Simon picked it over the page's own speeds, which slid the ground under the trees): it
 * scrolls at the speed of the ground row under its foot, {@link #plant()}. Drag the canvas, or hold the
 * arrow keys, to pan; the scroll slider pans by itself. Opened with effects-lab.html?lab=floor:
 * tools/effects-lab.sh --lab floor.
 * <p>
 * {@code &clip=1}: nothing moves by itself, {@code window.effectsLabAct(seconds)} steps both hearts at 1/60 s.
 */
public class FloorLab extends ApplicationAdapter
{
	static final String FLOOR = "floor";
	/**
	 * The parallax sets (core/test-data/floor/make_lab.py): Hiver on snow, and round1's PurpleFairy on a violet ground
	 * (Simon: "let's try it again with another parallax"), with the top and bottom row speeds each opens on: the top
	 * row at the speed of the layer whose foot is the horizon, the bottom row nearer than the front layers.
	 */
	static final String[] SET_PAGES = { "floor-lab/lab.jplax", "floor-lab/lab-purple.jplax" };
	static final String[] SET_NAMES = { "Hiver", "PurpleFairy" };
	static final float[] SET_TOP = { 0.016f, 0.0224f }, SET_BOTTOM = { 0.06f, 0.13f };
	static final float WORLD_WIDTH = FogLab.WORLD_WIDTH, WORLD_HEIGHT = FogLab.WORLD_HEIGHT;
	/** Both panels show the whole world: 760 x 428 px each, side by side. */
	static final int FRAME_WIDTH = FogLab.FRAME_WIDTH, FRAME_HEIGHT = FogLab.FRAME_HEIGHT;
	static final String[] ART_NAMES = { "from above", "painted as seen" };
	static final String[] MAPPING_NAMES = { "mode 7", "shift + stretch", "line scroll" };
	/** What the lab opens on: the top row at the mountain's speed, the bottom row nearer than the trees (0.04). */
	static final float TOP = 0.016f, BOTTOM = 0.06f, ROWS = 96, SCROLL = 40, STONE_WIDTH = 16, TILT = 0.6f;
	static final int MAX_ROWS = 256;

	/** Per set, per panel (A, B). */
	private final Parallax_Heart[][] hearts = new Parallax_Heart[SET_PAGES.length][2];
	private final Floor[][] floors = new Floor[SET_PAGES.length][2];
	/** Each set's ground art: the stones from above, and painted as seen. */
	private final TextureRegion[] stones = new TextureRegion[SET_PAGES.length], painted = new TextureRegion[SET_PAGES.length];
	/** The heart the lab draws in panel A ({@code [0]}) and B, the picked set's. */
	private Parallax_Heart[] shown;
	/**
	 * {@code &unplanted=1}: the layers in front of the ground keep the page's speeds, as before Simon picked planting
	 * (r275): tools/r275-floor/planted.py's proof that it sees a slide. Not a control: Simon kept planting only.
	 */
	private boolean unplanted;
	private int shownSet = -1;
	private SpriteBatch batch;
	private InputElement top, bottom, rows, scroll;
	private SelectElement set, art, mapping;
	private Element readout;
	private boolean clip;
	/** How far the view has travelled: a layer of speed ratio s has moved by -s * travel, as act() moves it. */
	private float travel;
	/** Where the pointer was last frame, in canvas pixels. */
	private int lastPointer;
	private float frameSeconds, frames;
	private final int[] panelCalls = new int[2];
	private String costLine = "";

	/** The hook drawing the ground of one panel, once a frame (the reader calls it once per tile of the EMPTY layer). */
	final class Floor implements LayerHook
	{
		final boolean flat;
		final TextureRegion[] strips = new TextureRegion[MAX_ROWS];
		boolean drawn;

		Floor(boolean flat)
		{
			this.flat = flat;
			for (int i = 0; i < MAX_ROWS; i++)
				strips[i] = new TextureRegion();
		}

		@Override
		public void draw(Batch batch, ParallaxLayer layer, float x, float y, float width, float height)
		{
			if (drawn)
				return;
			drawn = true;
			drawFloor(batch, this, y, height);
		}
	}

	@Override
	public void create()
	{
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		unplanted = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("unplanted"));
		batch = new SpriteBatch();
		for (int k = 0; k < SET_PAGES.length; k++)
		{
			for (int i = 0; i < 2; i++)
			{
				OrthographicCamera camera = new OrthographicCamera();
				camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
				hearts[k][i] = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
				hearts[k][i].setPage(Utils_Page_Json.loadPage(SET_PAGES[k]));
				hearts[k][i].parallaxReader.setLayerHook(FLOOR, floors[k][i] = new Floor(i == 1));
			}
			TextureAtlas atlas = hearts[k][0].currentPage.getLoadedAtlas();
			stones[k] = atlas.findRegion("ground");
			painted[k] = atlas.findRegion("painted");
			if (stones[k] == null || painted[k] == null)
				throw new IllegalStateException(SET_PAGES[k] + "'s atlas has no ground or no painted");
		}
		buildControls();
		pickSet();
		if (clip)
			exportAct(this);
	}

	private void buildControls()
	{
		Document document = Document.get();
		document.setTitle("Effects lab: floor (r275)");
		document.getElementById("effects-title").setInnerText("Effects lab: floor");
		LabControls.panelLabels(document, "effects-labels", "A: the ground row by row, each row at its depth", "B: the ground as today, one image at one speed");
		Element controls = document.getElementById("effects-controls");
		set = LabControls.choice(document, controls, "parallaxSet", "parallax set (Hiver on snow, or round1's PurpleFairy on a violet ground; picking one puts the row speeds back to its own)", SET_NAMES, 0);
		top = LabControls.slider(document, controls, "topRowSpeed", "top row's speed (ratio; Hiver's mountain behind is 0.016, PurpleFairy's back trees 0.0224)", 0, 0.08f, 0.0002f, TOP);
		bottom = LabControls.slider(document, controls, "bottomRowSpeed", "bottom row's speed (ratio; the layers in front take the speed of the row under their foot)", 0, 0.16f, 0.001f, BOTTOM);
		rows = LabControls.slider(document, controls, "strips", "strips (the rows the ground is cut in)", 2, MAX_ROWS, 1, ROWS);
		art = LabControls.choice(document, controls, "groundArt", "ground's art (the stones drawn from above, or the same stones painted in perspective, as a fighting game's floor is)", ART_NAMES, 0);
		mapping = LabControls.choice(document, controls, "rowMapping", "mapping (mode 7: stretched by depth, the image's rows spaced by depth; shift + stretch: each row stretched, the image as painted; line scroll: each row only shifted)", MAPPING_NAMES, 0);
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll (drag the picture, or the arrow keys, to pan)", -160, 160, 5, SCROLL);
		readout = LabControls.readout(document, controls, "effects-readout");
	}

	/**
	 * The ground in the box {@code y}, {@code height}, as strips. Row i of n covers the box's heights i/n to (i+1)/n; its
	 * speed is the bottom row's at 0, the top row's at 1, straight between (in a perspective, a row's height over the
	 * horizon goes as 1 / its depth, and so does its speed). Stretched, a row's tiles are as wide as its speed over the
	 * bottom row's, about the view's middle. Mode 7 spaces the image's rows by depth too: depth goes as 1 / speed, and the
	 * ground runs {@code (depth - 1) * near} along from the bottom row, {@code near} set so a step up the bottom rows
	 * crosses the ground {@link #TILT} times as fast as a step across.
	 */
	private void drawFloor(Batch batch, Floor floor, float y, float height)
	{
		float sTop = LabControls.read(top), sBottom = Math.max(1e-4f, LabControls.read(bottom));
		int n = Math.max(2, Math.min(MAX_ROWS, Math.round(LabControls.read(rows))));
		TextureRegion image = LabControls.read(art) == 0 ? stones[shownSet] : painted[shownSet];
		int map = LabControls.read(mapping);
		boolean stretch = !floor.flat && map < 2, mode7 = !floor.flat && map == 0;
		float flatSpeed = (sTop + sBottom) / 2;
		// A tile across: the stones 16 units, the painted ground as wide as it is painted, the box's height tall.
		float tileWidth = image == stones[shownSet] ? STONE_WIDTH : height * image.getRegionWidth() / image.getRegionHeight();
		float tileDepth = image == stones[shownSet] ? STONE_WIDTH : tileWidth;
		float near = sBottom > sTop ? height * sBottom / (TILT * (sBottom - sTop)) : 0;
		float viewLeft = 0, viewRight = WORLD_WIDTH, middle = WORLD_WIDTH / 2;
		int w = image.getRegionWidth(), h = image.getRegionHeight();
		float rowHeight = height / n;
		for (int i = 0; i < n; i++)
		{
			float t0 = (float) i / n, t1 = (float) (i + 1) / n, tc = (i + 0.5f) / n;
			float speed = floor.flat ? flatSpeed : sBottom + (sTop - sBottom) * tc;
			float k = stretch ? Math.max(0.02f, speed / sBottom) : 1;
			// Which rows of the image: t0..t1 of it, or in mode 7 where the ground is at those depths.
			float v0, v1;
			if (mode7 && near > 0)
			{
				v0 = along(sBottom, sTop, t0, near) / tileDepth;
				v1 = along(sBottom, sTop, t1, near) / tileDepth;
				float start = v0 - (float) Math.floor(v0);
				v1 = Math.min(1, start + (v1 - v0));
				v0 = start;
			}
			else
			{
				v0 = t0;
				v1 = t1;
			}
			// The image's rows count down from its top; t up from its bottom.
			int srcTop = Math.round((1 - v1) * h), srcHeight = Math.max(1, Math.round((v1 - v0) * h));
			srcTop = Math.max(0, Math.min(h - srcHeight, srcTop));
			TextureRegion strip = floor.strips[i];
			strip.setRegion(image, 0, srcTop, w, srcHeight);
			float across = tileWidth * k;
			float origin = middle - k * middle - speed * travel;
			float first = origin + across * (float) Math.floor((viewLeft - origin) / across);
			float rowY = y + t0 * height;
			for (float x = first; x < viewRight; x += across)
				batch.draw(strip, x, rowY, across, rowHeight);
		}
	}

	/** How far along the ground the row at {@code t} is from the bottom row, in world units: (depth - 1) * near. */
	private static float along(float sBottom, float sTop, float t, float near)
	{
		float speed = sBottom + (sTop - sBottom) * t;
		return (sBottom / Math.max(1e-4f, speed) - 1) * near;
	}

	/**
	 * The speed a layer standing on the ground at {@code footY} scrolls at in panel {@code panel}: the ground row's
	 * there (A: straight between the bottom and top rows' speeds; B: the flat ground's), or its own when not planted.
	 */
	private float plantedSpeed(int panel, ParallaxLayer ground, ParallaxLayer layer)
	{
		float sTop = LabControls.read(top), sBottom = LabControls.read(bottom);
		if (panel == 1)
			return (sTop + sBottom) / 2;
		float boxY = ground.getDecalPercentY() * WORLD_HEIGHT / 100;
		float t = (footOf(layer) - boxY) / ground.getRegionHeight();
		t = Math.max(0, Math.min(1, t));
		return sBottom + (sTop - sBottom) * t;
	}

	/**
	 * Where a layer stands, in world units up: its decal, and over it the transparent rows the atlas trimmed from the
	 * image's bottom (PurpleFairy's pink trees: 95 of 1080), as the reader draws it.
	 */
	private static float footOf(ParallaxLayer layer)
	{
		float foot = layer.getDecalPercentY() * WORLD_HEIGHT / 100;
		TextureRegion region = layer.getRegion();
		if (region instanceof AtlasRegion && ((AtlasRegion) region).originalHeight > 0)
			foot += ((AtlasRegion) region).offsetY / ((AtlasRegion) region).originalHeight * layer.getRegionHeight();
		return foot;
	}

	/**
	 * Every layer in front of the ground moves at {@link #plantedSpeed}, placed where that speed has taken it since the
	 * lab opened ({@code -speed * travel} from its decal, as the ground's rows are), so a slider moved mid-scroll
	 * re-plants it instead of leaving it where its old speed had carried it.
	 */
	private void plant()
	{
		if (unplanted)
			return;
		for (int panel = 0; panel < 2; panel++)
		{
			java.util.ArrayList<ParallaxLayer> layers = shown[panel].parallaxReader.layers;
			int g = layers.size();
			while (--g >= 0 && !FLOOR.equals(layers.get(g).getName()))
				;
			if (g < 0)
				return;
			for (int i = g + 1; i < layers.size(); i++)
			{
				ParallaxLayer layer = layers.get(i);
				float speed = plantedSpeed(panel, layers.get(g), layer);
				layer.setParallaxSpeedRatioX(speed);
				float width = layer.getTotalWidth(), at = layer.getDecalPercentX() * WORLD_WIDTH / 100 - speed * travel;
				layer.setCurrentDistanceX(width > 0 ? at % width : at);
			}
		}
	}

	/** Moves the view by {@code by} travel units this frame, both hearts as act() moves their layers. */
	private void pan(float by, float delta)
	{
		travel += by;
		for (Parallax_Heart heart : shown)
			heart.screenSpeedConsumableX += by / delta;
	}

	private void applyControls()
	{
		pickSet();
		float sc = LabControls.read(scroll);
		for (Parallax_Heart heart : shown)
			heart.screenSpeedConstantX = sc;
		float sTop = LabControls.read(top), sBottom = LabControls.read(bottom);
		readout.setInnerText(SET_NAMES[shownSet] + ": A's rows from " + LabControls.text(sBottom) + " (bottom) to " + LabControls.text(sTop) + " (top), depth "
				+ LabControls.text(sTop > 0 ? sBottom / sTop : 0) + "x the bottom row's at the top; B's ground "
				+ LabControls.text((sTop + sBottom) / 2) + "; planted in front, back to front: A " + frontSpeeds(0) + ", B "
				+ frontSpeeds(1) + "; " + costLine);
	}

	/**
	 * Shows the set the choice picks, once it changes: its row speeds on the sliders, its hearts moved to where the
	 * view has travelled, so the switch does not jump the camera.
	 */
	private void pickSet()
	{
		int k = LabControls.read(set);
		if (k == shownSet)
			return;
		if (shownSet >= 0)
		{
			top.setValue(LabControls.text(SET_TOP[k]));
			bottom.setValue(LabControls.text(SET_BOTTOM[k]));
		}
		shownSet = k;
		shown = hearts[k];
		for (Parallax_Heart heart : shown)
			for (ParallaxLayer layer : heart.parallaxReader.layers)
			{
				float width = layer.getTotalWidth();
				float at = layer.getDecalPercentX() * WORLD_WIDTH / 100 - layer.getParallaxSpeedRatioX() * travel;
				layer.setCurrentDistanceX(width > 0 ? at % width : at);
			}
		plant();
	}

	/** The speeds the layers in front of the ground scroll at now in panel {@code panel}, back to front. */
	private String frontSpeeds(int panel)
	{
		java.util.ArrayList<ParallaxLayer> layers = shown[panel].parallaxReader.layers;
		String out = "";
		boolean front = false;
		for (ParallaxLayer layer : layers)
		{
			if (front)
				out += (out.isEmpty() ? "" : " ") + LabControls.text(layer.getParallaxSpeedRatioX());
			front |= FLOOR.equals(layer.getName());
		}
		return out;
	}

	/** Steps both hearts by {@code seconds}, at 1/60 s: the clip's clock. */
	void act(float seconds)
	{
		applyControls();
		for (float t = 0; t < seconds - 1e-4f; t += 1 / 60f)
			step(1 / 60f);
		applyControls();
	}

	private void step(float delta)
	{
		travel += LabControls.read(scroll) * delta;
		for (Parallax_Heart heart : shown)
			heart.act(delta);
		plant();
	}

	private static native void exportAct(FloorLab lab) /*-{
		$wnd.effectsLabAct = $entry(function(seconds) {
			lab.@jks.tools2d.parallax.browsertest.FloorLab::act(F)(seconds);
		});
		$wnd.effectsLabReady = true;
	}-*/;

	@Override
	public void render()
	{
		if (!clip)
		{
			applyControls();
			float delta = Math.max(1e-3f, Math.min(Gdx.graphics.getDeltaTime(), 1 / 15f));
			// A drag moves the ground's bottom row with the pointer; the arrow keys pan at 120 a second.
			float perPixel = WORLD_WIDTH / FRAME_WIDTH, sBottom = Math.max(1e-3f, LabControls.read(bottom));
			// The pointer's own position: GWT's getDeltaX() is not reset between frames, a still finger would keep panning.
			if (Gdx.input.isTouched())
			{
				int pointer = Gdx.input.getX();
				if (!Gdx.input.justTouched())
					pan(-(pointer - lastPointer) * perPixel / sBottom, delta);
				lastPointer = pointer;
			}
			if (Gdx.input.isKeyPressed(Input.Keys.LEFT))
				pan(-120 * delta, delta);
			if (Gdx.input.isKeyPressed(Input.Keys.RIGHT))
				pan(120 * delta, delta);
			step(delta);
		}
		int width = Gdx.graphics.getBackBufferWidth(), height = Gdx.graphics.getBackBufferHeight();
		Gdx.gl.glViewport(0, 0, width, height);
		Gdx.gl.glClearColor(0, 0, 0, 1);
		Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
		Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
		for (int i = 0; i < 2; i++)
		{
			// A on the left, B on the right, a 4 px gap.
			int x = i * (FRAME_WIDTH + 4);
			Gdx.gl.glScissor(x, height - FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);
			Gdx.gl.glViewport(x, height - FRAME_HEIGHT, FRAME_WIDTH, FRAME_HEIGHT);
			floors[shownSet][i].drawn = false;
			shown[i].render();
			panelCalls[i] = batch.renderCalls;
		}
		Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
		Gdx.gl.glViewport(0, 0, width, height);
		frames++;
		frameSeconds += Gdx.graphics.getDeltaTime();
		if (frameSeconds >= 1 || clip)
		{
			costLine = (clip ? "" : "frame " + LabControls.text(1000 * frameSeconds / frames) + " ms; ") + "A " + panelCalls[0] + " draw calls, B "
					+ panelCalls[1];
			frames = frameSeconds = 0;
		}
	}

	@Override
	public void dispose()
	{batch.dispose();}
}
