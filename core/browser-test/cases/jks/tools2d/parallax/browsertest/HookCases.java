package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isTrue;

import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.LayerHook;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;

/**
 * ParallaxPageReader's cases on EMPTY layers: drawn by the game's LayerHook, per tile, in their place. Shared builders
 * in {@link ReaderFixtures}.
 */
final class HookCases extends ReaderFixtures
{
	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("reader: emptyLayerIsDrawnByItsHookInEveryRepeatMode", () -> new HookCases().emptyLayerIsDrawnByItsHookInEveryRepeatMode()),
				new BrowserCase("reader: emptyLayerWithoutAHookDrawsNothing", () -> new HookCases().emptyLayerWithoutAHookDrawsNothing()),
				new BrowserCase("reader: hookColorStaysInTheHookAndFadesWithThePage", () -> new HookCases().hookColorStaysInTheHookAndFadesWithThePage()));
	}

	/** A hook drawing one quad the size of the tile it is handed: it shows in the draws as a 12-wide quad. */
	private static final LayerHook TILE_HOOK = new LayerHook()
	{
		@Override
		public void draw(Batch batch, ParallaxLayer layer, float x, float y, float width, float height)
		{batch.draw((TextureRegion) null, x, y, width, height);}
	};

	/**
	 * An EMPTY layer between two image layers is drawn by its hook, in its place, once per tile the view shows: a box the
	 * world's size times sizeRatio, tiled on the axes the page repeats on, drawn once on the others.
	 */
	void emptyLayerIsDrawnByItsHookInEveryRepeatMode()
	{
		for (int mode = 0; mode < 4; mode++)
		{
			boolean onX = mode == 0 || mode == 2, onY = mode == 1 || mode == 2;
			String name = onX && onY ? "XY" : onX ? "X" : onY ? "Y" : "none";
			draws.clear();
			ParallaxPageReader reader = reader(onX, onY);
			reader.setWorldSize(40, 22.5f); // the world the camera shows
			ParallaxLayer slot = ParallaxLayer.empty("slot", 0.3f); // 12 x 6.75 world units
			slot.setPadX(1);
			slot.setPadY(1);
			reader.addLayers(list(layer(0), slot, layer(0)));
			reader.setLayerHook("slot", TILE_HOOK);
			reader.act(1 / 60f, 500, 300);
			reader.draw(camera, batch);

			int first = -1, last = -1;
			float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE, minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
			for (int i = 0; i < draws.size(); i++)
			{
				float[] draw = draws.get(i);
				if (Math.abs(draw[2] - 12) > 1e-3f) // a browser's floats are doubles: 40 * 0.3 is not 12 there
					continue;
				if (first < 0)
					first = i;
				last = i;
				equal(6.75f, draw[3], 1e-4f, name + ": the tile is the world's height times sizeRatio");
				isTrue(draw[0] + draw[2] > 0 && draw[0] < 40 && draw[1] + draw[3] > 0 && draw[1] < 22.5f, name + ": off-screen tile at " + draw[0] + ", " + draw[1]);
				minX = Math.min(minX, draw[0]);
				maxX = Math.max(maxX, draw[0] + draw[2]);
				minY = Math.min(minY, draw[1]);
				maxY = Math.max(maxY, draw[1] + draw[3]);
			}
			isTrue(first > 0, name + ": the hook draws after the layer behind it");
			isTrue(last < draws.size() - 1, name + ": and before the layer in front of it");
			int hooked = last - first + 1;
			// Up to a padding short of the far edge: the gap after the last tile is padding.
			if (onX)
				isTrue(minX <= 0 && maxX + 1 >= 40, name + ": the tiles cover the view's width, " + minX + " to " + maxX);
			if (onY)
				isTrue(minY <= 0 && maxY + 1 >= 22.5f, name + ": the tiles cover the view's height, " + minY + " to " + maxY);
			int across = onX ? 4 : 1, down = onY ? 4 : 1; // 13 and 7.75 a step: 3 or 4 steps cover 40 and 22.5
			isTrue(hooked <= across * down, name + ": " + hooked + " tiles, at most " + across * down);
		}
	}

	void emptyLayerWithoutAHookDrawsNothing()
	{
		ParallaxPageReader reader = reader(true, true);
		ParallaxLayer unnamed = ParallaxLayer.empty(null, 0.3f);
		reader.addLayers(list(ParallaxLayer.empty("nobody", 0.3f), unnamed));
		reader.setLayerHook("slot", TILE_HOOK);
		reader.draw(camera, batch);
		equal(0, draws.size(), "draws");

		reader.setLayerHook("nobody", TILE_HOOK);
		reader.draw(camera, batch);
		isTrue(draws.size() > 0, "a hook registered after the page was set draws");
		reader.setLayerHook("nobody", null);
		draws.clear();
		reader.draw(camera, batch);
		equal(0, draws.size(), "a removed hook draws nothing");
	}

	/** The hook gets the batch at the page's opacity; a color it sets does not leak into the layers drawn after it. */
	void hookColorStaysInTheHookAndFadesWithThePage()
	{
		ParallaxPageReader reader = reader(false, false);
		reader.setLayerHook("slot", new LayerHook()
		{
			@Override
			public void draw(Batch batch, ParallaxLayer layer, float x, float y, float width, float height)
			{
				batch.draw((TextureRegion) null, x, y, width, height);
				batch.setColor(0.25f, 0, 0, 1);
			}
		});
		reader.addLayers(list(ParallaxLayer.empty("slot", 1), layer(0)));
		reader.addLayersTransfert(page(list(layer(0))), 1);
		reader.act(0.5f, 0, 0);
		reader.draw(camera, batch);

		equal(3, draws.size(), "the hook, then both pages' front layer");
		equal(0.5f, draws.get(0)[5], 1e-4f, "the hook is drawn at the outgoing page's opacity");
		equal(1f, draws.get(1)[4], 0, "the layer after the hook is not tinted by it");
		equal(0.5f, draws.get(1)[5], 1e-4f, "and keeps its page's opacity");
	}
}
