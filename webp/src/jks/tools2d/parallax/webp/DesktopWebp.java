package jks.tools2d.parallax.webp;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;

/**
 * WebP on a desktop JVM: TwelveMonkeys' reader, asked for directly rather than through ImageIO's registry, so it is
 * found under any class loader. Loaded only off Android: Android has no java.awt.
 */
final class DesktopWebp
{
	private static final WebPImageReaderSpi SPI = new WebPImageReaderSpi();

	private DesktopWebp()
	{}

	static Pixmap decode(FileHandle file)
	{
		ImageReader reader = null;
		try (InputStream in = file.read(); MemoryCacheImageInputStream stream = new MemoryCacheImageInputStream(in))
		{
			reader = SPI.createReaderInstance();
			reader.setInput(stream);
			BufferedImage image = reader.read(0);
			int width = image.getWidth(), height = image.getHeight();
			// getRGB: sRGB, ARGB, never premultiplied, whatever the decoded image's type.
			int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
			return ParallaxWebp.fromArgb(argb, width, height);
		}
		catch (IOException e)
		{throw new GdxRuntimeException("Couldn't decode WebP " + file, e);}
		finally
		{
			if (reader != null)
				reader.dispose();
		}
	}
}
