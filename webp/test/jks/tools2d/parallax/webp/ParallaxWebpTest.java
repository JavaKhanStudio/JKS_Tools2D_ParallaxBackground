package jks.tools2d.parallax.webp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.resolvers.AbsoluteFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.GdxNativesLoader;

import jks.tools2d.parallax.heart.Gvars_Parallax;
import jks.tools2d.parallax.pages.DecodedTextureLoader;
import jks.tools2d.parallax.pages.Utils_Etc2_Atlas;

/**
 * r523: a WebP decodes to the very pixels its PNG does through libGDX's own reader. The fixture is a crop of the
 * PurpleFairy demo page with a 0-255 alpha ramp over its top rows and a band of transparent texels that keep their
 * colour, saved by Pillow as lossless WebP with exact=True (webp/test-data).
 */
class ParallaxWebpTest
{
	private static File data(String name)
	{return new File(System.getProperty("parallax.repoRoot", ".."), "webp/test-data/" + name);}

	@BeforeAll
	static void natives()
	{GdxNativesLoader.load();}

	@AfterEach
	void uninstall()
	{Gvars_Parallax.setImageDecoder(null);}

	@Test
	void losslessWebpIsItsPngPixelForPixel()
	{
		Pixmap png = new Pixmap(new FileHandle(data("fairy-crop.png")));
		Pixmap webp = ParallaxWebp.pixmap(new FileHandle(data("fairy-crop.webp")));
		assertEquals(Pixmap.Format.RGBA8888, png.getFormat());
		assertEquals(Pixmap.Format.RGBA8888, webp.getFormat());
		assertEquals(png.getWidth(), webp.getWidth());
		assertEquals(png.getHeight(), webp.getHeight());
		ByteBuffer a = png.getPixels(), b = webp.getPixels();
		int differ = 0, ramp = 0;
		for (int i = 0; i < png.getWidth() * png.getHeight() * 4; i++)
		{
			if (a.get(i) != b.get(i))
				differ++;
			if (i % 4 == 3 && a.get(i) != 0 && a.get(i) != (byte) 255)
				ramp++;
		}
		assertTrue(ramp > 10000, "the fixture's alpha ramp is there: " + ramp + " half-transparent texels");
		assertEquals(0, differ, "bytes differing between the PNG and the WebP");
		png.dispose();
		webp.dispose();
	}

	@Test
	void lossyWebpIsClose()
	{
		Pixmap png = new Pixmap(new FileHandle(data("fairy-crop.png")));
		Pixmap webp = ParallaxWebp.pixmap(new FileHandle(data("fairy-crop-lossy.webp")));
		assertEquals(png.getWidth(), webp.getWidth());
		ByteBuffer a = png.getPixels(), b = webp.getPixels();
		// Opaque texels only: a lossy WebP drops the colour of transparent ones.
		long sum = 0;
		int n = 0;
		for (int i = 0; i < png.getWidth() * png.getHeight() * 4; i += 4)
			if (a.get(i + 3) == (byte) 255)
				for (int c = 0; c < 4; c++, n++)
					sum += Math.abs((a.get(i + c) & 0xff) - (b.get(i + c) & 0xff));
		assertTrue(sum / (double) n < 6, "mean difference " + sum / (double) n);
		png.dispose();
		webp.dispose();
	}

	@Test
	void installedDecoderLoadsWebpTexturesThroughThePagesManager()
	{
		AssetManager manager = new AssetManager(new AbsoluteFileHandleResolver());
		Utils_Etc2_Atlas.register(manager);
		assertTrue(!(manager.getLoader(Texture.class, "page.webp") instanceof DecodedTextureLoader), "none before install");
		ParallaxWebp.install();
		Utils_Etc2_Atlas.register(manager);
		Object loader = manager.getLoader(Texture.class, "page.webp");
		assertTrue(loader instanceof DecodedTextureLoader);
		Utils_Etc2_Atlas.register(manager);
		assertSame(loader, manager.getLoader(Texture.class, "page.webp"), "registered once");
		assertTrue(!(manager.getLoader(Texture.class, "page.png") instanceof DecodedTextureLoader), "PNG stays libGDX's");
		manager.dispose();
	}
}
