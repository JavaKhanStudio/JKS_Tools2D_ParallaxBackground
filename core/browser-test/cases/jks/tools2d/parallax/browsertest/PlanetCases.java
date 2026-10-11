package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.LayerHook;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.pages.Enum_ParticleAnchor;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * ParallaxPageReader's cases on a page bent round its planet (r277): each IMAGE, SHADER and SEQUENCE layer cut in the
 * view's vertical strips, each strip lowered by {@code d dx² / 2R}, the far layers more, a still one not at all; a
 * PARTICLES layer's effects each lowered as one block, an EMPTY layer's hook handed the drop (r330). Shared builders in
 * {@link ReaderFixtures}.
 */
final class PlanetCases extends ReaderFixtures
{
	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("reader: planetBendFollowsTheLayersDepth", () -> new PlanetCases().planetBendFollowsTheLayersDepth()),
				new BrowserCase("reader: planetLowersEachStripByItsDropInEveryRepeatMode", () -> new PlanetCases().planetLowersEachStripByItsDropInEveryRepeatMode()),
				new BrowserCase("reader: planetBendsTheFarLayersMore", () -> new PlanetCases().planetBendsTheFarLayersMore()),
				new BrowserCase("reader: planetLeavesAStillLayerFlat", () -> new PlanetCases().planetLeavesAStillLayerFlat()),
				new BrowserCase("reader: planetCutsAFlippedLayerRightToLeft", () -> new PlanetCases().planetCutsAFlippedLayerRightToLeft()),
				new BrowserCase("reader: planetBendsTheMirroredCopy", () -> new PlanetCases().planetBendsTheMirroredCopy()),
				new BrowserCase("reader: planetLeavesATowerAsItIs", () -> new PlanetCases().planetLeavesATowerAsItIs()),
				new BrowserCase("reader: planetOfEachPageBendsItsOwnLayersInACrossFade", () -> new PlanetCases().planetOfEachPageBendsItsOwnLayersInACrossFade()),
				new BrowserCase("reader: planetLowersEachParticleTileAsOneBlockInEveryRepeatMode", () -> new PlanetCases().planetLowersEachParticleTileAsOneBlockInEveryRepeatMode()),
				new BrowserCase("reader: planetBringsAParticleTileFromAboveTheViewDown", () -> new PlanetCases().planetBringsAParticleTileFromAboveTheViewDown()),
				new BrowserCase("reader: planetLowersAViewAnchoredEffectByTheDropAtItsBoxsMiddle", () -> new PlanetCases().planetLowersAViewAnchoredEffectByTheDropAtItsBoxsMiddle()),
				new BrowserCase("reader: planetHandsAHookTheDropOfItsLayer", () -> new PlanetCases().planetHandsAHookTheDropOfItsLayer()));
	}

	/** The planet's radius: 250% of the 40-wide world, 100 units; the front layer's bend 1 / 200. */
	private static final float RADIUS = 250, BEND = 1 / 200f;
	/** A strip of the 40-wide view. */
	private static final float STEP = 40f / 256;
	/** Where the test layers' boxes start up the view: 5% of the 22.5-high world. */
	private static final float BOTTOM = 0.05f * 22.5f;

	/** A layer 20 wide and 10 high (a 1024 x 512 image at sizeRatio 0.5) at speed ratio {@code speed}, 5% up the world. */
	private static ParallaxLayer ground(float speed)
	{
		ParallaxLayer layer = new ParallaxLayer(texturedRegion(1024, 512), true, 40, speed, speed, 0.5f);
		layer.setDecalPercentY(5);
		return layer;
	}

	private ParallaxPageReader drawn(boolean onX, boolean onY, float radius, ParallaxLayer... layers)
	{
		ParallaxPageReader reader = reader(onX, onY);
		reader.setWorldSize(40, 22.5f);
		reader.setPlanetRadius(radius);
		reader.addLayers(list(layers));
		reader.resetPositions();
		draws.clear();
		reader.draw(camera, batch);
		return reader;
	}

	private static float left(float[] draw)
	{return Math.min(draw[0], draw[0] + draw[2]);}

	private static float right(float[] draw)
	{return Math.max(draw[0], draw[0] + draw[2]);}

	/** The drop of the strip the draw is in, bent by {@code bend}: {@code bend dx²}, dx from the view's middle to the strip's. */
	private static float dropAt(float[] draw, float bend)
	{
		int strip = (int) Math.floor((left(draw) + right(draw)) / 2 / STEP);
		float dx = (strip + 0.5f) * STEP - 20;
		return bend * dx * dx;
	}

	/**
	 * d / 2R, d the front's speed over the layer's: the front layer at 1 / 2R, one at half its speed twice that, a
	 * faster one than the stored front none more than the front; 0 without a planet, for a still layer and on a page that
	 * does not scroll.
	 */
	void planetBendFollowsTheLayersDepth()
	{
		equal(BEND, ParallaxPageReader.planetBendOf(RADIUS, 40, 0.02f, atSpeed(0.02f)), 1e-9f, "the front layer: 1 / 2R");
		equal(2 * BEND, ParallaxPageReader.planetBendOf(RADIUS, 40, 0.02f, atSpeed(0.01f)), 1e-9f, "twice as far: twice the bend");
		equal(2 * BEND, ParallaxPageReader.planetBendOf(RADIUS, 40, 0.02f, atSpeed(-0.01f)), 1e-9f, "a speed taken as a magnitude");
		equal(BEND / 2, ParallaxPageReader.planetBendOf(RADIUS, 80, 0.02f, atSpeed(0.02f)), 1e-9f, "the radius is in percent of the world width");
		equal(0, ParallaxPageReader.planetBendOf(0, 40, 0.02f, atSpeed(0.01f)), 0, "no planet");
		equal(0, ParallaxPageReader.planetBendOf(RADIUS, 40, 0.02f, atSpeed(0)), 0, "a still layer: the sky");
		equal(0, ParallaxPageReader.planetBendOf(RADIUS, 40, 0, atSpeed(0)), 0, "a page that does not scroll");
	}

	/**
	 * Every repeat mode: what shows of each tile is cut at the view's 256 strips, edge to edge, its u running on from one
	 * piece to the next, each piece as high as the layer and lowered by its strip's drop: least in the middle, most at the
	 * sides. Without the planet, the same page draws its tiles whole.
	 */
	void planetLowersEachStripByItsDropInEveryRepeatMode()
	{
		for (boolean[] mode : new boolean[][] { { true, false }, { false, true }, { true, true }, { false, false } })
		{
			String repeat = "repeat X " + mode[0] + ", Y " + mode[1];
			drawn(mode[0], mode[1], 0, ground(0.02f));
			int flat = draws.size();
			ParallaxLayer layer = ground(0.02f);
			drawn(mode[0], mode[1], RADIUS, layer);
			// Across: two tiles of 20 on X, one tile from 0 to 20 otherwise; up: rows of 10 on Y, one row otherwise.
			int across = mode[0] ? 256 : 128;
			isTrue(draws.size() == flat * across / (mode[0] ? 2 : 1), repeat + ": " + across + " pieces a row, " + flat + " tiles flat: " + draws.size());
			TextureRegion image = layer.getDrawnRegion();
			float uPerUnit = (image.getU2() - image.getU()) / 20;
			float least = Float.MAX_VALUE, most = 0;
			for (float[] draw : draws)
			{
				equal(STEP, draw[2], 1e-4f, repeat + ": a strip wide");
				equal(10, draw[3], 1e-4f, repeat + ": as high as the layer");
				float drop = dropAt(draw, BEND);
				float row = (BOTTOM - drop - draw[1]) / 10;
				equal(Math.round(row), row, 1e-3f, repeat + ": the strip at " + draw[0] + " lowered by " + drop + ", at " + draw[1]);
				equal(image.getU() + (left(draw) % 20) * uPerUnit, draw[8], 1e-5f, repeat + ": its u where it is in its tile");
				equal(STEP * uPerUnit, draw[9] - draw[8], 1e-5f, repeat + ": a strip's u");
				least = Math.min(least, drop);
				most = Math.max(most, drop);
			}
			equal(BEND * (STEP / 2) * (STEP / 2), least, 1e-6f, repeat + ": the middle strips barely");
			if (mode[0])
				equal(BEND * (20 - STEP / 2) * (20 - STEP / 2), most, 1e-4f, repeat + ": the side strips most");
		}
	}

	/** A layer at half the front's speed is twice as far, and bent twice as much; the front layer by 1 / 2R. */
	void planetBendsTheFarLayersMore()
	{
		ParallaxLayer far = ground(0.01f), front = ground(0.02f);
		front.setDecalPercentY(60);
		drawn(true, false, RADIUS, far, front);
		isTrue(draws.size() == 512, "256 pieces each: " + draws.size());
		for (float[] draw : draws.subList(0, 256))
			equal(BOTTOM - dropAt(draw, 2 * BEND), draw[1], 1e-4f, "the far layer at " + draw[0]);
		for (float[] draw : draws.subList(256, 512))
			equal(0.6f * 22.5f - dropAt(draw, BEND), draw[1], 1e-4f, "the front layer at " + draw[0]);
	}

	/** A still layer is the sky, not on the ground: drawn whole and flat, while the layer in front bends. */
	void planetLeavesAStillLayerFlat()
	{
		drawn(true, false, RADIUS, ground(0), ground(0.02f));
		isTrue(draws.size() == 2 + 256, "the sky's two tiles, the ground's 256 pieces: " + draws.size());
		equal(0, draws.get(0)[0], 1e-4f, "the sky's first tile, whole");
		equal(20, draws.get(0)[2], 1e-4f, "the sky's first tile, whole");
		equal(BOTTOM, draws.get(0)[1], 1e-4f, "the sky not lowered");
		equal(BOTTOM, draws.get(1)[1], 1e-4f, "the sky not lowered");
	}

	/**
	 * Flipped on X, each piece is drawn with a negative width from its right edge, and its u runs right to left: the
	 * piece at the tile's left edge shows the image's right edge.
	 */
	void planetCutsAFlippedLayerRightToLeft()
	{
		ParallaxLayer layer = ground(0.02f);
		layer.setFlipX(true);
		drawn(false, false, RADIUS, layer);
		isTrue(draws.size() == 128, "128 pieces: " + draws.size());
		TextureRegion image = layer.getDrawnRegion();
		float uPerUnit = (image.getU2() - image.getU()) / 20;
		for (float[] draw : draws)
		{
			equal(-STEP, draw[2], 1e-4f, "drawn right to left from " + draw[0]);
			equal(image.getU2() - right(draw) * uPerUnit, draw[8], 1e-5f, "its u from the image's right edge at " + draw[0]);
			equal(BOTTOM - dropAt(draw, BEND), draw[1], 1e-4f, "lowered at " + draw[0]);
		}
	}

	/** Repeating on X only, the mirrored copy stacked above the tiles is bent as they are. */
	void planetBendsTheMirroredCopy()
	{
		ParallaxLayer layer = ground(0.02f);
		layer.setMirror(true);
		drawn(true, false, RADIUS, layer);
		isTrue(draws.size() == 512, "the tiles' 256 pieces and the copy's: " + draws.size());
		for (float[] draw : draws.subList(256, 512))
		{
			// Flipped on Y: drawn down from the copy's top, 10 above the tiles' top.
			equal(-10, draw[3], 1e-4f, "the copy upside down at " + draw[0]);
			equal(BOTTOM + 20 - dropAt(draw, BEND), draw[1], 1e-4f, "the copy lowered at " + draw[0]);
		}
	}

	/** A tower stands in the view's middle on its own: the planet leaves its strips as they are. */
	void planetLeavesATowerAsItIs()
	{
		ParallaxLayer tower = ground(0.02f);
		tower.setTower(25, 0.65f);
		drawn(true, false, 0, tower);
		List<float[]> flat = new ArrayList<>(draws);
		drawn(true, false, RADIUS, tower);
		equal(flat.size(), draws.size(), "as many strips");
		for (int i = 0; i < flat.size(); i++)
			isTrue(Arrays.equals(flat.get(i), draws.get(i)), "strip " + i + " where it was");
	}

	/**
	 * During a cross-fade each page is bent by its own planet: the flat page fading out draws its tiles whole, the
	 * incoming one, on a planet, in pieces lowered by its bend; once it is in, the reader keeps its planet.
	 */
	void planetOfEachPageBendsItsOwnLayersInACrossFade()
	{
		ParallaxPageReader reader = drawn(true, false, 0, ground(0.02f));
		equal(2, draws.size(), "flat: two tiles");
		WholePage_Model round = page(list(ground(0.01f), ground(0.02f)));
		round.planetRadius = RADIUS;
		reader.addLayersTransfert(round, 2);
		reader.act(1, 0, 0);
		draws.clear();
		reader.draw(camera, batch);
		isTrue(draws.size() == 2 + 512, "the outgoing page's two tiles, the incoming page's pieces: " + draws.size());
		int whole = 0;
		for (float[] draw : draws)
			if (Math.abs(draw[2] - 20) < 1e-4f)
			{
				whole++;
				equal(BOTTOM, draw[1], 1e-4f, "the outgoing page flat");
			}
		equal(2, whole, "the outgoing page's tiles whole");
		reader.act(1.5f, 0, 0);
		equal(RADIUS, reader.getPlanetRadius(), 0, "the page in, its planet is the reader's");
	}

	/** The particles' draws, recognized by their 1-unit width ({@link ParticleCases#dot}). */
	private List<float[]> dots()
	{
		List<float[]> dots = new ArrayList<>();
		for (float[] draw : draws)
			if (Math.abs(draw[2] - 1) < 1e-3f && Math.abs(draw[3] - 1) < 1e-3f)
				dots.add(draw);
		return dots;
	}

	/**
	 * Every repeat mode: a PARTICLES layer pinned to its layer, at half the front's speed, has each tile's effect lowered
	 * as one block by twice the front's bend times dx², dx from the view's middle to its tile's box's (12 wide) (r330):
	 * its dot sits that far under its tile's corner.
	 */
	void planetLowersEachParticleTileAsOneBlockInEveryRepeatMode()
	{
		for (boolean[] mode : new boolean[][] { { true, false }, { false, true }, { true, true }, { false, false } })
		{
			String repeat = "repeat X " + mode[0] + ", Y " + mode[1];
			ParallaxLayer snow = ParallaxLayer.particles(ParticleCases.dot(0), Enum_ParticleAnchor.LAYER, 0.3f); // a step of 13 x 7.75
			snow.setPadX(1);
			snow.setPadY(1);
			snow.setParallaxSpeedRatioX(0.01f);
			snow.setParallaxSpeedRatioY(0.01f);
			snow.setDecalPercentX(25);
			snow.setDecalPercentY(25);
			ParallaxPageReader reader = drawn(mode[0], mode[1], RADIUS, ground(0.02f), snow);
			reader.act(1 / 60f, 0, 0);
			draws.clear();
			reader.draw(camera, batch);
			List<float[]> dots = dots();
			isTrue(dots.size() > 0, repeat + ": the particles are drawn");
			float lowest = 0;
			for (float[] dot : dots)
			{
				float x = dot[0] + 0.5f, dx = x + 6 - 20, drop = 2 * BEND * dx * dx;
				float stepsY = (dot[1] + 0.5f + drop - snow.getCurrentDistanceY() - 0) / 7.75f;
				equal(Math.round(stepsY), stepsY, 1e-3f, repeat + ": the dot at " + x + " lowered by " + drop + ", at " + dot[1]);
				lowest = Math.max(lowest, drop);
			}
			if (mode[0])
				isTrue(lowest > 1, repeat + ": the side tiles lowered by more than a unit: " + lowest);
		}
	}

	/**
	 * A tile whose effect is just over the view's top, flat, but lowered into it near the view's side, is drawn: the
	 * reader culls the tiles where their effect lands, not where it would be flat. Its dot at 0.5 across, 23.5 up, its
	 * box's middle 6 right of it, is lowered by 2 / 200 times 13.5², 1.82, into the view.
	 */
	void planetBringsAParticleTileFromAboveTheViewDown()
	{
		ParallaxLayer snow = ParallaxLayer.particles(ParticleCases.dot(0), Enum_ParticleAnchor.LAYER, 0.3f);
		snow.setParallaxSpeedRatioX(0.01f);
		snow.setParallaxSpeedRatioY(0.01f);
		snow.setDecalPercentX(1.25f);
		snow.setDecalPercentY(23.5f / 22.5f * 100);
		ParallaxPageReader reader = drawn(false, false, 0, ground(0.02f), snow);
		reader.act(1 / 60f, 0, 0);
		draws.clear();
		reader.draw(camera, batch);
		equal(0, dots().size(), "flat, the dot is over the view");
		reader.setPlanetRadius(RADIUS);
		draws.clear();
		reader.draw(camera, batch);
		equal(1, dots().size(), "bent, it is lowered into it");
		equal(23.5f - 2 * BEND * 13.5f * 13.5f, dots().get(0)[1] + 0.5f, 1e-3f, "by the drop at its box's middle");
	}

	/**
	 * Anchored to the view, the effect is lowered by the drop at its box's middle, 12 wide from 4 across, 10 left of the
	 * view's: 2 / 200 times 100, 1 under where the flat page draws it. A still one (the sky's snow) is not lowered.
	 */
	void planetLowersAViewAnchoredEffectByTheDropAtItsBoxsMiddle()
	{
		for (float speed : new float[] { 0.01f, 0 })
		{
			float[] flat = null;
			for (float radius : new float[] { 0, RADIUS })
			{
				ParallaxLayer rain = ParallaxLayer.particles(ParticleCases.dot(0), Enum_ParticleAnchor.VIEW, 0.3f);
				rain.setParallaxSpeedRatioX(speed);
				rain.setParallaxSpeedRatioY(speed);
				rain.setDecalPercentX(10);
				rain.setDecalPercentY(50);
				ParallaxPageReader reader = drawn(true, false, radius, ground(0.02f), rain);
				reader.act(1 / 60f, 0, 0);
				draws.clear();
				reader.draw(camera, batch);
				equal(1, dots().size(), "speed " + speed + ", radius " + radius + ": drawn once");
				if (flat == null)
					flat = dots().get(0);
				else
				{
					equal(flat[0], dots().get(0)[0], 1e-4f, "speed " + speed + ": not moved across");
					equal(flat[1] - (speed > 0 ? 1 : 0), dots().get(0)[1], 1e-3f, "speed " + speed + ": lowered");
				}
			}
		}
	}

	/**
	 * An EMPTY layer is not bent: its hook asks {@link ParallaxPageReader#planetDropAt} and lowers what it draws by it.
	 * At half the front's speed, twice the front's bend times dx²; 0 for a still layer and a flat page; during a
	 * cross-fade into a planet, the incoming page's.
	 */
	void planetHandsAHookTheDropOfItsLayer()
	{
		final ParallaxPageReader[] at = new ParallaxPageReader[1];
		final float[] asked = new float[1];
		LayerHook lowered = new LayerHook()
		{
			@Override
			public void draw(Batch batch, ParallaxLayer layer, float x, float y, float width, float height)
			{
				asked[0] = at[0].planetDropAt(layer, x + width / 2);
				batch.draw((TextureRegion) null, x, y - asked[0], width, height);
			}
		};
		ParallaxLayer flock = ParallaxLayer.empty("flock", 0.3f); // 12 wide, from 0 to 12 at decal 0
		flock.setParallaxSpeedRatioX(0.01f);
		at[0] = reader(false, false);
		at[0].setLayerHook("flock", lowered);
		at[0].setWorldSize(40, 22.5f);
		at[0].setPlanetRadius(RADIUS);
		at[0].addLayers(list(ground(0.02f), flock));
		at[0].resetPositions();
		draws.clear();
		at[0].draw(camera, batch);
		equal(2 * BEND * 14 * 14, asked[0], 1e-4f, "its tile's middle at 6, 14 left of the view's");
		float[] box = draws.get(draws.size() - 1);
		equal(-asked[0], box[1], 1e-4f, "the hook drew its box that much lower");
		equal(2 * BEND * 20 * 20, at[0].planetDropAt(flock, 0), 1e-4f, "at the view's left edge");
		equal(0, at[0].planetDropAt(flock, 20), 1e-6f, "none in its middle");
		ParallaxLayer still = ParallaxLayer.empty("flock", 0.3f);
		equal(0, at[0].planetDropAt(still, 0), 0, "a still layer: none");

		at[0].setPlanetRadius(0);
		equal(0, at[0].planetDropAt(flock, 0), 0, "a flat page: none");
		ParallaxLayer incoming = ParallaxLayer.empty("flock", 0.3f);
		incoming.setParallaxSpeedRatioX(0.01f);
		WholePage_Model round = page(list(ground(0.02f), incoming));
		round.planetRadius = RADIUS;
		at[0].addLayersTransfert(round, 2);
		at[0].act(1, 0, 0);
		draws.clear();
		at[0].draw(camera, batch);
		equal(0, at[0].planetDropAt(flock, 0), 0, "the outgoing page's layer: still flat");
		equal(2 * BEND * 20 * 20, at[0].planetDropAt(at[0].transferLayers.get(1), 0), 1e-4f, "the incoming page's: its planet");
	}
}
