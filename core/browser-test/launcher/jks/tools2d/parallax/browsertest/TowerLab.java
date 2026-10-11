package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Utils_Page_Json;

/**
 * The tower lab (r328): r276's tower, an IMAGE layer wrapped round a cylinder in the view's middle, drawn by the reader
 * itself ({@link ParallaxLayer#drawTower}), turning as the page scrolls. Its page is the tower round's w01
 * (core/test-data/tower/make_round.py): Hiver's set behind a brick tower. The sliders set the tower layer live: its
 * radius (percent of the world width), its shade, its speed ratio (how fast the scroll turns it) and how many images go
 * round it (its width: 2 pi r over that many); the camera scroll moves the page. One panel, the whole world. Opened with
 * effects-lab.html?lab=tower: tools/effects-lab.sh --lab tower.
 * <p>
 * {@code &clip=1}: nothing moves by itself, {@code window.effectsLabAct(seconds)} steps the heart at 1/60 s.
 */
public class TowerLab extends ApplicationAdapter implements LabKit.Clip
{
	static final String PAGE = "tower-lab/lab.jplax";
	static final float WORLD_WIDTH = FogLab.WORLD_WIDTH, WORLD_HEIGHT = FogLab.WORLD_HEIGHT;
	/** As FloorLab's: the whole world, 16:9, as wide as fits over the sliders at 1580 x 800. */
	static final int FRAME_WIDTH = FloorLab.FRAME_WIDTH, FRAME_HEIGHT = FloorLab.FRAME_HEIGHT;
	/** What the lab opens on: w01's tower (20%, shade 0.65, six images round), a turn about every 12 s at scroll 40. */
	static final float RADIUS = 20, SHADE = 0.65f, SPEED = 0.1f, AROUND = 6, SCROLL = 40;

	private Parallax_Heart heart;
	private ParallaxLayer tower;
	private SpriteBatch batch;
	private InputElement radius, shade, speed, around, scroll;
	private Element readout;
	private boolean clip;
	private float frameSeconds, frames;
	private int drawCalls;
	private String costLine = "";

	@Override
	public void create()
	{
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		batch = new SpriteBatch();
		OrthographicCamera camera = new OrthographicCamera();
		camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
		heart = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
		heart.setPage(Utils_Page_Json.loadPage(PAGE));
		for (ParallaxLayer layer : heart.parallaxReader.layers)
			if (layer.isTower())
				tower = layer;
		if (tower == null)
			throw new IllegalStateException(PAGE + " has no tower layer");
		buildControls();
		applyControls();
		if (clip)
			LabKit.exportClip("effectsLab", this);
	}

	private void buildControls()
	{
		Document document = Document.get();
		document.setTitle("Effects lab: tower (r328)");
		document.getElementById("effects-title").setInnerText("Effects lab: tower");
		document.getElementById("effects-lab").getStyle().setProperty("maxWidth", FRAME_WIDTH + "px");
		document.getElementById("effects-labels").getStyle().setProperty("gridTemplateColumns", "1fr");
		LabControls.panelLabels(document, "effects-labels", "A brick tower in front of Hiver, turning as the page scrolls");
		Element controls = document.getElementById("effects-controls");
		radius = LabControls.slider(document, controls, "towerRadius", "radius (percent of the world width; 20 is 8 units, the tower 16 wide)", 2, 50, 0.5f, RADIUS);
		shade = LabControls.slider(document, controls, "towerShade", "shade (how much the wall darkens as it turns away: 0 flat, 1 black at its sides)", 0, 1, 0.01f, SHADE);
		speed = LabControls.slider(document, controls, "towerSpeed", "tower's speed (ratio: how fast the scroll turns it; the trees in front of the peaks are 0.04)", 0, 0.4f, 0.001f, SPEED);
		around = LabControls.slider(document, controls, "imagesRound", "images round (how many bricks images go round the tower: each is 2 pi r over that many wide, and as tall for its shape)", 1, 12, 1, AROUND);
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll (units a second)", -160, 160, 5, SCROLL);
		readout = LabControls.readout(document, controls, "effects-readout");
	}

	/** The tower layer as the sliders say, and the readout. */
	private void applyControls()
	{
		float r = LabControls.read(radius), n = Math.max(1, LabControls.read(around)), s = LabControls.read(speed);
		float sc = LabControls.read(scroll);
		float units = r * WORLD_WIDTH / 100, circumference = (float) (2 * Math.PI) * units;
		tower.setTower(r, LabControls.read(shade));
		tower.setSizeRatio(circumference / n / WORLD_WIDTH);
		tower.setParallaxSpeedRatioX(s);
		heart.screenSpeedConstantX = sc;
		float turn = Math.abs(s * sc) > 1e-4f ? circumference / Math.abs(s * sc) : 0;
		readout.setInnerText("radius " + two(units) + " units (" + two(2 * units) + " wide), " + Math.round(n) + " images round, each "
				+ two(tower.getRegionWidth()) + " by " + two(tower.getRegionHeight()) + " units; "
				+ (turn > 0 ? "a turn every " + two(turn) + " s" : "not turning")
				+ "; " + costLine);
	}

	/** {@code value} to two decimals, for the readout. */
	private static String two(float value)
	{return String.valueOf(Math.round(value * 100) / 100.0);}

	/** Steps the heart by {@code seconds}, at 1/60 s: the clip's clock. */
	@Override
	public void act(float seconds)
	{
		applyControls();
		LabKit.clip(seconds, heart);
		applyControls();
	}

	@Override
	public void render()
	{
		if (!clip)
		{
			applyControls();
			LabKit.act(LabKit.liveDelta(), heart);
		}
		LabKit.clear();
		heart.render();
		drawCalls = batch.renderCalls;
		frames++;
		frameSeconds += Gdx.graphics.getDeltaTime();
		if (frameSeconds >= 1 || clip)
		{
			costLine = (clip ? "" : "frame " + LabControls.text(1000 * frameSeconds / frames) + " ms; ") + drawCalls + " draw calls";
			frames = frameSeconds = 0;
		}
	}

	@Override
	public void dispose()
	{batch.dispose();}
}
