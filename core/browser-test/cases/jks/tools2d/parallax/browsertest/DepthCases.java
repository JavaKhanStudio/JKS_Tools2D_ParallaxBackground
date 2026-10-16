package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jks.tools2d.parallax.ParallaxDepth;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;

/**
 * A layer's distance from its speed (r444, {@link ParallaxDepth}), and the reader drawing one layer alone for a 3D view
 * ({@link ParallaxPageReader#drawLayer(com.badlogic.gdx.graphics.OrthographicCamera, com.badlogic.gdx.graphics.g2d.Batch, int)}).
 */
final class DepthCases extends ReaderFixtures
{
	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("depth: aLayersDistanceIsTheFrontsSpeedOverItsOwn", () -> new DepthCases().aLayersDistanceIsTheFrontsSpeedOverItsOwn()),
				new BrowserCase("depth: speedAtUndoesDistanceOf", () -> new DepthCases().speedAtUndoesDistanceOf()),
				new BrowserCase("depth: theDollyReadsTheSameDistance", () -> new DepthCases().theDollyReadsTheSameDistance()),
				new BrowserCase("depth: theCameraDistanceFollowsTheFieldOfView", () -> new DepthCases().theCameraDistanceFollowsTheFieldOfView()),
				new BrowserCase("depth: drawLayerDrawsOneLayersTilesAsDrawDoesInEveryRepeatMode", () -> new DepthCases().drawLayerDrawsOneLayersTilesAsDrawDoesInEveryRepeatMode()));
	}

	/** The forest's speeds (r365's AI pages): 0.008, 0.02, 0.04, 0.08, the front at 1 and the back ten times as far. */
	void aLayersDistanceIsTheFrontsSpeedOverItsOwn()
	{
		List<ParallaxLayer> page = list(atSpeed(0.008f), atSpeed(0.02f), atSpeed(0.04f), atSpeed(0.08f));
		float[] expected = { 10, 4, 2, 1 };
		for (int i = 0; i < 4; i++)
			equal(expected[i], ParallaxDepth.distanceOf(page, i), 1e-5f, "layer " + i);
		equal(2, ParallaxDepth.distanceOf(0.08f, atSpeed(-0.04f)), 1e-5f, "a speed's magnitude");
		isTrue(ParallaxDepth.distanceOf(0.08f, atSpeed(0)) == Float.POSITIVE_INFINITY, "a still layer is infinitely far");
		equal(1, ParallaxDepth.distanceOf(0, atSpeed(0)), 0, "a page that does not scroll is one picture, every layer at 1");
	}

	void speedAtUndoesDistanceOf()
	{
		equal(0.02f, ParallaxDepth.speedAt(0.08f, 4), 1e-7f, "four times as far: a quarter of the front's speed");
		equal(0.08f, ParallaxDepth.speedAt(0.08f, 1), 0, "at 1: the front's speed");
		equal(0, ParallaxDepth.speedAt(0.08f, Float.POSITIVE_INFINITY), 0, "infinitely far: still");
		ParallaxLayer layer = atSpeed(0.0123f);
		equal(0.0123f, ParallaxDepth.speedAt(0.05f, ParallaxDepth.distanceOf(0.05f, layer)), 1e-7f, "the way back");
	}

	/**
	 * The dolly's zoom, {@code 1 - k (1 - zoom)}, is a camera moving in by {@code 1 - zoom} of its distance to the front:
	 * k is one over the layer's distance.
	 */
	void theDollyReadsTheSameDistance()
	{
		float front = 0.08f;
		for (float speed : new float[] { 0.08f, 0.04f, 0.02f, 0.008f })
		{
			ParallaxLayer layer = atSpeed(speed);
			float d = ParallaxDepth.distanceOf(front, layer);
			equal(1 - (1 - 0.5f) / d, ParallaxPageReader.dollyZoom(0.5f, front, layer), 1e-6f, "the dolly at speed " + speed);
		}
	}

	void theCameraDistanceFollowsTheFieldOfView()
	{
		equal(20, ParallaxDepth.cameraDistance(40, 90), 1e-4f, "a 90 degree view: half its width away");
		equal(34.641f, ParallaxDepth.cameraDistance(40, 60), 1e-3f, "60 degrees");
		isTrue(ParallaxDepth.cameraDistance(40, 30) > ParallaxDepth.cameraDistance(40, 60), "narrower: farther");
	}

	/**
	 * Each layer drawn alone is the draws {@link ParallaxPageReader#draw} makes for it, in the same places, and nothing of
	 * the others: the 3D view shows each layer where the game does. Scrolled, and in every repeat mode.
	 */
	void drawLayerDrawsOneLayersTilesAsDrawDoesInEveryRepeatMode()
	{
		for (int mode = 0; mode < 4; mode++)
		{
			boolean onX = mode == 0 || mode == 2, onY = mode == 1 || mode == 2;
			String name = onX && onY ? "XY" : onX ? "X" : onY ? "Y" : "none";
			ParallaxPageReader reader = reader(onX, onY);
			reader.setWorldSize(40, 22.5f);
			float[] sizes = { 0.25f, 0.3f, 0.45f };
			float[] speeds = { 0.01f, 0.02f, 0.04f };
			List<ParallaxLayer> page = new ArrayList<>();
			for (int i = 0; i < 3; i++)
			{
				ParallaxLayer layer = atSpeed(speeds[i]);
				layer.setSizeRatio(sizes[i]);
				layer.setDecalPercentY(10 * i);
				page.add(layer);
			}
			reader.addLayers(page);
			reader.act(1, onX ? 333 : 0, onY ? 77 : 0);
			draws.clear();
			reader.draw(camera, batch);
			List<float[]> all = new ArrayList<>(draws);
			int alone = 0;
			for (int i = 0; i < 3; i++)
			{
				float width = 40 * sizes[i];
				draws.clear();
				reader.drawLayer(camera, batch, i);
				isTrue(!draws.isEmpty(), name + ": layer " + i + " drawn alone");
				List<float[]> expected = new ArrayList<>();
				for (float[] draw : all)
					if (Math.abs(draw[2] - width) < 1e-3f)
						expected.add(draw);
				equal(expected.size(), draws.size(), name + ": layer " + i + "'s draws alone");
				for (int k = 0; k < draws.size(); k++)
					for (int c = 0; c < 4; c++)
						equal(expected.get(k)[c], draws.get(k)[c], 1e-4f, name + ": layer " + i + " draw " + k + " value " + c);
				alone += draws.size();
			}
			equal(all.size(), alone, name + ": the layers alone make the page's draws");
		}
	}
}
