package jks.tools2d.parallax.webp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.ByteBuffer;

import org.junit.jupiter.api.Test;

import com.jme3.asset.AssetLoadException;
import com.jme3.asset.AssetManager;
import com.jme3.asset.DesktopAssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.texture.Image;

/**
 * r552: a jME desktop AssetManager loads a lossless WebP, once {@link JmeWebp#register} is called, as the very jME image
 * its PNG loads as (jME's own AWTLoader), flipped or not. No GL: an Image is its bytes.
 */
class JmeWebpTest
{
	private static AssetManager assets()
	{
		AssetManager assets = new DesktopAssetManager(true);
		assets.registerLocator(new File(System.getProperty("parallax.repoRoot", ".."), "webp/test-data").getAbsolutePath(),
				FileLocator.class);
		return assets;
	}

	@Test
	void withoutRegisterJmeHasNoWebp()
	{assertThrows(AssetLoadException.class, () -> assets().loadTexture(new TextureKey("fairy-crop.webp", false)));}

	@Test
	void losslessWebpIsItsPngImage()
	{
		for (boolean flip : new boolean[] {false, true})
		{
			AssetManager assets = assets();
			JmeWebp.register(assets);
			Image png = assets.loadTexture(new TextureKey("fairy-crop.png", flip)).getImage();
			Image webp = assets.loadTexture(new TextureKey("fairy-crop.webp", flip)).getImage();
			assertEquals(png.getFormat(), webp.getFormat());
			assertEquals(png.getWidth(), webp.getWidth());
			assertEquals(png.getHeight(), webp.getHeight());
			assertEquals(png.getColorSpace(), webp.getColorSpace());
			ByteBuffer a = png.getData(0), b = webp.getData(0);
			assertEquals(a.remaining(), b.remaining());
			int differ = 0;
			for (int i = 0; i < a.remaining(); i++)
				if (a.get(i) != b.get(i))
					differ++;
			assertEquals(0, differ, "bytes differing, flip " + flip);
		}
	}
}
