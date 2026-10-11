package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.LayerEffects;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;

/**
 * ParallaxPageReader's cases on tower layers (r276): an IMAGE layer wrapped round a cylinder in the view's middle, its
 * scroll turning it, its strips darker toward its sides. Shared builders in {@link ReaderFixtures}.
 */
final class TowerCases extends ReaderFixtures
{
	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("reader: towerWrapsItsImageRoundTheViewsMiddleInEveryRepeatMode", () -> new TowerCases().towerWrapsItsImageRoundTheViewsMiddleInEveryRepeatMode()),
				new BrowserCase("reader: towerTurnsWithItsScroll", () -> new TowerCases().towerTurnsWithItsScroll()),
				new BrowserCase("reader: towerDarkensTowardItsSidesInTheBatchColor", () -> new TowerCases().towerDarkensTowardItsSidesInTheBatchColor()),
				new BrowserCase("reader: towerUnderThePageFogDarkensInTheBlue", () -> new TowerCases().towerUnderThePageFogDarkensInTheBlue()),
				new BrowserCase("reader: towerDrawsOnlyWhatTheViewShows", () -> new TowerCases().towerDrawsOnlyWhatTheViewShows()),
				new BrowserCase("reader: towerFlippedRunsItsImageTheOtherWay", () -> new TowerCases().towerFlippedRunsItsImageTheOtherWay()),
				new BrowserCase("reader: towerRadiusOnAnotherKindDrawsItFlat", () -> new TowerCases().towerRadiusOnAnotherKindDrawsItFlat()));
	}

	/** The tower's radius: 25% of the 40-wide world. */
	private static final float RADIUS = 10, AXIS = 20;

	/**
	 * A tower layer 20 wide and 10 high (a 1024 x 512 image at sizeRatio 0.5) round a radius of 10, at speed ratio 0.05,
	 * 5% up the world.
	 */
	private static ParallaxLayer tower()
	{
		ParallaxLayer tower = new ParallaxLayer(texturedRegion(1024, 512), true, 40, 0.05f, 0.05f, 0.5f);
		tower.setTower(25, 0.65f);
		tower.setDecalPercentY(5);
		return tower;
	}

	private ParallaxPageReader drawn(boolean onX, boolean onY, ParallaxLayer... layers)
	{
		ParallaxPageReader reader = reader(onX, onY);
		reader.setWorldSize(40, 22.5f);
		reader.addLayers(list(layers));
		reader.resetPositions();
		draws.clear();
		reader.draw(camera, batch);
		return reader;
	}

	/** The draws starting at y {@code y}, left to right by their leftmost edge. */
	private List<float[]> rowAt(float y)
	{
		List<float[]> row = new ArrayList<>();
		for (float[] draw : draws)
			if (Math.abs(draw[1] - y) < 1e-4f)
				row.add(draw);
		row.sort((a, b) -> Float.compare(Math.min(a[0], a[0] + a[2]), Math.min(b[0], b[0] + b[2])));
		return row;
	}

	private static float left(float[] draw)
	{return Math.min(draw[0], draw[0] + draw[2]);}

	private static float right(float[] draw)
	{return Math.max(draw[0], draw[0] + draw[2]);}

	/** How much of the image a draw shows: its u span over the drawn region's. */
	private static float images(float[] draw, ParallaxLayer layer)
	{
		TextureRegion image = layer.getDrawnRegion();
		return Math.abs(draw[9] - draw[8]) / Math.abs(image.getU2() - image.getU());
	}

	/** Where across the image the draw starting at the axis starts: its u, in the image's width, 0 to 1. */
	private static float shownAtTheAxis(List<float[]> row, ParallaxLayer layer)
	{
		TextureRegion image = layer.getDrawnRegion();
		for (float[] draw : row)
			if (Math.abs(left(draw) - AXIS) < 1e-4f)
				return (draw[8] - image.getU()) / (image.getU2() - image.getU());
		throw new AssertionError("no strip starts at the axis");
	}

	/**
	 * Every repeat mode: the tower is one row of strips from the axis minus the radius to the axis plus it, edge to edge,
	 * the strips narrowing toward the sides, the image round it at its own scale (the front half, pi * radius around, is
	 * pi / 2 images of 20); once, or tiled up on a page repeating on Y, never across.
	 */
	void towerWrapsItsImageRoundTheViewsMiddleInEveryRepeatMode()
	{
		for (boolean[] mode : new boolean[][] { { true, false }, { false, true }, { true, true }, { false, false } })
		{
			String repeat = "repeat X " + mode[0] + ", Y " + mode[1];
			ParallaxLayer tower = tower();
			drawn(mode[0], mode[1], tower);
			List<float[]> row = rowAt(0.05f * 22.5f);
			isTrue(row.size() >= ParallaxLayer.TOWER_STRIPS && row.size() <= ParallaxLayer.TOWER_STRIPS + 3,
					repeat + ": 128 strips, and a split at each image edge: " + row.size());
			equal(AXIS - RADIUS, left(row.get(0)), 1e-4f, repeat + ": from the axis minus the radius");
			equal(AXIS + RADIUS, right(row.get(row.size() - 1)), 1e-4f, repeat + ": to the axis plus it");
			float shown = 0;
			for (int i = 0; i < row.size(); i++)
			{
				float[] draw = row.get(i);
				if (i > 0)
					equal(right(row.get(i - 1)), left(draw), 1e-4f, repeat + ": strip " + i + " starts where the one before ends");
				equal(10, draw[3], 1e-4f, repeat + ": as high as the layer");
				shown += images(draw, tower);
			}
			equal((float) Math.PI / 2, shown, 1e-3f, repeat + ": pi / 2 images round the front half");
			float middle = right(row.get(row.size() / 2)) - left(row.get(row.size() / 2));
			isTrue(right(row.get(0)) - left(row.get(0)) < middle / 10, repeat + ": the side strips are the narrowest");
			if (mode[1])
				isTrue(draws.size() > row.size() && draws.size() % row.size() <= 3 * 2, repeat + ": tiled up the view: " + draws.size());
			else
				equal(row.size(), draws.size(), repeat + ": drawn once");
		}
	}

	/**
	 * The front of the tower, at the axis, shows what the flat layer shows there: its tile starts at the layer's distance,
	 * so the axis is (20 - distance) / 20 across an image; scrolled, the tower turns by as much.
	 */
	void towerTurnsWithItsScroll()
	{
		ParallaxLayer tower = tower();
		ParallaxPageReader reader = drawn(true, false, tower);
		float y = 0.05f * 22.5f;
		float before = shownAtTheAxis(rowAt(y), tower);
		float expected = (AXIS - tower.getCurrentDistanceX()) / 20;
		equal(expected - (float) Math.floor(expected), before, 1e-4f, "the axis shows the flat layer's point");
		reader.act(1, 50, 0);
		draws.clear();
		reader.draw(camera, batch);
		// 50 units of view at 0.05: the layer moved 2.5 left, so the axis shows 2.5 further along the image.
		float after = shownAtTheAxis(rowAt(y), tower);
		float turned = after - before;
		equal(2.5f / 20, turned - (float) Math.floor(turned), 1e-4f, "turned by its scroll");
	}

	/**
	 * Without the page fog each strip is drawn at 1 - 0.65 (1 - cos a) of the batch colour, a its middle's angle; the
	 * alpha untouched, and the colour put back for what is drawn after.
	 */
	void towerDarkensTowardItsSidesInTheBatchColor()
	{
		ParallaxLayer tower = tower();
		ParallaxPageReader reader = drawn(true, false, tower, atSpeed(0.1f));
		reader.addColorTransfert(new Color(0.5f, 1, 0.8f, 1), 0);
		draws.clear();
		reader.draw(camera, batch);
		List<float[]> row = rowAt(0.05f * 22.5f);
		for (float[] draw : row)
		{
			float a = (float) Math.asin(((left(draw) + right(draw)) / 2 - AXIS) / RADIUS);
			float bright = 1 - 0.65f * (1 - (float) Math.cos(a));
			// The middle of a strip's x is close to its middle angle's: within what a 128th of a half turn bends.
			equal(0.5f * bright, draw[4], 0.01f, "red at " + left(draw));
			equal(bright, draw[6], 0.02f, "green");
			equal(0.8f * bright, draw[7], 0.02f, "blue");
			equal(1, draw[5], 0, "alpha");
		}
		isTrue(row.get(0)[6] < 0.4f && row.get(row.size() / 2)[6] > 0.99f, "dark at the side, bright at the front");
		float[] after = draws.get(draws.size() - 1);
		isTrue(after[4] == 0.5f && after[6] == 1 && after[7] == 0.8f, "the layer after it drawn with the tint");
	}

	/** Under the page fog the strips keep the fog in the red, the page in the green; the darkness rides in the blue. */
	void towerUnderThePageFogDarkensInTheBlue()
	{
		ParallaxPageReader reader = reader(true, false);
		reader.setWorldSize(40, 22.5f);
		reader.setLayerEffects(new LayerEffects()
		{
			@Override
			public boolean begin(Batch batch, ParallaxLayer layer, float phase)
			{throw new AssertionError("no layer is begun under the page fog");}

			@Override
			public void end(Batch batch, ParallaxLayer layer)
			{}

			@Override
			public boolean beginPageFog(Batch batch, Color tint, Color fog, Color incomingFog)
			{return true;}
		});
		reader.setFog(0.05f, Color.GRAY);
		ParallaxLayer tower = tower();
		reader.addLayers(list(tower, atSpeed(0.1f)));
		reader.resetPositions();
		draws.clear();
		reader.draw(camera, batch);
		List<float[]> row = rowAt(0.05f * 22.5f);
		for (float[] draw : row)
		{
			float a = (float) Math.asin(((left(draw) + right(draw)) / 2 - AXIS) / RADIUS);
			equal(fog(0.05f, 0.05f, 0.1f), draw[4], 1e-6f, "the tower's fog in the red");
			equal(0, draw[6], 0, "its page in the green");
			equal(0.65f * (1 - (float) Math.cos(a)), draw[7], 0.02f, "its darkness in the blue");
		}
		float[] front = draws.get(draws.size() - 1);
		equal(0, front[7], 0, "the layer after it: no darkness");
	}

	/** Zoomed in to a view 10 wide (15 to 25), only the strips reaching into it are drawn, and they cover it. */
	void towerDrawsOnlyWhatTheViewShows()
	{
		ParallaxLayer tower = tower();
		ParallaxPageReader reader = reader(true, false);
		reader.setWorldSize(40, 22.5f);
		reader.addLayers(list(tower));
		reader.resetPositions();
		camera.zoom = 0.25f;
		camera.update();
		draws.clear();
		reader.draw(camera, batch);
		float viewLeft = AXIS - 5, viewRight = AXIS + 5;
		for (float[] draw : draws)
			isTrue(right(draw) > viewLeft && left(draw) < viewRight, "a strip in the view: " + left(draw));
		List<float[]> row = rowAt(draws.get(0)[1]);
		isTrue(left(row.get(0)) <= viewLeft && right(row.get(row.size() - 1)) >= viewRight, "the view covered");
		isTrue(row.size() < ParallaxLayer.TOWER_STRIPS / 2, "fewer strips than the front half's: " + row.size());
	}

	/**
	 * Flipped on X, each strip is drawn right to left (a negative width) and the image runs the other way round: the
	 * axis shows 1 minus what it shows unflipped.
	 */
	void towerFlippedRunsItsImageTheOtherWay()
	{
		ParallaxLayer plain = tower();
		drawn(true, false, plain);
		float unflipped = shownAtTheAxis(rowAt(0.05f * 22.5f), plain);

		ParallaxLayer flipped = tower();
		flipped.setFlipX(true);
		drawn(true, false, flipped);
		List<float[]> row = rowAt(0.05f * 22.5f);
		for (float[] draw : row)
			isTrue(draw[2] < 0, "drawn right to left");
		// Flipped, the strip starting at the axis starts at its u2: its u is 1 minus the point at the axis.
		TextureRegion image = flipped.getDrawnRegion();
		float atAxis = Float.NaN;
		for (float[] draw : row)
			if (Math.abs(left(draw) - AXIS) < 1e-4f)
				atAxis = (draw[9] - image.getU()) / (image.getU2() - image.getU());
		float expected = 1 - unflipped;
		equal(expected - (float) Math.floor(expected), atAxis - (float) Math.floor(atAxis), 1e-4f, "mirrored round the tower");
	}

	/** A radius on a SHADER layer, or none on an IMAGE one, draws the layer flat: its tiles across the view. */
	void towerRadiusOnAnotherKindDrawsItFlat()
	{
		ParallaxLayer shader = ParallaxLayer.shader(texturedRegion(1024, 512), 40, 0.5f, Enum_ShaderEffect.WAVE, 0, 0, 0);
		shader.setTower(25, 0.65f);
		isTrue(!shader.isTower(), "a SHADER layer is no tower");
		ParallaxPageReader reader = reader(true, false);
		reader.setWorldSize(40, 22.5f);
		reader.setLayerEffects(new RecordingEffects());
		reader.addLayers(list(shader));
		reader.resetPositions();
		draws.clear();
		reader.draw(camera, batch);
		isTrue(draws.size() >= 2 && draws.size() <= 3, "tiled across: " + draws.size());
		equal(20, draws.get(0)[2], 1e-4f, "at its own width");

		ParallaxLayer flat = tower();
		flat.setTower(0, 0.65f);
		drawn(true, false, flat);
		isTrue(draws.size() >= 2 && draws.size() <= 3, "radius 0: flat: " + draws.size());
	}
}
