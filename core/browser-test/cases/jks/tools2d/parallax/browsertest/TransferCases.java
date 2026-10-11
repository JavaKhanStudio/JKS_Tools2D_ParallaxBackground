package jks.tools2d.parallax.browsertest;

import static jks.tools2d.parallax.browsertest.Check.equal;
import static jks.tools2d.parallax.browsertest.Check.isFalse;
import static jks.tools2d.parallax.browsertest.Check.notSame;
import static jks.tools2d.parallax.browsertest.Check.same;

import java.util.Arrays;
import java.util.List;

import com.badlogic.gdx.graphics.Color;

import jks.tools2d.parallax.ParallaxLayer;
import jks.tools2d.parallax.ParallaxPageReader;
import jks.tools2d.parallax.heart.Gvars_Parallax;

/**
 * ParallaxPageReader's cases on the cross-fade between two pages: an immediate swap, the new layout, copies, layers at
 * alpha 0. Shared builders in {@link ReaderFixtures}.
 */
final class TransferCases extends ReaderFixtures
{
	static List<BrowserCase> all()
	{
		return Arrays.asList(
				new BrowserCase("reader: zeroSecondTransferSwapsImmediately", () -> new TransferCases().zeroSecondTransferSwapsImmediately()),
				new BrowserCase("reader: transferKeepsTheNewLayoutAndTheScrolledDistance", () -> new TransferCases().transferKeepsTheNewLayoutAndTheScrolledDistance()),
				new BrowserCase("reader: transferIntoThePageOnScreenUsesCopies", () -> new TransferCases().transferIntoThePageOnScreenUsesCopies()),
				new BrowserCase("reader: layersAtAlphaZeroAreNotDrawn", () -> new TransferCases().layersAtAlphaZeroAreNotDrawn()));
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
