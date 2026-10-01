package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isFalse;
import static jks.tools2d.parallax.browsertest.Check.isTrue;
import static jks.tools2d.parallax.browsertest.Check.notSame;
import static jks.tools2d.parallax.browsertest.Check.same;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.ParticleEffect;
import com.badlogic.gdx.graphics.g2d.ParticleEmitter;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Array;

import jks.tools2d.parallax.LayerHook;
import jks.tools2d.parallax.ParallaxParticles;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.pages.Enum_ParticleAnchor;
import jks.tools2d.parallax.pages.WholePage_Model;

/**
 * ParallaxPageReader's tests, in code GWT translates: tiling, stripped regions, mirror and cross-fades. They record the
 * reader's draws on a {@link RecordingBatch}, without a window; core/test BrowserSuiteTest runs them on the JVM and
 * `tools/browser-test.sh` in Chrome. Cover all four repeat modes (X, Y, XY, none).
 */
final class ReaderCases
{
	private final RecordingBatch batch = new RecordingBatch();
	private final List<float[]> draws = batch.draws;
	private OrthographicCamera camera;

	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("reader: tilesJustEnoughToCoverTheView", () -> new ReaderCases().tilesJustEnoughToCoverTheView()),
				new BrowserCase("reader: strippedRegionKeepsItsOriginalWidthAndOffset", () -> new ReaderCases().strippedRegionKeepsItsOriginalWidthAndOffset()),
				new BrowserCase("reader: strippedRegionKeepsItsOriginalHeightWhenTilingOnY", () -> new ReaderCases().strippedRegionKeepsItsOriginalHeightWhenTilingOnY()),
				new BrowserCase("reader: strippedRegionIsStretchedWhenThePageSaysSo", () -> new ReaderCases().strippedRegionIsStretchedWhenThePageSaysSo()),
				new BrowserCase("reader: mirrorStacksAFlippedStripAcrossTheTiledOne", () -> new ReaderCases().mirrorStacksAFlippedStripAcrossTheTiledOne()),
				new BrowserCase("reader: negativePaddingLargerThanTheImageDoesNotHang", () -> new ReaderCases().negativePaddingLargerThanTheImageDoesNotHang()),
				new BrowserCase("reader: zeroSecondTransferSwapsImmediately", () -> new ReaderCases().zeroSecondTransferSwapsImmediately()),
				new BrowserCase("reader: transferKeepsTheNewLayoutAndTheScrolledDistance", () -> new ReaderCases().transferKeepsTheNewLayoutAndTheScrolledDistance()),
				new BrowserCase("reader: transferIntoThePageOnScreenUsesCopies", () -> new ReaderCases().transferIntoThePageOnScreenUsesCopies()),
				new BrowserCase("reader: layersAtAlphaZeroAreNotDrawn", () -> new ReaderCases().layersAtAlphaZeroAreNotDrawn()),
				new BrowserCase("reader: emptyLayerIsDrawnByItsHookInEveryRepeatMode", () -> new ReaderCases().emptyLayerIsDrawnByItsHookInEveryRepeatMode()),
				new BrowserCase("reader: emptyLayerWithoutAHookDrawsNothing", () -> new ReaderCases().emptyLayerWithoutAHookDrawsNothing()),
				new BrowserCase("reader: hookColorStaysInTheHookAndFadesWithThePage", () -> new ReaderCases().hookColorStaysInTheHookAndFadesWithThePage()),
				new BrowserCase("reader: particlesArePinnedToTheirLayerInEveryRepeatMode", () -> new ReaderCases().particlesArePinnedToTheirLayerInEveryRepeatMode()),
				new BrowserCase("reader: viewAnchoredParticlesAreDrawnOnceAndDriftWithTheLayer", () -> new ReaderCases().viewAnchoredParticlesAreDrawnOnceAndDriftWithTheLayer()),
				new BrowserCase("reader: particlesPastTheirBoxAreDrawnWhenTheBoxIsNot", () -> new ReaderCases().particlesPastTheirBoxAreDrawnWhenTheBoxIsNot()),
				new BrowserCase("reader: particlesFadeAndTintWithTheirPage", () -> new ReaderCases().particlesFadeAndTintWithTheirPage()),
				new BrowserCase("reader: aParticleLayerWithoutAnEffectDrawsNothing", () -> new ReaderCases().aParticleLayerWithoutAnEffectDrawsNothing()));
	}

	/** A region of the given pixel size, without any texture behind it. */
	private static TextureRegion region(final int width, final int height)
	{
		return new TextureRegion()
		{
			@Override
			public int getRegionWidth()
			{return width;}

			@Override
			public int getRegionHeight()
			{return height;}
		};
	}

	/** An atlas region packed with its whitespace stripped, on a 4096px page that has no GL texture behind it. */
	private static AtlasRegion strippedRegion(int width, int height, int originalWidth, int originalHeight, int offsetX, int offsetY)
	{
		Texture page = new Texture()
		{
			@Override
			public int getWidth()
			{return 4096;}

			@Override
			public int getHeight()
			{return 4096;}
		};
		AtlasRegion region = new AtlasRegion(page, 0, 0, width, height);
		region.originalWidth = originalWidth;
		region.originalHeight = originalHeight;
		region.offsetX = offsetX;
		region.offsetY = offsetY;
		return region;
	}

	private static ParallaxLayer layer(float decalY)
	{
		ParallaxLayer layer = new ParallaxLayer(region(1920, 1080), true, 40, 0.02f, 0.02f, 1);
		layer.setDecalPercentY(decalY);
		return layer;
	}

	private static WholePage_Model page(final List<ParallaxLayer> layers)
	{
		return new WholePage_Model()
		{
			@Override
			public List<ParallaxLayer> getDrawing(String relativePath, float worldWidth, float worldHeight)
			{return layers;}
		};
	}

	private static List<ParallaxLayer> list(ParallaxLayer... layers)
	{return new ArrayList<>(Arrays.asList(layers));}

	private ParallaxPageReader reader(boolean onX, boolean onY)
	{
		camera = new OrthographicCamera();
		camera.setToOrtho(false, 40, 22.5f);
		camera.update();
		ParallaxPageReader reader = new ParallaxPageReader();
		reader.setRepeatOnX(onX);
		reader.setRepeatOnY(onY);
		return reader;
	}

	void tilesJustEnoughToCoverTheView()
	{
		ParallaxPageReader reader = reader(true, false);
		ParallaxLayer layer = layer(0);
		layer.setSizeRatio(0.3f); // 12 world units wide, the view is 40
		reader.addLayers(list(layer));

		reader.act(1 / 60f, 500, 0);
		reader.draw(camera, batch);

		for (float[] draw : draws)
			isTrue(draw[0] + draw[2] > 0 && draw[0] < 40, "off-screen draw at x=" + draw[0]);
		isTrue(draws.size() >= 4 && draws.size() <= 5, draws.size() + " draws");
	}

	/** core/test-data/samples Hiver.atlas parallax4 #1: 3403x580 packed out of 4559x580, 913px stripped on its left. */
	void strippedRegionKeepsItsOriginalWidthAndOffset()
	{
		float unit = 40f / 4559;
		ParallaxPageReader reader = reader(false, false);
		ParallaxLayer layer = new ParallaxLayer(strippedRegion(3403, 580, 4559, 580, 913, 0), true, 40, 0.02f, 0.02f, 1);
		layer.setUseOriginalSize(true);
		reader.addLayers(list(layer));

		equal(580 * unit, layer.getHeight(), 1e-4f, "the layer is as high as the original image");
		reader.draw(camera, batch);
		equal(1, draws.size(), "draws");
		float[] draw = draws.get(0);
		float layerX = draw[0] - 913 * unit;
		equal(3403 * unit, draw[2], 1e-4f, "the packed image keeps its pixel scale");
		equal(580 * unit, draw[3], 1e-4f, "height");

		// Flipped, the stripped margin moves to the right: the image ends 913px before the layer does.
		layer.setFlipX(true);
		draws.clear();
		reader.draw(camera, batch);
		draw = draws.get(0);
		equal(-3403 * unit, draw[2], 1e-4f, "width");
		equal(layerX + (4559 - 913) * unit, draw[0], 1e-4f, "a negative width draws leftwards from x");
	}

	/** core/test-data/samples Printemps.atlas parallax1 #4: 3645x335 packed out of 3645x580, 142px stripped below it. */
	void strippedRegionKeepsItsOriginalHeightWhenTilingOnY()
	{
		float unit = 40f / 3645;
		ParallaxPageReader reader = reader(false, true);
		ParallaxLayer layer = new ParallaxLayer(strippedRegion(3645, 335, 3645, 580, 0, 142), true, 40, 0.02f, 0.02f, 1);
		layer.setUseOriginalSize(true);
		reader.addLayers(list(layer));

		equal(580 * unit, layer.getTotalHeight(), 1e-4f, "the Y tiling step is the original height");
		reader.draw(camera, batch);
		isTrue(draws.size() >= 2, draws.size() + " draws");
		for (float[] draw : draws)
		{
			equal(40, draw[2], 1e-4f, "width");
			equal(335 * unit, draw[3], 1e-4f, "height");
		}
		for (int i = 1; i < draws.size(); i++)
			equal(580 * unit, draws.get(i)[1] - draws.get(i - 1)[1], 1e-3f, "tiles are one original height apart");
	}

	/** Pages saved before .plax format 4 were designed on the packed image stretched over the whole layer. */
	void strippedRegionIsStretchedWhenThePageSaysSo()
	{
		ParallaxPageReader reader = reader(false, false);
		ParallaxLayer layer = new ParallaxLayer(strippedRegion(3403, 580, 4559, 580, 913, 0), true, 40, 0.02f, 0.02f, 1);
		reader.addLayers(list(layer));

		isFalse(layer.isUseOriginalSize(), "off by default");
		equal(580 * 40f / 3403, layer.getHeight(), 1e-4f, "height");
		reader.draw(camera, batch);
		equal(40, draws.get(0)[2], 1e-4f, "width");
		equal(580 * 40f / 3403, draws.get(0)[3], 1e-4f, "height");

		// Switching back and forth resizes the layer, and a copy keeps the setting.
		layer.setUseOriginalSize(true);
		equal(580 * 40f / 4559, layer.clone().getHeight(), 1e-4f, "the copy's height");
		layer.setUseOriginalSize(false);
		equal(580 * 40f / 3403, layer.getHeight(), 1e-4f, "height");
	}

	/**
	 * Mirror adds a second strip flipped across the first one's far edge: above it, upside down, when the page tiles on X;
	 * to its right, reversed, when it tiles on Y; nothing when it tiles on both axes or neither (README, r127).
	 */
	void mirrorStacksAFlippedStripAcrossTheTiledOne()
	{
		for (boolean onX : new boolean[] { true, false })
		{
			draws.clear();
			ParallaxPageReader reader = reader(onX, !onX);
			ParallaxLayer layer = layer(0);
			layer.setSizeRatio(0.3f); // 12 x 6.75 world units, the view is 40 x 22.5
			layer.setMirror(true);
			reader.addLayers(list(layer));
			reader.draw(camera, batch);

			float width = layer.getWidth(), height = layer.getHeight();
			int flipped = 0;
			for (float[] draw : draws)
			{
				if (onX && draw[3] < 0)
				{
					flipped++;
					equal(2 * height, draw[1], 1e-4f, "upside down, its top on the strip's top edge");
					isTrue(draw[2] > 0, "not reversed on X");
				}
				else if (!onX && draw[2] < 0)
				{
					flipped++;
					equal(2 * width, draw[0], 1e-4f, "reversed, its right side on the column's right edge");
					isTrue(draw[3] > 0, "not upside down on Y");
				}
				else
					equal(0, onX ? draw[1] : draw[0], 1e-4f, "the tiled strip itself");
			}
			equal(draws.size() / 2, flipped, (onX ? "X" : "Y") + ": one mirrored copy per repeat, " + draws.size() + " draws");
		}

		for (boolean both : new boolean[] { true, false })
		{
			draws.clear();
			ParallaxPageReader reader = reader(both, both);
			ParallaxLayer layer = layer(0);
			layer.setSizeRatio(0.3f);
			layer.setMirror(true);
			reader.addLayers(list(layer));
			reader.draw(camera, batch);
			for (float[] draw : draws)
				isTrue(draw[2] > 0 && draw[3] > 0, "no mirrored copy when tiling on " + (both ? "both axes" : "neither"));
		}
	}

	/** BrowserSuiteTest bounds every case with assertTimeoutPreemptively; in Chrome a hang freezes the page and the gate times out. */
	void negativePaddingLargerThanTheImageDoesNotHang()
	{
		ParallaxPageReader reader = reader(true, true);
		ParallaxLayer layer = layer(0);
		layer.setPadX(-50);
		layer.setPadY(-50);
		reader.addLayers(list(layer));

		reader.draw(camera, batch);
		equal(1, draws.size(), "draws");
	}

	void zeroSecondTransferSwapsImmediately()
	{
		ParallaxPageReader reader = reader(true, false);
		reader.addLayers(list(layer(0)));
		List<ParallaxLayer> next = list(layer(0));

		reader.addLayersTransfert(page(next), 0);
		reader.act(0, 0, 0);

		isFalse(reader.isInTransfer(), "still in transfer");
		same(next.get(0), reader.layers.get(0), "the new page's layer");
	}

	void transferKeepsTheNewLayoutAndTheScrolledDistance()
	{
		ParallaxPageReader reader = reader(true, false);
		ParallaxLayer winter = layer(10);
		reader.addLayers(list(winter));
		reader.act(1, 100, 0);

		ParallaxLayer spring = layer(30);
		reader.addLayersTransfert(page(list(spring)), 1);

		equal(winter.getScrollX(), spring.getScrollX(), 1e-4f, "scrolled distance");
		equal(30 * Gvars_Parallax.getHeightPercent(), spring.getCurrentDistanceY(), 1e-4f, "vertical placement of the new page is kept");

		reader.act(2, 0, 0);
		same(spring, reader.layers.get(0), "the new page's layer");
	}

	void transferIntoThePageOnScreenUsesCopies()
	{
		ParallaxPageReader reader = reader(true, false);
		List<ParallaxLayer> layers = list(layer(0), layer(20));
		reader.addLayers(layers);

		reader.addLayersTransfert(page(layers), 1);

		for (int i = 0; i < layers.size(); i++)
			notSame(reader.layers.get(i), reader.transferLayers.get(i), "layer " + i);
	}

	void layersAtAlphaZeroAreNotDrawn()
	{
		ParallaxPageReader reader = reader(false, false);
		reader.addLayers(list(layer(0)));
		reader.addLayersTransfert(page(list(layer(0))), 1);

		reader.draw(camera, batch);
		equal(1, draws.size(), "the incoming page starts at alpha 0");

		reader.act(0.5f, 0, 0);
		draws.clear();
		reader.draw(camera, batch);
		equal(2, draws.size(), "both pages show mid-transfer");

		reader.addColorTransfert(new Color(1, 1, 1, 0), 0);
		draws.clear();
		reader.draw(camera, batch);
		equal(0, draws.size(), "a fully transparent tint hides every layer");
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

	/**
	 * One emitter holding one still particle, 1 world unit square, centered {@code offsetX} right of the effect's origin,
	 * alive 100 s: drawn, it shows where the reader put the origin.
	 */
	private static ParallaxParticles dot(float offsetX)
	{
		ParticleEmitter emitter = new ParticleEmitter();
		emitter.setMaxParticleCount(1);
		emitter.setMinParticleCount(1);
		emitter.setContinuous(true);
		emitter.getDuration().setLow(1000);
		emitter.getLife().setHigh(100000);
		emitter.getXScale().setHigh(1);
		emitter.getTransparency().setHigh(1);
		if (offsetX != 0)
		{
			emitter.getXOffsetValue().setActive(true);
			emitter.getXOffsetValue().setLow(offsetX);
		}
		emitter.setAdditive(false);
		emitter.setSprites(new Array<Sprite>(new Sprite[] { new Sprite(strippedRegion(10, 10, 10, 10, 0, 0)) }));
		ParticleEffect effect = new ParticleEffect();
		effect.getEmitters().add(emitter);
		return new ParallaxParticles(effect);
	}

	/** The particles' draws, recognized by their 1-unit width: a browser's floats are doubles, compare loosely. */
	private List<float[]> dots()
	{
		List<float[]> dots = new ArrayList<>();
		for (float[] draw : draws)
			if (Math.abs(draw[2] - 1) < 1e-3f && Math.abs(draw[3] - 1) < 1e-3f)
				dots.add(draw);
		return dots;
	}

	/**
	 * A PARTICLES layer pinned to its layer draws its effect at the corner of every tile the view shows, in its place in
	 * the draw order: the image layers around it are 40 wide, the particles 1.
	 */
	void particlesArePinnedToTheirLayerInEveryRepeatMode()
	{
		for (int mode = 0; mode < 4; mode++)
		{
			boolean onX = mode == 0 || mode == 2, onY = mode == 1 || mode == 2;
			String name = onX && onY ? "XY" : onX ? "X" : onY ? "Y" : "none";
			draws.clear();
			ParallaxPageReader reader = reader(onX, onY);
			reader.setWorldSize(40, 22.5f);
			ParallaxLayer snow = ParallaxLayer.particles(dot(0), Enum_ParticleAnchor.LAYER, 0.3f); // 12 x 6.75, a step of 13 x 7.75
			snow.setPadX(1);
			snow.setPadY(1);
			snow.setParallaxSpeedRatioX(0.02f);
			snow.setParallaxSpeedRatioY(0.02f);
			snow.setDecalPercentX(25); // its corner at 10, 5.625: inside the view when nothing repeats
			snow.setDecalPercentY(25);
			reader.addLayers(list(layer(0), snow, layer(0)));
			reader.act(1 / 60f, 500, 300);
			reader.draw(camera, batch);

			List<float[]> dots = dots();
			isTrue(dots.size() > 0, name + ": the particles are drawn");
			isTrue(draws.indexOf(dots.get(0)) > 0, name + ": after the layer behind them");
			isTrue(draws.indexOf(dots.get(dots.size() - 1)) < draws.size() - 1, name + ": and before the layer in front of them");
			float cornerX = snow.getCurrentDistanceX(), cornerY = snow.getCurrentDistanceY();
			for (float[] dot : dots)
			{
				float x = dot[0] + 0.5f - cornerX, y = dot[1] + 0.5f - cornerY;
				float stepsX = x / 13, stepsY = y / 7.75f;
				equal(Math.round(stepsX), stepsX, 1e-3f, name + ": a particle on a tile's corner, x " + dot[0]);
				equal(Math.round(stepsY), stepsY, 1e-3f, name + ": a particle on a tile's corner, y " + dot[1]);
				if (!onX)
					equal(0, Math.round(stepsX), name + ": not repeated on X");
				if (!onY)
					equal(0, Math.round(stepsY), name + ": not repeated on Y");
				isTrue(dot[0] + 1 > 0 && dot[0] < 40 && dot[1] + 1 > 0 && dot[1] < 22.5f, name + ": off-screen particle at " + dot[0] + ", " + dot[1]);
			}
			// The corners in the view: 3 or 4 across 40 at a step of 13, 3 or 4 up 22.5 at 7.75.
			int across = onX ? 3 : 1, down = onY ? 3 : 1;
			isTrue(dots.size() >= across * down && dots.size() <= (onX ? 4 : 1) * (onY ? 4 : 1), name + ": " + dots.size() + " particles drawn");
		}
	}

	/**
	 * Anchored to the view, the effect is emitted from the layer's decal in the view, and drawn once whatever the page
	 * repeats on; the particles it emitted then move with the layer's scroll, and the emitter does not.
	 */
	void viewAnchoredParticlesAreDrawnOnceAndDriftWithTheLayer()
	{
		for (int mode = 0; mode < 4; mode++)
		{
			boolean onX = mode == 0 || mode == 2, onY = mode == 1 || mode == 2;
			String name = onX && onY ? "XY" : onX ? "X" : onY ? "Y" : "none";
			ParallaxPageReader reader = reader(onX, onY);
			reader.setWorldSize(40, 22.5f);
			ParallaxParticles effect = dot(0);
			ParallaxLayer rain = ParallaxLayer.particles(effect, Enum_ParticleAnchor.VIEW, 0.3f);
			rain.setParallaxSpeedRatioX(0.1f);
			rain.setParallaxSpeedRatioY(0.1f);
			rain.setDecalPercentX(50);
			rain.setDecalPercentY(50);
			reader.addLayers(list(rain));

			reader.act(1 / 60f, 0, 0); // emits the particle at the emitter
			draws.clear();
			reader.draw(camera, batch);
			equal(1, dots().size(), name + ": drawn once");
			equal(20, dots().get(0)[0] + 0.5f, 1e-4f, name + ": at the decal across the view");
			equal(11.25f, dots().get(0)[1] + 0.5f, 1e-4f, name + ": and up it");

			reader.act(1, 60, 30); // the layer moves 6 left and 3 down
			draws.clear();
			reader.draw(camera, batch);
			equal(1, dots().size(), name + ": still drawn once");
			equal(14, dots().get(0)[0] + 0.5f, 1e-3f, name + ": the particle drifted with the layer");
			equal(8.25f, dots().get(0)[1] + 0.5f, 1e-3f, name + ": both ways");
			equal(0, effect.getEmitters().get(0).getX(), 0, name + ": the emitter stayed in the view");
		}
	}

	/** Particles reach past their box: a box out of the view still draws the particles that are in it. */
	void particlesPastTheirBoxAreDrawnWhenTheBoxIsNot()
	{
		ParallaxPageReader reader = reader(false, false);
		reader.setWorldSize(40, 22.5f);
		ParallaxLayer spray = ParallaxLayer.particles(dot(13), Enum_ParticleAnchor.LAYER, 0.3f); // 12 wide, a particle 13 right of it
		spray.setDecalPercentX(-31.25f); // the box from -12.5 to -0.5, the particle around 0.5
		reader.addLayers(list(spray));
		reader.act(1 / 60f, 0, 0);
		reader.draw(camera, batch);

		equal(1, dots().size(), "the particle in the view is drawn");
		equal(0.5f, dots().get(0)[0] + 0.5f, 1e-4f, "at its place");
	}

	/** The particles take their page's tint and its opacity during a cross-fade, as an image layer does. */
	void particlesFadeAndTintWithTheirPage()
	{
		ParallaxPageReader reader = reader(false, false);
		reader.setWorldSize(40, 22.5f);
		ParallaxLayer snow = ParallaxLayer.particles(dot(0), Enum_ParticleAnchor.LAYER, 0.3f);
		snow.setDecalPercentX(50);
		snow.setDecalPercentY(50);
		List<ParallaxLayer> page = list(snow);
		reader.addLayers(page);
		reader.addLayersTransfert(page(page), 1); // into itself: the incoming copy has an effect of its own
		reader.addColorTransfert(new Color(0.5f, 1, 1, 1), 0);
		reader.act(0.5f, 0, 0);
		reader.draw(camera, batch);

		notSame(snow.getParticles(), reader.transferLayers.get(0).getParticles(), "the copy's effect");
		equal(2, dots().size(), "both pages' particles mid-fade");
		for (float[] dot : dots())
		{
			// A packed color loses alpha's lowest bit (NumberUtils.intToFloatColor): 127 comes back 126.
			equal(0.5f, dot[5], 2 / 255f, "at half opacity");
			equal(0.5f, dot[4], 1 / 255f, "tinted");
		}
	}

	void aParticleLayerWithoutAnEffectDrawsNothing()
	{
		ParallaxPageReader reader = reader(true, true);
		reader.addLayers(list(ParallaxLayer.particles(null, Enum_ParticleAnchor.LAYER, 0.3f), ParallaxLayer.particles(null, Enum_ParticleAnchor.VIEW, 1)));
		reader.act(1 / 60f, 100, 100);
		reader.draw(camera, batch);
		equal(0, draws.size(), "draws");
	}
}
