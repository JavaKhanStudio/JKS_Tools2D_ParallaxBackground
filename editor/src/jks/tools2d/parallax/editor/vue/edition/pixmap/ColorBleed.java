package jks.tools2d.parallax.editor.vue.edition.pixmap;

import java.nio.ByteBuffer;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Pixmap.Format;

/**
 * Gives every fully transparent pixel the colour of its nearest visible pixels, keeping it transparent: TexturePacker's
 * {@code bleed}. Filtering averages colours without weighting them by alpha, so transparent pixels left black grey the
 * edges of every shape; mipmap levels average whole blocks and draw a grey outline around every silhouette (r53).
 */
public final class ColorBleed
{
	private static final int[] DX = { -1, 0, 1, -1, 1, -1, 0, 1 };
	private static final int[] DY = { -1, -1, -1, 0, 0, 1, 1, 1 };

	private ColorBleed()
	{}

	/** Bleeds an RGBA8888 pixmap in place, one ring of pixels at a time, each taking the mean of its coloured neighbours. */
	public static void bleed(Pixmap pixmap)
	{
		if (pixmap.getFormat() != Format.RGBA8888)
			throw new IllegalArgumentException("ColorBleed needs RGBA8888, not " + pixmap.getFormat());

		int width = pixmap.getWidth(), height = pixmap.getHeight();
		ByteBuffer pixels = pixmap.getPixels();
		int count = width * height;
		boolean[] coloured = new boolean[count];
		int[] ring = new int[count];
		int ringSize = 0;

		for (int i = 0; i < count; i++)
			coloured[i] = (pixels.get(i * 4 + 3) & 0xff) != 0;

		for (int i = 0; i < count; i++)
			if (!coloured[i] && touchesColoured(coloured, i % width, i / width, width, height))
				ring[ringSize++] = i;

		int[] next = new int[count];
		int[] rgb = new int[count * 3];
		boolean[] queued = new boolean[count];
		while (ringSize > 0)
		{
			// Colour the whole ring from the pixels coloured before it, then mark it, so the result does not depend on order.
			for (int r = 0; r < ringSize; r++)
			{
				int i = ring[r], x = i % width, y = i / width;
				int red = 0, green = 0, blue = 0, n = 0;
				for (int d = 0; d < 8; d++)
				{
					int nx = x + DX[d], ny = y + DY[d];
					if (nx < 0 || ny < 0 || nx >= width || ny >= height || !coloured[ny * width + nx])
						continue;
					int at = (ny * width + nx) * 4;
					red += pixels.get(at) & 0xff;
					green += pixels.get(at + 1) & 0xff;
					blue += pixels.get(at + 2) & 0xff;
					n++;
				}
				rgb[r * 3] = red / n;
				rgb[r * 3 + 1] = green / n;
				rgb[r * 3 + 2] = blue / n;
			}

			int nextSize = 0;
			for (int r = 0; r < ringSize; r++)
			{
				int at = ring[r] * 4;
				pixels.put(at, (byte) rgb[r * 3]);
				pixels.put(at + 1, (byte) rgb[r * 3 + 1]);
				pixels.put(at + 2, (byte) rgb[r * 3 + 2]);
				coloured[ring[r]] = true;
			}
			for (int r = 0; r < ringSize; r++)
			{
				int i = ring[r], x = i % width, y = i / width;
				for (int d = 0; d < 8; d++)
				{
					int nx = x + DX[d], ny = y + DY[d];
					if (nx < 0 || ny < 0 || nx >= width || ny >= height)
						continue;
					int j = ny * width + nx;
					if (!coloured[j] && !queued[j])
					{
						queued[j] = true;
						next[nextSize++] = j;
					}
				}
			}

			int[] swap = ring;
			ring = next;
			next = swap;
			ringSize = nextSize;
		}
	}

	private static boolean touchesColoured(boolean[] coloured, int x, int y, int width, int height)
	{
		for (int d = 0; d < 8; d++)
		{
			int nx = x + DX[d], ny = y + DY[d];
			if (nx >= 0 && ny >= 0 && nx < width && ny < height && coloured[ny * width + nx])
				return true;
		}
		return false;
	}
}
