package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isFalse;
import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.SequenceCycle;

/**
 * ParallaxPageReader's cases on SEQUENCE layers: the segments in view, pads, mirror, the pinned cycle of a known seed,
 * the game seed. Shared builders in {@link ReaderFixtures}.
 */
final class SequenceCases extends ReaderFixtures
{
	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("reader: sequenceDrawsJustTheSegmentsInViewInEveryRepeatMode", () -> new SequenceCases().sequenceDrawsJustTheSegmentsInViewInEveryRepeatMode()),
				new BrowserCase("reader: sequenceWithAPadBackOverASegmentStillDrawsWhatShows", () -> new SequenceCases().sequenceWithAPadBackOverASegmentStillDrawsWhatShows()),
				new BrowserCase("reader: sequenceWhosePadsOutweighItsSegmentsIsDrawnOnce", () -> new SequenceCases().sequenceWhosePadsOutweighItsSegmentsIsDrawnOnce()),
				new BrowserCase("reader: sequenceMirrorFlipsEachSegmentInItsSlot", () -> new SequenceCases().sequenceMirrorFlipsEachSegmentInItsSlot()),
				new BrowserCase("reader: sequenceCycleOfAKnownSeedIsPinned", () -> new SequenceCases().sequenceCycleOfAKnownSeedIsPinned()),
				new BrowserCase("reader: aGameSeedRedrawsTheSequencesOfBothPages", () -> new SequenceCases().aGameSeedRedrawsTheSequencesOfBothPages()));
	}

	/** Segment widths of {@link #sequence}, in world units: 2, 1 and 3 layer heights of 3. */
	private static final float[] SEGMENT_WIDTHS = { 6, 3, 9 };

	/**
	 * A SEQUENCE layer of three segments 200x100, 100x100 and 300x100 px, weighing 1, 2 and 3: the first 6 world units
	 * wide in a 40-wide world, so 3 high, the others 3 and 9 wide. Placed at a quarter of the world on both axes.
	 */
	private static ParallaxLayer sequence(int seed, int length)
	{
		ParallaxLayer layer = ParallaxLayer.sequence(list(region(200, 100), region(100, 100), region(300, 100)),
				new int[] { 1, 2, 3 }, seed, length, 40, 0.15f);
		layer.setParallaxSpeedRatioX(0.02f);
		layer.setParallaxSpeedRatioY(0.02f);
		layer.setDecalPercentX(25);
		layer.setDecalPercentY(25);
		layer.setPadX(0.5f);
		layer.setPadY(1);
		return layer;
	}

	private static List<TextureRegion> list(TextureRegion... regions)
	{return new ArrayList<>(Arrays.asList(regions));}

	/**
	 * Where every segment of the layer that shows in the 40 x 22.5 view at the origin is, worked out slot by slot, cycle
	 * by cycle, without the reader's search: x, y, width, height, sorted by y then x.
	 */
	private static List<float[]> segmentsInView(ParallaxLayer layer, boolean onX, boolean onY)
	{
		float height = 3, stepY = height + layer.getPadY();
		float cycle = 0;
		for (int slot = 0; slot < layer.getSequenceLength(); slot++)
			cycle += SEGMENT_WIDTHS[layer.getCycleSegment(slot)] + layer.getPadX();
		List<float[]> expected = new ArrayList<>();
		for (int row = onY ? -40 : 0; row <= (onY ? 40 : 0); row++)
		{
			float y = layer.getCurrentDistanceY() + row * stepY;
			if (y + height <= 0 || y >= 22.5f)
				continue;
			for (int copy = onX ? -40 : 0; copy <= (onX ? 40 : 0); copy++)
			{
				float x = layer.getCurrentDistanceX() + copy * cycle;
				for (int slot = 0; slot < layer.getSequenceLength(); slot++)
				{
					float width = SEGMENT_WIDTHS[layer.getCycleSegment(slot)];
					if (x + width > 0 && x < 40)
						expected.add(new float[] { x, y, width, height });
					x += width + layer.getPadX();
				}
			}
		}
		sortByRowThenX(expected);
		return expected;
	}

	/** Insertion sort: GWT has List.sort, but a Comparator lambda over float[] reads worse than this. */
	private static void sortByRowThenX(List<float[]> draws)
	{
		for (int i = 1; i < draws.size(); i++)
			for (int j = i; j > 0; j--)
			{
				float[] a = draws.get(j - 1), b = draws.get(j);
				boolean after = a[1] > b[1] + 1e-3f || (Math.abs(a[1] - b[1]) <= 1e-3f && a[0] > b[0]);
				if (!after)
					break;
				draws.set(j - 1, b);
				draws.set(j, a);
			}
	}

	/**
	 * A SEQUENCE layer between two image layers draws, in its place, each segment of its cycle that shows and no other:
	 * the reader's search over the cycle against a slot-by-slot walk of every copy of it, in all four repeat modes.
	 */
	void sequenceDrawsJustTheSegmentsInViewInEveryRepeatMode()
	{
		for (int mode = 0; mode < 4; mode++)
		{
			boolean onX = mode == 0 || mode == 2, onY = mode == 1 || mode == 2;
			String name = onX && onY ? "XY" : onX ? "X" : onY ? "Y" : "none";

			ParallaxPageReader reader = reader(onX, onY);
			reader.setWorldSize(40, 22.5f);
			ParallaxLayer behind = layer(0), ground = sequence(42, 10), before = layer(0);
			reader.addLayers(list(behind, ground, before));
			// Far enough that the view starts inside a later slot of the cycle, on both axes.
			for (int frame = 0; frame < 30; frame++)
				reader.act(1 / 60f, 2000, 700);
			draws.clear();
			reader.draw(camera, batch);

			// The image layers' tiles are 40 wide, every segment narrower: they are the draws of the sequence.
			List<float[]> drawn = new ArrayList<>();
			int first = -1, last = -1;
			for (int i = 0; i < draws.size(); i++)
				if (draws.get(i)[2] < 39)
				{
					drawn.add(draws.get(i));
					first = first < 0 ? i : first;
					last = i;
				}
			equal(drawn.size(), last - first + 1, name + ": the segments are drawn in one run, in the layer's place");
			isTrue(first > 0 && last < draws.size() - 1, name + ": between the layers behind and before it");
			sortByRowThenX(drawn);

			List<float[]> expected = segmentsInView(ground, onX, onY);
			isTrue(expected.size() > 0, name + ": the layer shows");
			equal(expected.size(), drawn.size(), name + ": one draw per segment in view");
			for (int i = 0; i < expected.size(); i++)
				for (int k = 0; k < 4; k++)
					equal(expected.get(i)[k], drawn.get(i)[k], 1e-3f, name + ": segment " + i + " value " + k);
		}
	}

	/**
	 * A padX of -4 steps back over the 3-wide segment: the slots' edges no longer grow left to right, the reader looks at
	 * every slot rather than search, and still draws only the ones that show.
	 */
	void sequenceWithAPadBackOverASegmentStillDrawsWhatShows()
	{
		for (boolean onX : new boolean[] { true, false })
		{
			ParallaxPageReader reader = reader(onX, false);
			reader.setWorldSize(40, 22.5f);
			ParallaxLayer ground = sequence(42, 10);
			ground.setPadX(-4);
			ground.setDecalPercentX(-20);
			reader.addLayers(list(ground));
			draws.clear();
			reader.draw(camera, batch);

			List<float[]> drawn = new ArrayList<>(draws);
			sortByRowThenX(drawn);
			List<float[]> expected = segmentsInView(ground, onX, false);
			isTrue(expected.size() > 0, "the layer shows");
			equal(expected.size(), drawn.size(), (onX ? "X" : "none") + ": one draw per segment in view");
			for (int i = 0; i < expected.size(); i++)
				for (int k = 0; k < 4; k++)
					equal(expected.get(i)[k], drawn.get(i)[k], 1e-3f, "segment " + i + " value " + k);
		}
	}

	/**
	 * A padX of -10 steps back over every segment (3 to 9 wide): the cycle is no wider than 0, its slots running left
	 * from its corner. It can't tile on X, so it is drawn once, as an image layer whose step is 0 or less is, every slot
	 * that shows and no other; on Y it still tiles. Its corner in the view, then past its right edge: the slots it runs
	 * back into the view are drawn even though the corner is not in it.
	 */
	void sequenceWhosePadsOutweighItsSegmentsIsDrawnOnce()
	{
		for (int mode = 0; mode < 8; mode++)
		{
			// Its corner at 20.4, or at 48.4: off whole units, so no slot's edge lands on the view's, where a browser's
			// doubles and the JVM's floats round apart.
			float decal = mode < 4 ? 51 : 121;

			boolean onX = mode % 4 == 0 || mode % 4 == 2, onY = mode % 4 == 1 || mode % 4 == 2;
			String name = (onX && onY ? "XY" : onX ? "X" : onY ? "Y" : "none") + " at " + decal + "%";
			ParallaxPageReader reader = reader(onX, onY);
			reader.setWorldSize(40, 22.5f);
			ParallaxLayer ground = sequence(42, 10);
			ground.setPadX(-10);
			ground.setDecalPercentX(decal);
			reader.addLayers(list(ground));
			isTrue(ground.getWidth() <= 0, name + ": the cycle is " + ground.getWidth() + " wide");
			isTrue(ground.getCycleLeft() < -20, name + ": its slots reach " + ground.getCycleLeft() + " left of its corner");
			draws.clear();
			reader.draw(camera, batch);

			List<float[]> drawn = new ArrayList<>(draws);
			sortByRowThenX(drawn);
			List<float[]> expected = segmentsInView(ground, false, onY);
			isTrue(expected.size() >= 2, name + ": " + expected.size() + " slots show");
			equal(expected.size(), drawn.size(), name + ": one draw per slot in view, the cycle once across");
			for (int i = 0; i < expected.size(); i++)
				for (int k = 0; k < 4; k++)
					equal(expected.get(i)[k], drawn.get(i)[k], 1e-3f, name + ": slot " + i + " value " + k);
		}
	}

	/**
	 * Mirrored on a page tiling on X, the strip above is each segment upside down in its own slot; on Y, the column to the
	 * right is each segment reversed in its own slot. The slots keep their order.
	 */
	void sequenceMirrorFlipsEachSegmentInItsSlot()
	{
		for (boolean onX : new boolean[] { true, false })
		{
			ParallaxPageReader reader = reader(onX, !onX);
			reader.setWorldSize(40, 22.5f);
			// Three slots, 13.5 wide: the strip and its mirrored copy both fit in the view.
			ParallaxLayer ground = sequence(42, 3);
			ground.setMirror(true);
			reader.addLayers(list(ground));
			draws.clear();
			reader.draw(camera, batch);

			List<float[]> plain = new ArrayList<>(), flipped = new ArrayList<>();
			for (float[] draw : draws)
				((onX ? draw[3] < 0 : draw[2] < 0) ? flipped : plain).add(draw);
			isTrue(plain.size() > 0, "the strip shows");
			for (float[] copy : flipped)
			{
				// Flipped, a draw starts at its far edge: put it back to its box.
				float x = onX ? copy[0] : copy[0] + copy[2], y = onX ? copy[1] + copy[3] : copy[1];
				float width = Math.abs(copy[2]), height = Math.abs(copy[3]);
				boolean matched = false;
				for (float[] draw : plain)
					if (onX ? Math.abs(draw[0] - x) < 1e-3f && Math.abs(draw[2] - width) < 1e-3f && Math.abs(draw[1] + 3 + 1 - y) < 1e-3f
							: Math.abs(draw[1] - y) < 1e-3f && Math.abs(draw[3] - height) < 1e-3f && Math.abs(draw[0] + ground.getTotalWidth() - x) < 1e-3f)
						matched = true;
				isTrue(matched, (onX ? "X" : "Y") + ": the mirrored segment at " + x + ", " + y + " sits over one of the strip's, as wide");
			}
			equal(plain.size(), flipped.size(), (onX ? "X" : "Y") + ": a mirrored copy of each segment");
		}
	}

	/**
	 * The generator's first picks, pinned: Godot's plax_page.gd must draw the same from the same seed (docs/
	 * sequence-layers.md), and the browser runs this case too. Worked out again in Python for r182.
	 */
	void sequenceCycleOfAKnownSeedIsPinned()
	{
		equal(270369, SequenceCycle.next(1), "xorshift32 (13, 17, 5) of 1");

		int[] cycle = new int[12];
		SequenceCycle.draw(42, new int[] { 1, 2, 3 }, cycle);
		equal("[0, 1, 1, 2, 0, 2, 0, 2, 2, 2, 2, 2]", Arrays.toString(cycle), "seed 42, weights 1 2 3");
		SequenceCycle.draw(-1640531527, new int[] { 25, 50, 25 }, cycle);
		equal("[1, 1, 2, 0, 2, 0, 1, 0, 1, 2, 0, 0]", Arrays.toString(cycle), "a negative seed, weights 25 50 25");

		int[] zero = new int[8], fixed = new int[8];
		SequenceCycle.draw(0, new int[] { 1, 1 }, zero);
		SequenceCycle.draw(SequenceCycle.ZERO_SEED, new int[] { 1, 1 }, fixed);
		equal("[1, 0, 0, 0, 0, 1, 0, 1]", Arrays.toString(zero), "seed 0 starts from ZERO_SEED, not from a stuck 0");
		equal(Arrays.toString(fixed), Arrays.toString(zero), "seed 0");

		// The weights are the odds: 25 50 25 over 20000 picks, within 2%.
		int[] many = new int[20000], counts = new int[3];
		SequenceCycle.draw(7, new int[] { 25, 50, 25 }, many);
		for (int pick : many)
			counts[pick]++;
		equal(0.25f, counts[0] / 20000f, 0.02f, "segment 0");
		equal(0.5f, counts[1] / 20000f, 0.02f, "segment 1");
		equal(0.25f, counts[2] / 20000f, 0.02f, "segment 2");

		// A weight of 0 is never picked; none above 0, each weighs 1.
		SequenceCycle.draw(7, new int[] { 0, 5, -3 }, cycle);
		equal("[1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1]", Arrays.toString(cycle), "only the weighted segment");
		SequenceCycle.draw(7, new int[] { 0, 0 }, many);
		counts[0] = counts[1] = 0;
		for (int pick : many)
			counts[pick]++;
		isTrue(counts[0] > 9000 && counts[1] > 9000, "no weight: even odds, " + counts[0] + " and " + counts[1]);
	}

	/**
	 * A game's seed draws every SEQUENCE layer from it XOR the layer's stored seed, on screen and in the page it fades
	 * into; clearing it goes back to the stored seeds.
	 */
	void aGameSeedRedrawsTheSequencesOfBothPages()
	{
		ParallaxPageReader reader = reader(true, false);
		ParallaxLayer ground = sequence(42, 10), hills = sequence(43, 10);
		reader.addLayers(list(ground, hills));
		equal(42, ground.getDrawnSeed(), "the page's seed");
		String stored = cycleOf(ground);

		reader.setSequenceSeed(1000);
		equal(1000 ^ 42, ground.getDrawnSeed(), "the game's seed XOR the page's");
		equal(1000 ^ 43, hills.getDrawnSeed(), "each layer its own");
		isFalse(stored.equals(cycleOf(ground)), "another cycle: " + stored);
		equal(42, ground.getSequenceSeed(), "the page keeps its seed");

		ParallaxLayer next = sequence(42, 10);
		reader.addLayersTransfert(page(list(next, sequence(43, 10))), 1);
		equal(1000 ^ 42, next.getDrawnSeed(), "the incoming page takes the game's seed");
		equal(cycleOf(ground), cycleOf(next), "the same stored seed, the same cycle");

		reader.clearSequenceSeed();
		equal(42, ground.getDrawnSeed(), "back to the page's seed");
		equal(42, next.getDrawnSeed(), "in the incoming page too");
		equal(stored, cycleOf(ground), "and its cycle");
	}

	private static String cycleOf(ParallaxLayer layer)
	{
		int[] cycle = new int[layer.getSequenceLength()];
		for (int slot = 0; slot < cycle.length; slot++)
			cycle[slot] = layer.getCycleSegment(slot);
		return Arrays.toString(cycle);
	}
}
