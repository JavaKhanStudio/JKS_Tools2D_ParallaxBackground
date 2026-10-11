package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;

import jks.tools2d.parallax.LayerEffects;
import jks.tools2d.parallax.LayerHook;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;

/**
 * ParallaxPageReader's cases on the dolly (r278, {@link ParallaxPageReader#setDolly}): the camera's zoom scales each
 * layer by its depth about the view's middle, tiled across its own view. Shared builders in {@link ReaderFixtures}.
 */
final class DollyCases extends ReaderFixtures
{
	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("reader: dollyZoomFollowsTheLayersDepth", () -> new DollyCases().dollyZoomFollowsTheLayersDepth()),
				new BrowserCase("reader: dollyScalesEachLayerByItsDepthAboutTheViewsMiddle", () -> new DollyCases().dollyScalesEachLayerByItsDepthAboutTheViewsMiddle()),
				new BrowserCase("reader: dollyTilesJustEnoughToCoverTheViewInEveryRepeatMode", () -> new DollyCases().dollyTilesJustEnoughToCoverTheViewInEveryRepeatMode()),
				new BrowserCase("reader: dollyAtZoomOneOrOffDrawsAsBefore", () -> new DollyCases().dollyAtZoomOneOrOffDrawsAsBefore()),
				new BrowserCase("reader: dollyScalesWhatAHookDraws", () -> new DollyCases().dollyScalesWhatAHookDraws()),
				new BrowserCase("reader: dollyKeepsTheEffectsOnTheGamesBatchInTheLayersView", () -> new DollyCases().dollyKeepsTheEffectsOnTheGamesBatchInTheLayersView()),
				new BrowserCase("reader: eachPageOfACrossFadeIsDolliedByItsOwnFront", () -> new DollyCases().eachPageOfACrossFadeIsDolliedByItsOwnFront()));
	}

	/** A layer 12 x 6.75 world units in a 40-wide world, scrolling at speed ratio X {@code speed}. */
	private static ParallaxLayer at(float speed)
	{
		ParallaxLayer layer = layer(0);
		layer.setSizeRatio(0.3f);
		layer.setParallaxSpeedRatioX(speed);
		return layer;
	}

	/** The draws {@code width} wide, the width telling the layers apart. */
	private List<float[]> wide(float width)
	{
		List<float[]> wide = new ArrayList<>();
		for (float[] draw : draws)
			if (Math.abs(draw[2] - width) < 1e-3f)
				wide.add(draw);
		return wide;
	}

	/** The view of the 40 x 22.5 camera at (20, 11.25) and zoom 0.5: x 10 to 30, y 5.625 to 16.875. */
	private ParallaxPageReader zoomed(boolean onX, boolean onY)
	{
		ParallaxPageReader reader = reader(onX, onY);
		reader.setWorldSize(40, 22.5f);
		camera.zoom = 0.5f;
		reader.setDolly(true);
		return reader;
	}

	/** 1 - k (1 - zoom), k the layer's speed over the front's: a camera moving in, each layer as far as its speed says. */
	void dollyZoomFollowsTheLayersDepth()
	{
		equal(0.5f, ParallaxPageReader.dollyZoom(0.5f, 0.02f, at(0.02f)), 1e-6f, "the front layer: the camera's zoom");
		equal(1, ParallaxPageReader.dollyZoom(0.5f, 0.02f, at(0)), 1e-6f, "a still layer: not zoomed");
		equal(0.75f, ParallaxPageReader.dollyZoom(0.5f, 0.02f, at(0.01f)), 1e-6f, "half the front's speed: half the way");
		equal(0.5f, ParallaxPageReader.dollyZoom(0.5f, 0.02f, at(-0.02f)), 1e-6f, "a speed's magnitude");
		equal(1.5f, ParallaxPageReader.dollyZoom(2, 0.02f, at(0.01f)), 1e-6f, "zoomed out the same way");
		equal(0.5f, ParallaxPageReader.dollyZoom(0.5f, 0, at(0)), 1e-6f, "a page that does not scroll: as one picture");
	}

	/**
	 * At zoom 0.5, on a page tiling on X: the sky (still) is drawn at zoom 1, the screen showing it as at zoom 1; the
	 * middle layer (half the front's speed) at 0.75; the front at 0.5, the camera's. Each layer's own view is its zoom
	 * about the camera's middle, its tiles placed from the corner of the view at zoom 1, and scaled into the camera's
	 * view by the camera's zoom over its own.
	 */
	void dollyScalesEachLayerByItsDepthAboutTheViewsMiddle()
	{
		ParallaxPageReader reader = zoomed(true, false);
		reader.addLayers(list(at(0), at(0.01f), at(0.02f)));
		reader.draw(camera, batch);

		// The sky's view is the world, x 0 to 40: tiles at 0, 12, 24 and 36, scaled by 0.5 about x 20.
		List<float[]> sky = wide(6);
		equal(4, sky.size(), "the sky's tiles");
		for (int i = 0; i < 4; i++)
		{
			equal(10 + 6 * i, sky.get(i)[0], 1e-4f, "sky tile " + i);
			equal(5.625f, sky.get(i)[1], 1e-4f, "the sky stays at the view's bottom");
			equal(3.375f, sky.get(i)[3], 1e-4f, "half as high in the view half as high");
		}
		// The middle layer's view is x 5 to 35: tiles at 0, 12 and 24, scaled by 2/3.
		List<float[]> middle = wide(8);
		equal(3, middle.size(), "the middle layer's tiles");
		for (int i = 0; i < 3; i++)
			equal(20 - 40 / 3f + 8 * i, middle.get(i)[0], 1e-4f, "middle tile " + i);
		equal(3.75f, middle.get(0)[1], 1e-4f, "below the view's bottom by a third of its way");
		// The front layer, at the camera's zoom: as the page zoomed as one picture about the middle.
		List<float[]> front = wide(12);
		equal(3, front.size(), "the front's tiles");
		for (int i = 0; i < 3; i++)
			equal(12 * i, front.get(i)[0], 1e-4f, "front tile " + i);
		equal(0, front.get(0)[1], 1e-4f, "the front stays at the world's bottom");
		equal(10, draws.size(), "nothing else drawn");
	}

	/** The same three layers, in every repeat mode, scrolled: no tile out of the camera's view, none missing. */
	void dollyTilesJustEnoughToCoverTheViewInEveryRepeatMode()
	{
		for (int mode = 0; mode < 4; mode++)
		{
			boolean onX = mode == 0 || mode == 2, onY = mode == 1 || mode == 2;
			String name = onX && onY ? "XY" : onX ? "X" : onY ? "Y" : "none";
			ParallaxPageReader reader = zoomed(onX, onY);
			reader.addLayers(list(at(0), at(0.01f), at(0.02f)));
			for (ParallaxLayer layer : reader.layers)
				layer.setDecalPercentY(30);
			// Scrolled along the axes it tiles on: on the others a layer scrolls out of the view.
			reader.act(1, onX ? 333 : 0, onY ? 77 : 0);
			draws.clear();
			reader.draw(camera, batch);
			float[] widths = { 6, 8, 12 };
			float[] heights = { 3.375f, 4.5f, 6.75f };
			for (int i = 0; i < 3; i++)
			{
				List<float[]> tiles = wide(widths[i]);
				isTrue(!tiles.isEmpty(), name + ": layer " + i + " drawn");
				float left = Float.MAX_VALUE, right = -Float.MAX_VALUE, bottom = Float.MAX_VALUE, top = -Float.MAX_VALUE;
				for (float[] tile : tiles)
				{
					equal(heights[i], tile[3], 1e-4f, name + ": layer " + i + "'s height");
					isTrue(tile[0] + tile[2] > 10 && tile[0] < 30 && tile[1] + tile[3] > 5.625f && tile[1] < 16.875f,
							name + ": layer " + i + "'s tile out of the view at " + tile[0] + ", " + tile[1]);
					left = Math.min(left, tile[0]);
					right = Math.max(right, tile[0] + tile[2]);
					bottom = Math.min(bottom, tile[1]);
					top = Math.max(top, tile[1] + tile[3]);
				}
				if (onX)
					isTrue(left <= 10 + 1e-4f && right >= 30 - 1e-4f, name + ": layer " + i + " covers the view across");
				else
					equal(1, countColumns(tiles), name + ": layer " + i + " drawn in one column");
				if (onY)
					isTrue(bottom <= 5.625f + 1e-4f && top >= 16.875f - 1e-4f, name + ": layer " + i + " covers the view up");
			}
		}
	}

	private static int countColumns(List<float[]> tiles)
	{
		List<Float> columns = new ArrayList<>();
		for (float[] tile : tiles)
		{
			boolean seen = false;
			for (Float x : columns)
				seen |= Math.abs(x - tile[0]) < 1e-3f;
			if (!seen)
				columns.add(tile[0]);
		}
		return columns.size();
	}

	/** At zoom 1 the dolly changes nothing; off, a zoom scales the whole page about the view's corner, as before r278. */
	void dollyAtZoomOneOrOffDrawsAsBefore()
	{
		for (int mode = 0; mode < 4; mode++)
		{
			boolean onX = mode == 0 || mode == 2, onY = mode == 1 || mode == 2;
			ParallaxPageReader plain = reader(onX, onY);
			plain.setWorldSize(40, 22.5f);
			plain.addLayers(list(at(0), at(0.01f), at(0.02f)));
			plain.act(1, 333, 77);
			draws.clear();
			plain.draw(camera, batch);
			List<float[]> before = new ArrayList<>(draws);

			ParallaxPageReader dollied = reader(onX, onY);
			dollied.setWorldSize(40, 22.5f);
			dollied.setDolly(true);
			dollied.addLayers(list(at(0), at(0.01f), at(0.02f)));
			dollied.act(1, 333, 77);
			draws.clear();
			dollied.draw(camera, batch);
			equal(before.size(), draws.size(), "mode " + mode + ": as many draws at zoom 1");
			for (int i = 0; i < draws.size(); i++)
				for (int c = 0; c < 4; c++)
					equal(before.get(i)[c], draws.get(i)[c], 1e-4f, "mode " + mode + ": draw " + i + " at zoom 1");
		}

		ParallaxPageReader off = reader(true, false);
		off.setWorldSize(40, 22.5f);
		camera.zoom = 0.5f;
		off.addLayers(list(at(0), at(0.01f), at(0.02f)));
		draws.clear();
		off.draw(camera, batch);
		equal(draws.size(), wide(12).size(), "off: every layer 12 wide, the page zoomed as one picture");
		equal(10, draws.get(0)[0], 1e-4f, "placed from the view's corner");
	}

	/** An EMPTY layer's hook draws through the dolly: its tiles and what it draws in them scaled as an image's. */
	void dollyScalesWhatAHookDraws()
	{
		ParallaxPageReader reader = zoomed(false, false);
		ParallaxLayer slot = ParallaxLayer.empty("slot", 0.3f);
		slot.setParallaxSpeedRatioX(0.01f);
		reader.addLayers(list(at(0.02f), slot));
		final List<Float> handed = new ArrayList<>();
		reader.setLayerHook("slot", new LayerHook()
		{
			@Override
			public void draw(Batch hookBatch, ParallaxLayer layer, float x, float y, float width, float height)
			{
				handed.add(width);
				hookBatch.draw(region(1, 1), x, y, width, height);
			}
		});
		reader.draw(camera, batch);
		equal(12, handed.get(0), 1e-4f, "the hook is handed the tile in the layer's own view");
		List<float[]> drawn = wide(8);
		equal(1, drawn.size(), "and what it draws is scaled by 2/3 into the camera's");
		equal(20 - 40 / 3f, drawn.get(0)[0], 1e-4f, "about the view's middle");
	}

	/** Records the batch each layer was begun with, and the view the effects were laid on then: width, left, floor. */
	private static final class ViewEffects implements LayerEffects
	{
		final List<Batch> batches = new ArrayList<>();
		final List<float[]> views = new ArrayList<>();
		float viewWidth, left, floor;

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{
			batches.add(batch);
			views.add(new float[] { viewWidth, left, floor });
			return true;
		}

		@Override
		public void end(Batch batch, ParallaxLayer layer)
		{}

		@Override
		public void setView(float width, float height, float left, float floor)
		{
			viewWidth = width;
			this.left = left;
			this.floor = floor;
		}
	}

	/**
	 * A SHADER layer's effect is begun on the game's batch (jME's reads its own from it), with the effects laid on the
	 * layer's own view (FOG's noise runs across that), the layers' corner where the view at zoom 1 has it, and the
	 * camera's view is theirs again after it.
	 */
	void dollyKeepsTheEffectsOnTheGamesBatchInTheLayersView()
	{
		ParallaxPageReader reader = zoomed(true, false);
		ViewEffects effects = new ViewEffects();
		reader.setLayerEffects(effects);
		ParallaxLayer wave = shaded(0.5f, 3, 1);
		wave.setParallaxSpeedRatioX(0.01f);
		reader.addLayers(list(wave, at(0.02f)));
		reader.draw(camera, batch);
		equal(1, effects.batches.size(), "begun once");
		isTrue(effects.batches.get(0) == batch, "on the game's batch");
		equal(30, effects.views.get(0)[0], 1e-4f, "laid on the layer's view, 40 x 0.75");
		equal(-5, effects.views.get(0)[1], 1e-4f, "the layers' x 0, 5 left of that view, x 5 to 35");
		equal(-2.8125f, effects.views.get(0)[2], 1e-4f, "their y 0, below its bottom by 22.5 x 0.125");
		equal(20, effects.viewWidth, 1e-4f, "the camera's view after it");
		equal(-10, effects.left, 1e-4f, "where the zoom 1 view puts them in it");
		isTrue(!wide(8).isEmpty(), "the layer drawn scaled");
	}

	/** During a cross-fade each page's layers take their depth from their own page's front. */
	void eachPageOfACrossFadeIsDolliedByItsOwnFront()
	{
		ParallaxPageReader reader = zoomed(false, false);
		reader.addLayers(list(at(0.01f), at(0.02f)));
		ParallaxLayer alone = at(0.01f);
		reader.addLayersTransfert(page(list(alone)), 2);
		reader.act(1, 0, 0);
		draws.clear();
		reader.draw(camera, batch);
		equal(1, wide(8).size(), "the outgoing page's back layer: half its front's speed, zoom 0.75");
		equal(2, wide(12).size(), "its front, and the incoming page's only layer, its own front: the camera's zoom");
		isTrue(reader.isInTransfer(), "mid-fade");
		equal(Color.WHITE.r, batch.getColor().r, 0, "the batch white after the draw");
	}
}
