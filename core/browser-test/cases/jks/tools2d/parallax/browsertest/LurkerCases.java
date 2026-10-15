package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isFalse;
import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.SequenceCycle;
import jks.tools2d.parallax.pages.Enum_LayerKind;

/**
 * ParallaxPageReader's cases on LURKER layers (r370, docs/lurker-layers.md): the figure appears or vanishes only while
 * nothing of it is on screen, gone at the sighting after one where it was seen, there by its seed's draws, or by
 * reveal(name). Shared builders in {@link ReaderFixtures}.
 * <p>
 * The page: a wide layer behind, the figure (2 x 4 world units, one every 20) and a bar in front of it (10 x 10, one
 * every 10) whose left 7 units stand for its opaque pixels: it covers the figure wholly when the figure's tile corner is
 * 0 to 5 past the bar's, which its cover says, (1, 0, 5). Whether a frame shows the figure is judged here from where
 * the bar's tiles were drawn, not from the reader's cover.
 */
final class LurkerCases extends ReaderFixtures
{
	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("reader: lurkerNeverSwitchesInSightInEveryRepeatMode", () -> new LurkerCases().lurkerNeverSwitchesInSightInEveryRepeatMode()),
				new BrowserCase("reader: lurkerDrawsOfAKnownSeedArePinned", () -> new LurkerCases().lurkerDrawsOfAKnownSeedArePinned()),
				new BrowserCase("reader: lurkerSeenIsGoneAtTheNextSighting", () -> new LurkerCases().lurkerSeenIsGoneAtTheNextSighting()),
				new BrowserCase("reader: revealShowsTheNextSightingNeverThisOne", () -> new LurkerCases().revealShowsTheNextSightingNeverThisOne()));
	}

	private static final float FIGURE_WIDTH = 2, FIGURE_HEIGHT = 4, BAR_WIDTH = 10, BAR_OPAQUE = 7;

	private ParallaxLayer figure, bar;
	private ParallaxPageReader reader;
	/** Frames stepped, and how many times the figure switched. */
	private int frames, switches;

	/** The page, on a reader repeating as given: the figure at speed 0.02, the bar at 0.04, both 10% up the world. */
	private void page(boolean onX, boolean onY, int seed, int odds)
	{
		reader = reader(onX, onY);
		reader.setWorldSize(40, 22.5f);
		figure = new ParallaxLayer(Enum_LayerKind.LURKER, new ArrayList<>(Arrays.asList(region(100, 200))), true, 40, 0.02f, 0, 0.05f);
		figure.setName("hooded");
		figure.setPadX(20 - FIGURE_WIDTH);
		// One row in the view: what repeats on Y is far apart.
		figure.setPadY(30);
		figure.setDecalPercentY(10);
		figure.setLurker(seed, odds, new float[] { 1, 0, BAR_OPAQUE - FIGURE_WIDTH });
		bar = new ParallaxLayer(region(1000, 1000), true, 40, 0.04f, 0, 0.25f);
		bar.setPadY(30);
		bar.setDecalPercentY(10);
		reader.addLayers(list(layer(0), figure, bar));
		reader.resetPositions();
		frames = switches = 0;
	}

	/**
	 * One frame of 1/60 s, the camera scrolling 300 units a second, its way turned every {@code turn} s (0: never), then
	 * drawn. When the figure switched, every copy of it in the view must be wholly behind a bar's opaque part, as the
	 * bar's draws place it, or none in the view; and its draws are its copies in the view when it is there, none when not.
	 */
	private void step(String mode, float turn)
	{
		boolean was = figure.isLurkerThere();
		float speed = turn > 0 && (int) (frames / (turn * 60)) % 2 == 1 ? -300 : 300;
		draws.clear();
		reader.act(1 / 60f, speed, 0);
		reader.draw(camera, batch);
		frames++;
		boolean there = figure.isLurkerThere();
		int inView = 0, shown = 0, bare = 0;
		for (float[] copy : copiesInView())
		{
			inView++;
			if (!behindABar(copy))
				bare++;
		}
		for (float[] draw : draws)
			if (Math.abs(draw[2] - FIGURE_WIDTH) < 1e-3f)
				shown++;
		equal(there ? inView : 0, shown, mode + ", frame " + frames + ": the figure's draws, it being " + (there ? "there" : "gone"));
		if (there != was)
		{
			switches++;
			equal(0, bare, mode + ", frame " + frames + ": switched " + (there ? "on" : "off") + " with copies of it in sight");
		}
	}

	/** Where the copies of the figure in the view stand: x and y of each tile's corner, as the reader tiles it. */
	private List<float[]> copiesInView()
	{
		List<float[]> copies = new ArrayList<>();
		// The camera's view is the world, its corner at 0: a tile's corner is the layer's distance.
		float x0 = figure.getCurrentDistanceX(), y0 = figure.getCurrentDistanceY();
		float stepX = figure.getTotalWidth(), stepY = figure.getTotalHeight();
		for (int column = -10; column <= 10; column++)
			for (int row = -10; row <= 10; row++)
			{
				if ((column != 0 && !reader.isRepeatOnX()) || (row != 0 && !reader.isRepeatOnY()))
					continue;
				float x = x0 + column * stepX, y = y0 + row * stepY;
				if (x + FIGURE_WIDTH > 0 && x < 40 && y + FIGURE_HEIGHT > 0 && y < 22.5f)
					copies.add(new float[] { x, y });
			}
		return copies;
	}

	/** Whether one of this frame's bar draws has its opaque part over the whole of {@code copy}. */
	private boolean behindABar(float[] copy)
	{
		for (float[] draw : draws)
			if (Math.abs(draw[2] - BAR_WIDTH) < 1e-3f && Math.abs(draw[1] - copy[1]) < 1e-3f && draw[0] <= copy[0] + 1e-4f
					&& copy[0] + FIGURE_WIDTH <= draw[0] + BAR_OPAQUE + 1e-4f)
				return true;
		return false;
	}

	/**
	 * 20 s of scroll in every repeat mode, odds 1 (gone after each sighting it was seen in, there at the next): the figure
	 * switches, and never in sight. Repeating on X, it is always in the view and switches behind the bar, the way turned
	 * every 3 s so that it goes behind the bar from both sides; on Y or none, the way turns every second and it switches
	 * out of the view.
	 */
	void lurkerNeverSwitchesInSightInEveryRepeatMode()
	{
		for (int mode = 0; mode < 4; mode++)
		{
			boolean onX = mode == 0 || mode == 2, onY = mode == 1 || mode == 2;
			String name = onX && onY ? "XY" : onX ? "X" : onY ? "Y" : "none";
			page(onX, onY, 7, 1);
			for (int i = 0; i < 1200; i++)
				step(name, onX ? 3 : 1);
			isTrue(switches >= 4, name + ": " + switches + " switches in 20 s; the case sees nothing");
		}
	}

	/**
	 * Seed 12345, odds 3: whether the figure is there at each of its first 12 sightings. Each engine draws the same:
	 * a change here is a change of every saved lurker's scares. Checked against the rule written out here too.
	 */
	void lurkerDrawsOfAKnownSeedArePinned()
	{
		boolean[] pinned = { false, false, false, false, false, true, false, true, false, false, false, false };
		boolean[] drawn = sightings(12345, 3, pinned.length);
		// The rule: the seed stepped once to start; after a sighting it was seen in, gone; else stepped once more.
		int state = SequenceCycle.next(12345);
		boolean there = (state >>> 1) % 3 == 0;
		for (int i = 0; i < pinned.length; i++)
		{
			equal(there, drawn[i], "sighting " + i + " by the rule");
			if (there)
				there = false;
			else
			{
				state = SequenceCycle.next(state);
				there = (state >>> 1) % 3 == 0;
			}
		}
		equal(Arrays.toString(pinned), Arrays.toString(drawn), "the sightings of seed 12345, odds 3");
	}

	/** Odds 1: there, gone, there, gone: a sighting it was seen in is followed by one where it is not. */
	void lurkerSeenIsGoneAtTheNextSighting()
	{
		boolean[] drawn = sightings(3, 1, 8);
		for (int i = 1; i < drawn.length; i++)
			isTrue(drawn[i] != drawn[i - 1], "sighting " + i + ": " + Arrays.toString(drawn));
	}

	/**
	 * Whether the figure is there at each of its first {@code count} sightings, on a page repeating on neither axis
	 * (no cover: a sighting is a pass through the view), the camera's way turned every second.
	 */
	private boolean[] sightings(int seed, int odds, int count)
	{
		page(false, false, seed, odds);
		boolean[] drawn = new boolean[count];
		int seen = 0;
		boolean inSight = false;
		for (int i = 0; i < 60 * 4 * count && seen < count; i++)
		{
			step("none", 1);
			if (figure.isLurkerInSight() && !inSight)
				drawn[seen++] = figure.isLurkerThere();
			inSight = figure.isLurkerInSight();
		}
		equal(count, seen, "sightings in " + frames + " frames");
		return drawn;
	}

	/**
	 * Odds 1000, a seed whose draws keep the figure away: reveal() in sight changes nothing while it lasts, and shows it
	 * at the next sighting; once seen it is gone again; reveal() out of sight shows it at the next one too.
	 */
	void revealShowsTheNextSightingNeverThisOne()
	{
		page(false, false, 1, 1000);
		equal(false, (SequenceCycle.next(1) >>> 1) % 1000 == 0, "seed 1's first draw keeps it away");
		step("none", 1);
		isTrue(figure.isLurkerInSight(), "it starts in sight");
		isFalse(figure.isLurkerThere(), "and gone");
		reader.reveal("hooded");
		reader.reveal("nobody");
		while (figure.isLurkerInSight())
		{
			step("none", 1);
			if (figure.isLurkerInSight())
				isFalse(figure.isLurkerThere(), "frame " + frames + ": revealed in sight, it waits for the next sighting");
		}
		isTrue(figure.isLurkerThere(), "revealed at the end of the sighting");
		while (!figure.isLurkerInSight())
			step("none", 1);
		while (figure.isLurkerInSight())
			step("none", 1);
		isFalse(figure.isLurkerThere(), "seen, it is gone at the next sighting");
		reader.reveal("hooded");
		isTrue(figure.isLurkerThere(), "revealed out of sight: at once");
		while (!figure.isLurkerInSight())
			step("none", 1);
		isTrue(figure.isLurkerThere(), "and there at the next sighting");
	}
}
