package jks.tools2d.parallax;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.GdxNativesLoader;

import jks.tools2d.parallax.pages.Enum_LayerKind;

/**
 * LurkerCover on a hand-made atlas (docs/lurker-layers.md, r369): a bar 100 texels wide and 20 high, opaque but for
 * a gap at columns 40 to 49, drawn 100 x 20 world units, so a texel is a unit; in front of a figure 4 x 10 texels at
 * (1, 1) in a transparent region of 6 x 12, drawn 6 x 12. Grown by 4% of its height (0.4), the figure's box is
 * x 0.6..5.4, y 0.6..11.4 from its tile corner.
 */
class LurkerCoverTest
{
	private static final int PAGE_WIDTH = 128, PAGE_HEIGHT = 64;
	/** Where the figure's region starts in the page. */
	private static final int FIGURE_Y = 30;

	private final Texture page = TestGdx.unbackedTexture(PAGE_WIDTH, PAGE_HEIGHT);
	private final int[][] alpha = new int[PAGE_HEIGHT][PAGE_WIDTH];
	private final LurkerCover.Alpha pixels = (texture, x, y) -> texture == page ? alpha[y][x] : -1;

	LurkerCoverTest()
	{
		for (int y = 0; y < 20; y++)
			for (int x = 0; x < 100; x++)
				alpha[y][x] = x >= 40 && x < 50 ? 0 : 255;
		for (int y = 1; y <= 10; y++)
			for (int x = 1; x <= 4; x++)
				alpha[FIGURE_Y + y][x] = 255;
	}

	private ParallaxLayer bar(float padX)
	{
		ParallaxLayer bar = new ParallaxLayer(List.of(new TextureRegion(page, 0, 0, 100, 20)), true, 100, 0.04f, 0.04f, 1);
		bar.setWorldSize(100, 100);
		bar.setPadX(padX);
		return bar;
	}

	/** The figure, its tile's corner 4 units over the bar's (a band of 4.6..15.4 on the bar), or as high as asked. */
	private ParallaxLayer figure(float decalY)
	{
		ParallaxLayer figure = new ParallaxLayer(Enum_LayerKind.LURKER, List.of(new TextureRegion(page, 0, FIGURE_Y, 6, 12)), true, 100,
				0.03f, 0.03f, 0.06f);
		figure.setWorldSize(100, 100);
		figure.setDecalPercentY(decalY);
		return figure;
	}

	private float[] cover(ParallaxLayer... layers)
	{return LurkerCover.cover(List.of(layers), 0, true, false, pixels);}

	/**
	 * The bar's opaque stretches are 0..40 and 50..100, and 50..100 meets the next tile's 0..40: 50..140. The box's left
	 * edge must be at 50 or more, its right at 140 or less: d from 50 - 0.6 to 140 - 5.4.
	 */
	@Test
	void aBarWithAGapCoversTheFigureEverywhereElse()
	{assertArrayEquals(new float[] { 1, 49.4f, 134.6f }, cover(figure(4), bar(0)), 1e-4f);}

	/** Padded, the bar's tiles no longer meet: two stretches a period of 110, the first one's from carried into it. */
	@Test
	void aPaddedBarCoversTwoStretchesAPeriod()
	{assertArrayEquals(new float[] { 1, 109.4f, 144.6f, 1, 49.4f, 94.6f }, cover(figure(4), bar(10)), 1e-4f);}

	/** One column at 249 is a gap: the figure is not hidden by what it shows through. */
	@Test
	void alphaUnder250IsAGap()
	{
		alpha[10][70] = 249;
		assertArrayEquals(new float[] { 1, 49.4f, 64.6f, 1, 70.4f, 134.6f }, cover(figure(4), bar(0)), 1e-4f);
	}

	/** Flipped, the gap is at 50..60. */
	@Test
	void aFlippedBarHasItsGapMirrored()
	{
		ParallaxLayer bar = bar(0);
		bar.setFlipX(true);
		assertArrayEquals(new float[] { 1, 59.4f, 144.6f }, cover(figure(4), bar), 1e-4f);
	}

	/** Its band reaching over the bar's top (15.6..26.4), nothing covers it; the bar's mirrored copy above does. */
	@Test
	void aFigurePastTheBarIsCoveredOnlyByItsMirroredCopy()
	{
		assertArrayEquals(new float[0], cover(figure(15), bar(0)));
		ParallaxLayer mirrored = bar(0);
		mirrored.setMirror(true);
		assertArrayEquals(new float[] { 1, 49.4f, 134.6f }, cover(figure(15), mirrored), 1e-4f);
	}

	/** Counted from the lurker: the bar is the second layer in front; one behind it is not looked at. */
	@Test
	void theLayerInFrontIsCountedFromTheLurker()
	{
		ParallaxLayer clear = new ParallaxLayer(List.of(new TextureRegion(page, 100, 0, 20, 20)), true, 100, 0.035f, 0.035f, 1);
		clear.setWorldSize(100, 100);
		float[] cover = LurkerCover.cover(List.of(bar(0), figure(4), clear, bar(0)), 1, true, false, pixels);
		assertArrayEquals(new float[] { 2, 49.4f, 134.6f }, cover, 1e-4f);
	}

	/** Without a gap the bar covers every offset; on a page not repeating on X, nothing is worked out. */
	@Test
	void aBarWithoutAGapCoversEveryOffsetAndANonRepeatingPageNone()
	{
		for (int y = 0; y < 20; y++)
			for (int x = 40; x < 50; x++)
				alpha[y][x] = 255;
		assertArrayEquals(new float[] { 1, 0, 100 }, cover(figure(4), bar(0)), 1e-4f);
		assertArrayEquals(new float[0], LurkerCover.cover(List.of(figure(4), bar(0)), 0, false, false, pixels));
	}

	/** Read from Pixmaps, the same; a page with no Pixmap leaves the cover empty. */
	@Test
	void pixmapsGiveTheSameAndAMissingOneNone()
	{
		GdxNativesLoader.load();
		Pixmap pixmap = new Pixmap(PAGE_WIDTH, PAGE_HEIGHT, Pixmap.Format.RGBA8888);
		pixmap.setBlending(Pixmap.Blending.None);
		for (int y = 0; y < PAGE_HEIGHT; y++)
			for (int x = 0; x < PAGE_WIDTH; x++)
				pixmap.drawPixel(x, y, 0xffffff00 | alpha[y][x]);
		Map<Texture, Pixmap> pixmaps = new HashMap<>();
		pixmaps.put(page, pixmap);
		assertArrayEquals(new float[] { 1, 49.4f, 134.6f },
				LurkerCover.cover(List.of(figure(4), bar(0)), 0, true, false, LurkerCover.of(pixmaps)), 1e-4f);
		assertEquals(0, LurkerCover.cover(List.of(figure(4), bar(0)), 0, true, false, LurkerCover.of(new HashMap<>())).length);
		pixmap.dispose();
	}
}
