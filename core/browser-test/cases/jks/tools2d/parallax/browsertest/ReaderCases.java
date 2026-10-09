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

import jks.tools2d.parallax.GdxLayerEffects;
import jks.tools2d.parallax.LayerEffects;
import jks.tools2d.parallax.LayerHook;
import jks.tools2d.parallax.ParallaxParticles;
import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.SequenceCycle;
import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.pages.Enum_LayerKind;
import jks.tools2d.parallax.pages.Enum_ParticleAnchor;
import jks.tools2d.parallax.pages.Enum_ShaderEffect;
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
				new BrowserCase("reader: aParticleLayerWithoutAnEffectDrawsNothing", () -> new ReaderCases().aParticleLayerWithoutAnEffectDrawsNothing()),
				new BrowserCase("reader: aFreshParticleLayerIsAlreadyFallingOnItsFirstFrame", () -> new ReaderCases().aFreshParticleLayerIsAlreadyFallingOnItsFirstFrame()),
				new BrowserCase("reader: shaderLayerIsTiledThroughItsEffectInEveryRepeatMode", () -> new ReaderCases().shaderLayerIsTiledThroughItsEffectInEveryRepeatMode()),
				new BrowserCase("reader: shaderPhaseFollowsTheReaderClockAndWraps", () -> new ReaderCases().shaderPhaseFollowsTheReaderClockAndWraps()),
				new BrowserCase("reader: bothPagesOfACrossFadeShadeOnOneClock", () -> new ReaderCases().bothPagesOfACrossFadeShadeOnOneClock()),
				new BrowserCase("reader: aShaderLayerWithoutItsEffectIsDrawnPlain", () -> new ReaderCases().aShaderLayerWithoutItsEffectIsDrawnPlain()),
				new BrowserCase("reader: shaderNumbersAreTheDrawnImages", () -> new ReaderCases().shaderNumbersAreTheDrawnImages()),
				new BrowserCase("reader: depthHazeMixesTheLayersBehindAFogLayerMoreTheFurtherBack", () -> new ReaderCases().depthHazeMixesTheLayersBehindAFogLayerMoreTheFurtherBack()),
				new BrowserCase("reader: noDepthHazeDrawsEveryImageLayerAsBefore", () -> new ReaderCases().noDepthHazeDrawsEveryImageLayerAsBefore()),
				new BrowserCase("reader: sequenceDrawsJustTheSegmentsInViewInEveryRepeatMode", () -> new ReaderCases().sequenceDrawsJustTheSegmentsInViewInEveryRepeatMode()),
				new BrowserCase("reader: sequenceWithAPadBackOverASegmentStillDrawsWhatShows", () -> new ReaderCases().sequenceWithAPadBackOverASegmentStillDrawsWhatShows()),
				new BrowserCase("reader: sequenceWhosePadsOutweighItsSegmentsIsDrawnOnce", () -> new ReaderCases().sequenceWhosePadsOutweighItsSegmentsIsDrawnOnce()),
				new BrowserCase("reader: sequenceMirrorFlipsEachSegmentInItsSlot", () -> new ReaderCases().sequenceMirrorFlipsEachSegmentInItsSlot()),
				new BrowserCase("reader: sequenceCycleOfAKnownSeedIsPinned", () -> new ReaderCases().sequenceCycleOfAKnownSeedIsPinned()),
				new BrowserCase("reader: aGameSeedRedrawsTheSequencesOfBothPages", () -> new ReaderCases().aGameSeedRedrawsTheSequencesOfBothPages()));
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

	/**
	 * A layer built and drawn on its first frame shows its effect already running: 20 particles a second, each living
	 * 3 s, rising 1 unit a second from a 12-wide line, is about 60 particles spread 3 units up, not the one or none
	 * emitted in a frame. Its cross-fade copy is warmed as well.
	 */
	void aFreshParticleLayerIsAlreadyFallingOnItsFirstFrame()
	{
		ParticleEmitter emitter = new ParticleEmitter();
		emitter.setMaxParticleCount(60);
		emitter.setContinuous(true);
		emitter.getDuration().setLow(1000);
		emitter.getEmission().setHigh(20);
		emitter.getLife().setHigh(3000);
		emitter.getSpawnShape().setShape(ParticleEmitter.SpawnShape.line);
		emitter.getSpawnWidth().setHigh(12);
		emitter.getXScale().setHigh(1);
		emitter.getVelocity().setActive(true);
		emitter.getVelocity().setHigh(1);
		emitter.getAngle().setActive(true);
		emitter.getAngle().setHigh(90);
		emitter.getTransparency().setHigh(1);
		emitter.setAdditive(false);
		emitter.setSprites(new Array<Sprite>(new Sprite[] { new Sprite(strippedRegion(10, 10, 10, 10, 0, 0)) }));
		ParticleEffect effect = new ParticleEffect();
		effect.getEmitters().add(emitter);

		ParallaxPageReader reader = reader(false, false);
		reader.setWorldSize(40, 22.5f);
		ParallaxLayer rising = ParallaxLayer.particles(new ParallaxParticles(effect), Enum_ParticleAnchor.LAYER, 0.3f);
		rising.setDecalPercentX(25);
		rising.setDecalPercentY(25);
		List<ParallaxLayer> page = list(rising);
		reader.addLayers(page);
		reader.act(1 / 60f, 0, 0);
		reader.draw(camera, batch);

		List<float[]> dots = dots();
		isTrue(dots.size() >= 50, dots.size() + " particles out on the first frame, not about 60");
		float lowest = Float.MAX_VALUE, highest = -Float.MAX_VALUE;
		for (float[] dot : dots)
		{
			lowest = Math.min(lowest, dot[1]);
			highest = Math.max(highest, dot[1]);
		}
		isTrue(highest - lowest > 2.5f, "the particles are spread " + (highest - lowest) + " up, not all just emitted");

		reader.addLayersTransfert(page(page), 1); // the incoming copy builds an effect of its own
		isTrue(!reader.transferLayers.get(0).getParticles().isEmpty(), "the cross-fade copy's particles are out too");
	}

	/**
	 * Stands for an engine's shaders: records, as indexes into the draws, where each SHADER layer began and ended, and the
	 * phase it was handed.
	 */
	private final class RecordingEffects implements LayerEffects
	{
		final List<float[]> runs = new ArrayList<>();
		boolean shades = true;
		private boolean open;

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{
			isTrue(!open, "a SHADER layer begun inside another");
			if (!shades)
				return false;
			open = true;
			runs.add(new float[] { draws.size(), -1, phase, layer.getShaderAmplitude() });
			return true;
		}

		@Override
		public void end(Batch batch, ParallaxLayer layer)
		{
			isTrue(open, "ended without a begin");
			open = false;
			runs.get(runs.size() - 1)[1] = draws.size();
		}
	}

	/** Stands for an engine's shaders with the depth haze: records each layer begun and the haze it was handed. */
	private final class HazeEffects implements LayerEffects
	{
		final List<ParallaxLayer> layers = new ArrayList<>();
		final List<Float> hazes = new ArrayList<>();

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase)
		{throw new AssertionError("the reader calls the begin that takes the haze");}

		@Override
		public boolean begin(Batch batch, ParallaxLayer layer, float phase, float haze)
		{
			layers.add(layer);
			hazes.add(haze);
			return true;
		}

		@Override
		public void end(Batch batch, ParallaxLayer layer)
		{}

		/** The haze {@code layer} was begun with; -1 when it was not begun. */
		float hazeOf(ParallaxLayer layer)
		{
			int i = layers.indexOf(layer);
			return i < 0 ? -1 : hazes.get(i);
		}
	}

	/** A FOG layer of {@code haze}, 12 x 6.75 world units in a 40-wide world. */
	private static ParallaxLayer mist(float haze)
	{
		ParallaxLayer layer = ParallaxLayer.shader(region(1920, 1080), 40, 0.3f, Enum_ShaderEffect.FOG, 0.5f, 3, 1);
		layer.setShaderHaze(haze);
		return layer;
	}

	/**
	 * r211: each layer behind a FOG layer of haze h, n steps back, is drawn through the engine's effects with haze
	 * 1 - (1 - h)^n; an EMPTY layer counts as a step; the FOG layer and the layers in front of it get none, and are
	 * begun only when they are SHADER layers. Two FOG layers: the products multiply. A WAVE layer's haze is 0 whatever it
	 * stores, and a haze above 1 is 1.
	 */
	void depthHazeMixesTheLayersBehindAFogLayerMoreTheFurtherBack()
	{
		ParallaxPageReader reader = reader(true, false);
		HazeEffects effects = new HazeEffects();
		reader.setLayerEffects(effects);
		ParallaxLayer far = layer(0), slot = ParallaxLayer.empty("slot", 0.3f), near = layer(0), fog = mist(0.25f), front = layer(0);
		ParallaxLayer wave = shaded(0.5f, 3, 1);
		wave.setShaderHaze(0.9f);
		reader.addLayers(list(far, slot, near, wave, fog, front));
		reader.draw(camera, batch);

		equal(-1, effects.hazeOf(front), 0, "the layer in front of the mist is drawn as is");
		equal(0, effects.hazeOf(fog), 0, "the mist does not haze itself");
		equal(0.25f, effects.hazeOf(wave), 1e-6f, "one step behind: h");
		equal(1 - 0.75f * 0.75f, effects.hazeOf(near), 1e-6f, "two steps: 1 - (1 - h)^2, the WAVE layer's haze is not one");
		equal(-1, effects.hazeOf(slot), 0, "an EMPTY layer is not hazed");
		equal(1 - 0.75f * 0.75f * 0.75f * 0.75f, effects.hazeOf(far), 1e-6f, "four steps, the EMPTY layer counted");

		ParallaxPageReader two = reader(true, false);
		HazeEffects both = new HazeEffects();
		two.setLayerEffects(both);
		ParallaxLayer back = layer(0), between = layer(0), thick = mist(3);
		two.addLayers(list(back, mist(0.5f), between, mist(0.2f), thick));
		two.draw(camera, batch);
		equal(1, both.hazeOf(between), 1e-6f, "a haze above 1 is 1");
		equal(1, ParallaxPageReader.hazeOf(two.layers, 0), 1e-6f, "and every layer behind it");

		List<ParallaxLayer> page = list(layer(0), mist(0.5f), layer(0), mist(0.2f), layer(0));
		equal(0.2f, ParallaxPageReader.hazeOf(page, 2), 1e-6f, "between them: the front one, one step");
		equal(1 - 0.8f * 0.8f * 0.8f * 0.5f, ParallaxPageReader.hazeOf(page, 0), 1e-6f, "behind both: (1 - 0.2)^3 (1 - 0.5)^1");
	}

	/**
	 * Haze 0, or no FOG layer: the IMAGE layers are never handed to the engine's effects, so they draw as before r211,
	 * with no shader and no flush; a LayerEffects written before the haze still draws the SHADER layers.
	 */
	void noDepthHazeDrawsEveryImageLayerAsBefore()
	{
		ParallaxPageReader reader = reader(true, false);
		HazeEffects effects = new HazeEffects();
		reader.setLayerEffects(effects);
		ParallaxLayer back = layer(0), fog = mist(0);
		reader.addLayers(list(back, fog));
		reader.draw(camera, batch);
		equal(-1, effects.hazeOf(back), 0, "haze 0 hands the layer behind to nobody");
		equal(0, effects.hazeOf(fog), 0, "the FOG layer is drawn through its effect, haze 0");

		ParallaxPageReader old = reader(true, false);
		RecordingEffects before = new RecordingEffects();
		old.setLayerEffects(before);
		old.addLayers(list(layer(0), mist(0.5f)));
		draws.clear();
		old.draw(camera, batch);
		equal(1, before.runs.size(), "a LayerEffects without the haze: the FOG layer only, through its effect");
		isTrue(before.runs.get(0)[0] > 0, "the hazed layer behind it is drawn, plain");
	}

	/** A SHADER layer drawing a 1920x1080 region through WAVE, 12 x 6.75 world units in a 40-wide world. */
	private static ParallaxLayer shaded(float amplitude, float wavelength, float speed)
	{
		ParallaxLayer layer = ParallaxLayer.shader(region(1920, 1080), 40, 0.3f, Enum_ShaderEffect.WAVE, amplitude, wavelength, speed);
		layer.setParallaxSpeedRatioX(0.02f);
		layer.setParallaxSpeedRatioY(0.02f);
		layer.setDecalPercentX(25);
		layer.setDecalPercentY(25);
		return layer;
	}

	/**
	 * A SHADER layer between two image layers is tiled as an IMAGE layer with the same settings would be, every tile and
	 * its mirrored copy between one begin and one end of its effect, in its place in the draw order.
	 */
	void shaderLayerIsTiledThroughItsEffectInEveryRepeatMode()
	{
		for (int mode = 0; mode < 4; mode++)
		{
			boolean onX = mode == 0 || mode == 2, onY = mode == 1 || mode == 2;
			String name = onX && onY ? "XY" : onX ? "X" : onY ? "Y" : "none";

			// The same layer as an IMAGE: what the SHADER one must draw.
			ParallaxPageReader plain = reader(onX, onY);
			plain.setWorldSize(40, 22.5f);
			ParallaxLayer image = new ParallaxLayer(region(1920, 1080), true, 40, 0.02f, 0.02f, 0.3f);
			image.setDecalPercentX(25);
			image.setDecalPercentY(25);
			image.setMirror(true);
			plain.addLayers(list(image));
			draws.clear();
			plain.act(1 / 60f, 500, 300);
			plain.draw(camera, batch);
			List<float[]> expected = new ArrayList<>(draws);

			ParallaxPageReader reader = reader(onX, onY);
			reader.setWorldSize(40, 22.5f);
			RecordingEffects effects = new RecordingEffects();
			reader.setLayerEffects(effects);
			ParallaxLayer wave = shaded(0.5f, 2, 1);
			wave.setMirror(true);
			reader.addLayers(list(layer(0), wave, layer(0)));
			draws.clear();
			reader.act(1 / 60f, 500, 300);
			reader.draw(camera, batch);

			equal(1, effects.runs.size(), name + ": one effect run a frame");
			float[] run = effects.runs.get(0);
			int from = (int) run[0], to = (int) run[1];
			isTrue(from > 0, name + ": after the layer behind it");
			isTrue(to < draws.size(), name + ": and before the layer in front of it");
			isTrue(expected.size() > 0, name + ": the layer shows");
			equal(expected.size(), to - from, name + ": its tiles, mirrored copies included, are inside the run");
			for (int i = 0; i < expected.size(); i++)
				for (int k = 0; k < 4; k++)
					equal(expected.get(i)[k], draws.get(from + i)[k], 1e-4f, name + ": tile " + i + " as an image layer draws it");
		}
	}

	/** The effect's phase is speed times the reader's clock, wrapped to one wavelength, never negative; 0 without a wavelength. */
	void shaderPhaseFollowsTheReaderClockAndWraps()
	{
		ParallaxPageReader reader = reader(true, false);
		RecordingEffects effects = new RecordingEffects();
		reader.setLayerEffects(effects);
		ParallaxLayer up = shaded(0.5f, 3, 2), down = shaded(0.5f, 3, -2), flat = shaded(0.5f, 0, 2);
		reader.addLayers(list(up, down, flat));
		for (int i = 0; i < 120; i++)
			reader.act(1 / 60f, 0, 0);
		reader.draw(camera, batch);

		equal(2, (float) reader.getEffectTime(), 1e-4f, "the clock counts the acted seconds");
		equal(3, effects.runs.size(), "runs");
		equal(1, effects.runs.get(0)[2], 1e-3f, "2 s at 2 units/s is 4, one past a wavelength of 3");
		equal(2, effects.runs.get(1)[2], 1e-3f, "-4 wraps to 2");
		equal(0, effects.runs.get(2)[2], 0, "no wavelength, no phase");
	}

	/** During a cross-fade both pages' SHADER layers are drawn through their effect at the same phase, faded. */
	void bothPagesOfACrossFadeShadeOnOneClock()
	{
		ParallaxPageReader reader = reader(true, false);
		RecordingEffects effects = new RecordingEffects();
		reader.setLayerEffects(effects);
		reader.addLayers(list(shaded(0.5f, 3, 0.7f)));
		for (int i = 0; i < 60; i++)
			reader.act(1 / 60f, 100, 0);
		reader.addLayersTransfert(page(list(shaded(0.25f, 3, 0.7f))), 1);
		reader.act(0.5f, 100, 0);
		draws.clear();
		reader.draw(camera, batch);

		equal(2, effects.runs.size(), "both pages' layers shaded");
		equal(0.5f, effects.runs.get(0)[3], 0, "the outgoing one first");
		equal(0.25f, effects.runs.get(1)[3], 0, "then the incoming one");
		equal(effects.runs.get(0)[2], effects.runs.get(1)[2], 0, "one clock: the incoming layer does not start its effect over");
		equal(0.5f, draws.get((int) effects.runs.get(1)[0])[5], 1e-2f, "faded with its page");
	}

	/** An engine that cannot draw the effect (a shader that did not compile) draws the layer as an image. */
	void aShaderLayerWithoutItsEffectIsDrawnPlain()
	{
		ParallaxPageReader reader = reader(true, false);
		RecordingEffects effects = new RecordingEffects();
		effects.shades = false;
		reader.setLayerEffects(effects);
		reader.addLayers(list(shaded(0.5f, 3, 1)));
		reader.draw(camera, batch);
		equal(0, effects.runs.size(), "no run");
		isTrue(draws.size() >= 4, draws.size() + " tiles drawn all the same");
	}

	/**
	 * What every engine hands its shader: the region's texture coordinates, the drawn image's size (the packed part of a
	 * stripped region), and amplitude, wavelength, phase; FOG's amplitude within 0..1; no wavelength, no effect.
	 */
	void shaderNumbersAreTheDrawnImages()
	{
		float unit = 40f / 4559;
		List<TextureRegion> regions = new ArrayList<>();
		regions.add(strippedRegion(3403, 580, 4559, 580, 913, 0));
		ParallaxLayer layer = new ParallaxLayer(Enum_LayerKind.SHADER, regions, true, 40, 0, 0, 1);
		layer.setUseOriginalSize(true);
		layer.setShaderEffect(Enum_ShaderEffect.FOG);
		layer.setShaderAmplitude(1.5f);
		layer.setShaderWavelength(4);
		float[] numbers = GdxLayerEffects.uniforms(layer, 0.75f, new float[9]);
		// The region drawn: half a texel inside the atlas region (r218).
		equal(0.5f / 4096, numbers[0], 1e-7f, "u");
		equal(0.5f / 4096, numbers[1], 1e-7f, "v");
		equal(3402.5f / 4096, numbers[2], 1e-6f, "u2");
		equal(579.5f / 4096, numbers[3], 1e-6f, "v2");
		equal(3403 * unit, numbers[4], 1e-4f, "the packed image's width, not the layer's");
		equal(580 * unit, numbers[5], 1e-4f, "its height");
		equal(1, numbers[6], 0, "FOG thins by 100% at most");
		equal(4, numbers[7], 0, "wavelength");
		equal(0.75f, numbers[8], 0, "phase");

		layer.setShaderEffect(Enum_ShaderEffect.WAVE);
		equal(1.5f, GdxLayerEffects.uniforms(layer, 0.75f, numbers)[6], 0, "WAVE's amplitude is a distance, not clamped");
		layer.setShaderWavelength(0);
		GdxLayerEffects.uniforms(layer, 0.75f, numbers);
		equal(0, numbers[6], 0, "no wavelength: no shift");
		equal(1, numbers[7], 0, "and a wavelength that divides safely");
		equal(0, numbers[8], 0, "at phase 0");
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
