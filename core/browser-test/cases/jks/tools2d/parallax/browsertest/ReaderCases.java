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
import com.badlogic.gdx.graphics.g2d.TextureAtlas.AtlasRegion;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.pages.WholePage_Model;

/** core/test ParallaxPageReaderTest, case for case, in code GWT translates: tiling, stripped regions and cross-fades. */
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
				new BrowserCase("reader: negativePaddingLargerThanTheImageDoesNotHang", () -> new ReaderCases().negativePaddingLargerThanTheImageDoesNotHang()),
				new BrowserCase("reader: zeroSecondTransferSwapsImmediately", () -> new ReaderCases().zeroSecondTransferSwapsImmediately()),
				new BrowserCase("reader: transferKeepsTheNewLayoutAndTheScrolledDistance", () -> new ReaderCases().transferKeepsTheNewLayoutAndTheScrolledDistance()),
				new BrowserCase("reader: transferIntoThePageOnScreenUsesCopies", () -> new ReaderCases().transferIntoThePageOnScreenUsesCopies()),
				new BrowserCase("reader: layersAtAlphaZeroAreNotDrawn", () -> new ReaderCases().layersAtAlphaZeroAreNotDrawn()));
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

	/** demo/assets Hiver.atlas parallax4 #1: 3403x580 packed out of 4559x580, 913px stripped on its left. */
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

	/** demo/assets Printemps.atlas parallax1 #4: 3645x335 packed out of 3645x580, 142px stripped below it. */
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

	/** The JVM test bounds it with assertTimeoutPreemptively; here a hang freezes the page and the gate times out. */
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
}
