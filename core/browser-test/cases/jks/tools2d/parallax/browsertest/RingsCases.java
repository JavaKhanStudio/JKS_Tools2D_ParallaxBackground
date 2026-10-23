package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.Arrays;
import java.util.List;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxRings;
import jks.tools2d.parallax.ParallaxRings.Band;
import jks.tools2d.parallax.ParallaxRings.Gradient;
import jks.tools2d.parallax.ParallaxRings.Options;

/** A page round a 3D world, as rings (parallax-readers r47, {@link ParallaxRings}, docs/page-as-3d-sky.md). */
final class RingsCases extends ReaderFixtures
{
	private static final float DEG = (float) Math.PI / 180;

	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("rings: aRingStandsAtTheLibrarysDepth", () -> new RingsCases().aRingStandsAtTheLibrarysDepth()),
				new BrowserCase("rings: farCompressesTheDepthsAsTheSagasSkyDoes", () -> new RingsCases().farCompressesTheDepthsAsTheSagasSkyDoes()),
				new BrowserCase("rings: aStillLayerIsInfinitelyFarAndDrawnFirst", () -> new RingsCases().aStillLayerIsInfinitelyFarAndDrawnFirst()),
				new BrowserCase("rings: aBandSpansTheElevationsItsLayerHasOnThePage", () -> new RingsCases().aBandSpansTheElevationsItsLayerHasOnThePage()),
				new BrowserCase("rings: theImageKeepsItsAspectRoundTheRing", () -> new RingsCases().theImageKeepsItsAspectRoundTheRing()),
				new BrowserCase("rings: theFogIsThePagesFog", () -> new RingsCases().theFogIsThePagesFog()),
				new BrowserCase("rings: aLayerThatDrawsNoImageHasNoRing", () -> new RingsCases().aLayerThatDrawsNoImageHasNoRing()),
				new BrowserCase("rings: theGradientsCoverWhatTheyCoverIn2D", () -> new RingsCases().theGradientsCoverWhatTheyCoverIn2D()),
				new BrowserCase("rings: noBandReachesTheZenith", () -> new RingsCases().noBandReachesTheZenith()));
	}

	private static Options options(float front, float far)
	{
		Options o = new Options();
		o.front = front;
		o.far = far;
		return o;
	}

	/** The forest's speeds (DepthCases): the front at 1, the back ten times as far, so ten times the front's radius. */
	void aRingStandsAtTheLibrarysDepth()
	{
		List<ParallaxLayer> page = list(atSpeed(0.008f), atSpeed(0.02f), atSpeed(0.04f), atSpeed(0.08f));
		List<Band> bands = ParallaxRings.bands(page, 40, options(100, 0));
		float[] radius = { 1000, 400, 200, 100 };
		for (int i = 0; i < 4; i++)
		{
			equal(radius[i], bands.get(i).radius, 1e-2f, "layer " + i);
			equal(i, bands.get(i).order, "far first: layer " + i);
		}
	}

	/** calm-day's speeds (round1/s10, the saga's sky): sky.js puts them from 330 to 760 by r = R0 (smax / speed)^p. */
	void farCompressesTheDepthsAsTheSagasSkyDoes()
	{
		float[] speeds = { 0.011000001f, 0.013348389f, 0.017797852f, 0.02373047f, 0.031640626f, 0.0421875f, 0.056250002f, 0.075f, 0.1f };
		ParallaxLayer[] layers = new ParallaxLayer[speeds.length];
		for (int i = 0; i < speeds.length; i++)
			layers[i] = atSpeed(speeds[i]);
		List<Band> bands = ParallaxRings.bands(list(layers), 40, options(330, 760));
		double p = Math.log(760.0 / 330) / Math.log(0.1 / 0.011000001);
		for (int i = 0; i < speeds.length; i++)
			equal((float) (330 * Math.pow(0.1 / speeds[i], p)), bands.get(i).radius, 1e-2f, "sky.js's radius of layer " + i);
		equal(760, bands.get(0).radius, 1e-2f, "the slowest at far");
		equal(330, bands.get(speeds.length - 1).radius, 1e-3f, "the front at front");
	}

	void aStillLayerIsInfinitelyFarAndDrawnFirst()
	{
		List<ParallaxLayer> page = list(atSpeed(0.04f), atSpeed(0), atSpeed(0.08f), atSpeed(0));
		for (float far : new float[] { 0, 500 })
		{
			List<Band> bands = ParallaxRings.bands(page, 40, options(100, far));
			isTrue(bands.get(1).radius == Float.POSITIVE_INFINITY, "a still layer: infinitely far, far " + far);
			equal(0, bands.get(1).order, "the first still layer first");
			equal(1, bands.get(3).order, "then the second, in page order");
			equal(2, bands.get(0).order, "then the slowest that moves");
			equal(far > 0 ? 500 : 200, bands.get(0).radius, 1e-3f, "the slowest that moves, far " + far);
			equal(0, bands.get(1).height, 0, "an infinite ring has no height in the world");
		}
	}

	/**
	 * A 16:9 image at sizeRatio 1 is the page's height, 22.5 of a 40 wide world: 0 to 45 degrees. Lowered by 2 and
	 * squeezed to 0.7 (the saga's lift and scale), it spans -2.8 to 28.7 degrees, at 2 degrees a page unit.
	 */
	void aBandSpansTheElevationsItsLayerHasOnThePage()
	{
		ParallaxLayer whole = atSpeed(0.08f);
		Band plain = ParallaxRings.bands(list(whole), 40, options(100, 0)).get(0);
		equal(0, plain.bottom, 1e-6f, "its bottom on the horizon");
		equal(45 * DEG, plain.top, 1e-5f, "its top 45 degrees up");
		equal(0, plain.y, 1e-4f, "its bottom at the foot");
		equal(100, plain.height, 1e-3f, "tan 45 times the radius");

		ParallaxLayer raised = atSpeed(0.08f);
		raised.setDecalPercentY(20);
		Options saga = options(100, 0);
		saga.lift = -2;
		saga.scale = 0.7f;
		saga.foot = 50;
		Band band = ParallaxRings.bands(list(raised), 40, saga).get(0);
		equal((4.5f - 2) * 2 * 0.7f * DEG, band.bottom, 1e-5f, "decal 20 % is 4.5 page units up");
		equal((4.5f + 22.5f - 2) * 2 * 0.7f * DEG, band.top, 1e-5f, "a page's height above");
		equal(50 + 100 * (float) Math.tan(band.bottom), band.y, 1e-3f, "its bottom seen from the foot");
		equal(100 * (float) (Math.tan(band.top) - Math.tan(band.bottom)), band.height, 1e-3f, "its height");
	}

	/**
	 * At radius 100 the whole-page band is 100 high, so a 40 x 22.5 tile is 177.8 round: 3.53 tiles, 4. A pad of 10
	 * makes the tile 50 wide, the image 0.8 of it: 2.83 tiles, 3.
	 */
	void theImageKeepsItsAspectRoundTheRing()
	{
		ParallaxLayer layer = atSpeed(0.08f);
		Band band = ParallaxRings.bands(list(layer), 40, options(100, 0)).get(0);
		equal(4, band.repeat, "2 pi 100 / (40 x 100 / 22.5)");
		equal(1, band.imageShare, 0, "no pad: the image fills its tile");
		layer.setPadX(10);
		band = ParallaxRings.bands(list(layer), 40, options(100, 0)).get(0);
		equal(3, band.repeat, "2 pi 100 / (50 x 100 / 22.5)");
		equal(0.8f, band.imageShare, 1e-6f, "the image, then its pad");
	}

	void theFogIsThePagesFog()
	{
		Options o = options(100, 0);
		o.fogStrength = 0.05f;
		List<Band> bands = ParallaxRings.bands(list(atSpeed(0.01f), atSpeed(0.04f), atSpeed(0.08f)), 40, o);
		equal(fog(0.05f, 0.01f, 0.08f), bands.get(0).fog, 1e-6f, "the back one");
		equal(fog(0.05f, 0.04f, 0.08f), bands.get(1).fog, 1e-6f, "the middle one");
		equal(0, bands.get(2).fog, 0, "the front one is clear");
		equal(0, ParallaxRings.bands(list(atSpeed(0.01f), atSpeed(0.08f)), 40, options(100, 0)).get(0).fog, 0, "no fog");
	}

	void aLayerThatDrawsNoImageHasNoRing()
	{
		List<Band> bands = ParallaxRings.bands(list(atSpeed(0.04f), ParallaxLayer.empty("birds", 1), atSpeed(0.08f)), 40, options(100, 0));
		equal(2, bands.size(), "the EMPTY layer is skipped");
		equal(0, bands.get(0).layer, "the first ring is layer 0");
		equal(2, bands.get(1).layer, "the second is layer 2");
	}

	/** calm-day's halves (0.5 each) meet at 22.5 degrees; 0.3 and 0.6 overlap from 13.5 to 18. */
	void theGradientsCoverWhatTheyCoverIn2D()
	{
		Gradient half = ParallaxRings.gradient(0.5f, 0.5f, 40, options(100, 0));
		equal(22.5f * DEG, half.topFrom, 1e-5f, "the top half from the middle");
		equal(45 * DEG, half.topTo, 1e-5f, "to the page's top");
		equal(0, half.bottomFrom, 1e-6f, "the bottom half from the horizon");
		equal(22.5f * DEG, half.bottomTo, 1e-5f, "to the middle");
		Gradient overlap = ParallaxRings.gradient(0.3f, 0.6f, 40, options(100, 0));
		equal(13.5f * DEG, overlap.topFrom, 1e-5f, "the top one leaves 30 % uncovered");
		equal(18 * DEG, overlap.bottomTo, 1e-5f, "the bottom one 60 %");
	}

	void noBandReachesTheZenith()
	{
		Options o = options(100, 0);
		o.pageDegrees = 120;
		Band band = ParallaxRings.bands(list(atSpeed(0.08f)), 40, o).get(0);
		equal(ParallaxRings.MAX_ELEVATION, band.top, 0, "kept under 90 degrees");
		isTrue(band.height > 0 && band.height < Float.POSITIVE_INFINITY, "a finite band");
	}
}
