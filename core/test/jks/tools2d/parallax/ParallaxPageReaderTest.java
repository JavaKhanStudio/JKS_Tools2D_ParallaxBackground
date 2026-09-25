package jks.tools2d.parallax;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.GdxNativesLoader;

import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.pages.WholePage_Model;

class ParallaxPageReaderTest
{
	private final List<float[]> draws = new ArrayList<>();
	private final Batch batch = (Batch) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { Batch.class }, (proxy, method, args) ->
	{
		if (method.getName().equals("draw") && args.length == 5)
			draws.add(new float[] { (float) args[1], (float) args[2], (float) args[3], (float) args[4] });
		return method.getReturnType() == boolean.class ? false : method.getReturnType().isPrimitive() && method.getReturnType() != void.class ? 0 : null;
	});
	private OrthographicCamera camera;

	@BeforeAll
	static void natives()
	{GdxNativesLoader.load();}

	/** A region of the given pixel size, without any texture behind it. */
	private static TextureRegion region(int width, int height)
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

	private static ParallaxLayer layer(float decalY)
	{
		ParallaxLayer layer = new ParallaxLayer(region(1920, 1080), true, 40, 0.02f, 0.02f, 1);
		layer.setDecalPercentY(decalY);
		return layer;
	}

	private static WholePage_Model page(List<ParallaxLayer> layers)
	{
		return new WholePage_Model()
		{
			@Override
			public List<ParallaxLayer> getDrawing(String relativePath, float worldWidth, float worldHeight)
			{return layers;}
		};
	}

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

	@Test
	void tilesJustEnoughToCoverTheView()
	{
		ParallaxPageReader reader = reader(true, false);
		ParallaxLayer layer = layer(0);
		layer.setSizeRatio(0.3f); // 12 world units wide, the view is 40
		reader.addLayers(List.of(layer));

		reader.act(1 / 60f, 500, 0);
		reader.draw(camera, batch);

		for (float[] draw : draws)
			assertTrue(draw[0] + draw[2] > 0 && draw[0] < 40, "off-screen draw at x=" + draw[0]);
		assertTrue(draws.size() >= 4 && draws.size() <= 5, draws.size() + " draws");
	}

	@Test
	void negativePaddingLargerThanTheImageDoesNotHang()
	{
		ParallaxPageReader reader = reader(true, true);
		ParallaxLayer layer = layer(0);
		layer.setPadX(-50);
		layer.setPadY(-50);
		reader.addLayers(List.of(layer));

		assertTimeoutPreemptively(Duration.ofSeconds(2), () -> reader.draw(camera, batch));
		assertEquals(1, draws.size());
	}

	@Test
	void zeroSecondTransferSwapsImmediately()
	{
		ParallaxPageReader reader = reader(true, false);
		reader.addLayers(List.of(layer(0)));
		List<ParallaxLayer> next = List.of(layer(0));

		reader.addLayersTransfert(page(next), 0);
		reader.act(0, 0, 0);

		assertFalse(reader.isInTransfer());
		assertSame(next.get(0), reader.layers.get(0));
	}

	@Test
	void transferKeepsTheNewLayoutAndTheScrolledDistance()
	{
		ParallaxPageReader reader = reader(true, false);
		ParallaxLayer winter = layer(10);
		reader.addLayers(List.of(winter));
		reader.act(1, 100, 0);

		ParallaxLayer spring = layer(30);
		reader.addLayersTransfert(page(List.of(spring)), 1);

		assertEquals(winter.getScrollX(), spring.getScrollX(), 1e-4f);
		assertEquals(30 * Gvars_Parallax.getHeightPercent(), spring.getCurrentDistanceY(), 1e-4f, "vertical placement of the new page is kept");

		reader.act(2, 0, 0);
		assertSame(spring, reader.layers.get(0));
	}

	@Test
	void transferIntoThePageOnScreenUsesCopies()
	{
		ParallaxPageReader reader = reader(true, false);
		List<ParallaxLayer> layers = List.of(layer(0), layer(20));
		reader.addLayers(layers);

		reader.addLayersTransfert(page(layers), 1);

		for (int i = 0; i < layers.size(); i++)
			assertNotSame(reader.layers.get(i), reader.transferLayers.get(i));
	}

	@Test
	void layersAtAlphaZeroAreNotDrawn()
	{
		ParallaxPageReader reader = reader(false, false);
		reader.addLayers(List.of(layer(0)));
		reader.addLayersTransfert(page(List.of(layer(0))), 1);

		reader.draw(camera, batch);
		assertEquals(1, draws.size(), "the incoming page starts at alpha 0");

		reader.act(0.5f, 0, 0);
		draws.clear();
		reader.draw(camera, batch);
		assertEquals(2, draws.size(), "both pages show mid-transfer");

		reader.addColorTransfert(new Color(1, 1, 1, 0), 0);
		draws.clear();
		reader.draw(camera, batch);
		assertEquals(0, draws.size(), "a fully transparent tint hides every layer");
	}
}
