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
 * scroll turning it, its strips darker toward its sides; seen from close by (r350), its strips scaled about the view's
 * middle and its page's slower layers wrapped round it as panoramas. Shared builders in {@link ReaderFixtures}.
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
				new BrowserCase("reader: towerRadiusOnAnotherKindDrawsItFlat", () -> new TowerCases().towerRadiusOnAnotherKindDrawsItFlat()),
				new BrowserCase("reader: nearTowerArcsItsCoursesAboutTheViewsMiddle", () -> new TowerCases().nearTowerArcsItsCoursesAboutTheViewsMiddle()),
				new BrowserCase("reader: layersBehindANearTowerWrapRoundItInEveryRepeatMode", () -> new TowerCases().layersBehindANearTowerWrapRoundItInEveryRepeatMode()),
				new BrowserCase("reader: panoramaRadiusGrowsWithDistanceAndHoldsTheCameraInside", () -> new TowerCases().panoramaRadiusGrowsWithDistanceAndHoldsTheCameraInside()),
				new BrowserCase("reader: onlySlowerMovingLayersWrapRoundANearTower", () -> new TowerCases().onlySlowerMovingLayersWrapRoundANearTower()));
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

	/** The view's middle, where a tower seen from close by scales about: the 22.5-high view's. */
	private static final float EYE = 22.5f / 2;

	/**
	 * Seen from 3 radii, each strip of the tower is scaled about the view's middle by its front's distance over its own,
	 * {@code (3 - 1) / (3 - cos a)}: 1 at the front, 2/3 at the sides, so its bottom (under the eye) rises toward the
	 * sides and its courses arc; from 1 radius or less, from far away: every strip as high as the layer.
	 */
	void nearTowerArcsItsCoursesAboutTheViewsMiddle()
	{
		ParallaxLayer tower = tower();
		tower.setTowerCamera(3);
		isTrue(tower.isNearTower(), "a tower with a camera of 3 is seen from close by");
		drawn(true, false, tower);
		equal(ParallaxLayer.TOWER_STRIPS, draws.size(), 3, "one row of strips");
		float bottom = 0.05f * 22.5f, front = 0, side = Float.MAX_VALUE;
		for (float[] draw : draws)
		{
			float scale = draw[3] / 10;
			equal(EYE + (bottom - EYE) * scale, draw[1], 1e-4f, "scaled about the view's middle");
			isTrue(scale <= 1 + 1e-5f && scale >= 2f / 3 - 1e-3f, "between 2/3 and 1: " + scale);
			front = Math.max(front, scale);
			side = Math.min(side, scale);
		}
		equal(1, front, 1e-3f, "the front at its own height");
		equal(2f / 3, side, 1e-2f, "the sides at 2/3 of it");
		float[] sideStrip = draws.get(0), middle = draws.get(draws.size() / 2);
		isTrue(sideStrip[1] > middle[1], "its bottom rises toward the sides: " + sideStrip[1] + " over " + middle[1]);
		equal(ParallaxLayer.towerScaleAt(3, 1), (3 - 1) / (3 - (float) Math.cos(1)), 1e-6f, "the scale's law");

		for (float camera : new float[] { 0, 1 })
		{
			ParallaxLayer far = tower();
			far.setTowerCamera(camera);
			isTrue(!far.isNearTower(), "a camera of " + camera + " is far away");
			drawn(true, false, far);
			for (float[] draw : draws)
				equal(10, draw[3], 1e-4f, "camera " + camera + ": every strip as high as the layer");
		}
	}

	/**
	 * A layer slower than a tower seen from close by is a panorama round its axis: cut in the view's strips, edge to edge
	 * across the view, as flat at the middle and taller toward the sides, every piece scaled about the view's middle, so
	 * its bottom (under the eye) drops toward the sides; in every repeat mode. The tower's camera at 0: flat again.
	 */
	void layersBehindANearTowerWrapRoundItInEveryRepeatMode()
	{
		for (boolean[] mode : new boolean[][] { { true, false }, { false, true }, { true, true }, { false, false } })
		{
			String repeat = "repeat X " + mode[0] + ", Y " + mode[1];
			ParallaxLayer back = layer(10), tower = tower();
			tower.setTowerCamera(3);
			drawn(mode[0], mode[1], back, tower);
			List<float[]> pieces = new ArrayList<>();
			for (float[] draw : draws)
				if (Math.abs(draw[1] + draw[3] - (EYE + (0.1f * 22.5f + 22.5f - EYE) * (draw[3] / 22.5f))) < 1e-3f)
					pieces.add(draw);
			isTrue(pieces.size() >= 100, repeat + ": the back layer cut in strips, each scaled about the middle: " + pieces.size());
			pieces.sort((a, b) -> Float.compare(left(a), left(b)));
			equal(0, left(pieces.get(0)), 1e-3f, repeat + ": from the view's left edge");
			equal(40, right(pieces.get(pieces.size() - 1)), 1e-3f, repeat + ": to its right one");
			float middle = Float.MAX_VALUE, side = 0;
			for (int i = 0; i < pieces.size(); i++)
			{
				float[] piece = pieces.get(i);
				if (i > 0)
					equal(right(pieces.get(i - 1)), left(piece), 1e-3f, repeat + ": piece " + i + " starts where the one before ends");
				float scale = piece[3] / 22.5f;
				isTrue(scale >= 1 - 1e-4f, repeat + ": never smaller than flat: " + scale);
				middle = Math.min(middle, scale);
				side = Math.max(side, scale);
			}
			equal(1, middle, 1e-2f, repeat + ": as flat at the middle");
			isTrue(side > 1.2f, repeat + ": taller toward the sides: " + side);
			isTrue(pieces.get(0)[1] < pieces.get(pieces.size() / 2)[1], repeat + ": its bottom drops toward the sides");

			ParallaxLayer flatBack = layer(10), farTower = tower();
			drawn(mode[0], mode[1], flatBack, farTower);
			int flat = 0;
			for (float[] draw : draws)
				if (Math.abs(draw[3] - 22.5f) < 1e-4f && Math.abs(draw[2]) > 39)
					flat++;
			isTrue(flat >= 1, repeat + ": a tower seen from far away leaves it flat");
		}
	}

	/**
	 * The panorama's radius: the tower's front 2 radii away (camera 3, radius 10), a layer 4 times slower is 80 away, its
	 * radius 80 - 30 = 50; one hardly slower would sit inside the camera's circle, so it is held at the camera's 30.
	 */
	void panoramaRadiusGrowsWithDistanceAndHoldsTheCameraInside()
	{
		ParallaxPageReader reader = reader(true, false);
		reader.setWorldSize(40, 22.5f);
		ParallaxLayer tower = tower(), back = layer(10);
		tower.setTowerCamera(3);
		reader.addLayers(list(back, tower));
		ParallaxLayer taken = reader.layers.get(1);
		equal(taken, ParallaxPageReader.nearTowerOf(reader.layers), "the page's near tower");
		ParallaxLayer slow = reader.layers.get(0);
		slow.setParallaxSpeedRatioX(0.0125f);
		equal(50, ParallaxPageReader.panoramaRadiusOf(taken, slow), 1e-3f, "4 times slower: radius 50");
		slow.setParallaxSpeedRatioX(0.04f);
		equal(30, ParallaxPageReader.panoramaRadiusOf(taken, slow), 1e-3f, "hardly slower: held at the camera's distance");
		taken.setTowerCamera(0);
		equal(null, ParallaxPageReader.nearTowerOf(reader.layers), "no near tower when it is seen from far away");
	}

	/** Behind a near tower, a still layer (the sky), a faster one (in front of it) and a SHADER-less hook stay flat. */
	void onlySlowerMovingLayersWrapRoundANearTower()
	{
		ParallaxLayer tower = tower();
		tower.setTowerCamera(3);
		ParallaxLayer still = layer(10), faster = layer(10);
		still.setParallaxSpeedRatioX(0);
		faster.setParallaxSpeedRatioX(0.08f);
		equal(0, ParallaxPageReader.panoramaRadiusOf(tower, still), 0, "a still layer is the sky");
		equal(0, ParallaxPageReader.panoramaRadiusOf(tower, faster), 0, "a faster one is in front of the tower");
		equal(0, ParallaxPageReader.panoramaRadiusOf(tower, tower), 0, "nor the tower itself");
		equal(0, ParallaxPageReader.panoramaRadiusOf(null, still), 0, "no tower, no panorama");
		drawn(true, false, still, faster, tower);
		int flat = 0;
		for (float[] draw : draws)
			if (Math.abs(draw[3] - 22.5f) < 1e-4f)
				flat++;
		isTrue(flat >= 2 && flat <= 6, "both drawn as flat tiles: " + flat);
	}
}
