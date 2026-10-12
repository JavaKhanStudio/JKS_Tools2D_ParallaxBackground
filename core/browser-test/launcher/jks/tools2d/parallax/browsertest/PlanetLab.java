package jks.tools2d.parallax.browsertest;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.InputElement;
import com.google.gwt.dom.client.SelectElement;

import jks.tools2d.parallax.LayerHook;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.heart.Parallax_Heart;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ParticleAnchor;
import jks.tools2d.parallax.pages.Utils_Page_Json;

/**
 * The planet lab (r330, r347): a page round its planet, the side of a world turning round one centre under the view,
 * with a snowfall (a PARTICLES layer) and a game's towers (an EMPTY layer, drawn by a hook) among its layers, so the
 * ways those follow the ground can be seen moving. The page is the effects lab's "all" one (the haze lab's s01: Hiver,
 * towers, snow, a FOG mist, WAVE trees). The reader turns each snow effect as one block, as the middle of its layer's
 * box turns; the hook asks where a point goes and how far it leans ({@link ParallaxPageReader#planetPlaceAt},
 * {@link ParallaxPageReader#planetAngleAt}), and the towers choice shows what a hook can do with it: stand each tower
 * on its own foot, turn its whole tile about the tile's middle, or ignore it. The planet radius at 0 is the flat page.
 * One panel, the whole world, the page lowered so its ground is at the frame's bottom. Opened with effects-lab.html?lab=planet: tools/effects-lab.sh --lab planet.
 * <p>
 * {@code &clip=1}: nothing moves by itself, {@code window.effectsLabAct(seconds)} steps the heart at 1/60 s.
 */
public class PlanetLab extends ApplicationAdapter implements LabKit.Clip
{
	static final String PAGE = EffectsLab.ALL_PAGE;
	static final float WORLD_WIDTH = FogLab.WORLD_WIDTH, WORLD_HEIGHT = FogLab.WORLD_HEIGHT;
	/** As FloorLab's: the whole world, 16:9, as wide as fits over the sliders at 1580 x 800. */
	static final int FRAME_WIDTH = FloorLab.FRAME_WIDTH, FRAME_HEIGHT = FloorLab.FRAME_HEIGHT;
	/** What the lab opens on: a planet of 400% (160 units, the planet round's p08), scrolling at 40 units a second. */
	static final float RADIUS = 400, SCROLL = 40;
	static final String[] TOWER_NAMES = { "each on its own foot", "the tile as one block", "flat (the hook ignores it)" };
	static final String[] ANCHOR_NAMES = { "VIEW", "LAYER" };
	/**
	 * How far down the lab sets the page's layers, in percent of the world height: s01 sits in the upper world (the
	 * effects lab shows its top), here its ground at the frame's bottom. Not the snow, which falls from the view's top.
	 */
	static final float LOWER = 32;
	/** The page's EMPTY layer, drawn by {@link #towers}. */
	static final String TOWERS = "towers";

	private Parallax_Heart heart;
	private ParallaxLayer snow, towerLayer;
	private SpriteBatch batch;
	private Texture pixel;
	private TextureRegion block;
	private InputElement radius, scroll;
	private SelectElement towersChoice, anchor;
	private Element readout;
	private boolean clip;
	private int towerMode;
	private float frameSeconds, frames;
	private int drawCalls;
	private String costLine = "";
	private final Vector2 place = new Vector2();

	/**
	 * The effects lab's towers: dark boxes across each tile the reader hands it, each placed and turned as
	 * {@link #towerMode} says by where the reader puts its foot or the tile's.
	 */
	private final LayerHook towers = new LayerHook()
	{
		/** Each tower: left and width in the tile's width, height in its height. */
		final float[] shape = { 0.05f, 0.12f, 0.9f, 0.25f, 0.08f, 0.55f, 0.45f, 0.18f, 1.0f, 0.75f, 0.1f, 0.7f };

		@Override
		public void draw(Batch batch, ParallaxLayer layer, float x, float y, float width, float height)
		{
			ParallaxPageReader reader = heart.parallaxReader;
			Color c = batch.getColor();
			batch.setColor(c.r * 0.15f, c.g * 0.135f, c.b * 0.195f, c.a);
			float middle = x + width / 2, tile = towerMode == 1 ? reader.planetAngleAt(layer, middle) : 0;
			float cos = MathUtils.cosDeg(tile), sin = MathUtils.sinDeg(tile);
			if (towerMode == 1)
				reader.planetPlaceAt(layer, middle, y, place);
			for (int i = 0; i < shape.length; i += 3)
			{
				float wide = shape[i + 1] * width, foot = x + shape[i] * width + wide / 2, tall = shape[i + 2] * height;
				if (towerMode == 2)
				{
					batch.draw(block, foot - wide / 2, y, wide, tall);
					continue;
				}
				float angle = tile, footX = place.x + (foot - middle) * cos, footY = place.y + (foot - middle) * sin;
				if (towerMode == 0)
				{
					reader.planetPlaceAt(layer, foot, y, place);
					footX = place.x;
					footY = place.y;
					angle = reader.planetAngleAt(layer, foot);
				}
				batch.draw(block, footX - wide / 2, footY, wide / 2, 0, wide, tall, 1, 1, angle);
			}
		}
	};

	@Override
	public void create()
	{
		clip = "1".equals(com.google.gwt.user.client.Window.Location.getParameter("clip"));
		batch = new SpriteBatch();
		Pixmap white = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
		white.setColor(Color.WHITE);
		white.fill();
		pixel = new Texture(white);
		white.dispose();
		block = new TextureRegion(pixel);
		OrthographicCamera camera = new OrthographicCamera();
		camera.setToOrtho(false, WORLD_WIDTH, WORLD_HEIGHT);
		heart = new Parallax_Heart(camera, batch, WORLD_WIDTH, WORLD_HEIGHT);
		heart.setPage(Utils_Page_Json.loadPage(PAGE));
		heart.parallaxReader.setLayerHook(TOWERS, towers);
		// The page's own fog (a format 9 haze, read as a strength) whitens its far layers: off, as in the effects lab.
		heart.parallaxReader.setFog(0, null);
		for (ParallaxLayer layer : heart.parallaxReader.layers)
		{
			if (layer.getKind() != Enum_LayerKind.PARTICLES)
				layer.setDecalPercentY(layer.getDecalPercentY() - LOWER);
			if (layer.getKind() == Enum_LayerKind.PARTICLES)
				snow = layer;
			else if (layer.getKind() == Enum_LayerKind.EMPTY)
				towerLayer = layer;
		}
		if (snow == null || towerLayer == null)
			throw new IllegalStateException(PAGE + " has no snow or no towers");
		heart.parallaxReader.resetPositions();
		buildControls();
		applyControls();
		if (clip)
			LabKit.exportClip("effectsLab", this);
	}

	private void buildControls()
	{
		Document document = Document.get();
		document.setTitle("Effects lab: planet (r330)");
		document.getElementById("effects-title").setInnerText("Effects lab: planet");
		document.getElementById("effects-lab").getStyle().setProperty("maxWidth", FRAME_WIDTH + "px");
		document.getElementById("effects-labels").getStyle().setProperty("gridTemplateColumns", "1fr");
		LabControls.panelLabels(document, "effects-labels", "Hiver round a planet: snow, a game's towers");
		Element controls = document.getElementById("effects-controls");
		radius = LabControls.slider(document, controls, "planetRadius",
				"planet radius (percent of the world width; 0 is flat, the smaller the rounder)", 0, 1500, 10, RADIUS);
		towersChoice = LabControls.choice(document, controls, "towersHook",
				"towers (the game's hook asks the reader where a foot goes and how far it leans, and draws there, or not)",
				TOWER_NAMES, 0);
		anchor = LabControls.choice(document, controls, "snowAnchor",
				"snow anchor (VIEW: one snowfall over the view, turned as its box's middle; LAYER: one per tile, each turned as its own)",
				ANCHOR_NAMES, 0);
		scroll = LabControls.slider(document, controls, "cameraScroll", "camera scroll (units a second)", -160, 160, 5, SCROLL);
		readout = LabControls.readout(document, controls, "effects-readout");
	}

	/** The page as the controls say, and the readout. */
	private void applyControls()
	{
		float r = LabControls.read(radius);
		heart.parallaxReader.setPlanetRadius(r);
		towerMode = LabControls.read(towersChoice);
		snow.setAnchor(LabControls.read(anchor) == 1 ? Enum_ParticleAnchor.LAYER : Enum_ParticleAnchor.VIEW);
		heart.screenSpeedConstantX = LabControls.read(scroll);
		// At the view's side, half the world from its middle: the ground's drop and lean, every layer's alike.
		float units = r * WORLD_WIDTH / 100, side = units > 0 ? WORLD_WIDTH / 2 / units : 0;
		readout.setInnerText((r > 0 ? "radius " + two(units) + " units; at the view's side the ground drops "
				+ two(units * (1 - (float) Math.cos(side))) + " units and leans " + two(side * MathUtils.radiansToDegrees) + "°"
				: "flat") + "; " + costLine);
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
	{
		batch.dispose();
		pixel.dispose();
	}
}
