package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.pages.Enum_FloorMapping;

/**
 * ParallaxPageReader's cases on FLOOR layers: rows at their own speed, mode 7, the strips, the layers planted on a
 * floor, its height. Shared builders in {@link ReaderFixtures}.
 */
final class FloorCases extends ReaderFixtures
{
	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("reader: floorDrawsEachRowAtItsSpeedInEveryRepeatMode", () -> new FloorCases().floorDrawsEachRowAtItsSpeedInEveryRepeatMode()),
				new BrowserCase("reader: mode7FloorStretchesItsRowsAboutTheViewsMiddle", () -> new FloorCases().mode7FloorStretchesItsRowsAboutTheViewsMiddle()),
				new BrowserCase("reader: floorStripsCutTheImageByDepth", () -> new FloorCases().floorStripsCutTheImageByDepth()),
				new BrowserCase("reader: layersInFrontOfAFloorArePlantedOnIt", () -> new FloorCases().layersInFrontOfAFloorArePlantedOnIt()),
				new BrowserCase("reader: floorHeightSetsTheBoxNotTheTile", () -> new FloorCases().floorHeightSetsTheBoxNotTheTile()));
	}

	/**
	 * A FLOOR layer 16 wide and 4 high (a 1024 x 256 image at sizeRatio 0.4), its bottom 10% up the world, in 8 rows from
	 * 0.06 at the bottom to 0.016 at the top.
	 */
	private static ParallaxLayer floor(Enum_FloorMapping mapping)
	{
		ParallaxLayer floor = ParallaxLayer.floor(texturedRegion(1024, 256), 40, 0.4f, mapping, 0.06f, 0.016f, 8);
		floor.setDecalPercentY(10);
		return floor;
	}

	/** Row i of 8's speed: the bottom row's to the top row's, straight, at the row's middle. */
	private static float rowSpeed(int i)
	{return 0.06f + (0.016f - 0.06f) * (i + 0.5f) / 8;}

	/** The draws of the floor's row {@code i} (its y), left to right. */
	private List<float[]> row(int i)
	{
		List<float[]> row = new ArrayList<>();
		for (float[] draw : draws)
			if (Math.abs(draw[1] - (2.25f + i * 0.5f)) < 1e-4f)
				row.add(draw);
		return row;
	}

	/**
	 * Line scroll: each row is the floor's tile, 16 wide and a row high, tiled across the whole view from where that row
	 * has scrolled to at its own speed; the same in every repeat mode (a floor tiles across whatever the page repeats).
	 */
	void floorDrawsEachRowAtItsSpeedInEveryRepeatMode()
	{
		for (boolean[] mode : new boolean[][] { { true, false }, { false, true }, { true, true }, { false, false } })
		{
			String repeat = "repeat X " + mode[0] + ", Y " + mode[1];
			ParallaxPageReader reader = reader(mode[0], mode[1]);
			reader.setWorldSize(40, 22.5f);
			reader.addLayers(list(floor(Enum_FloorMapping.LINE_SCROLL)));
			reader.resetPositions();
			draws.clear();
			reader.act(1, 100, 0);
			reader.draw(camera, batch);
			int total = 0;
			for (int i = 0; i < 8; i++)
			{
				List<float[]> row = row(i);
				total += row.size();
				isTrue(row.size() >= 3 && row.size() <= 4, repeat + ": row " + i + " covers the view in 3 or 4 tiles: " + row.size());
				float first = row.get(0)[0];
				isTrue(first <= 0 && first + 16 > 0, repeat + ": row " + i + " starts at the view's left edge: " + first);
				isTrue(row.get(row.size() - 1)[0] < 40 && row.get(row.size() - 1)[0] + 16 >= 40, repeat + ": and ends past its right one");
				for (int k = 0; k < row.size(); k++)
				{
					equal(first + 16 * k, row.get(k)[0], 1e-3f, repeat + ": row " + i + " tile " + k);
					equal(16, row.get(k)[2], 1e-4f, repeat + ": a tile as wide as the floor");
					equal(0.5f, row.get(k)[3], 1e-5f, repeat + ": a row high");
				}
				// Scrolled 100 units of view at its own speed: its tiles start where -100 * speed falls, within one tile.
				float moved = -100 * rowSpeed(i);
				float off = (first - moved) / 16;
				equal(Math.round(off), off, 1e-3f, repeat + ": row " + i + " has moved at " + rowSpeed(i));
			}
			equal(total, draws.size(), repeat + ": nothing but the rows is drawn");
		}
		// The rows move apart: the top row has moved less than the bottom one.
		isTrue(rowSpeed(7) < rowSpeed(0), "the top row is the slowest");
	}

	/**
	 * Mode 7: row i's tiles are 16 * its speed over the bottom row's wide, stretched about the view's middle (x 20), so a
	 * still floor's rows all have a tile edge... where 20 - 20 * stretch puts it; scrolled, each from there at its speed.
	 */
	void mode7FloorStretchesItsRowsAboutTheViewsMiddle()
	{
		ParallaxPageReader reader = reader(true, false);
		ParallaxLayer floor = floor(Enum_FloorMapping.MODE7);
		reader.setWorldSize(40, 22.5f);
		reader.addLayers(list(floor));
		reader.resetPositions();
		reader.draw(camera, batch);
		for (int i = 0; i < 8; i++)
		{
			float stretch = rowSpeed(i) / 0.06f, across = 16 * stretch;
			equal(stretch, floor.getRowStretch(i), 1e-5f, "row " + i + "'s stretch");
			List<float[]> row = row(i);
			for (float[] draw : row)
				equal(across, draw[2], 1e-3f, "row " + i + "'s tiles");
			float off = (row.get(0)[0] - (20 - 20 * stretch)) / across;
			equal(Math.round(off), off, 1e-3f, "row " + i + " still: a tile edge at 20 - 20 * stretch");
			isTrue(row.get(0)[0] <= 0 && row.get(row.size() - 1)[0] + across >= 40, "row " + i + " covers the view");
		}
		isTrue(row(7).get(0)[2] < row(0).get(0)[2] / 2, "the top row's tiles are under half the bottom row's");

		draws.clear();
		reader.act(1, 100, 0);
		reader.draw(camera, batch);
		for (int i = 0; i < 8; i++)
		{
			float stretch = rowSpeed(i) / 0.06f, across = 16 * stretch;
			float off = (row(i).get(0)[0] - (20 - 20 * stretch) + 100 * rowSpeed(i)) / across;
			equal(Math.round(off), off, 1e-3f, "row " + i + " moved at " + rowSpeed(i));
		}
	}

	/**
	 * The strips each row draws: line scroll cuts the image in 8 equal bands, bottom to top; mode 7 starts at the image's
	 * bottom too, and each row higher up covers more of the image (more ground, farther away), up to its top edge.
	 */
	void floorStripsCutTheImageByDepth()
	{
		ParallaxLayer lines = floor(Enum_FloorMapping.LINE_SCROLL), mode7 = floor(Enum_FloorMapping.MODE7);
		TextureRegion image = lines.getDrawnRegion();
		float span = image.getV2() - image.getV();
		for (int i = 0; i < 8; i++)
		{
			TextureRegion strip = lines.getRowStrip(i);
			equal(image.getV2() - span * i / 8, strip.getV2(), 1e-6f, "line scroll row " + i + "'s bottom");
			equal(image.getV2() - span * (i + 1) / 8, strip.getV(), 1e-6f, "line scroll row " + i + "'s top");
			equal(image.getU(), strip.getU(), 1e-6f, "the whole width");
			equal(image.getU2(), strip.getU2(), 1e-6f, "the whole width");
		}
		equal(image.getV2(), mode7.getRowStrip(0).getV2(), 1e-6f, "mode 7's bottom row starts at the image's bottom");
		float before = 0;
		for (int i = 0; i < 3; i++)
		{
			float covered = mode7.getRowStrip(i).getV2() - mode7.getRowStrip(i).getV();
			isTrue(covered > before, "mode 7's row " + i + " covers more of the image than the one under it: " + covered);
			before = covered;
		}
		for (int i = 0; i < 8; i++)
			isTrue(mode7.getRowStrip(i).getV() >= image.getV() - 1e-6f && mode7.getRowStrip(i).getV2() <= image.getV2() + 1e-6f,
					"mode 7's row " + i + " stays in the image");
	}

	/**
	 * Every layer in front of a floor scrolls at the speed of the floor's row under its foot: its decal, plus the rows an
	 * atlas trimmed off its image's bottom. One whose foot is above the floor's top keeps its own speed, and so does one
	 * behind the floor. The page's stored speeds do not change.
	 */
	void layersInFrontOfAFloorArePlantedOnIt()
	{
		ParallaxPageReader reader = reader(false, false);
		ParallaxLayer back = layer(50), floor = floor(Enum_FloorMapping.MODE7);
		// 4 x 2.25, its foot a quarter up the floor (2.25 + 1).
		ParallaxLayer tree = new ParallaxLayer(region(1920, 1080), true, 40, 0.02f, 0.02f, 0.1f);
		tree.setDecalPercentY(3.25f / 22.5f * 100);
		// A 1000 x 1000 image with its bottom 200 rows trimmed, 4 x 4: its decal at the floor's bottom, its foot 0.8 up.
		ParallaxLayer trimmed = new ParallaxLayer(strippedRegion(1000, 800, 1000, 1000, 0, 200), true, 40, 0.02f, 0.02f, 0.1f);
		trimmed.setUseOriginalSize(true);
		trimmed.setDecalPercentY(10);
		// Its foot above the floor's top (2.25 + 4).
		ParallaxLayer hanging = layer(40);
		reader.setWorldSize(40, 22.5f);
		reader.addLayers(list(back, floor, tree, trimmed, hanging));
		reader.resetPositions();

		reader.act(1, 100, 0);

		float treeSpeed = 0.06f + (0.016f - 0.06f) * 0.25f, trimmedSpeed = 0.06f + (0.016f - 0.06f) * 0.2f;
		equal(treeSpeed, tree.getActingSpeedX(), 1e-6f, "the tree: the row a quarter up");
		equal(-100 * treeSpeed, tree.getCurrentDistanceX(), 1e-4f, "and it moved at it");
		equal(trimmedSpeed, trimmed.getActingSpeedX(), 1e-6f, "the trimmed one: where its image starts, a fifth up");
		equal(0.02f, hanging.getActingSpeedX(), 0, "above the floor: its own");
		equal(-2, hanging.getCurrentDistanceX(), 1e-4f, "and it moved at it");
		equal(0.02f, back.getActingSpeedX(), 0, "behind the floor: its own");
		isTrue(Float.isNaN(floor.getPlantedSpeedX()), "the floor itself is not planted");
		equal(0.02f, tree.getParallaxSpeedRatioX(), 0, "the tree's stored speed stays");

		// The floor's speeds edited: planted again on the next frame.
		floor.setParallaxSpeedRatioX(0.08f);
		reader.act(0, 0, 0);
		equal(0.08f + (0.016f - 0.08f) * 0.25f, tree.getActingSpeedX(), 1e-6f, "replanted on the new bottom row");
		// No floor left: every layer its own speed.
		reader.layers.remove(floor);
		reader.act(0, 0, 0);
		equal(0.02f, tree.getActingSpeedX(), 0, "no floor, its own speed");
	}

	/**
	 * A floor's height (percent of the world height) sets its box, not its tiles: its rows share the box, its tiles stay
	 * the image at the layer's width, and in mode 7 a taller box over the same image shows more ground (its rows reach
	 * farther, a step up a row covering more of the image), as the floor lab's 40% box over a square ground did.
	 */
	void floorHeightSetsTheBoxNotTheTile()
	{
		ParallaxPageReader reader = reader(true, false);
		reader.setWorldSize(40, 22.5f);
		ParallaxLayer tall = ParallaxLayer.floor(texturedRegion(1024, 256), 40, 0.4f, Enum_FloorMapping.MODE7, 0.06f, 0.016f, 8);
		tall.setFloor(Enum_FloorMapping.MODE7, 0.016f, 8, 40);
		tall.setDecalPercentY(10);
		reader.addLayers(list(tall));
		reader.resetPositions();
		equal(9, tall.getHeight(), 1e-4f, "40% of the world");
		reader.draw(camera, batch);
		for (float[] draw : draws)
			equal(9 / 8f, draw[3], 1e-4f, "a row an eighth of the box");
		equal(16 * rowSpeed(0) / 0.06f, row(0).get(0)[2], 1e-3f, "the bottom row's tiles: the image at the layer's width, stretched by its row");

		ParallaxLayer image = floor(Enum_FloorMapping.MODE7);
		image.setWorldSize(40, 22.5f);
		TextureRegion bottomTall = tall.getRowStrip(0), bottomImage = image.getRowStrip(0);
		isTrue(bottomTall.getV2() - bottomTall.getV() > (bottomImage.getV2() - bottomImage.getV()) * 2,
				"the 9-high box's bottom row covers over twice the image the 4-high one's does: 9 / 4 as much ground");

		// Resized, the box follows the world.
		reader.setWorldSize(40, 30);
		equal(12, tall.getHeight(), 1e-4f, "40% of the new world");
	}
}
