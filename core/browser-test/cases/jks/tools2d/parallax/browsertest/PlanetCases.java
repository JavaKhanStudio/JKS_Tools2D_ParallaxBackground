package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;

import jks.tools2d.parallax.LayerHook;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.pages.Enum_ParticleAnchor;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * ParallaxPageReader's cases on a page round its planet (r277, r347): the view is the side of a world turning round one
 * centre R under the middle of its bottom. Each IMAGE, SHADER and SEQUENCE layer is cut in the view's vertical strips,
 * each strip drawn as the quad its corners go to (x along the ground's arc, y along the radius), every layer alike; a
 * PARTICLES layer's effects each turned as one block; an EMPTY layer's hook handed a place and an angle. Shared builders
 * in {@link ReaderFixtures}.
 */
final class PlanetCases extends ReaderFixtures
{
	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("reader: planetPlacesAPointRoundOneCentre", () -> new PlanetCases().planetPlacesAPointRoundOneCentre()),
				new BrowserCase("reader: planetWrapsEachStripRoundTheCentreInEveryRepeatMode", () -> new PlanetCases().planetWrapsEachStripRoundTheCentreInEveryRepeatMode()),
				new BrowserCase("reader: planetWrapsEveryLayerAlike", () -> new PlanetCases().planetWrapsEveryLayerAlike()),
				new BrowserCase("reader: planetLeansWhatStandsUp", () -> new PlanetCases().planetLeansWhatStandsUp()),
				new BrowserCase("reader: planetCutsAFlippedLayerRightToLeft", () -> new PlanetCases().planetCutsAFlippedLayerRightToLeft()),
				new BrowserCase("reader: planetWrapsTheMirroredCopy", () -> new PlanetCases().planetWrapsTheMirroredCopy()),
				new BrowserCase("reader: planetDrawsATileJustOverTheViewsTop", () -> new PlanetCases().planetDrawsATileJustOverTheViewsTop()),
				new BrowserCase("reader: planetLeavesATowerAsItIs", () -> new PlanetCases().planetLeavesATowerAsItIs()),
				new BrowserCase("reader: planetIsOneCircleOnScreenForDolliedLayers", () -> new PlanetCases().planetIsOneCircleOnScreenForDolliedLayers()),
				new BrowserCase("reader: planetOfEachPageWrapsItsOwnLayersInACrossFade", () -> new PlanetCases().planetOfEachPageWrapsItsOwnLayersInACrossFade()),
				new BrowserCase("reader: planetTurnsEachParticleTileAsOneBlockInEveryRepeatMode", () -> new PlanetCases().planetTurnsEachParticleTileAsOneBlockInEveryRepeatMode()),
				new BrowserCase("reader: planetBringsAParticleTileFromAboveTheViewDown", () -> new PlanetCases().planetBringsAParticleTileFromAboveTheViewDown()),
				new BrowserCase("reader: planetTurnsAViewAnchoredEffectAsItsBoxsMiddle", () -> new PlanetCases().planetTurnsAViewAnchoredEffectAsItsBoxsMiddle()),
				new BrowserCase("reader: planetHandsAHookAPlaceAndAnAngle", () -> new PlanetCases().planetHandsAHookAPlaceAndAnAngle()));
	}

	/** The planet's radius: 250% of the 40-wide world, 100 units, its centre at (20, -100). */
	private static final float RADIUS = 250, R = 100;
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
		redraw(reader);
		return reader;
	}

	private void redraw(ParallaxPageReader reader)
	{
		draws.clear();
		batch.quads.clear();
		batch.turned.clear();
		reader.draw(camera, batch);
	}

	/** Where (x, y) of a view from (0, 0), 40 wide, goes round a centre {@code r} under (20, 0). */
	private static float[] place(float r, float x, float y)
	{
		double a = (x - 20) / r;
		return new float[] { (float) (20 + (r + y) * Math.sin(a)), (float) (-r + (r + y) * Math.cos(a)) };
	}

	/** Vertex {@code corner} (0 bottom left, 1 top left, 2 top right, 3 bottom right) of a quad, x then y. */
	private static float[] corner(float[] quad, int corner)
	{return new float[] { quad[corner * 5], quad[corner * 5 + 1] };}

	/** Checks that vertex {@code corner} of {@code quad} is where (x, y) goes round a centre {@code r} under (20, 0). */
	private static void at(float[] quad, int corner, float r, float x, float y, String what)
	{
		float[] expected = place(r, x, y);
		equal(expected[0], quad[corner * 5], 2e-4f, what + ": x of corner " + corner);
		equal(expected[1], quad[corner * 5 + 1], 2e-4f, what + ": y of corner " + corner);
	}

	/** The x a quad's left edge came from: its bottom-left corner's angle round (20, -r), back along the arc. */
	private static float flatLeft(float[] quad, float r)
	{return 20 + r * (float) Math.atan2(quad[0] - 20, quad[1] + r);}

	/**
	 * The middle of the view's bottom stays where it is; a point above it goes straight up; one at dx along the ground goes
	 * to the angle dx / R round the centre, at R plus its height from it, and leans by that angle; the radius is in percent
	 * of the world width. Without a planet, a point stays where it is and leans not at all.
	 */
	void planetPlacesAPointRoundOneCentre()
	{
		ParallaxLayer layer = ground(0.02f);
		ParallaxPageReader reader = drawn(true, false, RADIUS, layer);
		Vector2 out = new Vector2();
		reader.planetPlaceAt(layer, 20, 0, out);
		equal(20, out.x, 1e-5f, "the middle of the bottom: across");
		equal(0, out.y, 1e-5f, "the middle of the bottom: up");
		reader.planetPlaceAt(layer, 20, 7, out);
		equal(20, out.x, 1e-5f, "straight above it: across");
		equal(7, out.y, 1e-5f, "straight above it: up");
		for (float[] point : new float[][] { { 0, 0 }, { 40, 5 }, { 31, 12 }, { 5, -2 } })
		{
			reader.planetPlaceAt(layer, point[0], point[1], out);
			float[] expected = place(R, point[0], point[1]);
			equal(expected[0], out.x, 1e-4f, point[0] + ", " + point[1] + ": across");
			equal(expected[1], out.y, 1e-4f, point[0] + ", " + point[1] + ": up");
			equal(R + point[1], (float) Math.hypot(out.x - 20, out.y + R), 1e-4f, point[0] + ", " + point[1] + ": R plus its height from the centre");
			equal(-(point[0] - 20) / R * 180 / (float) Math.PI, reader.planetAngleAt(layer, point[0]), 1e-4f, point[0] + ": leans by its angle");
		}
		isTrue(reader.planetAngleAt(layer, 40) < 0, "right of the middle it leans right: clockwise");
		reader.setPlanetRadius(500);
		reader.planetPlaceAt(layer, 0, 0, out);
		equal(place(200, 0, 0)[1], out.y, 1e-4f, "500% of the world: a radius of 200");
		reader.setPlanetRadius(0);
		reader.planetPlaceAt(layer, 3, 4, out);
		equal(3, out.x, 0, "flat: where it is");
		equal(4, out.y, 0, "flat: where it is");
		equal(0, reader.planetAngleAt(layer, 3), 0, "flat: no lean");
	}

	/**
	 * Every repeat mode: what shows of each tile is cut at the view's 256 strips, edge to edge, its u running on from one
	 * piece to the next, each piece the quad its four corners go to round the centre: a strip wide along the arc, as
	 * high as the layer along the radius. Without the planet, the same page draws its tiles whole.
	 */
	void planetWrapsEachStripRoundTheCentreInEveryRepeatMode()
	{
		for (boolean[] mode : new boolean[][] { { true, false }, { false, true }, { true, true }, { false, false } })
		{
			String repeat = "repeat X " + mode[0] + ", Y " + mode[1];
			drawn(mode[0], mode[1], 0, ground(0.02f));
			int flat = draws.size();
			isTrue(batch.quads.isEmpty(), repeat + ": flat, no quads");
			ParallaxLayer layer = ground(0.02f);
			drawn(mode[0], mode[1], RADIUS, layer);
			// Across: two tiles of 20 on X, one tile from 0 to 20 otherwise; up: rows of 10 on Y, one row otherwise.
			int across = mode[0] ? 256 : 128, rows = flat / (mode[0] ? 2 : 1);
			equal(rows * across, batch.quads.size(), repeat + ": " + across + " pieces a row, " + rows + " rows");
			TextureRegion image = layer.getDrawnRegion();
			float uPerUnit = (image.getU2() - image.getU()) / 20;
			for (float[] quad : batch.quads)
			{
				float left = flatLeft(quad, R);
				float strip = Math.round(left / STEP);
				equal(strip * STEP, left, 2e-4f, repeat + ": a strip's edge");
				// Its row's bottom: the bottom-left corner's height over R from the centre.
				float bottom = (float) Math.hypot(quad[0] - 20, quad[1] + R) - R;
				float row = (bottom - BOTTOM) / 10;
				equal(Math.round(row), row, 1e-3f, repeat + ": on a row, at " + bottom);
				at(quad, 0, R, left, bottom, repeat);
				at(quad, 1, R, left, bottom + 10, repeat);
				at(quad, 2, R, left + STEP, bottom + 10, repeat);
				at(quad, 3, R, left + STEP, bottom, repeat);
				equal(image.getU() + (left % 20) * uPerUnit, quad[3], 1e-5f, repeat + ": its u where it is in its tile");
				equal(quad[3], quad[8], 0, repeat + ": the left edge's u, up");
				equal(STEP * uPerUnit, quad[13] - quad[3], 1e-5f, repeat + ": a strip's u");
				equal(image.getV2(), quad[4], 0, repeat + ": v2 at the bottom");
				equal(image.getV(), quad[9], 0, repeat + ": v at the top");
			}
		}
	}

	/**
	 * A far layer, the front one and a still one, at the same place, are wrapped round the same circle: the same quads.
	 * Each turns at its own speed, by its own scroll; none pokes out under another.
	 */
	void planetWrapsEveryLayerAlike()
	{
		drawn(true, false, RADIUS, ground(0.01f), ground(0.02f), ground(0));
		equal(3 * 256, batch.quads.size(), "256 pieces each");
		for (int i = 0; i < 256; i++)
		{
			isTrue(Arrays.equals(batch.quads.get(i), batch.quads.get(256 + i)), "the far and the front layer's piece " + i + " alike");
			isTrue(Arrays.equals(batch.quads.get(i), batch.quads.get(512 + i)), "the far and the still layer's piece " + i + " alike");
		}
	}

	/**
	 * What stands up leans out from the centre: each piece's left edge, bottom to top, points along the radius through its
	 * bottom; in the middle straight up, at the sides out, right of the middle to the right.
	 */
	void planetLeansWhatStandsUp()
	{
		drawn(true, false, RADIUS, ground(0.02f));
		for (float[] quad : batch.quads)
		{
			float[] bottom = corner(quad, 0), top = corner(quad, 1);
			float ux = top[0] - bottom[0], uy = top[1] - bottom[1], rx = bottom[0] - 20, ry = bottom[1] + R;
			equal(0, ux * ry - uy * rx, 2e-3f, "the edge from " + bottom[0] + " along the radius");
			isTrue(ux * rx + uy * ry > 0, "outward");
		}
		float[] first = batch.quads.get(0), last = batch.quads.get(batch.quads.size() - 1);
		isTrue(first[5] < first[0], "the left side leans left");
		isTrue(last[5] > last[0], "the right side leans right");
	}

	/** Flipped on X, the u runs right to left: the piece at the tile's left edge shows the image's right edge. */
	void planetCutsAFlippedLayerRightToLeft()
	{
		ParallaxLayer layer = ground(0.02f);
		layer.setFlipX(true);
		drawn(false, false, RADIUS, layer);
		equal(128, batch.quads.size(), "128 pieces");
		TextureRegion image = layer.getDrawnRegion();
		float uPerUnit = (image.getU2() - image.getU()) / 20;
		for (float[] quad : batch.quads)
		{
			float left = flatLeft(quad, R);
			equal(image.getU2() - left * uPerUnit, quad[3], 1e-5f, "its left edge's u from the image's right edge at " + left);
			isTrue(quad[13] < quad[3], "its u decreasing to the right at " + left);
			at(quad, 0, R, left, BOTTOM, "flipped");
		}
	}

	/** Repeating on X only, the mirrored copy stacked above the tiles is wrapped as they are, upside down. */
	void planetWrapsTheMirroredCopy()
	{
		ParallaxLayer layer = ground(0.02f);
		layer.setMirror(true);
		drawn(true, false, RADIUS, layer);
		equal(512, batch.quads.size(), "the tiles' 256 pieces and the copy's");
		for (float[] quad : batch.quads.subList(256, 512))
		{
			float left = flatLeft(quad, R);
			// Flipped on Y: drawn down from the copy's top, 10 above the tiles' top, v2 there.
			at(quad, 0, R, left, BOTTOM + 20, "the copy");
			at(quad, 1, R, left, BOTTOM + 10, "the copy");
			equal(layer.getDrawnRegion().getV2(), quad[4], 0, "the copy's v2 at its top");
		}
	}

	/**
	 * The view's top corners show what lies a little over it: √((R + h)² + (w / 2)²) - R - h, 1.62 for R 100 and a view
	 * 22.5 high and 40 wide. A layer whose box starts 1.5 over the view's top is not drawn flat, but drawn round the planet;
	 * one 1.75 over is not.
	 */
	void planetDrawsATileJustOverTheViewsTop()
	{
		for (float over : new float[] { 1.5f, 1.75f })
		{
			ParallaxLayer layer = ground(0.02f);
			layer.setDecalPercentY((22.5f + over) / 22.5f * 100);
			drawn(true, false, 0, layer);
			equal(0, draws.size(), over + " over: flat, nothing drawn");
			drawn(true, false, RADIUS, layer);
			if (over < 1.6f)
				equal(256, batch.quads.size(), over + " over: round the planet, its pieces drawn");
			else
				equal(0, batch.quads.size(), over + " over: out of reach");
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
		isTrue(batch.quads.isEmpty(), "no quads");
		for (int i = 0; i < flat.size(); i++)
			isTrue(Arrays.equals(flat.get(i), draws.get(i)), "strip " + i + " where it was");
	}

	/**
	 * Dollied at zoom 0.5, a far layer drawn in a wider view of its own is wrapped round a circle as many times larger in
	 * it: once scaled into the camera's view, every layer's quads are round one centre, R under the middle of the camera
	 * view's bottom (20, 5.625): each piece's bottom corners as far from it, and its top ones.
	 */
	void planetIsOneCircleOnScreenForDolliedLayers()
	{
		ParallaxPageReader reader = reader(true, false);
		reader.setWorldSize(40, 22.5f);
		camera.zoom = 0.5f;
		reader.setDolly(true);
		reader.setPlanetRadius(RADIUS);
		ParallaxLayer far = ground(0.01f), front = ground(0.02f);
		reader.addLayers(list(far, front));
		reader.resetPositions();
		redraw(reader);
		isTrue(batch.quads.size() == 512, "256 pieces each: " + batch.quads.size());
		for (int i = 0; i < 512; i++)
		{
			float[] quad = batch.quads.get(i);
			float cy = 5.625f - R;
			equal((float) Math.hypot(quad[0] - 20, quad[1] - cy), (float) Math.hypot(quad[15] - 20, quad[16] - cy), 1e-3f,
					"piece " + i + "'s bottom corners round the one centre");
			equal((float) Math.hypot(quad[5] - 20, quad[6] - cy), (float) Math.hypot(quad[10] - 20, quad[11] - cy), 1e-3f,
					"piece " + i + "'s top corners round the one centre");
		}
		// The bottom strip's left corner at the view's middle stands on the same circle for both, straight over its centre.
		float[] farMiddle = batch.quads.get(128), frontMiddle = batch.quads.get(256 + 128);
		equal(20, farMiddle[0], 1e-3f, "the far layer's middle piece at the middle");
		equal(20, frontMiddle[0], 1e-3f, "the front layer's middle piece at the middle");
	}

	/**
	 * During a cross-fade each page is wrapped by its own planet: the flat page fading out draws its tiles whole, the
	 * incoming one, on a planet, in pieces; once it is in, the reader keeps its planet.
	 */
	void planetOfEachPageWrapsItsOwnLayersInACrossFade()
	{
		ParallaxPageReader reader = drawn(true, false, 0, ground(0.02f));
		equal(2, draws.size(), "flat: two tiles");
		WholePage_Model round = page(list(ground(0.01f), ground(0.02f)));
		round.planetRadius = RADIUS;
		reader.addLayersTransfert(round, 2);
		reader.act(1, 0, 0);
		redraw(reader);
		equal(512, batch.quads.size(), "the incoming page's pieces");
		equal(2 + 512, draws.size(), "and the outgoing page's two tiles");
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

	/** The particles' quads, recognized by their 1-unit sides ({@link ParticleCases#dot}). */
	private List<float[]> dots()
	{
		List<float[]> dots = new ArrayList<>();
		for (float[] quad : batch.quads)
			if (Math.abs(Math.hypot(quad[5] - quad[0], quad[6] - quad[1]) - 1) < 1e-3f)
				dots.add(quad);
		return dots;
	}

	/** A dot's middle: half way between its bottom-left and top-right corners. */
	private static float[] middle(float[] dot)
	{return new float[] { (dot[0] + dot[10]) / 2, (dot[1] + dot[11]) / 2 };}

	/** Where a point {@code (x, y)} of an effect turned as its box's middle {@code (mx, my)} goes round a centre r under (20, 0). */
	private static float[] turned(float r, float mx, float my, float x, float y)
	{
		float[] to = place(r, mx, my);
		double a = -(mx - 20) / r;
		float dx = x - mx, dy = y - my;
		return new float[] { (float) (to[0] + dx * Math.cos(a) - dy * Math.sin(a)), (float) (to[1] + dx * Math.sin(a) + dy * Math.cos(a)) };
	}

	/**
	 * Every repeat mode: a PARTICLES layer pinned to its layer has each tile's effect turned as one block, as the middle
	 * of its tile's box (12 wide, 6.75 high) is: its dot (1 by 1, its middle on its tile's corner) stays a unit square,
	 * turned by the angle of its box's middle, and sits where its tile's corner goes. One tile alone, the dot where the
	 * flat page draws it, turned so.
	 */
	void planetTurnsEachParticleTileAsOneBlockInEveryRepeatMode()
	{
		for (boolean[] mode : new boolean[][] { { true, false }, { false, true }, { true, true }, { false, false } })
		{
			String repeat = "repeat X " + mode[0] + ", Y " + mode[1];
			float[] flat = null;
			for (float radius : new float[] { 0, RADIUS })
			{
				ParallaxLayer snow = ParallaxLayer.particles(ParticleCases.dot(0), Enum_ParticleAnchor.LAYER, 0.3f); // a step of 13 x 7.75
				snow.setPadX(1);
				snow.setPadY(1);
				snow.setParallaxSpeedRatioX(0.01f);
				snow.setParallaxSpeedRatioY(0.01f);
				snow.setDecalPercentX(25);
				snow.setDecalPercentY(25);
				ParallaxPageReader reader = drawn(mode[0], mode[1], radius, ground(0.02f), snow);
				reader.act(1 / 60f, 0, 0);
				redraw(reader);
				List<float[]> dots = dots();
				isTrue(dots.size() > 0, repeat + ": the particles are drawn");
				if (radius == 0)
				{
					if (!mode[0] && !mode[1])
						flat = middle(dots.get(0));
					continue;
				}
				float tilted = 0;
				for (float[] dot : dots)
				{
					// The dot's side, bottom to top: its angle is its box's middle's, 6 right of its tile's corner.
					double angle = Math.atan2(-(dot[5] - dot[0]), dot[6] - dot[1]);
					equal(0, (dot[15] - dot[0]) * (dot[5] - dot[0]) + (dot[16] - dot[1]) * (dot[6] - dot[1]), 1e-3f,
							repeat + ": still square");
					equal(1, (float) Math.hypot(dot[15] - dot[0], dot[16] - dot[1]), 1e-3f, repeat + ": still a unit wide");
					tilted = Math.max(tilted, Math.abs((float) angle));
				}
				if (mode[0])
					isTrue(tilted > 0.05f, repeat + ": the side tiles turned by more than 0.05 rad: " + tilted);
				else if (!mode[1])
				{
					// The one tile: its corner where the flat dot's middle is, its box's middle 6 right and 3.375 up.
					float[] expected = turned(R, flat[0] + 6, flat[1] + 6.75f / 2, flat[0], flat[1]);
					float[] at = middle(dots.get(0));
					equal(expected[0], at[0], 1e-3f, repeat + ": the one dot, across");
					equal(expected[1], at[1], 1e-3f, repeat + ": the one dot, up");
				}
			}
		}
	}

	/**
	 * A tile whose effect is just over the view's top, flat, but lowered into it near the view's side, is drawn: the
	 * reader culls the tiles down to how far the ground falls at the view's side. Its dot at 0.5 across, 23.5 up, its
	 * box's middle 6 right of it and 3.375 up, turned so: down into the view.
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
		redraw(reader);
		equal(0, dots().size(), "flat, the dot is over the view");
		float[] flat = { snow.getCurrentDistanceX(), snow.getCurrentDistanceY() };
		equal(0.5f, flat[0], 0.01f, "its tile's corner, across");
		equal(23.5f, flat[1], 0.01f, "its tile's corner, up");
		reader.setPlanetRadius(RADIUS);
		redraw(reader);
		equal(1, dots().size(), "round the planet, it is brought into it");
		float[] expected = turned(R, flat[0] + 6, flat[1] + 6.75f / 2, flat[0], flat[1]);
		isTrue(expected[1] < 22.5f, "into the view: " + expected[1]);
		equal(expected[1], middle(dots().get(0))[1], 1e-3f, "turned as its box's middle");
	}

	/**
	 * Anchored to the view, the effect is turned as the middle of its box is, 12 wide from 4 across, 6.75 high from 11.25
	 * up: its dot where that takes the flat one. A still one (the sky's snow) too: every layer round the same planet.
	 */
	void planetTurnsAViewAnchoredEffectAsItsBoxsMiddle()
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
				redraw(reader);
				equal(1, dots().size(), "speed " + speed + ", radius " + radius + ": drawn once");
				if (flat == null)
					flat = middle(dots().get(0));
				else
				{
					float[] expected = turned(R, 4 + 6, 11.25f + 6.75f / 2, flat[0], flat[1]);
					equal(expected[0], middle(dots().get(0))[0], 1e-3f, "speed " + speed + ": across");
					equal(expected[1], middle(dots().get(0))[1], 1e-3f, "speed " + speed + ": up");
				}
			}
		}
	}

	/**
	 * An EMPTY layer is not wrapped: its hook asks where its tile's foot goes ({@link ParallaxPageReader#planetPlaceAt})
	 * and how far it leans ({@link ParallaxPageReader#planetAngleAt}), and draws its box there, turned. During a
	 * cross-fade into a planet, the incoming page's layers are round its planet, the outgoing page's flat. The
	 * deprecated drop still answers how far the ground falls.
	 */
	@SuppressWarnings("deprecation")
	void planetHandsAHookAPlaceAndAnAngle()
	{
		final ParallaxPageReader[] at = new ParallaxPageReader[1];
		final Vector2 foot = new Vector2();
		LayerHook standing = new LayerHook()
		{
			@Override
			public void draw(Batch batch, ParallaxLayer layer, float x, float y, float width, float height)
			{
				at[0].planetPlaceAt(layer, x + width / 2, y, foot);
				batch.draw((TextureRegion) null, foot.x - width / 2, foot.y, width / 2, 0, width, height, 1, 1,
						at[0].planetAngleAt(layer, x + width / 2));
			}
		};
		ParallaxLayer flock = ParallaxLayer.empty("flock", 0.3f); // 12 wide, from 0 to 12 at decal 0
		flock.setParallaxSpeedRatioX(0.01f);
		at[0] = reader(false, false);
		at[0].setLayerHook("flock", standing);
		at[0].setWorldSize(40, 22.5f);
		at[0].setPlanetRadius(RADIUS);
		at[0].addLayers(list(ground(0.02f), flock));
		at[0].resetPositions();
		redraw(at[0]);
		equal(1, batch.turned.size(), "the hook drew one turned box");
		float[] box = batch.turned.get(0);
		float[] expected = place(R, 6, 0);
		equal(expected[0] - 6, box[0], 1e-4f, "its tile's foot, 14 left of the view's middle, where it goes: across");
		equal(expected[1], box[1], 1e-4f, "its tile's foot, where it goes: up");
		equal(14 / R * 180 / (float) Math.PI, box[6], 1e-4f, "leaning left by 14 / R");
		equal(R * (1 - (float) Math.cos(0.2)), at[0].planetDropAt(flock, 0), 1e-4f, "the deprecated drop, at the view's left edge");
		ParallaxLayer still = ParallaxLayer.empty("flock", 0.3f);
		equal(-0.2f * 180 / (float) Math.PI, -at[0].planetAngleAt(still, 0), 1e-4f, "a still layer leans too");

		at[0].setPlanetRadius(0);
		equal(0, at[0].planetAngleAt(flock, 0), 0, "a flat page: no lean");
		ParallaxLayer incoming = ParallaxLayer.empty("flock", 0.3f);
		incoming.setParallaxSpeedRatioX(0.01f);
		WholePage_Model round = page(list(ground(0.02f), incoming));
		round.planetRadius = RADIUS;
		at[0].addLayersTransfert(round, 2);
		at[0].act(1, 0, 0);
		redraw(at[0]);
		equal(0, at[0].planetAngleAt(flock, 0), 0, "the outgoing page's layer: still flat");
		equal(0.2f * 180 / (float) Math.PI, at[0].planetAngleAt(at[0].transferLayers.get(1), 0), 1e-4f, "the incoming page's: its planet");
	}
}
